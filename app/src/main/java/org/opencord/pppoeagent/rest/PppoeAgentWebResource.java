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
package org.opencord.pppoeagent.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Map;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.onlab.packet.MacAddress;
import org.onlab.packet.PPPoED;
import org.onlab.util.ItemNotFoundException;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.Port;
import org.onosproject.rest.AbstractWebResource;

import org.opencord.pppoeagent.impl.PppoeAgentCounterNames;
import org.opencord.pppoeagent.impl.PppoeAgentCountersStore;
import org.opencord.pppoeagent.impl.PppoeAgentCountersIdentifier;
import org.opencord.pppoeagent.impl.PppoeAgentStatistics;
import org.opencord.pppoeagent.PppoeAgentEvent;
import org.opencord.pppoeagent.PppoeAgentService;
import org.opencord.pppoeagent.PppoeSessionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static javax.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;

/**
 * PppoeAgent web resource.
 */
@Path("pppoeagent-app")
public class PppoeAgentWebResource extends AbstractWebResource {
    private final ObjectNode root = mapper().createObjectNode();
    private final ArrayNode node = root.putArray("entry");
    private static final String SESSION_NOT_FOUND = "Session not found";

    private final Logger log = LoggerFactory.getLogger(getClass());

    DeviceService deviceService = AbstractShellCommand.get(DeviceService.class);

    /**
     * Get session info object.
     *
     * @param mac Session MAC address
     *
     * @return 200 OK
     */
    @GET
    @Path("/session/{mac}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSubscriber(@PathParam("mac") String mac) {
        MacAddress macAddress = MacAddress.valueOf(mac);
        PppoeAgentService pppoeAgent = get(PppoeAgentService.class);
        PppoeSessionInfo entry = pppoeAgent.getSessionsMap().get(macAddress);
        if (entry == null) {
            throw new ItemNotFoundException(SESSION_NOT_FOUND);
        }

        try {
            node.add(encodePppoeSessionInfo(entry, macAddress));
            return ok(mapper().writeValueAsString(root)).build();
        } catch (IllegalArgumentException e) {
            log.error("Error while fetching PPPoE session info for MAC {} through REST API: {}", mac, e.getMessage());
            return Response.status(INTERNAL_SERVER_ERROR).build();
        } catch (JsonProcessingException e) {
            log.error("Error assembling JSON response for PPPoE session info request for MAC {} " +
                    "through REST API: {}", mac, e.getMessage());
            return Response.status(INTERNAL_SERVER_ERROR).build();
        }
    }

    /**
     * Get all session info objects.
     *
     * @return 200 OK
     */
    @GET
    @Path("/session")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getSubscribers() {
        try {
            PppoeAgentService pppoeAgent = get(PppoeAgentService.class);
            pppoeAgent.getSessionsMap().forEach((mac, entry) -> {
                node.add(encodePppoeSessionInfo(entry, mac));
            });

            return ok(mapper().writeValueAsString(root)).build();
        } catch (Exception e) {
            log.error("Error while fetching PPPoE sessions information through REST API: {}", e.getMessage());
            return Response.status(INTERNAL_SERVER_ERROR).build();
        }
    }

    private ObjectNode encodePppoeSessionInfo(PppoeSessionInfo entry, MacAddress macAddress) {
        ConnectPoint cp = entry.getClientCp();
        Port devicePort = deviceService.getPort(cp);
        String portLabel = "uni-" + ((cp.port().toLong() & 0xF) + 1);
        String subscriberId = devicePort != null ? devicePort.annotations().value(AnnotationKeys.PORT_NAME) :
                "UNKNOWN";

        return mapper().createObjectNode()
                .put("macAddress", macAddress.toString())
                .put("sessionId", entry.getSessionId())
                .put("currentState", entry.getCurrentState())
                .put("lastReceivedPacket", PPPoED.Type.getTypeByValue(entry.getPacketCode()).name())
                .put("deviceId", cp.deviceId().toString())
                .put("portNumber", cp.port().toString())
                .put("portLabel", portLabel)
                .put("subscriberId", subscriberId);
    }

    /**
     * Gets PPPoE Agent counters for global context.
     *
     * @return 200 OK
     */
    @GET
    @Path("/stats")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPppoeStats() {
        return getStats(PppoeAgentEvent.GLOBAL_COUNTER);
    }

    /**
     * Gets PPPoE Agent counters for specific subscriber.
     *
     * @param subscriberId Id of subscriber.
     *
     * @return 200 OK
     */
    @GET
    @Path("/stats/{subscriberId}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPppoeSubscriberStats(@PathParam("subscriberId") String subscriberId) {
        return getStats(subscriberId);
    }
    private Response getStats(String key) {
        PppoeAgentCountersStore pppoeCounters = get(PppoeAgentCountersStore.class);
        try {
            PppoeAgentStatistics pppoeStatistics = pppoeCounters.getCounters();
            JsonNode completeNode = buildPppoeCounterNodeObject(key, pppoeStatistics.counters());
            return ok(mapper().writeValueAsString(completeNode)).build();
        } catch (JsonProcessingException e) {
            log.error("Error while fetching PPPoE agent counter stats through REST API: {}", e.getMessage());
            return Response.status(INTERNAL_SERVER_ERROR).build();
        }
    }

    private JsonNode buildPppoeCounterNodeObject(String key, Map<PppoeAgentCountersIdentifier, Long> countersMap) {
        ObjectNode entryNode = mapper().createObjectNode();
        for (PppoeAgentCounterNames counterType : PppoeAgentCounterNames.SUPPORTED_COUNTERS) {
            Long value = countersMap.get(new PppoeAgentCountersIdentifier(key, counterType));
            if (value == null) {
                continue;
            }
            entryNode = entryNode.put(counterType.name(), String.valueOf(value));
        }
        return mapper().createObjectNode().set(key, entryNode);
    }
}