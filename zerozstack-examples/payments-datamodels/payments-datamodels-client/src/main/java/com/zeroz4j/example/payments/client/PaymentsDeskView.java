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
package com.zeroz4j.example.payments.client;

import com.zeroz4j.api.Disposable;
import com.zeroz4j.example.payments.api.PaymentsService;
import com.zeroz4j.example.payments.api.PaymentsService_Stub;
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
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.Alert;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Card;
import com.zeroz4j.ui.component.CardTitle;
import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.EmptyState;
import com.zeroz4j.ui.component.KpiTile;
import com.zeroz4j.ui.component.Select;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.HorizontalLayout;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.layout.VerticalLayout;
import com.zeroz4j.ui.theme.TextStyle;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The payments desk: a basket, a way of paying, and the ledger.
 *
 * <p>Everything on this screen came off the wire as a record, a sealed value, or an object whose
 * fields live on a base class. The screen is built so that if any of those three had not survived
 * the journey it would be visible rather than theoretical:</p>
 *
 * <ul>
 *   <li>the ledger asks each row {@code instanceof Payment} — if the sealed hierarchy had not
 *       survived, every row would fall through to "an entry of an unexpected kind";</li>
 *   <li>every row shows the identifier, the time and the note, all four of which are declared on
 *       the abstract base class — if inherited fields had not travelled they would be blank;</li>
 *   <li>the running total comes back from the server as a {@code Money} record and is printed
 *       through the record's own method, so a total that arrived as loose numbers could not print
 *       at all.</li>
 * </ul>
 */
public class PaymentsDeskView extends VerticalLayout {

    private final PaymentsService payments = new PaymentsService_Stub();

    private final ValueSignal<List<LineItem>> basket = new ValueSignal<>(new ArrayList<>());
    private final ValueSignal<Money> basketTotal = new ValueSignal<>(Money.zero());
    private final ValueSignal<List<LedgerEntry>> ledger = new ValueSignal<>(new ArrayList<>());
    private final ValueSignal<DailySummary> summary = new ValueSignal<>(null);

    private final ValueSignal<String> methodKind = new ValueSignal<>("Card");
    private final ValueSignal<String> message = new ValueSignal<>("");
    private final ValueSignal<Boolean> messageIsGood = new ValueSignal<>(true);
    private final ValueSignal<List<String>> roundTrip = new ValueSignal<>(new ArrayList<>());

    private final List<Disposable> disposables = new ArrayList<>();

    // The basket form.
    private final TextField itemName = new TextField();
    private final IntegerField itemQuantity = new IntegerField();
    private final TextField itemPrice = new TextField();

    // How the customer is paying. Only the fields belonging to the chosen kind are on the screen.
    private final Select kindSelect = new Select();
    private final TextField cardScheme = new TextField();
    private final TextField cardLast4 = new TextField();
    private final TextField cashHandedOver = new TextField();
    private final TextField transferReference = new TextField();
    private final TextField giftCardNumber = new TextField();
    private final TextField giftCardRemaining = new TextField();
    private final TextField note = new TextField();

    private final Div cardFields = new Div();
    private final Div cashFields = new Div();
    private final Div transferFields = new Div();
    private final Div giftCardFields = new Div();

    public PaymentsDeskView() {
        super();
        addClassName("gap-6");
        addClassName("w-full");

        add(header());
        add(totals());
        add(messageArea());

        HorizontalLayout columns = new HorizontalLayout();
        columns.addClassName("gap-6");
        columns.addClassName("w-full");
        columns.addClassName("items-start");
        columns.addClassName("flex-wrap");
        columns.add(basketCard());
        columns.add(payingCard());
        columns.add(ledgerCard());
        add(columns);

        refreshEverything();
    }

    // ------------------------------------------------------------------ the page furniture

