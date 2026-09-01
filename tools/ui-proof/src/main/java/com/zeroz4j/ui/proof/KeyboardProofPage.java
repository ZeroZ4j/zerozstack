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
package com.zeroz4j.ui.proof;

import com.zeroz4j.api.validation.FieldRule;
import com.zeroz4j.ui.binding.Binder;
import com.zeroz4j.ui.component.AbstractField;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.Checkbox;
import com.zeroz4j.ui.component.CodeBlock;
import com.zeroz4j.ui.component.ContextMenu;
import com.zeroz4j.ui.component.Dialog;
import com.zeroz4j.ui.component.DiffView;
import com.zeroz4j.ui.component.Drawer;
import com.zeroz4j.ui.component.Dropdown;
import com.zeroz4j.ui.component.FileInput;
import com.zeroz4j.ui.component.FileUpload;
import com.zeroz4j.ui.component.LaneTimeline;
import com.zeroz4j.ui.component.Login;
import com.zeroz4j.ui.component.Menu;
import com.zeroz4j.ui.component.PropertyGrid;
import com.zeroz4j.ui.component.RadioButtonGroup;
import com.zeroz4j.ui.component.Range;
import com.zeroz4j.ui.component.Rating;
import com.zeroz4j.ui.component.Resizer;
import com.zeroz4j.ui.component.Select;
import com.zeroz4j.ui.component.SplitPane;
import com.zeroz4j.ui.component.SvgCanvas;
import com.zeroz4j.ui.component.Swap;
import com.zeroz4j.ui.component.Tab;
import com.zeroz4j.ui.component.TextArea;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.component.ThemeController;
import com.zeroz4j.ui.component.Toast;
import com.zeroz4j.ui.component.Toggle;
import com.zeroz4j.ui.component.Tooltip;
import com.zeroz4j.ui.layout.Div;

import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The one page the browser proof is driven against.
 *
 * <p>Every control on it is a real component from {@code zerozstack-ui-components}, compiled to
 * JavaScript exactly as an application's would be. Nothing here is hand-written HTML, which is the
 * point: a page of hand-written HTML would prove that the markup behaves, not that the library
 * produces that markup.</p>
 *
 * <p>Driven by {@code tools/ui-proof/drive.mjs}. <b>Element ids are the contract between the
 * two</b>; changing one means changing both.</p>
 *
 * <h2>Three sections, in this order</h2>
 *
 * <ol>
 *   <li><b>Keyboard.</b> One instance of every component a person can operate, bracketed by
 *       {@code kb-before} and {@code kb-after} so the driver can Tab through the lot and write
 *       down what it reached and what it never reached. It is first on the page only so that the
 *       walk is short: the fields section below puts dozens of controls in the tab order.</li>
 *   <li><b>Overlays.</b> Dialogs, a drawer, a menu, a tooltip and a message. This was its own
 *       page, {@code OverlayProofPage}, until the two harnesses were merged into one.</li>
 *   <li><b>Fields.</b> Every form field, captioned, explained and refused, checking itself and
 *       writing one verdict line per check into a hidden {@code <pre id="proof-results">}. This
 *       was its own harness, {@code FieldDomProof}, which ran under Chrome's {@code --dump-dom}
 *       and so could never press a key.</li>
 * </ol>
 *
 * <h2>Ids the driver uses</h2>
 *
 * <p>Where a component's own element is the thing you operate, it simply carries the id. Where the
 * thing you operate is built inside the component - the copy button in a code block, the divider
 * in a split pane, the scrub strip in a timeline - the id is put on that inner element after the
 * component is built, because that is the element a person's keyboard actually arrives at. Both
 * cases use the same name, {@code kb-<component>}, so the driver never has to know which is
 * which.</p>
 *
 * <p>Dialog, Drawer and Dropdown are <b>not</b> rebuilt in the keyboard section. They already
 * exist in the overlays section and are driven there, at their own ids ({@code open-default},
 * {@code open-drawer}, {@code dd-1}). A second copy would prove nothing the first does not.
 * ContextMenu was <b>not</b> in the overlays section despite belonging with them, so it is built
 * here, in the keyboard section, as {@code kb-contextmenu}.</p>
 */
