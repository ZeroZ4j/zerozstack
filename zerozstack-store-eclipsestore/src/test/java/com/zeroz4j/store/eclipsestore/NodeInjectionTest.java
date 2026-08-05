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

import com.zeroz4j.db.WriteContext;
import com.zeroz4j.db.net.DbCommand;
import com.zeroz4j.db.net.DbQuery;
import com.zeroz4j.db.net.ZeroZDbNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.weld.junit5.WeldInitiator;
import org.jboss.weld.junit5.WeldSetup;
import org.jboss.weld.junit5.EnableWeld;
import org.jboss.weld.junit.MockBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * {@code @Inject ZeroZDbNode} — the documented way to reach the store — through a real CDI
 * container.
 *
 * <p>Everything else in this project builds a {@link ZeroZDbNode} by hand, which is why the defect
 * this test guards shipped in 0.4.1: {@link EclipseStoreProducer#getNode} was {@code @RequestScoped},
 * a normal scope needs a client proxy, and {@code ZeroZDbNode} is {@code final} with a private
 * constructor and cannot be proxied. Every application following the guide got WELD-001410 at
 * deployment. No unit test noticed, because no unit test used CDI.</p>
 *
 * <p>The test is therefore mostly its own setup: if the producer's scope regresses to a normal one,
 * the container fails to start and every method here fails before its first assertion. That is the
 * point — the assertions below are the second line of defence, not the first.</p>
 */
@EnableWeld
class NodeInjectionTest {

    /** An application root, exactly as a {@code DataRootProvider} would supply. */
    public static class Catalog {
        public final Map<String, String> products = new LinkedHashMap<>();
        public long nextId = 1;
    }

    public static class AddProduct implements DbCommand<Long> {
        public String name;

        public AddProduct() {
        }

        public AddProduct(String name) {
            this.name = name;
        }

        @Override
        public Long execute(WriteContext ctx, Object root) {
            Catalog catalog = (Catalog) root;
            ctx.edit(catalog);
            ctx.edit(catalog.products);
            long id = catalog.nextId++;
            catalog.products.put("P-" + id, name);
            return id;
        }
    }

    public static class CountProducts implements DbQuery<Integer> {
        @Override
        public Integer execute(Object root) {
            return ((Catalog) root).products.size();
        }
    }

    /**
     * The shape the guide documents, and the shape that failed: an application-scoped service that
     * injects the node directly.
     */
    @ApplicationScoped
    public static class ProductService {

        @Inject
        ZeroZDbNode db;

        long add(String name) {
            return db.execute(new AddProduct(name));
        }

        int count() {
            return db.query(new CountProducts());
        }

        ZeroZDbNode node() {
            return db;
        }
    }

    /**
     * Stands in for the real provider without its {@code @ConfigProperty} fields, which need a
     * MicroProfile Config implementation this module does not carry at test scope. What is under
     * test is the producer and the injection point, not the provider's configuration parsing.
     */
    static class StubProvider extends TenantStorageProvider {
        ZeroZDbNode node;
        String lastTenantAsked;

        @Override
        public ZeroZDbNode getNode(String tenantId) {
            lastTenantAsked = tenantId;
            return node;
        }
    }

    @TempDir
    static Path storeDir;

    static final StubProvider PROVIDER = new StubProvider();

    /**
     * Note what is <em>not</em> activated: no request context. A request-scoped node was unusable
     * from a scheduler, a virtual thread or startup code, and this container reproduces that
     * condition rather than hiding it behind an implicit active context.
     */
    @WeldSetup
    WeldInitiator weld = WeldInitiator
            .from(EclipseStoreProducer.class, ProductService.class)
            .addBeans(MockBean.of(PROVIDER, TenantStorageProvider.class))
            .build();

    @BeforeAll
    static void openStore() {
        PROVIDER.node = ZeroZDbNode.embedded(storeDir.resolve("store"), Catalog::new);
    }

    @AfterAll
    static void closeStore() {
        if (PROVIDER.node != null) {
            PROVIDER.node.close();
        }
    }

    @Test
    void theNodeInjectsIntoAnApplicationScopedService(ProductService service) {
        assertNotNull(service.node(), "the documented injection point must resolve");
        assertSame(PROVIDER.node, service.node(),
                "and must hand over the provider's node, not a copy or a proxy of one");
    }

    @Test
    void anInjectedNodeReadsAndWritesWithNoRequestContextActive(ProductService service) {
        int before = service.count();

        service.add("Laptop stand");
        service.add("Desk lamp");

        assertEquals(before + 2, service.count(),
                "commands and queries run through the injected node off-request");
    }

    @Test
    void idAllocationStaysAtomicThroughTheInjectedNode(ProductService service) {
        long first = service.add("A");
        long second = service.add("B");

        assertEquals(first + 1, second, "the counter and the insert commit together");
    }

    @Test
    void theTenantIsResolvedThroughTheProducer(ProductService service) {
        service.count();

        assertEquals("default", PROVIDER.lastTenantAsked,
                "with no TenantResolver published, the producer falls back to the default tenant");
    }
}
