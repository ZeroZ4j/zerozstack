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
package com.zeroz4j.example.signin.client;

import com.zeroz4j.client.SignInSession;
import com.zeroz4j.example.signin.shared.AccountService;
import com.zeroz4j.example.signin.shared.AccountService_Stub;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.layout.HorizontalLayout;
import com.zeroz4j.ui.layout.VerticalLayout;
import com.zeroz4j.ui.theme.TextStyle;
import org.teavm.jso.browser.Window;

/** What a signed-in person sees. */
class HomePage extends VerticalLayout {

    private final AccountService account = new AccountService_Stub();

    HomePage() {
        addClassName("p-6");
        addClassName("gap-3");
        add(TextStyle.PAGE_TITLE.paragraph("Account"));
        add(TextStyle.BODY.paragraph(account.whoAmI()));
        add(TextStyle.SECONDARY.paragraph(Window.current().getLocation().getPathName()));

        Button signOut = new Button("Sign out", event -> SignInSession.signOut());
        signOut.addClassName("btn-primary");
        Button everywhere = new Button("Sign out everywhere", event -> account.signOutEverywhere());
        add(new HorizontalLayout(signOut, everywhere));
    }
}
