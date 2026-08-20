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
package com.zeroz4j.server;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The upload pass is the whole permission story: the HTTP upload address has no WebSocket session to
 * read an identity from, so if a pass can be replayed, outlived or moved to another browser, an
 * upload can be made by someone who never logged in.
 *
 * <p>Each of these is a property the feature would be broken without, which is why they are asserted
 * rather than reviewed.</p>
 */
class UploadPassesTest {

    private static final Principal DEMO = () -> "demo";

    @BeforeEach
    void bindAConnection() {
        UploadPasses.clearForTesting();
        RmiRequestContext.setContext(DEMO, Set.of("user"), "session-1", "acme", "browser-1");
    }

    @AfterEach
    void unbind() {
        RmiRequestContext.clear();
        UploadPasses.clearForTesting();
        System.clearProperty(UploadLimits.PASS_SECONDS_PROPERTY);
        System.clearProperty(UploadLimits.MAX_BYTES_PROPERTY);
    }

    @Test
    void carriesTheIdentityOfTheConnectionThatAskedForIt() {
        UploadPass pass = UploadPasses.issue("holiday.jpg", "image/jpeg", 1024L);

        assertSame(DEMO, pass.getPrincipal());
        assertEquals(Set.of("user"), pass.getRoles());
        assertEquals("session-1", pass.getSessionId());
        assertEquals("acme", pass.getTenantId());
        assertEquals("browser-1", pass.getClientId());
        assertEquals("holiday.jpg", pass.getFileName());
        assertEquals(1024L, pass.getDeclaredSize());
        assertNotNull(pass.getToken());
    }

    @Test
    void cannotBeUsedTwice() {
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 10L);

        assertNotNull(UploadPasses.consume(pass.getToken(), "browser-1"));

        UploadRefusedException refused = assertThrows(UploadRefusedException.class,
                () -> UploadPasses.consume(pass.getToken(), "browser-1"));
        assertEquals(401, refused.getStatus());
    }

    @Test
    void cannotBeUsedAfterItExpires() throws Exception {
        System.setProperty(UploadLimits.PASS_SECONDS_PROPERTY, "1");
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 10L);

        Thread.sleep(1100L);

        UploadRefusedException refused = assertThrows(UploadRefusedException.class,
                () -> UploadPasses.consume(pass.getToken(), "browser-1"));
        assertEquals(401, refused.getStatus());
    }

    @Test
    void cannotBeUsedByADifferentBrowser() {
        UploadPass pass = UploadPasses.issue("a.txt", "text/plain", 10L);

        UploadRefusedException refused = assertThrows(UploadRefusedException.class,
                () -> UploadPasses.consume(pass.getToken(), "browser-2"));
        assertEquals(403, refused.getStatus());

        // And it is spent regardless, so the attempt did not leave a usable pass behind.
        assertThrows(UploadRefusedException.class,
                () -> UploadPasses.consume(pass.getToken(), "browser-1"));
    }

    @Test
    void aRequestWithNoPassIsRefused() {
        assertEquals(401, assertThrows(UploadRefusedException.class,
                () -> UploadPasses.consume(null, "browser-1")).getStatus());
        assertEquals(401, assertThrows(UploadRefusedException.class,
                () -> UploadPasses.consume("", "browser-1")).getStatus());
        assertEquals(401, assertThrows(UploadRefusedException.class,
                () -> UploadPasses.consume("not-a-token-anyone-issued", "browser-1")).getStatus());
    }

    @Test
    void aDeclaredSizeOverTheMaximumIsRefusedBeforeAnyByteIsSent() {
        System.setProperty(UploadLimits.MAX_BYTES_PROPERTY, "1024");

        UploadRefusedException refused = assertThrows(UploadRefusedException.class,
                () -> UploadPasses.issue("huge.bin", "application/octet-stream", 1025L));

        assertEquals(413, refused.getStatus());
        assertTrue(refused.getMessage().contains("too big"),
                "the message goes on the screen, so it has to read like a sentence: "
                        + refused.getMessage());
        assertEquals(0, UploadPasses.outstanding(), "a refused request must not leave a pass behind");
    }

    @Test
    void anExpiredPassNobodyCameBackForIsSweptOut() throws Exception {
        System.setProperty(UploadLimits.PASS_SECONDS_PROPERTY, "1");
        UploadPasses.issue("a.txt", "text/plain", 10L);
        assertEquals(1, UploadPasses.outstanding());

        Thread.sleep(1100L);
        UploadPasses.issue("b.txt", "text/plain", 10L);

        assertEquals(1, UploadPasses.outstanding(), "the abandoned pass should have been dropped");
    }
}
