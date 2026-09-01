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
import com.zeroz4j.server.ClientVisibleException;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * The till, kept in memory.
 *
 * <p><b>Why nothing here is saved to disk.</b> Every other example that stores anything puts it in
 * ZeroZ DB. This one deliberately does not, and the reason is worth knowing before you copy it:
 * the persistence layer reaches into an object's fields directly, and the Java runtime refuses that
 * for a {@code record} unless the whole application is started with an extra command-line flag. A
 * record is a wire shape, not a storage shape. Records travel well and are the wrong thing to make
 * a persistence root out of, so the ledger lives in a list for as long as the server is running and
 * is gone when it stops. See the README.
 *
 * <p>Every call logs what it received, field by field, including the kind each sealed value turned
 * out to be. That is not decoration: it is how you can see, from the console, that a record came
 * back a record and that an inherited field arrived at all.
 */
@ApplicationScoped
public class PaymentsServiceImpl implements PaymentsService {

    private static final Logger LOG = Logger.getLogger(PaymentsServiceImpl.class.getName());

    /** The ledger. Newest is last, the way a paper roll works. */
    private final List<LedgerEntry> entries = new ArrayList<>();

    private final AtomicLong nextId = new AtomicLong(1);

    /** Puts two sales on the roll, so the screen has something on it the first time it is opened. */
    public PaymentsServiceImpl() {
        seed();
    }

    @Override
    public synchronized List<LedgerEntry> ledger() {
        LOG.info("[payments] ledger() -> " + entries.size() + " entries");
        return new ArrayList<>(entries);
    }

    @Override
    public Money quote(List<LineItem> lines) {
        Money total = totalOf(lines);
        LOG.info("[payments] quote() received " + describe(lines) + " -> " + total.formatted());
        return total;
    }

    @Override
    public synchronized Payment take(Payment proposed) {
        if (proposed == null) {
            throw new ClientVisibleException("There is nothing to take payment for.");
        }
        if (proposed.getLines() == null || proposed.getLines().isEmpty()) {
            throw new ClientVisibleException("Add something to the basket first.");
        }
        if (proposed.getMethod() == null) {
            throw new ClientVisibleException("Say how the customer is paying.");
        }

        Money due = totalOf(proposed.getLines());

        // This is the line that proves the round trip. Everything it prints came off the wire: the
        // note is a field declared on the abstract base class, the lines are records inside a
        // collection, and the method is a sealed value whose real kind is named here.
        LOG.info("[payments] take() received"
                + " note=\"" + safe(proposed.getNote()) + "\" (inherited from LedgerEntry)"
                + ", " + describe(proposed.getLines())
                + ", method=" + describeMethod(proposed.getMethod())
                + " -> due " + due.formatted());

        if (proposed.getMethod() instanceof CashPayment cash) {
            if (cash.handedOver() == null || cash.handedOver().isLessThan(due)) {
                throw new ClientVisibleException("That is not enough cash. The total is "
                        + due.formatted() + ".");
            }
        }

        Payment recorded = new Payment();
        recorded.setId("P" + nextId.getAndIncrement());
        recorded.setRecordedAt(Instant.now());
        recorded.setAmount(due);
        recorded.setNote(safe(proposed.getNote()));
        recorded.setLines(new ArrayList<>(proposed.getLines()));
        recorded.setMethod(proposed.getMethod());
        entries.add(recorded);

        LOG.info("[payments] take() recorded " + recorded.getId() + " for " + due.formatted());
        return recorded;
    }

    @Override
    public synchronized Refund refund(String paymentId, String note) {
        Payment original = null;
        for (LedgerEntry entry : entries) {
            if (entry instanceof Payment payment && payment.getId().equals(paymentId)) {
                original = payment;
                break;
            }
        }
        if (original == null) {
            throw new ClientVisibleException("There is no payment " + paymentId + " to refund.");
        }
        for (LedgerEntry entry : entries) {
            if (entry instanceof Refund existing
                    && paymentId.equals(existing.getAgainstPaymentId())) {
                throw new ClientVisibleException("Payment " + paymentId
                        + " has already been refunded.");
            }
        }

        Refund refund = new Refund();
        refund.setId("R" + nextId.getAndIncrement());
        refund.setRecordedAt(Instant.now());
        refund.setAmount(original.getAmount());
        refund.setNote(safe(note));
        refund.setAgainstPaymentId(paymentId);
        entries.add(refund);

        LOG.info("[payments] refund() gave back " + refund.getAmount().formatted()
                + " against " + paymentId);
        return refund;
    }

