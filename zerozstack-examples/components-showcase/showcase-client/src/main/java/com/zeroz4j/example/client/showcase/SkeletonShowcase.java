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

import com.zeroz4j.ui.component.*;
import com.zeroz4j.ui.layout.*;
import com.zeroz4j.ui.theme.*;
import com.zeroz4j.signals.*;

public class SkeletonShowcase extends ComponentShowcase {

    public SkeletonShowcase() {
        addTitle("Skeleton");
        addDescription("Skeleton is used to show a placeholder while content is loading.");

        // Basic Skeleton shapes
        Skeleton circle = new Skeleton();
        circle.setWidth("3rem");
        circle.setHeight("3rem");
        circle.addClassName("rounded-full");

        Skeleton line1 = new Skeleton();
        line1.setWidth("12rem");
        line1.setHeight("1rem");

        Skeleton line2 = new Skeleton();
        line2.setWidth("8rem");
        line2.setHeight("1rem");

        VerticalLayout lines = new VerticalLayout(line1, line2);
        lines.addClassName("gap-2");

        HorizontalLayout avatarMock = new HorizontalLayout(circle, lines);
        avatarMock.addClassName("items-center");
        avatarMock.addClassName("gap-4");

        addSection("Avatar Loading Mock", avatarMock);

        Skeleton rect = new Skeleton();
        rect.setWidth("100%");
        rect.setHeight("10rem");

        addSection("Rectangle Placeholder", rect);
    }
}
