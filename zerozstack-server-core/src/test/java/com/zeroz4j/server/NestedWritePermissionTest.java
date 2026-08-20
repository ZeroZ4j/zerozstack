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
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a client-proposed change is allowed to reach.
 *
 * <h2>What this protects</h2>
 * A change frame is one object graph, and applying it writes the incoming fields into whichever
 * server object each handle in that graph names. Authorization used to look at the outermost object
 * only. So a change to something the client may write could carry, nested inside it, the handle of
 * something it may not — a model not marked writable at all, or one gated behind a role the
 * connection does not hold. The nested object was overwritten and then broadcast to every session.
 *
 * <p>The handles are not a secret: every object goes on the wire with its own handle attached, so
 * any client that has ever been sent an object knows the handles of everything inside it.
 *
 * <p>The rule now enforced is the outermost object's rule applied to all of them: every server
 * object a change reaches must itself be writable by clients and pass its own role check. These
 * tests pin both halves — the refusal, and that ordinary nested editing still works.
 */
public class NestedWritePermissionTest {

    @ClientWritable
    public static class Team {
        private String name;
        private Secret secret;
        private AdminNote note;
        private Member member;
        public Team() { }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Secret getSecret() { return secret; }
        public void setSecret(Secret secret) { this.secret = secret; }
        public AdminNote getNote() { return note; }
        public void setNote(AdminNote note) { this.note = note; }
        public Member getMember() { return member; }
        public void setMember(Member member) { this.member = member; }
    }

    /** Not writable by clients at all. */
    public static class Secret {
        private String text;
        public Secret() { }
        public Secret(String text) { this.text = text; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    /** Writable, but only by an administrator. */
    @ClientWritable("admin")
    public static class AdminNote {
        private String text;
        public AdminNote() { }
        public AdminNote(String text) { this.text = text; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    /** Writable by anybody: the legitimate nested case. */
    @ClientWritable
    public static class Member {
        private String nickname;
        public Member() { }
        public Member(String nickname) { this.nickname = nickname; }
        public String getNickname() { return nickname; }
        public void setNickname(String nickname) { this.nickname = nickname; }
    }

    @BeforeAll
    public static void registerModels() {
        BinaryRegistry.register(Team.class.getName(), Team::new,
                new BinarySerializerDelegate<Team>() {
                    @Override public void write(Team obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getName() == null ? "" : obj.getName());
                        BinarySerializer.writeValue(buffer, obj.getSecret(), mapper);
                        BinarySerializer.writeValue(buffer, obj.getNote(), mapper);
                        BinarySerializer.writeValue(buffer, obj.getMember(), mapper);
                    }
                    @Override public void read(Team obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setName(BinarySerializer.readString(buffer));
                        obj.setSecret((Secret) BinarySerializer.readValue(buffer, mapper));
                        obj.setNote((AdminNote) BinarySerializer.readValue(buffer, mapper));
                        obj.setMember((Member) BinarySerializer.readValue(buffer, mapper));
                    }
                });
        BinaryRegistry.register(Secret.class.getName(), Secret::new,
                new BinarySerializerDelegate<Secret>() {
                    @Override public void write(Secret obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getText() == null ? "" : obj.getText());
                    }
                    @Override public void read(Secret obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setText(BinarySerializer.readString(buffer));
                    }
                });
        BinaryRegistry.register(AdminNote.class.getName(), AdminNote::new,
                new BinarySerializerDelegate<AdminNote>() {
                    @Override public void write(AdminNote obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getText() == null ? "" : obj.getText());
                    }
                    @Override public void read(AdminNote obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setText(BinarySerializer.readString(buffer));
                    }
                });
        BinaryRegistry.register(Member.class.getName(), Member::new,
                new BinarySerializerDelegate<Member>() {
                    @Override public void write(Member obj, GrowableBuffer buffer, ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, obj.getNickname() == null ? "" : obj.getNickname());
                    }
                    @Override public void read(Member obj, ByteBuffer buffer, ObjectMapper mapper) {
                        obj.setNickname(BinarySerializer.readString(buffer));
                    }
                });
    }

    private WasmRmiServerEngine engine;
    private WasmRmiServerEngineTest.FakeSession writer;
    private WasmRmiServerEngineTest.FakeSession bystander;

    @BeforeEach
    public void setup() {
        engine = new WasmRmiServerEngine();
        engine.mapper = new ObjectMapper();
        engine.syncEngine = new SyncEngine();
        engine.syncEngine.mapper = engine.mapper;
        Disclosures.resetForTesting();
    }

