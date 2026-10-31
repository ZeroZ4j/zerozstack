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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Teaches the wire about {@link SlowCall} and {@link SlowCallBoard}.
 *
 * <p>Written by hand: the annotation processor depends on this module,
 * so this module cannot run it. Both travel in the ordinary object format, under their class names,
 * so this adds no tag and no opcode. Found through {@code META-INF/services} by
 * {@link BinaryRegistry#init()} on both tiers.</p>
 *
 * @since 0.11.0
 */
public final class SlowCallRegistrar implements BinaryRegistrar {

    @Override
    public void registerAll() {
        BinaryRegistry.register("com.zeroz4j.api.SlowCall", SlowCall<Object>::new,
                new BinarySerializerDelegate<SlowCall<Object>>() {
                    @Override
                    public void write(SlowCall<Object> call, GrowableBuffer buffer,
                                      ObjectMapper mapper) {
                        BinarySerializer.writeString(buffer, call.key());
                        BinarySerializer.writeString(buffer, call.id());
                    }

                    @Override
                    public void read(SlowCall<Object> call, ByteBuffer buffer,
                                     ObjectMapper mapper) {
                        String key = BinarySerializer.readString(buffer);
                        call.assign(key, BinarySerializer.readString(buffer));
                    }
                });
        BinaryRegistry.register("com.zeroz4j.api.SlowCallBoard", SlowCallBoard::new,
                new BinarySerializerDelegate<SlowCallBoard>() {
                    @Override
                    public void write(SlowCallBoard board, GrowableBuffer buffer,
                                      ObjectMapper mapper) {
                        buffer.putInt(board.entries().size());
                        for (SlowCallBoard.Entry entry : board.entries()) {
                            BinarySerializer.writeString(buffer, entry.id());
                            BinarySerializer.writeString(buffer, entry.key());
                            BinarySerializer.writeString(buffer, entry.status().name());
                            buffer.putInt(entry.percent());
                            BinarySerializer.writeString(buffer, entry.progressText());
                            BinarySerializer.writeString(buffer, entry.message());
                        }
                    }

                    @Override
                    public void read(SlowCallBoard board, ByteBuffer buffer, ObjectMapper mapper) {
                        int count = buffer.getInt();
                        List<SlowCallBoard.Entry> entries = new ArrayList<>();
                        for (int i = 0; i < count; i++) {
                            String id = BinarySerializer.readString(buffer);
                            String key = BinarySerializer.readString(buffer);
                            SlowCallStatus status =
                                    SlowCallStatus.valueOf(BinarySerializer.readString(buffer));
                            int percent = buffer.getInt();
                            String text = BinarySerializer.readString(buffer);
                            String message = BinarySerializer.readString(buffer);
                            entries.add(new SlowCallBoard.Entry(id, key, status, percent, text,
                                    message));
                        }
                        board.replaceEntries(entries);
                    }
                });
    }
}
