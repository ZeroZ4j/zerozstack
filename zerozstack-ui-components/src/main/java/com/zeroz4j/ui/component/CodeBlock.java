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

import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.theme.TextStyle;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.Set;

/**
 * Syntax-highlighted code with a header (language chip + copy button). The tokenizer is a
 * deliberately small single-pass scanner (comments, strings, numbers, keywords) — enough
 * to make code readable, zero external JS. All content enters the DOM as text nodes;
 * nothing from the model is ever parsed as HTML.
 */
public final class CodeBlock extends Div {

    private static final Set<String> JAVA_KEYWORDS = Set.of(
        "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
        "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
        "finally", "float", "for", "if", "implements", "import", "instanceof", "int",
        "interface", "long", "native", "new", "package", "private", "protected", "public",
        "record", "return", "sealed", "short", "static", "strictfp", "super", "switch",
        "synchronized", "this", "throw", "throws", "transient", "try", "var", "void",
        "volatile", "while", "yield", "true", "false", "null");

    private static final Set<String> YAML_JSON_KEYWORDS = Set.of("true", "false", "null");

    public CodeBlock(String language, String code) {
        this(language, code, false);
    }

    public CodeBlock(String language, String code, boolean lineNumbers) {
        String lang = language == null ? "" : language.trim().toLowerCase();
        addClassName("rounded-lg border border-base-300 bg-base-200 overflow-hidden my-1 "
            + "text-[13px] leading-relaxed");

        Div header = new Div();
        header.addClassName("flex items-center justify-between px-3 py-1 bg-base-300/50 "
            + TextStyle.CAPTION.getClassNames());
        Span langChip = new Span(lang.isEmpty() ? "text" : lang);
        langChip.addClassName("font-mono");
        // A real <button>, not a styled box. The browser puts a button in the tab order and
        // presses it on Enter and Space for nothing; a div with a click handler can only ever be
        // used with a mouse. The class list is the one the box had, minus the "btn" a Button adds
        // for itself, so it still reads as a quiet word in the header rather than a chunky button.
        Button copyButton = new Button();
        copyButton.setClassName("flex items-center gap-1 cursor-pointer hover:text-primary");
        copyButton.getElement().setAttribute("type", "button");
        copyButton.getElement().appendChild(Icon.of("copy", "w-3.5 h-3.5").getElement());
        Span copyLabel = new Span("Copy");
        // The word changes to "Copied" after the copy, and that is the only sign it worked. A
        // polite live region means somebody using a screen reader is told, instead of being left
        // wondering whether anything happened.
        copyLabel.getElement().setAttribute("aria-live", "polite");
        copyButton.getElement().appendChild(copyLabel.getElement());
        copyButton.getElement().addEventListener("click", threaded(e -> {
            Js.copyToClipboard(code);
            copyLabel.setText("Copied");
        }));
        header.getElement().appendChild(langChip.getElement());
        header.add(copyButton);
        add(header);

        Div scroll = new Div();
        scroll.addClassName("overflow-x-auto");
        HTMLElement pre = Window.current().getDocument().createElement("pre");
        pre.setClassName("m-0 p-3 font-mono whitespace-pre");
        String[] lines = (code == null ? "" : code).split("\n", -1);
        boolean inBlockComment = false;
        for (int i = 0; i < lines.length; i++) {
            HTMLElement lineEl = Window.current().getDocument().createElement("div");
            if (lineNumbers) {
                HTMLElement num = Window.current().getDocument().createElement("span");
                num.setClassName("inline-block w-8 pr-3 text-right select-none "
                + Emphasis.FAINT.getClassNames());
                num.appendChild(Window.current().getDocument().createTextNode(String.valueOf(i + 1)));
                lineEl.appendChild(num);
            }
            inBlockComment = renderLine(lineEl, lines[i], lang, inBlockComment);
            pre.appendChild(lineEl);
        }
        scroll.getElement().appendChild(pre);
        add(scroll);
    }