    private WasmRmiServerEngineTest.FakeSession session(String id, Set<String> roles) {
        WasmRmiServerEngineTest.FakeSession s = new WasmRmiServerEngineTest.FakeSession(id);
        s.getUserProperties().put(RmiEndpointConfigurator.ROLES_KEY, roles);
        return s;
    }

    private void connect(Set<String> writerRoles) {
        writer = session("writer", writerRoles);
        bystander = session("bystander", Set.of());
        engine.syncEngine.addSession(writer);
        engine.syncEngine.addSession(bystander);
    }

    /**
     * Builds the frame a client would send: the client's own copy of the whole graph, written under
     * the handles the server itself minted. That is what makes the attack possible in the first
     * place — the client knows the nested handles because it was sent them.
     */
    private ByteBuffer craft(Team clientCopy, String teamId, Object nested, String nestedId) {
        ObjectMapper clientMapper = new ObjectMapper();
        clientMapper.registerWithId(teamId, clientCopy);
        if (nested != null && nestedId != null) {
            clientMapper.registerWithId(nestedId, nested);
        }
        GrowableBuffer buffer = new GrowableBuffer();
        BinarySerializer.writeValue(buffer, clientCopy, clientMapper);
        return ByteBuffer.wrap(buffer.toByteArray());
    }

    private static String rejectionReason(WasmRmiServerEngineTest.FakeSession session, int index) {
        ByteBuffer frame = session.basic.sentBuffers().get(index).duplicate();
        frame.position(0);
        frame.getInt();
        assertEquals(SyncFrameTypes.REJECT, frame.get(), "expected a REJECT frame");
        BinarySerializer.readString(frame);
        return BinarySerializer.readString(frame);
    }

    @Test
    @DisplayName("a nested object nobody may write refuses the whole change")
    public void nestedNonWritableObjectIsRefused() {
        Team canonicalTeam = new Team();
        canonicalTeam.setName("Platform");
        Secret canonicalSecret = new Secret("original");
        canonicalTeam.setSecret(canonicalSecret);

        String teamId = engine.mapper.register(canonicalTeam);
        String secretId = engine.mapper.register(canonicalSecret);
        connect(Set.of());

        Team forged = new Team();
        forged.setName("Platform");
        forged.setSecret(new Secret("stolen"));

        engine.handleLiveMutation(craft(forged, teamId, forged.getSecret(), secretId), writer);

        assertEquals("original", canonicalSecret.getText(),
                "the object the client may not write must be exactly as it was");
        assertEquals("Platform", canonicalTeam.getName(),
                "and the whole change is refused, not partly applied");
        assertEquals(0, bystander.basic.sentBuffers().size(),
                "nothing may be broadcast: the point of the attack was to have the server "
                        + "re-publish the smuggled state to everybody");
        assertEquals(2, writer.basic.sentBuffers().size(),
                "the writer is snapped back to server truth and told why");
        assertTrue(rejectionReason(writer, 1).contains("Secret"),
                "the reason must name what was refused: " + rejectionReason(writer, 1));
        assertTrue(rejectionReason(writer, 1).contains("may not write"),
                "and say what the problem is: " + rejectionReason(writer, 1));
    }

    @Test
    @DisplayName("a nested object gated by a role the session lacks refuses the whole change")
    public void nestedRoleGatedObjectIsRefused() {
        Team canonicalTeam = new Team();
        canonicalTeam.setName("Platform");
        AdminNote canonicalNote = new AdminNote("original");
        canonicalTeam.setNote(canonicalNote);

        String teamId = engine.mapper.register(canonicalTeam);
        String noteId = engine.mapper.register(canonicalNote);
        connect(Set.of("user"));

        Team forged = new Team();
        forged.setName("Platform");
        forged.setNote(new AdminNote("defaced"));

        engine.handleLiveMutation(craft(forged, teamId, forged.getNote(), noteId), writer);

        assertEquals("original", canonicalNote.getText(), "role-gated nested object untouched");
        assertEquals(0, bystander.basic.sentBuffers().size(), "nothing broadcast");
        assertEquals(2, writer.basic.sentBuffers().size(), "snapped back and told why");
        assertTrue(rejectionReason(writer, 1).contains("admin"),
                "the reason must name the role required: " + rejectionReason(writer, 1));
    }

