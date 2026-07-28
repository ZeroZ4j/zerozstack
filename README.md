# ZeroZ4j: The Zero-Impedance Enterprise Reference Architecture

**ZeroZ4j is a highly opinionated, pure-Java architectural proof-of-concept designed to eliminate the friction of modern web development.**

No JavaScript. No TypeScript. No JSON. No REST routes. No Object-Relational Impedance Mismatch. Just end-to-end Java, from the browser's DOM all the way down to the database.

Built by an Enterprise Architect, ZeroZ4j serves as a technical thesis demonstrating how radical stack simplification can eliminate technical debt, drastically reduce Total Cost of Ownership (TCO), and provide the unified context required for **AI-assisted coding agents** to function safely at the enterprise level.

---

## 1. The Problem: Architectural Impedance & AI Context Collapse

Modern enterprise web development is drowning in translation layers. These layers create massive friction for human developers and cause catastrophic context collapse for AI coding agents:

1. **The Database Mismatch:** Domain models in Java must be translated via SQL or JPA/Hibernate to map to relational tables, creating dual-schema maintenance.
2. **The Network Mismatch:** Java objects are serialized into text (JSON), sent over HTTP, and parsed back into JavaScript/TypeScript objects on the client.
3. **The UI Mismatch:** JavaScript/TypeScript is required to mutate a browser DOM, fracturing the codebase's language ecosystem.
4. **The AI Context Collapse:** When using AI coding agents (Copilot, Cursor, bespoke LLMs), the AI must maintain context across four different languages and paradigms (SQL, Java, JSON, TS/JS). This cognitive load causes AI hallucinations, broken contracts, and security vulnerabilities.

Every translation layer breaks static analysis, prevents fearless refactoring, and forces the enterprise to maintain three different models of the exact same data.

## 2. The Solution: A Zero-Impedance Paradigm

ZeroZ4j was engineered to prove that these translation layers are no longer strictly necessary. By unifying the stack, the architecture achieves "Zero Impedance."

