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

import com.zeroz4j.api.store.TenantResolver;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.Dependent;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.enterprise.inject.Produces;
import jakarta.enterprise.inject.spi.InjectionPoint;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.eclipse.store.storage.embedded.types.EmbeddedStorageManager;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * CDI producer for the store: the tenant's {@link com.zeroz4j.db.net.ZeroZDbNode}, and the raw
 * {@link EmbeddedStorageManager} underneath it.
 *
 * <p>Uses {@link TenantResolver} (if available) to determine the current tenant ID and fetches the matching
 * object from {@link TenantStorageProvider}.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>Tenant Resolution:</b> Checks CDI {@link Instance} of {@link TenantResolver}. If unsatisfied or returns null, defaults to {@code "default"}.</li>
 *   <li><b>Scope:</b> {@link #getNode(InjectionPoint)} is {@code @Dependent} — it must be, see its
 *       javadoc. {@link #getStorageManager()} is {@code @RequestScoped}.</li>
 * </ul>
 */
public class EclipseStoreProducer {

    private static final Logger LOG = Logger.getLogger(EclipseStoreProducer.class.getName());

    /** Bean classes already warned about, so a hot injection point does not flood the log. */
    private static final Set<String> PINNED_TENANT_WARNINGS = ConcurrentHashMap.newKeySet();

    @Inject
    Instance<TenantResolver> tenantResolver;

    @Inject
    TenantStorageProvider storageProvider;

    /**
     * Resolves the current tenant ID and produces the corresponding {@link EmbeddedStorageManager} for the request.
     *
     * @return active {@link EmbeddedStorageManager} instance for the resolved tenant
     *
     * <p><b>Under the hood:</b> Checks {@code tenantResolver.isUnsatisfied()}. Resolves tenant ID. Invokes {@code storageProvider.getStorageManager(tenantId)}.</p>
     *
     * <p>This one stays {@code @RequestScoped} on purpose, and the asymmetry with
     * {@link #getNode(InjectionPoint)} is not an oversight. {@link EmbeddedStorageManager} is an
     * <em>interface</em>, so it proxies without trouble, and the proxy is what re-resolves the
     * tenant on every request — which {@code @Dependent} would give up. The cost is that it is
     * only reachable where a request context is active; the engine activates one around each RMI
     * dispatch, so services and {@code LiveMutationListener}s are covered and background threads
     * are not. Prefer the node anyway: the raw manager is null wherever the data is not local.</p>
     */
    @Produces
    @RequestScoped
    public EmbeddedStorageManager getStorageManager() {
        return storageProvider.getStorageManager(currentTenant());
    }

    /**
     * Produces the current tenant's database node — the injection point that works in every
     * {@link StoreMode}.
     *
     * <p>Prefer this over injecting {@link EmbeddedStorageManager}: the node exposes the same
     * API whether this process owns the data or talks to a server, so a service written against
     * it survives a change of deployment shape. The raw storage manager exists only where the
     * data is local, and injecting it pins the application to {@code EMBEDDED} or
     * {@code AUTO_SERVER}.</p>
     *
     * <pre>{@code
     * @Inject ZeroZDbNode db;
     *
     * long id = db.execute(new AddProduct("SKU-1", "Laptop stand"));
     * int total = db.query(new CountProducts());
     * }</pre>
     *
     * <h4>Why {@code @Dependent} and not a normal scope</h4>
     * A normal scope ({@code @RequestScoped}, {@code @ApplicationScoped}) makes the container
     * inject a client proxy, and a proxy is a generated subclass. {@code ZeroZDbNode} is
     * {@code final} and its only constructor is private, so it cannot be subclassed and the
     * injection point is rejected outright — WELD-001410 at deployment, or WELD-001437 at first use
     * when it is reached through {@code Instance}. Through 0.4.1 this method was
     * {@code @RequestScoped} and {@code @Inject ZeroZDbNode} therefore could not work at all,
     * however it was written.
     *
     * <p>{@code @Dependent} is a pseudo-scope, so no proxy is needed and the final class injects
     * cleanly. It also means the node is reachable from threads with no active request context —
     * schedulers, virtual threads, startup code — which a request-scoped node never was.</p>
     *
     * <h4>The tenant is resolved once, when the injecting bean is created</h4>
     * A {@code @Dependent} producer runs when the bean holding the injection point is created, not
     * on every call. For an {@code @ApplicationScoped} service — which is what a
     * {@code @RmiService} implementation is — that means one node for the life of the application.
     *
     * <p>That is exactly right for a single-tenant application, which is the default and by far the
     * common case. It is <strong>wrong</strong> for a multi-tenant one: whichever tenant happened to
     * trigger the service's creation would own every write after it. If you have a
     * {@link TenantResolver}, inject {@link TenantStorageProvider} and call
     * {@code getNode(tenantId)} per operation instead; this producer logs a warning when it spots
     * the combination.</p>
     *
     * @param ip supplied by the container. It reports the pinned-tenant hazard above, and it also
     *           pins the scope: {@code InjectionPoint} metadata may only be injected into a
     *           {@code @Dependent} bean, so restoring a normal scope here is a definition error
     *           (WELD-001406) rather than something that compiles and fails in the field.
     * @return the node for the resolved tenant
     */
    @Produces
    @Dependent
    public com.zeroz4j.db.net.ZeroZDbNode getNode(InjectionPoint ip) {
        warnIfTenantWillBePinned(ip);
        return storageProvider.getNode(currentTenant());
    }

    /**
     * Warns when a tenant-specific node is about to be held for longer than one tenant's turn.
     *
     * <p>Only fires in a genuinely multi-tenant application (one that publishes a
     * {@link TenantResolver}) and only for injection points on beans that outlive a request. In a
     * single-tenant application there is nothing to warn about, so nothing is logged.</p>
     */
    private void warnIfTenantWillBePinned(InjectionPoint ip) {
        if (tenantResolver.isUnsatisfied() || ip == null || ip.getBean() == null) {
            return;
        }
        Class<?> scope = ip.getBean().getScope();
        if (scope != ApplicationScoped.class && scope != Singleton.class) {
            return;
        }
        String beanClass = ip.getBean().getBeanClass().getName();
        if (!PINNED_TENANT_WARNINGS.add(beanClass)) {
            return;
        }
        LOG.warning("[zeroz4j-store] " + beanClass + " is " + scope.getSimpleName()
                + " and injects ZeroZDbNode directly, but this application has a TenantResolver. "
                + "The node is resolved once, when the bean is created, so every later request will "
                + "use tenant '" + currentTenant() + "' whoever it belongs to. Inject "
                + "TenantStorageProvider and call getNode(tenantId) per operation instead.");
    }

    private String currentTenant() {
        String tenantId = "default";
        if (!tenantResolver.isUnsatisfied()) {
            tenantId = tenantResolver.get().resolveTenant();
            if (tenantId == null || tenantId.isEmpty()) {
                tenantId = "default";
            }
        }
        return tenantId;
    }
}
