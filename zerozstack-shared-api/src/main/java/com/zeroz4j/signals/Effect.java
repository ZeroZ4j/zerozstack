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

import com.zeroz4j.api.Disposable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Side-effect runner in zeroz4j's Signals framework.
 *
 * <p>Executes a block of code (an effect) and automatically tracks any {@link Signal} instances read during execution,
 * re-running the effect whenever any tracked signal changes.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>ThreadLocal Tracking Stack:</b> Uses {@link ThreadLocal} stack ({@code trackingStack}) to manage nested dependency tracking contexts safely across threads.</li>
 *   <li><b>Re-entrancy Guard:</b> Prevents infinite loops if an effect mutates a signal it reads during execution.</li>
 *   <li><b>Disposal:</b> Returns a {@link Disposable} handle to unsubscribe from all signals and prevent memory leaks.</li>
 * </ul>
 */
public class Effect {

    // S1 fix: ThreadLocal per-thread tracking stack instead of a shared static Stack.
    private static final ThreadLocal<ArrayDeque<Consumer<Signal<?>>>> trackingStack =
            ThreadLocal.withInitial(ArrayDeque::new);

    /**
     * Registers a signal dependency on the current top of the tracking stack if active.
     *
     * @param signal target signal being read
     */
    public static void registerDependency(Signal<?> signal) {
        Consumer<Signal<?>> current = trackingStack.get().peek();
        if (current != null) {
            current.accept(signal);
        }
    }

    /**
     * Pushes a new dependency consumer onto the per-thread tracking stack.
     *
     * @param dependencyConsumer consumer callback receiving signals read during tracking
     */
    public static void startTracking(Consumer<Signal<?>> dependencyConsumer) {
        trackingStack.get().push(dependencyConsumer);
    }

    /**
     * Pops the top dependency consumer from the per-thread tracking stack.
     */
    public static void stopTracking() {
        ArrayDeque<Consumer<Signal<?>>> stack = trackingStack.get();
        if (!stack.isEmpty()) {
            stack.pop();
        }
    }

    /**
     * Creates a reactive effect that automatically runs and re-runs {@code effectRunner} whenever any
     * signal read within the runner is updated.
     *
     * @param effectRunner the effect logic to run reactively
     * @return a {@link Disposable} handle to unsubscribe and dispose of the effect
     *
     * <p><b>Under the hood:</b> Instantiates a subscription list, initializes re-entrancy flags, executes {@link #runEffect},
     * and returns a disposable lambda calling {@link #removeAllSubscriptions(List)}.</p>
     */
    public static Disposable create(Runnable effectRunner) {
        List<SignalSubscription> subscriptions = new ArrayList<>();

        boolean[] isRunning = {false};
        boolean[] pendingRerun = {false};
        boolean[] disposed = {false};

        Consumer<Object> invalidator = new Consumer<Object>() {
            @Override
            public void accept(Object o) {
                if (disposed[0]) {
                    return;
                }
                if (isRunning[0]) {
                    // Schedule a re-run after the current execution finishes
                    pendingRerun[0] = true;
                    return;
                }
                runEffect(effectRunner, this, subscriptions, isRunning, pendingRerun, disposed);
            }
        };

        runEffect(effectRunner, invalidator, subscriptions, isRunning, pendingRerun, disposed);

        return () -> {
            disposed[0] = true;
            removeAllSubscriptions(subscriptions);
        };
    }

    @SuppressWarnings("unchecked")
    private static void runEffect(Runnable effectRunner,
                                  Consumer<Object> invalidator,
                                  List<SignalSubscription> subscriptions,
                                  boolean[] isRunning,
                                  boolean[] pendingRerun,
                                  boolean[] disposed) {
        // Remove old subscriptions before re-tracking.
        removeAllSubscriptions(subscriptions);

        startTracking(signal -> {
            if (signal instanceof ObservableSignal) {
                ObservableSignal<Object> observable = (ObservableSignal<Object>) signal;
                subscriptions.add(new SignalSubscription(observable, invalidator));
                observable.addListener(invalidator);
            }
        });

        isRunning[0] = true;
        try {
            effectRunner.run();
        } finally {
            isRunning[0] = false;
            stopTracking();
        }

        // If a signal write during the run triggered a re-run request, honour it now.
        if (pendingRerun[0] && !disposed[0]) {
            pendingRerun[0] = false;
            runEffect(effectRunner, invalidator, subscriptions, isRunning, pendingRerun, disposed);
        }
    }

    private static void removeAllSubscriptions(List<SignalSubscription> subscriptions) {
        for (SignalSubscription sub : subscriptions) {
            sub.signal.removeListener(sub.listener);
        }
        subscriptions.clear();
    }

    private static class SignalSubscription {
        final ObservableSignal<Object> signal;
        final Consumer<Object> listener;

        SignalSubscription(ObservableSignal<Object> signal, Consumer<Object> listener) {
            this.signal = signal;
            this.listener = listener;
        }
    }
}