public final class KeyboardProofPage {

    private static HTMLDocument doc;

    /** Where the fields section writes its verdicts. The driver reads it line by line. */
    private static HTMLElement results;

    /** The fields section's stage, kept in a field because its rows are built from everywhere. */
    private static HTMLElement fieldStage;

    private KeyboardProofPage() {
    }

    public static void main(String[] args) {
        doc = Window.current().getDocument();
        HTMLElement root = doc.getElementById("app-root");

        // Hidden, so the report of what is on the screen never becomes part of the screen.
        results = doc.createElement("pre");
        results.setAttribute("id", "proof-results");
        results.getStyle().setProperty("display", "none");
        doc.getBody().appendChild(results);

        HTMLElement keyboard = section(root, "Keyboard", "section-keyboard");
        HTMLElement overlays = section(root, "Overlays", "section-overlays");
        HTMLElement fields = section(root, "Fields", "section-fields");

        try {
            buildKeyboardSection(keyboard);
        } catch (Throwable t) {
            line("FAIL|keyboard section threw while building|" + t.getClass().getName()
                    + ": " + t.getMessage());
        }
        try {
            buildOverlaysSection(overlays);
        } catch (Throwable t) {
            line("FAIL|overlays section threw while building|" + t.getClass().getName()
                    + ": " + t.getMessage());
        }

        fieldStage = fields;
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

    private static HTMLElement section(HTMLElement root, String heading, String id) {
        HTMLElement wrapper = doc.createElement("section");
        wrapper.setAttribute("id", id);
        wrapper.setClassName("p-8 flex flex-col gap-4 items-start border-b border-base-300");
        HTMLElement title = doc.createElement("h1");
        title.setClassName("text-xl font-bold");
        title.setTextContent(heading);
        wrapper.appendChild(title);
        root.appendChild(wrapper);
        return wrapper;
    }

    // =================================================================
    // Section 1: the keyboard
    // =================================================================

    /**
     * One instance of every component a person can operate, each one reachable, each one next to
     * a hidden span its own listener writes into.
     *
     * <p>The span is the whole trick. "Did pressing Enter do anything" cannot be answered by
     * looking at the page - plenty of controls change nothing visible - so each control reports
     * for itself, and the driver only has to notice that the words changed.</p>
     */
    private static void buildKeyboardSection(HTMLElement host) {
        Button before = new Button("Before the keyboard section");
        before.setId("kb-before");
        host.appendChild(before.getOuterElement());

        // -- Button ---------------------------------------------------
        Button button = new Button("Press me");
        button.setId("kb-button");
        rowFor(host, "Button", button.getOuterElement(), "kb-button");
        recordsOn(button.getElement(), "kb-button", "click");

        // -- Checkbox -------------------------------------------------
        Checkbox checkbox = new Checkbox();
        checkbox.setLabel("Send me the weekly summary");
        checkbox.setId("kb-checkbox");
        rowFor(host, "Checkbox", checkbox.getOuterElement(), "kb-checkbox");
        recordsOn(checkbox.getElement(), "kb-checkbox", "change");

        // -- CodeBlock ------------------------------------------------
        // The operable part is the copy button in its header, not the block itself.
        CodeBlock codeBlock = new CodeBlock("java", "int answer = 42;\nSystem.out.println(answer);");
        rowFor(host, "CodeBlock", codeBlock.getOuterElement(), "kb-codeblock");
        HTMLElement copyInCode = inner(codeBlock.getElement(), "button", "kb-codeblock");
        recordsOn(copyInCode, "kb-codeblock", "click");

        // -- ContextMenu ----------------------------------------------
        // Not in the overlays section, though it belongs with them, so it is built here. Its own
        // listener is on "contextmenu", which is what the span records: a menu that only a right
        // click opens has no keyboard way in, and this is where that shows.
        Button ctxTarget = new Button("Right-click for actions");
        ctxTarget.setId("kb-contextmenu");
        ContextMenu contextMenu = new ContextMenu();
        contextMenu.item("pencil", "Rename", () -> { });
        contextMenu.item("trash", "Delete", () -> { });
        contextMenu.attachTo(ctxTarget, null);
        rowFor(host, "ContextMenu", ctxTarget.getOuterElement(), "kb-contextmenu");
        recordsOn(ctxTarget.getElement(), "kb-contextmenu", "contextmenu");

        // -- DiffView -------------------------------------------------
        DiffView diffView = new DiffView("diff --git a/Greeting.java b/Greeting.java\n"
                + "--- a/Greeting.java\n"
                + "+++ b/Greeting.java\n"
                + "@@ -1,3 +1,3 @@\n"
                + " class Greeting {\n"
                + "-    String text = \"hi\";\n"
                + "+    String text = \"hello\";\n"
                + " }\n");
        rowFor(host, "DiffView", diffView.getOuterElement(), "kb-diffview");
        HTMLElement diffHeader = inner(diffView.getElement(), "button", "kb-diffview");
        recordsOn(diffHeader, "kb-diffview", "click");

        // -- FileInput ------------------------------------------------
        FileInput fileInput = new FileInput();
        fileInput.setLabel("Choose a file");
        fileInput.setId("kb-fileinput");
        rowFor(host, "FileInput", fileInput.getOuterElement(), "kb-fileinput");
        recordsOn(fileInput.getElement(), "kb-fileinput", "click", "change");

        // -- FileUpload -----------------------------------------------
        FileUpload fileUpload = new FileUpload();
        fileUpload.setTitle("Drop a file here");
        rowFor(host, "FileUpload", fileUpload.getOuterElement(), "kb-fileupload");
        inner(fileUpload.getElement(), "[role=\"button\"]", "kb-fileupload");
        // On the hidden file input, not on the box. Both the mouse path and the key path end at
        // the same place - the component opens the file chooser by clicking that input - and a
        // listener on the box would see the mouse click and never the key press.
        recordsOn(fileUpload.getElement().querySelector("input[type=\"file\"]"),
                "kb-fileupload", "click");

        // -- LaneTimeline ---------------------------------------------
        // Needs lanes: the strip a person scrubs is only drawn once there is a run to scrub.
        LaneTimeline timeline = new LaneTimeline();
        long start = 1700000000000L;
        timeline.setLanes(Arrays.asList(
                new LaneTimeline.Lane("worker-1", "ok", start, start + 60000L,
                        Arrays.asList(start + 10000L, start + 30000L)),
                new LaneTimeline.Lane("worker-2", "ok", start + 5000L, start + 55000L,
                        Arrays.asList(start + 20000L))));
        Div timelineBox = new Div();
        timelineBox.addClassName("w-[40rem]");
        timelineBox.add(timeline);
        rowFor(host, "LaneTimeline", timelineBox.getOuterElement(), "kb-lanetimeline");
        inner(timeline.getElement(), "[role=\"slider\"]", "kb-lanetimeline");
        // On the host, not on the strip. Every arrow key redraws the strip, which throws that
        // element away and builds another - taking any id and any listener on it with it. The
        // host survives, and keydown bubbles up to it. The driver knows the same thing and finds
        // the strip by role inside this row rather than by id after the first press.
        recordsOn(timeline.getElement(), "kb-lanetimeline", "keydown");

        // -- Login ----------------------------------------------------
        Login login = new Login("Sign in", (user, password) -> { });
        login.setHint("Anything will do here.");
        rowFor(host, "Login", login.getOuterElement(), "kb-login");
        HTMLElement signIn = inner(login.getElement(), "button", "kb-login");
        recordsOn(signIn, "kb-login", "click");

        // -- Menu -----------------------------------------------------
        Menu menu = new Menu();
        menu.addClassName("bg-base-200 rounded-box w-56");
        menu.addItem("Rename this file", e -> { });
        menu.addItem("Delete this file", e -> { });
        rowFor(host, "Menu", menu.getOuterElement(), "kb-menu-item");
        HTMLElement menuItem = inner(menu.getElement(), "li > *", "kb-menu-item");
        recordsOn(menuItem, "kb-menu-item", "click");

        // -- PropertyGrid ---------------------------------------------
        PropertyGrid grid = new PropertyGrid();
        grid.row("Host", "build-07");
        grid.row("Started", "09:41");
        rowFor(host, "PropertyGrid", grid.getOuterElement(), "kb-propertygrid");
        HTMLElement gridCopy = inner(grid.getElement(), "button", "kb-propertygrid");
        recordsOn(gridCopy, "kb-propertygrid", "click");

        // -- RadioButtonGroup -----------------------------------------
        RadioButtonGroup radios = new RadioButtonGroup("kb-radios");
        radios.setItems(Arrays.asList("Post", "Email"));
        radios.setLabel("How should we reach you");
        radios.setId("kb-radiobuttongroup-group");
        rowFor(host, "RadioButtonGroup", radios.getOuterElement(), "kb-radiobuttongroup");
        HTMLElement firstRadio = inner(radios.getElement(), "input", "kb-radiobuttongroup");
        recordsOn(firstRadio, "kb-radiobuttongroup", "change");

        // -- Range ----------------------------------------------------
        Range range = new Range();
        range.setLabel("How loud");
        range.setId("kb-range");
        rowFor(host, "Range", range.getOuterElement(), "kb-range");
        recordsOn(range.getElement(), "kb-range", "input");

        // -- Rating ---------------------------------------------------
        Rating rating = new Rating();
        rating.setLabel("How was it");
        rating.setId("kb-rating-group");
        rowFor(host, "Rating", rating.getOuterElement(), "kb-rating");
        HTMLElement firstStar = inner(rating.getElement(), "input", "kb-rating");
        recordsOn(firstStar, "kb-rating", "change");

        // -- Resizer --------------------------------------------------
        // Upright handle, so the left and right arrow keys are the ones that move it.
        Div resizeTarget = new Div("A panel you can resize");
        resizeTarget.addClassName("w-48 h-24 bg-base-200 p-2 overflow-hidden");
        Div resizeRow = new Div();
        resizeRow.addClassName("flex flex-row h-24 border border-base-300");
        Resizer resizer = new Resizer(resizeTarget, Resizer.Orientation.VERTICAL, false);
        resizer.setId("kb-resizer");
        Div resizeRest = new Div("and the rest of the row");
        resizeRest.addClassName("flex-1 p-2");
        resizeRow.add(resizeTarget, resizer, resizeRest);
        rowFor(host, "Resizer", resizeRow.getOuterElement(), "kb-resizer");
        recordsOn(resizer.getElement(), "kb-resizer", "keydown");

        // -- Select ---------------------------------------------------
        Select select = new Select();
        select.setItems(Arrays.asList("Red", "Green", "Blue"));
        select.setLabel("Pick a colour");
        select.setId("kb-select");
        rowFor(host, "Select", select.getOuterElement(), "kb-select");
        recordsOn(select.getElement(), "kb-select", "change");

        // -- SplitPane ------------------------------------------------
        // Side by side, so again the left and right arrow keys are the ones that move it.
        SplitPane split = SplitPane.horizontal("kb-proof", 160, 80, 320);
        split.setFirst(new Div("Left"));
        split.setSecond(new Div("Right"));
        Div splitBox = new Div();
        splitBox.addClassName("w-[32rem] h-24 border border-base-300");
        splitBox.add(split);
        rowFor(host, "SplitPane", splitBox.getOuterElement(), "kb-splitpane");
        HTMLElement divider = inner(split.getElement(), "[role=\"separator\"]", "kb-splitpane");
        recordsOn(divider, "kb-splitpane", "keydown");

        // -- SvgCanvas ------------------------------------------------
        SvgCanvas canvas = new SvgCanvas();
        canvas.setId("kb-svgcanvas");
        Div canvasBox = new Div();
        canvasBox.addClassName("w-[32rem] h-32 border border-base-300");
        canvasBox.add(canvas);
        rowFor(host, "SvgCanvas", canvasBox.getOuterElement(), "kb-svgcanvas");
        recordsOn(canvas.getElement(), "kb-svgcanvas", "keydown");

        // -- Swap -----------------------------------------------------
        // Its own element is a <label> around a hidden checkbox, so the checkbox is what is
        // driven, and the caption is repointed at it so the name still works.
        Swap swap = new Swap();
        swap.setLabel("Show the details");
        rowFor(host, "Swap", swap.getOuterElement(), "kb-swap");
        HTMLElement swapBox = renameControl(swap, "kb-swap");
        recordsOn(swapBox, "kb-swap", "change");

        // -- Tab ------------------------------------------------------
        Tab tab = new Tab("Overview");
        tab.setId("kb-tab");
        Div tabRow = new Div();
        tabRow.addClassName("tabs tabs-box");
        tabRow.add(tab, new Tab("History"));
        rowFor(host, "Tab", tabRow.getOuterElement(), "kb-tab");
        recordsOn(tab.getElement(), "kb-tab", "click");

        // -- TextArea -------------------------------------------------
        TextArea textArea = new TextArea();
        textArea.setLabel("Tell us what happened");
        textArea.setId("kb-textarea");
        rowFor(host, "TextArea", textArea.getOuterElement(), "kb-textarea");
        recordsOn(textArea.getElement(), "kb-textarea", "input");

        // -- TextField ------------------------------------------------
        TextField textField = new TextField();
        textField.setLabel("Your name");
        textField.setId("kb-textfield");
        rowFor(host, "TextField", textField.getOuterElement(), "kb-textfield");
        recordsOn(textField.getElement(), "kb-textfield", "input");

        // -- ThemeController ------------------------------------------
        ThemeController theme = new ThemeController();
        theme.setLabel("Dark");
        rowFor(host, "ThemeController", theme.getOuterElement(), "kb-themecontroller");
        HTMLElement themeBox = renameControl(theme, "kb-themecontroller");
        recordsOn(themeBox, "kb-themecontroller", "change");

        Button after = new Button("After the keyboard section");
        after.setId("kb-after");
        host.appendChild(after.getOuterElement());
    }

    /** A labelled row holding one component, with its hidden report span beside it. */
    private static void rowFor(HTMLElement host, String what, HTMLElement component, String id) {
        HTMLElement row = doc.createElement("div");
        row.setAttribute("data-kb-row", id);
        row.setClassName("flex flex-col gap-1 w-full max-w-3xl");

        HTMLElement caption = doc.createElement("div");
        caption.setClassName("text-xs opacity-60");
        caption.setTextContent(what);
        row.appendChild(caption);
        row.appendChild(component);

        HTMLElement fired = doc.createElement("span");
        fired.setAttribute("id", id + "-fired");
        fired.getStyle().setProperty("display", "none");
        fired.setTextContent("");
        row.appendChild(fired);

        host.appendChild(row);
    }

    private static int firings;

    /**
     * Makes one control report that it did something.
     *
     * <p>Every listener writes the same shape of words and a number that never repeats, so the
     * driver can say "this changed" without knowing anything about what the control does.</p>
     */
    private static void recordsOn(HTMLElement el, String id, String... events) {
        if (el == null) {
            line("FAIL|" + id + " has no element to drive|the component built nothing to press");
            return;
        }
        for (String type : events) {
            final String eventName = type;
            el.addEventListener(eventName, (org.teavm.jso.dom.events.EventListener<Event>) evt -> {
                HTMLElement span = doc.getElementById(id + "-fired");
                if (span != null) {
                    span.setTextContent(eventName + " " + (++firings));
                }
            });
        }
    }

    /**
     * Finds the element inside a component that a person actually operates, and names it.
     *
     * <p>A code block is not pressed; the copy button in its header is. A split pane is not
     * dragged; the divider between its halves is. The driver has to arrive at that element, so
     * that is the element the id goes on.</p>
     */
    private static HTMLElement inner(HTMLElement componentElement, String selector, String id) {
        HTMLElement found = componentElement.querySelector(selector);
        if (found == null) {
            line("FAIL|" + id + " is missing its control|nothing matched '" + selector + "'");
            return null;
        }
        found.setAttribute("id", id);
        return found;
    }

    /**
     * Renames the control a field's caption points at, and repoints the caption at the new name.
     *
     * <p>Needed for a swap and a theme switch, whose own element is a {@code <label>} wrapping the
     * checkbox that does the work. {@code setId} on those deliberately leaves the internal wiring
     * alone, so the rename is done here instead - and the {@code for} attribute has to move with
     * it, or the control loses its name.</p>
     */
    private static HTMLElement renameControl(AbstractField<?, ?> field, String id) {
        HTMLElement control = control(field);
        String old = control.getAttribute("id");
        HTMLElement labelEl = old == null || old.isEmpty() ? null
                : Browser.query("label[for=\"" + old + "\"]");
        control.setAttribute("id", id);
        if (labelEl != null) {
            labelEl.setAttribute("for", id);
        }
        return control;
    }

    // =================================================================
    // Section 2: the overlays
    // =================================================================

    /**
     * Every overlay in the library, with the ids the driver has always used.
     *
     * <p>This was {@code OverlayProofPage} until the two harnesses became one. Nothing about it
     * changed in the move except where it hangs on the page.</p>
     */
    private static void buildOverlaysSection(HTMLElement host) {
        Div page = new Div();
        page.addClassName("flex flex-col gap-4 items-start");

        // Something focusable on the page behind every overlay. If Tab ever lands here while a
        // dialog is open, the focus trap has failed.
        Button behind = new Button("A button on the page behind");
        behind.setId("page-button");
        page.add(behind);

        page.add(defaultDialog());
        page.add(wideDialog());
        page.add(strictDialog());
        page.add(unownedDialog());
        page.add(dropdown());
        page.add(drawer());
        page.add(tooltip());
        page.add(toast());

        host.appendChild(page.getOuterElement());
    }

    /** Escape, the dim and both buttons all close it. Two focusables, so Tab has somewhere to go. */
    private static Div defaultDialog() {
        Dialog dialog = new Dialog("Confirm the change");
        dialog.setId("dlg-default");
        dialog.setWidth("32rem");
        dialog.add(new Div("Everything about this dialog is the library's, including the heading."));

        Button keep = new Button("Keep");
        keep.setId("dlg-default-keep");
        Button discard = new Button("Discard");
        discard.setId("dlg-default-discard");
        discard.addClickListener(e -> dialog.close());
        dialog.addAction(keep);
        dialog.addAction(discard);

        Button open = new Button("Open the default dialog");
        open.setId("open-default");
        open.addClickListener(e -> dialog.open());

        Div row = new Div();
        row.add(open, dialog);
        return row;
    }

    /** Sized with one call. The panel is never wider than the window, at any viewport. */
    private static Div wideDialog() {
        Dialog dialog = new Dialog("A wide panel");
        dialog.setId("dlg-wide");
        dialog.setWidth("56rem");
        dialog.add(new Div("setWidth(\"56rem\") sizes the panel, not the full-window overlay."));
        Button close = new Button("Close");
        close.setId("dlg-wide-close");
        close.addClickListener(e -> dialog.close());
        dialog.addAction(close);

        Button open = new Button("Open the wide dialog");
        open.setId("open-wide");
        open.addClickListener(e -> dialog.open());

        Div row = new Div();
        row.add(open, dialog);
        return row;
    }

    /** Refuses the dim, the way a form with half-written input has to. Escape still works. */
    private static Div strictDialog() {
        Dialog dialog = new Dialog("Unsaved changes");
        dialog.setId("dlg-strict");
        dialog.setCloseOnOutsideClick(false);
        dialog.add(new Div("Clicking outside this one does nothing."));
        Button close = new Button("Close");
        close.setId("dlg-strict-close");
        close.addClickListener(e -> dialog.close());
        dialog.addAction(close);

        Button open = new Button("Open the dialog that refuses the dim");
        open.setId("open-strict");
        open.addClickListener(e -> dialog.open());

        Div row = new Div();
        row.add(open, dialog);
        return row;
    }

    /**
     * A dialog the browser does not own - {@code setModal(false)}, the behaviour of 0.7.0. Nothing
     * moves the keyboard for it, so the component has to, and that is the path being checked here.
     */
    private static Div unownedDialog() {
        Dialog dialog = new Dialog("Not owned by the browser");
        dialog.setId("dlg-unowned");
        dialog.setModal(false);
        dialog.setCloseOnOutsideClick(false);
        dialog.add(new Div("The page behind this one is still live."));
        Button close = new Button("Close");
        close.setId("dlg-unowned-close");
        close.addClickListener(e -> dialog.close());
        dialog.addAction(close);

        Button open = new Button("Open the unowned dialog");
        open.setId("open-unowned");
        open.addClickListener(e -> dialog.open());

        Div row = new Div();
        row.add(open, dialog);
        return row;
    }

    /** A menu on the dropdown layer, left open by the driver so a dialog can be opened over it. */
    private static Div dropdown() {
        Dropdown menu = new Dropdown("Actions");
        menu.setId("dd-1");
        Button rename = new Button("Rename");
        rename.setId("dd-1-rename");
        menu.add(rename);

        Div row = new Div();
        row.add(menu);
        return row;
    }

    /** A drawer with two things in the panel, so the trap has a first and a last to wrap between. */
    private static Div drawer() {
        Drawer drawer = new Drawer("Settings");
        drawer.setId("drawer-1");

        Button first = new Button("First setting");
        first.setId("drawer-first");
        Button last = new Button("Last setting");
        last.setId("drawer-last");
        drawer.add(first, last);

        Button open = new Button("Open the drawer");
        open.setId("open-drawer");
        open.addClickListener(e -> drawer.open());
        drawer.addToPage(open);

        Div row = new Div();
        row.add(drawer);
        return row;
    }

    /** Not interactive. Here to prove it is on the top of the scale and that Escape hides it. */
    private static Div tooltip() {
        Tooltip tip = new Tooltip("The words the tip shows");
        tip.setId("tip-1");
        Button hover = new Button("Hover me");
        hover.setId("tip-1-target");
        tip.add(hover);

        Div row = new Div();
        row.add(tip);
        return row;
    }

    /**
     * A message, put on the page by a button so the driver can ask for a fresh one. Escape takes a
     * message away, which is the right behaviour and also means the driver cannot keep one around.
     */
    private static Div toast() {
        Div row = new Div();

        Button show = new Button("Show a message");
        show.setId("show-toast");
        show.addClickListener(e -> {
            Toast toast = new Toast();
            toast.setId("toast-1");
            toast.addClassName("toast-top toast-center");
            Div box = new Div("Saved");
            box.addClassName("alert alert-success");
            toast.add(box);
            row.add(toast);
        });

        row.add(show);
        return row;
    }

    // =================================================================
    // Section 3: the fields
    // =================================================================
    //
    // Ported unchanged from FieldDomProof, the harness that ran under Chrome's --dump-dom. Every
    // check ends at the same question - is this sentence part of the text on the screen - answered
    // by document.body.innerText, which contains no text that is detached, hidden, or never
    // inserted. That is the blind spot it exists for: 0.7.0 shipped a form that turned red and
    // said nothing, and the message was a perfectly good string on a perfectly good object the
    // whole time.
    //
    // Each field is proved twice, because the two orders fail differently: once captioned before
    // it is put on the page, and once put on the page bare and given its message and caption
    // afterwards.

    // ----------------------------------------------------------------
    // Steps, because a typed character is not answered in the same breath
    // ----------------------------------------------------------------

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
        Window.setTimeout(KeyboardProofPage::runNextStep, 1);
    }

