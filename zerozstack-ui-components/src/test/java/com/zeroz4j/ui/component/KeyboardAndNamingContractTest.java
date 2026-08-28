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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Anything a person can operate can be operated from the keyboard, and has a name.
 *
 * <p>This is the gate. It fails the build, and it is the reason this library may not grow a
 * component that only answers a mouse.</p>
 *
 * <h2>What counts as interactive</h2>
 *
 * <p>Nothing is listed by hand. A component is <b>interactive</b> when its own source wires a
 * person's action to behaviour - it registers a browser listener for a pointer, key or value
 * event, or it publishes a method an application uses to hear one, or it is an input field. Every
 * other component is <b>decorative</b>: it draws, it is never selected here, so it needs no
 * exemption and cannot be granted one. That is deliberate. A list of exemptions grows until it is
 * the rule.</p>
 *
 * <h2>What an interactive component owes</h2>
 *
 * <p><b>1. A keyboard route.</b> Every element the component makes a person act on is either a
 * control the browser already drives from the keyboard - {@code button}, {@code a} with an
 * {@code href}, {@code input}, {@code select}, {@code textarea}, {@code summary}, {@code details},
 * {@code label}, {@code option} - or it is given all three of a {@code role}, a {@code tabindex}
 * and a {@code keydown} handler, which is what it takes to build one by hand.</p>
 *
 * <p><b>2. A name.</b> That same element has words: text the component puts into it, or an
 * {@code aria-label}, {@code aria-labelledby} or {@code title} the component sets on it. An input
 * field is named by its caption, which {@code AbstractField} attaches to every field in the
 * library, so a field meets this by being one.</p>
 *
 * <p><b>3. A surface you drag has arrow keys.</b> A component that starts a drag - it listens for
 * {@code mousedown} and then follows the pointer - must let somebody who cannot drag do the same
 * thing: the surface takes {@code tabindex} and answers the arrow keys.</p>
 *
 * <p><b>4. It is proved in a browser.</b> Every interactive component appears on the keyboard
 * proof page in {@code tools/ui-proof}, which a real browser drives with real key presses. Source
 * text can show that the wiring is present; only a browser can show that pressing Enter does
 * anything. Adding an interactive component and not putting it on that page fails here.</p>
 *
 * <h2>Why it reads source text</h2>
 *
 * <p>Every component in this library is a wrapper around a browser element. There is no document
 * on the JVM, so none of them can be constructed in an ordinary unit test - which is why
 * {@link OverlayContractTest} and {@code SourceTextEncodingTest} read source text too.</p>
 *
 * <p><b>It resolves rather than greps.</b> A test that searched for the word "aria" would pass
 * while a component stayed unusable, and that failure mode is the whole reason this exists. So for
 * every listener registration it works out <i>which element</i> the listener was put on and
 * <i>what tag that element is</i>, by following the receiver back to where it was created. When it
 * cannot work that out it says so and fails, rather than passing quietly: an interaction the gate
 * cannot read is an interaction the gate cannot vouch for.</p>
 */
class KeyboardAndNamingContractTest {

    // ------------------------------------------------------------------ the rule, as data

    /** Raising one of these is somebody activating a control. */
    private static final Set<String> ACTIVATION = new LinkedHashSet<>(Arrays.asList(
            "click", "dblclick"));

    /** Raising one of these starts a drag, if the component then follows the pointer. */
    private static final Set<String> DRAG_START = new LinkedHashSet<>(Arrays.asList(
            "mousedown", "pointerdown", "touchstart"));

    /** Everything a person does, which is what decides "is this component interactive at all". */
    private static final Set<String> USER_EVENTS = new LinkedHashSet<>(Arrays.asList(
            "click", "dblclick", "mousedown", "pointerdown", "touchstart", "contextmenu",
            "keydown", "keyup", "keypress", "change", "input", "drop", "wheel", "submit"));

    /**
     * Tags the browser already puts in the tab order and already activates from the keyboard.
     *
     * <p>{@code dialog} is here for a different reason from the rest. It is not a control and
     * nobody presses it; it is the one element the browser drives entirely by itself once it has
     * been handed over - Escape closes it, the keyboard cannot leave it, and the page behind stops
     * answering. A click listener on a dialog is the dim being clicked, not a control being
     * pressed. That whole contract is checked in {@link OverlayContractTest} instead.</p>
     */
    private static final Set<String> NATIVELY_OPERABLE = new LinkedHashSet<>(Arrays.asList(
            "button", "input", "select", "textarea", "summary", "details", "label", "option", "a",
            "dialog"));

