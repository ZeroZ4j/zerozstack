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
package com.zeroz4j.api;

/**
 * The small script a page needs so that an {@code EmbeddedFrame} can size itself to it.
 *
 * <p>Put {@link #HEIGHT_SCRIPT} in the page that is shown inside the frame, or run the page through
 * {@link #withHeightScript(String)}. The script posts the height of the page's body to the page that
 * holds the frame, once the page has loaded and again whenever its images, fonts or layout change.
 * It posts a number and nothing else.</p>
 *
 * <p>Two choices in it came from measuring real pages. It reports the height of the
 * {@code <body>}, not of the document: the document stretches to fill the frame, so measuring it
 * would only ever say "as tall as I already am" and a short page could never shrink the frame. And
 * it caps how many measurements in a row that only grow may be sent when nothing but a resize
 * prompted them, because a frame that follows its page's height and a page that follows its frame's
 * height will otherwise grow each other without end. A real event (load, an image or font arriving)
 * lifts the cap.</p>
 *
 * <p>The script runs in the embedded page, so a frame with a {@code sandbox} must include
 * {@code allow-scripts} for it to run at all. Without the script the frame keeps the height the host
 * gave it.</p>
 *
 * @since 0.11.0
 */
public final class EmbeddedFrameScript {

    /** The key of the message the script posts: {@code {zeroz4jFrame: "height", height: 480}}. */
    public static final String MESSAGE_KEY = "zeroz4jFrame";

    /** The value of {@link #MESSAGE_KEY} for a height message. */
    public static final String HEIGHT_TYPE = "height";

    /**
     * The script, as a {@code <script>} element, posting to any parent page. Fine for a number;
     * use {@link #heightScript(String)} to name the one page that may receive it.
     */
    public static final String HEIGHT_SCRIPT = heightScript("*");

    private EmbeddedFrameScript() {
    }

    /**
     * The script, posting only to the page at one origin.
     *
     * @param parentOrigin the origin of the page that holds the frame, for example
     *                     {@code https://app.example.com}, or {@code *} for any
     * @return a {@code <script>} element
     * @throws IllegalArgumentException when the origin is not {@code *} or a scheme, host and
     *                                  optional port
     */
    public static String heightScript(String parentOrigin) {
        if (parentOrigin == null || !isOrigin(parentOrigin)) {
            throw new IllegalArgumentException("Not an origin: " + parentOrigin);
        }
        return "<script>(function(){"
                + "if(window.parent===window){return;}"
                + "var target='" + parentOrigin + "';"
                + "var lastPosted=0;var growthStreak=0;var MAX_UNTRUSTED_GROWTH=3;"
                + "function measure(){"
                + "var body=document.body;"
                + "var height=body?Math.max(body.scrollHeight,body.offsetHeight):0;"
                + "if(!height){height=document.documentElement.scrollHeight;}"
                + "return height;}"
                + "function post(height){"
                + "if(height===lastPosted){return;}"
                + "lastPosted=height;"
                + "try{window.parent.postMessage({" + MESSAGE_KEY + ":'" + HEIGHT_TYPE
                + "',height:height},target);}catch(ignored){}}"
                + "function fit(trusted){"
                + "try{"
                + "var height=measure();"
                + "if(height<=0){return;}"
                + "if(trusted){growthStreak=0;}"
                + "else if(height>lastPosted){growthStreak++;if(growthStreak>MAX_UNTRUSTED_GROWTH){return;}}"
                + "post(height);"
                + "}catch(ignored){}}"
                + "window.addEventListener('load',function(){fit(true);});"
                + "window.addEventListener('resize',function(){fit(false);});"
                + "document.addEventListener('load',function(event){"
                + "var node=event.target;"
                + "if(node&&(node.tagName==='IMG'||node.tagName==='VIDEO'||node.tagName==='AUDIO'"
                + "||node.tagName==='IFRAME')){fit(true);}"
                + "},true);"
                + "if(window.ResizeObserver&&document.body){"
                + "try{new ResizeObserver(function(){fit(false);}).observe(document.body);}"
                + "catch(ignored){}}"
                + "if(document.fonts&&document.fonts.ready){"
                + "document.fonts.ready.then(function(){fit(true);}).catch(function(){});}"
                + "setTimeout(function(){fit(true);},60);"
                + "setTimeout(function(){fit(true);},400);"
                + "fit(true);"
                + "})();</script>";
    }

    /**
     * The page with {@link #HEIGHT_SCRIPT} added, before {@code </head>} or else before
     * {@code </body>} or else at the end, never before the doctype.
     *
     * @param html a whole page or a fragment
     * @return the page with the script in it; null and empty come back unchanged
     */
    public static String withHeightScript(String html) {
        return withHeightScript(html, "*");
    }

    /**
     * The page with the script added, posting only to the page at one origin.
     *
     * @param html         a whole page or a fragment
     * @param parentOrigin the origin of the page that holds the frame, or {@code *}
     * @return the page with the script in it; null and empty come back unchanged
     */
    public static String withHeightScript(String html, String parentOrigin) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        String script = parentOrigin == null || "*".equals(parentOrigin) ? HEIGHT_SCRIPT
                : heightScript(parentOrigin);
        String lower = html.toLowerCase();
        int at = lower.lastIndexOf("</head>");
        if (at < 0) {
            at = lower.lastIndexOf("</body>");
        }
        if (at < 0) {
            return html + script;
        }
        return html.substring(0, at) + script + html.substring(at);
    }

    private static boolean isOrigin(String origin) {
        if ("*".equals(origin)) {
            return true;
        }
        int scheme = origin.indexOf("://");
        if (scheme < 1) {
            return false;
        }
        String name = origin.substring(0, scheme);
        if (!"http".equals(name) && !"https".equals(name)) {
            return false;
        }
        String hostAndPort = origin.substring(scheme + 3);
        if (hostAndPort.isEmpty()) {
            return false;
        }
        for (int i = 0; i < hostAndPort.length(); i++) {
            char c = hostAndPort.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')
                    || c == '.' || c == '-' || c == ':' || c == '[' || c == ']';
            if (!ok) {
                return false;
            }
        }
        return true;
    }
}
