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

import org.opencord.pppoeagent.PppoeAgentEvent;

import java.util.Objects;

/**
 * Represents PPPoED agent counters identifier.
 */
public final class PppoeAgentCountersIdentifier {
    final String counterClassKey;
    final Enum<PppoeAgentCounterNames> counterTypeKey;

    /**
     * Creates a default global counter identifier for a given counterType.
     *
     * @param counterTypeKey Identifies the supported type of pppoe agent counters
     */
    public PppoeAgentCountersIdentifier(PppoeAgentCounterNames counterTypeKey) {
        this.counterClassKey = PppoeAgentEvent.GLOBAL_COUNTER;
        this.counterTypeKey = counterTypeKey;
    }

    /**
     * Creates a counter identifier. A counter is defined by the key pair [counterClass, counterType],
     * where counterClass can be global or the subscriber ID and counterType is the supported pppoe counter.
     *
     * @param counterClassKey Identifies which class the counter is assigned (global or per subscriber)
     * @param counterTypeKey Identifies the supported type of pppoed relay counters
     */
    public PppoeAgentCountersIdentifier(String counterClassKey, PppoeAgentCounterNames counterTypeKey) {
        this.counterClassKey = counterClassKey;
        this.counterTypeKey = counterTypeKey;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof PppoeAgentCountersIdentifier) {
            final PppoeAgentCountersIdentifier other = (PppoeAgentCountersIdentifier) obj;
            return Objects.equals(this.counterClassKey, other.counterClassKey)
                    && Objects.equals(this.counterTypeKey, other.counterTypeKey);
        }

        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(counterClassKey, counterTypeKey);
    }

    @Override
    public String toString() {
        return "PppoeAgentCountersIdentifier{" +
                "counterClassKey='" + counterClassKey + '\'' +
                ", counterTypeKey=" + counterTypeKey +
                '}';
    }
}