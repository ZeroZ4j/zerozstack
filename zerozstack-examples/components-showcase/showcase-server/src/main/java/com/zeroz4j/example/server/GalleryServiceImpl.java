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

import com.zeroz4j.example.api.GalleryService;
import com.zeroz4j.example.model.GalleryItem;
import com.zeroz4j.server.WasmRmiServerEngine;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import com.zeroz4j.example.model.GalleryStateUpdate;
import com.zeroz4j.example.model.VisualResult;
import java.util.Arrays;

@ApplicationScoped
public class GalleryServiceImpl implements GalleryService {
    
    @Inject private WasmRmiServerEngine rmiEngine;
    private double currentSliderValue = 50.0;
    private final Map<String, String> state = new ConcurrentHashMap<>();
    
    public GalleryServiceImpl() {
        // Initial state
        state.put("textField", "Initial Text");
        state.put("passwordField", "secret123");
        state.put("textArea", "This is a bound text area.");
        state.put("checkBox", "false");
        state.put("radioGroup", "Option 1");
        state.put("toggleButton", "false");
        state.put("comboBox", "Item B");
        state.put("choiceBox", "Choice 2");
        state.put("colorPicker", "0xff0000ff"); // Red
        state.put("slider", "50.0");
        state.put("datePicker", "");
    }

    @Override
    public List<GalleryItem> getItems() {
        List<GalleryItem> items = new ArrayList<>();
        items.add(new GalleryItem(1, "Laptop", "Electronics", 999.99));
        items.add(new GalleryItem(2, "Coffee Mug", "Home", 12.50));
        items.add(new GalleryItem(3, "Desk Chair", "Furniture", 150.00));
        items.add(new GalleryItem(4, "Headphones", "Electronics", 199.99));
        return items;
    }

    @Override
    public void updateSliderValue(double value) {
        this.currentSliderValue = value;
        // Push the new value to all connected clients
        rmiEngine.broadcastPush("gallery.slider_updated", value);
    }

    @Override
    public double getInitialSliderValue() {
        return currentSliderValue;
    }

    @Override
    public Map<String, String> getInitialState() {
        return state;
    }

    @Override
    public void updateState(String componentId, String value) {
        state.put(componentId, value);
        // Broadcast change using DTO
        rmiEngine.broadcastPush("gallery.state_updated", new GalleryStateUpdate(componentId, value));
    }

    @Override
    public VisualResult getGridData() {
        return new VisualResult(
            4, 4,
            Arrays.asList("ID", "Product Name", "Category", "Price"),
            Arrays.asList("Integer", "String", "String", "Double"),
            Arrays.asList(
                Arrays.asList(1, "Laptop Pro", "Electronics", 1299.99),
                Arrays.asList(2, "Coffee Mug", "Home", 12.50),
                Arrays.asList(3, "Desk Chair", "Furniture", 150.00),
                Arrays.asList(4, "Headphones", "Electronics", 199.99)
            )
        );
    }

    @Override
    public VisualResult getChartData() {
        return new VisualResult(
            4, 2,
            Arrays.asList("Category", "Sales"),
            Arrays.asList("String", "Double"),
            Arrays.asList(
                Arrays.asList("Electronics", 145000.0),
                Arrays.asList("Software", 85000.0),
                Arrays.asList("Furniture", 150.00),
                Arrays.asList("Office", 75.00)
            )
        );
    }

    @Override
    public VisualResult getGanttData() {
        return new VisualResult(
            3, 4,
            Arrays.asList("Task Name", "Resource", "Start", "End"),
            Arrays.asList("String", "String", "String", "String"),
            Arrays.asList(
                Arrays.asList("Requirements", "Alice", "2023-01-01", "2023-01-15"),
                Arrays.asList("Development", "Bob", "2023-01-16", "2023-03-01"),
                Arrays.asList("Testing", "Charlie", "2026-08-02", "2026-08-15")
            )
        );
    }
}

