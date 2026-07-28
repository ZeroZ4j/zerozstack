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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.zeroz4j.db.net.ZeroZDbNode;

import java.util.List;

@ApplicationScoped
public class ProductServiceImpl implements ProductService {

    /**
     * The database node, not a raw storage manager.
     *
     * <p>Writes go through commands so they are atomic, and so the same code runs whether this
     * process owns the data ({@code zeroz4j.store.mode=EMBEDDED}) or talks to a database server
     * ({@code CLIENT}). Injecting {@code EmbeddedStorageManager} still works, but only where the
     * data is local, and it offers no transaction.</p>
     */
    @Inject
    private ZeroZDbNode db;

    @Override
    public List<Product> list() {
        db.execute(new ProductCommands.SeedIfEmpty());
        return db.query(new ProductQueries.ListAll());
    }

    @Override
    public Product save(Product p) {
        return p.getId() == 0
                ? db.execute(new ProductCommands.Create(p))
                : db.execute(new ProductCommands.Update(p));
    }

    @Override
    public void delete(long id) {
        db.execute(new ProductCommands.Delete(id));
    }
}
