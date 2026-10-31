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
package com.zeroz4j.server.dev;

import jakarta.enterprise.inject.spi.CDI;
import org.apache.tomcat.InstanceManager;

import java.lang.reflect.InvocationTargetException;

/**
 * Lets Tomcat create servlets, listeners and WebSocket endpoints through CDI.
 *
 * <p>Tomcat's own manager calls the no-argument constructor and knows nothing about
 * {@code @Inject}, so the framework's engine would start with null fields and fail on the first
 * frame. A class CDI knows is handed back as the injected bean, which also makes the engine the one
 * application-scoped instance the other containers give. A class CDI does not know, such as one of
 * Tomcat's own listeners, is constructed directly.</p>
 */
final class DevInstanceManager implements InstanceManager {

    DevInstanceManager() {
    }

    @Override
    public Object newInstance(Class<?> type) throws IllegalAccessException,
            InvocationTargetException, NoSuchMethodException {
        try {
            if (!CDI.current().select(type).isUnsatisfied()) {
                return CDI.current().select(type).get();
            }
        } catch (RuntimeException notABean) {
            // Not resolvable through CDI: constructed directly below.
        }
        try {
            return type.getDeclaredConstructor().newInstance();
        } catch (InstantiationException failure) {
            throw new IllegalStateException("Cannot instantiate " + type.getName(), failure);
        }
    }

    @Override
    public Object newInstance(String className) throws ClassNotFoundException,
            IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return newInstance(Class.forName(className, false, loader()));
    }

    @Override
    public Object newInstance(String className, ClassLoader loader) throws ClassNotFoundException,
            IllegalAccessException, InvocationTargetException, NoSuchMethodException {
        return newInstance(Class.forName(className, false, loader));
    }

    @Override
    public void newInstance(Object instance) {
        // Everything CDI knows arrives through newInstance(Class), already injected.
    }

    @Override
    public void destroyInstance(Object instance) {
        // Application-scoped beans are released when the container shuts down.
    }

    private static ClassLoader loader() {
        ClassLoader context = Thread.currentThread().getContextClassLoader();
        return context != null ? context : DevInstanceManager.class.getClassLoader();
    }
}
