# Developer Setup & Getting Started Guide

> ⚠️ **Superseded.** Use **[Quickstart](start/quickstart.md)** instead, where every command has been
> executed against the current `main`. This page is kept only because it is linked from elsewhere.
>
> The UI package names and the `Button` API on this page were wrong until 0.4.1 and are now
> corrected. One known error remains: `java -jar …-server.jar` cannot work, because no shade or
> assembly plugin is configured — launch with `java -cp "target/classes:target/libs/*"` instead.

The easiest way to scaffold a new `zeroz4j` project is to use the provided Maven Archetype. This will automatically generate a complete, multi-module project (client, shared, server) with all TeaVM, annotation processors, and Helidon dependencies correctly configured.

## 1. Generate the Project

Run the following Maven command to scaffold your project:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.zeroz4j \
  -DarchetypeArtifactId=zerozstack-archetype \
  -DarchetypeVersion=0.6.1 \
  -DgroupId=com.mycompany \
  -DartifactId=myapp \
  -Dversion=1.0.0-SNAPSHOT
```

## 2. Project Structure

The generated project will contain:
*   `myapp-shared`: Contains your API interfaces and Domain Models.
*   `myapp-client`: Contains the Java UI code that compiles to WebAssembly.
*   `myapp-server`: Contains the Helidon-based backend that persists your data.

## 3. Build and Run

To build the entire project (including compiling the WASM client):

```bash
cd myapp
mvn clean install
```

To run the server:

```bash
java -jar myapp-server/target/myapp-server-1.0.0-SNAPSHOT.jar
```

Navigate to `http://localhost:8080` to see your running ZeroZ Stack application!

## 4. Code Example

With ZeroZ Stack, you avoid boilerplate HTTP mapping, JSON translation, and ORM schemas. Here is how simple it is to build a full-stack feature.

### 1. The Domain Model (Shared)
Define your data structure. It automatically becomes serializable and persistable.

```java
import com.zeroz4j.api.DataModel;

@DataModel
public class ChatMessage {
    private String author;
    private String text;

    public ChatMessage() {}

    public ChatMessage(String author, String text) {
        this.author = author;
        this.text = text;
    }
    // getters and setters...
}
```

### 2. The API (Shared)
Define the RPC interface.

```java
import com.zeroz4j.api.RmiService;

@RmiService
public interface ChatService {
    void sendMessage(ChatMessage msg);
}
```

### 3. The Server Backend
Implement the service. The method receives the exact object sent from the client.

```java
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import com.zeroz4j.db.net.ZeroZDbNode;

@ApplicationScoped
public class ChatServiceImpl implements ChatService {
    @Inject
    private ZeroZDbNode db;

    @Override
    public void sendMessage(ChatMessage msg) {
        // Automatically persist or broadcast!
        System.out.println("Received: " + msg.getText());
        
        // Example: save to your data root graph, atomically
        // db.localDb().write(ctx -> { ctx.edit(root.getMessages()); root.getMessages().add(msg); });
        // root.getMessages().add(msg);
        // storage.store(root.getMessages());
    }
}
```

### 4. The Client UI (Wasm)
Invoke the backend directly from a UI button click event. The UI never touches JSON or REST.

```java
import com.zeroz4j.ui.component.Button;   // component, singular
import com.zeroz4j.ui.layout.Div;         // layouts live in ui.layout

public class ChatView extends Div {
    public ChatView(ChatService chatService) {
        Button sendBtn = new Button("Send Hello");
        sendBtn.addClickListener(event -> {
            // Suspends cooperatively, calls backend over binary WebSocket
            chatService.sendMessage(new ChatMessage("Alice", "Hello World!"));
        });
        
        add(sendBtn);
    }
}
```
