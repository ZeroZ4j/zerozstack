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
package com.zeroz4j.api;

/**
 * Implemented by the APT-generated {@code <Model>_Live} subclass of every {@code @LiveSync} model, so
 * that a synced object participates in signal dependency tracking exactly like a
 * {@code ValueSignal} does.
 *
 * <p>Applications never implement or call this. You write a plain POJO; the annotation processor
 * generates the subclass, overrides the getters to report reads, and the framework reports inbound
 * syncs as changes. The effect is that this just works, with no subscription and no polling:</p>
 *
 * <pre>{@code
 * Effect.create(() -> label.setText(profile.getMission()));
 * }</pre>
 *
 * <p>Granularity is <b>per object</b>, not per field: any inbound sync touching the instance re-runs
 * every effect that read any of its getters.</p>
 *
 * <p>Declared here rather than in {@code com.zeroz4j.signals} so that this package never has to
 * depend on the signals package — the generated subclass supplies the signal implementation, which
 * keeps the dependency pointing one way.</p>
 */
public interface LiveObservable {

    /**
     * Registers this instance as a dependency of the effect or computed currently being tracked.
     * Called by every generated getter; a no-op when nothing is tracking.
     */
    void zeroz4jLiveRead();

    /**
     * Notifies everything that read this instance that its fields have changed. Called by
     * {@link LiveMutationTracker} after an inbound sync has been applied.
     */
    void zeroz4jLiveChanged();
}
