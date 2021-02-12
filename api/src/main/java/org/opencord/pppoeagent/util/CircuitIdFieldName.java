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
 * Represents the field names for circuit-id.
 */
public enum CircuitIdFieldName {
    AcessNodeIdentifier(1),
    Slot(2),
    Port(3),
    OnuSerialNumber(4),
    UniPortNumber(5),
    SvID(6),
    CvID(7),
    NetworkTechnology(8);
    private final int value;
    CircuitIdFieldName(int value) {
        this.value = value;
    }
}