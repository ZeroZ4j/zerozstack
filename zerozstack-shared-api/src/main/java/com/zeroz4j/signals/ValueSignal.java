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
package com.zeroz4j.signals;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * Mutable reactive state variable holding a single value.
 *
 * <p>Notifies registered listeners and invalidates dependent {@link Computed} or {@link Effect} computations whenever its value changes.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Dependency Registration:</b> {@link #get()} notifies {@link Effect#registerDependency(Signal)} if an active tracking context is present.</li>
 *   <li><b>Deduplication:</b> {@link #set(Object)} ignores updates if the new value equals the current value (using {@code Object.equals}).</li>
 * </ul>
 *
 * @param <T> value type
 */
public class ValueSignal<T> implements ObservableSignal<T> {

    private T value;
    private final List<Consumer<T>> listeners = new ArrayList<>();

    /**
     * Constructs a new {@link ValueSignal} with the specified initial value.
     *
     * @param initialValue initial reactive value
     */
    public ValueSignal(T initialValue) {
        this.value = initialValue;
    }

    /**
     * Reads the current reactive value, registering a dependency on any active tracking effect or computed context.
     *
     * @return current value
     *
     * <p><b>Under the hood:</b> Calls {@link Effect#registerDependency(Signal)} passing {@code this}, then returns {@code value}.</p>
     */
    @Override
    public T get() {
        Effect.registerDependency(this);
        synchronized (this) {
            return value;
        }
    }

    /**
     * Updates the reactive value and notifies all registered listeners if the new value differs from the existing value.
     *
     * @param newValue new value to assign
     *
     * <p><b>Under the hood:</b> Checks equality (`this.value == newValue || equals`). Assigns {@code value} and calls {@link #notifyListeners()}.</p>
     */
    public void set(T newValue) {
        if (assignIfChanged(newValue)) {
            notifyListeners();
        }
    }

    /**
     * Mutates the value by applying a transformation function.
     *
     * @param updater transformation operator function
     */
    public void update(UnaryOperator<T> updater) {
        T current;
        synchronized (this) {
            current = value;
        }
        set(updater.apply(current));
    }

    /**
     * Adds a change listener callback.
     *
     * @param listener change listener
     */
    @Override
    public void addListener(Consumer<T> listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a change listener callback.
     *
     * @param listener change listener
     */
    @Override
    public void removeListener(Consumer<T> listener) {
        synchronized (listeners) {
            listeners.remove(listener);
        }
    }

    /**
     * Assigns the value if it differs from the current one (by identity or equals),
     * without notifying listeners.
     *
     * @return true if the value changed
     */
    synchronized boolean assignIfChanged(T newValue) {
        if (this.value == newValue || (this.value != null && this.value.equals(newValue))) {
            return false;
        }
        this.value = newValue;
        return true;
    }

    void notifyListeners() {
        List<Consumer<T>> currentListeners;
        synchronized (listeners) {
            currentListeners = new ArrayList<>(listeners);
        }
        T currentValue;
        synchronized (this) {
            currentValue = value;
        }
        for (Consumer<T> listener : currentListeners) {
            listener.accept(currentValue);
        }
    }
}
