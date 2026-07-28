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
import com.zeroz4j.db.WriteContext;
import com.zeroz4j.db.net.DbCommand;

/**
 * Writes expressed as commands.
 *
 * <p>Each one runs inside a single atomic transaction: everything it enlists is committed
 * together or not at all. That is what makes the id counter and the product list impossible to
 * separate — the bug this example used to have, where a crash between two saves persisted the
 * product and lost the counter that named it.</p>
 *
 * <p>These are plain classes with public fields and no-arg constructors, <b>not records</b>.
 * EclipseStore's serializer reaches fields directly and the JVM refuses that for records unless
 * started with {@code --add-exports java.base/jdk.internal.misc=ALL-UNNAMED}. A record fails at
 * the first remote call rather than at compile time, so it looks like a networking fault.</p>
 *
 * <p>Because they are commands rather than lambdas, they execute wherever the data lives: in this
 * process when {@code zeroz4j.store.mode=EMBEDDED}, on the database server when {@code CLIENT}.
 * The service code above does not change either way.</p>
 */
public final class ProductCommands {

    /** Allocates an id and inserts the product in one commit. */
    public static final class Create implements DbCommand<Product> {
        public Product product;

        public Create() {
        }

        public Create(Product product) {
            this.product = product;
        }

        @Override
        public Product execute(WriteContext ctx, Object root) {
            DataRoot data = (DataRoot) root;
            ctx.edit(data);                 // the id counter is about to change
            ctx.edit(data.getProducts());   // and so is the list

            long newId = data.getNextId() <= 0 ? 1 : data.getNextId();
            product.setId(newId);
            data.setNextId(newId + 1);
            data.getProducts().add(product);
            return product;
        }
    }

    /** Updates a product in place. */
    public static final class Update implements DbCommand<Product> {
        public Product product;

        public Update() {
        }

        public Update(Product product) {
            this.product = product;
        }

        @Override
        public Product execute(WriteContext ctx, Object root) {
            DataRoot data = (DataRoot) root;
            for (Product existing : data.getProducts()) {
                if (existing.getId() == product.getId()) {
                    ctx.edit(existing);
                    existing.setName(product.getName());
                    existing.setCategory(product.getCategory());
                    existing.setQuantity(product.getQuantity());
                    existing.setUnitPrice(product.getUnitPrice());
                    break;
                }
            }
            // The list itself is unchanged; only a member was edited. Enlisting the member is
            // enough, and enlisting the list as well would be harmless but misleading.
            return product;
        }
    }

    public static final class Delete implements DbCommand<Boolean> {
        public long id;

        public Delete() {
        }

        public Delete(long id) {
            this.id = id;
        }

        @Override
        public Boolean execute(WriteContext ctx, Object root) {
            DataRoot data = (DataRoot) root;
            ctx.edit(data.getProducts());
            return data.getProducts().removeIf(p -> p.getId() == id);
        }
    }

    /** Seeds the catalogue on first use — again, list and counter in one commit. */
    public static final class SeedIfEmpty implements DbCommand<Integer> {
        @Override
        public Integer execute(WriteContext ctx, Object root) {
            DataRoot data = (DataRoot) root;
            if (!data.getProducts().isEmpty()) {
                return 0;
            }
            ctx.edit(data);
            ctx.edit(data.getProducts());

            long nextId = data.getNextId() <= 0 ? 1 : data.getNextId();
            data.getProducts().add(new Product(nextId++, "Wireless Ergonomic Mouse", "Electronics", 45, 29.99));
            data.getProducts().add(new Product(nextId++, "Electric Standing Desk", "Furniture", 12, 349.50));
            data.getProducts().add(new Product(nextId++, "USB-C Multi-Port Hub", "Electronics", 80, 49.95));
            data.getProducts().add(new Product(nextId++, "Ergonomic Mesh Chair", "Furniture", 18, 199.00));
            data.setNextId(nextId);
            return 4;
        }
    }

    private ProductCommands() {
    }
}
