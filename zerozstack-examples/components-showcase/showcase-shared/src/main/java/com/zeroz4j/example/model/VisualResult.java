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
package com.zeroz4j.example.model;


import java.util.ArrayList;
import java.util.List;
import com.zeroz4j.api.DataModel;

@DataModel
public class VisualResult {
    public int rowCount;
    public int columnCount;
    public List<String> columnNames;
    public List<String> columnTypes;
    public List<Object> values;

    public VisualResult() {
    }

    public VisualResult(int rowCount, int columnCount, List<String> columnNames, List<String> columnTypes, List<Object> values) {
        this.rowCount = rowCount;
        this.columnCount = columnCount;
        this.columnNames = columnNames;
        this.columnTypes = columnTypes;
        this.values = values;
    }

    public int getRowCount() { return rowCount; }
    public int getColumnCount() { return columnCount; }
    public List<String> getColumnNames() { return columnNames; }
    public List<String> getColumnTypes() { return columnTypes; }
    public List<Object> getValues() { return values; }


}