    private Component header() {
        VerticalLayout head = new VerticalLayout();
        head.addClassName("gap-1");
        Span title = new Span("Payments desk");
        TextStyle.PAGE_TITLE.applyTo(title);
        head.add(title);
        Span subtitle = new Span("Records, a sealed family of payment kinds, and two entry types "
                + "that share a base class - all of it crossing the wire in both directions.");
        TextStyle.SECONDARY.applyTo(subtitle);
        head.add(subtitle);
        return head;
    }

    private Component totals() {
        HorizontalLayout tiles = new HorizontalLayout();
        tiles.addClassName("gap-4");
        tiles.addClassName("w-full");
        tiles.addClassName("flex-wrap");

        KpiTile taken = new KpiTile("Taken");
        KpiTile refunded = new KpiTile("Given back");
        KpiTile net = new KpiTile("In the drawer");
        tiles.add(taken, refunded, net);

        Div breakdown = new Div();
        breakdown.addClassName("w-full");

        disposables.add(Effect.create(() -> {
            DailySummary current = summary.get();
            if (current == null) {
                taken.value("-");
                refunded.value("-");
                net.value("-");
                breakdown.setText("");
                return;
            }
            // Every one of these is a Money record that arrived inside another record.
            taken.value(current.taken().formatted());
            refunded.value(current.refunded().formatted());
            net.value(current.net().formatted());

            StringBuilder line = new StringBuilder();
            for (Map.Entry<String, Money> byKind : current.byMethod().entrySet()) {
                if (line.length() > 0) {
                    line.append("   |   ");
                }
                line.append(byKind.getKey()).append(": ").append(byKind.getValue().formatted());
            }
            breakdown.setText(line.length() == 0
                    ? "Nothing taken yet."
                    : "By how they paid - " + line);
            TextStyle.CAPTION.applyTo(breakdown);
        }));

        VerticalLayout block = new VerticalLayout();
        block.addClassName("gap-2");
        block.addClassName("w-full");
        block.add(tiles, breakdown);
        return block;
    }

    private Component messageArea() {
        Div area = new Div();
        area.addClassName("w-full");
        disposables.add(Effect.create(() -> {
            String text = message.get();
            area.removeAll();
            if (text != null && !text.trim().isEmpty()) {
                area.add(Boolean.TRUE.equals(messageIsGood.get())
                        ? Alert.success(text)
                        : Alert.danger(text));
            }
        }));
        return area;
    }

    // ------------------------------------------------------------------ the basket

    private Component basketCard() {
        Card card = new Card();
        card.addClassName("flex-1");
        card.addClassName("min-w-[20rem]");
        card.addClassName("bg-base-200");
        card.add(new CardTitle("This sale"));

        itemName.withLabel("What are they buying?");
        itemName.setHelperText("For example: Flat white");
        itemQuantity.withLabel("How many");
        itemQuantity.setValue(1);
        itemPrice.withLabel("Price of one");
        itemPrice.setHelperText("In euros, like 3.20");

        card.add(itemName, itemQuantity, itemPrice);

        Button addLine = new Button("Add to the basket");
        addLine.addClassName("btn-secondary");
        addLine.addClassName("btn-sm");
        addLine.addClassName("mt-2");
        addLine.addClickListener(event -> addLineToBasket());
        card.add(addLine);

        Div lines = new Div();
        lines.addClassName("mt-4");
        lines.addClassName("w-full");
        card.add(lines);
        disposables.add(Effect.create(() -> renderBasket(lines)));

        Span total = new Span("");
        TextStyle.SECTION_TITLE.applyTo(total);
        total.addClassName("mt-2");
        card.add(total);
        disposables.add(Effect.create(() ->
                // basketTotal holds a Money the server worked out and sent back. Printing it goes
                // through the record's own method, so this line only appears if a real record came
                // back rather than a bag of numbers.
                total.setText("Total: " + basketTotal.get().formatted())));

        return card;
    }

