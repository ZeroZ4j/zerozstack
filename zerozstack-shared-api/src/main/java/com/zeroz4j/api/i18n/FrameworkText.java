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
 * The framework's own words, in English, compiled in.
 *
 * <p>Written by hand rather than generated, for a reason that is not going to change: the
 * annotation processor depends on this module, so this module is built first and cannot run a
 * processor that does not exist yet. {@code FrameworkCatalogParityTest} fails the build when this
 * class and {@code i18n/zeroz4j.properties} disagree in either direction, which is what keeps the
 * hand-written copy honest.</p>
 *
 * <p>English is compiled in and every other language is read from the classpath, so a deployment
 * that adds no language gets exactly the sentences it got before language support existed, with no
 * file to load and nothing that can fail.</p>
 *
 * <h2>Translating the framework's own refusals</h2>
 *
 * <p>Put {@code i18n/zeroz4j_de.properties} on the server's classpath — the application's own
 * resources folder is the usual place — with the same keys. There is nothing to register.</p>
 *
 * @since 0.9.0
 */
public final class FrameworkText {

    /** The base name of the framework's own catalog. */
    public static final String CATALOG = "i18n/zeroz4j";

    private FrameworkText() {
    }

    /**
     * Every key this catalog has. Used by the parity test, which compares this list with the
     * {@code .properties} file in both directions.
     *
     * @return the keys, in the order they are written in the file
     */
    public static String[] keys() {
        return new String[] {
            FrameworkKeys.UNEXPECTED_FAILURE,
            FrameworkKeys.UNKNOWN_SERVICE,
            FrameworkKeys.UNKNOWN_METHOD,
            FrameworkKeys.AUTHENTICATION_REQUIRED,
            FrameworkKeys.ACCESS_DENIED,
            FrameworkKeys.VALIDATION_FAILED,
            FrameworkKeys.UNKNOWN_LAZY_HANDLE,
            FrameworkKeys.LIVE_WRONG_TYPE,
            FrameworkKeys.LIVE_NOT_CLIENT_WRITABLE,
            FrameworkKeys.LIVE_VALIDATION_FAILED,
            FrameworkKeys.LIVE_REQUIRES_ROLE,
            FrameworkKeys.LIVE_NESTED_NOT_WRITABLE,
            FrameworkKeys.LIVE_NESTED_REQUIRES_ROLE,
            FrameworkKeys.UI_LANGUAGE
        };
    }

    /**
     * The English pattern for one key.
     *
     * @param key the key
     * @return the pattern, or null when this catalog does not have that key
     */
    public static String fallbackText(String key) {
        if (key == null) {
            return null;
        }
        switch (key) {
            case FrameworkKeys.UNEXPECTED_FAILURE:
                return "The server could not complete this request. Reference: {0}";
            case FrameworkKeys.UNKNOWN_SERVICE:
                return "Rejected RMI call to unregistered service: {0}";
            case FrameworkKeys.UNKNOWN_METHOD:
                return "No method '{0}' on service: {1}";
            case FrameworkKeys.AUTHENTICATION_REQUIRED:
                return "Authentication required for: {0}#{1}";
            case FrameworkKeys.ACCESS_DENIED:
                return "Access denied: requires role {0} but user has {1}";
            case FrameworkKeys.VALIDATION_FAILED:
                return "Validation failed for {0}: {1}";
            case FrameworkKeys.UNKNOWN_LAZY_HANDLE:
                return "Unknown or unauthorized lazy handle: {0}";
            case FrameworkKeys.LIVE_WRONG_TYPE:
                return "The change claims to be a {0} but names an object the server holds as a {1}."
                        + " Nothing was changed.";
            case FrameworkKeys.LIVE_NOT_CLIENT_WRITABLE:
                return "{0} is not @ClientWritable";
            case FrameworkKeys.LIVE_VALIDATION_FAILED:
                return "Validation failed: {0}";
            case FrameworkKeys.LIVE_REQUIRES_ROLE:
                return "Requires one of the roles {0}";
            case FrameworkKeys.LIVE_NESTED_NOT_WRITABLE:
                return "The change also alters a {0} that clients may not write."
                        + " Nothing was changed.";
            case FrameworkKeys.LIVE_NESTED_REQUIRES_ROLE:
                return "The change also alters a {0}, which needs one of the roles {1}."
                        + " Nothing was changed.";
            case FrameworkKeys.UI_LANGUAGE:
                return "Language";
            default:
                return null;
        }
    }

