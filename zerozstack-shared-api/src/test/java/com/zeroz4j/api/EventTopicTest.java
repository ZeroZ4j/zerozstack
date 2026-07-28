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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class EventTopicTest {

    @Test
    public void testOfBindsNameAndPayloadType() {
        EventTopic<String> topic = EventTopic.of(String.class, "test.topic");
        assertEquals("test.topic", topic.name());
        assertEquals(String.class, topic.payloadType());
    }

    @Test
    public void testEqualityIsNameBased() {
        EventTopic<String> a = EventTopic.of(String.class, "same.name");
        EventTopic<String> b = EventTopic.of(String.class, "same.name");
        EventTopic<String> c = EventTopic.of(String.class, "other.name");

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
    }

    @Test
    public void testOfRejectsInvalidArguments() {
        assertThrows(IllegalArgumentException.class, () -> EventTopic.of(null, "x"));
        assertThrows(IllegalArgumentException.class, () -> EventTopic.of(String.class, null));
        assertThrows(IllegalArgumentException.class, () -> EventTopic.of(String.class, "  "));
    }
}
