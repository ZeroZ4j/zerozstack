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

import com.zeroz4j.signals.Signal;

public interface HasValue<T> {

    T getValue();
    void setValue(T value);
    
    /**
     * Binds this field two-way to a {@link com.zeroz4j.signals.ValueSignal}: the field follows the
     * signal, and user edits are written back.
     *
     * @param signal the signal to bind to; must be writable
     * @return a {@link com.zeroz4j.api.Disposable} releasing the binding
     * @throws IllegalArgumentException if the signal cannot be written to, which would make the
     *         binding silently one-way — use {@link #bindValueReadOnly} for that
     */
    com.zeroz4j.api.Disposable bindValue(Signal<T> signal);

    /**
     * Binds this field one-way: it follows the signal, and user edits are not written back.
     *
     * @param signal any signal, including a {@link com.zeroz4j.signals.Computed}
     * @return a {@link com.zeroz4j.api.Disposable} releasing the binding
     */
    com.zeroz4j.api.Disposable bindValueReadOnly(Signal<T> signal);
    
    /**
     * Registers a listener notified whenever this field's value changes.
     *
     * <p>Implementations MUST support this: {@link com.zeroz4j.ui.binding.Binder} relies on it to
     * write field edits through to the bound bean. {@link AbstractField} implements it, so any field
     * extending {@code AbstractField} works with {@code Binder} automatically.</p>
     *
     * <p>The default throws rather than silently doing nothing — a no-op here would make
     * {@code Binder.setBean(...)} appear to work while never writing anything to the bean.</p>
     *
     * @param listener the listener to add
     * @throws UnsupportedOperationException if the implementation does not support change listeners
     */
    default void addValueChangeListener(ValueChangeListener<T> listener) {
        throw new UnsupportedOperationException(getClass().getName()
                + " does not support value change listeners. Extend AbstractField, or override "
                + "addValueChangeListener/removeValueChangeListener, otherwise Binder cannot write "
                + "this field back to the bean.");
    }

    /**
     * Unregisters a listener previously added with {@link #addValueChangeListener}.
     *
     * @param listener the listener to remove; unknown listeners are ignored
     * @throws UnsupportedOperationException if the implementation does not support change listeners
     */
    default void removeValueChangeListener(ValueChangeListener<T> listener) {
        throw new UnsupportedOperationException(getClass().getName()
                + " does not support value change listeners.");
    }
    
    @FunctionalInterface
    interface ValueChangeListener<T> {
        void valueChanged(ValueChangeEvent<T> event);
    }
    
    class ValueChangeEvent<T> {
        private final HasValue<T> source;
        private final T oldValue;
        private final T value;
        private final boolean isFromClient;

        public ValueChangeEvent(HasValue<T> source, T oldValue, T value, boolean isFromClient) {
            this.source = source;
            this.oldValue = oldValue;
            this.value = value;
            this.isFromClient = isFromClient;
        }

        public HasValue<T> getHasValue() { return source; }
        public T getOldValue() { return oldValue; }
        public T getValue() { return value; }
        public boolean isFromClient() { return isFromClient; }
    }
}
