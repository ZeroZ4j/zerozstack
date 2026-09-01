/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
 * Author: Franz Schöning - Principal Enterprise Architect (https://www.franzschoning.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.zeroz4j.server.test;

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.RmiService;
import com.zeroz4j.api.RolesAllowed;
import com.zeroz4j.api.Secured;
import com.zeroz4j.api.i18n.FrameworkKeys;
import com.zeroz4j.api.i18n.Message;
import com.zeroz4j.server.ClientVisibleException;
import com.zeroz4j.server.RmiRequestContext;
import com.zeroz4j.server.ServerSettings;
import jakarta.enterprise.context.ApplicationScoped;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One server, two people, two languages, decided by nothing but who is asking.
 *
 * <p>This is what the whole first half of language support is for, and it is proved here end to end
 * against a real server: two connections open on the same server, one reading English and one
 * reading German, both make the same refused call, and each is answered in their own words. Nothing
 * is passed down through the call — the language rides on the connection, next to the caller's
 * identity, and is read at the one place that already decides what a caller is told.</p>
 *
 * <p>The German comes from {@code src/test/resources/i18n/zeroz4j_de.properties}, which is exactly
 * how an application supplies it: a file on the classpath, nothing registered, nothing compiled.</p>
 */
class OneServerTwoLanguagesTest {

    /** The catalog an application would put in its own shared module. */
    private static final String APP_CATALOG = "i18n/demo";

    @RmiService
    public interface Invoices {

        @RolesAllowed("approver")
        String approve(String invoiceId);

        @Secured
        String mine();

        String approveAgain(String invoiceId);
    }

    @ApplicationScoped
    public static class InvoicesImpl implements Invoices {

        @Override
        public String approve(String invoiceId) {
            return "approved";
        }

        @Override
        public String mine() {
            return "none";
        }

        @Override
        public String approveAgain(String invoiceId) {
            // What an application writes: the refusal is chosen here, in a method that knows
            // nothing about connections, and turned into words at the edge of the server.
            throw new ClientVisibleException(
                    new Message(APP_CATALOG, "invoice.alreadyApproved", invoiceId));
        }
    }

    // ================================================================= the demonstration

    @Test
    @DisplayName("the same refused call is answered in English and in German at the same time")
    void oneServerAnswersTwoPeopleInTwoLanguages() {
        try (TestServer server = TestServer.builder()
                     .named("invoices").beans(InvoicesImpl.class).start();
             TestConnection english = server.connectSpeaking("en", "alice");
             TestConnection german = server.connectSpeaking("de", "bernd")) {

            String toEnglish = errorFrom(english, Invoices.class, "approve", 1);
            String toGerman = errorFrom(german, Invoices.class, "approve", 2);

            System.out.println("A caller reading English is told: " + toEnglish);
            System.out.println("A caller reading German is told:  " + toGerman);

            assertEquals("Access denied: requires role [approver] but user has []", toEnglish);
            assertEquals("Zugriff verweigert: erfordert die Rolle [approver], vorhanden ist []",
                    toGerman);
        }
    }

    @Test
    @DisplayName("an application's own refusal is answered in the caller's language too")
    void anApplicationRefusalIsTranslated() {
        try (TestServer server = TestServer.builder()
                     .named("invoices-app").beans(InvoicesImpl.class).start();
             TestConnection english = server.connectSpeaking("en");
             TestConnection german = server.connectSpeaking("de")) {

            String toEnglish = errorFrom(english, Invoices.class, "approveAgain", 11, "INV-4711");
            String toGerman = errorFrom(german, Invoices.class, "approveAgain", 12, "INV-4711");

            System.out.println("An application refusal in English: " + toEnglish);
            System.out.println("An application refusal in German:  " + toGerman);

            assertEquals("Invoice INV-4711 was already approved.", toEnglish);
            assertEquals("Rechnung INV-4711 wurde bereits genehmigt.", toGerman);
        }
    }

    @Test
    @DisplayName("the server log keeps English whatever language the caller reads")
    void theLogKeepsEnglish() {
        ClientVisibleException refusal = new ClientVisibleException(
                new Message(APP_CATALOG, "invoice.alreadyApproved", "INV-4711"));

        // getMessage() is what every log line and every stack trace prints.
        assertEquals("Invoice INV-4711 was already approved.", refusal.getMessage());
        assertEquals("Rechnung INV-4711 wurde bereits genehmigt.",
                refusal.clientMessage().text("de"));
    }

    @Test
    @DisplayName("a connection that never said what it reads is answered in the deployment's own language")
    void aSilentConnectionGetsTheDeploymentDefault() {
        try (TestServer server = TestServer.builder()
                     .named("german-deployment")
                     .ignoringSystemProperties()
                     .set(ServerSettings.I18N_DEFAULT_LOCALE, "de")
                     .beans(InvoicesImpl.class).start();
             TestConnection quiet = server.connect("carla")) {

            String told = errorFrom(quiet, Invoices.class, "approve", 21);

            assertEquals("Zugriff verweigert: erfordert die Rolle [approver], vorhanden ist []",
                    told);
        }
    }

    @Test
    @DisplayName("a deployment that adds no language sees exactly what it saw before")
    void nothingChangesForADeploymentWithNoLanguage() {
        try (TestServer server = TestServer.builder()
                     .named("english-only").beans(InvoicesImpl.class).start();
             TestConnection anybody = server.connect()) {

            assertEquals("Authentication required for: " + Invoices.class.getName() + "#mine",
                    errorFrom(anybody, Invoices.class, "mine", 31));
            assertEquals("Rejected RMI call to unregistered service: com.example.NoSuchService",
                    errorFrom(anybody, "com.example.NoSuchService", "anything", 32));
            assertEquals("No method 'noSuchMethod' on service: " + Invoices.class.getName(),
                    errorFrom(anybody, Invoices.class, "noSuchMethod", 33));
        }
    }

    @Test
    @DisplayName("a service reads the caller's language off the context, never off an argument")
    void theCallersLanguageIsOnTheContext() {
        try (TestServer server = TestServer.builder()
                     .named("context").beans(InvoicesImpl.class).start();
             TestConnection german = server.connectSpeaking("de-AT", "bernd")) {

            // de-AT is narrowed to de: the deployment has a German catalog and no Austrian one, and
            // a half-translated screen is worse than a translated one.
            assertEquals("de", german.language());
        } finally {
            RmiRequestContext.clear();
        }
    }

    @Test
    @DisplayName("a test can assert on which refusal happened rather than on its wording")
    void refusalsCanBeAssertedByName() {
        ClientVisibleException refusal = new ClientVisibleException(
                com.zeroz4j.api.i18n.FrameworkText.accessDenied("[approver]", "[]"));

        Refusals.assertRefusedWith(FrameworkKeys.ACCESS_DENIED, refusal);

        AssertionError wrongOne = assertThrows(AssertionError.class,
                () -> Refusals.assertRefusedWith(FrameworkKeys.UNKNOWN_SERVICE, refusal));
        assertTrue(wrongOne.getMessage().contains(FrameworkKeys.ACCESS_DENIED),
                wrongOne.getMessage());

        AssertionError plainSentence = assertThrows(AssertionError.class,
                () -> Refusals.assertRefusedWith(FrameworkKeys.ACCESS_DENIED,
                        new ClientVisibleException("no key here")));
        assertTrue(plainSentence.getMessage().contains("carries no message to name"),
                plainSentence.getMessage());
    }

    @Test
    @DisplayName("the caller's locale is never null, so nothing has to check")
    void theLocaleIsNeverNull() {
        RmiRequestContext.clear();
        assertNotNull(RmiRequestContext.getLocale());
        RmiRequestContext.setContext(null, java.util.Collections.emptySet(), "s1", null, "b1",
                Locale.GERMAN);
        assertEquals("de", RmiRequestContext.getLocale().getLanguage());
        RmiRequestContext.clear();
    }

    // ---------------------------------------------------------------- driving one call in

    private static String errorFrom(TestConnection browser, Class<?> service, String method,
                                    int messageId, Object... arguments) {
        return errorFrom(browser, service.getName(), method, messageId, arguments);
    }

    /** Sends one call the way a browser does, and reads the refusal it comes back with. */
    private static String errorFrom(TestConnection browser, String serviceName, String method,
                                    int messageId, Object... arguments) {
        GrowableBuffer call = new GrowableBuffer();
        call.putInt(messageId);
        BinarySerializer.writeString(call, serviceName);
        BinarySerializer.writeString(call, method);
        call.putInt(arguments.length);
        for (Object argument : arguments) {
            BinarySerializer.writeValue(call, argument, browser.server().mapper());
        }
        browser.send(call.toByteArray());

        // The answer is handled on another thread, so it is not necessarily the last frame: find
        // the error frame answering this call's own message number.
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            List<byte[]> frames = browser.frames();
            for (byte[] frame : frames) {
                if (frame.length <= 5) {
                    continue;
                }
                ByteBuffer reading = ByteBuffer.wrap(frame);
                if (reading.getInt() != messageId || reading.get() != 0x0F) {
                    continue;
                }
                return BinarySerializer.readString(reading);
            }
            try {
                Thread.sleep(5);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("No refusal came back for call " + messageId + " to "
                + serviceName + "#" + method);
    }
}
