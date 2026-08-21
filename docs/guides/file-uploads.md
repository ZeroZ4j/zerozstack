# Accepting file uploads

For an application that needs a file from the person using it — a photo, a spreadsheet, a scanned
contract — and needs it on the server.
You end up with a drop area in the browser and one class on the server that decides what to do with
each file.

## When to use this

Use `FileUpload` and `FileUploadHandler` whenever bytes have to travel from a person's machine to
your server.
Use [`FileInput`](../UI_COMPONENTS.md) instead when a form just needs a file chooser and its value —
`FileInput` never transfers anything.
Do not send file contents through an [`@RmiService`](../decide/rmi-vs-state-sync.md) call: an RMI
message is assembled whole in memory and is subject to `zeroz.ws.maxBinaryMessageBytes`.

**Uploads do not travel over the live WebSocket connection, so its message size limit does not apply
to them.**
They are posted to a separate HTTP address that writes them straight to disk, which is what makes
progress reporting and cancelling possible.

## Three steps

### 1. Put the component on a screen

```java
import com.zeroz4j.ui.component.FileUpload;

FileUpload upload = new FileUpload()
        .setTitle("Add your photos")
        .setAccept("image/*");
layout.add(upload);
```

That is the whole client side.
The component asks the server for permission, sends each file, shows a progress bar per file, and
shows whatever sentence the server sends back.

### 2. Write the handler

```java
import com.zeroz4j.server.FileUploadHandler;
import com.zeroz4j.server.UploadResult;
import com.zeroz4j.server.UploadedFile;
import jakarta.enterprise.context.ApplicationScoped;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@ApplicationScoped
public class SaveToDisk implements FileUploadHandler {

    private static final Path FOLDER = Path.of("uploads");

    @Override
    public UploadResult onFileUploaded(UploadedFile file) throws Exception {
        if (file.getSizeBytes() == 0) {
            return UploadResult.rejected("That file is empty.");
        }
        Files.createDirectories(FOLDER);
        // The stored name is generated here. file.getFileName() is text from the browser, never a path.
        Path target = FOLDER.resolve(UUID.randomUUID().toString());
        Files.move(file.getTempFile(), target, StandardCopyOption.REPLACE_EXISTING);
        return UploadResult.accepted("Saved.");
    }
}
```

`FileUploadHandler` is found the same way `AuthenticationProvider` and `LiveMutationListener` are:
make it an `@ApplicationScoped` CDI bean and the framework picks it up.
There is no route to map and nothing to register.

### 3. Nothing — the address is already there

**A WAR needs `zerozstack-server-jakarta`, and a standalone server needs `zerozstack-server-jaxrs`;
both already carry the upload address, so there is nothing to add and nothing to map.**

Either way the address is `zeroz4j-upload` under wherever the application is mounted, and the
component works that out for itself from the shell's `<base href>`.

The two entry points share every rule — the permission check, the size limit, the temporary file and
its deletion — so a WAR and a standalone server answer the same request identically, down to the
wording of a refusal.

!!! note "If you need that path for something else"
    In a WAR the servlet maps itself at `/zeroz4j-upload`, reserved by the framework in the same way
    `/wasm-rmi` is.
    A `web.xml` entry for the servlet name `zeroz4j-upload` overrides that mapping, and
    `metadata-complete="true"` ignores the annotation altogether — uploads then stop working.

## What the handler is given

`UploadedFile` carries the file and who sent it.

| Method | What it is | Who decided it |
|---|---|---|
| `getTempFile()` | the complete file, under a name the framework generated | the server |
| `getSizeBytes()` | how many bytes the server counted as they arrived | the server |
| `getPrincipal()`, `getRoles()`, `getTenantId()`, `getClientId()`, `getSessionId()` | the identity of the live connection that asked to upload | the server, at the handshake |
| `getFileName()` | the name the browser reported | **the browser** — treat it as text |
| `getContentType()` | the type the browser reported | **the browser** — treat it as text |