    private void renderBasket(Div container) {
        container.removeAll();
        List<LineItem> current = basket.get();
        if (current.isEmpty()) {
            Span empty = new Span("The basket is empty.");
            TextStyle.SECONDARY.applyTo(empty);
            container.add(empty);
            return;
        }
        for (int index = 0; index < current.size(); index++) {
            LineItem line = current.get(index);
            final int position = index;

            HorizontalLayout row = new HorizontalLayout();
            row.addClassName("justify-between");
            row.addClassName("items-center");
            row.addClassName("gap-2");
            row.addClassName("py-1");

            Span text = new Span(line.quantity() + " x " + line.description()
                    + " @ " + line.unitPrice().formatted() + "  =  " + line.total().formatted());
            row.add(text);

            Button remove = new Button("Take off");
            remove.addClassName("btn-ghost");
            remove.addClassName("btn-xs");
            remove.addClickListener(event -> removeLine(position));
            row.add(remove);

            container.add(row);
        }
    }

    private void addLineToBasket() {
        String description = trimmed(itemName.getValue());
        if (description.isEmpty()) {
            say("Say what they are buying before adding it.", false);
            return;
        }
        int quantity = itemQuantity.getValue() == null ? 1 : itemQuantity.getValue();
        if (quantity < 1) {
            say("A basket line needs at least one of something.", false);
            return;
        }
        long cents = parseEuros(itemPrice.getValue());
        if (cents < 0) {
            say("Write the price in euros, like 3.20.", false);
            return;
        }

        // A record built on the browser, about to travel up inside a collection.
        LineItem line = new LineItem(description, quantity, Money.of(cents));
        basket.update(existing -> {
            List<LineItem> next = new ArrayList<>(existing);
            next.add(line);
            return next;
        });
        itemName.setValue("");
        itemQuantity.setValue(1);
        itemPrice.setValue("");
        say("", true);
        requote();
    }

    private void removeLine(int position) {
        basket.update(existing -> {
            List<LineItem> next = new ArrayList<>(existing);
            if (position >= 0 && position < next.size()) {
                next.remove(position);
            }
            return next;
        });
        requote();
    }

    /**
     * Asks the server what the basket comes to.
     *
     * <p>Records go up inside a list, and one comes back on its own. Doing the arithmetic on the
     * browser would be quicker and would prove nothing.</p>
     */
    private void requote() {
        try {
            basketTotal.set(payments.quote(basket.get()));
        } catch (Exception failed) {
            say("Could not work out the total: " + failed.getMessage(), false);
        }
    }

    // ------------------------------------------------------------------ how they are paying

    private Component payingCard() {
        Card card = new Card();
        card.addClassName("flex-1");
        card.addClassName("min-w-[20rem]");
        card.addClassName("bg-base-200");
        card.add(new CardTitle("How they are paying"));

        kindSelect.withLabel("Payment kind");
        kindSelect.setItems(List.of("Card", "Cash", "Bank transfer", "Gift card"));
        kindSelect.bindValue(methodKind);
        card.add(kindSelect);

        cardScheme.withLabel("Card scheme");
        cardScheme.setValue("Visa");
        cardLast4.withLabel("Last four digits");
        cardLast4.setValue("4242");
        cardFields.addClassName("w-full");
        cardFields.add(cardScheme, cardLast4);

        cashHandedOver.withLabel("Cash handed over");
        cashHandedOver.setHelperText("In euros, like 10.00");
        cashHandedOver.setValue("10.00");
        cashFields.addClassName("w-full");
        cashFields.add(cashHandedOver);

        transferReference.withLabel("Transfer reference");
        transferReference.setValue("INV-2026-0912");
        transferFields.addClassName("w-full");
        transferFields.add(transferReference);

        giftCardNumber.withLabel("Gift card number");
        giftCardNumber.setValue("GC-8841");
        giftCardRemaining.withLabel("Left on the card afterwards");
        giftCardRemaining.setHelperText("In euros, like 12.50");
        giftCardRemaining.setValue("12.50");
        giftCardFields.addClassName("w-full");
        giftCardFields.add(giftCardNumber, giftCardRemaining);

        Div kindFields = new Div();
        kindFields.addClassName("w-full");
        card.add(kindFields);
        disposables.add(Effect.create(() -> {
            String chosen = methodKind.get();
            kindFields.removeAll();
            if ("Cash".equals(chosen)) {
                kindFields.add(cashFields);
            } else if ("Bank transfer".equals(chosen)) {
                kindFields.add(transferFields);
            } else if ("Gift card".equals(chosen)) {
                kindFields.add(giftCardFields);
            } else {
                kindFields.add(cardFields);
            }
        }));

        note.withLabel("Note for the ledger");
        note.setHelperText("This field is declared on the base class, not on the payment. "
                + "Watch it come back on the entry.");
        note.setValue("Regular");
        card.add(note);

        Button take = new Button("Take the payment");
        take.addClassName("btn-primary");
        take.addClassName("mt-3");
        take.addClickListener(event -> takePayment());
        card.add(take);

        Div proof = new Div();
        proof.addClassName("mt-4");
        proof.addClassName("w-full");
        card.add(proof);
        disposables.add(Effect.create(() -> renderRoundTrip(proof)));

        return card;
    }

