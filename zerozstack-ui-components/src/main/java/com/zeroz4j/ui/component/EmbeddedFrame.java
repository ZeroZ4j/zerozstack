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

import com.zeroz4j.api.EmbeddedFrameScript;
import com.zeroz4j.signals.Signal;
import com.zeroz4j.signals.ValueSignal;
import org.teavm.jso.JSBody;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.html.HTMLElement;

/**
 * Shows another page inside this one, and grows or shrinks to the height of what it shows.
 *
 * <pre>{@code
 * EmbeddedFrame frame = new EmbeddedFrame();
 * frame.setTitle("Handout");
 * frame.setSandbox("allow-scripts allow-popups");
 * frame.setContent(html);                  // a page of your own; the height script is added for you
 * layout.add(frame);
 * }</pre>
 *
 * <h2>How it sizes itself</h2>
 * <p>A page cannot be measured from outside once it comes from another origin, so the page reports
 * its own height: {@link EmbeddedFrameScript#HEIGHT_SCRIPT} posts the height of its body to this
 * page. {@link #setContent(String)} adds the script to the page you give it. For a page you serve
 * yourself and show with {@link #setSource(String)}, add the script to that page, or run its HTML
 * through {@link EmbeddedFrameScript#withHeightScript(String)}. A page that does not cooperate keeps
 * the height the frame started with, {@code h-96}, and can set another with
 * {@code addClassName}.</p>
 *
 * <h2>What it believes</h2>
 * <p>A height message is accepted only when it comes from this frame's own window, from the origin
 * the frame was loaded from ({@code null} for {@link #setContent(String)} and for a sandbox without
 * {@code allow-same-origin}; {@link #setAllowedOrigin(String)} overrides), and carries a finite
 * non-negative number. Anything else on the page's message channel is ignored. The number only ever
 * sets the frame's height, between {@link #setMinHeight(int)} and {@link #setMaxHeight(int)}.</p>
 *
 * <h2>Sandbox</h2>
 * <p>{@link #setSandbox(String)} takes the browser's own tokens. A sandbox without
 * {@code allow-scripts} stops the height script running, so the frame keeps its starting height. Do
 * not combine {@code allow-scripts} with {@code allow-same-origin} for a page you do not control:
 * together they let it remove its own sandbox. Embedded video players need no sandbox, or one that
 * lists what they use.</p>
 *
 * <p>The listener that receives heights is added when a container puts the frame on the page with
 * {@code add(...)} and removed when it is taken off.</p>
 *
 * @since 0.11.0
 */
public class EmbeddedFrame extends Component implements HasStyle {

    private static final String SAME_ORIGIN_TOKEN = "allow-same-origin";

    private final ValueSignal<Integer> contentHeight = new ValueSignal<>(0);

    private String source;
    private String content;
    private boolean fitContent = true;
    private String sandbox;
    private String allowedOrigin;
    private int minHeight;
    private int maxHeight = 100000;
    private EventListener<Event> messageListener;

    /** Creates an empty frame, full width, with a starting height of {@code h-96}. */
    public EmbeddedFrame() {
        super("iframe");
        addClassName("w-full block border-0 h-96");
        HTMLElement element = getElement();
        element.setAttribute("frameborder", "0");
        element.setAttribute("scrolling", "no");
        element.setAttribute("referrerpolicy", "strict-origin-when-cross-origin");
        setTitle("Embedded content");
    }

    // ---------------------------------------------------------------- what to show

    /**
     * Shows the page at this address.
     *
     * @param url an address; relative ones resolve against the page's base address
     * @return this frame
     */
    public EmbeddedFrame setSource(String url) {
        this.source = url;
        this.content = null;
        getElement().removeAttribute("srcdoc");
        if (url == null || url.isEmpty()) {
            getElement().removeAttribute("src");
        } else {
            getElement().setAttribute("src", url);
        }
        return this;
    }

    /**
     * Shows a page given as text. With {@link #setFitContent(boolean) fit content} on, which is the
     * default, the height script is added to it.
     *
     * @param html a whole page or a fragment
     * @return this frame
     */
    public EmbeddedFrame setContent(String html) {
        this.content = html;
        this.source = null;
        getElement().removeAttribute("src");
        applyContent();
        return this;
    }

    /**
     * Whether the frame follows the height its page reports. On by default. Turn it off to keep a
     * fixed height.
     *
     * @param fit whether to follow the page
     * @return this frame
     */
    public EmbeddedFrame setFitContent(boolean fit) {
        this.fitContent = fit;
        getElement().setAttribute("scrolling", fit ? "no" : "auto");
        if (content != null) {
            applyContent();
        }
        return this;
    }

    /**
     * What a screen reader announces for the frame. Always set one: an unnamed frame is read as
     * "frame" and nothing else.
     *
     * @param title for example the name of the page inside
     * @return this frame
     */
    public EmbeddedFrame setTitle(String title) {
        getElement().setAttribute("title", title == null ? "" : title);
        return this;
    }

    // ---------------------------------------------------------------- what it may do

