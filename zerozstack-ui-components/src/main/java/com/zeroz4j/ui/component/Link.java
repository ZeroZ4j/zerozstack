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

import com.zeroz4j.ui.component.mixin.HasColorVariants;

/**
 * A link goes somewhere.
 *
 * <p>Give it a destination. An {@code <a>} with no {@code href} is not a link: the browser leaves
 * it out of the tab order, so it cannot be reached by keyboard at all, and a screen reader reads
 * it as ordinary text. It looks exactly like a link and nobody notices until somebody puts the
 * mouse down.</p>
 *
 * <pre>{@code
 * Link docs = new Link("Read the guide", "/docs/guide");
 * }</pre>
 *
 * <p><b>If it does something rather than going somewhere, it is a {@link Button}.</b> Saving,
 * deleting, opening a panel and switching a tab are all buttons, however small and quiet they are
 * meant to look - {@code btn-link} makes a button look exactly like a link, which is the right way
 * round.</p>
 */
public class Link extends Component implements HasText, HasComponents, HasStyle, HasSize,
        HasColorVariants<Link> {

    public Link() {
        super("a");
        addClassName("link");
    }

    /** A link with words and somewhere to go, which is what a link is. */
    public Link(String text, String href) {
        this();
        setText(text);
        setHref(href);
    }

    /**
     * Where this link goes.
     *
     * @param href the address, or null to take it away - which also takes the link out of the tab
     *             order, so only do that to something nobody is meant to follow
     */
    public void setHref(String href) {
        if (href == null || href.isEmpty()) {
            getElement().removeAttribute("href");
        } else {
            getElement().setAttribute("href", href);
        }
    }

    /** The address this link goes to, or null when it has none. */
    public String getHref() {
        return getElement().getAttribute("href");
    }

    /** {@link #setHref(String)}, returning the link so it reads inside the expression that builds it. */
    public Link withHref(String href) {
        setHref(href);
        return this;
    }

    @Override
    public Component getComponent() {
        return this;
    }

    @Override
    public String getThemePrefix() {
        return "link";
    }
}

