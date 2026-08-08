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
package com.zeroz4j.example.routing.client;

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.Link;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;

/**
 * Small builders so the route classes read as routing rather than as markup.
 *
 * <p>Not part of the framework — every example needs some of this, and keeping it here means the
 * views next door show only what routing actually requires.</p>
 */
final class Ui {

    private Ui() {}

    /** A block with the given children and Tailwind classes. */
    static Div box(String classes, Component... children) {
        Div div = new Div(children);
        for (String cls : classes.split(" ")) {
            if (!cls.isEmpty()) {
                div.addClassName(cls);
            }
        }
        return div;
    }

    /** Text with Tailwind classes. */
    static Span text(String value, String classes) {
        Span span = new Span(value);
        for (String cls : classes.split(" ")) {
            if (!cls.isEmpty()) {
                span.addClassName(cls);
            }
        }
        return span;
    }

    /**
     * An in-application link.
     *
     * <p>{@code data-route} is what opts the anchor into the router — without it the browser would
     * do a full page load, and links to other sites keep working normally.</p>
     */
    static Link routerLink(String path, String label) {
        Link link = new Link();
        link.setText(label);
        link.getElement().setAttribute("href", path);
        link.getElement().setAttribute("data-route", "");
        link.addClassName("link-primary");
        return link;
    }
}