    @Override
    public synchronized DailySummary summary() {
        Money taken = Money.zero();
        Money refunded = Money.zero();
        Map<String, Money> byMethod = new LinkedHashMap<>();
        for (LedgerEntry entry : entries) {
            if (entry instanceof Payment payment) {
                taken = taken.plus(payment.getAmount());
                String kind = kindOf(payment.getMethod());
                byMethod.put(kind, byMethod.getOrDefault(kind, Money.zero())
                        .plus(payment.getAmount()));
            } else if (entry instanceof Refund refund) {
                refunded = refunded.plus(refund.getAmount());
            }
        }
        DailySummary summary = new DailySummary(taken, refunded, byMethod, entries.size());
        LOG.info("[payments] summary() -> taken " + taken.formatted()
                + ", refunded " + refunded.formatted()
                + ", " + byMethod.size() + " kinds of payment");
        return summary;
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Two sales, so the ledger is not empty the first time somebody opens the screen and two
     * different kinds of payment are on it from the start.
     */
    private void seed() {
        Payment opening = new Payment();
        opening.setId("P" + nextId.getAndIncrement());
        opening.setRecordedAt(Instant.now());
        opening.setNote("First sale of the day");
        opening.setLines(List.of(
                new LineItem("Flat white", 2, Money.of(320)),
                new LineItem("Almond croissant", 1, Money.of(285))));
        opening.setAmount(totalOf(opening.getLines()));
        opening.setMethod(new CardPayment("Visa", "4242"));
        entries.add(opening);

        Payment second = new Payment();
        second.setId("P" + nextId.getAndIncrement());
        second.setRecordedAt(Instant.now());
        second.setNote("Paid with a note, change given");
        second.setLines(List.of(new LineItem("Loaf of sourdough", 1, Money.of(450))));
        second.setAmount(totalOf(second.getLines()));
        second.setMethod(new CashPayment(Money.of(500)));
        entries.add(second);
    }

    private static Money totalOf(List<LineItem> lines) {
        Money total = Money.zero();
        if (lines != null) {
            for (LineItem line : lines) {
                total = total.plus(line.total());
            }
        }
        return total;
    }

    /**
     * Names the real kind a sealed value turned out to be.
     *
     * <p>An {@code instanceof} chain rather than a flag on the object. If a sealed hierarchy had not
     * survived the wire this would fall through to the last branch, which is exactly why an example
     * writes it this way.
     *
     * @param method the method to name
     * @return a short name for the kind
     */
    static String kindOf(PaymentMethod method) {
        if (method instanceof CardPayment) {
            return "Card";
        }
        if (method instanceof CashPayment) {
            return "Cash";
        }
        if (method instanceof BankTransfer) {
            return "Bank transfer";
        }
        if (method instanceof GiftCard) {
            return "Gift card";
        }
        return "Unknown";
    }

    private static String describeMethod(PaymentMethod method) {
        if (method instanceof CardPayment card) {
            return "CardPayment[scheme=" + card.scheme() + ", last4=" + card.last4() + "]";
        }
        if (method instanceof CashPayment cash) {
            return "CashPayment[handedOver=" + cash.handedOver().formatted() + "]";
        }
        if (method instanceof BankTransfer transfer) {
            return "BankTransfer[reference=" + transfer.reference() + "]";
        }
        if (method instanceof GiftCard card) {
            return "GiftCard[cardNumber=" + card.cardNumber()
                    + ", remainingAfter=" + card.remainingAfter().formatted() + "]";
        }
        return "an unrecognized kind: " + (method == null ? "null" : method.getClass().getName());
    }

    private static String describe(List<LineItem> lines) {
        if (lines == null || lines.isEmpty()) {
            return "no lines";
        }
        StringBuilder text = new StringBuilder(lines.size() + " line(s) [");
        for (int i = 0; i < lines.size(); i++) {
            LineItem line = lines.get(i);
            if (i > 0) {
                text.append(", ");
            }
            text.append(line.quantity()).append(" x ").append(line.description())
                .append(" @ ").append(line.unitPrice().formatted());
        }
        text.append("]");
        return text.toString();
    }

    private static String safe(String text) {
        return text == null ? "" : text.trim();
    }
}