    @Test
    @DisplayName("the same nested object goes through for a session that does hold the role")
    public void nestedRoleGatedObjectIsAllowedForTheRoleHolder() {
        Team canonicalTeam = new Team();
        canonicalTeam.setName("Platform");
        AdminNote canonicalNote = new AdminNote("original");
        canonicalTeam.setNote(canonicalNote);

        String teamId = engine.mapper.register(canonicalTeam);
        String noteId = engine.mapper.register(canonicalNote);
        connect(Set.of("admin"));

        Team edit = new Team();
        edit.setName("Platform");
        edit.setNote(new AdminNote("reviewed"));

        engine.handleLiveMutation(craft(edit, teamId, edit.getNote(), noteId), writer);

        assertEquals("reviewed", canonicalNote.getText(), "an administrator may edit it");
        assertEquals(1, bystander.basic.sentBuffers().size(), "and everyone is told");
    }

    @Test
    @DisplayName("ordinary nested editing of writable objects still works")
    public void legitimateNestedMutationStillApplies() {
        Team canonicalTeam = new Team();
        canonicalTeam.setName("Platform");
        Member canonicalMember = new Member("old");
        canonicalTeam.setMember(canonicalMember);

        String teamId = engine.mapper.register(canonicalTeam);
        String memberId = engine.mapper.register(canonicalMember);
        connect(Set.of());

        Team edit = new Team();
        edit.setName("Platform Team");
        edit.setMember(new Member("new"));

        engine.handleLiveMutation(craft(edit, teamId, edit.getMember(), memberId), writer);

        assertEquals("Platform Team", canonicalTeam.getName(), "the outermost object is updated");
        assertEquals("new", canonicalMember.getNickname(),
                "and so is the nested one, in place — this is the behaviour the guard must not break");
        assertEquals(1, bystander.basic.sentBuffers().size(), "the change is broadcast once");
    }

    @Test
    @DisplayName("a brand-new nested object is still accepted")
    public void aFreshNestedObjectIsStillAccepted() {
        Team canonicalTeam = new Team();
        canonicalTeam.setName("Platform");
        String teamId = engine.mapper.register(canonicalTeam);
        connect(Set.of());

        Team edit = new Team();
        edit.setName("Platform");
        edit.setMember(new Member("newcomer"));

        // No handle registered for the member: it is a value the client just invented, and there is
        // no server object behind it that could be overwritten.
        engine.handleLiveMutation(craft(edit, teamId, null, null), writer);

        assertEquals("newcomer", canonicalTeam.getMember().getNickname(),
                "adding a nested object the server has never seen is a normal edit");
    }

    @Test
    @DisplayName("naming a writable class over a restricted object's handle is refused")
    public void aMismatchedClassNameIsRefused() {
        Secret canonicalSecret = new Secret("original");
        String secretId = engine.mapper.register(canonicalSecret);
        connect(Set.of());

        // The frame claims to be a Team - which clients may write - but carries the handle of a
        // Secret, which they may not. Authorization reads the class the SERVER holds, so the claim
        // buys nothing.
        Team forged = new Team();
        forged.setName("owned");

        engine.handleLiveMutation(craft(forged, secretId, null, null), writer);

        assertEquals("original", canonicalSecret.getText(), "the restricted object is untouched");
        assertEquals(0, bystander.basic.sentBuffers().size(), "nothing broadcast");
        assertEquals(2, writer.basic.sentBuffers().size(), "snapped back and told why");
    }

    @Test
    @DisplayName("a bare reference to a restricted object cannot be spliced into a writable one")
    public void aReferenceToARestrictedObjectIsRefused() {
        Team canonicalTeam = new Team();
        canonicalTeam.setName("Platform");
        Secret canonicalSecret = new Secret("original");
        String teamId = engine.mapper.register(canonicalTeam);
        String secretId = engine.mapper.register(canonicalSecret);
        connect(Set.of());

        // Hand-built frame: the team's secret field is a 0x0E back-reference to the restricted
        // object rather than a copy of it. Accepting it would hang the restricted object off a
        // writable one, and the broadcast that follows would ship its contents to every session.
        GrowableBuffer buffer = new GrowableBuffer();
        buffer.put(BinarySerializer.TAG_OBJECT);
        BinarySerializer.writeString(buffer, teamId);
        BinarySerializer.writeString(buffer, Team.class.getName());
        BinarySerializer.writeString(buffer, "Platform");
        buffer.put(BinarySerializer.TAG_REF);
        BinarySerializer.writeString(buffer, secretId);
        buffer.put(BinarySerializer.TAG_NULL);
        buffer.put(BinarySerializer.TAG_NULL);

        engine.handleLiveMutation(ByteBuffer.wrap(buffer.toByteArray()), writer);

        assertNull(canonicalTeam.getSecret(),
                "the restricted object must not be attached to a writable one");
        assertEquals(0, bystander.basic.sentBuffers().size(), "and nothing is broadcast");
    }
}
