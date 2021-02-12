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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.google.common.collect.ImmutableSet;
import org.onosproject.core.ApplicationId;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.config.Config;

import java.util.HashSet;
import java.util.Set;

public class PppoeAgentConfig extends Config<ApplicationId> {
    private static final String PPPOE_CONNECT_POINTS = "pppoeServerConnectPoints";
    private static final String USE_OLT_ULPORT_FOR_PKT_INOUT = "useOltUplinkForServerPktInOut";

    protected static final Boolean DEFAULT_USE_OLT_ULPORT_FOR_PKT_INOUT = true;

    @Override
    public boolean isValid() {
        return hasOnlyFields(PPPOE_CONNECT_POINTS, USE_OLT_ULPORT_FOR_PKT_INOUT);
    }

    /**
     * Returns whether the app would use the uplink port of OLT for sending/receving
     * messages to/from the server.
     *
     * @return true if OLT uplink port is to be used, false otherwise
     */
    public boolean getUseOltUplinkForServerPktInOut() {
        if (object == null) {
            return DEFAULT_USE_OLT_ULPORT_FOR_PKT_INOUT;
        }
        if (!object.has(USE_OLT_ULPORT_FOR_PKT_INOUT)) {
            return DEFAULT_USE_OLT_ULPORT_FOR_PKT_INOUT;
        }
        return object.path(USE_OLT_ULPORT_FOR_PKT_INOUT).asBoolean();
    }

    /**
     * Returns the pppoe server connect points.
     *
     * @return pppoe server connect points
     */
    public Set<ConnectPoint> getPppoeServerConnectPoint() {
        if (object == null) {
            return new HashSet<ConnectPoint>();
        }

        if (!object.has(PPPOE_CONNECT_POINTS)) {
            return ImmutableSet.of();
        }

        ImmutableSet.Builder<ConnectPoint> builder = ImmutableSet.builder();
        ArrayNode arrayNode = (ArrayNode) object.path(PPPOE_CONNECT_POINTS);
        for (JsonNode jsonNode : arrayNode) {
            String portName = jsonNode.asText(null);
            if (portName == null) {
                return null;
            }
            try {
                builder.add(ConnectPoint.deviceConnectPoint(portName));
            } catch (IllegalArgumentException e) {
                return null;
            }
        }
        return builder.build();
    }


}
