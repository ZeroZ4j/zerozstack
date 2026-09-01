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
package com.zeroz4j.apt;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A client edit travels back up, through the classes the processor actually generates.
 *
 * <h2>What this protects</h2>
 *
 * <p>The browser does not hold the model class the developer wrote. It holds the generated
 * {@code <Model>_Live} subclass, whose setters report the edit and whose getters make the object a
 * reactive dependency. When the edit is sent, that subclass is what goes into the serializer — so
 * everything the write path decides about a model, it has to decide correctly about a class whose
 * name the developer never wrote and which is not the name the registry was keyed by.</p>
 *
 * <p>Nothing tested this before. The server-side tests stand in for the browser with plain model
 * instances, which is the one case that cannot go wrong.</p>
 *
 * <h2>What went wrong here, and what fixed it</h2>
 *
 * <p>This failed for the whole of the unreleased version. The write path looked a model's
 * serializer up by its runtime class name, and the runtime class in the browser is
 * {@code Profile_Live}, which the registry has no serializer for — only {@code Profile} does. The
 * write threw {@code Unsupported type for GrowableBuffer: live.up.Profile_Live}, the client caught
 * it, printed one line and dropped the edit, so <b>the whole up direction of LiveSync did nothing
 * at all</b>, silently, for every application.</p>
 *
 * <p>The fix is in the code generator: the generated registrar now names the live subclass as well
 * as the model, and the writer puts the model's own name on the wire. The receiving side has no
 * serializer for a subclass name and could not build one.</p>
 */
class LiveUpDirectionWireTest {

    private static final String PROFILE = "live.up.Profile";

    private static GeneratedWire liveModel(Path tempDir) throws Exception {
        return GeneratedWire.compileAndRegister(tempDir, GeneratedWire.sources(
                PROFILE,
                "package live.up;\n"
                + "import com.zeroz4j.api.ClientWritable;\n"
                + "import com.zeroz4j.api.DataModel;\n"
                + "import com.zeroz4j.api.LiveSync;\n"
                + "@DataModel @LiveSync @ClientWritable\n"
                + "public class Profile {\n"
                + "    private String mission;\n"
                + "    public Profile() { }\n"
                + "    public String getMission() { return mission; }\n"
                + "    public void setMission(String mission) { this.mission = mission; }\n"
                + "}\n"));
    }

    @Test
    @DisplayName("the browser's live instance goes back up and lands on the server's own object")
    void aClientEditReachesTheCanonicalObject(@TempDir Path tempDir) throws Exception {
        GeneratedWire wire = liveModel(tempDir);

        // The server sends its object to the browser.
        ObjectMapper server = new ObjectMapper();
        Object canonical = wire.make(PROFILE);
        canonical.getClass().getMethod("setMission", String.class).invoke(canonical, "as sent");
        GrowableBuffer down = new GrowableBuffer();
        BinarySerializer.writeValue(down, canonical, server);
        String handle = server.getId(canonical);
        assertTrue(handle != null, "a live model earns a handle that outlives its message");

        // The browser reads it. This is the one place a _Live subclass is built.
        ObjectMapper browser = new ObjectMapper();
        BinaryRegistry.setPreferLiveInstances(true);
        Object live;
        try {
            live = BinarySerializer.readValue(ByteBuffer.wrap(down.toByteArray()), browser);
        } finally {
            BinaryRegistry.setPreferLiveInstances(false);
        }
        assertTrue(live.getClass().getName().endsWith("_Live"),
                "the browser holds the generated subclass, not the model class: "
                        + live.getClass().getName());
        assertEquals(handle, browser.getId(live), "under the name the server gave it");

        // Somebody types. This is exactly what the client's mutation frame carries.
        live.getClass().getMethod("setMission", String.class).invoke(live, "edited");
        GrowableBuffer up = new GrowableBuffer();
        BinarySerializer.writeValue(up, live, browser);

        // The server applies it in place, into the object it already had.
        Object applied = BinarySerializer.readValue(ByteBuffer.wrap(up.toByteArray()), server);
        assertSame(canonical, applied, "applied to the server's own instance, not a copy");
        assertEquals("edited", canonical.getClass().getMethod("getMission").invoke(canonical));
    }
}
