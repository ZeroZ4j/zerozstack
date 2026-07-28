# Zeroz4j Example: Inventory CRUD (`inventory-crud`)

A small warehouse inventory manager built with **Zeroz4j**, demonstrating an end-to-end zero-impedance Java web application with zero DTOs, zero REST endpoints, zero JSON, zero SQL, and zero JavaScript.

---

## Key Concepts Demonstrated

1. **Shared `@DataModel` POJO**
   - The same `Product` class (`@DataModel`) travels end-to-end: persisted in EclipseStore object graph, transmitted over binary WebSocket RMI, and rendered directly in the TeaVM client heap. Zero mapping code exists anywhere.

2. **Server-Side EclipseStore Persistence**
   - Implements native object-graph storage with `DataRoot` containing `List<Product>` and `nextId` counter.
   - Products and ID counter changes persist natively via `storage.store(...)`.

3. **Reactive Client UI (Signals & Computed)**
   - All state lives in `ValueSignal`s (`products`, `filter`, `selectedProductId`, form fields).
   - KPI metrics (`totalProductCount`, `totalStockItems`, `totalStockValue`) and table filtering (`filteredProducts`) derive lazily via `Computed`.
   - UI updates render exclusively through reactive `Effect`s.

4. **Annotation-Driven Model Validation**
   - Validation rules (`@NotBlank`, `@Size`, `@Min`) declared directly on `Product` fields.
   - Form inputs attach APT-generated `Product_Rules` via `withRule(...)` for real-time client feedback.
   - The server enforces the same rules automatically on every incoming RMI argument.

5. **Component Library Gaps (App-Local Components)**
   - `IntegerField` and `DoubleField` extend `AbstractField<C, T>` to provide strongly typed numeric HTML input binding and validation rule attachment for `int` and `double` model fields.

---

## Project Structure

```
inventory-crud/
├── inventory-crud-shared/      # Product @DataModel & ProductService @RmiService interface
├── inventory-crud-client/      # TeaVM Wasm UI (ExampleClientApp, MainLayout, InventoryView, IntegerField, DoubleField)
└── inventory-crud-server/      # Helidon server, ProductServiceImpl, EclipseStore DataRoot
```

---

## How to Build and Run

### 1. Build the full project from repository root

```bash
mvn clean install
```

### 2. Run the Helidon backend server

```bash
cd zerozstack-examples/inventory-crud/inventory-crud-server
java -jar target/inventory-crud-server-1.0.0-SNAPSHOT.jar
```

### 3. Open in Browser

Navigate to `http://localhost:8080` in your web browser.
