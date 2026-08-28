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
package com.zeroz4j.ui.domproof;

import com.zeroz4j.api.validation.FieldRule;
import com.zeroz4j.ui.binding.Binder;
import com.zeroz4j.ui.component.AbstractField;
import com.zeroz4j.ui.component.Checkbox;
import com.zeroz4j.ui.component.FileInput;
import com.zeroz4j.ui.component.RadioButtonGroup;
import com.zeroz4j.ui.component.Range;
import com.zeroz4j.ui.component.Rating;
import com.zeroz4j.ui.component.Select;
import com.zeroz4j.ui.component.Swap;
import com.zeroz4j.ui.component.TextArea;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.component.ThemeController;
import com.zeroz4j.ui.component.Toggle;

import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Builds a page of every form field in a real browser and asks the browser what a person would
 * see. Compiled to JavaScript by TeaVM and run by {@code FieldDomProofTest} in headless Chrome.
 *
 * <p><b>Why this exists.</b> The consuming application's report of 0.7.0 said validation "wrote its
 * error message somewhere invisible and stopped - the box went red and the reason was thrown
 * away". That was true, and a unit test asking a field for its error message would have passed
 * throughout: the message existed as a value, it was simply never put anywhere a person could
 * read it. So every check here ends at the same question - <em>is this sentence part of the text
 * on the screen</em> - answered by {@code document.body.innerText}, which contains no text that is
 * detached, hidden, or never inserted.</p>
 *
 * <p>Each field is proved twice, because the two orders fail differently: once captioned before it
 * is put on the page, and once put on the page bare and given its message and caption afterwards.
 * The second is the case the report describes, and the one where a group built too late could
 * leave the control behind.</p>
 */
public final class FieldDomProof {

    private static HTMLDocument doc;
    private static HTMLElement stage;
    private static HTMLElement results;

    private FieldDomProof() {
    }

    public static void main(String[] args) {
        doc = Window.current().getDocument();

        stage = doc.createElement("div");
        stage.setAttribute("id", "stage");
        stage.getStyle().setProperty("padding", "1rem");
        doc.getBody().appendChild(stage);

        // Hidden, so the report of what is on the screen never becomes part of the screen.
        results = doc.createElement("pre");
        results.setAttribute("id", "proof-results");
        results.getStyle().setProperty("display", "none");
        doc.getBody().appendChild(results);

        try {
            proveEveryField();
            proveLateArrivals();
            proveValidationOnEdit();
            proveBinder();
        } catch (Throwable t) {
            line("FAIL|harness threw while building|" + t.getClass().getName()
                    + ": " + t.getMessage());
        }
        runNextStep();
    }

    // -----------------------------------------------------------------
    // Steps, because a typed character is not answered in the same breath
    // -----------------------------------------------------------------

    private static final List<Runnable> steps = new ArrayList<>();

    /**
     * Queues one piece of the proof, to be run a browser turn after the piece before it.
     *
     * <p>This is not tidiness, it is correctness. Every field's own listener runs on a green
     * thread, so a value typed into a control is not yet a value the component has seen when the
     * next Java statement runs. A check written straight after the keystroke reads the state
     * before the edit and reports a working field as broken - which is what this harness did on
     * its first run.</p>
     */
    private static void step(Runnable piece) {
        steps.add(piece);
    }

    private static void runNextStep() {
        if (steps.isEmpty()) {
            line("PASS|harness completed|");
            return;
        }
        Runnable piece = steps.remove(0);
        try {
            piece.run();
        } catch (Throwable t) {
            line("FAIL|harness threw|" + t.getClass().getName() + ": " + t.getMessage());
        }
        Window.setTimeout(FieldDomProof::runNextStep, 1);
    }

    // -----------------------------------------------------------------
    // Group 1: a field captioned before it reaches the page
    // -----------------------------------------------------------------

