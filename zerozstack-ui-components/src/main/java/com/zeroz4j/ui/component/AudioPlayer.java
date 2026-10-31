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
package com.zeroz4j.ui.component;

/**
 * Plays an audio file with the browser's own controls: play and pause, a scrubber, volume.
 *
 * <pre>{@code
 * AudioPlayer player = new AudioPlayer();
 * player.setTitle("Session 3");
 * player.setServerFile("recording", "42");
 * player.addWatchedListener(p -> progress.markHeard("42"));
 * Effect.create(() -> clock.setText(format(player.position().get())));
 * layout.add(player);
 * }</pre>
 *
 * <p>Move it from code with {@link #play()}, {@link #pause()}, {@link #seekTo(double)},
 * {@link #skip(double)} and {@link #setPlaybackRate(double)}. Where it is and whether it plays are
 * the signals {@link #position()}, {@link #duration()} and {@link #playing()}. See
 * {@link MediaPlayer}.</p>
 *
 * @since 0.11.0
 */
public class AudioPlayer extends MediaPlayer {

    /** Creates a player with nothing loaded. */
    public AudioPlayer() {
        super("audio", "Audio");
    }
}