    /**
     * A comment is the one token in a code block that is quiet rather than coloured - strings,
     * numbers and keywords all name a colour - so it asks for the quietest step on the emphasis
     * scale instead of naming a faded colour of its own.
     */
    private static final String COMMENT_STYLE = Emphasis.FAINT.getClassNames() + " italic";

    /** Tokenizes one line into styled spans; returns whether a block comment is still open. */
    private static boolean renderLine(HTMLElement parent, String line, String lang, boolean inBlockComment) {
        int i = 0;
        int n = line.length();
        StringBuilder plain = new StringBuilder();
        while (i < n) {
            char c = line.charAt(i);
            if (inBlockComment) {
                int end = line.indexOf("*/", i);
                if (end < 0) {
                    emit(parent, plain);
                    token(parent, line.substring(i), COMMENT_STYLE);
                    return true;
                }
                emit(parent, plain);
                token(parent, line.substring(i, end + 2), COMMENT_STYLE);
                i = end + 2;
                inBlockComment = false;
            } else if (c == '/' && i + 1 < n && line.charAt(i + 1) == '/' && isCodeLang(lang)) {
                emit(parent, plain);
                token(parent, line.substring(i), COMMENT_STYLE);
                return false;
            } else if (c == '/' && i + 1 < n && line.charAt(i + 1) == '*' && isCodeLang(lang)) {
                emit(parent, plain);
                inBlockComment = true;
            } else if (c == '#' && (lang.equals("yaml") || lang.equals("yml") || lang.equals("sh")
                    || lang.equals("bash") || lang.equals("properties"))) {
                emit(parent, plain);
                token(parent, line.substring(i), COMMENT_STYLE);
                return false;
            } else if (c == '"' || c == '\'') {
                emit(parent, plain);
                int end = i + 1;
                while (end < n && (line.charAt(end) != c || line.charAt(end - 1) == '\\')) {
                    end++;
                }
                end = Math.min(end + 1, n);
                token(parent, line.substring(i, end), "text-success");
                i = end;
            } else if (Character.isDigit(c) && (i == 0 || !Character.isJavaIdentifierPart(line.charAt(i - 1)))) {
                emit(parent, plain);
                int end = i;
                while (end < n && (Character.isLetterOrDigit(line.charAt(end)) || line.charAt(end) == '.'
                        || line.charAt(end) == '_')) {
                    end++;
                }
                token(parent, line.substring(i, end), "text-warning");
                i = end;
            } else if (Character.isJavaIdentifierStart(c)) {
                int end = i;
                while (end < n && Character.isJavaIdentifierPart(line.charAt(end))) {
                    end++;
                }
                String word = line.substring(i, end);
                if (keywords(lang).contains(word)) {
                    emit(parent, plain);
                    token(parent, word, "text-primary font-semibold");
                } else {
                    plain.append(word);
                }
                i = end;
            } else {
                plain.append(c);
                i++;
            }
        }
        emit(parent, plain);
        if (parent.getFirstChild() == null) {
            // Zero-width space keeps an empty line's height.
            parent.appendChild(Window.current().getDocument().createTextNode("\u200b"));
        }
        return inBlockComment;
    }

    private static void emit(HTMLElement parent, StringBuilder plain) {
        if (plain.length() > 0) {
            parent.appendChild(Window.current().getDocument().createTextNode(plain.toString()));
            plain.setLength(0);
        }
    }

    private static void token(HTMLElement parent, String text, String classes) {
        HTMLElement span = Window.current().getDocument().createElement("span");
        span.setClassName(classes);
        span.appendChild(Window.current().getDocument().createTextNode(text));
        parent.appendChild(span);
    }

    private static boolean isCodeLang(String lang) {
        return lang.equals("java") || lang.equals("json") || lang.equals("js")
            || lang.equals("ts") || lang.equals("kotlin") || lang.equals("c") || lang.equals("cpp")
            || lang.isEmpty();
    }

    private static Set<String> keywords(String lang) {
        return switch (lang) {
            case "java", "kotlin" -> JAVA_KEYWORDS;
            case "json", "yaml", "yml" -> YAML_JSON_KEYWORDS;
            default -> Set.of();
        };
    }
}

