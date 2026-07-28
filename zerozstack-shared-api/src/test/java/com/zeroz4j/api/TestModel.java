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

public class TestModel implements BinaryPackable {
    private String field;
    private int number;

    public TestModel() {
    }

    public TestModel(String field, int number) {
        this.field = field;
        this.number = number;
    }

    public String getField() {
        return field;
    }

    public int getNumber() {
        return number;
    }

    @Override
    public void writeToBuffer(GrowableBuffer buffer, ObjectMapper mapper) {
        BinarySerializer.writeString(buffer, field);
        buffer.putInt(number);
    }

    @Override
    public void readFromBuffer(ByteBuffer buffer, ObjectMapper mapper) {
        this.field = BinarySerializer.readString(buffer);
        this.number = buffer.getInt();
    }
}