    /**
     * Builds a {@link Payment} on the browser and sends the whole thing up.
     *
     * <p>One object carrying all three shapes at once: fields declared on its base class, a list of
     * records, and a sealed value. What comes back is read apart again below, and what was found is
     * put on the screen.</p>
     */
    private void takePayment() {
        List<LineItem> lines = basket.get();
        if (lines.isEmpty()) {
            say("Put something in the basket first.", false);
            return;
        }
        PaymentMethod method = chosenMethod();
        if (method == null) {
            return;
        }

        Payment proposed = new Payment();
        proposed.setLines(new ArrayList<>(lines));
        proposed.setMethod(method);
        proposed.setNote(trimmed(note.getValue()));

        try {
            Payment recorded = payments.take(proposed);
            basket.set(new ArrayList<>());
            basketTotal.set(Money.zero());
            roundTrip.set(describe(recorded));
            say("Payment " + recorded.getId() + " recorded for "
                    + recorded.getAmount().formatted() + ".", true);
            refreshEverything();
        } catch (Exception refused) {
            say(refused.getMessage(), false);
        }
    }

    /**
     * Reads the payment the server sent back and says, in words, what shape each part arrived in.
     *
     * <p>This is the browser half of the proof. The server logs what it received; this says what
     * came back, and it can only say it by asking the values themselves.</p>
     *
     * @param recorded the payment as it came back
     * @return one line per thing worth checking
     */
    private List<String> describe(Payment recorded) {
        List<String> found = new ArrayList<>();
        found.add("Came back as " + recorded.getClass().getSimpleName()
                + ", a class that extends LedgerEntry.");
        found.add("From the base class: id " + recorded.getId()
                + ", recorded at " + timeOf(recorded)
                + ", note \"" + safe(recorded.getNote()) + "\".");
        found.add("Amount is a Money record: " + recorded.getAmount().formatted() + ".");
        found.add(recorded.getLines().size()
                + " line(s), each a LineItem record holding a Money record.");
        found.add("Paid by: " + detailOf(recorded.getMethod()) + ".");
        return found;
    }

    private void renderRoundTrip(Div container) {
        container.removeAll();
        List<String> lines = roundTrip.get();
        if (lines.isEmpty()) {
            return;
        }
        VerticalLayout box = new VerticalLayout();
        box.addClassName("gap-1");
        box.addClassName("bg-base-300");
        box.addClassName("rounded-box");
        box.addClassName("p-3");

        Span heading = new Span("What came back");
        TextStyle.SECTION_TITLE.applyTo(heading);
        box.add(heading);
        for (String line : lines) {
            Span item = new Span(line);
            TextStyle.CAPTION.applyTo(item);
            box.add(item);
        }
        container.add(box);
    }