    // ----------------------------------------------------------------
    // Group 1: a field captioned before it reaches the page
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // Group 2: the reported case - a bare field on a live page that is
    // later told it is wrong, and later still given its name
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // Group 3: a real edit that breaks a rule
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // Group 4: the same thing through Binder, which is how forms are written
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // Rules
    // ----------------------------------------------------------------

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

    // ----------------------------------------------------------------
    // Plumbing for the fields section
    // ----------------------------------------------------------------

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
        fieldStage.appendChild(el);
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

    // =================================================================
    // The browser facts a Java object cannot report about itself
    // =================================================================

    /**
     * Questions asked of the live page rather than of the component: is this node in the document,
     * does the browser lay it out with a size, what does a reader actually see, which element has
     * focus.
     *
     * <p>A field can hold a perfectly correct error message in a field of its own class while the
     * user sees nothing, and only these calls can tell the two apart.</p>
     */
    static final class Browser {

        private Browser() {
        }

        /** True when the node is in the live document rather than dangling in memory. */
        @JSBody(params = {"el"}, script = "return !!(el && el.isConnected);")
        static native boolean attached(HTMLElement el);

        /**
         * True when a person looking at the page would see this node: it is in the document, the
         * browser gives it a box with width and height, and nothing has made it invisible.
         */
        @JSBody(params = {"el"}, script =
                "if (!el || !el.isConnected) { return false; }"
                + "var style = window.getComputedStyle(el);"
                + "if (style.display === 'none' || style.visibility === 'hidden') { return false; }"
                + "if (parseFloat(style.opacity) === 0) { return false; }"
                + "var rect = el.getBoundingClientRect();"
                + "return rect.width > 0 && rect.height > 0;")
        static native boolean visible(HTMLElement el);

