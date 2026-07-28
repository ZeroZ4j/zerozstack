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
package com.zeroz4j.example.client;

import com.zeroz4j.ui.binding.Binder;
import com.zeroz4j.ui.component.Button;
import com.zeroz4j.ui.component.TextField;
import com.zeroz4j.ui.layout.VerticalLayout;


public class ProfileView extends VerticalLayout {

    public static class UserProfile {
        private String username;
        private String email;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getEmail() { return email; }
        public void setEmail(String email) { this.email = email; }
    }

    public ProfileView() {
        TextField usernameField = new TextField("Username");
        TextField emailField = new TextField("Email");

        Button saveButton = new Button("Save");
        saveButton.addClassName("btn-primary");

        add(usernameField, emailField, saveButton);

        Binder<UserProfile> binder = new Binder<>();

        binder.forField(usernameField)
              .asRequired("Username is required")
              .withValidator(name -> name.length() >= 3, "Must be at least 3 characters")
              .bind(UserProfile::getUsername, UserProfile::setUsername);

        binder.forField(emailField)
              .asRequired("Email is required")
              .withValidator(email -> email.contains("@"), "Must be a valid email")
              .bind(UserProfile::getEmail, UserProfile::setEmail);

        UserProfile profile = new UserProfile();
        profile.setUsername("ZeroZ");
        binder.setBean(profile);

        saveButton.addClickListener(event -> {
            if (binder.writeBeanIfValid(profile)) {
                saveButton.setText("Saved!");
            } else {
                saveButton.setText("Error");
            }
        });
    }
}
