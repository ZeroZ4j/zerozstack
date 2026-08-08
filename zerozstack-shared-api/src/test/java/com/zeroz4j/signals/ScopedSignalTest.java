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

import com.zeroz4j.api.Scope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A shared signal is one value everyone agrees on. A scoped signal is one value per tenant, user,
 * browser or session — these tests pin the declaration rules and the per-target isolation, with no
 * transport installed, exactly as an application's own unit tests would run.
 */
class ScopedSignalTest {

    @BeforeEach
    @AfterEach
    void reset() {
        Signals.resetForTesting();
    }

    @Test
    void eachTargetKeepsItsOwnValue() {
        ScopedSignal<String> basket = Signals.scoped("shop.basket", "empty", Scope.CLIENT);

        basket.forTarget("browser-a").set("one apple");

        assertEquals("one apple", basket.forTarget("browser-a").get());
        assertEquals("empty", basket.forTarget("browser-b").get(),
                "another browser must see its own value, not the first one's");
    }

    @Test
    void aTargetIsCreatedOnceAndReused() {
        ScopedSignal<String> basket = Signals.scoped("shop.basket", "empty", Scope.CLIENT);

        assertSame(basket.forTarget("browser-a"), basket.forTarget("browser-a"));
        assertNotSame(basket.forTarget("browser-a"), basket.forTarget("browser-b"));
    }

    @Test
    void anUntouchedTargetStartsFromTheDeclaredValue() {
        ScopedSignal<Integer> counter = Signals.scoped("app.counter", 7, Scope.TENANT);

        assertEquals(7, counter.forTarget("acme").get());
        assertTrue(counter.knownTargets().contains("acme"));
        assertTrue(counter.knownTargets().size() == 1);
    }

    @Test
    void redeclaringTheSameFamilyReturnsIt() {
        ScopedSignal<String> first = Signals.scoped("app.state", "idle", Scope.USER);
        ScopedSignal<String> second = Signals.scoped("app.state", "idle", Scope.USER);

        assertSame(first, second, "re-running a declaration must not produce a second family");
    }

    @Test
    void aConflictingRedeclarationIsRefused() {
        Signals.scoped("app.state", "idle", Scope.USER);

        // Silently keeping the first would leave the second caller holding a signal with a scope it
        // did not ask for -- which is a data-leak shape, not a style problem.
        assertThrows(IllegalStateException.class,
                () -> Signals.scoped("app.state", "idle", Scope.TENANT));
        assertThrows(IllegalStateException.class,
                () -> Signals.scoped("app.state", "busy", Scope.USER));
        assertThrows(IllegalStateException.class,
                () -> Signals.scopedWritable("app.state", "idle", Scope.USER));
    }

    @Test
    void globalScopeIsRefusedBecauseThatIsJustASharedSignal() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> Signals.scoped("app.state", "idle", Scope.GLOBAL));

        assertTrue(error.getMessage().contains("Signals.shared"),
                "the message should name the thing to use instead: " + error.getMessage());
    }

    @Test
    void aNameCannotBeBothSharedAndScoped() {
        Signals.shared("app.state", "idle");

        assertThrows(IllegalStateException.class,
                () -> Signals.scoped("app.state", "idle", Scope.USER));
    }

    @Test
    void aNameCannotBeBothScopedAndShared() {
        Signals.scoped("app.state", "idle", Scope.USER);

        assertThrows(IllegalStateException.class, () -> Signals.shared("app.state", "idle"));
    }

    @Test
    void aBlankTargetIsRefusedRatherThanSilentlyPooled() {
        ScopedSignal<String> state = Signals.scoped("app.state", "idle", Scope.TENANT);

        // The usual cause is an anonymous session having no tenant. Bucketing every such caller
        // under one empty-string target would silently share their state with each other.
        assertThrows(IllegalArgumentException.class, () -> state.forTarget(null));
        assertThrows(IllegalArgumentException.class, () -> state.forTarget("  "));
    }

    @Test
    void writeRolesAreCarriedOnTheFamily() {
        ScopedSignal<String> state =
                Signals.scopedWritable("app.state", "idle", Scope.CLIENT, "editor");

        assertTrue(state.isClientWritable());
        assertEquals(java.util.Set.of("editor"), state.writeRoles());
    }

    @Test
    void withNoTransportBothTiersWork() {
        // A plain unit test has no transport, so neither tier check applies and the signal behaves
        // locally -- the same fallback a plain shared signal already has.
        ScopedSignal<String> state = Signals.scoped("app.state", "idle", Scope.CLIENT);

        state.forTarget("browser-a").set("busy");

        assertEquals("idle", state.mine().get(),
                "the mirror is its own instance until a transport connects it to a target");
    }
}