    /**
     * Builds the sealed value for whichever kind is chosen.
     *
     * @return the payment method, or null when a field is not filled in properly
     */
    private PaymentMethod chosenMethod() {
        String chosen = methodKind.get();
        if ("Cash".equals(chosen)) {
            long cents = parseEuros(cashHandedOver.getValue());
            if (cents < 0) {
                say("Write the cash handed over in euros, like 10.00.", false);
                return null;
            }
            return new CashPayment(Money.of(cents));
        }
        if ("Bank transfer".equals(chosen)) {
            String reference = trimmed(transferReference.getValue());
            if (reference.isEmpty()) {
                say("A bank transfer needs a reference.", false);
                return null;
            }
            return new BankTransfer(reference);
        }
        if ("Gift card".equals(chosen)) {
            String number = trimmed(giftCardNumber.getValue());
            long remaining = parseEuros(giftCardRemaining.getValue());
            if (number.isEmpty() || remaining < 0) {
                say("A gift card needs its number and what is left on it.", false);
                return null;
            }
            return new GiftCard(number, Money.of(remaining));
        }
        String scheme = trimmed(cardScheme.getValue());
        String last4 = trimmed(cardLast4.getValue());
        if (scheme.isEmpty() || last4.isEmpty()) {
            say("A card payment needs the scheme and the last four digits.", false);
            return null;
        }
        return new CardPayment(scheme, last4);
    }

    // ------------------------------------------------------------------ the ledger

    private Component ledgerCard() {
        Card card = new Card();
        card.addClassName("flex-1");
        card.addClassName("min-w-[22rem]");
        card.addClassName("bg-base-200");
        card.add(new CardTitle("The ledger"));

        Div rows = new Div();
        rows.addClassName("w-full");
        card.add(rows);
        disposables.add(Effect.create(() -> renderLedger(rows)));
        return card;
    }

    /**
     * Draws one row per entry.
     *
     * <p>The list came back declared as {@code List<LedgerEntry>} — an abstract sealed base — and
     * every row is asked what it really is. That question is the whole point: with a kind field and
     * a cast this code would compile whatever arrived and go wrong quietly.</p>
     *
     * @param container where the rows go
     */
    private void renderLedger(Div container) {
        container.removeAll();
        List<LedgerEntry> entries = ledger.get();
        if (entries.isEmpty()) {
            container.add(new EmptyState("receipt", "Nothing yet",
                    "Take a payment and it will appear here."));
            return;
        }

        for (int index = entries.size() - 1; index >= 0; index--) {
            LedgerEntry entry = entries.get(index);

            VerticalLayout row = new VerticalLayout();
            row.addClassName("gap-1");
            row.addClassName("border-b");
            row.addClassName("border-base-300");
            row.addClassName("py-2");

            if (entry instanceof Payment payment) {
                HorizontalLayout top = new HorizontalLayout();
                top.addClassName("justify-between");
                top.addClassName("items-center");
                top.addClassName("gap-2");

                Span headline = new Span("Payment " + payment.getId()
                        + "   " + payment.getAmount().formatted());
                TextStyle.SECTION_TITLE.applyTo(headline);
                top.add(headline);

                Button refund = new Button("Refund");
                refund.addClassName("btn-outline");
                refund.addClassName("btn-xs");
                refund.addClickListener(event -> refund(payment.getId()));
                top.add(refund);
                row.add(top);

                row.add(caption("Paid by " + detailOf(payment.getMethod())));
                row.add(caption(basketOf(payment)));
            } else if (entry instanceof Refund refund) {
                Span headline = new Span("Refund " + refund.getId()
                        + "   " + refund.getAmount().formatted());
                TextStyle.SECTION_TITLE.applyTo(headline);
                row.add(headline);
                row.add(caption("Against payment " + refund.getAgainstPaymentId()));
            } else {
                // Unreachable while the sealed set holds. It is here because an example that
                // pretends the impossible cannot happen teaches the wrong habit.
                row.add(caption("An entry of an unexpected kind."));
            }

            // The four fields every entry has, all of them declared on the base class.
            row.add(caption("At " + timeOf(entry) + " - note: \"" + safe(entry.getNote()) + "\""));
            container.add(row);
        }
    }

