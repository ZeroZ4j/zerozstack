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
package com.zeroz4j.example.scopedsignals.api;

import com.zeroz4j.api.DataModel;

import java.util.ArrayList;
import java.util.List;

/**
 * What one browser has put in its basket.
 *
 * <p>Treated as immutable: a scoped signal deduplicates on {@code equals}, so setting a mutated
 * instance back would be swallowed as "no change" and nothing would propagate.</p>
 */
@DataModel
public class Basket {

    private List<String> items = new ArrayList<>();

    public Basket() {}

    public Basket(List<String> items) {
        this.items = new ArrayList<>(items);
    }

    /** @return an empty basket, the value a browser holds before it adds anything */
    public static Basket empty() {
        return new Basket(new ArrayList<>());
    }

    /** @return a new basket with one more item; the original is untouched */
    public Basket plus(String item) {
        List<String> next = new ArrayList<>(items);
        next.add(item);
        return new Basket(next);
    }

    public List<String> getItems() {
        return items;
    }

    public void setItems(List<String> items) {
        this.items = items;
    }

    public int size() {
        return items == null ? 0 : items.size();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Basket)) {
            return false;
        }
        Basket that = (Basket) other;
        return items == null ? that.items == null : items.equals(that.items);
    }

    @Override
    public int hashCode() {
        return items == null ? 0 : items.hashCode();
    }
}