    // ------------------------------------------------------------------ the checks

    /**
     * Every element a person is made to click is a control the keyboard can reach and press.
     *
     * <p>Either it is one of the tags the browser already drives, or the component built one by
     * hand and did the whole job: a role saying what it is, a tabindex putting it in the tab
     * order, and a keydown handler answering the keys that role implies. Two of the three gives a
     * control that can be focused and not pressed, or pressed and never found.</p>
     */
    @Test
    void everythingYouClickCanAlsoBeReachedAndPressed() {
        List<String> findings = new ArrayList<>();

        for (Library.Source source : Library.interactiveComponents()) {
            for (Library.Site site : source.sites()) {
                if (!ACTIVATION.contains(site.event())) {
                    continue;
                }
                if (site.unresolved()) {
                    findings.add(source.name() + " line " + site.line() + ": this gate cannot tell "
                            + "what '" + site.receiver() + "' is, so it cannot say whether the "
                            + "keyboard can reach it. Create it in the ordinary way - a field or a "
                            + "local with its type written out - or put the listener on a Button.");
                    continue;
                }
                for (String tag : site.tags()) {
                    boolean anchorWithNowhereToGo = "a".equals(tag) && !source.setsHref();
                    if (NATIVELY_OPERABLE.contains(tag) && !anchorWithNowhereToGo) {
                        continue;
                    }
                    List<String> missing = new ArrayList<>();
                    if (!site.hasAttribute("role")) {
                        missing.add("a role saying what it is");
                    }
                    if (!site.hasAttribute("tabindex")) {
                        missing.add("a tabindex putting it in the tab order");
                    }
                    if (!source.handlesKeydownOn(site.receiver())) {
                        missing.add("a keydown handler answering Enter and Space");
                    }
                    if (!missing.isEmpty()) {
                        findings.add(source.name() + " line " + site.line() + ": '"
                                + site.receiver() + "' is a <" + tag + "> that answers a click but "
                                + "is missing " + String.join(", ", missing) + ".");
                    }
                }
            }
        }

        assertTrue(findings.isEmpty(), report(findings,
                "Something in this library can be clicked and cannot be used from a keyboard.",
                "The short way out is nearly always the right one: put the listener on a Button "
                        + "instead of a Div. A <button> is in the tab order, answers Enter and "
                        + "Space, and announces itself, without a line of code. Build one by hand "
                        + "only when it genuinely is not a button - and then it needs the role, "
                        + "the tabindex and the keydown handler together."));
    }

    /**
     * Every control has words, so somebody who cannot see it still knows what it does.
     *
     * <p>An icon on its own is not a name. Neither is a colour, a position, or a tip that only
     * appears under a pointer. The words can be text the component writes into the control, or an
     * {@code aria-label} it sets on it - but they have to be somewhere.</p>
     */
    @Test
    void everyControlHasWords() {
        List<String> findings = new ArrayList<>();

        for (Library.Source source : Library.interactiveComponents()) {
            if (source.isField()) {
                continue;   // named by its caption; AbstractField gives every field one
            }
            for (Library.Site site : source.sites()) {
                boolean operated = ACTIVATION.contains(site.event())
                        || DRAG_START.contains(site.event());
                if (!operated || site.unresolved()) {
                    continue;   // an unresolvable receiver is already reported by the check above
                }
                if (!source.namesReceiver(site.receiver())) {
                    findings.add(source.name() + " line " + site.line() + ": '" + site.receiver()
                            + "' is operated and never named. Give it words, or an aria-label.");
                }
            }
        }

        assertTrue(findings.isEmpty(), report(findings,
                "A control in this library has no name.",
                "Put the words in the control - new Button(\"Copy\") - or, when the control is an "
                        + "icon or a bare surface with nothing to write on, set them with "
                        + "setAttribute(\"aria-label\", ...). A control with no name is announced "
                        + "as \"button\", which tells the listener nothing at all."));
    }

