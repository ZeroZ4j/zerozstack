# Playing media and embedding pages

For an application that plays a recording or a video the server holds, or shows another page inside
one of its own screens.

## When to use this

- **`AudioPlayer` and `VideoPlayer`** play a file with the browser's own controls and give the
  application the position, the length and one event when the file has been watched.
- **`EmbeddedFrame`** shows another page, a video site or a page the application builds from text,
  and grows to the height of what it shows.
- Files the server holds are served by a `FileDownloadHandler`, described in
  [Accepting file uploads](file-uploads.md#serving-a-file-back). Use `setSource(url)` instead for an
  address that is already public.

## Playing a file

```java
AudioPlayer player = new AudioPlayer();
player.setTitle("Session 3");
player.setServerFile("recording", "42");           // the server's FileDownloadHandler decides
player.addWatchedListener(p -> progress.markHeard("42"));
Effect.create(() -> clock.setText(format(player.position().get())));
layout.add(player);
```

`VideoPlayer` is the same, with `setPoster(url)`, `requestFullscreen()` and `exitFullscreen()`.

| Call | What it does |
|---|---|
| `setSource(url)` | plays this address; a relative address resolves against the page's base address |
| `setServerFile(kind, id)` | asks the server for an address of its own file and plays that |
| `setTitle(text)` | the name a screen reader announces |
| `play()`, `pause()` | start and stop; a browser may refuse `play()` until the person has pressed something |
| `seekTo(seconds)`, `skip(seconds)` | move to a moment, or forward and back from here |
| `setPlaybackRate(rate)` | 1.0 is normal speed |
| `setSpeedControlVisible(true)` | adds a Speed list under the player |
| `position()`, `duration()`, `playing()`, `playbackRate()` | signals; read them inside an `Effect` |
| `error()` | signal holding a sentence when the file could not be opened, otherwise null |
| `addWatchedListener(...)` | fires once per source |

### The browser draws the controls

Play, pause, the scrubber, volume and full screen are the browser's own. They are in the tab order,
Space and Enter press them, the arrow keys move the scrubber, and a person's phone adds its own
features to them. The player adds a title and the signals above. The Speed list is a normal `Select`
with a caption, for browsers that hide speed in a menu.

### The watched event

The listener fires once, when playback reaches the end or nine tenths of the length
(`setWatchedFraction`), whichever comes first. Moving the scrubber to that point counts. Setting a new
source starts over. The listener runs on its own green thread, so it may call a service.

### Seeking and the server

A browser seeks by asking for a byte range. The download address answers `Range` requests, so a
person can drag the scrubber on a 95 MB recording without loading it first. If the server answers 401
because the address expired (60 minutes by default), call `setServerFile` again.

## Embedding a page

```java
EmbeddedFrame frame = new EmbeddedFrame();
frame.setTitle("Handout");
frame.setSandbox("allow-scripts allow-popups");
frame.setContent(html);
layout.add(frame);
```

- **`setContent(html)`** shows a page given as text, and adds the height script to it.
- **`setSource(url)`** shows an address. For a page the application serves, put
  `EmbeddedFrameScript.HEIGHT_SCRIPT` in it, or pass its HTML through
  `EmbeddedFrameScript.withHeightScript(html)`. `EmbeddedFrameScript.heightScript(origin)` posts only
  to the page at one origin. The class is in `zerozstack-shared-api`, so server code can use it.
- **A page that does not cooperate** keeps the starting height, `h-96`. Change it with
  `addClassName`, or turn following off with `setFitContent(false)`.
- **`setMinHeight` and `setMaxHeight`** bound whatever the page reports.
- **`contentHeight()`** is a signal with the last height taken.

### What the frame believes

A height message is used only when it comes from this frame's own window, from the expected origin, and
carries a finite non-negative number. The expected origin is that of the address given to
`setSource`; `null` for `setContent` and for a sandbox without `allow-same-origin`;
`setAllowedOrigin` overrides it. The number only sets the frame's height.

### Sandbox

`setSandbox` takes the browser's own tokens. The height script is a script, so a sandbox without
`allow-scripts` stops it and the frame keeps its starting height. Do not give a page you do not
control both `allow-scripts` and `allow-same-origin`; together they let it remove its own sandbox.
A video site's embedded player breaks inside most sandboxes: leave `setSandbox` unset for those and
list what they need in `setAllow("autoplay; fullscreen; picture-in-picture")`.

The page listens for heights from the moment a container puts the frame on the page with `add(...)`
until it is taken off.

### Why the script is cautious

The script reports the height of the `<body>`, not of the document. The document stretches to fill the
frame, so measuring it could never shrink the frame. It also limits how many growing measurements in
a row are sent when only a resize prompted them, because a frame that follows its page and a page
that follows its frame will otherwise grow each other without end. A load event, an image or a font
lifts the limit.
