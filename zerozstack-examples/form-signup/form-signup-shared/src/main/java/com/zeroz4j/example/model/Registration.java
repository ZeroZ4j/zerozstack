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
import com.zeroz4j.api.validation.Max;
import com.zeroz4j.api.validation.Min;
import com.zeroz4j.api.validation.NotBlank;
import com.zeroz4j.api.validation.Size;

import java.util.Objects;

@DataModel
public class Registration {
    private long id;

    @NotBlank
    @Size(min = 2, max = 60)
    private String fullName;

    @NotBlank
    @Size(min = 5, max = 120)
    private String email;

    @Min(0)
    @Max(50)
    private int experienceYears;

    @NotBlank
    private String tShirtSize;

    private boolean newsletter;

    @Size(max = 400)
    private String bio;

    public Registration() {
    }

    public Registration(long id, String fullName, String email, int experienceYears, String tShirtSize, boolean newsletter, String bio) {
        this.id = id;
        this.fullName = fullName;
        this.email = email;
        this.experienceYears = experienceYears;
        this.tShirtSize = tShirtSize;
        this.newsletter = newsletter;
        this.bio = bio;
    }

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public int getExperienceYears() {
        return experienceYears;
    }

    public void setExperienceYears(int experienceYears) {
        this.experienceYears = experienceYears;
    }

    public String getTShirtSize() {
        return tShirtSize;
    }

    public void setTShirtSize(String tShirtSize) {
        this.tShirtSize = tShirtSize;
    }

    public boolean isNewsletter() {
        return newsletter;
    }

    public void setNewsletter(boolean newsletter) {
        this.newsletter = newsletter;
    }

    public String getBio() {
        return bio;
    }

    public void setBio(String bio) {
        this.bio = bio;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Registration)) return false;
        Registration that = (Registration) o;
        return id == that.id &&
                experienceYears == that.experienceYears &&
                newsletter == that.newsletter &&
                Objects.equals(fullName, that.fullName) &&
                Objects.equals(email, that.email) &&
                Objects.equals(tShirtSize, that.tShirtSize) &&
                Objects.equals(bio, that.bio);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, fullName, email, experienceYears, tShirtSize, newsletter, bio);
    }
}