    /**
     * Anything you drag can also be moved with the arrow keys.
     *
     * <p>A splitter, a resize handle, a scrubbed timeline and a panned canvas are the same shape:
     * a surface that does nothing until a pointer is dragged across it. Dragging is the one
     * gesture the browser supplies no keyboard equivalent for, so the component has to supply it -
     * the surface goes in the tab order, and the arrow keys move it.</p>
     */
    @Test
    void anythingYouDragAlsoAnswersTheArrowKeys() {
        List<String> findings = new ArrayList<>();

        for (Library.Source source : Library.interactiveComponents()) {
            if (!source.isDragSurface()) {
                continue;
            }
            for (Library.Site site : source.sites()) {
                if (!DRAG_START.contains(site.event())) {
                    continue;
                }
                List<String> missing = new ArrayList<>();
                if (!site.hasAttribute("tabindex")) {
                    missing.add("a tabindex, so the keyboard can get to it");
                }
                if (!source.answersArrowKeys()) {
                    missing.add("arrow keys, so it can be moved without a pointer");
                }
                if (!missing.isEmpty()) {
                    findings.add(source.name() + " line " + site.line() + ": '" + site.receiver()
                            + "' is dragged with a pointer and is missing "
                            + String.join(", ", missing) + ".");
                }
            }
        }

        assertTrue(findings.isEmpty(), report(findings,
                "Something in this library can only be moved by dragging it.",
                "Give the surface a tabindex, and handle the arrow keys in a keydown listener. A "
                        + "splitter that only moves by dragging is a splitter nobody with a "
                        + "tremor, a trackpad they dislike, or no mouse at all can move."));
    }

    /**
     * A link goes somewhere. One that does not is not a link, and cannot be tabbed to.
     *
     * <p>An {@code <a>} with no {@code href} is not in the tab order - the browser treats it as
     * text that happens to be blue. Three components in 0.7.0 were exactly that, one of them the
     * only way this library builds a menu.</p>
     */
    @Test
    void everyAnchorHasSomewhereToGo() {
        List<String> findings = new ArrayList<>();

        for (Library.Source source : Library.allComponents()) {
            if (source.createsAnchor() && !source.setsHref()) {
                findings.add(source.name() + " builds an <a> and never gives it an href.");
            }
        }

        assertTrue(findings.isEmpty(), report(findings,
                "A component builds a link with no destination.",
                "An <a> with no href is not focusable and is not announced as a link - it is blue "
                        + "text. If it goes somewhere, set the href. If it does something, it is a "
                        + "Button, not a Link."));
    }

    /**
     * Every interactive component is on the page a real browser drives.
     *
     * <p>Everything above reads text. Text can show that a keydown handler exists; it cannot show
     * that pressing Enter does anything, that Tab arrives where it should, or that a screen reader
     * would hear a name. Only {@code tools/ui-proof} can, so nothing interactive may stay off it -
     * and because the list here is derived from the code rather than typed out, a new component
     * joins that obligation the moment it is written.</p>
     */
    @Test
    void everyInteractiveComponentIsOnTheKeyboardProofPage() {
        Path page = Library.repositoryRoot().resolve(Paths.get("tools", "ui-proof", "src", "main",
                "java", "com", "zeroz4j", "ui", "proof", "KeyboardProofPage.java"));
        assertTrue(Files.isRegularFile(page),
                "The keyboard proof page is missing: " + page + ". It is where every interactive "
                        + "component is actually operated by a browser; without it nothing here is "
                        + "more than well-formed text.");

        String proof = Library.read(page);
        Set<String> missing = new TreeSet<>();
        for (Library.Source source : Library.interactiveComponents()) {
            String type = source.typeName();
            boolean built = Pattern.compile("\\bnew\\s+" + Pattern.quote(type) + "\\b")
                    .matcher(proof).find();
            boolean called = Pattern.compile("\\b" + Pattern.quote(type) + "\\s*\\.")
                    .matcher(proof).find();
            if (!built && !called) {
                missing.add(type);
            }
        }

        assertTrue(missing.isEmpty(),
                "These components can be operated and are never operated in a browser: "
                        + String.join(", ", missing) + System.lineSeparator()
                        + "Add each one to tools/ui-proof/src/main/java/com/zeroz4j/ui/proof/"
                        + "KeyboardProofPage.java and give drive.mjs a check that presses a key at "
                        + "it. A component nobody has driven from a keyboard has not been shown to "
                        + "work from one.");
    }

    private static String report(List<String> findings, String headline, String howToFix) {
        return headline + System.lineSeparator() + System.lineSeparator()
                + String.join(System.lineSeparator(), findings) + System.lineSeparator()
                + System.lineSeparator() + howToFix;
    }