    /**
     * Restricts the page inside, with the browser's own sandbox tokens, for example
     * {@code "allow-scripts allow-popups"}. An empty string applies every restriction; null removes
     * the sandbox.
     *
     * @param tokens the tokens separated by spaces, or null
     * @return this frame
     */
    public EmbeddedFrame setSandbox(String tokens) {
        this.sandbox = tokens;
        if (tokens == null) {
            getElement().removeAttribute("sandbox");
        } else {
            getElement().setAttribute("sandbox", tokens);
        }
        return this;
    }

    /**
     * What the page inside may ask the browser for, for example {@code "autoplay; fullscreen"}.
     *
     * @param permissions the browser's permissions policy list, or null to remove it
     * @return this frame
     */
    public EmbeddedFrame setAllow(String permissions) {
        if (permissions == null || permissions.isEmpty()) {
            getElement().removeAttribute("allow");
            getElement().removeAttribute("allowfullscreen");
        } else {
            getElement().setAttribute("allow", permissions);
            if (permissions.contains("fullscreen")) {
                getElement().setAttribute("allowfullscreen", "true");
            }
        }
        return this;
    }

    /**
     * The one origin a height message may come from, when it is not the origin of the page the frame
     * was loaded from.
     *
     * @param origin for example {@code https://pages.example.com}; {@code null} (the text) for a page
     *               without an origin; {@code *} accepts any origin from this frame's window
     * @return this frame
     */
    public EmbeddedFrame setAllowedOrigin(String origin) {
        this.allowedOrigin = origin;
        return this;
    }

    /**
     * The smallest height the frame is given.
     *
     * @param pixels a height in pixels
     * @return this frame
     */
    public EmbeddedFrame setMinHeight(int pixels) {
        this.minHeight = Math.max(0, pixels);
        return this;
    }

    /**
     * The largest height the frame is given, whatever the page reports. The default is 100000.
     *
     * @param pixels a height in pixels
     * @return this frame
     */
    public EmbeddedFrame setMaxHeight(int pixels) {
        this.maxHeight = Math.max(1, pixels);
        return this;
    }

    /** @return the last height the page reported and the frame took, in pixels; zero until then */
    public Signal<Integer> contentHeight() {
        return contentHeight;
    }

    // ---------------------------------------------------------------- listening

    @Override
    protected void onAttach() {
        super.onAttach();
        if (messageListener == null) {
            messageListener = this::onMessage;
            Window.current().addEventListener("message", messageListener);
        }
    }

    @Override
    protected void onDetach() {
        if (messageListener != null) {
            Window.current().removeEventListener("message", messageListener);
            messageListener = null;
        }
        super.onDetach();
    }

    private void onMessage(Event event) {
        double reported = Frames.acceptedHeight(event, getElement(), expectedOrigin());
        if (reported < 0.0 || !fitContent) {
            return;
        }
        int height = (int) Math.round(reported);
        height = Math.max(minHeight, Math.min(maxHeight, height));
        if (height == contentHeight.get().intValue()) {
            return;
        }
        getElement().getStyle().setProperty("height", height + "px");
        contentHeight.set(height);
    }

    /** The origin a message from this frame must carry. */
    private String expectedOrigin() {
        if (allowedOrigin != null) {
            return allowedOrigin;
        }
        if (content != null) {
            return "null";
        }
        if (sandbox != null && !hasToken(sandbox, SAME_ORIGIN_TOKEN)) {
            return "null";
        }
        return source == null ? "null" : Frames.originOf(source);
    }

    private void applyContent() {
        if (content == null) {
            getElement().removeAttribute("srcdoc");
            return;
        }
        getElement().setAttribute("srcdoc",
                fitContent ? EmbeddedFrameScript.withHeightScript(content) : content);
    }

    private static boolean hasToken(String tokens, String wanted) {
        String padded = " " + tokens.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ') + " ";
        return padded.contains(" " + wanted + " ");
    }

    @Override
    public Component getComponent() {
        return this;
    }

    /** The browser calls the frame needs. */
    private static final class Frames {

        private Frames() {
        }

        /**
         * The height a message reports, or -1 when it is not an acceptable height message from this
         * frame. All the checks are here, in one place, so none can be skipped.
         */
        @JSBody(params = { "event", "frame", "allowed" }, script =
                "if (!event || !frame.contentWindow || event.source !== frame.contentWindow) { return -1; }"
                + "if (allowed !== '*' && event.origin !== allowed) { return -1; }"
                + "var data = event.data;"
                + "if (!data || typeof data !== 'object' || data.zeroz4jFrame !== 'height') { return -1; }"
                + "var height = data.height;"
                + "if (typeof height !== 'number' || !isFinite(height) || height < 0) { return -1; }"
                + "return height;")
        static native double acceptedHeight(Event event, HTMLElement frame, String allowed);

        @JSBody(params = { "url" }, script =
                "try { return new URL(url, document.baseURI).origin; } catch (ignored) { return 'null'; }")
        static native String originOf(String url);
    }
}
