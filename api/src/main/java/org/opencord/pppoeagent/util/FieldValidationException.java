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

/**
 * Exception meant to be thrown when circuit-id builder builds a field which the result
 * is not valid according to the validation criteria.
 */
public class FieldValidationException extends Exception {
    public FieldValidationException(CircuitIdField field, String value) {
        super(String.format("Failed to build the circuit ID. %s field is not valid: %s",
                field.getFieldName().name(), value));
    }


}