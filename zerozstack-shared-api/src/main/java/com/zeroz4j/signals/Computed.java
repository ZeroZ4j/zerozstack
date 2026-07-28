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

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.ArrayList;
import java.util.List;

/**
 * Lazily evaluated reactive computation signal derived from one or more upstream signals.
 *
 * <p>Tracks upstream dependencies automatically during evaluation and invalidates its cached result when any dependency changes.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Lazy Evaluation:</b> Evaluation is deferred until {@link #get()} is called while {@code dirty == true}.</li>
 *   <li><b>Dynamic Dependency Tracking:</b> Uses {@link Effect#startTracking(Consumer)} to dynamically capture dependencies during execution of the computation {@link Supplier}.</li>
 * </ul>
 *
 * @param <T> result value type
 */
public class Computed<T> implements ObservableSignal<T> {

    private final Supplier<T> computation;
    private T cachedValue;
    private boolean dirty = true;
    private boolean disposed = false;
    private final List<ObservableSignal<Object>> dependencies = new ArrayList<>();

    // S6 fix: typed Consumer<Object> instead of raw Consumer.
    @SuppressWarnings("unchecked")
    private final Consumer<Object> invalidator = (val) -> {
        if (!this.dirty) {
            this.dirty = true;
            notifyListeners();
        }
    };

    private final List<Consumer<T>> listeners = new ArrayList<>();

    /**
     * Constructs a new {@link Computed} signal with the specified derivation supplier logic.
     *
     * @param computation derivation supplier lambda
     */
    public Computed(Supplier<T> computation) {
        this.computation = computation;
    }

    /**
     * Reads the current computed value, re-evaluating the underlying computation if dirty.
     *
     * @return current cached or freshly evaluated value
     *
     * <p><b>Under the hood:</b> Registers dependency via {@link Effect#registerDependency(Signal)}. If {@code dirty}, executes {@link #evaluate()} and returns {@code cachedValue}.</p>
     */
    @Override
    public T get() {
        Effect.registerDependency(this);
        if (dirty) {
            evaluate();
        }
        return cachedValue;
    }

    private void evaluate() {
        for (ObservableSignal<Object> dep : dependencies) {
            dep.removeListener(invalidator);
        }
        dependencies.clear();

        Effect.startTracking(this::addDependency);
        try {
            cachedValue = computation.get();
            dirty = false;
        } finally {
            Effect.stopTracking();
        }
    }

    @SuppressWarnings("unchecked")
    private void addDependency(Signal<?> dep) {
        if (disposed) {
            return;
        }
        if (dep instanceof ObservableSignal) {
            ObservableSignal<Object> observable = (ObservableSignal<Object>) dep;
            dependencies.add(observable);
            observable.addListener(invalidator);
        }
    }

    /**
     * Disposes this computed signal, unsubscribing it from all upstream dependencies and
     * dropping its downstream listeners.
     *
     * <p>After disposal the signal stops receiving invalidations: reads return the last
     * computed value (recomputing once if it was dirty at disposal time) and no listeners
     * are notified. Call this when the owning view or component is permanently removed
     * to avoid upstream signals holding a strong reference to this computation.</p>
     */
    public void dispose() {
        disposed = true;
        for (ObservableSignal<Object> dep : dependencies) {
            dep.removeListener(invalidator);
        }
        dependencies.clear();
        listeners.clear();
    }

    /**
     * Adds a listener callback invoked when this computed value changes.
     *
     * @param listener change listener callback
     */
    @Override
    public void addListener(Consumer<T> listener) {
        listeners.add(listener);
    }

    /**
     * Removes a listener callback.
     *
     * @param listener change listener callback
     */
    @Override
    public void removeListener(Consumer<T> listener) {
        listeners.remove(listener);
    }

    // S4 fix: re-evaluate (via get()) before notifying, so listeners receive the current value.
    private void notifyListeners() {
        T currentValue = get();
        List<Consumer<T>> currentListeners = new ArrayList<>(listeners);
        for (Consumer<T> listener : currentListeners) {
            listener.accept(currentValue);
        }
    }
}