    private static void proveEveryField() {
        for (String kind : KINDS) {
            AbstractField<?, ?> field = make(kind);
            String caption = kind + " caption";
            String helper = kind + " explains itself here";

            field.setLabel(caption);
            field.setHelperText(helper);
            field.setRequiredIndicatorVisible(true);

            HTMLElement row = row(kind + "-early");
            row.appendChild(field.getOuterElement());

            String id = idOf(field);
            check(kind + " early: control has an id", id != null && !id.isEmpty(), "id=" + id);

            HTMLElement labelEl = labelFor(field, id);
            check(kind + " early: caption is in the document", Browser.attached(labelEl), "");
            check(kind + " early: caption is visible", Browser.visible(labelEl), "");
            check(kind + " early: caption reads what was set",
                    Browser.textOf(labelEl).indexOf(caption) >= 0,
                    "saw '" + Browser.textOf(labelEl) + "'");
            check(kind + " early: caption is on the screen",
                    Browser.pageText().indexOf(caption) >= 0, "");

            proveAssociation(kind + " early", field, id, labelEl);

            HTMLElement helpEl = Browser.byId(id + "-help");
            check(kind + " early: helper text is visible", Browser.visible(helpEl), "");
            check(kind + " early: helper text is on the screen",
                    Browser.pageText().indexOf(helper) >= 0, "");
            check(kind + " early: helper text is announced with the field",
                    describedBy(field).indexOf(id + "-help") >= 0,
                    "aria-describedby=" + describedBy(field));

            check(kind + " early: required mark is visible",
                    Browser.visible(requiredMark(labelEl)), "");
        }
    }

    /**
     * The caption has to be the field's name to assistive technology, not merely words placed
     * above it. There are two correct ways to say that and a field uses whichever fits: a
     * {@code <label for>} pointing at a single control, or - for a group of controls, where there
     * is no one control to point at - a named group.
     */
    private static void proveAssociation(String what, AbstractField<?, ?> field,
                                         String id, HTMLElement labelEl) {
        String tag = Browser.tagOf(labelEl);
        if ("label".equals(tag)) {
            check(what + ": caption is a label bound to the control",
                    id.equals(labelEl.getAttribute("for")), "for=" + labelEl.getAttribute("for"));
            if (!(field instanceof FileInput)) {
                // A file input's label opens the operating system's file chooser when clicked,
                // which is not something to do in a test.
                Browser.click(labelEl);
                check(what + ": clicking the caption focuses the field",
                        Browser.same(Browser.focused(), control(field)),
                        "focus went to <" + Browser.tagOf(Browser.focused()) + ">");
            }
        } else {
            check(what + ": group is announced as a group",
                    "group".equals(control(field).getAttribute("role")),
                    "role=" + control(field).getAttribute("role"));
            check(what + ": group is named by its caption",
                    (id + "-label").equals(control(field).getAttribute("aria-labelledby")),
                    "aria-labelledby=" + control(field).getAttribute("aria-labelledby"));
        }
    }

    // -----------------------------------------------------------------
    // Group 2: the reported case - a bare field on a live page that is
    // later told it is wrong, and later still given its name
    // -----------------------------------------------------------------

    private static void proveLateArrivals() {
        for (String kind : KINDS) {
            AbstractField<?, ?> field = make(kind);
            HTMLElement row = row(kind + "-late");

            HTMLElement before = doc.createElement("span");
            before.setTextContent("[before]");
            HTMLElement after = doc.createElement("span");
            after.setTextContent("[after]");
            row.appendChild(before);
            row.appendChild(field.getOuterElement());
            row.appendChild(after);

            check(kind + " late: starts as a bare control",
                    Browser.same(field.getOuterElement(), field.getElement()), "");
            int placeBefore = Browser.indexAmongSiblings(field.getElement());

            String message = kind + " will not be accepted like that";
            field.setErrorMessage(message);

            check(kind + " late: the message is on the screen",
                    Browser.pageText().indexOf(message) >= 0,
                    "page text has no such sentence");
            HTMLElement errorEl = Browser.byId(idOf(field) + "-error");
            check(kind + " late: the message element is visible",
                    Browser.visible(errorEl), "");
            check(kind + " late: the field is marked invalid",
                    "true".equals(control(field).getAttribute("aria-invalid")),
                    "aria-invalid=" + control(field).getAttribute("aria-invalid"));
            check(kind + " late: the message is announced with the field",
                    describedBy(field).indexOf(idOf(field) + "-error") >= 0,
                    "aria-describedby=" + describedBy(field));

            String caption = kind + " named afterwards";
            field.setLabel(caption);
            HTMLElement labelEl = labelFor(field, idOf(field));
            check(kind + " late: a caption set afterwards is visible",
                    Browser.visible(labelEl), "");
            check(kind + " late: a caption set afterwards is on the screen",
                    Browser.pageText().indexOf(caption) >= 0, "");
            proveAssociation(kind + " late", field, idOf(field), labelEl);

            int placeAfter = Browser.indexAmongSiblings(field.getOuterElement());
            check(kind + " late: the field keeps its place among its neighbours",
                    placeBefore == placeAfter,
                    "was child " + placeBefore + ", now child " + placeAfter);
            check(kind + " late: the neighbours are still either side",
                    Browser.indexAmongSiblings(before) == 0
                            && Browser.indexAmongSiblings(after) == 2,
                    "before=" + Browser.indexAmongSiblings(before)
                            + " after=" + Browser.indexAmongSiblings(after));

            field.setErrorMessage(null);
            check(kind + " late: clearing takes the message off the screen",
                    Browser.pageText().indexOf(message) < 0, "the sentence is still readable");
            check(kind + " late: clearing removes the invalid marking",
                    control(field).getAttribute("aria-invalid") == null
                            || control(field).getAttribute("aria-invalid").isEmpty(),
                    "aria-invalid=" + control(field).getAttribute("aria-invalid"));
            check(kind + " late: the caption survives clearing",
                    Browser.pageText().indexOf(caption) >= 0, "");
        }
    }

