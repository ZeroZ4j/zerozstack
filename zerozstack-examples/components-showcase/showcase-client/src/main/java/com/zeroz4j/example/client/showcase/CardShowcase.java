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

public class CardShowcase extends ComponentShowcase {

    public CardShowcase() {
        addTitle("Card");
        addDescription("Cards are flexible content containers with options for titles, bodies, and action groups.");

        Card card = new Card();
        card.addClassName("w-96 bg-base-100 border border-base-300");

        CardTitle title = new CardTitle("Card Title");
        Div bodyText = new Div("If a dog chews shoes whose shoes does he choose?");
        
        CardActions actions = new CardActions();
        Button buyNow = new Button("Buy Now");
        buyNow.addClassName("btn-primary");
        actions.add(buyNow);

        card.add(title, bodyText, actions);

        addSection("Card Example", card);
    }
}
