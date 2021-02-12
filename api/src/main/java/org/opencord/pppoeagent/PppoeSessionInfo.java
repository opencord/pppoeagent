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
import org.onlab.packet.PPPoED;
import org.onosproject.net.ConnectPoint;
import org.opencord.sadis.SubscriberAndDeviceInformation;

/**
 * PppoeAgent session information.
 */
public class PppoeSessionInfo {
    private ConnectPoint clientCp;
    private ConnectPoint serverCp;
    private Byte packetCode;
    private short sessionId;
    SubscriberAndDeviceInformation subscriber;
    MacAddress clientMac;

    /**
     * Creates a new PPPoE session information object.
     *
     * @param clientCp   PPPoE client connect-point.
     * @param serverCp   PPPoE server connect-point.
     * @param packetCode The packet code of last PPPOED received message.
     * @param sessionId  session-id value.
     * @param subscriber Sadis object of PPPoE client.
     * @param clientMac  MAC address of PPPoE client.
     */
    public PppoeSessionInfo(ConnectPoint clientCp, ConnectPoint serverCp, Byte packetCode, short sessionId,
                            SubscriberAndDeviceInformation subscriber, MacAddress clientMac) {
        this.clientCp = clientCp;
        this.serverCp = serverCp;
        this.packetCode = packetCode;
        this.sessionId = sessionId;
        this.subscriber = subscriber;
        this.clientMac = clientMac;
    }

    /**
     * Creates an empty PPPoE session information object.
     */
    public PppoeSessionInfo() {
    }

    /**
     * Gets the PPPoE client connect-point.
     *
     * @return client connect-point.
     */
    public ConnectPoint getClientCp() {
        return clientCp;
    }

    /**
     * Sets the PPPoE client connect-point.
     *
     * @param clientCp client connect-point.
     */
    public void setClientCp(ConnectPoint clientCp) {
        this.clientCp = clientCp;
    }

    /**
     * Gets the PPPoE server connect-point.
     *
     * @return server connect-point.
     */
    public ConnectPoint getServerCp() {
        return serverCp;
    }

    /**
     * Sets the PPPoE server connect-point.
     *
     * @param serverCp server connect-point.
     */
    public void setServerCp(ConnectPoint serverCp) {
        this.serverCp = serverCp;
    }

    /**
     * Gets the PPPoE client SADIS object.
     *
     * @return client SADIS object.
     */
    public SubscriberAndDeviceInformation getSubscriber() {
        return subscriber;
    }

    /**
     * Sets the PPPoE client SADIS object.
     *
     * @param subscriber client SADIS object.
     */
    public void setSubscriber(SubscriberAndDeviceInformation subscriber) {
        this.subscriber = subscriber;
    }

    /**
     * Gets the PPPoE client MAC address.
     *
     * @return client MAC address.
     */
    public MacAddress getClientMac() {
        return clientMac;
    }

    /**
     * Sets the PPPoE client MAC address.
     *
     * @param clientMac MAC address.
     */
    public void setClientMac(MacAddress clientMac) {
        this.clientMac = clientMac;
    }

    /**
     * Gets the PPPoE session-id.
     *
     * @return PPPoE session-id.
     */
    public short getSessionId() {
        return sessionId;
    }

    /**
     * Sets the PPPoE session-id.
     *
     * @param sessionId PPPoE session-id.
     */
    public void setSessionId(short sessionId) {
        this.sessionId = sessionId;
    }

    /**
     * Gets the PPPoED packet code of the last received message.
     *
     * @return last received PPPoED code.
     */
    public Byte getPacketCode() {
        return packetCode;
    }

    /**
     * Sets the PPPoED packet code from the last received message.
     *
     * @param packetCode PPPoED packet code.
     */
    public void setPacketCode(byte packetCode) {
        this.packetCode = packetCode;
    }

    /**
     * Gets a string to represent the current session state based on the last received packet code.
     * This function uses conveniently the name of some PPPoEAgentEvent types to represent the state.
     *
     * @return PPPOE session state.
     */
    public String getCurrentState() {
        PPPoED.Type lastReceivedPkt = PPPoED.Type.getTypeByValue(this.packetCode);
        switch (lastReceivedPkt) {
            case PADI:
                return PppoeAgentEvent.Type.START.name();
            case PADR:
            case PADO:
                return PppoeAgentEvent.Type.NEGOTIATION.name();
            case PADS:
                return PppoeAgentEvent.Type.SESSION_ESTABLISHED.name();
            case PADT:
                // This case might never happen (entry is being removed on PADT messages).
                return PppoeAgentEvent.Type.TERMINATE.name();
            default:
                return PppoeAgentEvent.Type.UNKNOWN.name();
        }
    }

    @Override
    public String toString() {
        return "PppoeSessionInfo{" +
                "clientCp=" + clientCp +
                ", serverCp=" + serverCp +
                ", packetCode=" + packetCode +
                ", sessionId=" + sessionId +
                ", subscriber=" + subscriber +
                ", clientMac=" + clientMac +
                '}';
    }
}

