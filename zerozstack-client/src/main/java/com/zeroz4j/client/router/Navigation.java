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
package com.zeroz4j.client.router;

import com.zeroz4j.api.DisconnectedException;
import com.zeroz4j.api.RequestTimeoutException;

/**
 * One attempt to change what the router shows: started by a link, a call, Back or Forward, the
 * first render, a redirect or a retry, and ended by exactly one of finishing, failing, or being
 * overtaken by a newer navigation.
 *
 * <p>Handed to every {@link Router.LifecycleListener} callback. Read-only; the router owns it.</p>
 *
 * <h2>Only the newest navigation counts</h2>
 * <p>Every navigation gets a number one higher than the one before. When a new navigation starts
 * while an older one is still loading, the older one is {@link Outcome#SUPERSEDED} at that moment,
 * and whatever it later produces - a finished view or a failure - is thrown away: it is not put on
 * the screen, it does not change {@link Router#currentPath()}, and no listener hears about it. So a
 * listener that turns a loading indicator on at {@code onNavigationStarted} and off at
 * {@code onNavigationFinished} or {@code onNavigationFailed} is never left waiting: the navigation
 * that started last always ends in one of the two.</p>
 */
public final class Navigation {

    /** What started a navigation. */
    public enum Trigger {
        /** {@link Router#start}, rendering whatever address the page was opened at. */
        INITIAL,
        /** A click on an {@code <a data-route>} link. */
        LINK,
        /** {@link Router#navigate}, called by the application. */
        NAVIGATE,
        /** {@link Router#replace}, called by the application. */
        REPLACE,
        /** The browser's Back or Forward button. */
        HISTORY,
        /**
         * The router sending a navigation somewhere else: to the not-found route for an address
         * nothing matches, or to the forbidden route for one the person may not see.
         * {@link #redirectedFrom()} names the navigation that was sent away.
         */
        REDIRECT,
        /** {@link Router#retry()}, or the Retry button on the router's failure message. */
        RETRY
    }

    /** Where a navigation has got to. */
    public enum Outcome {
        /** Still loading. */
        PENDING,
        /** Its view is on the screen. */
        FINISHED,
        /** It could not be completed; {@link #failure()} says why. The screen was left as it was. */
        FAILED,
        /** A newer navigation started before this one ended, so its result is ignored. */
        SUPERSEDED
    }

    private final long id;
    private final String path;
    private final Trigger trigger;
    private final Navigation redirectedFrom;
    private final long supersededId;
    private Outcome outcome = Outcome.PENDING;
    private Throwable failure;

    Navigation(long id, String path, Trigger trigger, Navigation redirectedFrom, long supersededId) {
        this.id = id;
        this.path = path;
        this.trigger = trigger;
        this.redirectedFrom = redirectedFrom;
        this.supersededId = supersededId;
    }

    /**
     * This navigation's sequence number. Each navigation's is one higher than the one started before
     * it, so the larger of two is always the newer.
     *
     * @return the sequence number, starting at 1
     */
    public long id() {
        return id;
    }

    /**
     * The route this navigation is going to, as {@code @Route} declares routes, with its query
     * string when it has one.
     *
     * @return e.g. {@code "/projects?sort=name"}
     */
    public String path() {
        return path;
    }

    /**
     * What started it.
     *
     * @return the trigger
     */
    public Trigger trigger() {
        return trigger;
    }

    /**
     * The navigation a redirect was sent away from.
     *
     * <p>A redirect is a navigation of its own. The one that caused it is superseded by it at the
     * moment it starts - it never finishes or fails by itself - and the redirect then finishes or
     * fails like any other. So an address nothing matches, with a not-found route set, produces
     * two started events and one finished event.</p>
     *
     * @return the navigation that was redirected, or null when this is not a redirect
     */
    public Navigation redirectedFrom() {
        return redirectedFrom;
    }

    /**
     * The sequence number of the navigation this one overtook, if one was still loading when this
     * one started.
     *
     * @return that navigation's {@link #id()}, or 0 when nothing was loading
     */
    public long supersededId() {
        return supersededId;
    }

    /**
     * Where this navigation has got to.
     *
     * @return the outcome so far
     */
    public Outcome outcome() {
        return outcome;
    }

    /**
     * Why it failed.
     *
     * @return the reason, or null unless {@link #outcome()} is {@link Outcome#FAILED}
     */
    public Throwable failure() {
        return failure;
    }

    /**
     * Whether it failed because the connection dropped or the server did not answer in time, as
     * opposed to the server refusing a loader's call or a route that does not exist.
     *
     * <p>Worth distinguishing because the advice is different. A connection problem is usually
     * solved by trying again once the connection is back; the framework reconnects by itself, but
     * never repeats a failed call by itself. A refused call is not solved by trying again.</p>
     *
     * @return true when {@link #failure()} is a {@link DisconnectedException} or a
     *         {@link RequestTimeoutException}
     */
    public boolean isConnectionProblem() {
        return isConnectionProblem(failure);
    }

    static boolean isConnectionProblem(Throwable reason) {
        return reason instanceof DisconnectedException || reason instanceof RequestTimeoutException;
    }

    void markSuperseded() {
        if (outcome == Outcome.PENDING) {
            outcome = Outcome.SUPERSEDED;
        }
    }

    void markFinished() {
        outcome = Outcome.FINISHED;
    }

    void markFailed(Throwable reason) {
        outcome = Outcome.FAILED;
        failure = reason;
    }

    @Override
    public String toString() {
        return "Navigation[" + id + " " + trigger + " " + path + " " + outcome + "]";
    }
}