    private void refund(String paymentId) {
        try {
            Refund given = payments.refund(paymentId, "Customer changed their mind");
            say("Refund " + given.getId() + " gave back " + given.getAmount().formatted()
                    + " against " + given.getAgainstPaymentId() + ".", true);
            refreshEverything();
        } catch (Exception refused) {
            say(refused.getMessage(), false);
        }
    }

    // ------------------------------------------------------------------ shared bits

    private void refreshEverything() {
        try {
            ledger.set(new ArrayList<>(payments.ledger()));
            summary.set(payments.summary());
        } catch (Exception failed) {
            say("Could not read the till: " + failed.getMessage(), false);
        }
    }

    private void say(String text, boolean good) {
        messageIsGood.set(good);
        message.set(text == null ? "" : text);
    }

    private static Span caption(String text) {
        Span span = new Span(text);
        TextStyle.CAPTION.applyTo(span);
        return span;
    }

    /**
     * Names the kind a sealed value really is, and shows what that kind carries.
     *
     * @param method the payment method that came off the wire
     * @return words describing it
     */
    private static String detailOf(PaymentMethod method) {
        if (method instanceof CardPayment card) {
            return "card - " + card.scheme() + " ending " + card.last4();
        }
        if (method instanceof CashPayment cash) {
            return "cash - " + cash.handedOver().formatted() + " handed over";
        }
        if (method instanceof BankTransfer transfer) {
            return "bank transfer - reference " + transfer.reference();
        }
        if (method instanceof GiftCard card) {
            return "gift card " + card.cardNumber() + " - "
                    + card.remainingAfter().formatted() + " left on it";
        }
        return "an unrecognized kind";
    }

    private static String basketOf(Payment payment) {
        StringBuilder text = new StringBuilder();
        for (LineItem line : payment.getLines()) {
            if (text.length() > 0) {
                text.append(", ");
            }
            text.append(line.quantity()).append(" x ").append(line.description());
        }
        return text.length() == 0 ? "nothing on it" : text.toString();
    }

    /**
     * The time part of an {@code Instant}, which is a field the base class declares.
     *
     * @param entry the entry
     * @return {@code hh:mm:ss}, or a dash when nothing arrived
     */
    private static String timeOf(LedgerEntry entry) {
        if (entry.getRecordedAt() == null) {
            return "-";
        }
        String text = entry.getRecordedAt().toString();
        int start = text.indexOf('T');
        return start >= 0 && text.length() >= start + 9 ? text.substring(start + 1, start + 9) : text;
    }

    /**
     * Reads an amount typed in euros.
     *
     * @param typed what the person wrote
     * @return the amount in cents, or -1 when it does not read as an amount
     */
    private static long parseEuros(String typed) {
        String text = trimmed(typed).replace(',', '.');
        if (text.isEmpty()) {
            return -1;
        }
        try {
            int dot = text.indexOf('.');
            if (dot < 0) {
                return Long.parseLong(text) * 100;
            }
            String whole = text.substring(0, dot);
            String fraction = text.substring(dot + 1);
            if (whole.isEmpty()) {
                whole = "0";
            }
            if (fraction.length() == 1) {
                fraction = fraction + "0";
            }
            if (fraction.length() != 2) {
                return -1;
            }
            return Long.parseLong(whole) * 100 + Long.parseLong(fraction);
        } catch (NumberFormatException notANumber) {
            return -1;
        }
    }

    private static String trimmed(String text) {
        return text == null ? "" : text.trim();
    }

    private static String safe(String text) {
        return text == null ? "" : text;
    }

    /** Releases every Effect this view created. */
    public void dispose() {
        for (Disposable disposable : disposables) {
            disposable.dispose();
        }
        disposables.clear();
    }
}
