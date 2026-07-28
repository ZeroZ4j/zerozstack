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
import static org.junit.jupiter.api.Assertions.*;

public class ObjectMapperTest {

    @Test
    public void testRegistration() {
        ObjectMapper mapper = new ObjectMapper();
        Object obj1 = new Object();
        
        String id1 = mapper.register(obj1);
        assertNotNull(id1);
        
        // Idempotency: registering the same object again yields the same ID
        String id2 = mapper.register(obj1);
        assertEquals(id1, id2);
        
        // Getting ID directly
        assertEquals(id1, mapper.getId(obj1));
        
        // Getting Object directly
        assertSame(obj1, mapper.getObject(id1));
    }

    @Test
    public void testNullRegistration() {
        ObjectMapper mapper = new ObjectMapper();
        assertNull(mapper.register(null));
        assertNull(mapper.getId(null));
    }

    @Test
    public void testRegisterWithId() {
        ObjectMapper mapper = new ObjectMapper();
        Object obj = new Object();
        String customId = "custom-id-123";
        
        mapper.registerWithId(customId, obj);
        
        assertEquals(customId, mapper.getId(obj));
        assertSame(obj, mapper.getObject(customId));
    }

    @Test
    public void testRegisterWithIdNulls() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerWithId(null, new Object());
        mapper.registerWithId("id", null);
        // Should not throw exceptions and shouldn't store null keys/values that break
        assertNull(mapper.getObject(null));
    }
}
