/*
 * Copyright 2021-present Open Networking Foundation
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
package org.opencord.pppoeagent.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Configuration options for circuit-id builder.
 */
public class CircuitIdConfig {
    private String separator;
    private ArrayList<CircuitIdField> fieldList;
    private final Logger log = LoggerFactory.getLogger(getClass());

    // This list defines the default configuration for circuit-id.
    // Circuit-id will follow the same order of this list.
    private final ArrayList<CircuitIdFieldName> defaultFieldSelection = new ArrayList<>(
            Arrays.asList(CircuitIdFieldName.AcessNodeIdentifier,
                    CircuitIdFieldName.NetworkTechnology,
                    CircuitIdFieldName.Slot,
                    CircuitIdFieldName.Port,
                    CircuitIdFieldName.OnuSerialNumber,
                    CircuitIdFieldName.UniPortNumber,
                    CircuitIdFieldName.SvID,
                    CircuitIdFieldName.CvID)
    );

    /**
     * Gets the default separator.
     *
     * @return separator.
     */
    public String getSeparator() {
        return separator;
    }

    /**
     * Sets the default separator.
     *
     * @param  value separator.
     * @return circuit-id configuration object.
     */
    public CircuitIdConfig setSeparator(String value) {
        this.separator = value;
        return this;
    }

    /**
     * Gets the circuit-id field list.
     *
     * @return circuit-id field list.
     */
    public ArrayList<CircuitIdField> getFieldList() {
        return fieldList;
    }

    private CircuitIdConfig setFieldList(ArrayList<CircuitIdField> value) {
        this.fieldList = value;
        return this;
    }

    /**
     * Sets the field list considering a list of supported fields.
     *
     * @param fieldSelection   desired field list.
     * @param availableFields  available field list.
     * @return circuit-id config object.
     */
    public CircuitIdConfig setFieldList(ArrayList<CircuitIdFieldName> fieldSelection,
                                        ArrayList<CircuitIdField> availableFields) {
        // Find out the fields based on the field selection for this config.
        ArrayList<CircuitIdField> value = fieldSelection
                .stream()
                .map(fieldName -> availableFields
                        .stream()
                        .filter(field -> field.getFieldName()
                                .equals(fieldName))
                        .findFirst()
                        .orElse(null))
                .collect(Collectors.toCollection(ArrayList::new));

        boolean foundAllFields = value.stream().noneMatch(Objects::isNull);

        // Ignores if it found all fields.
        if (!foundAllFields) {
            // Otherwise, it log and remove null entries.
            log.warn("Some desired fields are not available.");
            value.removeAll(Collections.singleton(null));
        }

        setFieldList(value);
        return this;
    }

    /**
     * Sets the field list with the default values considering a list of available fields.
     *
     * @param availableFields available field list.
     * @return circuit-id config object.
     */
    public CircuitIdConfig setFieldListWithDefault(ArrayList<CircuitIdField> availableFields) {
        return setFieldList(this.getDefaultFieldSelection(), availableFields);
    }

    private ArrayList<CircuitIdFieldName> getDefaultFieldSelection() {
        return defaultFieldSelection;
    }
}