    // -----------------------------------------------------------------
    // Group 3: a real edit that breaks a rule
    // -----------------------------------------------------------------

    private static void proveValidationOnEdit() {
        // TextField and TextArea: too short, then long enough.
        TextField text = new TextField();
        editCase("TextField", text, minLength(3), () -> {
            Browser.typeInto(text.getElement(), "ab");
            Browser.fire(text.getElement(), "input");
        }, () -> {
            Browser.typeInto(text.getElement(), "abcd");
            Browser.fire(text.getElement(), "input");
        });

        TextArea area = new TextArea();
        editCase("TextArea", area, minLength(3), () -> {
            Browser.typeInto(area.getElement(), "ab");
            Browser.fire(area.getElement(), "input");
        }, () -> {
            Browser.typeInto(area.getElement(), "abcd");
            Browser.fire(area.getElement(), "input");
        });

        Select select = new Select();
        select.setItems(Arrays.asList("", "red"));
        editCase("Select", select, notBlank(), () -> {
            Browser.typeInto(select.getElement(), "");
            Browser.fire(select.getElement(), "change");
        }, () -> {
            Browser.typeInto(select.getElement(), "red");
            Browser.fire(select.getElement(), "change");
        });

        // Ticked to start with, so that unticking it is a change the field notices.
        Checkbox box = new Checkbox();
        box.setValue(true);
        editCase("Checkbox", box, mustBeTicked(), () -> {
            Browser.tick(box.getElement(), false);
            Browser.fire(box.getElement(), "change");
        }, () -> {
            Browser.tick(box.getElement(), true);
            Browser.fire(box.getElement(), "change");
        });

        Toggle toggle = new Toggle();
        toggle.setValue(true);
        editCase("Toggle", toggle, mustBeTicked(), () -> {
            Browser.tick(toggle.getElement(), false);
            Browser.fire(toggle.getElement(), "change");
        }, () -> {
            Browser.tick(toggle.getElement(), true);
            Browser.fire(toggle.getElement(), "change");
        });

        Range range = new Range();
        editCase("Range", range, atLeast(50.0), () -> {
            Browser.typeInto(range.getElement(), "10");
            Browser.fire(range.getElement(), "input");
        }, () -> {
            Browser.typeInto(range.getElement(), "80");
            Browser.fire(range.getElement(), "input");
        });

        RadioButtonGroup radios = new RadioButtonGroup("proof-radios");
        radios.setItems(Arrays.asList("yes", "no"));
        editCase("RadioButtonGroup", radios, mustEqual("yes"),
                () -> pickRadio(radios.getElement(), "no"),
                () -> pickRadio(radios.getElement(), "yes"));

        Rating rating = new Rating();
        editCase("Rating", rating, atLeastStars(4),
                () -> pickRadio(rating.getElement(), "2"),
                () -> pickRadio(rating.getElement(), "5"));

        Swap swap = new Swap();
        swap.setValue(true);
        HTMLElement swapBox = control(swap);
        editCase("Swap", swap, mustBeTicked(), () -> {
            Browser.tick(swapBox, false);
            Browser.fire(swapBox, "change");
        }, () -> {
            Browser.tick(swapBox, true);
            Browser.fire(swapBox, "change");
        });

        ThemeController theme = new ThemeController();
        theme.setValue(true);
        HTMLElement themeBox = control(theme);
        editCase("ThemeController", theme, mustBeTicked(), () -> {
            Browser.tick(themeBox, false);
            Browser.fire(themeBox, "change");
        }, () -> {
            Browser.tick(themeBox, true);
            Browser.fire(themeBox, "change");
        });
    }