Return `UploadResult.accepted(message)` or `UploadResult.rejected(message)`.
The message is shown next to the file in the browser, so write it for the person sitting there:
"Saved.", or "That is not a picture. Please choose a JPEG or a PNG."

**The temporary file is deleted as soon as the handler returns**, whether it returned a result or
threw.
Move it or copy it inside the method; do not keep the `Path` and read it later.

## The size limit

The default is 25 MB per file.
Change it with a system property:

```bash
java -Dzeroz.upload.maxBytes=104857600 -cp "target/classes;target/libs/*" com.example.Server
```

| Property | Default | Meaning |
|---|---|---|
| `zeroz.upload.maxBytes` | `26214400` (25 MB) | the largest file the server accepts |
| `zeroz.upload.passSeconds` | `60` | how long an upload permission stays usable |
| `zeroz.upload.tempDir` | a `zeroz4j-uploads` folder inside the JVM's temporary directory | where a file is written while it arrives |

Put `zeroz.upload.tempDir` on the same filesystem as wherever your handler moves files to, so the
move is a rename rather than a copy.

## What the framework does for you

- **An upload needs a live connection.**
  The page asks its existing WebSocket connection for a one-time pass, and the upload address
  accepts nothing without one.
  A pass lasts 60 seconds, works once, and is accepted only from the browser it was issued to.
  So an upload carries exactly the same identity as every other call on that connection, with
  nothing extra for you to arrange — and there is no other way in: no API key, no signed URL.
- **The pass is checked before any file is created**, so a request without one writes nothing to
  disk.
- **The size is checked twice** — first against the length the request declares, before any of the
  body is read, and then against the bytes actually counted as they arrive.
- **The temporary file is named by the framework.** Nothing the browser sent is used to build a
  path.
- **The file is whole when the handler runs.** An upload that was cancelled or whose connection
  dropped is answered "That file did not finish sending. Please try again." and never reaches your
  code.
- **The temporary file is always deleted** — on success, on rejection, on a handler that threw, on a
  cancel, and on a connection that died mid-upload.
- **The upload address applies the same `zeroz.origins` rule as the WebSocket handshake**, so the
  pages allowed to open a connection are the pages allowed to upload.

## What the application must check itself

- **What the bytes actually are.**
  `getContentType()` is the browser's guess from the file extension.
  If it matters, read the start of the file:

    ```java
    byte[] head = new byte[4];
    try (InputStream in = Files.newInputStream(file.getTempFile())) {
        in.readNBytes(head, 0, head.length);
    }
    boolean png = head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G';
    ```

- **Whether this caller may upload at all**, and how much.
  The framework enforces one login and one size limit.
  Quotas, per-role limits and per-tenant rules are application rules — read `getPrincipal()`,
  `getRoles()` and `getTenantId()` and decide.
- **What the stored file is called.**
  Generate the name yourself, as the sample above does.
  `getFileName()` is a piece of text the browser sent: it can contain slashes, dots, control
  characters, or a name the operating system treats specially, and none of that is checked.
  Keep it as data — a database column, a text file beside the upload — if you want to show it later.
- **Virus scanning.** The framework does none.

## The checks in the browser are feedback only

`setAccept(...)` filters the file picker and warns about a dragged file that does not match, and the
component refuses a file over the limit without sending it.
Both exist so a person is told immediately rather than after a long upload.
Neither decides anything: the server checks the size again for itself, and ignores the type
altogether.

## Limits

- **One handler per application.**
  If more than one `FileUploadHandler` is deployed the framework uses one and logs a warning naming
  them all.
- **No resume and no chunking.**
  A cancelled or dropped upload starts again from the beginning.
- **No per-user or per-tenant quota**, and no limit on how many files can be uploaded at once.
  `zeroz.upload.maxBytes` applies to one file.
- **No virus scanning, and no image or document validation.**
- **The handler runs on the upload request's thread**, so a slow handler holds that request open.
  Hand long work to a background thread and return quickly.
