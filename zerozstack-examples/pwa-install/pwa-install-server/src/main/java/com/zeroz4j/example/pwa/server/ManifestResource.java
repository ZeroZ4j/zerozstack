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
package com.zeroz4j.example.pwa.server;

import com.zeroz4j.server.PwaManifest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;

/**
 * Serves the manifest, built per request.
 *
 * <p>A single-identity application would drop a static {@code manifest.webmanifest} into
 * {@code src/main/webapp} and be done — the framework already serves it with the right content type.
 * This endpoint exists to show the case a static file cannot cover: the {@code ?brand=} parameter
 * stands in for whatever a multi-tenant product would look up, and changes the installed
 * application's name and colour.</p>
 *
 * <p>A literal path outranks the framework's {@code {path: .*}} catch-all, so this wins without any
 * configuration.</p>
 */
@Path("/manifest.webmanifest")
public class ManifestResource {

    /**
     * @param brand which identity to build the manifest for; {@code "sunset"} is the alternative
     * @return the manifest JSON
     */
    @GET
    @Produces("application/manifest+json")
    public String manifest(@QueryParam("brand") String brand) {
        boolean sunset = "sunset".equals(brand);
        return PwaManifest.named(
                        sunset ? "zeroz4j PWA (Sunset)" : "zeroz4j PWA Install",
                        sunset ? "Sunset" : "zeroz4j PWA")
                .description("Shows what installing a zeroz4j application does, and what it does not.")
                .themeColor(sunset ? "#7c2d12" : "#1f2937")
                .backgroundColor(sunset ? "#7c2d12" : "#1f2937")
                .icon("/icons/icon-192.png", 192)
                .icon("/icons/icon-512.png", 512)
                // Without a maskable icon Android puts the square in a white circle instead of
                // filling the shape it wants. This one keeps the Z inside the middle 80%.
                .icon("/icons/icon-512.png", "512x512", "image/png", "maskable")
                .toJson();
    }
}
