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

import com.zeroz4j.api.ClientWritable;
import com.zeroz4j.api.i18n.FrameworkText;
import com.zeroz4j.api.i18n.Message;
import com.zeroz4j.api.ObjectMapper;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Checks every server object a client-proposed change reaches, not only the outermost one.
 *
 * <p>A change frame is one object graph. Decoding it looks each handle up in the server's registry
 * and writes the incoming fields straight into the object it finds. Checking only the outermost
 * object therefore checked almost nothing: a change to something the client may write could carry,
 * nested inside it, the handle of something it may not, and the nested object was overwritten and
 * then broadcast to everyone. Handles are learnable, because every object goes on the wire with its
 * own handle attached to it.</p>
 *
 * <p>The rule this enforces is the same one that governs the outermost object, applied to all of
 * them: <b>every server object the change reaches must itself be marked writable by clients, and the
 * connection must hold one of the roles that mark demands.</b> One failure refuses the whole change;
 * nothing is applied and nothing is broadcast.</p>
 *
 * <p>The outermost handle is skipped here and left to the caller, which has the decoded object and
 * can therefore say precisely why it was refused — not writable, wrong role, or failed validation.
 * The first handle a change frame resolves is by construction its outermost object.</p>
 *
 * <p>Authorization is decided on the class of the object <b>the server holds</b>, never on the class
 * name in the frame. The frame's class name is the client's claim, and a client that could name a
 * writable class while handing over a restricted object's handle would walk straight through the
 * check.</p>
 *
 * <h2>The second half: models with no handle</h2>
 *
 * <p>Since 0.8.0 only a {@code @LiveSync} model and the objects inside one carry a lasting handle;
 * everything else on the wire is a value with a name good for one message. A handle check alone
 * would therefore no longer see a model the frame invented on the spot — and that model is still
 * written into the server's graph, replacing whatever was in that field. So the same rule is applied
 * a second way, through {@link ObjectMapper.ModelGuard}: <b>every model class a change reaches,
 * other than the outermost, must itself be marked writable by clients and pass its own role
 * check</b>, whether it arrived with a handle or without one. This also closes a hole that was
 * already there: before 0.8.0, a client could smuggle a fresh instance of a restricted model into a
 * writable one simply by not reusing the restricted object's handle.</p>
 *
 * <p>A {@code record} is exempt, because a record is a value rather than an object that is edited:
 * it has no setters, never changes, and replacing one is the same kind of act as replacing a
 * number.</p>
 */
final class LiveMutationGuard implements ObjectMapper.ResolutionGuard, ObjectMapper.ModelGuard {

    /** Refusal that aborts a decode in progress. Never reaches a client as-is. */
    static final class Denied extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final transient Message reason;

        Denied(Message reason) {
            super(ServerMessages.inEnglish(reason));
            this.reason = reason;
        }

        /** @return what to tell the writer, still unrendered so it can be said in their language */
        Message reason() {
            return reason;
        }
    }

    private final ObjectMapper canonicalMapper;
    private final Set<String> sessionRoles;

    private String rootHandleId;
    private boolean checking;

    /**
     * @param canonicalMapper the server's own registry, in which a handle either names a real object
     *                        or names nothing
     * @param sessionRoles    the roles the writing connection holds
     */
    LiveMutationGuard(ObjectMapper canonicalMapper, Set<String> sessionRoles) {
        this.canonicalMapper = canonicalMapper;
        this.sessionRoles = sessionRoles == null ? Collections.<String>emptySet() : sessionRoles;
    }

    /** @return the outermost handle in the frame, or null before decoding has started */
    String rootHandleId() {
        return rootHandleId;
    }

    @Override
    public void checkResolve(String handleId) {
        if (handleId == null || checking) {
            // Re-entry: the lookup below goes through the same guarded method.
            return;
        }
        if (rootHandleId == null) {
            rootHandleId = handleId;
            return;
        }
        if (handleId.equals(rootHandleId)) {
            return;
        }

        Object canonical;
        checking = true;
        try {
            canonical = canonicalMapper.getObject(handleId);
        } finally {
            checking = false;
        }
        if (canonical == null) {
            // No server object behind this handle, so nothing can be overwritten through it. The
            // decoder builds a fresh instance, which is how a genuinely new nested object arrives.
            return;
        }

        Class<?> type = canonical.getClass();
        ClientWritable writable = type.getAnnotation(ClientWritable.class);
        if (writable == null) {
            throw new Denied(FrameworkText.liveNestedNotWritable(type.getSimpleName()));
        }
        if (writable.value().length > 0 && !holdsAnyRole(writable.value())) {
            throw new Denied(FrameworkText.liveNestedRequiresRole(
                    type.getSimpleName(), Arrays.toString(writable.value())));
        }
    }

    /**
     * Checks a model the decode has just built or is about to write into, handle or no handle.
     *
     * @param model the instance
     * @param depth how deeply nested it is; 1 is the change's outermost object
     * @throws Denied if this connection may not write a model of that class
     */
    @Override
    public void checkModel(Object model, int depth) {
        if (model == null || depth <= 1) {
            // The outermost object is the caller's to judge: it has the decoded value and can say
            // precisely why it was refused - not writable, wrong role, or failed validation.
            return;
        }
        Class<?> type = model.getClass();
        if (type.isRecord()) {
            // A record never changes and has no identity to protect; it travels as a value.
            return;
        }
        ClientWritable writable = type.getAnnotation(ClientWritable.class);
        if (writable == null) {
            throw new Denied(FrameworkText.liveNestedNotWritable(type.getSimpleName()));
        }
        if (writable.value().length > 0 && !holdsAnyRole(writable.value())) {
            throw new Denied(FrameworkText.liveNestedRequiresRole(
                    type.getSimpleName(), Arrays.toString(writable.value())));
        }
    }

    private boolean holdsAnyRole(String[] required) {
        for (String role : required) {
            if (sessionRoles.contains(role)) {
                return true;
            }
        }
        return false;
    }
}
