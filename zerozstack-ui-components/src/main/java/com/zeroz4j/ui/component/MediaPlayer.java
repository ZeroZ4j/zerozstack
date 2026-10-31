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

import com.zeroz4j.api.RmiClientExecutor;
import com.zeroz4j.signals.Signal;
import com.zeroz4j.signals.ValueSignal;
import com.zeroz4j.ui.layout.Div;
import com.zeroz4j.ui.layout.Span;
import com.zeroz4j.ui.theme.Emphasis;
import com.zeroz4j.ui.theme.TextStyle;
import org.teavm.jso.JSBody;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.html.HTMLElement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * What {@link AudioPlayer} and {@link VideoPlayer} share: a browser media element with its own
 * controls, a source, and the state a screen needs as signals.
 *
 * <p>The browser's own controls do the playing, pausing, seeking, volume and full screen, because
 * they are already reachable by keyboard, already named, and already what a person's phone adds
 * its own features to. This class adds what the controls cannot tell the application: where the
 * player is ({@link #position()}), how long the file is ({@link #duration()}), whether it is
 * playing ({@link #playing()}), and one event when the person has watched it
 * ({@link #addWatchedListener}).</p>
 *
 * <p>The source is a plain address ({@link #setSource}) or a file the server holds
 * ({@link #setServerFile}), which asks the server for an address its
 * {@code com.zeroz4j.server.FileDownloadHandler} will answer, including the range requests the
 * browser makes when somebody drags the scrubber.</p>
 *
 * @since 0.11.0
 */
public abstract class MediaPlayer extends Div {

    /** Called once when the person has played the file to the end or past the watched fraction. */
    @FunctionalInterface
    public interface WatchedListener {

        /**
         * @param player the player that was watched
         */
        void onWatched(MediaPlayer player);
    }

    private static final List<String> SPEEDS = Arrays.asList("0.5x", "0.75x", "1x", "1.25x", "1.5x", "2x");
    private static final double[] SPEED_VALUES = { 0.5, 0.75, 1.0, 1.25, 1.5, 2.0 };

    private final Component media;
    private final ValueSignal<Double> position = new ValueSignal<>(0.0);
    private final ValueSignal<Double> duration = new ValueSignal<>(0.0);
    private final ValueSignal<Boolean> playing = new ValueSignal<>(Boolean.FALSE);
    private final ValueSignal<Double> playbackRate = new ValueSignal<>(1.0);
    private final ValueSignal<String> error = new ValueSignal<>(null);
    private final List<WatchedListener> watchedListeners = new ArrayList<>();
    private final Span errorLine = new Span("");

    private Select speed;
    private double watchedFraction = 0.9;
    private boolean watchedFired;
    private int sourceGeneration;

    /**
     * @param tag          {@code audio} or {@code video}
     * @param defaultTitle the name a screen reader announces until {@link #setTitle} says better
     */
    protected MediaPlayer(String tag, String defaultTitle) {
        addClassName("flex flex-col gap-2 w-full");
        media = new Component(tag) {
        };
        HTMLElement element = media.getElement();
        element.setAttribute("controls", "controls");
        element.setAttribute("preload", "metadata");
        element.setAttribute("title", defaultTitle);
        media.setAriaLabel(defaultTitle);
        media.getElement().setClassName("w-full");

        errorLine.addClassName(TextStyle.CAPTION.getClassNames(Emphasis.FULL) + " text-error");
        errorLine.setVisible(false);

        add(media, errorLine);
        wire(element);
    }

    // ---------------------------------------------------------------- what to play

    /**
     * Plays the file at this address. A relative address is resolved against the page's base
     * address, the same as a link. Setting a source starts the player over: position zero, not yet
     * watched.
     *
     * @param url the address, or null to unload
     * @return this player
     */
    public MediaPlayer setSource(String url) {
        sourceGeneration++;
        clearError();
        loadAddress(url);
        return this;
    }

    /**
     * Plays a file the server holds. The server's {@code FileDownloadHandler} for this kind decides
     * whether this person may have it, on this request and on every range request after it.
     *
     * <p>The address is fetched in the background; the file starts loading as soon as it arrives. If
     * the server refuses, {@link #error()} carries the sentence and the player shows it.</p>
     *
     * @param kind the kind the handler names with {@code getKind()}, empty for the default handler
     * @param id   the id the handler looks the file up by
     * @return this player
     */
    public MediaPlayer setServerFile(String kind, String id) {
        int generation = ++sourceGeneration;
        clearError();
        loadAddress(null);
        new Thread(() -> {
            String address;
            try {
                address = (String) RmiClientExecutor.executeCall("com.zeroz4j.api.FileDownloadRpc",
                        "requestDownloadLink", new Object[] { kind == null ? "" : kind, id });
            } catch (RuntimeException ex) {
                if (generation == sourceGeneration) {
                    showError(readable(ex.getMessage()));
                }
                return;
            }
            if (generation != sourceGeneration) {
                return;
            }
            if (address == null || address.isEmpty()) {
                showError("We could not open that file.");
                return;
            }
            loadAddress(address);
        }).start();
        return this;
    }

    /**
     * The name a screen reader announces for the player.
     *
     * @param title for example the name of the recording
     * @return this player
     */
    public MediaPlayer setTitle(String title) {
        if (title != null && !title.trim().isEmpty()) {
            media.getElement().setAttribute("title", title);
            media.setAriaLabel(title);
        }
        return this;
    }

    /**
     * How much the browser loads before anyone presses play.
     *
     * @param preload {@code none}, {@code metadata} (the default) or {@code auto}
     * @return this player
     */
    public MediaPlayer setPreload(String preload) {
        media.getElement().setAttribute("preload", preload);
        return this;
    }

    // ---------------------------------------------------------------- controlling it

    /** Starts or resumes playback. A browser may refuse until the person has pressed something. */
    public void play() {
        MediaScripts.play(media.getElement());
    }

    /** Pauses playback. */
    public void pause() {
        MediaScripts.pause(media.getElement());
    }

    /**
     * Moves to a moment in the file.
     *
     * @param seconds seconds from the start; clamped to the length of the file
     */
    public void seekTo(double seconds) {
        MediaScripts.seek(media.getElement(), Math.max(0.0, seconds));
    }

    /**
     * Moves forward or back from where the player is.
     *
     * @param seconds how far; negative goes back
     */
    public void skip(double seconds) {
        seekTo(MediaScripts.position(media.getElement()) + seconds);
    }

    /**
     * Sets how fast the file plays.
     *
     * @param rate 1.0 is normal speed; browsers accept roughly 0.25 to 4
     */
    public void setPlaybackRate(double rate) {
        MediaScripts.setRate(media.getElement(), rate);
    }

    /**
     * Shows a Speed list under the player, for browsers whose own controls hide the speed in a menu.
     *
     * @param visible whether to show it
     * @return this player
     */
    public MediaPlayer setSpeedControlVisible(boolean visible) {
        if (visible && speed == null) {
            speed = new Select();
            speed.withLabel("Speed");
            speed.setItems(SPEEDS);
            speed.setValue(speedLabel(playbackRate.get()));
            speed.addValueChangeListener(event -> {
                String chosen = event.getValue();
                int index = SPEEDS.indexOf(chosen);
                if (index >= 0) {
                    setPlaybackRate(SPEED_VALUES[index]);
                }
            });
            speed.addClassName("select-sm w-32");
            add(speed);
        }
        if (speed != null) {
            speed.setVisible(visible);
        }
        return this;
    }

    /**
     * The Speed list, for a test or for styling.
     *
     * @return the list, or null until {@link #setSpeedControlVisible} has shown it
     */
    public Select getSpeedControl() {
        return speed;
    }

    // ---------------------------------------------------------------- what it tells you

    /** @return where the player is, in seconds; follows the file as it plays and when it is moved */
    public Signal<Double> position() {
        return position;
    }

    /** @return the length of the file in seconds; zero until the browser knows it, and for a stream */
    public Signal<Double> duration() {
        return duration;
    }

    /** @return whether the file is playing right now */
    public Signal<Boolean> playing() {
        return playing;
    }

    /** @return the playing speed; 1.0 is normal */
    public Signal<Double> playbackRate() {
        return playbackRate;
    }

    /** @return a sentence saying why the file could not be opened, or null when nothing is wrong */
    public Signal<String> error() {
        return error;
    }

    /**
     * Hears about the file being watched: once per source, when playback reaches the end or the
     * watched fraction of the length (nine tenths by default), whichever comes first. The listener
     * runs on its own green thread, so it may call a service.
     *
     * @param listener what to do
     * @return this player
     */
    public MediaPlayer addWatchedListener(WatchedListener listener) {
        if (listener != null) {
            watchedListeners.add(listener);
        }
        return this;
    }

    /**
     * How much of the file counts as watched.
     *
     * @param fraction between 0 and 1; the default is 0.9, so a closing minute of credits does not
     *                 have to be sat through
     * @return this player
     */
    public MediaPlayer setWatchedFraction(double fraction) {
        this.watchedFraction = Math.max(0.01, Math.min(1.0, fraction));
        return this;
    }

    /**
     * The browser element, for the rare thing this class does not cover.
     *
     * @return the {@code audio} or {@code video} element
     */
    protected HTMLElement mediaElement() {
        return media.getElement();
    }

    // ---------------------------------------------------------------- wiring

    private void wire(HTMLElement element) {
        // Raw listeners, not addDomEventListener: these fire several times a second and touch only
        // signals, so a green thread for each would be pure cost. Nothing here suspends.
        element.addEventListener("timeupdate", (Event event) -> {
            double now = MediaScripts.position(element);
            position.set(now);
            double length = MediaScripts.duration(element);
            if (!watchedFired && length > 0.0 && now / length >= watchedFraction) {
                fireWatched();
            }
        });
        element.addEventListener("durationchange", (Event event) ->
                duration.set(MediaScripts.duration(element)));
        element.addEventListener("loadedmetadata", (Event event) ->
                duration.set(MediaScripts.duration(element)));
        element.addEventListener("play", (Event event) -> playing.set(Boolean.TRUE));
        element.addEventListener("pause", (Event event) -> playing.set(Boolean.FALSE));
        element.addEventListener("ended", (Event event) -> {
            playing.set(Boolean.FALSE);
            fireWatched();
        });
        element.addEventListener("ratechange", (Event event) -> {
            double rate = MediaScripts.rate(element);
            playbackRate.set(rate);
            if (speed != null) {
                String label = speedLabel(rate);
                if (label != null) {
                    speed.setValue(label);
                }
            }
        });
        element.addEventListener("error", (Event event) -> {
            if (MediaScripts.hasSource(element)) {
                showError("We could not play that file.");
            }
        });
    }

    private void loadAddress(String url) {
        HTMLElement element = media.getElement();
        watchedFired = false;
        position.set(0.0);
        duration.set(0.0);
        playing.set(Boolean.FALSE);
        if (url == null || url.isEmpty()) {
            MediaScripts.unload(element);
        } else {
            element.setAttribute("src", url);
        }
    }

    private void fireWatched() {
        if (watchedFired) {
            return;
        }
        watchedFired = true;
        if (watchedListeners.isEmpty()) {
            return;
        }
        // A listener is application code and may well call a service, so it gets its own green thread.
        new Thread(() -> {
            for (WatchedListener listener : watchedListeners) {
                listener.onWatched(this);
            }
        }).start();
    }

    private void showError(String message) {
        error.set(message);
        errorLine.setText(message);
        errorLine.setVisible(true);
    }

    private void clearError() {
        error.set(null);
        errorLine.setText("");
        errorLine.setVisible(false);
    }

    private static String speedLabel(double rate) {
        for (int i = 0; i < SPEED_VALUES.length; i++) {
            if (Math.abs(SPEED_VALUES[i] - rate) < 0.001) {
                return SPEEDS.get(i);
            }
        }
        return null;
    }

    private static String readable(String message) {
        if (message == null || message.isEmpty() || message.indexOf(' ') < 0) {
            return "We could not open that file.";
        }
        return message;
    }

    /** The browser calls this class needs, kept apart so each script is small enough to read. */
    static final class MediaScripts {

        private MediaScripts() {
        }

        @JSBody(params = { "media" }, script =
                "try { var result = media.play(); if (result && result.catch) "
                + "{ result.catch(function (ignored) { }); } } catch (ignored) { }")
        static native void play(HTMLElement media);

        @JSBody(params = { "media" }, script = "try { media.pause(); } catch (ignored) { }")
        static native void pause(HTMLElement media);

        @JSBody(params = { "media", "seconds" }, script =
                "try { media.currentTime = seconds; } catch (ignored) { }")
        static native void seek(HTMLElement media, double seconds);

        @JSBody(params = { "media" }, script = "return media.currentTime || 0;")
        static native double position(HTMLElement media);

        @JSBody(params = { "media" }, script =
                "return isFinite(media.duration) ? media.duration : 0;")
        static native double duration(HTMLElement media);

        @JSBody(params = { "media", "rate" }, script =
                "try { media.playbackRate = rate; } catch (ignored) { }")
        static native void setRate(HTMLElement media, double rate);

        @JSBody(params = { "media" }, script = "return media.playbackRate || 1;")
        static native double rate(HTMLElement media);

        @JSBody(params = { "media" }, script =
                "return !!media.getAttribute('src');")
        static native boolean hasSource(HTMLElement media);

        @JSBody(params = { "media" }, script =
                "try { media.pause(); media.removeAttribute('src'); media.load(); } "
                + "catch (ignored) { }")
        static native void unload(HTMLElement media);
    }
}
