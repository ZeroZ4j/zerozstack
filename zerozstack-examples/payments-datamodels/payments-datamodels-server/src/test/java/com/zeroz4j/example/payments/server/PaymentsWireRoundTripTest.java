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
package com.zeroz4j.example.payments.server;

import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.example.payments.api.PaymentsService;
import com.zeroz4j.example.payments.model.BankTransfer;
import com.zeroz4j.example.payments.model.CardPayment;
import com.zeroz4j.example.payments.model.CashPayment;
import com.zeroz4j.example.payments.model.DailySummary;
import com.zeroz4j.example.payments.model.GiftCard;
import com.zeroz4j.example.payments.model.LedgerEntry;
import com.zeroz4j.example.payments.model.LineItem;
import com.zeroz4j.example.payments.model.Money;
import com.zeroz4j.example.payments.model.Payment;
import com.zeroz4j.example.payments.model.PaymentMethod;
import com.zeroz4j.example.payments.model.Refund;
import com.zeroz4j.server.test.TestConnection;
import com.zeroz4j.server.test.TestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every wire shape this example uses, sent as real bytes through a real server.
 *
 * <p>Calling {@code new PaymentsServiceImpl().take(payment)} would prove that the arithmetic is
 * right and nothing else — no serializer would run, so a record that could not be written and a
 * base class whose fields were dropped would both pass. So none of these tests call the bean
 * directly. Each one <b>builds the bytes a browser would send</b>, hands them to the server through
 * a connection, and decodes the answer with the same serializer the browser uses.</p>
 *
 * <p>The server is a {@link TestServer}: a whole server of its own, started in this process in
 * about a tenth of a second, with its own connections and its own settings. It comes from
 * {@code zerozstack-server-test}, which the pom takes with <b>test scope</b> because it starts a
 * bean container and must never reach a production classpath. See {@code docs/guides/testing.md}.
 *
 * <p>What the framework's own tests cannot cover, and this does: the code the annotation processor
 * generated <em>for this application's models</em>. That code is written fresh for every project,
 * so every project wants a test like this one.
 */
class PaymentsWireRoundTripTest {

    private TestServer server;
    private TestConnection browser;
    private final AtomicInteger messageIds = new AtomicInteger(1);

    @BeforeEach
    void startTheServer() {
        server = TestServer.builder()
                .named("payments-desk")
                .beans(PaymentsServiceImpl.class)
                .start();
        browser = server.connect();
    }

    @AfterEach
    void stopTheServer() {
        if (browser != null) {
            browser.close();
        }
        if (server != null) {
            server.close();
        }
    }

    // ================================================================== records

    @Test
    @DisplayName("records go up inside a list and one comes back on its own")
    void recordsSurviveBothDirections() {
        List<LineItem> basket = List.of(
                new LineItem("Flat white", 2, Money.of(320)),
                new LineItem("Almond croissant", 1, Money.of(285)));

        Object answer = call("quote", basket);

        Money total = assertInstanceOf(Money.class, answer,
                "the total must come back as a Money record, not as a number");
        assertEquals(925, total.cents(), "2 x 3.20 plus 2.85");
        assertEquals("EUR", total.currency(), "the second component travelled too");
        assertEquals(Money.of(925), total, "a record's own equals works on both sides of the wire");
    }

    @Test
    @DisplayName("a record holding a map of records comes back whole")
    void aRecordCanHoldAMapOfRecords() {
        takePaidBy(new CardPayment("Visa", "4242"), "card sale");
        takePaidBy(new BankTransfer("INV-1"), "transfer sale");

        DailySummary summary = assertInstanceOf(DailySummary.class, call("summary"),
                "the summary is a record");

        Map<String, Money> byMethod = summary.byMethod();
        assertNotNull(byMethod, "the map inside the record arrived");
        assertTrue(byMethod.containsKey("Card"), "the card takings are there: " + byMethod);
        assertTrue(byMethod.containsKey("Bank transfer"),
                "the transfer takings are there: " + byMethod);
        assertInstanceOf(Money.class, byMethod.get("Card"),
                "each value in the map is itself a record");
        long acrossTheMap = 0;
        for (Money perKind : byMethod.values()) {
            acrossTheMap += perKind.cents();
        }
        assertEquals(summary.taken().cents(), acrossTheMap,
                "the total in the record and the totals in its map agree");
    }

    // ================================================================== sealed families

