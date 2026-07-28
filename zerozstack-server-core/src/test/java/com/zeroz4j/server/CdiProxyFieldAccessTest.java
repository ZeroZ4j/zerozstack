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
package com.zeroz4j.server;

import com.zeroz4j.api.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;

@EnableWeld
public class CdiProxyFieldAccessTest {

    @WeldSetup
    public WeldInitiator weld = WeldInitiator.of(
            SyncEngine.class,
            TestConsumer.class,
            ObjectMapper.class
    );

    @ApplicationScoped
    public static class TestConsumer {
        @Inject
        SyncEngine syncEngine;

        public ObjectMapper getMapperFromProxyField() {
            // This reproduces the exact bug described:
            // Direct field access on a CDI proxy bypasses the proxy handler
            // and returns the uninitialized field of the proxy subclass.
            return syncEngine.mapper;
        }
    }

    @Inject
    TestConsumer consumer;

    @Test
    public void testDirectFieldAccessOnCdiProxyReturnsNull() {
        // Reproduces the issue where accessing an injected field
        // directly from another bean's proxy reference yields null, causing NPEs.
        ObjectMapper mapper = consumer.getMapperFromProxyField();
        
        // This assertion verifies the bug is present (mapper is null due to CDI proxy).
        assertNull(mapper, "Direct field access on an @ApplicationScoped CDI proxy should return null");
    }
}
