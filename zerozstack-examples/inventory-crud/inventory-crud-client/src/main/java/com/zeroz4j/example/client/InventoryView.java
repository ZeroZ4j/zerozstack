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
package com.zeroz4j.example.client;

import com.zeroz4j.api.Disposable;
import com.zeroz4j.example.api.ProductService;
import com.zeroz4j.example.api.ProductService_Stub;
import com.zeroz4j.example.model.Product;
import com.zeroz4j.example.model.Product_Rules;
import com.zeroz4j.signals.Computed;
import com.zeroz4j.signals.Effect;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;

import java.util.ArrayList;
import java.util.List;

public class InventoryView extends Card {

    private final ProductService productService = new ProductService_Stub();

    // Source signals
    private final ValueSignal<List<Product>> products = new ValueSignal<>(new ArrayList<>());
    private final ValueSignal<String> filter = new ValueSignal<>("");
    private final ValueSignal<Long> selectedProductId = new ValueSignal<>(0L);

    // Form field signals
    private final ValueSignal<String> formName = new ValueSignal<>("");
    private final ValueSignal<String> formCategory = new ValueSignal<>("Electronics");
    private final ValueSignal<Integer> formQuantity = new ValueSignal<>(0);
    private final ValueSignal<Double> formUnitPrice = new ValueSignal<>(0.0);

    // Status message signals
    private final ValueSignal<String> statusMessage = new ValueSignal<>("");
    private final ValueSignal<Boolean> statusSuccess = new ValueSignal<>(true);

    // Derived signals
    private final Computed<List<Product>> filteredProducts = new Computed<>(() -> {
        List<Product> list = products.get();
        String query = filter.get();
        if (query == null || query.trim().isEmpty()) {
            return new ArrayList<>(list);
        }
        String lowerQuery = query.trim().toLowerCase();
        List<Product> result = new ArrayList<>();
        for (Product p : list) {
            boolean matchesName = p.getName() != null && p.getName().toLowerCase().contains(lowerQuery);
            boolean matchesCat = p.getCategory() != null && p.getCategory().toLowerCase().contains(lowerQuery);
            if (matchesName || matchesCat) {
                result.add(p);
            }
        }
        return result;
    });

    private final Computed<Integer> totalProductCount = new Computed<>(() -> products.get().size());

    private final Computed<Integer> totalStockItems = new Computed<>(() -> {
        int total = 0;
        for (Product p : products.get()) {
            total += p.getQuantity();
        }
        return total;
    });

    private final Computed<Double> totalStockValue = new Computed<>(() -> {
        double total = 0.0;
        for (Product p : products.get()) {
            total += p.getQuantity() * p.getUnitPrice();
        }
        return total;
    });

    private final List<Disposable> disposables = new ArrayList<>();