    @Test
    @DisplayName("each kind of payment comes back as the kind it really is")
    void everySealedMemberKeepsItsIdentity() {
        Payment card = takePaidBy(new CardPayment("Visa", "4242"), "card");
        Payment cash = takePaidBy(new CashPayment(Money.of(1000)), "cash");
        Payment transfer = takePaidBy(new BankTransfer("INV-2026-0912"), "transfer");
        Payment gift = takePaidBy(new GiftCard("GC-8841", Money.of(1250)), "gift card");

        // Not "has the right kind field" — is the right class. That is what the sealed set buys.
        CardPayment backAsCard = assertInstanceOf(CardPayment.class, card.getMethod());
        assertEquals("Visa", backAsCard.scheme());
        assertEquals("4242", backAsCard.last4());

        CashPayment backAsCash = assertInstanceOf(CashPayment.class, cash.getMethod());
        assertEquals(Money.of(1000), backAsCash.handedOver(),
                "a record nested inside a sealed value inside a class");

        assertEquals("INV-2026-0912",
                assertInstanceOf(BankTransfer.class, transfer.getMethod()).reference());

        GiftCard backAsGift = assertInstanceOf(GiftCard.class, gift.getMethod());
        assertEquals("GC-8841", backAsGift.cardNumber());
        assertEquals(Money.of(1250), backAsGift.remainingAfter());
    }

    @Test
    @DisplayName("a list declared as the sealed base arrives as the real kinds")
    void aSealedBaseInAListKeepsEveryRowsType() {
        Payment paid = takePaidBy(new CardPayment("Visa", "4242"), "to be refunded");
        call("refund", paid.getId(), "changed their mind");

        Object answer = call("ledger");
        List<?> entries = assertInstanceOf(List.class, answer);

        boolean sawPayment = false;
        boolean sawRefund = false;
        for (Object entry : entries) {
            assertInstanceOf(LedgerEntry.class, entry,
                    "every row is a ledger entry: " + entry.getClass());
            if (entry instanceof Payment) {
                sawPayment = true;
            } else if (entry instanceof Refund) {
                sawRefund = true;
            }
        }
        assertTrue(sawPayment, "the payments came back as Payment");
        assertTrue(sawRefund, "the refund came back as Refund, in the same list");
    }

    // ================================================================== model inheritance

    @Test
    @DisplayName("fields declared on the base class travel in both directions")
    void inheritedFieldsTravel() {
        // note is declared on LedgerEntry, not on Payment. Before 0.8.0 it would have gone up as
        // nothing at all and come back as nothing at all, with no error either way.
        Payment recorded = takePaidBy(new CardPayment("Visa", "4242"), "table 4, no receipt");

        assertEquals("table 4, no receipt", recorded.getNote(),
                "the note went up on an inherited field and came back on one");
        assertNotNull(recorded.getId(), "the identifier is on the base class");
        assertNotNull(recorded.getRecordedAt(), "so is the time, and it is an Instant");
        assertNotNull(recorded.getAmount(), "so is the amount, and it is a record");
        assertEquals(925, recorded.getAmount().cents());
    }

    @Test
    @DisplayName("a refund carries the base class's fields as well as its own")
    void bothSubclassesGetTheBaseFields() {
        Payment paid = takePaidBy(new CardPayment("Visa", "4242"), "sale");

        Refund refund = assertInstanceOf(Refund.class,
                call("refund", paid.getId(), "wrong size"));

        assertEquals(paid.getId(), refund.getAgainstPaymentId(), "its own field");
        assertEquals("wrong size", refund.getNote(), "and the base class's");
        assertEquals(paid.getAmount(), refund.getAmount(), "the same amount goes back");
        assertNotNull(refund.getRecordedAt());
    }

    // ================================================================== the connection itself

    @Test
    @DisplayName("a refusal the application wrote reaches the browser as words")
    void aRefusalIsAnswered() {
        Payment tooLittleCash = new Payment();
        tooLittleCash.setLines(List.of(new LineItem("Flat white", 2, Money.of(320))));
        tooLittleCash.setMethod(new CashPayment(Money.of(100)));
        tooLittleCash.setNote("not enough");

        byte[] answer = sendAndAwaitAnswer("take", tooLittleCash);
        ByteBuffer buffer = ByteBuffer.wrap(answer);
        buffer.getInt();
        assertEquals(0x0F, buffer.get(), "an error frame, not a result");
        String reason = BinarySerializer.readString(buffer);
        assertTrue(reason.contains("not enough cash"),
                "the sentence the service wrote reaches the caller: " + reason);
    }

