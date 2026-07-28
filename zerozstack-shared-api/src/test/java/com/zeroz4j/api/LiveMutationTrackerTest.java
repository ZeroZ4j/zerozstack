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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class LiveMutationTrackerTest {

    @AfterEach
    public void cleanup() {
        LiveMutationTracker.install(null);
        LiveMutationTracker.endRemoteApply();
        BinaryRegistry.setPreferLiveInstances(false);
    }

    @Test
    public void testListenerReceivesChangesAndTouch() {
        List<Object> changed = new ArrayList<>();
        LiveMutationTracker.install(changed::add);

        Object model = new Object();
        LiveMutationTracker.fieldChanged(model);
        LiveMutationTracker.touch(model);

        assertEquals(2, changed.size());
        assertSame(model, changed.get(0));
    }

    @Test
    public void testRemoteApplySuppressesReporting() {
        List<Object> changed = new ArrayList<>();
        LiveMutationTracker.install(changed::add);

        LiveMutationTracker.beginRemoteApply();
        try {
            LiveMutationTracker.fieldChanged(new Object());
        } finally {
            LiveMutationTracker.endRemoteApply();
        }
        assertTrue(changed.isEmpty(), "Inbound applies must not be reported as writes");

        LiveMutationTracker.fieldChanged(new Object());
        assertEquals(1, changed.size(), "Reporting resumes after the apply");
    }

    @Test
    public void testNoListenerIsNoOp() {
        LiveMutationTracker.fieldChanged(new Object()); // must not throw
    }

    @Test
    public void testLiveInstantiationPreference() {
        BinaryRegistry.register("live.test.Model", Object::new,
                new BinarySerializerDelegate<Object>() {
                    @Override public void write(Object obj, GrowableBuffer buffer, ObjectMapper mapper) {}
                    @Override public void read(Object obj, java.nio.ByteBuffer buffer, ObjectMapper mapper) {}
                });
        BinaryRegistry.registerLive("live.test.Model", ArrayList::new);

        assertTrue(BinaryRegistry.create("live.test.Model") instanceof Object);
        assertTrue(!(BinaryRegistry.create("live.test.Model") instanceof ArrayList),
                "Plain instances by default");

        BinaryRegistry.setPreferLiveInstances(true);
        assertTrue(BinaryRegistry.create("live.test.Model") instanceof ArrayList,
                "Live instances when preferred (client tier)");
    }
}