- **The UI is Java:** Client-side code is written entirely in Java and compiled ahead-of-time for the browser by TeaVM. UI logic runs in the browser with no hand-written JavaScript. *(Builds currently target TeaVM's JavaScript backend, because WasmGC support in TeaVM is not yet complete enough for ZeroZ4j; WasmGC is the intended destination — see [Limitations](docs/reference/limitations.md#compilation-target).)*
- **The Network is Java:** Client and server communicate over a persistent, bidirectional WebSocket using a dense, pure binary RPC protocol. A Java interface called on the client executes seamlessly on the Jakarta EE/CDI backend.
- **The Database is Java:** Using EclipseStore, the server persists the JVM object graph directly to disk. No SQL. No JPA. The database *is* the memory heap.

Because the stack is unified, AI coding agents can generate end-to-end features with near-perfect accuracy, and human developers can refactor from the database to the button-click with a single IDE command.

---

## 3. Core Architectural Pillars

ZeroZ4j relies on a carefully curated, highly concurrent architecture to achieve this vision.

### A. Native Object Persistence (Killing the ORM)
ZeroZ4j bypasses the Object-Relational Mismatch entirely. By utilizing **EclipseStore** via our pluggable `zerozstack-store-eclipsestore` module, objects are stored natively as a graph. There are no translation layers, no `UPDATE` statements, and no N+1 query problems. Multi-tenancy is handled seamlessly out-of-the-box. The in-memory graph is explicitly saved by the developer, while realtime UI updates can be implicitly managed via `@LiveSync`.

### B. Binary RPC & Project Loom (Virtual Threads)
The framework discards REST and JSON. The client (Wasm) and server communicate via a custom binary protocol over persistent WebSockets. 
To handle thousands of persistent connections without thread exhaustion, the backend leverages **Project Loom Virtual Threads**. Incoming WebSocket binary frames are immediately handed off to a Virtual Thread, ensuring the application server's I/O threads never block during complex processing.

### C. AOT Compilation & Client-Side State
ZeroZ4j relies on Ahead-of-Time (AOT) compilation via **TeaVM** to guarantee performance. Annotation processors generate binary serializers and RMI stubs at compile-time, avoiding slow runtime reflection in the browser. 
Unlike traditional Java web frameworks (like Vaadin), **ZeroZ4j maintains zero server-side DOM state**. UI components (styled with utility-first DaisyUI/Tailwind CSS) are instantiated, configured, and bound to listeners entirely in the client-side Wasm heap, utilizing cooperative coroutines for non-blocking I/O.

---

## 4. Framework Modules

ZeroZ4j is fully modular, allowing developers to pick exactly what they need:

*   **`zerozstack-shared-api`**: Annotations (`@DataModel`, `@RmiService`) and common interfaces.
*   **`zerozstack-apt`**: The compile-time annotation processor for generating model serializers and RMI stubs.
*   **`zerozstack-client`**: The TeaVM bridging logic for the browser (WebSocket client, coroutines).
*   **`zerozstack-ui-components`**: A Vaadin-inspired, DOM-less Java UI component library built on Tailwind/DaisyUI.
*   **`zerozstack-server-core`**: The agnostic CDI engine, RMI dispatcher, and `LiveSync` logic.
*   **`zerozstack-server-helidon`**: The Helidon-specific HTTP and WebSocket bindings.
*   **`zerozstack-store-eclipsestore`**: The native object-graph persistence adapter.
*   **`zerozstack-archetype`**: A Maven Archetype to instantly scaffold a new multi-module project.

---

## 5. Using ZeroZ Stack via Maven

Available from Maven Central. Depend on the modules you need; the BOM keeps versions aligned:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>com.zeroz4j</groupId>
            <artifactId>zerozstack-bom</artifactId>
            <version>0.4.0</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <dependency>
        <groupId>com.zeroz4j</groupId>
        <artifactId>zerozstack-server-core</artifactId>
    </dependency>
    <dependency>
        <groupId>com.zeroz4j</groupId>
        <artifactId>zerozstack-store-eclipsestore</artifactId>
    </dependency>
</dependencies>
```

Or start from the archetype, which generates the three-module shape for you:

```bash
mvn archetype:generate   -DarchetypeGroupId=com.zeroz4j   -DarchetypeArtifactId=zerozstack-archetype   -DarchetypeVersion=0.4.0
```

Publication to Maven Central is planned; see
[ZeroZ DB's RELEASING.md](https://github.com/ZeroZ4j/zerozdb/blob/main/RELEASING.md) for the
process the family uses.

## 6. Developer Resources

This repository contains the core framework and reference implementations.

**Start here:**

* **[Quickstart](docs/start/quickstart.md)** — build the framework and run a working example in about five minutes. Every command verified.
* **[Choosing how state moves](docs/decide/index.md)** — ZeroZ4j gives you five ways to propagate state (local signals, RMI, server events, shared signals, LiveSync). Picking the wrong one is the most common source of trouble in ZeroZ4j applications; this is the decision procedure.
* **[Troubleshooting](docs/guides/troubleshooting.md)** — symptom-first, and specifically covering the cases where nothing happens and there is no exception to search for.
* **[Limitations](docs/reference/limitations.md)** — every known gap in 0.4.0, stated plainly.
* **[Changelog](CHANGELOG.md)** — what changed and what breaks. Read the Breaking section before upgrading; 0.4.0 renames an artifact and changes several silent behaviours into thrown exceptions.
* **[Glossary](docs/reference/glossary.md)** — event, signal, push, sync and mutation are not interchangeable terms here.

**Deeper:**

* **[10 Core Concepts](docs/CONCEPTS.md)** - A quick guide to the essential concepts you need to know when building a Zeroz4j application.
* **[Developer Setup & Getting Started Guide](docs/GETTING_STARTED.md)** - Learn how to scaffold a new project using the `zerozstack-archetype`.
* **[Code Walkthrough: End-to-End Java](docs/CODE_WALKTHROUGH.md)** - Examples of Models, RMI Interfaces, and Wasm UI binding.
* **[Detailed Protocol Specification](docs/PROTOCOL.md)** - Deep dive into the binary WebSocket frame structure and architecture.
* **[Server Events: Typed Push Topics](docs/SERVER_EVENTS.md)** - Broadcasting typed, fire-and-forget events from the server to connected clients.
* **[Signals: Client-Side Reactive State](docs/SIGNALS.md)** - Reactive UI state with `ValueSignal`, `Computed`, and `Effect`.
* **[Validation: Annotate Once, Enforce Everywhere](docs/VALIDATION.md)** - Model annotations enforced by the client binder and automatically by the server.
* **[LiveSync: Two-Way Object Synchronization](docs/LIVESYNC.md)** - In-place state sync down, and `@ClientWritable` automatic mutation propagation up.
* **[Agent Prompts: Build a Zeroz4j App](docs/AGENT_PROMPTS.md)** - Ready-to-paste prompts for building Zeroz4j apps with an AI coding agent.

---

## About the Author & Enterprise Architecture Strategy

ZeroZ4j is an open-source reference architecture created by **Franz Schöning**, a Principal Enterprise Architect.

**Why did an Enterprise Architect build a software framework?**
In my consulting practice, I audit IT landscapes and rationalize technology portfolios for large organizations. The greatest threat to enterprise agility today is **architectural gridlock**—application and data landscapes that are too fragmented to be maintained, too complex to be modernized, and too disjointed to be quickly adapted to fast changing business environments.

I built ZeroZ4j as an architectural thesis to prove a strategic analogy between software and enterprise architecture: **Radical simplification is possible.** It serves as a tangible demonstration of how rethinking foundational assumptions yields massive efficiency gains. Whether at the software architecture level or at the enterprise application level, the path forward requires unifying and simplifying your architecture. 

ZeroZ4j is an experimental proof-of-concept and not intended as a drop-in replacement for industrialized production systems. However, it is a working demonstration of how to eliminate impedance mismatch, reduce TCO, and enable AI-assisted development in a safe and controlled manner at the software architecture level.

**Enterprise Architecture Consulting**  
Are you struggling with complex IT portfolios, legacy modernization, or the need to safely integrate AI into your enterprise development lifecycle? I help organizations untangle architectural gridlock and chart a pragmatic, high-ROI path forward.

🔗 **Let's talk about your architecture:** [www.franzschoning.com](https://www.franzschoning.com)

## License

This project is open-source under the [Apache 2.0 License](LICENSE). Anyone is welcome to fork it, adapt it, and build upon it. See the [NOTICE.md](NOTICE.md) file for attribution details.