    // ------------------------------------------------------------------ reading the library

    /**
     * The component library, read as text, with just enough resolution to answer the questions
     * above honestly.
     */
    static final class Library {

        private Library() {
        }

        /**
         * One interaction: an event, the expression it was registered on, and every tag that
         * expression can hold.
         *
         * <p>More than one, because a receiver assigned in two branches is two different elements.
         * Both have to be operable, or the control works half the time - which is worse than never
         * working, because it is harder to notice.</p>
         */
        record Site(String event, String receiver, List<String> tags, int line, Source owner) {

            /** True when the owning source sets this attribute on this same receiver. */
            boolean hasAttribute(String attribute) {
                return owner.setsAttributeOn(receiver, attribute);
            }

            /** True when nothing in the file says what this expression holds. */
            boolean unresolved() {
                return tags.isEmpty();
            }
        }

        /** One component source file, and what can be read out of it. */
        static final class Source {

            private final String text;
            private final String type;
            private List<Site> sites;

            Source(Path file) {
                this.text = read(file);
                String fileName = file.getFileName().toString();
                this.type = fileName.substring(0, fileName.length() - ".java".length());
            }

            String name() {
                return type + ".java";
            }

            String typeName() {
                return type;
            }

            /** True when this component reacts to something a person did. */
            boolean isInteractive() {
                if (isField()) {
                    return true;
                }
                for (Site site : sites()) {
                    if (USER_EVENTS.contains(site.event())) {
                        return true;
                    }
                }
                return DECLARES_LISTENER_API.matcher(text).find();
            }

            /** True when this is an input field, and is therefore named by its caption. */
            boolean isField() {
                return EXTENDS_FIELD.matcher(text).find();
            }

            /** True when it starts a drag and then follows the pointer, rather than merely noting a press. */
            boolean isDragSurface() {
                boolean starts = sites().stream().anyMatch(s -> DRAG_START.contains(s.event()));
                boolean follows = text.contains("\"mousemove\"") || text.contains("\"pointermove\"");
                return starts && follows;
            }

            boolean answersArrowKeys() {
                return text.contains("Arrow");
            }

            boolean createsAnchor() {
                return text.contains("super(\"a\")") || text.contains("createElement(\"a\")");
            }

            boolean setsHref() {
                return text.contains("\"href\"") || text.contains("setHref");
            }

            /** True when a keydown listener is registered on this same receiver. */
            boolean handlesKeydownOn(String receiver) {
                for (Site site : sites()) {
                    if ("keydown".equals(site.event()) && site.receiver().equals(receiver)) {
                        return true;
                    }
                }
                return false;
            }

            boolean setsAttributeOn(String receiver, String attribute) {
                String target = "getElement()".equals(receiver) ? "getElement\\(\\)"
                        : Pattern.quote(receiver) + "\\s*(?:\\.getElement\\(\\))?";
                return Pattern.compile(target + "\\s*\\.setAttribute\\s*\\(\\s*\""
                        + Pattern.quote(attribute) + "\"").matcher(text).find();
            }

            /**
             * True when the component puts words on this element - as text it writes, as a name it
             * declares, or as text handed to the constructor that made it.
             */
            boolean namesReceiver(String receiver) {
                for (String attribute : new String[] { "aria-label", "aria-labelledby", "title" }) {
                    if (setsAttributeOn(receiver, attribute)) {
                        return true;
                    }
                }
                String target = "getElement()".equals(receiver) ? "" : Pattern.quote(receiver)
                        + "\\s*(?:\\.getElement\\(\\))?\\s*\\.";
                if (!target.isEmpty() && Pattern.compile(target
                        + "(setText|setTextContent|setInnerText)\\s*\\(").matcher(text).find()) {
                    return true;
                }
                // Words handed to whatever built it: new Span("Copy"), new Button(label, ...).
                if (!"getElement()".equals(receiver) && Pattern.compile("\\b"
                        + Pattern.quote(receiver) + "\\s*=\\s*new\\s+\\w+\\s*\\(\\s*[^)\\s]")
                        .matcher(text).find()) {
                    return true;
                }
                // Words put inside it. A menu entry, a tab and a button all take their name from
                // what they contain, so a child built out of words names the thing it went into.
                if (!"getElement()".equals(receiver) && namedByItsContents(receiver)) {
                    return true;
                }
                // The component's own element, named through the component's own text API.
                if ("getElement()".equals(receiver)) {
                    return text.contains("HasText") || text.contains("setAriaLabel");
                }
                return false;
            }

