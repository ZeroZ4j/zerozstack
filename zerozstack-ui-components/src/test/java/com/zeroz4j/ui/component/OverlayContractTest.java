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
package com.zeroz4j.ui.component;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The things that make the overlays behave, checked without a browser.
 *
 * <p>The browser proof in {@code tools/ui-proof} is what actually shows that the keyboard
 * stays inside a dialog and that Escape closes it. It needs Chrome and about a minute, so it is
 * run by hand. This test is the tripwire that runs on every build: it reads the source of each
 * overlay and fails if the wiring the proof depends on has been taken out.</p>
 *
 * <p>It reads text rather than driving components because the components wrap browser elements —
 * there is no document on the JVM, so none of them can even be constructed here. Reading the
 * source is what {@code SourceTextEncodingTest} in this module already does, and for the same
 * reason.</p>
 */
class OverlayContractTest {

    /**
     * Nothing that floats over the page picks its own stacking number.
     *
     * <p>This is the fault the layer scale was written for: an application had hand-picked numbers
     * that disagreed with each other, and one overlay came out underneath something it should have
     * covered. Named tiers only work if everybody uses them.</p>
     *
     * <p>Scoped to the components that float and to the gallery pages that show them. A stacking
     * number used inside one component to order its own parts — a resize handle over a panel, say —
     * is a different thing and is left alone.</p>
     */
    @Test
    void noOverlayPicksItsOwnStackingNumber() {
        Pattern handWritten = Pattern.compile(
                "setStyle\\s*\\(\\s*\"z-index\"|setProperty\\s*\\(\\s*\"z-index\"|\"z-\\[|\\bz-\\d+\\b");
        List<String> findings = new ArrayList<>();

        for (Map.Entry<String, Path> overlay : overlaySources().entrySet()) {
            String source = read(overlay.getValue());
            Matcher m = handWritten.matcher(source);
            while (m.find()) {
                findings.add(overlay.getKey() + " writes a stacking number by hand: " + m.group());
            }
        }

        assertTrue(findings.isEmpty(),
                "An overlay is picking its own stacking number instead of asking for a layer by "
                        + "name. Use setLayer(Layer.X), or HasLayer.applyTo(component, Layer.X) for "
                        + "a part built from plain elements."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    /** Every overlay puts itself on a tier when it is built, so no application has to remember to. */
    @Test
    void everyOverlayPutsItselfOnATier() {
        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("Dialog.java", "Layer.OVERLAY");
        expected.put("Drawer.java", "Layer.OVERLAY");
        expected.put("Dropdown.java", "Layer.DROPDOWN");
        expected.put("ContextMenu.java", "Layer.DROPDOWN");
        expected.put("Toast.java", "Layer.TOAST");
        expected.put("Tooltip.java", "Layer.TOOLTIP");

        List<String> findings = new ArrayList<>();
        for (Map.Entry<String, String> want : expected.entrySet()) {
            String source = read(componentSource(want.getKey()));
            boolean setsIt = source.contains("setLayer(" + want.getValue() + ")")
                    || source.contains("applyTo(menu, " + want.getValue() + ")")
                    || source.contains("applyTo(side, " + want.getValue() + ")");
            if (!setsIt) {
                findings.add(want.getKey() + " no longer puts itself on " + want.getValue());
            }
        }

        assertTrue(findings.isEmpty(),
                "An overlay stopped putting itself on a layer. An application should never have to "
                        + "know which tier a toast belongs on."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    /**
     * The dialog is a real {@code <dialog>} and is handed to the browser to show.
     *
     * <p>Everything a dialog has to do — hold the keyboard, close on Escape, stop the page behind
     * responding, and draw above every stacking number there is — comes from that one call. A
     * dialog drawn with a stylesheet class instead looks identical and does none of it, which is
     * exactly what shipped in 0.7.0.</p>
     */
    @Test
    void theDialogIsARealDialogHandedToTheBrowser() {
        String dialog = read(componentSource("Dialog.java"));
        assertTrue(dialog.contains("super(\"dialog\")"),
                "Dialog must be a native <dialog> element. Nothing else can enter the browser's "
                        + "top layer, and the top layer is what beats every stacking number.");
        assertTrue(dialog.contains("Js.dialogShowModal(getElement())"),
                "Dialog.open() must call showModal(). Showing it any other way gives up the focus "
                        + "trap, Escape, and the top layer, all at once.");
        assertTrue(dialog.contains("\"cancel\""),
                "Dialog must listen for the browser's cancel event. Refusing the default there is "
                        + "the only way setCloseOnEsc(false) can keep a dialog open.");

        String js = read(componentSource("Js.java"));
        assertTrue(js.contains("dialog.showModal()"),
                "Js.dialogShowModal must call the element's own showModal().");
    }

    /** A dialog with a heading is announced by that heading, not as an unnamed "dialog". */
    @Test
    void theDialogTakesItsNameFromItsOwnHeading() {
        String dialog = read(componentSource("Dialog.java"));
        assertTrue(dialog.contains("setAttribute(\"aria-labelledby\", heading.getId())"),
                "A dialog given a title must point at the heading with aria-labelledby, or a screen "
                        + "reader announces nothing but the word dialog.");
        assertTrue(dialog.contains("super(\"h2\")"),
                "The dialog's title must be a real heading element, so the page has an outline.");
        assertTrue(dialog.contains("setAttribute(\"aria-label\""),
                "Dialog must still offer a spoken name for designs with no room for a heading.");
    }

    /**
     * A dialog's size is clamped against the window, not against its own parent.
     *
     * <p>The overlay lays the panel out in a grid track sized by the panel itself, so a percentage
     * clamp resolves against the panel's own width and clamps nothing. A panel asked for 56rem
     * stayed 56rem in a 380-pixel window and hung off both edges.</p>
     */
    @Test
    void aDialogPanelIsNeverWiderThanTheWindow() {
        String dialog = read(componentSource("Dialog.java"));
        assertTrue(dialog.contains("\"max-width\", \"calc(100vw - 2rem)\""),
                "Dialog.setWidth must clamp against the window with vw units. A percentage clamps "
                        + "against the panel itself and does nothing.");
        assertTrue(dialog.contains("\"max-height\", \"calc(100vh - 2rem)\""),
                "Dialog.setHeight must clamp against the window height for the same reason.");
    }

    /**
     * Everything that covers the page can be got out of with Escape, and puts the keyboard back.
     *
     * <p>A drawer covers the page and dims it exactly as a dialog does, so it needs the same way
     * out — but it is not a native dialog, so nothing gives it one for free.</p>
     */
    @Test
    void everythingThatCoversThePageAnswersToEscape() {
        List<String> findings = new ArrayList<>();
        for (String file : new String[] { "Drawer.java", "Dropdown.java", "ContextMenu.java",
                "Toast.java", "Tooltip.java" }) {
            String source = read(componentSource(file));
            if (!source.contains("\"Escape\".equals(Js.eventKey(")) {
                findings.add(file + " no longer closes on Escape");
            }
        }
        assertTrue(findings.isEmpty(),
                "An overlay stopped answering to Escape. Somebody who opened it by mistake now has "
                        + "to find the mouse."
                        + System.lineSeparator()
                        + String.join(System.lineSeparator(), findings));
    }

    /** The drawer holds the keyboard while it covers the page, and gives it back when it stops. */
    @Test
    void theDrawerHoldsTheKeyboardWhileItCoversThePage() {
        String drawer = read(componentSource("Drawer.java"));
        assertTrue(drawer.contains("Js.trapFocusIn(panel.getElement())"),
                "An open drawer must hold the keyboard inside its panel. Without it, Tab walks out "
                        + "into the dimmed page behind, which is still there and still clickable.");
        assertTrue(drawer.contains("Js.releaseFocusTrap(panel.getElement())"),
                "A closed drawer must let the keyboard go again.");
        assertTrue(drawer.contains("Js.focusFirstInside(panel.getElement())"),
                "Opening a drawer must move the keyboard into it.");
        assertTrue(drawer.contains("Js.contains(side.getElement(), target)"),
                "The drawer must measure 'did the keyboard come from inside me' against the sliding "
                        + "side, not the whole drawer: the button that opens a drawer is normally on "
                        + "the page area, which is inside the drawer element too.");
    }

    /** A tooltip is read, never used, so it takes nothing and holds nothing. */
    @Test
    void theTooltipHoldsNothing() {
        String tooltip = read(componentSource("Tooltip.java"));
        assertTrue(!tooltip.contains("trapFocusIn"),
                "A tooltip must never hold the keyboard. It is something you read, not something "
                        + "you use, and there is nothing inside one to reach.");
        assertTrue(!tooltip.contains("focusFirstInside"),
                "A tooltip must never take the keyboard.");
        assertTrue(tooltip.contains("setAttribute(\"data-tip\", text)"),
                "The tooltip's words belong on the tip, which the stylesheet reads from data-tip. "
                        + "Setting the wrapper's own text puts them on the page beside the button "
                        + "instead, permanently, and leaves the tip empty.");
    }

    /** A message is announced when it appears, and never steals the keyboard from a typist. */
    @Test
    void aMessageIsAnnouncedAndTakesNothing() {
        String toast = read(componentSource("Toast.java"));
        assertTrue(toast.contains("setAttribute(\"aria-live\", \"polite\")"),
                "A message must be announced when it appears, politely.");
        assertTrue(!toast.contains("focusFirstInside") && !toast.contains("trapFocusIn"),
                "A message must never take the keyboard. One arriving while somebody is typing "
                        + "would move them out of the box they are typing in.");
    }

    // ---------------------------------------------------------------- helpers

    private static Map<String, Path> overlaySources() {
        Map<String, Path> sources = new LinkedHashMap<>();
        for (String file : new String[] { "Dialog.java", "Drawer.java", "Dropdown.java",
                "ContextMenu.java", "Toast.java", "Tooltip.java" }) {
            sources.put(file, componentSource(file));
        }
        Path gallery = repositoryRoot().resolve(Paths.get("zerozstack-examples", "components-showcase",
                "showcase-client", "src", "main", "java", "com", "zeroz4j", "example", "client",
                "showcase"));
        if (Files.isDirectory(gallery)) {
            for (String file : new String[] { "DialogShowcase.java", "DrawerShowcase.java",
                    "DropdownShowcase.java", "ToastShowcase.java", "TooltipShowcase.java" }) {
                Path page = gallery.resolve(file);
                if (Files.isRegularFile(page)) {
                    sources.put("the gallery's " + file, page);
                }
            }
        }
        return sources;
    }

    private static Path componentSource(String fileName) {
        return repositoryRoot().resolve(Paths.get("zerozstack-ui-components", "src", "main", "java",
                "com", "zeroz4j", "ui", "component", fileName));
    }

    private static String read(Path file) {
        assertTrue(Files.isRegularFile(file), "expected to find " + file);
        try {
            return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Walks up from the working directory until the checkout's own root pom is found. */
    private static Path repositoryRoot() {
        Path here = Paths.get("").toAbsolutePath();
        for (Path candidate = here; candidate != null; candidate = candidate.getParent()) {
            if (Files.isDirectory(candidate.resolve("zerozstack-ui-components"))
                    && Files.isRegularFile(candidate.resolve("pom.xml"))) {
                return candidate;
            }
        }
        throw new IllegalStateException("could not find the checkout root from " + here);
    }
}