    public InventoryView() {
        super();
        addClassName("w-full");
        addClassName("max-w-7xl");
        addClassName("mx-auto");
        addClassName("flex");
        addClassName("flex-col");
        addClassName("gap-4");

        add(new CardTitle("Warehouse Inventory Management"));

        // Status alert container
        Div statusDiv = new Div();
        add(statusDiv);
        disposables.add(Effect.create(() -> {
            String msg = statusMessage.get();
            statusDiv.getElement().setInnerHTML("");
            if (msg != null && !msg.trim().isEmpty()) {
                Alert alert = new Alert(msg);
                if (Boolean.TRUE.equals(statusSuccess.get())) {
                    alert.addClassName("alert-success");
                } else {
                    alert.addClassName("alert-error");
                }
                statusDiv.add(alert);
            }
        }));

        // Header KPI summary cards
        HorizontalLayout statsLayout = new HorizontalLayout();
        statsLayout.addClassName("gap-4");
        statsLayout.addClassName("w-full");
        statsLayout.addClassName("mb-2");

        KpiTile totalProductsTile = new KpiTile("Total Products");
        KpiTile totalItemsTile = new KpiTile("Total In-Stock Items");
        KpiTile totalValueTile = new KpiTile("Total Inventory Value");

        statsLayout.add(totalProductsTile, totalItemsTile, totalValueTile);
        add(statsLayout);

        disposables.add(Effect.create(() -> {
            totalProductsTile.value(String.valueOf(totalProductCount.get()));
            totalItemsTile.value(String.valueOf(totalStockItems.get()));
            totalValueTile.value("$" + String.format("%.2f", totalStockValue.get()));
        }));

        // Main Split Content: Master (Left) and Detail (Right)
        HorizontalLayout mainSplit = new HorizontalLayout();
        mainSplit.addClassName("gap-6");
        mainSplit.addClassName("w-full");
        mainSplit.addClassName("items-start");

        // --- MASTER PANEL (Left) ---
        VerticalLayout masterPanel = new VerticalLayout();
        masterPanel.addClassName("w-7/12");
        masterPanel.addClassName("gap-3");

        // Filter / Search Input
        TextField filterField = new TextField("Filter products by name or category...");
        filterField.addClassName("w-full");
        filterField.bindValue(filter);
        masterPanel.add(filterField);

        // Products List / Table Container
        Div tableContainer = new Div();
        tableContainer.addClassName("w-full");
        tableContainer.addClassName("overflow-x-auto");
        tableContainer.addClassName("bg-base-200");
        tableContainer.addClassName("rounded-box");
        tableContainer.addClassName("p-2");
        masterPanel.add(tableContainer);

        disposables.add(Effect.create(() -> renderProductTable(tableContainer)));

        mainSplit.add(masterPanel);

        // --- DETAIL PANEL (Right) ---
        Card detailCard = new Card();
        detailCard.addClassName("w-5/12");
        detailCard.addClassName("bg-base-200");

        CardTitle detailTitle = new CardTitle("Product Details");
        detailCard.add(detailTitle);

        FormLayout formLayout = new FormLayout();
        formLayout.addClassName("gap-3");

        // 1. Name field
        VerticalLayout nameGroup = new VerticalLayout();
        nameGroup.addClassName("gap-1");
        Span nameLabel = new Span("Product Name *");
        nameLabel.addClassName("font-semibold");
        nameLabel.addClassName("text-sm");
        TextField nameField = new TextField("Enter product name");
        nameField.bindValue(formName);
        nameField.withRule(Product_Rules.name());
        Div nameError = new Div();
        nameError.addClassName("text-error");
        nameError.addClassName("text-xs");
        nameGroup.add(nameLabel, nameField, nameError);
        disposables.add(Effect.create(() -> {
            formName.get();
            List<String> violations = nameField.getViolations();
            nameError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.add(nameGroup);

        // 2. Category field
        VerticalLayout catGroup = new VerticalLayout();
        catGroup.addClassName("gap-1");
        Span catLabel = new Span("Category *");
        catLabel.addClassName("font-semibold");
        catLabel.addClassName("text-sm");
        Select categorySelect = new Select();
        categorySelect.setItems(List.of("Electronics", "Furniture", "Supplies", "Clothing", "Food", "Other"));
        categorySelect.bindValue(formCategory);
        categorySelect.withRule(Product_Rules.category());
        Div catError = new Div();
        catError.addClassName("text-error");
        catError.addClassName("text-xs");
        catGroup.add(catLabel, categorySelect, catError);
        disposables.add(Effect.create(() -> {
            formCategory.get();
            List<String> violations = categorySelect.getViolations();
            catError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.add(catGroup);

        // 3. Quantity field
        VerticalLayout qtyGroup = new VerticalLayout();
        qtyGroup.addClassName("gap-1");
        Span qtyLabel = new Span("Quantity in Stock *");
        qtyLabel.addClassName("font-semibold");
        qtyLabel.addClassName("text-sm");
        IntegerField quantityField = new IntegerField("0");
        quantityField.bindValue(formQuantity);
        quantityField.withRule(Product_Rules.quantity());
        Div qtyError = new Div();
        qtyError.addClassName("text-error");
        qtyError.addClassName("text-xs");
        qtyGroup.add(qtyLabel, quantityField, qtyError);
        disposables.add(Effect.create(() -> {
            formQuantity.get();
            List<String> violations = quantityField.getViolations();
            qtyError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.add(qtyGroup);

        // 4. Unit Price field
        VerticalLayout priceGroup = new VerticalLayout();
        priceGroup.addClassName("gap-1");
        Span priceLabel = new Span("Unit Price ($) *");
        priceLabel.addClassName("font-semibold");
        priceLabel.addClassName("text-sm");
        DoubleField priceField = new DoubleField("0.00");
        priceField.bindValue(formUnitPrice);
        priceField.withRule(Product_Rules.unitPrice());
        Div priceError = new Div();
        priceError.addClassName("text-error");
        priceError.addClassName("text-xs");
        priceGroup.add(priceLabel, priceField, priceError);
        disposables.add(Effect.create(() -> {
            formUnitPrice.get();
            List<String> violations = priceField.getViolations();
            priceError.setText(violations.isEmpty() ? "" : violations.get(0));
        }));
        formLayout.add(priceGroup);

        detailCard.add(formLayout);

        // Form validity computed
        Computed<Boolean> formValid = new Computed<>(() -> {
            formName.get();
            formCategory.get();
            formQuantity.get();
            formUnitPrice.get();

            return nameField.isValid()
                    && categorySelect.isValid()
                    && quantityField.isValid()
                    && priceField.isValid();
        });

        // Detail Action Buttons
        HorizontalLayout buttonRow = new HorizontalLayout();
        buttonRow.addClassName("mt-4");
        buttonRow.addClassName("gap-2");

        Button newButton = new Button("New");
        newButton.addClassName("btn-secondary");
        newButton.addClassName("btn-sm");
        newButton.addClickListener(e -> resetForm());

        Button saveButton = new Button("Save");
        saveButton.addClassName("btn-primary");
        saveButton.addClassName("btn-sm");

        Button deleteButton = new Button("Delete");
        deleteButton.addClassName("btn-error");
        deleteButton.addClassName("btn-sm");

        buttonRow.add(newButton, saveButton, deleteButton);
        detailCard.add(buttonRow);

        // Reactively enable/disable buttons based on selection and form validity
        disposables.add(Effect.create(() -> {
            boolean valid = Boolean.TRUE.equals(formValid.get());
            saveButton.setEnabled(valid);
        }));

        disposables.add(Effect.create(() -> {
            long selId = selectedProductId.get();
            deleteButton.setEnabled(selId > 0);
            if (selId == 0) {
                detailTitle.setText("New Product");
            } else {
                detailTitle.setText("Edit Product #" + selId);
            }
        }));

        // Save Action
        saveButton.addClickListener(e -> {
            if (!Boolean.TRUE.equals(formValid.get())) {
                return;
            }
            long id = selectedProductId.get();
            Product p = new Product(
                    id,
                    formName.get() != null ? formName.get().trim() : "",
                    formCategory.get() != null ? formCategory.get().trim() : "Electronics",
                    formQuantity.get() != null ? formQuantity.get() : 0,
                    formUnitPrice.get() != null ? formUnitPrice.get() : 0.0
            );

            try {
                Product saved = productService.save(p);
                statusMessage.set("Product saved successfully: " + saved.getName() + " (ID #" + saved.getId() + ")");
                statusSuccess.set(true);

                // Update client products signal immutably with the saved product
                products.update(current -> {
                    List<Product> next = new ArrayList<>();
                    boolean found = false;
                    for (Product item : current) {
                        if (item.getId() == saved.getId()) {
                            next.add(saved);
                            found = true;
                        } else {
                            next.add(item);
                        }
                    }
                    if (!found) {
                        next.add(saved);
                    }
                    return next;
                });
                selectedProductId.set(saved.getId());
            } catch (Exception ex) {
                statusMessage.set("Failed to save product: " + ex.getMessage());
                statusSuccess.set(false);
            }
        });

        // Delete Action
        deleteButton.addClickListener(e -> {
            long id = selectedProductId.get();
            if (id <= 0) {
                return;
            }
            try {
                productService.delete(id);
                statusMessage.set("Product #" + id + " deleted.");
                statusSuccess.set(true);

                products.update(current -> {
                    List<Product> next = new ArrayList<>();
                    for (Product item : current) {
                        if (item.getId() != id) {
                            next.add(item);
                        }
                    }
                    return next;
                });
                resetForm();
            } catch (Exception ex) {
                statusMessage.set("Failed to delete product: " + ex.getMessage());
                statusSuccess.set(false);
            }
        });

        mainSplit.add(detailCard);
        add(mainSplit);

        // Fetch initial list
        loadProducts();
    }

    private void renderProductTable(Div container) {
        container.getElement().setInnerHTML("");
        List<Product> list = filteredProducts.get();

        if (list == null || list.isEmpty()) {
            EmptyState emptyState = new EmptyState(
                    "inbox",
                    "No Products Found",
                    "No matching inventory products exist. Add a new product or clear the search filter."
            );
            container.add(emptyState);
            return;
        }

        Table table = new Table();
        table.addClassName("table");
        table.addClassName("table-zebra");
        table.addClassName("w-full");

        Component thead = new Component("thead") {};
        Component headerRow = new Component("tr") {};

        for (String col : new String[]{"ID", "Name", "Category", "Quantity", "Price", "Action"}) {
            Component th = new Component("th") {};
            th.getElement().setTextContent(col);
            headerRow.getElement().appendChild(th.getElement());
        }
        thead.getElement().appendChild(headerRow.getElement());
        table.getElement().appendChild(thead.getElement());

        Component tbody = new Component("tbody") {};
        long currentSelected = selectedProductId.get();

        for (Product p : list) {
            Component tr = new Component("tr") {};
            String cssClass = "cursor-pointer" + (p.getId() == currentSelected ? " bg-primary/20" : "");
            tr.getElement().setAttribute("class", cssClass);
            tr.addDomEventListener("click", evt -> selectProduct(p));

            addTd(tr, "#" + p.getId());
            addTd(tr, p.getName());
            addTd(tr, p.getCategory());
            addTd(tr, String.valueOf(p.getQuantity()));
            addTd(tr, "$" + String.format("%.2f", p.getUnitPrice()));

            Component tdAction = new Component("td") {};
            Button selectBtn = new Button("Edit");
            selectBtn.addClassName("btn-xs");
            selectBtn.addClassName("btn-outline");
            selectBtn.addClickListener(e -> selectProduct(p));
            tdAction.getElement().appendChild(selectBtn.getElement());
            tr.getElement().appendChild(tdAction.getElement());

            tbody.getElement().appendChild(tr.getElement());
        }
        table.getElement().appendChild(tbody.getElement());
        container.add(table);
    }

    private void addTd(Component tr, String text) {
        Component td = new Component("td") {};
        td.getElement().setTextContent(text != null ? text : "");
        tr.getElement().appendChild(td.getElement());
    }

    private void selectProduct(Product p) {
        selectedProductId.set(p.getId());
        formName.set(p.getName());
        formCategory.set(p.getCategory() != null ? p.getCategory() : "Electronics");
        formQuantity.set(p.getQuantity());
        formUnitPrice.set(p.getUnitPrice());
    }

    private void resetForm() {
        selectedProductId.set(0L);
        formName.set("");
        formCategory.set("Electronics");
        formQuantity.set(0);
        formUnitPrice.set(0.0);
    }

    private void loadProducts() {
        try {
            List<Product> list = productService.list();
            products.set(new ArrayList<>(list));
            statusMessage.set("");
        } catch (Exception ex) {
            statusMessage.set("Failed to load inventory products: " + ex.getMessage());
            statusSuccess.set(false);
        }
    }

    public void dispose() {
        for (Disposable disposable : disposables) {
            disposable.dispose();
        }
        disposables.clear();
        filteredProducts.dispose();
        totalProductCount.dispose();
        totalStockItems.dispose();
        totalStockValue.dispose();
    }
}
