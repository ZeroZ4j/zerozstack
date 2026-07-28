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
package com.zeroz4j.client;

import com.zeroz4j.api.SyncFrameTypes;
import org.eclipse.serializer.reference.Lazy;

/**
 * The browser-side implementation of EclipseStore's {@code Lazy} interface.
 *
 * <p>This is what makes a {@code Lazy<T>} field work end to end without changing your model: the
 * server holds a real {@code Lazy.Default} backed by storage, the client holds one of these backed by
 * an RMI round trip, and both satisfy the same interface. Only the handle travels on the wire.</p>
 *
 * <p>{@link #get()} suspends the calling coroutine while the round trip is in flight, exactly like any
 * other RMI call, then caches the result. Reading it again is free.</p>
 *
 * <p>TeaVM links the {@code Lazy} interface and eliminates every EclipseStore implementation behind
 * it, so none of the storage engine reaches the browser bundle.</p>
 *
 * @param <T> the deferred value type
 */
public final class ClientLazy<T> implements Lazy<T> {

    private final String handle;
    private T value;
    private boolean loaded;

    ClientLazy(String handle) {
        this.handle = handle;
    }

    /**
     * Returns the deferred value, fetching it from the server on first call.
     *
     * @return the resolved value, possibly null
     */
    @Override
    @SuppressWarnings("unchecked")
    public T get() {
        if (!loaded) {
            value = (T) WasmRmiClient.executeCall(
                    SyncFrameTypes.LAZY_SERVICE, "resolve", new Object[] {handle});
            loaded = true;
        }
        return value;
    }

    /**
     * Returns the value only if it has already been fetched.
     *
     * @return the cached value, or null when nothing has been loaded yet — never triggers a round trip
     */
    @Override
    public T peek() {
        return value;
    }

    /**
     * Drops the cached value so the next {@link #get()} fetches again.
     *
     * @return the value that was cached, or null
     */
    @Override
    public T clear() {
        T previous = value;
        value = null;
        loaded = false;
        return previous;
    }

    @Override
    public T forceClear() {
        return clear();
    }

    @Override
    public boolean clear(Lazy.ClearingEvaluator evaluator) {
        if (evaluator == null || evaluator.needsClearing(this)) {
            clear();
            return true;
        }
        return false;
    }

    /**
     * @return always true — the value exists on the server whether or not this client has fetched it
     */
    @Override
    public boolean isStored() {
        return true;
    }

    /**
     * @return whether this client has already fetched the value
     */
    @Override
    public boolean isLoaded() {
        return loaded;
    }

    /**
     * @return 0; the client does not track access times, which only matter to the server's cache
     *         eviction
     */
    @Override
    public long lastTouched() {
        return 0L;
    }

    /**
     * @return the server-issued handle this reference resolves through
     */
    public String handle() {
        return handle;
    }

    // --- UsageMarkable -------------------------------------------------------------------------
    // Usage marks drive EclipseStore's server-side cache eviction: they tell the reference manager
    // whether something still needs the loaded value. The browser has no such manager and no memory
    // pressure story of its own, so these are inert here. clear() remains available for a view that
    // wants to drop a cached value explicitly.

    @Override
    public int markUsedFor(Object user) {
        return 0;
    }

    @Override
    public int unmarkUsedFor(Object user) {
        return 0;
    }

    @Override
    public boolean isUsed() {
        return false;
    }

    @Override
    public int markUnused() {
        return 0;
    }

    @Override
    public void accessUsageMarks(
            java.util.function.Consumer<? super org.eclipse.serializer.collections.types.XGettingEnum<Object>> logic) {
        // No usage marks are tracked, so there is nothing to hand to the visitor.
    }
}
