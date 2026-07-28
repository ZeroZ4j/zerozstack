/*
 * Copyright 2026 Franz Schöning
 * Project: https://www.zeroz4j.com
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
package com.zeroz4j.store;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import com.zeroz4j.db.WriteContext;
import com.zeroz4j.db.ZeroZDb;
import com.zeroz4j.db.net.DbCommand;
import com.zeroz4j.db.net.DbQuery;
import com.zeroz4j.db.net.ZeroZDbNode;
import com.zeroz4j.db.net.ZeroZDbServer;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The promise behind {@code zeroz4j.store.mode}: one piece of application code, three deployment
 * shapes, identical results.
 *
 * <p>The service below is written once against {@code ZeroZDbNode} and then run embedded, in
 * auto-server mode, and against a separate server. If that promise ever breaks, a framework user
 * discovers it when they change deployment — which is the worst possible moment — so it is
 * asserted here instead.</p>
 */
class StoreModeTest {

    /** An application root, exactly as a {@code DataRootProvider} would supply. */
    public static class Catalog {
        public final Map<String, String> products = new LinkedHashMap<>();
        public long nextId = 1;
    }

    /**
     * Application write. Runs wherever the data is, unchanged.
     *
     * <p>A plain class with public fields, not a record: EclipseStore's serializer reaches
     * fields directly, and records refuse that without
     * {@code --add-exports java.base/jdk.internal.misc=ALL-UNNAMED} on the JVM.</p>
     */
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
            return id;   // id allocation and insert in ONE commit
        }
    }

    public static class CountProducts implements DbQuery<Integer> {
        @Override
        public Integer execute(Object root) {
            return ((Catalog) root).products.size();
        }
    }

    /** The application code under test — written once, run in every mode. */
    private static void exerciseService(ZeroZDbNode node) {
        long first = node.execute(new AddProduct("Laptop stand"));
        long second = node.execute(new AddProduct("Desk lamp"));
        assertEquals(first + 1, second, "id allocation must be atomic with the insert");
        assertEquals(2, (int) node.query(new CountProducts()));
    }

    @Test
    void embeddedModeOwnsItsDataAndServesNobody(@TempDir Path dir) {
        try (ZeroZDbNode node = ZeroZDbNode.builder(dir.resolve("t1"), Catalog::new)
                .mode(ZeroZDbNode.Mode.EMBEDDED).build()) {
            exerciseService(node);
            assertTrue(node.isOwner(), "embedded holds its own data");
            assertFalse(node.isServing(), "embedded opens no socket");
            assertNotNull(node.localDb(), "the engine is available in-process");
        }
    }

    @Test
    void autoServerModeOwnsAndServes(@TempDir Path dir) {
        try (ZeroZDbNode node = ZeroZDbNode.builder(dir.resolve("t2"), Catalog::new)
                .mode(ZeroZDbNode.Mode.AUTO_SERVER).build()) {
            exerciseService(node);
            assertTrue(node.isOwner());
            assertTrue(node.isServing(), "auto-server publishes the store to other processes");
        }
    }

    @Test
    void clientModeRunsTheSameCodeAgainstASeparateServer(@TempDir Path dir) {
        try (ZeroZDb serverDb = ZeroZDb.open(new Catalog(), dir.resolve("server"));
             ZeroZDbServer server = ZeroZDbServer.builder()
                     .store("t3", serverDb).schemaId("v1").start();
             ZeroZDbNode node = ZeroZDbNode.builder(dir.resolve("client"), Catalog::new)
                     .storeName("t3").schemaId("v1")
                     .remote("127.0.0.1", server.port()).build()) {

            exerciseService(node);

            assertFalse(node.isOwner(), "a client never holds the data");
            assertNull(node.localDb(), "and therefore has no in-process engine");
            // The work really happened on the server's graph, not a local copy.
            assertEquals(2, (int) serverDb.read(() -> ((Catalog) serverDb.root()).products.size()));
        }
    }

    @Test
    void aClientReadsLocallyThroughAReplica(@TempDir Path dir) throws Exception {
        try (ZeroZDb serverDb = ZeroZDb.open(new Catalog(), dir.resolve("server"));
             ZeroZDbServer server = ZeroZDbServer.builder()
                     .store("t4", serverDb).schemaId("v1").start();
             ZeroZDbNode node = ZeroZDbNode.builder(dir.resolve("client"), Catalog::new)
                     .storeName("t4").schemaId("v1")
                     .remote("127.0.0.1", server.port()).build()) {

            node.execute(new AddProduct("Monitor arm"));

            try (ZeroZDbNode.LocalReads<Catalog> local = node.localReads()) {
                long deadline = System.currentTimeMillis() + 20_000;
                while (local.read(c -> c.products.size()) < 1
                        && System.currentTimeMillis() < deadline) {
                    Thread.sleep(10);
                }
                assertEquals(1, (int) local.read(c -> c.products.size()),
                        "a client reads its own writes from the replica once it catches up");
            }
        }
    }
}
