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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two different declarations colliding on one shared-signal wire name used to be resolved by silently
 * keeping the first, so a signal quietly carried another declaration's initial value, writability and
 * roles. The default wire name is the payload's class name, which makes the collision easy to hit.
 */
class SharedSignalConflictTest {

    static class Temperature {
        final int degrees;

        Temperature(int degrees) {
            this.degrees = degrees;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Temperature && ((Temperature) other).degrees == degrees;
        }

        @Override
        public int hashCode() {
            return degrees;
        }
    }

    @BeforeEach
    void reset() {
        Signals.resetForTesting();
    }

    @Test
    void collidingDeclarationsAreRefused() {
        Signals.shared(new Temperature(20));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> Signals.shared(new Temperature(5)));

        assertTrue(error.getMessage().contains("Conflicting declaration"), error.getMessage());
        assertTrue(error.getMessage().contains("explicit names"),
                "the message must say how to fix it: " + error.getMessage());
    }

    @Test
    void explicitNamesLetTheSameTypeHaveSeveralSignals() {
        ValueSignal<Temperature> indoor = Signals.shared("temp.indoor", new Temperature(20));
        ValueSignal<Temperature> outdoor = Signals.shared("temp.outdoor", new Temperature(5));

        assertEquals(20, indoor.get().degrees);
        assertEquals(5, outdoor.get().degrees);
    }

    @Test
    void reRunningTheSameDeclarationIsStillIdempotent() {
        // A constant's initializer may legitimately be evaluated more than once across classloaders;
        // an identical declaration must not be treated as a conflict.
        ValueSignal<Temperature> first = Signals.shared("temp.indoor", new Temperature(20));
        ValueSignal<Temperature> second = Signals.shared("temp.indoor", new Temperature(20));

        assertSame(first, second);
    }

    @Test
    void changingWritabilityIsAConflict() {
        Signals.shared("doc.title", "Untitled");

        assertThrows(IllegalStateException.class,
                () -> Signals.sharedWritable("doc.title", "Untitled"));
    }

    @Test
    void changingRolesIsAConflict() {
        Signals.sharedWritable("doc.title", "Untitled", "editor");

        assertThrows(IllegalStateException.class,
                () -> Signals.sharedWritable("doc.title", "Untitled", "admin"));
    }

    @Test
    void identicalWritableDeclarationsStillMatch() {
        ValueSignal<String> first = Signals.sharedWritable("doc.title", "Untitled", "editor");
        ValueSignal<String> second = Signals.sharedWritable("doc.title", "Untitled", "editor");

        assertSame(first, second);
    }

    @Test
    void aLaterWriteDoesNotMakeTheDeclarationConflict() {
        // The conflict check compares DECLARED initial values, not current ones, so ordinary use of a
        // signal must not turn a later identical declaration into an error.
        ValueSignal<String> signal = Signals.shared("doc.title", "Untitled");
        signal.set("Changed");

        assertSame(signal, Signals.shared("doc.title", "Untitled"));
    }
}
