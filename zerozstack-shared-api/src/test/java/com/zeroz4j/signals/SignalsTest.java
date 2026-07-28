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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SignalsTest {

    static class RecordingTransport implements SignalTransport {
        final List<String> created = new ArrayList<>();
        final List<Object> afterSets = new ArrayList<>();
        boolean allowSet = true;

        @Override
        public void onSharedSignalCreated(SharedValueSignal<?> signal) {
            created.add(signal.name());
        }

        @Override
        public boolean canSet(SharedValueSignal<?> signal) {
            return allowSet;
        }

        @Override
        public void afterSet(SharedValueSignal<?> signal, Object newValue) {
            afterSets.add(newValue);
        }
    }

    @BeforeEach
    @AfterEach
    public void reset() {
        Signals.resetForTesting();
    }

    @Test
    public void testSharedActsLocalWithoutTransport() {
        ValueSignal<String> signal = Signals.shared("test.local", "a");
        List<String> observed = new ArrayList<>();
        Effect.create(() -> observed.add(signal.get()));

        signal.set("b");
        assertEquals(List.of("a", "b"), observed);
    }

    @Test
    public void testSharedDerivesNameFromValueClass() {
        ValueSignal<String> signal = Signals.shared("hello");
        assertSame(signal, Signals.lookup("java.lang.String"));
        assertEquals("hello", signal.get());

        assertThrows(IllegalArgumentException.class, () -> Signals.shared(null));
    }

    @Test
    public void testCollidingDefaultNameIsRefused() {
        // The default wire name is the payload's class name, so a second unnamed declaration of the
        // same type collides. This used to be resolved by silently keeping the first, leaving the
        // second declaration's value, writability and roles quietly discarded.
        Signals.shared("hello");
        assertThrows(IllegalStateException.class, () -> Signals.shared("world"));
    }

    @Test
    public void testSharedIsIdempotentForAnIdenticalDeclaration() {
        ValueSignal<String> first = Signals.shared("test.same", "x");
        ValueSignal<String> second = Signals.shared("test.same", "x");
        assertSame(first, second);
        assertEquals("x", second.get());
    }

    @Test
    public void testSharedRefusesAConflictingRedeclaration() {
        Signals.shared("test.same", "x");
        assertThrows(IllegalStateException.class, () -> Signals.shared("test.same", "different"));
    }

    @Test
    public void testTransportInstallationReplaysExistingSignals() {
        Signals.shared("test.early", 1);
        RecordingTransport transport = new RecordingTransport();
        Signals.installTransport(transport);
        assertEquals(List.of("test.early"), transport.created);

        Signals.shared("test.late", 2);
        assertEquals(List.of("test.early", "test.late"), transport.created);
    }

    @Test
    public void testAfterSetFiresOnlyOnActualChange() {
        RecordingTransport transport = new RecordingTransport();
        Signals.installTransport(transport);
        ValueSignal<String> signal = Signals.shared("test.dedup", "a");

        signal.set("a"); // unchanged -> no broadcast
        assertTrue(transport.afterSets.isEmpty());

        signal.set("b");
        assertEquals(List.of("b"), transport.afterSets);
    }

    @Test
    public void testSharedWritableCarriesAuthorityMetadata() {
        Signals.sharedWritable("test.writable", "x", "admin", "editor");
        SharedValueSignal<?> handle = Signals.lookup("test.writable");
        assertTrue(handle.isClientWritable());
        assertEquals(java.util.Set.of("admin", "editor"), handle.writeRoles());

        Signals.shared("test.readonly", "y");
        assertTrue(!Signals.lookup("test.readonly").isClientWritable());
    }

    @Test
    public void testWritableSignalPassesTransportCanSet() {
        RecordingTransport transport = new RecordingTransport() {
            @Override
            public boolean canSet(SharedValueSignal<?> signal) {
                return signal.isClientWritable(); // mimic the client transport
            }
        };
        Signals.installTransport(transport);

        ValueSignal<String> readOnly = Signals.shared("test.ro", "a");
        ValueSignal<String> writable = Signals.sharedWritable("test.rw", "a");

        assertThrows(IllegalStateException.class, () -> readOnly.set("blocked"));

        writable.set("accepted");
        assertEquals("accepted", writable.get(), "Optimistic local apply");
        assertEquals(List.of("accepted"), transport.afterSets, "Write forwarded to transport");
    }

    @Test
    public void testCanSetFalseBlocksLocalWritesButNotRemoteApplies() {
        RecordingTransport transport = new RecordingTransport();
        transport.allowSet = false;
        Signals.installTransport(transport);
        ValueSignal<String> mirror = Signals.shared("test.mirror", "initial");

        assertThrows(IllegalStateException.class, () -> mirror.set("local write"));
        assertEquals("initial", mirror.get());

        List<String> observed = new ArrayList<>();
        Effect.create(() -> observed.add(mirror.get()));

        SharedValueSignal<?> handle = Signals.lookup("test.mirror");
        handle.applyRemote("from server");

        assertEquals("from server", mirror.get());
        assertEquals(List.of("initial", "from server"), observed, "Remote apply must notify effects");
        assertTrue(transport.afterSets.isEmpty(), "Remote apply must not re-broadcast");
    }
}
