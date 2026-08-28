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

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Puts every form field on a real page in a real browser and asks whether a person would see the
 * caption, the explanation and the reason a value was refused.
 *
 * <p><b>Why not an ordinary unit test.</b> There is no server-side DOM in this library - a
 * component is a wrapper around a browser element, so on the JVM there is nothing to inspect. The
 * only other test of a field in this module builds a stand-in object with no element at all. That
 * is exactly the blind spot that let 0.7.0 ship a form which turned red and said nothing: the
 * message was a perfectly good string on a perfectly good object, and no test noticed that it
 * never reached the page. So the checks run inside Chrome, where "on the screen" is a question
 * with an answer, and this class only starts the browser and reads the verdicts back.</p>
 *
 * <p><b>What it needs.</b> {@code target/domproof/classes.js}, which the build compiles from
 * {@link FieldDomProof} with TeaVM, and a Chrome or Edge on the machine. Pass
 * {@code -DskipDomProof} to leave both out of the build; set {@code CHROME_BIN} to point at a
 * browser somewhere unusual.</p>
 */
class FieldDomProofTest {

    private static final List<String> verdicts = new ArrayList<>();
    private static String dumpedPage;

    @BeforeAll
    static void runTheProofInABrowser() throws Exception {
        Path bundle = Paths.get("target", "domproof", "classes.js");
        Assumptions.assumeTrue(Files.exists(bundle),
                "No compiled proof page at " + bundle.toAbsolutePath()
                        + " - the build was run with -DskipDomProof.");

        String browser = findBrowser();
        Assumptions.assumeTrue(browser != null,
                "No Chrome or Edge found. Set CHROME_BIN to run the field proof.");

        Path page = bundle.resolveSibling("proof.html");
        Files.write(page, ("<!doctype html>\n"
                + "<html><head><meta charset=\"utf-8\"><title>Field proof</title></head>\n"
                + "<body>\n"
                + "<script src=\"classes.js\"></script>\n"
                + "<script>main();</script>\n"
                + "</body></html>\n").getBytes(StandardCharsets.UTF_8));

        Path profile = Files.createTempDirectory("zeroz-domproof");
        ProcessBuilder pb = new ProcessBuilder(
                browser,
                "--headless",
                "--disable-gpu",
                "--no-sandbox",
                "--no-first-run",
                "--disable-extensions",
                "--user-data-dir=" + profile.toAbsolutePath(),
                // An explicit window, because "is it visible" is measured from the box the
                // browser lays the element out in, and a window of no size gives everything a
                // box of no size.
                "--window-size=1280,900",
                "--virtual-time-budget=20000",
                "--dump-dom",
                page.toAbsolutePath().toUri().toString());
        pb.redirectErrorStream(false);
        Process process = pb.start();
        String output = read(process.getInputStream());
        if (!process.waitFor(120, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            fail("The browser did not finish rendering the field proof within two minutes.");
        }
        dumpedPage = output;

        Matcher m = Pattern.compile("<div class=\"proof-line\">(.*?)</div>", Pattern.DOTALL)
                .matcher(output);
        while (m.find()) {
            verdicts.add(unescape(m.group(1)));
        }
        deleteTree(profile.toFile());

        if (verdicts.isEmpty()) {
            fail("The proof page produced no verdicts at all. The page Chrome rendered was:\n"
                    + output);
        }
    }

    // -----------------------------------------------------------------

    @Test
    void theHarnessRanToTheEnd() {
        assertTrue(verdicts.contains("PASS|harness completed|"),
                "The page stopped part way through:\n" + report(verdicts));
    }

    @Test
    void everyFieldShowsItsCaptionAndTiesItToTheControl() {
        assertNoFailures("early:");
    }

    @Test
    void aFieldAlreadyOnThePageStillShowsAMessageAndACaptionAddedAfterwards() {
        assertNoFailures("late:");
    }

    @Test
    void aBrokenRulePutsAReadableSentenceOnTheScreenAndTakesItBack() {
        assertNoFailures("rule:");
    }

    @Test
    void aBinderFormSaysWhyItRefusedToSave() {
        assertNoFailures("Binder:");
    }

    /**
     * Eleven field types, proved in four ways each. A shrinking count means a component quietly
     * stopped being covered, which is how this kind of test rots.
     */
    @Test
    void everyFieldTypeWasActuallyExercised() {
        String[] kinds = {"TextField", "TextArea", "Select", "Checkbox", "Toggle",
                "RadioButtonGroup", "Range", "Rating", "FileInput", "Swap", "ThemeController"};
        for (String kind : kinds) {
            long seen = verdicts.stream().filter(v -> v.contains("|" + kind + " ")).count();
            assertTrue(seen >= 8, kind + " was checked only " + seen + " times; expected at "
                    + "least eight checks. Did it drop out of the proof page?");
        }
    }

    // -----------------------------------------------------------------

    private void assertNoFailures(String group) {
        List<String> failures = new ArrayList<>();
        int considered = 0;
        for (String verdict : verdicts) {
            if (!verdict.contains(group)) {
                continue;
            }
            considered++;
            if (verdict.startsWith("FAIL|")) {
                failures.add(verdict.substring("FAIL|".length()));
            }
        }
        assertTrue(considered > 0, "No check in this group ran at all.");
        assertTrue(failures.isEmpty(), failures.size() + " of " + considered
                + " checks failed in the browser:\n  " + String.join("\n  ", failures));
    }

    private static String report(List<String> lines) {
        return String.join("\n  ", lines);
    }

    private static String findBrowser() {
        String configured = System.getenv("CHROME_BIN");
        if (configured != null && new File(configured).canExecute()) {
            return configured;
        }
        String[] candidates = {
                "C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Google\\Chrome\\Application\\chrome.exe",
                "C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe",
                "/usr/bin/google-chrome",
                "/usr/bin/chromium",
                "/usr/bin/chromium-browser",
                "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
        };
        for (String candidate : candidates) {
            if (new File(candidate).isFile()) {
                return candidate;
            }
        }
        return null;
    }

    private static String read(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }

    private static String unescape(String html) {
        return html.replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'")
                .replace("&amp;", "&");
    }

    private static void deleteTree(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteTree(child);
            }
        }
        // A browser profile holds files open on Windows for a moment after exit; a leftover
        // temporary directory is not worth failing a test over.
        file.delete();
    }

    /** Kept for the failure message: the page as Chrome rendered it. */
    static String renderedPage() {
        return dumpedPage;
    }
}
