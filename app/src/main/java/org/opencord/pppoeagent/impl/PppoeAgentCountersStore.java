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

import org.onosproject.store.Store;
import org.opencord.pppoeagent.PppoeAgentEvent;
import org.opencord.pppoeagent.PppoeAgentStoreDelegate;

/**
 * Represents a stored Pppoe Agent Counters. A counter entry is defined by the pair [counterClass, counterType],
 * where counterClass can be maybe global or subscriber ID and counterType is the pppoe counter.
 */
public interface PppoeAgentCountersStore extends Store<PppoeAgentEvent, PppoeAgentStoreDelegate> {

    String NAME = "PPPOE_Agent_stats";

    /**
     * Creates or updates PPPOE Agent counter.
     *
     * @param counterClass class of counters (global, per subscriber).
     * @param counterType name of counter
     */
    void incrementCounter(String counterClass, PppoeAgentCounterNames counterType);

    /**
     * Sets the value of a PPPOE Agent counter.
     *
     * @param counterClass class of counters (global, per subscriber).
     * @param counterType name of counter
     * @param value The value of the counter
     */
    void setCounter(String counterClass, PppoeAgentCounterNames counterType, Long value);

    /**
     * Gets the current PPPoE Agent counter values.
     *
     * @return PPPoE Agent counter values
     */
    PppoeAgentStatistics getCounters();

    /**
     * Resets counter values for a given counter class.
     *
     * @param counterClass class of counters (global, per subscriber).
     */
    void resetCounters(String counterClass);
}