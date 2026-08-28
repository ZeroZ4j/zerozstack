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
package com.zeroz4j.server;

import com.zeroz4j.api.BinaryRegistry;
import com.zeroz4j.api.BinarySerializer;
import com.zeroz4j.api.BinarySerializerDelegate;
import com.zeroz4j.api.ClientWritable;
import com.zeroz4j.api.GrowableBuffer;
import com.zeroz4j.api.ObjectMapper;
import com.zeroz4j.api.SyncFrameTypes;
import com.zeroz4j.api.validation.ValidationRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the server side of two-way LiveSync: the authorize/validate/apply
 * gates of {@link WasmRmiServerEngine#handleLiveMutation} and the writable shared
 * signal path of {@link ServerSignalTransport}, using the FakeSession test double.
 */
public class ServerLiveMutationTest {

    @ClientWritable
    public static class Profile {
        private String mission;
        public Profile() {}
        public Profile(String mission) { this.mission = mission; }
        public String getMission() { return mission; }
        public void setMission(String mission) { this.mission = mission; }
    }

    @ClientWritable("admin")
    public static class AdminDoc {
        private String text;
        public AdminDoc() {}
        public AdminDoc(String text) { this.text = text; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class PlainDoc { // no @ClientWritable
        private String text;
        public PlainDoc() {}
        public PlainDoc(String text) { this.text = text; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    @BeforeAll
    public static void registerModels() {
        BinaryRegistry.register(Profile.class.getName(), Profile::new,
                new BinarySerializerDelegate<Profile>() {
                    @Override public void write(Profile obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getMission() == null ? "" : obj.getMission());
                    }
                    @Override public void read(Profile obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setMission(BinarySerializer.readString(buffer));
                    }
                });
        BinaryRegistry.register(AdminDoc.class.getName(), AdminDoc::new,
                new BinarySerializerDelegate<AdminDoc>() {
                    @Override public void write(AdminDoc obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getText() == null ? "" : obj.getText());
                    }
                    @Override public void read(AdminDoc obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setText(BinarySerializer.readString(buffer));
                    }
                });
        BinaryRegistry.register(PlainDoc.class.getName(), PlainDoc::new,
                new BinarySerializerDelegate<PlainDoc>() {
                    @Override public void write(PlainDoc obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getText() == null ? "" : obj.getText());
                    }
                    @Override public void read(PlainDoc obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setText(BinarySerializer.readString(buffer));
                    }
                });
        // Field-constraint stand-in: rejects the mission value "bad".
        ValidationRegistry.register(Profile.class.getName(), obj -> {
            Profile p = (Profile) obj;
            return "bad".equals(p.getMission())
                    ? List.of("mission must not be 'bad'")
                    : List.of();
        });
    }

    private WasmRmiServerEngine engine;

    @BeforeEach
    public void setup() {
        engine = new WasmRmiServerEngine();
        engine.injectedRuntime = new ServerRuntime();
        engine.mapper = new ObjectMapper();
        engine.syncEngine = new SyncEngine();
        engine.syncEngine.mapper = engine.mapper;
        engine.syncEngine.runtime = engine.injectedRuntime;
    }

    private WasmRmiServerEngineTest.FakeSession fakeSession(String id, Set<String> roles) {
        WasmRmiServerEngineTest.FakeSession session = new WasmRmiServerEngineTest.FakeSession(id);
        session.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, roles);
        // Connections belong to a server since 0.8.0, and code holding only a connection finds its
        // server through it. Opening one for real does this; a hand-built one has to say so.
        engine.addActiveSessionForTesting(session);
        return session;
    }

    private static int frameCount(WasmRmiServerEngineTest.FakeSession session) {
        return session.basic.sentBuffers().size();
    }

    private static byte frameOpcode(WasmRmiServerEngineTest.FakeSession session, int index) {
        return session.basic.sentBuffers().get(index).get(4);
    }

    /**
     * Reads the reason string out of a 0x15 REJECT frame: [int32 0][0x15][String className][String reason].
     */
    private static String rejectionReason(WasmRmiServerEngineTest.FakeSession session, int index) {
        ByteBuffer frame = session.basic.sentBuffers().get(index).duplicate();
        frame.position(0);
        frame.getInt();                                   // correlation id
        assertEquals(SyncFrameTypes.REJECT, frame.get(), "expected a REJECT frame");
        BinarySerializer.readString(frame);                // class name
        return BinarySerializer.readString(frame);
    }

    /** Serializes a client-side copy under the canonical object's wire id. */
    private ByteBuffer craftMutation(Object clientCopy, String canonicalId) {
        ObjectMapper clientMapper = new ObjectMapper();
        clientMapper.registerWithId(canonicalId, clientCopy);
        GrowableBuffer buffer = new GrowableBuffer();
        BinarySerializer.writeValue(buffer, clientCopy, clientMapper);
        return ByteBuffer.wrap(buffer.toByteArray());
    }

    @Test
    public void testAcceptedMutationAppliesInPlaceAndBroadcasts() throws Exception {
        Profile canonical = new Profile("old");
        String id = engine.mapper.register(canonical);

        WasmRmiServerEngineTest.FakeSession writer = fakeSession("s1", Set.of());
        WasmRmiServerEngineTest.FakeSession other = fakeSession("s2", Set.of());
        engine.syncEngine.addSession(writer);
        engine.syncEngine.addSession(other);

        engine.handleLiveMutation(craftMutation(new Profile("updated"), id), writer);

        assertEquals("updated", canonical.getMission(), "Canonical instance mutated in place");
        assertEquals(1, frameCount(writer), "Writer receives the confirming broadcast");
        assertEquals(1, frameCount(other), "Other sessions receive the broadcast");
        assertEquals(0x10, frameOpcode(other, 0), "Sync update frame opcode");
    }

    @Test
    public void testNonWritableModelIsRejectedWithCorrective() throws Exception {
        PlainDoc canonical = new PlainDoc("original");
        String id = engine.mapper.register(canonical);

        WasmRmiServerEngineTest.FakeSession writer = fakeSession("s1", Set.of());
        WasmRmiServerEngineTest.FakeSession other = fakeSession("s2", Set.of());
        engine.syncEngine.addSession(writer);
        engine.syncEngine.addSession(other);

        engine.handleLiveMutation(craftMutation(new PlainDoc("hacked"), id), writer);

        assertEquals("original", canonical.getText(), "Canonical instance untouched");
        assertEquals(2, frameCount(writer), "Writer receives a corrective sync and a rejection reason");
        assertTrue(rejectionReason(writer, 1).contains("not @ClientWritable"),
                "the writer must be told why, not just snapped back: " + rejectionReason(writer, 1));
        assertEquals(0, frameCount(other), "Other sessions are not disturbed");
    }

    @Test
    public void testWriteRoleIsEnforced() throws Exception {
        AdminDoc canonical = new AdminDoc("original");
        String id = engine.mapper.register(canonical);

        WasmRmiServerEngineTest.FakeSession intruder = fakeSession("s1", Set.of("user"));
        engine.syncEngine.addSession(intruder);

        engine.handleLiveMutation(craftMutation(new AdminDoc("defaced"), id), intruder);
        assertEquals("original", canonical.getText(), "Role-gated mutation rejected");
        assertEquals(2, frameCount(intruder), "Intruder snapped back, and told why");
        assertTrue(rejectionReason(intruder, 1).contains("roles"),
                "a failed role check must report a reason: " + rejectionReason(intruder, 1));

        WasmRmiServerEngineTest.FakeSession admin = fakeSession("s2", Set.of("admin"));
        engine.syncEngine.addSession(admin);

        engine.handleLiveMutation(craftMutation(new AdminDoc("edited"), id), admin);
        assertEquals("edited", canonical.getText(), "Role-holding session may mutate");
    }

    @Test
    public void testValidationGateRejectsBeforeApply() throws Exception {
        Profile canonical = new Profile("valid");
        String id = engine.mapper.register(canonical);

        WasmRmiServerEngineTest.FakeSession writer = fakeSession("s1", Set.of());
        engine.syncEngine.addSession(writer);

        engine.handleLiveMutation(craftMutation(new Profile("bad"), id), writer);

        assertEquals("valid", canonical.getMission(),
                "Invalid state must never reach the canonical instance");
        assertEquals(2, frameCount(writer), "Writer receives a corrective sync and a rejection reason");
        assertTrue(rejectionReason(writer, 1).contains("Validation failed"),
                "the violation must reach the writer: " + rejectionReason(writer, 1));
    }

    @Test
    public void testWritableSharedSignalRoleGate() throws Exception {
        com.zeroz4j.signals.Signals.resetForTesting();
        ServerSignalTransport.install(engine.injectedRuntime, engine.mapper);
        com.zeroz4j.signals.Signals.sharedWritable("test.banner", "welcome", "admin");

        WasmRmiServerEngineTest.FakeSession intruder = fakeSession("s1", Set.of("user"));

        ServerSignalTransport.handleClientSet("test.banner", "defaced", intruder);
        assertEquals("welcome", com.zeroz4j.signals.Signals.lookup("test.banner").get(),
                "Role-gated signal write rejected");
        assertEquals(1, frameCount(intruder), "Writer receives the corrective value");
        assertEquals(0x17, frameOpcode(intruder, 0), "SIGNAL_UPD opcode");

        WasmRmiServerEngineTest.FakeSession admin = fakeSession("s2", Set.of("admin"));
        ServerSignalTransport.handleClientSet("test.banner", "maintenance at noon", admin);
        assertEquals("maintenance at noon", com.zeroz4j.signals.Signals.lookup("test.banner").get(),
                "Role-holding session may write");
        com.zeroz4j.signals.Signals.resetForTesting();
    }
}
