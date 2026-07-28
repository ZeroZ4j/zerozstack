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
package com.zeroz4j.example.client.showcase;

import com.zeroz4j.ui.component.Component;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.LinkedHashMap;

public class ShowcaseRegistry {

    private static final Map<String, Supplier<Component>> showcases = new HashMap<>();

    static {
        showcases.put("accordion", AccordionShowcase::new);
        showcases.put("alert", AlertShowcase::new);
        showcases.put("artboard", ArtboardShowcase::new);
        showcases.put("avatar", AvatarShowcase::new);
        showcases.put("badge", BadgeShowcase::new);
        showcases.put("btm-nav", BottomNavigationShowcase::new);
        showcases.put("breadcrumbs", BreadcrumbsShowcase::new);
        showcases.put("mockup-browser", BrowserMockupShowcase::new);
        showcases.put("btn", ButtonShowcase::new);
        showcases.put("card", CardShowcase::new);
        showcases.put("carousel", CarouselShowcase::new);
        showcases.put("chat-bubble", ChatBubbleShowcase::new);
        showcases.put("checkbox", CheckboxShowcase::new);
        showcases.put("mockup-code", CodeMockupShowcase::new);
        showcases.put("collapse", CollapseShowcase::new);
        showcases.put("countdown", CountdownShowcase::new);
        showcases.put("dialog", DialogShowcase::new);
        showcases.put("diff", DiffShowcase::new);
        showcases.put("divider", DividerShowcase::new);
        showcases.put("drawer", DrawerShowcase::new);
        showcases.put("dropdown", DropdownShowcase::new);
        showcases.put("file-input", FileInputShowcase::new);
        showcases.put("footer", FooterShowcase::new);
        showcases.put("hero", HeroShowcase::new);
        showcases.put("indicator", IndicatorShowcase::new);
        showcases.put("join", JoinShowcase::new);
        showcases.put("kbd", KbdShowcase::new);
        showcases.put("link", LinkShowcase::new);
        showcases.put("loading", LoadingShowcase::new);
        showcases.put("mask", MaskShowcase::new);
        showcases.put("navbar", NavbarShowcase::new);
        showcases.put("pagination", PaginationShowcase::new);
        showcases.put("mockup-phone", PhoneMockupShowcase::new);
        showcases.put("progress", ProgressShowcase::new);
        showcases.put("radial-progress", RadialProgressShowcase::new);
        showcases.put("radio", RadioButtonGroupShowcase::new);
        showcases.put("range", RangeShowcase::new);
        showcases.put("rating", RatingShowcase::new);
        showcases.put("select", SelectShowcase::new);
        showcases.put("skeleton", SkeletonShowcase::new);
        showcases.put("stack", StackShowcase::new);
        showcases.put("stat", StatShowcase::new);
        showcases.put("steps", StepsShowcase::new);
        showcases.put("swap", SwapShowcase::new);
        showcases.put("tab", TabShowcase::new);
        showcases.put("table", TableShowcase::new);
        showcases.put("textarea", TextAreaShowcase::new);
        showcases.put("input", TextFieldShowcase::new);
        showcases.put("theme-controller", ThemeControllerShowcase::new);
        showcases.put("timeline", TimelineShowcase::new);
        showcases.put("toast", ToastShowcase::new);
        showcases.put("toggle", ToggleShowcase::new);
        showcases.put("tooltip", TooltipShowcase::new);
        showcases.put("mockup-window", WindowMockupShowcase::new);
    }

    public static Component createShowcase(String componentId) {
        Supplier<Component> supplier = showcases.get(componentId);
        if (supplier != null) {
            return supplier.get();
        }
        return null;
    }
    
    public static Map<String, String> getComponentLabels() {
        Map<String, String> labels = new LinkedHashMap<>();
        labels.put("accordion", "Accordion");
        labels.put("alert", "Alert");
        labels.put("artboard", "Artboard");
        labels.put("avatar", "Avatar");
        labels.put("badge", "Badge");
        labels.put("btm-nav", "Bottom Navigation");
        labels.put("breadcrumbs", "Breadcrumbs");
        labels.put("mockup-browser", "Browser Mockup");
        labels.put("btn", "Button");
        labels.put("card", "Card");
        labels.put("carousel", "Carousel");
        labels.put("chat-bubble", "Chat Bubble");
        labels.put("checkbox", "Checkbox");
        labels.put("mockup-code", "Code Mockup");
        labels.put("collapse", "Collapse");
        labels.put("countdown", "Countdown");
        labels.put("dialog", "Dialog");
        labels.put("diff", "Diff");
        labels.put("divider", "Divider");
        labels.put("drawer", "Drawer");
        labels.put("dropdown", "Dropdown");
        labels.put("file-input", "File Input");
        labels.put("footer", "Footer");
        labels.put("hero", "Hero");
        labels.put("indicator", "Indicator");
        labels.put("join", "Join");
        labels.put("kbd", "Kbd");
        labels.put("link", "Link");
        labels.put("loading", "Loading");
        labels.put("mask", "Mask");
        labels.put("navbar", "Navbar");
        labels.put("pagination", "Pagination");
        labels.put("mockup-phone", "Phone Mockup");
        labels.put("progress", "Progress");
        labels.put("radial-progress", "Radial Progress");
        labels.put("radio", "Radio Button Group");
        labels.put("range", "Range");
        labels.put("rating", "Rating");
        labels.put("select", "Select");
        labels.put("skeleton", "Skeleton");
        labels.put("stack", "Stack");
        labels.put("stat", "Stat");
        labels.put("steps", "Steps");
        labels.put("swap", "Swap");
        labels.put("tab", "Tab");
        labels.put("table", "Table");
        labels.put("textarea", "Text Area");
        labels.put("input", "Text Field");
        labels.put("theme-controller", "Theme Controller");
        labels.put("timeline", "Timeline");
        labels.put("toast", "Toast");
        labels.put("toggle", "Toggle");
        labels.put("tooltip", "Tooltip");
        labels.put("mockup-window", "Window Mockup");
        return labels;
    }
}
