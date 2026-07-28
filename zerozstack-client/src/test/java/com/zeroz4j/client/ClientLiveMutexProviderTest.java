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
package com.zeroz4j.client;

import com.zeroz4j.api.LiveMutex;
import com.zeroz4j.api.RmiClientExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ClientLiveMutexProviderTest {

    private ClientLiveMutexProvider provider;
    private List<CallRecord> calls;

    static class CallRecord {
        String iface;
        String method;
        Object[] args;
        CallRecord(String iface, String method, Object[] args) {
            this.iface = iface;
            this.method = method;
            this.args = args;
        }
    }

    @BeforeEach
    public void setup() {
        calls = new ArrayList<>();
        RmiClientExecutor.setInstance((iface, method, args) -> {
            calls.add(new CallRecord(iface, method, args));
            return null;
        });
        provider = new ClientLiveMutexProvider();
    }

    @Test
    public void testLockAndUnlock() throws Exception {
        Object sharedObject = new Object();
        LiveMutex mutex = provider.create(sharedObject);
        
        mutex.lock();
        
        assertEquals(1, calls.size());
        assertEquals("com.zeroz4j.api.LiveMutexRpc", calls.get(0).iface);
        assertEquals("acquireLock", calls.get(0).method);
        String id = (String) calls.get(0).args[0];
        assertNotNull(id);
        
        calls.clear();
        mutex.unlock();
        
        assertEquals(1, calls.size());
        assertEquals("com.zeroz4j.api.LiveMutexRpc", calls.get(0).iface);
        assertEquals("releaseLock", calls.get(0).method);
        assertEquals(id, calls.get(0).args[0]);
    }
}
