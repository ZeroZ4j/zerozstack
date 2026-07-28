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
import com.zeroz4j.ui.layout.Span;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.List;

/**
 * Unified-diff renderer: one collapsible section per file with +N ÃƒÂ¢Ã‹â€ Ã¢â‚¬â„¢M badges, colored
 * add/remove gutters, hunk headers. Understands git-style headers (diff --git, ---, +++).
 * This is a CODE diff ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â DaisyUI's own `diff` class is an image-compare widget.
 */
public final class DiffView extends Div {

    private record FileDiff(String path, List<String> lines, int adds, int dels) {}

    public DiffView(String unifiedDiff) {
        addClassName("space-y-2 my-1");
        List<FileDiff> files = parse(unifiedDiff == null ? "" : unifiedDiff);
        if (files.isEmpty()) {
            add(new EmptyState("diff", "No changes", "The diff is empty."));
            return;
        }
        for (FileDiff file : files) {
            add(fileSection(file));
        }
    }

    private static List<FileDiff> parse(String diff) {
        List<FileDiff> files = new ArrayList<>();
        String currentPath = null;
        List<String> current = new ArrayList<>();
        int adds = 0;
        int dels = 0;
        for (String line : diff.split("\n", -1)) {
            if (line.startsWith("diff --git") || line.startsWith("--- ")) {
                if (line.startsWith("diff --git") && currentPath != null) {
                    files.add(new FileDiff(currentPath, current, adds, dels));
                    currentPath = null;
                    current = new ArrayList<>();
                    adds = 0;
                    dels = 0;
                }
                continue;
            }
            if (line.startsWith("+++ ")) {
                if (currentPath != null) {
                    files.add(new FileDiff(currentPath, current, adds, dels));
                    current = new ArrayList<>();
                    adds = 0;
                    dels = 0;
                }
                String path = line.substring(4).trim();
                currentPath = path.startsWith("b/") ? path.substring(2) : path;
                continue;
            }
            if (currentPath == null && !line.startsWith("@@")) {
                continue; // preamble (index lines, mode changes)
            }
            if (currentPath == null) {
                currentPath = "(unknown file)";
            }
            current.add(line);
            if (line.startsWith("+")) {
                adds++;
            } else if (line.startsWith("-")) {
                dels++;
            }
        }
        if (currentPath != null) {
            files.add(new FileDiff(currentPath, current, adds, dels));
        }
        return files;
    }

    private static Div fileSection(FileDiff file) {
        Div section = new Div();
        section.addClassName("rounded-lg border border-base-300 overflow-hidden");

        Div header = new Div();
        header.addClassName("flex items-center gap-2 px-3 py-1.5 bg-base-200 cursor-pointer "
            + "hover:bg-base-300/60 text-sm");
        Icon chevron = Icon.of("chevron-down", "w-3.5 h-3.5 transition-transform");
        Span path = new Span(file.path());
        path.addClassName("font-mono text-xs flex-1 truncate");
        Span addBadge = new Span("+" + file.adds());
        addBadge.addClassName("text-success text-xs font-mono");
        Span delBadge = new Span("ÃƒÂ¢Ã‹â€ Ã¢â‚¬â„¢" + file.dels());
        delBadge.addClassName("text-error text-xs font-mono");
        header.add(chevron);
        header.getElement().appendChild(path.getElement());
        header.getElement().appendChild(addBadge.getElement());
        header.getElement().appendChild(delBadge.getElement());

        Div body = new Div();
        body.addClassName("overflow-x-auto");
        HTMLElement pre = Window.current().getDocument().createElement("pre");
        pre.setClassName("m-0 font-mono text-[12px] leading-relaxed whitespace-pre");
        for (String line : file.lines()) {
            HTMLElement row = Window.current().getDocument().createElement("div");
            String cls;
            if (line.startsWith("@@")) {
                cls = "px-3 text-info bg-info/5";
            } else if (line.startsWith("+")) {
                cls = "px-3 bg-success/10 text-success";
            } else if (line.startsWith("-")) {
                cls = "px-3 bg-error/10 text-error";
            } else {
                cls = "px-3 text-base-content/70";
            }
            row.setClassName(cls);
            row.appendChild(Window.current().getDocument()
                .createTextNode(line.isEmpty() ? " " : line));
            pre.appendChild(row);
        }
        body.getElement().appendChild(pre);

        header.getElement().addEventListener("click", e -> {
            boolean visible = !"none".equals(body.getElement().getStyle().getPropertyValue("display"));
            body.setVisible(!visible);
            chevron.setStyle("transform", visible ? "rotate(-90deg)" : "rotate(0deg)");
        });

        section.add(header, body);
        return section;
    }
}

