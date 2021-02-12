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

package org.opencord.pppoeagent.impl;

/**
 * Constants for default values of configurable properties.
 */
public final class OsgiPropertyConstants {

    private OsgiPropertyConstants() {
    }

    public static final String ENABLE_CIRCUIT_ID_VALIDATION = "enableCircuitIdValidation";
    public static final boolean ENABLE_CIRCUIT_ID_VALIDATION_DEFAULT = true;

    public static final String PPPOE_COUNTERS_TOPIC = "pppoeCountersTopic";
    public static final String PPPOE_COUNTERS_TOPIC_DEFAULT = "onos.pppoe.stats.kpis";

    public static final String PUBLISH_COUNTERS_RATE = "publishCountersRate";
    public static final int PUBLISH_COUNTERS_RATE_DEFAULT = 10;

    public static final String PPPOE_MAX_MTU = "pppoeMaxMtu";
    public static final int PPPOE_MAX_MTU_DEFAULT = 1500;

    public static final String PACKET_PROCESSOR_THREADS = "packetProcessorThreads";
    public static final int PACKET_PROCESSOR_THREADS_DEFAULT = 10;

    public static final String SYNC_COUNTERS_RATE = "syncCountersRate";
    public static final int SYNC_COUNTERS_RATE_DEFAULT = 5;
}
