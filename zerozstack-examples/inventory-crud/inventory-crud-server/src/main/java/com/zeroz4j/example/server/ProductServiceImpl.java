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

import com.zeroz4j.example.api.ProductService;
import com.zeroz4j.example.model.Product;
import com.zeroz4j.example.server.store.DataRoot;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class ProductServiceImpl implements ProductService {

    @Inject
    private EmbeddedStorageManager storage;

    private DataRoot getRoot() {
        return (DataRoot) storage.root();
    }

    @Override
    public List<Product> list() {
        DataRoot root = getRoot();
        if (root.getProducts().isEmpty()) {
            seedInitialProducts(root);
        }
        return new ArrayList<>(root.getProducts());
    }

    @Override
    public Product save(Product p) {
        DataRoot root = getRoot();
        if (p.getId() == 0) {
            long newId = root.getNextId() <= 0 ? 1 : root.getNextId();
            p.setId(newId);
            root.setNextId(newId + 1);
            root.getProducts().add(p);
            // One call, one commit. Two separate store() calls are two commits: a crash between
            // them would persist the product and lose the nextId bump.
            storage.storeAll(root.getProducts(), root);
        } else {
            for (Product existing : root.getProducts()) {
                if (existing.getId() == p.getId()) {
                    existing.setName(p.getName());
                    existing.setCategory(p.getCategory());
                    existing.setQuantity(p.getQuantity());
                    existing.setUnitPrice(p.getUnitPrice());
                    break;
                }
            }
            storage.store(root.getProducts());
        }
        return p;
    }

    @Override
    public void delete(long id) {
        DataRoot root = getRoot();
        root.getProducts().removeIf(prod -> prod.getId() == id);
        storage.store(root.getProducts());
    }

    private void seedInitialProducts(DataRoot root) {
        long nextId = root.getNextId() <= 0 ? 1 : root.getNextId();

        Product p1 = new Product(nextId++, "Wireless Ergonomic Mouse", "Electronics", 45, 29.99);
        Product p2 = new Product(nextId++, "Electric Standing Desk", "Furniture", 12, 349.50);
        Product p3 = new Product(nextId++, "USB-C Multi-Port Hub", "Electronics", 80, 49.95);
        Product p4 = new Product(nextId++, "Ergonomic Mesh Chair", "Furniture", 18, 199.00);

        root.getProducts().add(p1);
        root.getProducts().add(p2);
        root.getProducts().add(p3);
        root.getProducts().add(p4);
        root.setNextId(nextId);

        // One call, one commit - see save().
        storage.storeAll(root.getProducts(), root);
    }
}
