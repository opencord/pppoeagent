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

import org.apache.commons.lang3.Range;

import java.util.Arrays;

/**
 *  Circuit-id field abstract model.
 *  This is a model that shouldn't be instantiated, for new fields please create a class that extends this one.
 */
public abstract class CircuitIdField {
    // Definitions to perform general validation.
    private Integer maxLength;
    private boolean isRangeLike;
    private Range<Long> range;
    private CircuitIdFieldName fieldName;
    private boolean isNumber;

    // This is used to avoid conflicts in string values,
    // Example: when a field value contains the same char as the separator.
    private String[] exclusiveExpressions;

    CircuitIdFieldName getFieldName() {
        return this.fieldName;
    }

    CircuitIdField setFieldName(CircuitIdFieldName value) {
        this.fieldName = value;
        return this;
    }

    Integer getMaxLength() {
        return this.maxLength;
    }

    CircuitIdField setMaxLength(int value) {
        this.maxLength = value;
        return this;
    }

    Range<Long> getRange() {
        return isRangeLike ? range : null;
    }

    CircuitIdField setRange(Range<Long> value) {
        this.isRangeLike = value != null;
        this.range = value;
        return this;
    }

    CircuitIdField setIsNumber(boolean value) {
        this.isNumber = value;
        return this;
    }

    CircuitIdField setExclusiveExpressions(String[] value) {
        this.exclusiveExpressions = value;
        return this;
    }

    // It's recommended to call this method after building the field value, this will
    // perform general validations based on the definitions of the field (range, maxLength, isNumber, (...))
    boolean isValid(String value) {
        // This is the flag is the output of the assertions.
        boolean challenge = true;

        if (value == null) {
            return false;
        }

        // This code block is only in the case we're validating a number:
        long longValue = 0L;
        if (isNumber) {
            try {
                // We try to parse the value.
                longValue = Long.parseLong(value);
                // If there's a range defined.
                if (isRangeLike) {
                    // We verify if the value is at the defined range.
                    challenge &= range.contains(longValue);
                }
            } catch (NumberFormatException e) {
                // If something goes wrong in the conversion, it means the number is invalid.
                return false;
            }
        } else if (exclusiveExpressions != null) {
            // When exclusive expressions are defined, it verifies if the value contains one of the expressions.
            // If this is the case, it's an invalid value.
            challenge = Arrays.stream(exclusiveExpressions)
                    .noneMatch(exp -> value.contains(exp));
        }

        // At the end, it verifies if the value respects the max length. But only if the max length is defined.
        if (maxLength != null) {
            challenge &= value.length() <= maxLength;
        }
        return challenge;
    }

    // Each field should implement its own build method.
    String build() throws MissingParameterException, FieldValidationException {
        return "";
    }
}