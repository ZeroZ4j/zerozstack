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
package com.zeroz4j.api;

/**
 * Decouples generated client RMI stubs in shared code from the concrete client execution runtime.
 *
 * <p>The client runtime (`zerozstack-client`) registers its concrete {@link Executor} delegate
 * implementation at startup via {@link #setInstance(Executor)}. Generated stubs invoke
 * {@link #executeCall(String, String, Object[])} to issue network calls.</p>
 *
 * <p><b>AI Agent Execution Notes:</b></p>
 * <ul>
 *   <li><b>State Mutations:</b> Stores static volatile {@code delegate} instance.</li>
 *   <li><b>Coroutines:</b> Execution of {@link #executeCall} triggers WebAssembly coroutine suspension while awaiting WebSocket network responses.</li>
 * </ul>
 */
public class RmiClientExecutor {

    /**
     * Internal SPI interface for executing RMI network calls on the client.
     */
    public interface Executor {
        /**
         * Executes a remote RMI method call.
         *
         * @param interfaceName canonical interface name of the service
         * @param methodName    name of the target service method
         * @param args          marshalled array of arguments
         * @return method return value (unmarshalled from binary frame)
         */
        Object execute(String interfaceName, String methodName, Object[] args);
    }

    private static volatile Executor delegate;

    /**
     * Registers the singleton client RMI executor delegate instance.
     *
     * @param exec the concrete client executor
     *
     * <p><b>Under the hood:</b> Assigns volatile field {@code delegate = exec}. Called during `WasmRmiClient.initialize()`.</p>
     */
    public static void setInstance(Executor exec) {
        delegate = exec;
    }

    /**
     * Executes a remote method call for a generated RMI stub.
     *
     * @param interfaceName canonical interface name
     * @param methodName    target method name
     * @param args          method arguments
     * @return return value of the remote invocation
     * @throws IllegalStateException if delegate is uninitialized
     *
     * <p><b>Under the hood:</b> Validates non-null {@code delegate} and calls {@code delegate.execute(interfaceName, methodName, args)}.</p>
     */
    public static Object executeCall(String interfaceName, String methodName, Object[] args) {
        Executor exec = delegate;
        if (exec == null) {
            throw new IllegalStateException("RMI client executor delegate has not been initialized. "
                + "Ensure WasmRmiClient.initialize() was called on startup.");
        }
        return exec.execute(interfaceName, methodName, args);
    }
}