            /**
             * True when the component puts a child made out of words inside this element.
             *
             * <p>Deliberately narrow: the child has to have been built with something in its
             * brackets, or had text written into it. An icon dropped into an empty box is a child
             * too, and names nothing.</p>
             */
            private boolean namedByItsContents(String receiver) {
                Matcher appended = Pattern.compile(Pattern.quote(receiver)
                        + "\\s*(?:\\.getElement\\(\\)\\s*\\.appendChild\\s*\\(\\s*(\\w+)"
                        + "\\s*\\.getElement\\(\\)|\\.add\\s*\\(\\s*(\\w+)\\s*[,)])")
                        .matcher(text);
                while (appended.find()) {
                    String child = appended.group(1) != null ? appended.group(1)
                            : appended.group(2);
                    boolean builtFromWords = Pattern.compile("\\b" + Pattern.quote(child)
                            + "\\s*=\\s*new\\s+\\w+\\s*\\(\\s*[^)\\s]").matcher(text).find();
                    boolean givenWords = Pattern.compile("\\b" + Pattern.quote(child)
                            + "\\s*\\.(setText|setTextContent)\\s*\\(").matcher(text).find();
                    if (builtFromWords || givenWords) {
                        return true;
                    }
                }
                return false;
            }

            /** Every listener registration in the file, with the receiver resolved to a tag. */
            List<Site> sites() {
                if (sites != null) {
                    return sites;
                }
                sites = new ArrayList<>();
                Matcher m = LISTENER.matcher(text);
                while (m.find()) {
                    String raw = m.group(1) == null ? "" : m.group(1).trim();
                    if (isPageWide(raw)) {
                        continue;   // an outside-click watch on the document is not a control
                    }
                    String receiver = normalise(raw);
                    sites.add(new Site(m.group(2), receiver, tagsOf(receiver),
                            lineOf(text, m.start()), this));
                }
                return sites;
            }

            /** Strips a trailing .getElement() so a receiver is named the way the source names it. */
            private static String normalise(String receiver) {
                String r = receiver;
                while (r.endsWith(".")) {
                    r = r.substring(0, r.length() - 1);
                }
                if (r.endsWith(".getElement()")) {
                    r = r.substring(0, r.length() - ".getElement()".length());
                }
                if (r.startsWith("this.")) {
                    r = r.substring("this.".length());
                }
                return r.isEmpty() || "this".equals(r) || "getElement()".equals(r)
                        ? "getElement()" : r;
            }

            private static boolean isPageWide(String receiver) {
                return receiver.contains("getDocument()") || receiver.contains("documentElement")
                        || receiver.contains("Window.current()");
            }

            /**
             * Every tag the element a receiver names can hold, or an empty list when nothing in
             * the file says.
             *
             * <p>Assignments come first and win: a variable assigned in two branches really is two
             * elements, and both are collected. Only when there is no assignment at all does it
             * fall back to the declared type, which is the weaker answer.</p>
             */
            private List<String> tagsOf(String receiver) {
                if ("getElement()".equals(receiver)) {
                    return tagsFromType(type);
                }
                List<String> tags = new ArrayList<>();
                Matcher created = Pattern.compile("\\b" + Pattern.quote(receiver)
                        + "\\s*=\\s*[^;]*?createElement(?:NS)?\\s*\\([^;]*?\"([a-zA-Z]+)\"\\s*\\)")
                        .matcher(text);
                while (created.find()) {
                    tags.add(created.group(1));
                }
                Matcher built = Pattern.compile("\\b" + Pattern.quote(receiver)
                        + "\\s*=\\s*new\\s+(\\w+)\\s*[(<]").matcher(text);
                while (built.find()) {
                    tags.addAll(tagsFromType(built.group(1)));
                }
                if (!tags.isEmpty()) {
                    return tags;
                }
                Matcher declared = Pattern.compile("(?:private|protected|public|final|\\(|,)\\s*"
                        + "(?:final\\s+)?([A-Z]\\w+)\\s+" + Pattern.quote(receiver) + "\\b")
                        .matcher(text);
                if (declared.find()) {
                    return tagsFromType(declared.group(1));
                }
                return tags;
            }

