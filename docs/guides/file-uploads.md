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
progress reporting and canceling possible.

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

## Several kinds of upload, each with its own limit

An application that takes 2 MB avatars and 100 MB recordings writes one handler for each and names it.
The page picks a handler by that name.

```java
@ApplicationScoped
public class RecordingUploads implements FileUploadHandler {

    @Override
    public String getKind() {
        return "recording";
    }

    @Override
    public long getMaxBytes() {
        return 200L * 1024 * 1024;
    }

    @Override
    public UploadResult onFileUploaded(UploadedFile file) throws Exception {
        // move file.getTempFile() into the recording store
        return UploadResult.accepted("Saved.");
    }
}
```

```java
layout.add(new FileUpload().setKind("recording").setAccept("audio/*"));
```

- **A box that sets no kind reaches the handler whose kind is empty**, which is what every handler
  written before kinds existed has. Existing code compiles and behaves as before.
- **The limit of a kind** is the property `zeroz.upload.maxBytes.<kind>` if it is set, else the
  handler's `getMaxBytes()` if that is above zero, else `zeroz.upload.maxBytes`. For the default
  handler a `zeroz.upload.maxBytes` that is set explicitly wins over `getMaxBytes()`.
- **The server decides.** The kind and the declared size are checked when the pass is issued, and the
  bytes are counted against the limit of that kind as they arrive. A pass is tied to its kind, so a
  page cannot ask for the recording limit and send to the avatar handler. An unknown or malformed kind
  gets no pass. The limit shown in the box and the check before sending are feedback only.
- **Kinds are letters, digits, dot, dash and underscore, at most 64 characters.**
- `UploadedFile.getKind()` tells the handler which kind it received.

### Large files

A file is copied to the temporary file in 64 KB pieces on all three servers (Helidon, the Jakarta WAR
servlet and Vidocq), so a 100 MB recording costs the same memory as a 1 KB one. Progress is reported
by the browser as the bytes leave it, and the cancel button works at any point; a canceled or dropped
upload leaves nothing behind. On Vidocq the request size ceiling is set at start-up from the largest
limit of any kind, so a handler deployed with a bigger `getMaxBytes()` is covered.

## Serving a file back

A page can play or show a file the server holds without the bytes crossing the live connection. The
application writes one `@ApplicationScoped` class implementing `FileDownloadHandler`:

```java
@ApplicationScoped
public class RecordingDownloads implements FileDownloadHandler {

    @Inject Recordings recordings;

    @Override
    public String getKind() {
        return "recording";
    }

    @Override
    public FileDownload open(DownloadRequest request) {
        Recording recording = recordings.find(request.getId());
        if (recording == null) {
            return FileDownload.notFound();
        }
        if (!recording.isVisibleTo(request.getPrincipal())) {
            return FileDownload.forbidden();
        }
        return FileDownload.of(recording.getPath()).withContentType("audio/mpeg");
    }
}
```

The page uses `new AudioPlayer().setServerFile("recording", "42")` or `VideoPlayer`. See
[Playing media and embedding pages](media.md).

- **The address is `zeroz4j-download`** under wherever the application is mounted, on all three servers.
  A WAR needs `zerozstack-server-jakarta` and a standalone Helidon server `zerozstack-server-jaxrs`, as
  for uploads; Vidocq serves it itself. Reserved by the framework, like `zeroz4j-upload`.
- **The page asks the live connection for the address.** The server issues a random token tied to the
  browser (the signed browser id cookie), the kind and id, and the identity of the connection. It
  works repeatedly for 60 minutes (`zeroz.download.linkMinutes`), because a media element makes a
  request for every position it seeks to.
- **The handler runs on every request**, with the principal, roles, tenant, client id and session id
  the address was issued to. Check them each time. The id is text from the page: look it up, never use
  it as a path.
- **The framework sends the file** with `Content-Type` (yours, else from the extension, else
  `application/octet-stream`), `Content-Length`, `Accept-Ranges: bytes`, `ETag` and `Last-Modified`.
  `Range` requests answer 206 with `Content-Range`, a start past the end answers 416, `HEAD` and
  `If-None-Match` work, and several ranges in one request get the whole file, which HTTP allows. The
  file is read in 64 KB pieces, never into memory, and never over the RMI socket.
- **Audio, video, pictures other than SVG, PDF and plain text are sent inline.** Everything else,
  HTML included, is sent as `attachment`, with `X-Content-Type-Options: nosniff`, so a file a user
  uploaded cannot run as a page of the application. `asAttachment()` forces a download. The file name
  in the header is cleaned of quotes, slashes and line breaks.
- **A refused request answers with a short sentence**: 401 for an address that is unknown, expired or
  presented by another browser, 403 for `forbidden()`, 404 for `notFound()` or a missing file, and 500
  with a general apology when the handler threw.

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
- **The file is whole when the handler runs.** An upload that was canceled or whose connection
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

- **One handler per kind.**
  If two `FileUploadHandler` beans share a kind the framework uses one and logs a warning naming
  them both.
- **No resume and no chunking.**
  A canceled or dropped upload starts again from the beginning.
- **No per-user or per-tenant quota**, and no limit on how many files can be uploaded at once.
  `zeroz.upload.maxBytes` applies to one file.
- **No virus scanning, and no image or document validation.**
- **The handler runs on the upload request's thread**, so a slow handler holds that request open.
  Hand long work to a background thread and return quickly.
