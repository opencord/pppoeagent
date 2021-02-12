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

package org.opencord.pppoeagent.cli;

import org.opencord.pppoeagent.PppoeAgentEvent;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.Option;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onosproject.cli.AbstractShellCommand;
import java.util.Collections;
import java.util.Map;
import org.opencord.pppoeagent.impl.PppoeAgentCountersIdentifier;
import org.opencord.pppoeagent.impl.PppoeAgentCountersStore;
import org.opencord.pppoeagent.impl.PppoeAgentCounterNames;

/**
 * Display/Reset the PPPoE Agent application statistics.
 */
@Service
@Command(scope = "pppoe", name = "pppoeagent-stats",
        description = "Display or Reset the PPPoE Agent application statistics.")
public class PppoeAgentStatsCommand extends AbstractShellCommand {
    private static final String CONFIRM_PHRASE = "please";

    @Option(name = "-r", aliases = "--reset", description = "Resets a specific counter.\n",
            required = false, multiValued = false)
    PppoeAgentCounterNames reset = null;

    @Option(name = "-R", aliases = "--reset-all", description = "Resets all counters.\n",
            required = false, multiValued = false)
    boolean resetAll = false;

    @Option(name = "-s", aliases = "--subscriberId", description = "Subscriber Id.\n",
            required = false, multiValued = false)
    String subscriberId = null;

    @Option(name = "-p", aliases = "--please", description = "Confirmation phrase.",
            required = false, multiValued = false)
    String please = null;

    @Argument(index = 0, name = "counter",
            description = "The counter to display. In case not specified, all counters will be displayed.",
            required = false, multiValued = false)
    PppoeAgentCounterNames counter = null;

    @Override
    protected void doExecute() {
        PppoeAgentCountersStore pppoeCounters = AbstractShellCommand.get(PppoeAgentCountersStore.class);

        if ((subscriberId == null) || (subscriberId.equals("global"))) {
            // All subscriber Ids
            subscriberId = PppoeAgentEvent.GLOBAL_COUNTER;
        }

        if (resetAll || reset != null) {
            if (please == null || !please.equals(CONFIRM_PHRASE)) {
                print("WARNING: Be aware that you are going to reset the counters. " +
                        "Enter confirmation phrase to continue.");
                return;
            }
            if (resetAll) {
                // Reset all counters.
                pppoeCounters.resetCounters(subscriberId);
            } else {
                // Reset the specified counter.
                pppoeCounters.setCounter(subscriberId, reset, (long) 0);
            }
        } else {
            Map<PppoeAgentCountersIdentifier, Long> countersMap = pppoeCounters.getCounters().counters();
            if (countersMap.size() > 0) {
                if (counter == null) {
                    String jsonString = "";
                    if (outputJson()) {
                        jsonString = String.format("{\"%s\":{", pppoeCounters.NAME);
                    } else {
                        print("%s [%s] :", pppoeCounters.NAME, subscriberId);
                    }
                    PppoeAgentCounterNames[] counters = PppoeAgentCounterNames.values();
                    for (int i = 0; i < counters.length; i++) {
                        PppoeAgentCounterNames counterType = counters[i];
                        Long value = countersMap.get(new PppoeAgentCountersIdentifier(subscriberId, counterType));
                        if (value == null) {
                            value = 0L;
                        }
                        if (outputJson()) {
                            jsonString += String.format("\"%s\":%d", counterType, value);
                            if (i < counters.length - 1) {
                                jsonString += ",";
                            }
                        } else {
                            printCounter(counterType, value);
                        }
                    }
                    if (outputJson()) {
                        jsonString += "}}";
                        print("%s", jsonString);
                    }
                } else {
                    // Show only the specified counter
                    Long value = countersMap.get(new PppoeAgentCountersIdentifier(subscriberId, counter));
                    if (value == null) {
                        value = 0L;
                    }
                    if (outputJson()) {
                        print("{\"%s\":%d}", counter, value);
                    } else {
                        printCounter(counter, value);
                    }
                }
            } else {
                print("No PPPoE Agent Counters were created yet for counter class [%s]",
                        PppoeAgentEvent.GLOBAL_COUNTER);
            }
        }
    }

    void printCounter(PppoeAgentCounterNames c, long value) {
        // print in non-JSON format
        print("  %s %s %-4d", c,
                String.join("", Collections.nCopies(50 - c.toString().length(), ".")), value);
    }
}