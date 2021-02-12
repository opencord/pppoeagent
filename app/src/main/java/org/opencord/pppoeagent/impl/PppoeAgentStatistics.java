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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot of PPPoE Agent statistics.
 */
public class PppoeAgentStatistics {
    private final ImmutableMap<PppoeAgentCountersIdentifier, Long> counters;
    private PppoeAgentStatistics(ImmutableMap<PppoeAgentCountersIdentifier, Long> counters) {
        this.counters = counters;
    }
    /**
     * Creates a new empty statistics instance.
     */
    public PppoeAgentStatistics() {
        counters = ImmutableMap.of();
    }
    /**
     * Gets the value of the counter with the given ID. Defaults to 0 if counter is not present.
     *
     * @param id counter ID
     * @return counter value
     */
    public long get(PppoeAgentCountersIdentifier id) {
        return counters.getOrDefault(id, 0L);
    }
    /**
     * Gets the map of counters.
     *
     * @return map of counters
     */
    public Map<PppoeAgentCountersIdentifier, Long> counters() {
        return counters;
    }
    /**
     * Creates a new statistics instance with the given counter values.
     *
     * @param counters counters
     * @return statistics
     */
    public static PppoeAgentStatistics withCounters(Map<PppoeAgentCountersIdentifier, Long> counters) {
        ImmutableMap.Builder<PppoeAgentCountersIdentifier, Long> builder = ImmutableMap.builder();
        counters.forEach(builder::put);
        return new PppoeAgentStatistics(builder.build());
    }
    /**
     * Adds the given statistics instance to this one (sums the common counters) and returns
     * a new instance containing the result.
     *
     * @param other other instance
     * @return result
     */
    public PppoeAgentStatistics add(PppoeAgentStatistics other) {
        ImmutableMap.Builder<PppoeAgentCountersIdentifier, Long> builder = ImmutableMap.builder();
        Set<PppoeAgentCountersIdentifier> keys = Sets.newHashSet(other.counters.keySet());
        counters.forEach((id, value) -> {
            builder.put(id, value + other.counters.getOrDefault(id, 0L));
            keys.remove(id);
        });
        keys.forEach(i -> builder.put(i, other.counters.get(i)));
        return new PppoeAgentStatistics(builder.build());
    }
    @Override
    public String toString() {
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this.getClass());
        counters.forEach((id, v) -> helper.add(id.toString(), v));
        return helper.toString();
    }
}