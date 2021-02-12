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

import com.google.common.util.concurrent.MoreExecutors;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.onlab.junit.TestUtils;
import org.onlab.osgi.ComponentContextAdapter;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.PPPoED;
import org.onlab.packet.PPPoEDTag;
import org.onlab.packet.VlanId;

import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cluster.ClusterServiceAdapter;
import org.onosproject.cluster.LeadershipServiceAdapter;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.store.service.ConsistentMap;
import org.onosproject.store.service.TestStorageService;

import org.opencord.pppoeagent.PppoeAgentEvent;
import org.opencord.pppoeagent.PPPoEDVendorSpecificTag;
import org.opencord.pppoeagent.PppoeSessionInfo;
import org.opencord.sadis.SubscriberAndDeviceInformation;

import static org.easymock.EasyMock.createMock;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class PppoeAgentTest extends PppoeAgentTestBase {
    private PppoeAgent pppoeAgent;
    private SimplePppoeAgentCountersStore store;

    ComponentConfigService mockConfigService =
            createMock(ComponentConfigService.class);

    /**
     * Sets up the services required by the PPPoE agent app.
     */
    @Before
    public void setUp() {
        pppoeAgent = new PppoeAgent();
        pppoeAgent.cfgService = new MockNetworkConfigRegistry();
        pppoeAgent.coreService = new MockCoreServiceAdapter();
        pppoeAgent.packetService = new MockPacketService();
        pppoeAgent.componentConfigService = mockConfigService;
        pppoeAgent.deviceService = new MockDeviceService();
        pppoeAgent.sadisService = new MockSadisService();
        pppoeAgent.subsService = pppoeAgent.sadisService.getSubscriberInfoService();
        pppoeAgent.mastershipService = new MockMastershipService();
        pppoeAgent.storageService = new TestStorageService();
        pppoeAgent.pppoeAgentCounters = this.store;

        store = new SimplePppoeAgentCountersStore();
        store.storageService = new TestStorageService();
        store.clusterService = new ClusterServiceAdapter();
        store.leadershipService = new LeadershipServiceAdapter();
        store.clusterCommunicationService = new MockClusterCommunicationService<>();
        store.componentConfigService = mockConfigService;
        TestUtils.setField(store, "eventDispatcher", new MockEventDispatcher());
        store.activate(new MockComponentContext());

        pppoeAgent.pppoeAgentCounters = this.store;

        TestUtils.setField(pppoeAgent, "eventDispatcher", new MockEventDispatcher());
        TestUtils.setField(pppoeAgent, "packetProcessorExecutor", MoreExecutors.newDirectExecutorService());

        pppoeAgent.activate(new ComponentContextAdapter());
    }

    /**
     * Tears down the PPPoE agent app.
     */
    @After
    public void tearDown() {
        pppoeAgent.deactivate();
    }

    @Test
    public void testPppoePadi() {
        testPppoeUpstreamPacket(PPPoED.PPPOED_CODE_PADI);
    }

    @Test
    public void testPppoePado() {
        testPppoeDownstreamPacket(PPPoED.PPPOED_CODE_PADO);
    }

    @Test
    public void testPppoePadr() {
        testPppoeUpstreamPacket(PPPoED.PPPOED_CODE_PADR);
    }

    @Test
    public void testPppoePads() {
        testPppoeDownstreamPacket(PPPoED.PPPOED_CODE_PADS);
    }

    @Test
    public void testPppoePadt() {
        // To simulate a successful PADT from subscriber a session entry is needed.
        PppoeSessionInfo sessionInfo = new PppoeSessionInfo(DEFAULT_CONNECT_POINT, SERVER_CONNECT_POINT,
                                                            PPPoED.PPPOED_CODE_PADS, (short) 1,
                                                            pppoeAgent.subsService.get(CLIENT_NAS_PORT_ID),
                                                            CLIENT_MAC);
        putInfoOnSessionMap(CLIENT_MAC, sessionInfo);
        assertTrue(pppoeAgent.getSessionsMap().containsKey(CLIENT_MAC));

        Ethernet padt = constructPppoedPacket(PPPoED.PPPOED_CODE_PADT, CLIENT_MAC, MacAddress.BROADCAST,
                                              CLIENT_C_TAG, CLIENT_S_TAG, (short) 1);
        sendPacket(padt, DEFAULT_CONNECT_POINT);
        Ethernet processedPadt = (Ethernet) getPacket();
        assertNotNull(processedPadt);
        assertEquals(padt, processedPadt);
        assertFalse(pppoeAgent.getSessionsMap().containsKey(CLIENT_MAC));
        PppoeAgentEvent e = getEvent();
        assertNotNull(e);
        assertEquals(PppoeAgentEvent.Type.TERMINATE, e.type());
        assertEquals(DEFAULT_CONNECT_POINT, e.getConnectPoint());
        assertEquals(CLIENT_MAC, e.getSubscriberMacAddress());

        // Simulating PADT from server.
        putInfoOnSessionMap(CLIENT_MAC, sessionInfo);
        assertTrue(pppoeAgent.getSessionsMap().containsKey(CLIENT_MAC));
        padt = constructPppoedPacket(PPPoED.PPPOED_CODE_PADT, SERVER_MAC, CLIENT_MAC,
                                     CLIENT_C_TAG, CLIENT_S_TAG, (short) 1);
        sendPacket(padt, SERVER_CONNECT_POINT);
        processedPadt = (Ethernet) getPacket();
        assertNotNull(processedPadt);
        assertEquals(padt, processedPadt);
        assertFalse(pppoeAgent.getSessionsMap().containsKey(CLIENT_MAC));
        e = getEvent();
        assertNotNull(e);
        assertEquals(PppoeAgentEvent.Type.TERMINATE, e.type());
        assertEquals(SERVER_CONNECT_POINT, e.getConnectPoint());
        assertEquals(CLIENT_MAC, e.getSubscriberMacAddress());

        // Simulating PADT from client for unknown session (the packet must not be processed)..
        padt = constructPppoedPacket(PPPoED.PPPOED_CODE_PADT, CLIENT_MAC, MacAddress.BROADCAST,
                                     CLIENT_C_TAG, CLIENT_S_TAG, (short) 1);
        sendPacket(padt, DEFAULT_CONNECT_POINT);
        processedPadt = (Ethernet) getPacket();
        assertNull(processedPadt);

        // Simulating PADT from server for unknown session (the packet must not be processed).
        padt = constructPppoedPacket(PPPoED.PPPOED_CODE_PADT, SERVER_MAC, CLIENT_MAC,
                                     CLIENT_C_TAG, CLIENT_S_TAG, (short) 1);
        sendPacket(padt, SERVER_CONNECT_POINT);
        processedPadt = (Ethernet) getPacket();
        assertNull(processedPadt);
    }

    @Test
    public void testPppoeCircuitIdValidation() {
        Ethernet packet = constructPppoedPacket(PPPoED.PPPOED_CODE_PADI, CLIENT_MAC, MacAddress.BROADCAST,
                                                CLIENT_C_TAG, CLIENT_S_TAG, (short) 0);
        // Send packet with a different port number, so there will be a circuit-id mismatch.
        sendPacket(packet, new ConnectPoint(DEVICE_ID, PortNumber.portNumber(4096L)));
        Ethernet processedPacket = (Ethernet) getPacket();
        assertNull(processedPacket);
        PppoeAgentEvent e = getEvent();
        assertNotNull(e);
        assertEquals(PppoeAgentEvent.Type.INVALID_CID, e.type());

        // Now send it from the default connect point, which should generate the valid circuit-id.
        sendPacket(packet, DEFAULT_CONNECT_POINT);
        processedPacket = (Ethernet) getPacket();
        assertNotNull(processedPacket);
        PPPoED pppoeLayer = (PPPoED) processedPacket.getPayload();
        assertNotNull(pppoeLayer);
        PPPoEDTag tag = pppoeLayer.getTag(PPPoEDTag.PPPOED_TAG_VENDOR_SPECIFIC);
        assertNotNull(tag);
        PPPoEDVendorSpecificTag vendorSpecificTag = PPPoEDVendorSpecificTag.fromByteArray(tag.getValue());
        assertNotNull(vendorSpecificTag);

        // Checks if the configured circuit-id matches with the built one.
        assertEquals(CLIENT_CIRCUIT_ID, vendorSpecificTag.getCircuitId());
    }

    @Test
    public void testPppoeCounters() {
        short sessionId = (short) 0;
        Ethernet padi = constructPppoedPacket(PPPoED.PPPOED_CODE_PADI, CLIENT_MAC, MacAddress.BROADCAST,
                                              CLIENT_C_TAG, CLIENT_S_TAG, sessionId);
        Ethernet pado = constructPppoedPacket(PPPoED.PPPOED_CODE_PADO, SERVER_MAC, CLIENT_MAC,
                                              CLIENT_C_TAG, CLIENT_S_TAG, sessionId);
        Ethernet padr = constructPppoedPacket(PPPoED.PPPOED_CODE_PADR, CLIENT_MAC, MacAddress.BROADCAST,
                                              CLIENT_C_TAG, CLIENT_S_TAG, sessionId);
        sessionId++;
        Ethernet pads = constructPppoedPacket(PPPoED.PPPOED_CODE_PADS, SERVER_MAC, CLIENT_MAC,
                                              CLIENT_C_TAG, CLIENT_S_TAG, sessionId);

        List.of(new CounterTester(PppoeAgentCounterNames.PADI, 6, padi, DEFAULT_CONNECT_POINT),
                new CounterTester(PppoeAgentCounterNames.PADO, 2, pado, SERVER_CONNECT_POINT),
                new CounterTester(PppoeAgentCounterNames.PADR, 5, padr, DEFAULT_CONNECT_POINT),
                new CounterTester(PppoeAgentCounterNames.PADS, 3, pads, SERVER_CONNECT_POINT),
                new CounterTester(PppoeAgentCounterNames.PPPOED_PACKETS_FROM_SERVER, 5, null, null),
                new CounterTester(PppoeAgentCounterNames.PPPOED_PACKETS_TO_SERVER, 11, null, null),
                new CounterTester(PppoeAgentCounterNames.AC_SYSTEM_ERROR, 0, null, null),
                new CounterTester(PppoeAgentCounterNames.GENERIC_ERROR_FROM_CLIENT, 0, null, null),
                new CounterTester(PppoeAgentCounterNames.GENERIC_ERROR_FROM_SERVER, 0, null, null),
                new CounterTester(PppoeAgentCounterNames.MTU_EXCEEDED, 0, null, null),
                new CounterTester(PppoeAgentCounterNames.SERVICE_NAME_ERROR, 0, null, null))
        .forEach(CounterTester::test);
    }

    @Test
    public void testSessionsMap() {
        assertEquals(0, pppoeAgent.getSessionsMap().size());
        Ethernet packet = constructPppoedPacket(PPPoED.PPPOED_CODE_PADI, CLIENT_MAC, MacAddress.BROADCAST,
                                                CLIENT_C_TAG, CLIENT_S_TAG, (short) 0);
        sendPacket(packet, DEFAULT_CONNECT_POINT);
        assertEquals(1, pppoeAgent.getSessionsMap().size());

        int randomPacketsNumber = 15;
        sendMultiplePadi(randomPacketsNumber);
        assertEquals(randomPacketsNumber + 1, pppoeAgent.getSessionsMap().size());
        PppoeSessionInfo sessionInfo = pppoeAgent.getSessionsMap().get(CLIENT_MAC);
        assertSessionInfo(sessionInfo, PPPoED.PPPOED_CODE_PADI, (short) 0);

        packet = constructPppoedPacket(PPPoED.PPPOED_CODE_PADT, CLIENT_MAC, MacAddress.BROADCAST,
                                       CLIENT_C_TAG, CLIENT_S_TAG, (short) 0);
        sendPacket(packet, DEFAULT_CONNECT_POINT);

        assertEquals(randomPacketsNumber, pppoeAgent.getSessionsMap().size());
    }

    @Test
    public void testDeviceEvents() {
        // Guarantee map is empty.
        assertEquals(0, pppoeAgent.getSessionsMap().size());

        // Fill sessionsMap by sending 10 PADI packets for random mac addresses.
        int numPackets = 10;
        sendMultiplePadi(numPackets);
        assertEquals(numPackets, pppoeAgent.getSessionsMap().size());

        // Generate PORT_REMOVED event and inject into the device listener.
        DeviceListener deviceListener = TestUtils.getField(pppoeAgent, "deviceListener");
        Device device = pppoeAgent.deviceService.getDevice(DEVICE_ID);
        DeviceEvent deviceEvent = new DeviceEvent(DeviceEvent.Type.PORT_REMOVED,
                                                  device,
                                                  new MockPort(PortNumber.portNumber(1L)));
        deviceListener.event(deviceEvent);

        // Check if session map is empty again.
        assertEquals(0, pppoeAgent.getSessionsMap().size());

        // Perform the same test but for PORT_UPDATED event.
        sendMultiplePadi(numPackets);
        assertEquals(numPackets, pppoeAgent.getSessionsMap().size());
        deviceEvent = new DeviceEvent(DeviceEvent.Type.PORT_UPDATED,
                                      device,
                                      new MockPort(PortNumber.portNumber(1L), false));
        deviceListener.event(deviceEvent);
        assertEquals(0, pppoeAgent.getSessionsMap().size());

        // Same test for DEVICE_REMOVED.
        sendMultiplePadi(numPackets);
        assertEquals(numPackets, pppoeAgent.getSessionsMap().size());
        deviceEvent = new DeviceEvent(DeviceEvent.Type.DEVICE_REMOVED, device, null);
        deviceListener.event(deviceEvent);
        assertEquals(0, pppoeAgent.getSessionsMap().size());
    }

    private void sendMultiplePadi(int num) {
        for (int i = 0; i < num; i++) {
            MacAddress macAddress;
            // A trick to guarantee the Mac address won't repeat (this case may never occur).
            do {
                macAddress = randomizeMacAddress();
            } while (pppoeAgent.getSessionsMap().containsKey(macAddress));

            Ethernet packet = constructPppoedPacket(PPPoED.PPPOED_CODE_PADI, macAddress, MacAddress.BROADCAST,
                    CLIENT_C_TAG, CLIENT_S_TAG, (short) 0);
            sendPacket(packet, DEFAULT_CONNECT_POINT);
        }
    }

    private void testPppoeUpstreamPacket(byte packetCode) {
        Ethernet packet = constructPppoedPacket(packetCode, CLIENT_MAC, MacAddress.BROADCAST,
                                                CLIENT_C_TAG, CLIENT_S_TAG, (short) 0);
        sendPacket(packet, DEFAULT_CONNECT_POINT);

        Ethernet processedPacket = (Ethernet) getPacket();
        assertNotNull(processedPacket);

        PPPoED pppoedLayer = (PPPoED) processedPacket.getPayload();
        assertNotNull(pppoedLayer);

        List<PPPoEDTag> pppoedTagList = pppoedLayer.getTags();
        assertEquals(1, pppoedTagList.size());

        PPPoEDTag ppPoEDTag = pppoedTagList.get(0);
        assertEquals(PPPoEDTag.PPPOED_TAG_VENDOR_SPECIFIC, ppPoEDTag.getType());

        PPPoEDVendorSpecificTag vendorSpecificTag = PPPoEDVendorSpecificTag.fromByteArray(ppPoEDTag.getValue());
        assertEquals(CLIENT_CIRCUIT_ID, vendorSpecificTag.getCircuitId());
        assertEquals(CLIENT_REMOTE_ID, vendorSpecificTag.getRemoteId());
        assertEquals(Integer.valueOf(PPPoEDVendorSpecificTag.BBF_IANA_VENDOR_ID), vendorSpecificTag.getVendorId());

        // The only difference between the original and processed is the tag list,
        // so after removing it the packets must be equal.
        pppoedLayer.setPayload(null);
        pppoedLayer.setPayloadLength((short) 0);
        processedPacket.setPayload(pppoedLayer);
        assertEquals(packet, processedPacket);

        PppoeSessionInfo sessionInfo = pppoeAgent.getSessionsMap().get(CLIENT_MAC);
        assertNotNull(sessionInfo);
        assertSessionInfo(sessionInfo, packetCode, (short) 0);
    }

    private void testPppoeDownstreamPacket(byte packetCode) {
        // Simulating a session entry of a previous packet.
        byte previousPacketCode = packetCode == PPPoED.PPPOED_CODE_PADO ? PPPoED.PPPOED_CODE_PADI :
                                                                         PPPoED.PPPOED_CODE_PADR;

        SubscriberAndDeviceInformation deviceInfo = pppoeAgent.subsService.get(CLIENT_NAS_PORT_ID);

        putInfoOnSessionMap(CLIENT_MAC, new PppoeSessionInfo(DEFAULT_CONNECT_POINT, SERVER_CONNECT_POINT,
                                                             previousPacketCode, (short) 0, deviceInfo, CLIENT_MAC));

        short sessionId = (short) (packetCode == PPPoED.PPPOED_CODE_PADS ? 1 : 0);
        Ethernet packet = constructPppoedPacket(packetCode, SERVER_MAC, CLIENT_MAC,
                                                CLIENT_C_TAG, CLIENT_S_TAG, sessionId);
        sendPacket(packet, SERVER_CONNECT_POINT);

        Ethernet processedPacket = (Ethernet) getPacket();
        assertNotNull(processedPacket);

        // sTag is removed before sending the packet to client.
        packet.setQinQVID(VlanId.UNTAGGED);
        assertEquals(packet, processedPacket);

        PppoeSessionInfo sessionInfo = pppoeAgent.getSessionsMap().get(CLIENT_MAC);
        assertNotNull(sessionInfo);
        assertSessionInfo(sessionInfo, packetCode, sessionId);
    }

    private void assertSessionInfo(PppoeSessionInfo sessionInfo, Byte packetCode, short sessionId) {
        assertEquals(packetCode, sessionInfo.getPacketCode());
        assertEquals(sessionId, sessionInfo.getSessionId());
        assertEquals(DEFAULT_CONNECT_POINT, sessionInfo.getClientCp());
        assertEquals(CLIENT_MAC, sessionInfo.getClientMac());
    }

    private void putInfoOnSessionMap(MacAddress key, PppoeSessionInfo sessionInfo) {
        ConsistentMap<MacAddress, PppoeSessionInfo> sessionsMap = TestUtils.getField(pppoeAgent, "sessionsMap");
        sessionsMap.put(key, sessionInfo);
    }

    class CounterTester {
        CounterTester(String subscriber, PppoeAgentCounterNames counter,
                      long expectedValue, Ethernet packetModel,
                      ConnectPoint cp) {
            this.subscriber = subscriber;
            this.counter = counter;
            this.expectedValue = expectedValue;
            this.packetModel = packetModel;
            this.cp = cp;
        }

        CounterTester(PppoeAgentCounterNames counter, long expectedValue,
                      Ethernet packetModel, ConnectPoint cp) {
            this(PppoeAgentEvent.GLOBAL_COUNTER, counter, expectedValue, packetModel, cp);
        }

        String subscriber;
        PppoeAgentCounterNames counter;
        long expectedValue;
        Ethernet packetModel;
        ConnectPoint cp;

        void sendModel() {
            for (int i = 0; i < expectedValue; i++) {
                sendPacket(packetModel, cp);
            }
        }

        void assertCounterValue() {
            long actualValue = store.getCountersMap()
                    .get(new PppoeAgentCountersIdentifier(subscriber, counter));
            assertEquals(expectedValue, actualValue);
        }

        void test() {
            if (packetModel != null && cp != null) {
                sendModel();
            }
            assertCounterValue();
        }
    }
}
