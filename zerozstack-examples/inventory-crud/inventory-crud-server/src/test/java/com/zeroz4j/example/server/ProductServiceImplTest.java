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

import com.zeroz4j.db.net.ZeroZDbNode;
import com.zeroz4j.example.model.Product;
import com.zeroz4j.example.server.store.DataRoot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the service against a real store rather than a mock.
 *
 * <p>The previous version recorded calls to a fake storage manager and asserted that the product
 * list and the id counter were written in the <em>same</em> call, because two calls meant two
 * commits and a crash between them lost the counter. That hazard no longer exists to test for: a
 * command commits everything it enlists atomically or nothing at all, so the property is now
 * structural. What is worth testing is the behaviour a user relies on — and this checks it by
 * reopening the store, which a mock could never do.</p>
 */
class ProductServiceImplTest {

    @TempDir
    Path storeDir;

    private ZeroZDbNode db;
    private ProductServiceImpl service;

    @BeforeEach
    void setUp() throws Exception {
        db = ZeroZDbNode.embedded(storeDir.resolve("store"), DataRoot::new);
        service = new ProductServiceImpl();
        inject(service, db);
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
    }

    private static void inject(ProductServiceImpl target, ZeroZDbNode node) throws Exception {
        Field field = ProductServiceImpl.class.getDeclaredField("db");
        field.setAccessible(true);
        field.set(target, node);
    }

    @Test
    void listSeedsTheCatalogueOnFirstUseAndNotAgain() {
        assertEquals(4, service.list().size(), "first call seeds the catalogue");
        assertEquals(4, service.list().size(), "seeding must not repeat");
    }

    @Test
    void savingANewProductAssignsAnId() {
        service.list();
        Product saved = service.save(new Product(0, "Desk Lamp", "Furniture", 5, 19.99));
        assertNotEquals(0, saved.getId(), "a new product is given an id");
    }

    @Test
    void twoSavesDoNotReuseAnId() {
        service.list();
        long first = service.save(new Product(0, "A", "X", 1, 1.0)).getId();
        long second = service.save(new Product(0, "B", "X", 1, 1.0)).getId();
        assertNotEquals(first, second, "the id counter advanced with the insert");
    }

    @Test
    void anUpdateChangesTheProductInPlace() {
        service.list();
        Product saved = service.save(new Product(0, "Old name", "X", 1, 1.0));

        service.save(new Product(saved.getId(), "New name", "X", 2, 2.0));

        Product found = service.list().stream()
                .filter(p -> p.getId() == saved.getId())
                .findFirst().orElseThrow();
        assertEquals("New name", found.getName());
        assertEquals(2, found.getQuantity());
    }

    @Test
    void deleteRemovesTheProduct() {
        service.list();
        Product saved = service.save(new Product(0, "Doomed", "X", 1, 1.0));

        service.delete(saved.getId());

        assertFalse(service.list().stream().anyMatch(p -> p.getId() == saved.getId()));
    }

    /**
     * The property the old mock-based assertions were really reaching for: after a restart, the
     * product and the counter that named it are both present. A save that persisted one without
     * the other would hand out a duplicate id here.
     */
    @Test
    void productsAndTheIdCounterSurviveAReopen() throws Exception {
        service.list();
        long firstId = service.save(new Product(0, "Persisted", "X", 1, 1.0)).getId();
        int countBefore = service.list().size();
        db.close();

        db = ZeroZDbNode.embedded(storeDir.resolve("store"), DataRoot::new);
        service = new ProductServiceImpl();
        inject(service, db);

        assertEquals(countBefore, service.list().size(), "the catalogue survived the restart");
        long nextId = service.save(new Product(0, "After restart", "X", 1, 1.0)).getId();
        assertTrue(nextId > firstId, "the counter survived too, so ids are not reused");
    }
}
