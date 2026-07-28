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
package com.zeroz4j.example.server;

import com.zeroz4j.example.model.Product;
import com.zeroz4j.example.server.store.DataRoot;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each call to the storage manager is one commit. Writing the product list and the root in two calls
 * meant two commits: a crash in between saved the product and lost the id counter, so the next save
 * reused an id. These tests hold the write to a single call.
 */
class ProductServiceImplTest {

    /** Records what each persist call wrote, so the number of commits is observable. */
    private static final class RecordingStorage implements InvocationHandler {
        final DataRoot root = new DataRoot();
        final List<List<Object>> commits = new ArrayList<>();

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) {
            switch (method.getName()) {
                case "root":
                    return root;
                case "store":
                    commits.add(List.of(args[0]));
                    return 1L;
                case "storeAll":
                    Object first = args[0];
                    commits.add(first instanceof Object[]
                            ? Arrays.asList((Object[]) first)
                            : List.of(first));
                    return new long[0];
                case "toString":
                    return "RecordingStorage";
                case "equals":
                    return proxy == args[0];
                case "hashCode":
                    return System.identityHashCode(proxy);
                default:
                    return null;
            }
        }
    }

    private RecordingStorage storage;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        storage = new RecordingStorage();
        EmbeddedStorageManager proxy = (EmbeddedStorageManager) Proxy.newProxyInstance(
                EmbeddedStorageManager.class.getClassLoader(),
                new Class<?>[] {EmbeddedStorageManager.class},
                storage);

        service = new ProductServiceImpl();
        Field field = ProductServiceImpl.class.getDeclaredField("storage");
        field.setAccessible(true);
        field.set(service, proxy);
    }

    @Test
    void savingANewProductWritesTheListAndTheRootInOneCommit() {
        service.save(new Product(0, "Desk Lamp", "Furniture", 5, 19.99));

        assertEquals(1, storage.commits.size(),
                "the product and the id counter must be written in a single commit, "
                + "otherwise a crash between them loses the counter");
        List<Object> written = storage.commits.get(0);
        assertTrue(written.contains(storage.root.getProducts()), "the product list must be written");
        assertTrue(written.contains(storage.root), "the root must be written in the same commit");
    }

    @Test
    void savingANewProductAssignsAnIdAndBumpsTheCounter() {
        Product saved = service.save(new Product(0, "Desk Lamp", "Furniture", 5, 19.99));

        assertEquals(1L, saved.getId(), "the first product gets id 1");
        assertEquals(2L, storage.root.getNextId(), "the counter must advance past it");
        assertEquals(1, storage.root.getProducts().size());
    }

    @Test
    void twoSavesDoNotReuseAnId() {
        // The bug this guards against: with two commits, a crash after the list write and before the
        // root write left the counter behind, so the next save handed out an id already in use.
        Product first = service.save(new Product(0, "Desk Lamp", "Furniture", 5, 19.99));
        Product second = service.save(new Product(0, "Monitor Arm", "Furniture", 3, 89.00));

        assertEquals(1L, first.getId());
        assertEquals(2L, second.getId());
        assertEquals(3L, storage.root.getNextId());
    }

    @Test
    void seedingWritesTheListAndTheRootInOneCommit() {
        service.list();   // seeds on first read when the store is empty

        assertEquals(1, storage.commits.size(), "seeding must also be a single commit");
        List<Object> written = storage.commits.get(0);
        assertTrue(written.contains(storage.root.getProducts()));
        assertTrue(written.contains(storage.root));
        assertEquals(4, storage.root.getProducts().size());
        assertEquals(5L, storage.root.getNextId(), "the counter must follow the seeded products");
    }

    @Test
    void updatingAnExistingProductWritesOnlyTheList() {
        Product saved = service.save(new Product(0, "Desk Lamp", "Furniture", 5, 19.99));
        storage.commits.clear();

        service.save(new Product(saved.getId(), "Desk Lamp", "Furniture", 12, 24.99));

        assertEquals(1, storage.commits.size());
        assertEquals(12, storage.root.getProducts().get(0).getQuantity(), "the update must be applied");
        assertEquals(2L, storage.root.getNextId(), "an update must not advance the id counter");
    }

    @Test
    void deletingWritesOnlyTheList() {
        Product saved = service.save(new Product(0, "Desk Lamp", "Furniture", 5, 19.99));
        storage.commits.clear();

        service.delete(saved.getId());

        assertEquals(1, storage.commits.size());
        assertTrue(storage.root.getProducts().isEmpty());
    }
}