    private static void pickRadio(HTMLElement group, String value) {
        HTMLElement radio = group.querySelector("input[value=\"" + value + "\"]");
        Browser.tick(radio, true);
        Browser.fire(radio, "change");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static void editCase(String kind, AbstractField field, FieldRule rule,
                                 Runnable breakIt, Runnable fixIt) {
        String caption = kind + " under a rule";
        field.setLabel(caption);
        row(kind + "-rule").appendChild(field.getOuterElement());
        field.withRule(rule);

        step(breakIt);
        step(() -> {
            check(kind + " rule: the reason is on the screen",
                    Browser.pageText().indexOf(RULE_MESSAGE) >= 0,
                    "the field went invalid without saying why");
            check(kind + " rule: the field is marked invalid",
                    "true".equals(control(field).getAttribute("aria-invalid")),
                    "aria-invalid=" + control(field).getAttribute("aria-invalid"));
            check(kind + " rule: the control is styled as wrong",
                    field.getElement().getAttribute("class").indexOf("input-error") >= 0,
                    "class=" + field.getElement().getAttribute("class"));
        });

        step(fixIt);
        step(() -> {
            check(kind + " rule: fixing it takes the reason off the screen",
                    Browser.pageText().indexOf(RULE_MESSAGE) < 0,
                    "the sentence is still readable");
            check(kind + " rule: fixing it removes the invalid marking",
                    control(field).getAttribute("aria-invalid") == null
                            || control(field).getAttribute("aria-invalid").isEmpty(),
                    "aria-invalid=" + control(field).getAttribute("aria-invalid"));
            // The caption is not a casualty of the round trip.
            check(kind + " rule: the caption is still on the screen",
                    Browser.pageText().indexOf(caption) >= 0, "");
        });
    }

    // -----------------------------------------------------------------
    // Group 4: the same thing through Binder, which is how forms are written
    // -----------------------------------------------------------------

    private static void proveBinder() {
        TextField name = new TextField().withLabel("Binder caption");
        row("binder").appendChild(name.getOuterElement());

        Binder<Person> binder = new Binder<>();
        binder.forField(name)
                .asRequired(BINDER_MESSAGE)
                .bind(Person::getName, Person::setName);

        HTMLElement labelEl = labelFor(name, idOf(name));
        check("Binder: asRequired marks the caption",
                Browser.visible(requiredMark(labelEl)), "");

        Person person = new Person();
        binder.setBean(person);
        binder.validate();

        check("Binder: the reason a required field failed is on the screen",
                Browser.pageText().indexOf(BINDER_MESSAGE) >= 0,
                "the form went red and said nothing");
        check("Binder: the field is marked invalid",
                "true".equals(control(name).getAttribute("aria-invalid")),
                "aria-invalid=" + control(name).getAttribute("aria-invalid"));

        step(() -> {
            Browser.typeInto(name.getElement(), "Ada");
            Browser.fire(name.getElement(), "input");
        });
        step(() -> {
            check("Binder: filling it in takes the reason off the screen",
                    Browser.pageText().indexOf(BINDER_MESSAGE) < 0,
                    "the sentence is still readable");
            check("Binder: the value reached the bean", "Ada".equals(person.getName()),
                    "bean holds '" + person.getName() + "'");
        });
    }

    /** A plain bean, because Binder edits domain objects. */
    public static class Person {
        private String name;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    // -----------------------------------------------------------------
    // Rules
    // -----------------------------------------------------------------

    static final String RULE_MESSAGE = "That answer cannot be used";
    static final String BINDER_MESSAGE = "A name is needed before this can be saved";

    private static List<String> broken() {
        List<String> out = new ArrayList<>();
        out.add(RULE_MESSAGE);
        return out;
    }

    private static FieldRule<String> minLength(int min) {
        return value -> value != null && value.length() >= min
                ? Collections.emptyList() : broken();
    }

    private static FieldRule<String> notBlank() {
        return value -> value != null && !value.trim().isEmpty()
                ? Collections.emptyList() : broken();
    }

    private static FieldRule<String> mustEqual(String wanted) {
        return value -> wanted.equals(value) ? Collections.emptyList() : broken();
    }

    private static FieldRule<Boolean> mustBeTicked() {
        return value -> value != null && value ? Collections.emptyList() : broken();
    }

    private static FieldRule<Double> atLeast(double min) {
        return value -> value != null && value >= min ? Collections.emptyList() : broken();
    }

    private static FieldRule<Integer> atLeastStars(int min) {
        return value -> value != null && value >= min ? Collections.emptyList() : broken();
    }

    // -----------------------------------------------------------------
    // Plumbing
    // -----------------------------------------------------------------

    private static final String[] KINDS = {
            "TextField", "TextArea", "Select", "Checkbox", "Toggle",
            "RadioButtonGroup", "Range", "Rating", "FileInput", "Swap", "ThemeController"
    };

    private static AbstractField<?, ?> make(String kind) {
        if ("TextField".equals(kind)) {
            return new TextField();
        }
        if ("TextArea".equals(kind)) {
            return new TextArea();
        }
        if ("Select".equals(kind)) {
            Select select = new Select();
            select.setItems(Arrays.asList("one", "two"));
            return select;
        }
        if ("Checkbox".equals(kind)) {
            return new Checkbox();
        }
        if ("Toggle".equals(kind)) {
            return new Toggle();
        }
        if ("RadioButtonGroup".equals(kind)) {
            RadioButtonGroup group = new RadioButtonGroup("proof-" + kind + counter());
            group.setItems(Arrays.asList("one", "two"));
            return group;
        }
        if ("Range".equals(kind)) {
            return new Range();
        }
        if ("Rating".equals(kind)) {
            return new Rating();
        }
        if ("FileInput".equals(kind)) {
            return new FileInput();
        }
        if ("Swap".equals(kind)) {
            return new Swap();
        }
        if ("ThemeController".equals(kind)) {
            return new ThemeController();
        }
        throw new IllegalArgumentException(kind);
    }

    private static int rows;

    private static int counter() {
        return ++rows;
    }

    private static HTMLElement row(String name) {
        HTMLElement el = doc.createElement("div");
        el.setAttribute("data-row", name);
        el.getStyle().setProperty("margin", "1rem 0");
        stage.appendChild(el);
        return el;
    }

    /**
     * The element that behaves as the form control. Usually the field's own element; for a swap
     * or a theme switch, whose own element is a {@code <label>} wrapping a hidden checkbox, it is
     * the checkbox - and that is the element a caption has to name.
     */
    private static HTMLElement control(AbstractField<?, ?> field) {
        HTMLElement own = field.getElement();
        if ("label".equals(Browser.tagOf(own))) {
            HTMLElement inner = own.querySelector("input");
            if (inner != null) {
                return inner;
            }
        }
        return own;
    }

    private static String idOf(AbstractField<?, ?> field) {
        return control(field).getAttribute("id");
    }

    private static String describedBy(AbstractField<?, ?> field) {
        String value = control(field).getAttribute("aria-describedby");
        return value == null ? "" : value;
    }

    /** The caption element, found the way a browser finds it rather than from a Java field. */
    private static HTMLElement labelFor(AbstractField<?, ?> field, String id) {
        HTMLElement byFor = Browser.query("label[for=\"" + id + "\"]");
        return byFor != null ? byFor : Browser.byId(id + "-label");
    }

    private static HTMLElement requiredMark(HTMLElement labelEl) {
        return labelEl == null ? null : labelEl.querySelector("[aria-hidden=\"true\"]");
    }

    private static void check(String name, boolean ok, String detail) {
        line((ok ? "PASS|" : "FAIL|") + name + "|" + detail);
    }

    private static void line(String text) {
        HTMLElement el = doc.createElement("div");
        el.setClassName("proof-line");
        el.setTextContent(text);
        results.appendChild(el);
    }
}