            private static List<String> tagsFromType(String type) {
                String tag = tagOfType(type);
                return tag == null ? new ArrayList<>()
                        : new ArrayList<>(Collections.singletonList(tag));
            }
        }

        // ------------------------------------------------------------ resolving a type to a tag

        private static final Map<String, String> TAG_BY_TYPE = new LinkedHashMap<>();

        /** The tag a component class builds, following extends where it must; null if it is not one. */
        static String tagOfType(String type) {
            if (TAG_BY_TYPE.containsKey(type)) {
                return TAG_BY_TYPE.get(type);
            }
            TAG_BY_TYPE.put(type, null);   // stops a cycle in an extends chain
            String tag = null;
            Path source = sourceOf(type);
            if (source != null) {
                String body = read(source);
                Matcher own = Pattern.compile("super\\s*\\(\\s*\"([a-zA-Z]+)\"").matcher(body);
                if (own.find()) {
                    tag = own.group(1);
                } else {
                    Matcher parent = Pattern.compile("class\\s+" + Pattern.quote(type)
                            + "[^{]*?\\bextends\\s+(\\w+)").matcher(body);
                    if (parent.find()) {
                        tag = tagOfType(parent.group(1));
                    }
                }
            }
            TAG_BY_TYPE.put(type, tag);
            return tag;
        }

        private static Map<String, Path> sourcesByType;

        private static Path sourceOf(String type) {
            if (sourcesByType == null) {
                sourcesByType = new LinkedHashMap<>();
                for (Path p : javaUnder(uiRoot())) {
                    String fileName = p.getFileName().toString();
                    sourcesByType.putIfAbsent(fileName.substring(0, fileName.length() - 5), p);
                }
            }
            return sourcesByType.get(type);
        }

        // ------------------------------------------------------------ which files to look at

        /** Every component and layout source in the library. */
        static List<Source> allComponents() {
            List<Source> all = new ArrayList<>();
            for (Path dir : new Path[] { uiRoot().resolve("component"), uiRoot().resolve("layout") }) {
                if (!Files.isDirectory(dir)) {
                    continue;
                }
                try (Stream<Path> files = Files.list(dir)) {
                    files.filter(p -> p.getFileName().toString().endsWith(".java"))
                         .sorted()
                         .forEach(p -> all.add(new Source(p)));
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
            return all;
        }

        /** The ones a person can operate - worked out from the code, never typed out. */
        static List<Source> interactiveComponents() {
            return allComponents().stream()
                    .filter(s -> !INFRASTRUCTURE.contains(s.typeName()))
                    .filter(Source::isInteractive)
                    .collect(Collectors.toList());
        }

        /**
         * The plumbing every component is built out of, which is not itself a component: the base
         * class that registers listeners on behalf of others, the raw-JavaScript escape hatch, and
         * the browser-file helper it calls. Excluded because they build no control of their own,
         * not as a favour.
         */
        private static final Set<String> INFRASTRUCTURE = new HashSet<>(Arrays.asList(
                "Component", "AbstractField", "Js", "UploadBrowser"));

        // ------------------------------------------------------------ small helpers

        private static final Pattern LISTENER = Pattern.compile(
                "([\\w.()]*?)\\.?add(?:Dom)?EventListener\\s*\\(\\s*\"([a-zA-Z]+)\"");

        private static final Pattern DECLARES_LISTENER_API = Pattern.compile(
                "public\\s+[\\w<>., ]+\\s+add(Click|Selection|Change|Toggle|Upload)Listener\\s*\\(");

        private static final Pattern EXTENDS_FIELD = Pattern.compile("extends\\s+AbstractField\\s*<");

        private static int lineOf(String text, int offset) {
            int line = 1;
            for (int i = 0; i < offset && i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    line++;
                }
            }
            return line;
        }

        private static List<Path> javaUnder(Path root) {
            try (Stream<Path> files = Files.walk(root)) {
                return files.filter(Files::isRegularFile)
                            .filter(p -> p.getFileName().toString().endsWith(".java"))
                            .collect(Collectors.toList());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        static Path uiRoot() {
            return repositoryRoot().resolve(Paths.get("zerozstack-ui-components", "src", "main",
                    "java", "com", "zeroz4j", "ui"));
        }

        static String read(Path file) {
            try {
                return new String(Files.readAllBytes(file), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        static Path repositoryRoot() {
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
}
