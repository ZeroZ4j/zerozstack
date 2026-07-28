# Code Walkthrough: End-to-End Java

> ⚠️ **Partly outdated.** Steps 1–3 reflect the current API. **Step 4 (the client bootstrap) does
> not** — it shows a manual `BinaryPackableRegistrar.registerAll()` / `WasmRmiClientChannel` /
> `WasmRmiClient.initialize` path that is obsolete. The current bootstrap is a single call,
> `Zeroz4jClient.connect(webSocketUrl, onReady)`, and registrars are discovered automatically through
> `META-INF/services`. See [Quickstart](start/quickstart.md).

Zero impedance means your Java objects flow from the database to the browser without translation layers.

### Step 1: Declare the Domain Model
Annotate your models with `@DataModel` — one annotation, nothing else. Compile-time serializers will be generated to write/read fields without reflection:

```java
import com.zeroz4j.api.DataModel;

@DataModel
public class UserInfo {
    private String name;
    private int score;

    public UserInfo() {} // Required
    
    // Getters and setters...
}
```

### Step 2: Declare the Remote Service Interface
Annotate your contract interfaces with `@RmiService`:

```java
import com.zeroz4j.api.RmiService;

@RmiService
public interface UserService {
    UserInfo getUserInfo(String username);
    void updateScore(String username, int newScore);
}
```

### Step 3: Server Persistence with EclipseStore
Because there is zero impedance, you don't need JPA or SQL to save your data. By simply depending on the `zerozstack-store-eclipsestore` module, EclipseStore is automatically wired up and ready to persist your object graph exactly as it is in memory.

If you want your entities to automatically synchronize memory state between the frontend and backend without a manual push, you can add `@LiveSync` to your models. However, database persistence is always strictly explicit to keep you in control.

```java
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import jakarta.inject.Inject;

@ApplicationScoped
public class UserServiceImpl implements UserService {
    
    @Inject
    private EmbeddedStorageManager storage; 

    private DataRoot getRoot() {
        return (DataRoot) storage.root();
    }

    @Override
    public UserInfo getUserInfo(String username) {
        // Querying is just Java stream operations over the stored object graph
        return getRoot().getUsers().stream()
                .filter(u -> u.getName().equals(username))
                .findFirst()
                .orElse(null);
    }

    @Override
    public void updateScore(String username, int newScore) {
        UserInfo user = getRoot().getUsers().stream()
                .filter(u -> u.getName().equals(username))
                .findFirst().orElseThrow();
                
        user.setScore(newScore);
        
        // Persist the delta directly! No UPDATE statements.
        // Saves are explicitly controlled by the developer.
        storage.store(user); 
    }
}
```

### Step 4: Implement the Client-Side Application (TeaVM/Wasm)
Initialize the WebSocket connection and call the generated RMI stub. The method execution will suspend processing cooperatively until response packet returns, keeping the UI fully responsive:

```java
import com.zeroz4j.client.WasmRmiClient;
import com.zeroz4j.client.WasmRmiClientChannel;
import com.zeroz4j.generated.BinaryPackableRegistrar;

public class ClientApplication {
    public static void main(String[] args) {
        // 1. Register generated serializers
        BinaryPackableRegistrar.registerAll();

        // 2. Open WebSocket Channel
        WasmRmiClientChannel channel = new WasmRmiClientChannel("ws://localhost:8080/wasm-rmi", () -> {
            System.out.println("Connection opened!");
        });
        WasmRmiClient.initialize(channel);

        // 3. Obtain Service Stub
        UserService service = new UserService_Stub(); // Generated implementation

        // 4. Invoke RMI calls seamlessly
        UserInfo profile = service.getUserInfo("franz");
        System.out.println("User Profile: " + profile);
    }
}
```
