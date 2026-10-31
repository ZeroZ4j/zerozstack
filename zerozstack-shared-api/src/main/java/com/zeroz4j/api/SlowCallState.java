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
 * One moment in the life of a slow call, for a screen to draw.
 *
 * <p>Immutable. {@link SlowCall#state()} hands back a new one each time something changes.</p>
 *
 * @param <T> what the work returns
 * @since 0.11.0
 */
public final class SlowCallState<T> {

    private final SlowCallStatus status;
    private final T value;
    private final String message;
    private final int percent;
    private final String progressText;

    private SlowCallState(SlowCallStatus status, T value, String message, int percent,
                          String progressText) {
        this.status = status;
        this.value = value;
        this.message = message;
        this.percent = percent;
        this.progressText = progressText;
    }

    /**
     * @param <T> what the work returns
     * @return the state before the browser has heard from the server
     */
    public static <T> SlowCallState<T> checking() {
        return new SlowCallState<>(SlowCallStatus.CHECKING, null, null, -1, null);
    }

    /**
     * @param <T> what the work returns
     * @return the state of a key the server has nothing under
     */
    public static <T> SlowCallState<T> idle() {
        return new SlowCallState<>(SlowCallStatus.IDLE, null, null, -1, null);
    }

    /**
     * @param <T>          what the work returns
     * @param percent      how far along, 0 to 100, or -1 when the work has not said
     * @param progressText what the work says it is doing, or null
     * @return the state of work that is running
     */
    public static <T> SlowCallState<T> running(int percent, String progressText) {
        return new SlowCallState<>(SlowCallStatus.RUNNING, null, null, percent, progressText);
    }

    /**
     * @param <T>   what the work returns
     * @param value what it returned
     * @return the state of work that finished
     */
    public static <T> SlowCallState<T> finished(T value) {
        return new SlowCallState<>(SlowCallStatus.FINISHED, value, null, 100, null);
    }

    /**
     * @param <T>     what the work returns
     * @param message the sentence to show
     * @return the state of work that failed
     */
    public static <T> SlowCallState<T> failed(String message) {
        return new SlowCallState<>(SlowCallStatus.FAILED, null, message, -1, null);
    }

    /**
     * @param <T> what the work returns
     * @return the state of work somebody stopped
     */
    public static <T> SlowCallState<T> canceled() {
        return new SlowCallState<>(SlowCallStatus.CANCELED, null, null, -1, null);
    }

    /** @return where the call stands */
    public SlowCallStatus status() {
        return status;
    }

    /** @return true until the browser has heard from the server */
    public boolean isChecking() {
        return status == SlowCallStatus.CHECKING;
    }

    /** @return true when the server has nothing under this key */
    public boolean isIdle() {
        return status == SlowCallStatus.IDLE;
    }

    /** @return true while the work runs, and until its value has reached this browser */
    public boolean isRunning() {
        return status == SlowCallStatus.RUNNING;
    }

    /** @return true once the value is here */
    public boolean isFinished() {
        return status == SlowCallStatus.FINISHED;
    }

    /** @return true when the work failed or the server no longer has it */
    public boolean isFailed() {
        return status == SlowCallStatus.FAILED;
    }

    /** @return true when somebody stopped the work */
    public boolean isCanceled() {
        return status == SlowCallStatus.CANCELED;
    }

    /** @return what the work returned; null unless {@link #isFinished()} */
    public T value() {
        return value;
    }

    /** @return the sentence to show; null unless {@link #isFailed()} */
    public String message() {
        return message;
    }

    /** @return how far along, 0 to 100, or -1 when the work has not said */
    public int percent() {
        return percent;
    }

    /** @return what the work last said it was doing, or null */
    public String progressText() {
        return progressText;
    }

    @Override
    public String toString() {
        return "SlowCallState[" + status
                + (message != null ? ", " + message : "")
                + (percent >= 0 ? ", " + percent + "%" : "") + "]";
    }
}
