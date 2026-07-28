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

public class AvatarShowcase extends ComponentShowcase {

    public AvatarShowcase() {
        addTitle("Avatar");
        addDescription("Avatars are used to represent users or profiles via images, icons, or text initials.");

        // Initials Avatar
        Avatar initialAvatar = new Avatar();
        Div initialWrapper = new Div("JD");
        initialWrapper.addClassName("w-16 h-16 rounded-full bg-neutral text-neutral-content flex items-center justify-center font-bold text-lg");
        initialAvatar.add(initialWrapper);

        class Img extends Component implements HasStyle {
            public Img(String src) {
                super("img");
                getElement().setAttribute("src", src);
            }
            @Override
            public Component getComponent() {
                return this;
            }
        }

        // Circular Image Avatar (using a placeholder image)
        Avatar circleAvatar = new Avatar();
        Div circleWrapper = new Div();
        circleWrapper.addClassName("w-16 h-16 rounded-full overflow-hidden border-2 border-primary");
        Img circleImg = new Img("https://img.daisyui.com/images/stock/photo-1534528741775-53994a69daeb.webp");
        circleImg.addClassName("w-full h-full object-cover");
        circleWrapper.add(circleImg);
        circleAvatar.add(circleWrapper);

        // Rounded Avatar
        Avatar roundedAvatar = new Avatar();
        Div roundedWrapper = new Div();
        roundedWrapper.addClassName("w-16 h-16 rounded-2xl overflow-hidden");
        Img roundedImg = new Img("https://img.daisyui.com/images/stock/photo-1534528741775-53994a69daeb.webp");
        roundedImg.addClassName("w-full h-full object-cover");
        roundedWrapper.add(roundedImg);
        roundedAvatar.add(roundedWrapper);

        // Custom Size Avatar
        Avatar largeAvatar = new Avatar();
        Div largeWrapper = new Div("XX");
        largeWrapper.addClassName("w-24 h-24 rounded-full bg-accent text-accent-content flex items-center justify-center font-bold text-2xl");
        largeAvatar.add(largeWrapper);

        addSection("Avatar Variants", initialAvatar, circleAvatar, roundedAvatar, largeAvatar);
    }
}
