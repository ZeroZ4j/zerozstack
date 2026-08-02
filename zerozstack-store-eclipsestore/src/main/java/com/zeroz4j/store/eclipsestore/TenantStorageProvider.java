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
package com.zeroz4j.store.eclipsestore;

import com.zeroz4j.api.store.DataRootProvider;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;
import com.zeroz4j.db.Durability;
import com.zeroz4j.db.net.ZeroZDbNode;

import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application-scoped CDI manager for multi-tenant EclipseStore {@link EmbeddedStorageManager} storage engines.
 *
 * <p>Initializes and maintains separate persistent EclipseStore engines per tenant under subdirectories of {@code zeroz4j.store.path}.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Root Initialization:</b> Uses {@link DataRootProvider} (if available) to initialize default tenant root objects via {@code createDefaultRoot(tenantId)}.</li>
 *   <li><b>Lazy Instantiation:</b> Uses {@link ConcurrentHashMap#computeIfAbsent} to lazily spin up storage engines on demand.</li>
 *   <li><b>Graceful Teardown:</b> {@link #shutdownAll()} shuts down all active tenant storage managers on bean destruction ({@code @PreDestroy}).</li>
 * </ul>
 */
@ApplicationScoped
public class TenantStorageProvider {

    private static final Logger LOG = Logger.getLogger(TenantStorageProvider.class.getName());

    @Inject
    @ConfigProperty(name = "zeroz4j.store.path", defaultValue = "./data")
    String basePath;

    @Inject
    Instance<DataRootProvider> dataRootProvider;

    @Inject
    @ConfigProperty(name = "zeroz4j.store.mode", defaultValue = "EMBEDDED")
    String modeSetting;

    @Inject
    @ConfigProperty(name = "zeroz4j.store.durability", defaultValue = "SYNC")
    String durabilitySetting;

    @Inject
    @ConfigProperty(name = "zeroz4j.store.schemaId", defaultValue = "default")
    String schemaId;

    /**
     * Only read in {@code CLIENT} mode, so it is genuinely optional.
     *
     * <p>Declared as {@code Optional} rather than {@code defaultValue = ""}: Helidon's
     * {@code ConfigCdiExtension} treats an empty default as <em>no</em> default, so the empty-string
     * form fails CDI validation at startup with "Cannot find value for key" — and takes down every
     * application that never intended to use client mode.</p>
     */
    @Inject
    @ConfigProperty(name = "zeroz4j.store.server.host")
    Optional<String> serverHost;

    @Inject
    @ConfigProperty(name = "zeroz4j.store.server.port", defaultValue = "5150")
    int serverPort;

    /** Optional for the same reason as {@link #serverHost}. */
    @Inject
    @ConfigProperty(name = "zeroz4j.store.server.secret")
    Optional<String> serverSecret;

    private final ConcurrentHashMap<String, ZeroZDbNode> nodes = new ConcurrentHashMap<>();

    /**
     * Installs the server's handling of EclipseStore {@code Lazy} fields.
     *
     * <p>Done here because this module is where EclipseStore is on the classpath: with the adapter
     * installed, a {@code Lazy} field on a {@code @DataModel} serializes as a session-scoped handle
     * instead of failing as an unsupported type, and the deferred subgraph is only loaded if a client
     * actually asks for it.</p>
     */
    @jakarta.annotation.PostConstruct
    void installLazySupport() {
        com.zeroz4j.store.EclipseStoreLazyAdapter.install();
        LOG.info("[zeroz4j] EclipseStore Lazy support installed");
    }

    /**
     * Lazily gets or creates the EclipseStore {@link EmbeddedStorageManager} for a specific tenant.
     *
     * @param tenantId the tenant identifier string
     * @return active {@link EmbeddedStorageManager} instance for the specified tenant
     * @throws IllegalArgumentException if {@code tenantId} is null or empty
     *
     * <p><b>Under the hood:</b> Checks {@code dataRootProvider}. Instantiates root object. Invokes {@code EmbeddedStorage.start(root, Paths.get(basePath, tenantId))}.</p>
     */
    public EmbeddedStorageManager getStorageManager(String tenantId) {
        ZeroZDbNode node = getNode(tenantId);
        EmbeddedStorageManager manager = node.localDb() == null ? null
                : node.localDb().storageManager();
        if (manager == null) {
            throw new IllegalStateException(
                    "No local storage manager in " + mode() + " mode: this process does not hold "
                            + "tenant '" + tenantId + "' data, it talks to a server. Use the "
                            + "tenant's ZeroZDbNode (inject ZeroZDbNode, or call getNode) and send "
                            + "DbCommand/DbQuery objects, which work in every mode.");
        }
        return manager;
    }

    /**
     * The tenant's database node — the API that works in every {@link StoreMode}.
     *
     * <p>Send {@code DbCommand} to write and {@code DbQuery} to read, or take
     * {@code node.localReads()} for heap-speed reads (the live graph when this process owns the
     * store, a continuously refreshed replica when it does not). {@code node.localDb()} exposes
     * the engine directly for lambda write-blocks and index registration, and is present only
     * when this process owns the data.</p>
     *
     * @param tenantId the tenant identifier string
     * @return the node for that tenant, created on first use
     * @throws IllegalArgumentException if {@code tenantId} is null or empty
     */
    public ZeroZDbNode getNode(String tenantId) {
        if (tenantId == null || tenantId.isEmpty()) {
            throw new IllegalArgumentException("Tenant ID must not be null or empty");
        }
        return nodes.computeIfAbsent(tenantId, this::openNode);
    }

    /** The configured mode, parsed once and reported in logs and errors. */
    public StoreMode mode() {
        try {
            return StoreMode.valueOf(modeSetting.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown zeroz4j.store.mode '" + modeSetting
                    + "'. Expected one of EMBEDDED, AUTO_SERVER, CLIENT.", e);
        }
    }

    private ZeroZDbNode openNode(String tenantId) {
        StoreMode mode = mode();
        LOG.info("[zeroz4j-store] Opening store for tenant '" + tenantId + "' in " + mode + " mode");

        ZeroZDbNode.Builder builder = ZeroZDbNode
                .builder(Paths.get(basePath, tenantId), () -> createRoot(tenantId))
                .storeName(tenantId)
                .schemaId(schemaId)
                .durability(Durability.valueOf(durabilitySetting.trim().toUpperCase()));

        switch (mode) {
            case EMBEDDED -> builder.mode(ZeroZDbNode.Mode.EMBEDDED);
            case AUTO_SERVER -> builder.mode(ZeroZDbNode.Mode.AUTO_SERVER);
            case CLIENT -> {
                String host = serverHost.orElse("");
                if (host.isBlank()) {
                    throw new IllegalStateException(
                            "zeroz4j.store.mode=CLIENT needs zeroz4j.store.server.host to be set.");
                }
                builder.remote(host, serverPort);
                String secret = serverSecret.orElse("");
                if (!secret.isBlank()) {
                    builder.secret(secret);
                }
            }
        }
        return builder.build();
    }

    private Object createRoot(String tenantId) {
        Object root = null;
        if (!dataRootProvider.isUnsatisfied()) {
            root = dataRootProvider.get().createDefaultRoot(tenantId);
        }
        return root == null ? new Object() : root;   // fallback, as before
    }

    /**
     * Shuts down all active tenant storage managers gracefully upon application teardown.
     *
     * <p><b>Under the hood:</b> Iterates through {@code storageManagers.values()} and calls {@code manager.shutdown()}.</p>
     */
    @PreDestroy
    public void shutdownAll() {
        nodes.values().forEach(node -> {
            if (node != null) {
                try {
                    node.close();
                } catch (Exception e) {
                    LOG.log(Level.WARNING, "[zeroz4j-store] Error closing store: " + e.getMessage(), e);
                }
            }
        });
        nodes.clear();
    }
}
