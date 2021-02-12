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

package org.opencord.pppoeagent;

import org.onlab.packet.MacAddress;
import org.onosproject.event.AbstractEvent;
import org.onosproject.net.ConnectPoint;
/**
 * PppoeAgent event.
 */
public class PppoeAgentEvent extends AbstractEvent<PppoeAgentEvent.Type, PppoeSessionInfo> {
    private final ConnectPoint connectPoint;
    private final MacAddress subscriberMacAddress;
    private final String counterName;
    private final Long counterValue;
    private final String subscriberId;

    // Session terminates may have an error tag with a 'reason' message, this field is meant to track that info.
    private final String reason;

    public static final String GLOBAL_COUNTER = "global";

    /**
     * Type of the event.
     */
    public enum Type {
        /**
         * PPPoE discovery negotiation started.
         */
        START,

        /**
         * PPPoE server is responding to client packets - session is under negotiation.
         */
        NEGOTIATION,

        /**
         * PPPoE discovery negotiation is complete, session is established.
         */
        SESSION_ESTABLISHED,

        /**
         * Client or server event to indicate end of a session.
         */
        TERMINATE,

        /**
         * PPPoE stats update.
         */
        STATS_UPDATE,

        /**
         * Circuit-id mismatch.
         */
        INVALID_CID,

        /**
         * Default value for unknown event.
         */
        UNKNOWN
    }


    /**
     * Creates a new PPPoE counters event.
     *
     * @param type type of the event
     * @param sessionInfo session info
     * @param counterName name of specific counter
     * @param counterValue value of specific counter
     * @param subscriberMacAddress the subscriber MAC address information
     * @param subscriberId id of subscriber
     */
    public PppoeAgentEvent(Type type, PppoeSessionInfo sessionInfo, String counterName, Long counterValue,
                           MacAddress subscriberMacAddress, String subscriberId) {
        super(type, sessionInfo);
        this.counterName = counterName;
        this.counterValue = counterValue;
        this.subscriberMacAddress = subscriberMacAddress;
        this.subscriberId = subscriberId;
        this.connectPoint = null;
        this.reason = null;
    }

    /**
     * Creates a new generic PPPoE event.
     *
     * @param type type of the event
     * @param sessionInfo session info
     * @param connectPoint connect point the client is on
     * @param subscriberMacAddress the subscriber MAC address information
     */
    public PppoeAgentEvent(Type type, PppoeSessionInfo sessionInfo, ConnectPoint connectPoint,
                           MacAddress subscriberMacAddress) {
        super(type, sessionInfo);
        this.connectPoint = connectPoint;
        this.subscriberMacAddress = subscriberMacAddress;
        this.counterName = null;
        this.counterValue = null;
        this.subscriberId = null;
        this.reason = null;
    }

    /**
     * Creates a new PPPOE event with a reason (PADT packets may have a 'reason' field).
     *
     * @param type type of the event
     * @param sessionInfo session info
     * @param connectPoint connect point the client is on
     * @param subscriberMacAddress the subscriber MAC address information
     * @param reason events such TERMINATE may have reason field
     */
    public PppoeAgentEvent(Type type, PppoeSessionInfo sessionInfo, ConnectPoint connectPoint,
                           MacAddress subscriberMacAddress, String reason) {
        super(type, sessionInfo);
        this.connectPoint = connectPoint;
        this.subscriberMacAddress = subscriberMacAddress;
        this.reason = reason;
        this.counterName = null;
        this.counterValue = null;
        this.subscriberId = null;
    }


    /**
     * Gets the PPPoE client connect point.
     *
     * @return connect point
     */
    public ConnectPoint getConnectPoint() {
        return connectPoint;
    }

    /**
     * Gets the subscriber MAC address.
     *
     * @return the MAC address from subscriber
     */
    public MacAddress getSubscriberMacAddress() {
        return subscriberMacAddress;
    }

    /**
     * Gets the event reason.
     *
     * @return event reason.
     */
    public String getReason() {
        return reason;
    }

    /**
     * Gets the counter name.
     *
     * @return counter name.
     */
    public String getCounterName() {
        return counterName;
    }

    /**
     * Gets the counter value.
     *
     * @return counter value.
     */
    public Long getCounterValue() {
        return counterValue;
    }

    /**
     * Gets the subscriber identifier information.
     *
     * @return the Id from subscriber
     */
    public String getSubscriberId() {
        return subscriberId;
    }

    @Override
    public String toString() {
        return "PppoeAgentEvent{" +
                "connectPoint=" + connectPoint +
                ", subscriberMacAddress=" + subscriberMacAddress +
                ", reason='" + reason + '\'' +
                ", counterName=" + counterName +
                ", counterValue=" + counterValue +
                ", subscriberId='" + subscriberId + '\'' +
                '}';
    }
}
