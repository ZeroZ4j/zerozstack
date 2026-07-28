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

import com.zeroz4j.ui.component.Component;
import com.zeroz4j.ui.component.HasComponents;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.Signal;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Binds a {@code Signal<List<T>>} to a container's children, patching by key instead of
 * rebuilding: unchanged rows keep their DOM (and their event listeners, focus, animation
 * state), removed keys unmount, new keys mount in order. The standard way every dynamic
 * list in the Console renders.
 */
public final class KeyedList<T> {

    private final HasComponents container;
    private final Function<T, String> keyOf;
    private final Function<T, Component> render;
    /** May be null: rows that support in-place update implement it to avoid re-rendering. */
    private final Updater<T> updater;
    private final Map<String, Component> mounted = new HashMap<>();

    public interface Updater<T> {
        void update(Component existing, T newItem);
    }

    public KeyedList(HasComponents container, Signal<List<T>> source,
                     Function<T, String> keyOf, Function<T, Component> render) {
        this(container, source, keyOf, render, null);
    }

    public KeyedList(HasComponents container, Signal<List<T>> source,
                     Function<T, String> keyOf, Function<T, Component> render,
                     Updater<T> updater) {
        this.container = container;
        this.keyOf = keyOf;
        this.render = render;
        this.updater = updater;
        Effect.create(() -> patch(source.get()));
    }

    private void patch(List<T> items) {
        if (items == null) {
            items = List.of();
        }
        Map<String, T> wanted = new LinkedHashMap<>();
        for (T item : items) {
            wanted.put(keyOf.apply(item), item);
        }
        // Unmount removed keys.
        mounted.keySet().removeIf(key -> {
            if (!wanted.containsKey(key)) {
                container.remove(mounted.get(key));
                return true;
            }
            return false;
        });
        // Mount new keys / update existing, then fix order by re-appending in sequence
        // (appendChild moves an already-attached node ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â cheap and order-correct).
        for (Map.Entry<String, T> entry : wanted.entrySet()) {
            Component existing = mounted.get(entry.getKey());
            if (existing == null) {
                Component fresh = render.apply(entry.getValue());
                mounted.put(entry.getKey(), fresh);
                container.add(fresh);
            } else if (updater != null) {
                updater.update(existing, entry.getValue());
            }
        }
        for (String key : wanted.keySet()) {
            Component component = mounted.get(key);
            container.getComponent().getElement().appendChild(component.getElement());
        }
    }
}

