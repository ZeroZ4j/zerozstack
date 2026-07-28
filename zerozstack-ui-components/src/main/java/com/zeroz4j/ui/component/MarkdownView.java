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

import com.zeroz4j.ui.layout.Div;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.List;
import org.teavm.jso.dom.xml.Text;

/**
 * Renders LLM markdown: headings, bold/italic, inline code, fenced code (ÃƒÂ¢Ã¢â‚¬Â Ã¢â‚¬â„¢ CodeBlock),
 * lists, blockquotes, tables, links, rules. Everything is built from text nodes ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â model
 * output is untrusted and no HTML ever passes through. Small by design; if the model
 * emits something exotic it degrades to readable plain text.
 */
public final class MarkdownView extends Div {

    public MarkdownView(String markdown) {
        addClassName("md-view text-sm leading-relaxed space-y-2 break-words");
        render(markdown == null ? "" : markdown);
    }

    private void render(String markdown) {
        String[] lines = markdown.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i];
            String trimmed = line.trim();

            if (trimmed.startsWith("```")) {
                String lang = trimmed.substring(3).trim();
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) {
                    code.append(lines[i]).append('\n');
                    i++;
                }
                i++; // closing fence
                if ("diff".equals(lang)) {
                    add(new DiffView(code.toString()));
                } else {
                    add(new CodeBlock(lang, stripTrailingNewline(code.toString())));
                }
            } else if (trimmed.startsWith("#")) {
                int level = 0;
                while (level < trimmed.length() && trimmed.charAt(level) == '#') {
                    level++;
                }
                HTMLElement h = element("div", switch (Math.min(level, 4)) {
                    case 1 -> "text-xl font-bold mt-3";
                    case 2 -> "text-lg font-bold mt-2";
                    case 3 -> "text-base font-semibold mt-2";
                    default -> "text-sm font-semibold mt-1";
                });
                inline(h, trimmed.substring(level).trim());
                getElement().appendChild(h);
                i++;
            } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")
                    || startsWithOrderedMarker(trimmed)) {
                boolean ordered = startsWithOrderedMarker(trimmed);
                HTMLElement list = element(ordered ? "ol" : "ul",
                    (ordered ? "list-decimal" : "list-disc") + " list-inside space-y-0.5 pl-1");
                while (i < lines.length) {
                    String item = lines[i].trim();
                    if (item.startsWith("- ") || item.startsWith("* ")) {
                        HTMLElement li = element("li", "");
                        inline(li, item.substring(2));
                        list.appendChild(li);
                        i++;
                    } else if (startsWithOrderedMarker(item)) {
                        HTMLElement li = element("li", "");
                        inline(li, item.substring(item.indexOf('.') + 1).trim());
                        list.appendChild(li);
                        i++;
                    } else {
                        break;
                    }
                }
                getElement().appendChild(list);
            } else if (trimmed.startsWith(">")) {
                HTMLElement quote = element("div",
                    "border-l-4 border-base-300 pl-3 text-base-content/70 italic");
                StringBuilder text = new StringBuilder();
                while (i < lines.length && lines[i].trim().startsWith(">")) {
                    text.append(lines[i].trim().substring(1).trim()).append(' ');
                    i++;
                }
                inline(quote, text.toString().trim());
                getElement().appendChild(quote);
            } else if (trimmed.equals("---") || trimmed.equals("***")) {
                getElement().appendChild(element("div", "border-t border-base-300 my-2"));
                i++;
            } else if (trimmed.startsWith("|") && i + 1 < lines.length
                    && lines[i + 1].trim().startsWith("|")) {
                i = renderTable(lines, i);
            } else if (trimmed.isEmpty()) {
                i++;
            } else {
                HTMLElement p = element("div", "");
                StringBuilder text = new StringBuilder(trimmed);
                i++;
                while (i < lines.length && !lines[i].trim().isEmpty()
                        && !isBlockStart(lines[i].trim())) {
                    text.append(' ').append(lines[i].trim());
                    i++;
                }
                inline(p, text.toString());
                getElement().appendChild(p);
            }
        }
    }

    private int renderTable(String[] lines, int start) {
        List<String[]> rows = new ArrayList<>();
        int i = start;
        while (i < lines.length && lines[i].trim().startsWith("|")) {
            String row = lines[i].trim();
            row = row.substring(1, row.endsWith("|") ? row.length() - 1 : row.length());
            rows.add(row.split("\\|", -1));
            i++;
        }
        HTMLElement wrap = element("div", "overflow-x-auto");
        HTMLElement table = element("table", "table table-xs table-zebra w-auto");
        for (int r = 0; r < rows.size(); r++) {
            String[] cells = rows.get(r);
            if (r == 1 && isSeparatorRow(cells)) {
                continue;
            }
            HTMLElement tr = element("tr", "");
            for (String cell : cells) {
                HTMLElement td = element(r == 0 ? "th" : "td", "");
                inline(td, cell.trim());
                tr.appendChild(td);
            }
            table.appendChild(tr);
        }
        wrap.appendChild(table);
        getElement().appendChild(wrap);
        return i;
    }

    /** Inline spans: `code`, **bold**, *italic*, [text](url). Text-node construction only. */
    private static void inline(HTMLElement parent, String text) {
        int i = 0;
        int n = text.length();
        StringBuilder plain = new StringBuilder();
        while (i < n) {
            char c = text.charAt(i);
            if (c == '`') {
                int end = text.indexOf('`', i + 1);
                if (end > i) {
                    flush(parent, plain);
                    HTMLElement code = element("code",
                        "bg-base-300/60 rounded px-1 font-mono text-[0.85em]");
                    code.appendChild(textNode(text.substring(i + 1, end)));
                    parent.appendChild(code);
                    i = end + 1;
                    continue;
                }
            } else if (c == '*' && i + 1 < n && text.charAt(i + 1) == '*') {
                int end = text.indexOf("**", i + 2);
                if (end > i) {
                    flush(parent, plain);
                    HTMLElement bold = element("strong", "font-semibold");
                    inline(bold, text.substring(i + 2, end));
                    parent.appendChild(bold);
                    i = end + 2;
                    continue;
                }
            } else if (c == '*') {
                int end = text.indexOf('*', i + 1);
                if (end > i + 1) {
                    flush(parent, plain);
                    HTMLElement em = element("em", "italic");
                    inline(em, text.substring(i + 1, end));
                    parent.appendChild(em);
                    i = end + 1;
                    continue;
                }
            } else if (c == '[') {
                int mid = text.indexOf("](", i);
                int end = mid > 0 ? text.indexOf(')', mid) : -1;
                if (mid > 0 && end > mid) {
                    String url = text.substring(mid + 2, end).trim();
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        flush(parent, plain);
                        HTMLElement a = element("a", "link link-primary");
                        a.setAttribute("href", url);
                        a.setAttribute("target", "_blank");
                        a.setAttribute("rel", "noopener noreferrer");
                        a.appendChild(textNode(text.substring(i + 1, mid)));
                        parent.appendChild(a);
                        i = end + 1;
                        continue;
                    }
                }
            }
            plain.append(c);
            i++;
        }
        flush(parent, plain);
    }

    private static boolean isBlockStart(String trimmed) {
        return trimmed.startsWith("#") || trimmed.startsWith("```") || trimmed.startsWith("- ")
            || trimmed.startsWith("* ") || trimmed.startsWith(">") || trimmed.startsWith("|")
            || startsWithOrderedMarker(trimmed);
    }

    private static boolean startsWithOrderedMarker(String s) {
        int dot = s.indexOf('.');
        if (dot <= 0 || dot > 3 || dot + 1 >= s.length() || s.charAt(dot + 1) != ' ') {
            return false;
        }
        for (int i = 0; i < dot; i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSeparatorRow(String[] cells) {
        for (String cell : cells) {
            String t = cell.trim();
            if (!t.isEmpty() && !t.chars().allMatch(ch -> ch == '-' || ch == ':')) {
                return false;
            }
        }
        return true;
    }

    private static String stripTrailingNewline(String s) {
        return s.endsWith("\n") ? s.substring(0, s.length() - 1) : s;
    }

    private static HTMLElement element(String tag, String classes) {
        HTMLElement el = Window.current().getDocument().createElement(tag);
        if (!classes.isEmpty()) {
            el.setClassName(classes);
        }
        return el;
    }

    private static Text textNode(String text) {
        return Window.current().getDocument().createTextNode(text);
    }

    private static void flush(HTMLElement parent, StringBuilder plain) {
        if (plain.length() > 0) {
            parent.appendChild(textNode(plain.toString()));
            plain.setLength(0);
        }
    }
}

