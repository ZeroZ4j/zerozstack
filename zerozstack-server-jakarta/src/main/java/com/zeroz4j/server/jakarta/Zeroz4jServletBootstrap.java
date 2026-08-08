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
package com.zeroz4j.server.jakarta;

import com.zeroz4j.api.BinaryRegistry;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.util.logging.Logger;

/**
 * Loads the generated serializers when the WAR starts.
 *
 * <p>{@link BinaryRegistry#init()} discovers the registrars the annotation processor generated. In a
 * standalone deployment {@code Zeroz4jServer.start} calls it; in a WAR nothing does, and the symptom
 * is an "Unknown @DataModel class" the first time anything is sent — a long way from the missing
 * call. Annotated {@code @WebListener}, so adding this module is enough; no {@code web.xml} entry is
 * needed.</p>
 */
@WebListener
public class Zeroz4jServletBootstrap implements ServletContextListener {

    private static final Logger LOG = Logger.getLogger(Zeroz4jServletBootstrap.class.getName());

    /** Instantiated by the servlet container through {@code @WebListener}, not by application code. */
    public Zeroz4jServletBootstrap() {
        // Container-instantiated; nothing to set up here.
    }

    @Override
    public void contextInitialized(ServletContextEvent event) {
        BinaryRegistry.init();
        LOG.info("[zeroz4j] Serializer registry initialised for "
                + event.getServletContext().getContextPath() + ".");
    }

    @Override
    public void contextDestroyed(ServletContextEvent event) {
        // Nothing to undo: the registry is static and the container is discarding the classloader.
    }
}
