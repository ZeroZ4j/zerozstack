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
package com.zeroz4j.api.i18n;

/**
 * The names of the framework's own refusals.
 *
 * <p>A test that used to assert on the English sentence a refusal produced should assert on the
 * name instead — the sentence is now a translation and can differ per caller, while the name never
 * changes. {@code Refusals.assertRefusedWith(FrameworkKeys.ACCESS_DENIED, thrown)} in
 * {@code zerozstack-server-test} does exactly that.</p>
 *
 * <p>The English behind each one is in {@code i18n/zeroz4j.properties} and compiled into
 * {@link FrameworkText}, and it is unchanged from before language support existed.</p>
 *
 * @since 0.9.0
 */
public final class FrameworkKeys {

    /** A failure the application did not plan for: one sentence and a code to quote to support. */
    public static final String UNEXPECTED_FAILURE = "error.unexpected";

    /** A call naming a service this server does not publish. */
    public static final String UNKNOWN_SERVICE = "error.unknownService";

    /** A call naming a method the service does not have. */
    public static final String UNKNOWN_METHOD = "error.unknownMethod";

    /** A call to a {@code @Secured} method from a connection that never signed in. */
    public static final String AUTHENTICATION_REQUIRED = "error.authenticationRequired";

    /** A call to a {@code @RolesAllowed} method by somebody without one of those roles. */
    public static final String ACCESS_DENIED = "error.accessDenied";

    /** An argument that broke the rules declared on its own model. */
    public static final String VALIDATION_FAILED = "error.validationFailed";

    /** A request to open a lazy reference this connection was never given. */
    public static final String UNKNOWN_LAZY_HANDLE = "error.unknownLazyHandle";

    /** A live change whose payload names a different class than the object it edits. */
    public static final String LIVE_WRONG_TYPE = "live.wrongType";

    /** A live change to a model that never said clients may edit it. */
    public static final String LIVE_NOT_CLIENT_WRITABLE = "live.notClientWritable";

    /** A live change that broke the rules declared on the model. */
    public static final String LIVE_VALIDATION_FAILED = "live.validationFailed";

    /** A live change by somebody without one of the roles the model requires. */
    public static final String LIVE_REQUIRES_ROLE = "live.requiresRole";

    /** A live change that also reaches a nested model clients may not write. */
    public static final String LIVE_NESTED_NOT_WRITABLE = "live.nestedNotWritable";

    /** A live change that also reaches a nested model needing a role the writer has not got. */
    public static final String LIVE_NESTED_REQUIRES_ROLE = "live.nestedRequiresRole";

    private FrameworkKeys() {
    }
}
