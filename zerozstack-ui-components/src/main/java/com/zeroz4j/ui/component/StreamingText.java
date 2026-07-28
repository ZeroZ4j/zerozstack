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
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.Signal;

/**
 * Text that grows as tokens stream in, with a blinking caret while live. Bind it to the
 * chat's streaming signal; call {@link #done()} when the message completes (caret stops).
 */
public final class StreamingText extends Div {

    private final Span text = new Span();
    private final Span caret = new Span("ÃƒÂ¢Ã¢â‚¬â€œÃ‚Â");

    public StreamingText() {
        addClassName("whitespace-pre-wrap break-words");
        caret.addClassName("animate-pulse text-primary");
        getElement().appendChild(text.getElement());
        getElement().appendChild(caret.getElement());
    }

    public StreamingText bind(Signal<String> streaming) {
        Effect.create(() -> {
            String value = streaming.get();
            text.setText(value == null ? "" : value);
        });
        return this;
    }

    public void done() {
        caret.setVisible(false);
    }

    public void live() {
        caret.setVisible(true);
    }
}

