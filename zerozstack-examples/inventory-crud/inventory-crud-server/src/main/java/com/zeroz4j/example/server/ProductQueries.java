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
import com.zeroz4j.db.net.DbQuery;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads expressed as queries, so they run wherever the data lives.
 *
 * <p>A query returns a value, never a live graph node: whatever it returns is serialized back to
 * the caller, so returning a deeply-connected entity would ship its reachable graph. Copying into
 * a new list is deliberate.</p>
 */
public final class ProductQueries {

    public static final class ListAll implements DbQuery<List<Product>> {
        @Override
        public List<Product> execute(Object root) {
            return new ArrayList<>(((DataRoot) root).getProducts());
        }
    }

    private ProductQueries() {
    }
}