    @Test
    @DisplayName("two servers in one test do not share a ledger")
    void eachServerHasItsOwnTill() {
        takePaidBy(new CardPayment("Visa", "4242"), "on the first server");

        try (TestServer second = TestServer.builder()
                     .named("second-desk")
                     .beans(PaymentsServiceImpl.class)
                     .start()) {

            int onFirst = server.bean(PaymentsService.class).ledger().size();
            int onSecond = second.bean(PaymentsService.class).ledger().size();

            assertEquals(3, onFirst, "two seeded entries plus the one this test took");
            assertEquals(2, onSecond, "the other server has only its own two seeded entries");
        }
    }

    // ================================================================== driving the wire

    /**
     * Sends one payment up and returns what came back.
     *
     * @param method how it was paid
     * @param note   what to write on it, which lives on the base class
     * @return the recorded payment, decoded from the server's answer
     */
    private Payment takePaidBy(PaymentMethod method, String note) {
        Payment proposed = new Payment();
        proposed.setLines(new ArrayList<>(List.of(
                new LineItem("Flat white", 2, Money.of(320)),
                new LineItem("Almond croissant", 1, Money.of(285)))));
        proposed.setMethod(method);
        proposed.setNote(note);
        return assertInstanceOf(Payment.class, call("take", proposed));
    }

    /**
     * Makes one call the way a browser makes it, and decodes the result.
     *
     * @param method the service method to call
     * @param args   its arguments
     * @return whatever came back
     */
    private Object call(String method, Object... args) {
        byte[] answer = sendAndAwaitAnswer(method, args);
        ByteBuffer buffer = ByteBuffer.wrap(answer);
        buffer.getInt();
        byte opcode = buffer.get();
        if (opcode == 0x0F) {
            throw new AssertionError("the server refused the call: "
                    + BinarySerializer.readString(buffer));
        }
        assertEquals(0x01, opcode, "a successful RMI answer");
        return BinarySerializer.readValue(buffer, server.mapper());
    }

    /**
     * Writes the frame a browser writes for an RMI call, puts it on the connection, and hands back
     * the frame the server wrote in reply.
     *
     * <p>The shape is in {@code docs/PROTOCOL.md}: a message number, the interface name, the method
     * name, how many arguments there are, then the arguments.
     *
     * @param method the method name
     * @param args   the arguments
     * @return the reply frame, bytes and all
     */
    private byte[] sendAndAwaitAnswer(String method, Object... args) {
        ObjectMapper mapper = server.mapper();
        int messageId = messageIds.getAndIncrement();
        GrowableBuffer request = new GrowableBuffer();
        request.putInt(messageId);
        BinarySerializer.writeString(request, PaymentsService.class.getName());
        BinarySerializer.writeString(request, method);
        request.putInt(args == null ? 0 : args.length);
        if (args != null) {
            for (Object arg : args) {
                BinarySerializer.writeValue(request, arg, mapper);
            }
        }

        browser.clearFrames();
        browser.send(request.toByteArray());
        return awaitAnswer(messageId);
    }

    /**
     * Waits for the server to answer the call just sent, and picks that answer out.
     *
     * <p>Two things make this more than a read. Reading a connection waits for the server's
     * <em>outbound</em> writer, which is empty the instant a call is sent — the call has not been
     * handled yet, because a frame a browser sends is queued and handled on another thread. And the
     * answer is not necessarily the last frame: anything else the server writes to that browser
     * arrives on the same connection. So this waits, and matches on the number the request was sent
     * under, exactly as a browser does. Calling a bean through {@code server.bean(...)} needs none
     * of it.
     *
     * @param messageId the number this call was sent under
     * @return the answer frame
     */
    private byte[] awaitAnswer(int messageId) {
        long deadline = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < deadline) {
            for (byte[] frame : browser.frames()) {
                if (frame.length < 5) {
                    continue;
                }
                ByteBuffer header = ByteBuffer.wrap(frame);
                if (header.getInt() == messageId) {
                    byte opcode = header.get();
                    if (opcode == 0x01 || opcode == 0x0F) {
                        return frame;
                    }
                }
            }
            Thread.onSpinWait();
        }
        throw new AssertionError("the server did not answer the call within five seconds");
    }
}