        /**
         * Everything on the page a reader can see, as text. This is the one that matters: text
         * inside a hidden node, or inside a node nobody inserted, does not appear here.
         */
        @JSBody(params = {}, script = "return document.body.innerText || '';")
        static native String pageText();

        @JSBody(params = {"el"}, script = "return el ? (el.textContent || '') : '';")
        static native String textOf(HTMLElement el);

        @JSBody(params = {"el"}, script = "return el ? el.tagName.toLowerCase() : '';")
        static native String tagOf(HTMLElement el);

        @JSBody(params = {"id"}, script = "return document.getElementById(id);")
        static native HTMLElement byId(String id);

        @JSBody(params = {"sel"}, script = "return document.querySelector(sel);")
        static native HTMLElement query(String sel);

        /** A click the way a person makes one, not a Java method call on the component. */
        @JSBody(params = {"el"}, script =
                "el.dispatchEvent(new MouseEvent('click', "
                + "{bubbles: true, cancelable: true, view: window}));")
        static native void click(HTMLElement el);

        @JSBody(params = {"el", "type"}, script =
                "el.dispatchEvent(new Event(type, {bubbles: true}));")
        static native void fire(HTMLElement el, String type);

        @JSBody(params = {"el", "value"}, script = "el.value = value;")
        static native void typeInto(HTMLElement el, String value);

        @JSBody(params = {"el", "checked"}, script = "el.checked = checked;")
        static native void tick(HTMLElement el, boolean checked);

        @JSBody(params = {}, script = "return document.activeElement;")
        static native HTMLElement focused();

        @JSBody(params = {"a", "b"}, script = "return a === b;")
        static native boolean same(HTMLElement a, HTMLElement b);

        /** The position of a node among its parent's element children, or -1. */
        @JSBody(params = {"el"}, script =
                "if (!el || !el.parentElement) { return -1; }"
                + "var kids = el.parentElement.children;"
                + "for (var idx = 0; idx < kids.length; idx++) { if (kids[idx] === el) { return idx; } }"
                + "return -1;")
        static native int indexAmongSiblings(HTMLElement el);
    }
}
