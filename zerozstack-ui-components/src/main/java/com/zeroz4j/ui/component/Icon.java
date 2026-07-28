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

import com.zeroz4j.ui.layout.Span;

/**
 * Inline SVG glyphs (24ÃƒÆ’Ã¢â‚¬â€24 viewBox, stroke-based, currentColor) ÃƒÂ¢Ã¢â€šÂ¬Ã¢â‚¬Â no icon font, no CDN.
 * Usage: {@code Icon.of("gear")} or {@code Icon.of("play", "w-6 h-6 text-success")}.
 */
public final class Icon extends Span {

    private Icon(String svgPaths, String sizeClasses) {
        addClassName("inline-flex items-center justify-center shrink-0");
        getElement().setInnerHTML(
            "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 24 24\" fill=\"none\" "
            + "stroke=\"currentColor\" stroke-width=\"2\" stroke-linecap=\"round\" "
            + "stroke-linejoin=\"round\" class=\"" + sizeClasses + "\">" + svgPaths + "</svg>");
    }

    public static Icon of(String name) {
        return of(name, "w-4 h-4");
    }

    public static Icon of(String name, String sizeClasses) {
        return new Icon(paths(name), sizeClasses);
    }

    private static String paths(String name) {
        return switch (name) {
            case "gear" -> "<circle cx='12' cy='12' r='3'/><path d='M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33H9a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z'/>";
            case "plus" -> "<line x1='12' y1='5' x2='12' y2='19'/><line x1='5' y1='12' x2='19' y2='12'/>";
            case "x" -> "<line x1='18' y1='6' x2='6' y2='18'/><line x1='6' y1='6' x2='18' y2='18'/>";
            case "check" -> "<polyline points='20 6 9 17 4 12'/>";
            case "copy" -> "<rect x='9' y='9' width='13' height='13' rx='2'/><path d='M5 15H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v1'/>";
            case "chat" -> "<path d='M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z'/>";
            case "play" -> "<polygon points='5 3 19 12 5 21 5 3'/>";
            case "stop" -> "<rect x='6' y='6' width='12' height='12' rx='1'/>";
            case "graph" -> "<circle cx='5' cy='6' r='2'/><circle cx='19' cy='6' r='2'/><circle cx='12' cy='18' r='2'/><line x1='6.5' y1='7.5' x2='10.7' y2='16.4'/><line x1='17.5' y1='7.5' x2='13.3' y2='16.4'/><line x1='7' y1='6' x2='17' y2='6'/>";
            case "folder" -> "<path d='M22 19a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h5l2 3h9a2 2 0 0 1 2 2z'/>";
            case "search" -> "<circle cx='11' cy='11' r='8'/><line x1='21' y1='21' x2='16.65' y2='16.65'/>";
            case "chevron-right" -> "<polyline points='9 18 15 12 9 6'/>";
            case "chevron-down" -> "<polyline points='6 9 12 15 18 9'/>";
            case "warning" -> "<path d='M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0z'/><line x1='12' y1='9' x2='12' y2='13'/><line x1='12' y1='17' x2='12.01' y2='17'/>";
            case "info" -> "<circle cx='12' cy='12' r='10'/><line x1='12' y1='16' x2='12' y2='12'/><line x1='12' y1='8' x2='12.01' y2='8'/>";
            case "send" -> "<line x1='22' y1='2' x2='11' y2='13'/><polygon points='22 2 15 22 11 13 2 9 22 2'/>";
            case "terminal" -> "<polyline points='4 17 10 11 4 5'/><line x1='12' y1='19' x2='20' y2='19'/>";
            case "file" -> "<path d='M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z'/><polyline points='14 2 14 8 20 8'/>";
            case "diff" -> "<path d='M12 3v18'/><rect x='3' y='8' width='6' height='8' rx='1'/><rect x='15' y='8' width='6' height='8' rx='1'/>";
            case "clock" -> "<circle cx='12' cy='12' r='10'/><polyline points='12 6 12 12 16 14'/>";
            case "bolt" -> "<polygon points='13 2 3 14 12 14 11 22 21 10 12 10 13 2'/>";
            case "crown" -> "<path d='M2 18h20l-2-9-5 4-3-7-3 7-5-4z'/>";
            case "skull" -> "<circle cx='9' cy='12' r='1.5'/><circle cx='15' cy='12' r='1.5'/><path d='M8 20v-2a8 8 0 1 1 8 0v2z'/><path d='M12 16v2'/>";
            case "refresh" -> "<polyline points='23 4 23 10 17 10'/><path d='M20.49 15a9 9 0 1 1-2.12-9.36L23 10'/>";
            case "heart" -> "<path d='M20.84 4.61a5.5 5.5 0 0 0-7.78 0L12 5.67l-1.06-1.06a5.5 5.5 0 0 0-7.78 7.78l1.06 1.06L12 21.23l7.78-7.78 1.06-1.06a5.5 5.5 0 0 0 0-7.78z'/>";
            case "home" -> "<path d='M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z'/><polyline points='9 22 9 12 15 12 15 22'/>";
            case "list" -> "<line x1='8' y1='6' x2='21' y2='6'/><line x1='8' y1='12' x2='21' y2='12'/><line x1='8' y1='18' x2='21' y2='18'/><line x1='3' y1='6' x2='3.01' y2='6'/><line x1='3' y1='12' x2='3.01' y2='12'/><line x1='3' y1='18' x2='3.01' y2='18'/>";
            case "book" -> "<path d='M4 19.5A2.5 2.5 0 0 1 6.5 17H20'/><path d='M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z'/>";
            case "shield" -> "<path d='M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z'/>";
            case "chart" -> "<line x1='18' y1='20' x2='18' y2='10'/><line x1='12' y1='20' x2='12' y2='4'/><line x1='6' y1='20' x2='6' y2='14'/>";
            case "inbox" -> "<polyline points='22 12 16 12 14 15 10 15 8 12 2 12'/><path d='M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z'/>";
            case "dot" -> "<circle cx='12' cy='12' r='4' fill='currentColor' stroke='none'/>";
            case "grip" -> "<circle cx='9' cy='6' r='1' fill='currentColor'/><circle cx='15' cy='6' r='1' fill='currentColor'/><circle cx='9' cy='12' r='1' fill='currentColor'/><circle cx='15' cy='12' r='1' fill='currentColor'/><circle cx='9' cy='18' r='1' fill='currentColor'/><circle cx='15' cy='18' r='1' fill='currentColor'/>";
            default -> "<circle cx='12' cy='12' r='10'/><line x1='12' y1='8' x2='12' y2='16'/><line x1='8' y1='12' x2='16' y2='12'/>";
        };
    }
}