    // ---------------------------------------------------------------- one method per refusal

    /**
     * @param reference the code that also appears in the server log
     * @return the sentence an unplanned failure is answered with
     */
    public static Message unexpectedFailure(Object reference) {
        return new Message(CATALOG, FrameworkKeys.UNEXPECTED_FAILURE, reference);
    }

    /**
     * @param serviceName the interface the call named
     * @return the refusal
     */
    public static Message unknownService(Object serviceName) {
        return new Message(CATALOG, FrameworkKeys.UNKNOWN_SERVICE, serviceName);
    }

    /**
     * @param methodName  the method the call named
     * @param serviceName the interface it named
     * @return the refusal
     */
    public static Message unknownMethod(Object methodName, Object serviceName) {
        return new Message(CATALOG, FrameworkKeys.UNKNOWN_METHOD, methodName, serviceName);
    }

    /**
     * @param serviceName the interface the call named
     * @param methodName  the method it named
     * @return the refusal
     */
    public static Message authenticationRequired(Object serviceName, Object methodName) {
        return new Message(CATALOG, FrameworkKeys.AUTHENTICATION_REQUIRED, serviceName, methodName);
    }

    /**
     * @param requiredRoles the roles the method asks for
     * @param heldRoles     the roles the caller has
     * @return the refusal
     */
    public static Message accessDenied(Object requiredRoles, Object heldRoles) {
        return new Message(CATALOG, FrameworkKeys.ACCESS_DENIED, requiredRoles, heldRoles);
    }

    /**
     * @param modelName  the model whose rules were broken
     * @param violations the broken rules, already joined
     * @return the refusal
     */
    public static Message validationFailed(Object modelName, Object violations) {
        return new Message(CATALOG, FrameworkKeys.VALIDATION_FAILED, modelName, violations);
    }

    /**
     * @param handle the name the caller presented
     * @return the refusal
     */
    public static Message unknownLazyHandle(Object handle) {
        return new Message(CATALOG, FrameworkKeys.UNKNOWN_LAZY_HANDLE, handle);
    }

    /**
     * @param claimedType the class the change said it was
     * @param actualType  the class the server holds
     * @return the refusal
     */
    public static Message liveWrongType(Object claimedType, Object actualType) {
        return new Message(CATALOG, FrameworkKeys.LIVE_WRONG_TYPE, claimedType, actualType);
    }

    /**
     * @param modelName the model the change reached
     * @return the refusal
     */
    public static Message liveNotClientWritable(Object modelName) {
        return new Message(CATALOG, FrameworkKeys.LIVE_NOT_CLIENT_WRITABLE, modelName);
    }

    /**
     * @param violations the broken rules, already joined
     * @return the refusal
     */
    public static Message liveValidationFailed(Object violations) {
        return new Message(CATALOG, FrameworkKeys.LIVE_VALIDATION_FAILED, violations);
    }

    /**
     * @param roles the roles the model asks for
     * @return the refusal
     */
    public static Message liveRequiresRole(Object roles) {
        return new Message(CATALOG, FrameworkKeys.LIVE_REQUIRES_ROLE, roles);
    }

    /**
     * @param modelName the nested model the change reached
     * @return the refusal
     */
    public static Message liveNestedNotWritable(Object modelName) {
        return new Message(CATALOG, FrameworkKeys.LIVE_NESTED_NOT_WRITABLE, modelName);
    }

    /**
     * @param modelName the nested model the change reached
     * @param roles     the roles it asks for
     * @return the refusal
     */
    public static Message liveNestedRequiresRole(Object modelName, Object roles) {
        return new Message(CATALOG, FrameworkKeys.LIVE_NESTED_REQUIRES_ROLE, modelName, roles);
    }

    // ---------------------------------------------------------------- words on a control

    /**
     * What a language selector is called when the application has not named it itself.
     *
     * <p>A deployment that puts {@code i18n/zeroz4j_de.properties} on its server classpath gets
     * this in German like everything else, because the framework catalog travels to the browser on
     * the same frame the application catalog does.</p>
     *
     * @return the name of a language selector
     */
    public static Message uiLanguage() {
        return new Message(CATALOG, FrameworkKeys.UI_LANGUAGE);
    }
}
