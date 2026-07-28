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
import com.zeroz4j.api.LiveMutationTracker;
import com.zeroz4j.api.LiveObservable;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the reactive half of LiveSync: reading a live object's getter inside an {@link Effect} makes
 * it a dependency, and an inbound sync re-runs that effect — with no subscription and no polling.
 *
 * <p>{@code Profile} below stands in for the APT-generated {@code <Model>_Live} subclass; the
 * generated code has exactly this shape.</p>
 */
class LiveSignalTest {

    /** What APT generates: getters report reads, and the object carries its own signal. */
    static class Profile implements LiveObservable {
        private final LiveSignal zeroz4jSignal = new LiveSignal(this);
        private String mission = "initial";

        @Override
        public void zeroz4jLiveRead() {
            zeroz4jSignal.reportRead();
        }

        @Override
        public void zeroz4jLiveChanged() {
            zeroz4jSignal.notifyChanged();
        }

        String getMission() {
            zeroz4jLiveRead();
            return mission;
        }

        /** Stands in for the deserializer writing new field values in place. */
        void applyRemote(String value) {
            this.mission = value;
            LiveMutationTracker.remoteObjectUpdated(this);
        }

        LiveSignal signal() {
            return zeroz4jSignal;
        }
    }

    @Test
    void effectReRunsWhenTheLiveObjectIsSynced() {
        Profile profile = new Profile();
        List<String> rendered = new ArrayList<>();

        Disposable effect = Effect.create(() -> rendered.add(profile.getMission()));
        assertEquals(List.of("initial"), rendered, "the effect runs once immediately");

        profile.applyRemote("shipped");

        assertEquals(List.of("initial", "shipped"), rendered,
                "an inbound sync must re-run the effect that read the getter");
        effect.dispose();
    }

    @Test
    void computedOverALiveObjectRecomputes() {
        Profile profile = new Profile();
        Computed<Integer> length = new Computed<>(() -> profile.getMission().length());

        assertEquals(7, length.get());
        profile.applyRemote("shipped it");
        assertEquals(10, length.get(), "the computed must invalidate when the object is synced");

        length.dispose();
    }

    @Test
    void disposingTheEffectUnsubscribes() {
        Profile profile = new Profile();
        List<String> rendered = new ArrayList<>();

        Disposable effect = Effect.create(() -> rendered.add(profile.getMission()));
        assertEquals(1, profile.signal().listenerCount());

        effect.dispose();
        assertEquals(0, profile.signal().listenerCount(), "disposal must release the subscription");

        profile.applyRemote("ignored");
        assertEquals(1, rendered.size(), "a disposed effect must not re-run");
    }

    @Test
    void readingOutsideAnEffectTracksNothing() {
        Profile profile = new Profile();
        profile.getMission();
        assertEquals(0, profile.signal().listenerCount(),
                "a plain read must not register a dependency");
    }

    @Test
    void updatesDuringARemoteApplyAreBatched() {
        // An effect reading two objects from one frame should not re-run against a half-applied graph:
        // notifications are held until the whole sync has been applied.
        Profile first = new Profile();
        Profile second = new Profile();
        List<String> rendered = new ArrayList<>();

        Disposable effect = Effect.create(
                () -> rendered.add(first.getMission() + "|" + second.getMission()));
        assertEquals(1, rendered.size());

        LiveMutationTracker.beginRemoteApply();
        try {
            first.mission = "a";
            LiveMutationTracker.remoteObjectUpdated(first);
            second.mission = "b";
            LiveMutationTracker.remoteObjectUpdated(second);

            assertEquals(1, rendered.size(), "nothing should re-run mid-apply");
        } finally {
            LiveMutationTracker.endRemoteApply();
        }

        assertTrue(rendered.contains("a|b"),
                "after the apply completes the effect sees both updates, got " + rendered);
        effect.dispose();
    }

    @Test
    void repeatedUpdatesToOneObjectInAFrameNotifyOnce() {
        Profile profile = new Profile();
        List<String> rendered = new ArrayList<>();
        Disposable effect = Effect.create(() -> rendered.add(profile.getMission()));

        LiveMutationTracker.beginRemoteApply();
        try {
            profile.mission = "x";
            LiveMutationTracker.remoteObjectUpdated(profile);
            LiveMutationTracker.remoteObjectUpdated(profile);
            LiveMutationTracker.remoteObjectUpdated(profile);
        } finally {
            LiveMutationTracker.endRemoteApply();
        }

        assertEquals(2, rendered.size(),
                "one initial run plus one re-run, not one per report; got " + rendered);
        effect.dispose();
    }

    @Test
    void aPlainObjectIsIgnored() {
        // Models without @LiveSync have no generated subclass and must pass through harmlessly.
        LiveMutationTracker.remoteObjectUpdated(new Object());
        LiveMutationTracker.beginRemoteApply();
        try {
            LiveMutationTracker.remoteObjectUpdated("not live");
        } finally {
            LiveMutationTracker.endRemoteApply();
        }
    }
}
