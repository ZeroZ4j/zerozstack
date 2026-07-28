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
package com.zeroz4j.example.model;

import com.zeroz4j.api.DataModel;

/**
 * Example Data Transfer Object representing user information.
 * Marked with @DataModel so the APT generates a serializer companion.
 */
@DataModel
public class UserInfo {
    private String name;
    private int score;
    private boolean active;

    // A default zero-argument constructor is required for BinaryRegistry instantiation
    public UserInfo() {
    }

    public UserInfo(String name, int score, boolean active) {
        this.name = name;
        this.score = score;
        this.active = active;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }



    @Override
    public String toString() {
        return "UserInfo{name='" + name + "', score=" + score + ", active=" + active + "}";
    }
}

