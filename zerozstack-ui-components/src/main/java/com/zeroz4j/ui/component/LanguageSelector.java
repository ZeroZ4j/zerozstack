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

import com.zeroz4j.api.Disposable;
import com.zeroz4j.api.i18n.ClientMessages;
import com.zeroz4j.api.i18n.FrameworkText;
import com.zeroz4j.api.i18n.Messages;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.Zeroz4jSignals;

import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLOptionElement;
import org.teavm.jso.dom.html.HTMLSelectElement;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lets somebody choose the language they read in. Drop it in a header or a side panel.
 *
 * <pre>{@code
 * sidebar.add(new LanguageSelector());
 * }</pre>
 *
 * <p>That is the whole of it. It offers the languages this deployment can actually answer in - the
 * list arrives with the words when the connection opens - so it can never offer one that would be
 * refused, and it binds itself to the language, because there is nothing else a language selector
 * could be bound to.</p>
 *
 * <h2>The keyboard is the browser's</h2>
 *
 * <p>It is a real {@code <select>}. Tab reaches it, the arrow keys move through the choices, typing
 * jumps to a language by its first letters, Enter takes the one highlighted and Escape leaves it
 * alone. None of that is written here, which is why none of it can be got wrong.</p>
 *
 * <h2>Each language is named in itself</h2>
 *
 * <p>German is offered as {@code Deutsch}, not as "German". Somebody who has landed on a page in a
 * language they cannot read is exactly the person who needs this control, and a list written in
 * the language they are stuck in is no help at all.</p>
 *
 * <p>The names are fixed words, not {@code Locale.getDisplayLanguage()}. That method works in the
 * browser but reads locale data the build has to compile in, which almost no build does - and it
 * would answer in the current language, which is the thing to avoid. A deployment can still change
 * a name, or add one this list has never heard of, by putting {@code language.<tag>} in its own
 * {@code i18n/zeroz4j_*.properties}.</p>
 *
 * <h2>What it is called</h2>
 *
 * <p>It announces itself as "Language" until {@link #setLabel} gives it words of its own, at which
 * point the built-in name goes away so the two cannot disagree. That name is a framework message,
 * so a deployment that translates the framework catalog gets it translated too.</p>
 *
 * @since 0.9.0
 */
public class LanguageSelector extends Select {

    /**
     * What each language calls itself.
     *
     * <p>Endonyms, which is why they can be one fixed list rather than one per language: Deutsch is
     * Deutsch on a French page too. About twenty bytes each, and a language not in the list falls
     * back to its own tag, which is ugly and readable - better than a blank line in a list somebody
     * is using because they cannot read the page.</p>
     */
    private static final Map<String, String> ENDONYMS = new LinkedHashMap<>();

    static {
        ENDONYMS.put("ar", "العربية");
        ENDONYMS.put("bg", "Български");
        ENDONYMS.put("cs", "Čeština");
        ENDONYMS.put("da", "Dansk");
        ENDONYMS.put("de", "Deutsch");
        ENDONYMS.put("el", "Ελληνικά");
        ENDONYMS.put("en", "English");
        ENDONYMS.put("es", "Español");
        ENDONYMS.put("et", "Eesti");
        ENDONYMS.put("fi", "Suomi");
        ENDONYMS.put("fr", "Français");
        ENDONYMS.put("he", "עברית");
        ENDONYMS.put("hi", "हिन्दी");
        ENDONYMS.put("hr", "Hrvatski");
        ENDONYMS.put("hu", "Magyar");
        ENDONYMS.put("id", "Bahasa Indonesia");
        ENDONYMS.put("it", "Italiano");
        ENDONYMS.put("ja", "日本語");
        ENDONYMS.put("ko", "한국어");
        ENDONYMS.put("lt", "Lietuvių");
        ENDONYMS.put("lv", "Latviešu");
        ENDONYMS.put("nb", "Norsk bokmål");
        ENDONYMS.put("nl", "Nederlands");
        ENDONYMS.put("pl", "Polski");
        ENDONYMS.put("pt", "Português");
        ENDONYMS.put("pt-br", "Português (Brasil)");
        ENDONYMS.put("ro", "Română");
        ENDONYMS.put("ru", "Русский");
        ENDONYMS.put("sk", "Slovenčina");
        ENDONYMS.put("sl", "Slovenščina");
        ENDONYMS.put("sr", "Српски");
        ENDONYMS.put("sv", "Svenska");
        ENDONYMS.put("th", "ไทย");
        ENDONYMS.put("tr", "Türkçe");
        ENDONYMS.put("uk", "Українська");
        ENDONYMS.put("vi", "Tiếng Việt");
        ENDONYMS.put("zh", "中文");
        ENDONYMS.put("zh-tw", "繁體中文");
    }

    /** The languages the options currently show, so the list is only rebuilt when it changes. */
    private List<String> rendered = new ArrayList<>();

    private final List<Disposable> disposables = new ArrayList<>();

    /**
     * Whether the built-in name is the one in use.
     *
     * <p>A signal rather than a field because the built-in name is a translated word: the effect
     * below has to re-read it when the language changes <em>and</em> when this changes, and one
     * signal read is how both get to be the same mechanism.</p>
     */
    private final com.zeroz4j.signals.ValueSignal<Boolean> useBuiltInName =
            new com.zeroz4j.signals.ValueSignal<>(Boolean.TRUE);

    /**
     * Builds a selector offering whatever this deployment can answer in, bound to the language.
     */
    public LanguageSelector() {
        super();
        setId("zeroz-language");
        // The list, the name and the chosen value all follow the language signal, so all three
        // come back the moment the words for a new language land.
        disposables.add(Effect.create(this::refresh));
        disposables.add(bindValue(Zeroz4jSignals.LOCALE.mine()));
    }

    /**
     * Gives the control words of your own, and takes the built-in name away so the two cannot
     * disagree. Passing null or nothing puts the built-in name back.
     *
     * @param label the caption to show beside the control
     */
    @Override
    public void setLabel(String label) {
        super.setLabel(label);
        useBuiltInName.set(Boolean.valueOf(label == null || label.isEmpty()));
    }

    /**
     * Releases the effects that keep the list and the name in the current language.
     */
    @Override
    protected void onDetach() {
        for (Disposable disposable : disposables) {
            disposable.dispose();
        }
        disposables.clear();
        super.onDetach();
    }

    /**
     * Rebuilds the options and the name for whatever language the screen is now in.
     *
     * <p>Reads the language signal, which is what subscribes this to a switch. The options
     * themselves do not change wording between languages - a language is named in itself - but the
     * <em>list</em> does change when the first catalog arrives, and the control's own name is a
     * translated word.</p>
     */
    private void refresh() {
        // The read that subscribes. Nothing is done with the value: which language it is, is on
        // the wire signal; that it changed is the whole of what this effect wants to know.
        ClientMessages.language();
        List<String> offered = ClientMessages.offeredLanguages();
        if (!offered.equals(rendered)) {
            rendered = new ArrayList<>(offered);
            rebuildOptions(rendered);
        }
        // The words are read here, inside the effect, rather than in a helper. A helper would be
        // correct and MessageReadContractTest could not see that it was: it reads one file's text
        // and cannot follow a call.
        if (useBuiltInName.get().booleanValue()) {
            getElement().setAttribute("aria-label", FrameworkText.uiLanguage().text());
        } else {
            getElement().removeAttribute("aria-label");
        }
    }

    private void rebuildOptions(List<String> languages) {
        HTMLSelectElement select = getElement().cast();
        String keep = select.getValue();
        while (select.getLastChild() != null) {
            select.removeChild(select.getLastChild());
        }
        for (String tag : languages) {
            HTMLOptionElement option = (HTMLOptionElement)
                    Window.current().getDocument().createElement("option");
            option.setValue(tag);
            option.setText(nameOf(tag));
            select.appendChild(option);
        }
        // Rebuilding empties the control, so the choice is put back. Without this the select drops
        // to its first option and the person appears to have chosen a language they did not.
        String current = getValue();
        String wanted = current != null && !current.isEmpty() ? current : keep;
        if (wanted != null && !wanted.isEmpty()) {
            select.setValue(wanted);
        }
    }

    /**
     * What a language calls itself.
     *
     * @param tag an IETF language tag
     * @return the deployment's own name for it, this library's, or the tag when neither has one
     */
    static String nameOf(String tag) {
        if (tag == null || tag.isEmpty()) {
            return "";
        }
        String key = asciiLower(tag);
        String deploymentsOwn = Messages.lookup(FrameworkText.CATALOG, "language." + key,
                ClientMessages.language());
        if (!deploymentsOwn.equals("language." + key)) {
            return deploymentsOwn;
        }
        String known = ENDONYMS.get(key);
        if (known != null) {
            return known;
        }
        int region = key.indexOf('-');
        if (region > 0) {
            known = ENDONYMS.get(key.substring(0, region));
            if (known != null) {
                return known + " (" + asciiUpper(key.substring(region + 1)) + ")";
            }
        }
        return tag;
    }

    /**
     * Lower-cases a language tag without asking what the current locale is.
     *
     * <p>A tag is ASCII by definition, and {@code String.toLowerCase()} with no locale answers
     * differently in Turkish - where a capital I lower-cases to a dotless one, so {@code ID} stops
     * being Indonesian.</p>
     */
    private static String asciiLower(String text) {
        char[] out = text.toCharArray();
        for (int at = 0; at < out.length; at++) {
            if (out[at] >= 'A' && out[at] <= 'Z') {
                out[at] = (char) (out[at] + 32);
            }
        }
        return new String(out);
    }

    private static String asciiUpper(String text) {
        char[] out = text.toCharArray();
        for (int at = 0; at < out.length; at++) {
            if (out[at] >= 'a' && out[at] <= 'z') {
                out[at] = (char) (out[at] - 32);
            }
        }
        return new String(out);
    }
}
