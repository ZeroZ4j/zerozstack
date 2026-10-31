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
package com.zeroz4j.ui.component;

import org.teavm.jso.JSBody;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Plays a video file with the browser's own controls, which include full screen.
 *
 * <p>The video plays inside the page on a phone ({@code playsinline}); without that an iPhone takes
 * the screen over the moment the video starts. Everything {@link MediaPlayer} offers applies, and
 * {@link #requestFullscreen()} and {@link #exitFullscreen()} do from code what the full screen
 * control does. Browsers only allow full screen straight after a person pressed something, and an
 * iPhone has no full screen for a page element at all, so check {@link #isFullscreenAvailable()}
 * before offering a control of your own.</p>
 *
 * @since 0.11.0
 */
public class VideoPlayer extends MediaPlayer {

    /** Creates a player with nothing loaded. */
    public VideoPlayer() {
        super("video", "Video");
        mediaElement().setAttribute("playsinline", "playsinline");
        mediaElement().getClassList().add("bg-black");
    }

    /**
     * The still picture shown before the video starts.
     *
     * @param url the address of the picture, or null to remove it
     * @return this player
     */
    public VideoPlayer setPoster(String url) {
        if (url == null || url.isEmpty()) {
            mediaElement().removeAttribute("poster");
        } else {
            mediaElement().setAttribute("poster", url);
        }
        return this;
    }

    /** Puts the video on the whole screen. Does nothing where the browser refuses. */
    public void requestFullscreen() {
        Fullscreen.enter(mediaElement());
    }

    /** Takes the page out of full screen. */
    public void exitFullscreen() {
        Fullscreen.exit();
    }

    /**
     * Whether this browser can put a page element on the whole screen. An iPhone cannot.
     *
     * @return true when {@link #requestFullscreen()} can work
     */
    public boolean isFullscreenAvailable() {
        return Fullscreen.available();
    }

    /** The browser calls full screen needs. */
    private static final class Fullscreen {

        private Fullscreen() {
        }

        @JSBody(script = "return !!(document.fullscreenEnabled || document.webkitFullscreenEnabled);")
        static native boolean available();

        @JSBody(params = { "target" }, script =
                "try {"
                + " var result;"
                + " if (target.requestFullscreen) { result = target.requestFullscreen(); }"
                + " else if (target.webkitRequestFullscreen) { target.webkitRequestFullscreen(); }"
                + " if (result && result.catch) { result.catch(function (ignored) { }); }"
                + "} catch (ignored) { }")
        static native void enter(HTMLElement target);

        @JSBody(script =
                "try {"
                + " var showing = document.fullscreenElement || document.webkitFullscreenElement;"
                + " if (showing) {"
                + "  if (document.exitFullscreen) { document.exitFullscreen(); }"
                + "  else if (document.webkitExitFullscreen) { document.webkitExitFullscreen(); }"
                + " }"
                + "} catch (ignored) { }")
        static native void exit();
    }
}
