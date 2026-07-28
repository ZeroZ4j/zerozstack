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
package com.zeroz4j.example.model;

import com.zeroz4j.api.DataModel;
import com.zeroz4j.api.validation.Min;
import com.zeroz4j.api.validation.NotBlank;
import com.zeroz4j.api.validation.Size;

import java.util.Objects;

@DataModel
public class Product {

    private long id;

    @NotBlank
    @Size(min = 1, max = 100)
    private String name;

    @NotBlank
    private String category;

    @Min(0)
    private int quantity;

    @Min(0)
    private double unitPrice;

    public Product() {
    }

    public Product(long id, String name, String category, int quantity, double unitPrice) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.quantity = quantity;
        this.unitPrice = unitPrice;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getUnitPrice() {
        return unitPrice;
    }

    public void setUnitPrice(double unitPrice) {
        this.unitPrice = unitPrice;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Product)) {
            return false;
        }
        Product other = (Product) o;
        return id == other.id
                && quantity == other.quantity
                && Double.compare(other.unitPrice, unitPrice) == 0
                && Objects.equals(name, other.name)
                && Objects.equals(category, other.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, category, quantity, unitPrice);
    }
}
