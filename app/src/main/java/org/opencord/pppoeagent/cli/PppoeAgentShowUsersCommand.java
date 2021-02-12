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

import org.opencord.pppoeagent.PppoeAgentService;
import org.opencord.pppoeagent.PppoeSessionInfo;
import org.apache.karaf.shell.api.action.Argument;
import org.apache.karaf.shell.api.action.Command;
import org.apache.karaf.shell.api.action.lifecycle.Service;
import org.onlab.packet.MacAddress;
import org.onlab.packet.PPPoED;
import org.onosproject.cli.AbstractShellCommand;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Port;
import org.onosproject.net.device.DeviceService;

/**
 *  Shows the PPPoE sessions/users learned by the agent.
 */
@Service
@Command(scope = "pppoe", name = "pppoe-users",
        description = "Shows the PPPoE users")
public class PppoeAgentShowUsersCommand extends AbstractShellCommand {

    @Argument(index = 0, name = "mac", description = "MAC address related to PPPoE session.",
            required = false, multiValued = false)
    private String macStr = null;

    @Override
    protected void doExecute() {
        MacAddress macAddress = null;
        if (macStr != null && !macStr.isEmpty()) {
            try {
                macAddress = MacAddress.valueOf(macStr);
            } catch (IllegalArgumentException e) {
                log.error(e.getMessage());
                return;
            }
        }

        DeviceService deviceService = AbstractShellCommand.get(DeviceService.class);
        PppoeAgentService pppoeAgentService = AbstractShellCommand.get(PppoeAgentService.class);

        if (macAddress != null) {
            PppoeSessionInfo singleInfo = pppoeAgentService.getSessionsMap().get(macAddress);
            if (singleInfo != null) {
                Port devicePort = deviceService.getPort(singleInfo.getClientCp());
                printPppoeInfo(macAddress, singleInfo, devicePort);
            } else {
                print("No session information found for provided MAC address %s", macAddress.toString());
            }
        } else {
            pppoeAgentService.getSessionsMap().forEach((mac, sessionInfo) -> {
                final Port devicePortFinal = deviceService.getPort(sessionInfo.getClientCp());
                printPppoeInfo(mac, sessionInfo, devicePortFinal);
            });
        }
    }

    private void printPppoeInfo(MacAddress macAddr, PppoeSessionInfo sessionInfo, Port devicePort) {
        PPPoED.Type lastReceivedPkt = PPPoED.Type.getTypeByValue(sessionInfo.getPacketCode());
        ConnectPoint cp = sessionInfo.getClientCp();
        String subscriberId = devicePort != null ? devicePort.annotations().value(AnnotationKeys.PORT_NAME) :
                "UNKNOWN";

        print("MacAddress=%s,SessionId=%s,CurrentState=%s,LastReceivedPacket=%s,DeviceId=%s,PortNumber=%s," +
                        "SubscriberId=%s",
                macAddr.toString(), String.valueOf(sessionInfo.getSessionId()),
                sessionInfo.getCurrentState(), lastReceivedPkt.name(),
                cp.deviceId().toString(), cp.port().toString(), subscriberId);
    }
}
