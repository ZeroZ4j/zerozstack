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
import com.zeroz4j.signals.Effect;
import com.zeroz4j.ui.theme.Theme;

/**
 * Light, Dark and System as three buttons in a row, for a settings screen. Reads and writes
 * {@link Theme}, so it needs nothing beyond {@code Theme.start()} in {@code main}.
 */
public class ThemeSelector extends Join {

    private final Button[] buttons = new Button[Theme.Choice.values().length];
    private Disposable follow;

    /** Creates the control with the current choice selected. */
    public ThemeSelector() {
        getElement().setAttribute("role", "group");
        getElement().setAttribute("aria-label", "Theme");
        for (Theme.Choice choice : Theme.Choice.values()) {
            String text = choice.name().charAt(0) + choice.name().substring(1).toLowerCase();
            Button button = new Button(text);
            button.addClassName("join-item btn-outline");
            button.addClickListener(event -> Theme.choose(choice));
            buttons[choice.ordinal()] = button;
            add(button);
        }
        show(Theme.choice().get());
    }

    /** Marks the chosen button in place, so the keyboard stays on the button that was pressed. */
    private void show(Theme.Choice current) {
        for (Theme.Choice choice : Theme.Choice.values()) {
            Button button = buttons[choice.ordinal()];
            boolean on = choice == current;
            if (on) {
                button.addClassName("btn-active");
            } else {
                button.removeClassName("btn-active");
            }
            button.getElement().setAttribute("aria-pressed", on ? "true" : "false");
        }
    }

    @Override
    protected void onAttach() {
        follow = Effect.create(() -> show(Theme.choice().get()));
    }

    @Override
    protected void onDetach() {
        if (follow != null) {
            follow.dispose();
            follow = null;
        }
    }
}
