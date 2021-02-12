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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;
import java.util.Dictionary;
import java.util.Hashtable;
import java.util.List;
import java.util.LinkedList;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.time.Duration;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Function;

import org.onlab.packet.BasePacket;
import org.onlab.packet.ChassisId;
import org.onlab.packet.Ethernet;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.MacAddress;
import org.onlab.packet.PPPoED;
import org.onlab.packet.VlanId;

import org.onosproject.cluster.NodeId;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreServiceAdapter;
import org.onosproject.core.DefaultApplicationId;
import org.onosproject.event.DefaultEventSinkRegistry;
import org.onosproject.event.Event;
import org.onosproject.event.EventDeliveryService;
import org.onosproject.event.EventSink;
import org.onosproject.mastership.MastershipServiceAdapter;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.Annotations;
import org.onosproject.net.config.Config;
import org.onosproject.net.config.NetworkConfigRegistryAdapter;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DefaultAnnotations;
import org.onosproject.net.DefaultDevice;
import org.onosproject.net.Device;
import org.onosproject.net.device.DeviceServiceAdapter;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Element;
import org.onosproject.net.packet.DefaultInboundPacket;
import org.onosproject.net.packet.DefaultPacketContext;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketServiceAdapter;
import org.onosproject.net.provider.ProviderId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.store.cluster.messaging.ClusterCommunicationService;
import org.onosproject.store.cluster.messaging.ClusterMessageHandler;
import org.onosproject.store.cluster.messaging.MessageSubject;

import org.opencord.pppoeagent.PppoeAgentEvent;
import org.opencord.sadis.BandwidthProfileInformation;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;

import org.osgi.framework.Bundle;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentInstance;

import static com.google.common.base.Preconditions.checkState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * Common methods for PpppoeAgent app tests.
 */
public class PppoeAgentTestBase {
    static final int UPLINK_PORT = 5;
    static final String OLT_DEV_ID = "of:00000000000000aa";
    static final String OLT_SERIAL_NUMBER = "OLT123456789";

    static final VlanId CLIENT_C_TAG = VlanId.vlanId((short) 999);
    static final VlanId CLIENT_C_TAG_2 = VlanId.vlanId((short) 998);
    static final VlanId CLIENT_S_TAG = VlanId.vlanId((short) 111);
    static final String CLIENT_ID_1 = "SUBSCRIBER_ID_1";
    static final short CLIENT_C_PBIT = 6;
    static final String CLIENT_NAS_PORT_ID = "ONU123456789";
    static final String CLIENT_REMOTE_ID = "remote0";

    static final MacAddress CLIENT_MAC = MacAddress.valueOf("B8:26:D4:09:E5:D1");
    static final MacAddress SERVER_MAC = MacAddress.valueOf("74:86:7A:FB:07:86");
    static final MacAddress OLT_MAC_ADDRESS = MacAddress.valueOf("01:02:03:04:05:06");

    static final ConnectPoint DEFAULT_CONNECT_POINT = ConnectPoint.deviceConnectPoint(OLT_DEV_ID + "/" + 1);
    static final String CLIENT_CIRCUIT_ID = String.format("%s 0/%s:%s", OLT_SERIAL_NUMBER,
                                                         (DEFAULT_CONNECT_POINT.port().toLong() >> 12) + 1,
                                                          CLIENT_NAS_PORT_ID);

    static final DeviceId DEVICE_ID = DeviceId.deviceId(OLT_DEV_ID);
    static final String SCHEME_NAME = "pppoeagent";

    static final ConnectPoint SERVER_CONNECT_POINT = ConnectPoint.deviceConnectPoint("of:00000000000000aa/5");

    static final DefaultAnnotations DEVICE_ANNOTATIONS = DefaultAnnotations.builder()
            .set(AnnotationKeys.PROTOCOL, SCHEME_NAME.toUpperCase()).build();

    List<BasePacket> savedPackets = new LinkedList<>();
    PacketProcessor packetProcessor;

    /**
     * Saves the given packet onto the saved packets list.
     *
     * @param packet packet to save
     */
    void savePacket(BasePacket packet) {
        savedPackets.add(packet);
    }

    /**
     * Gets and removes the packet in the 1st position of savedPackets list.
     *
     * @return the packet in 1st position of savedPackets list.
     */
    BasePacket getPacket() {
        return savedPackets.size() > 0 ? savedPackets.remove(0) : null;
    }

    /**
     * Gets the last generated event.
     *
     * @return the last generated pppoe agent event.
     */
    PppoeAgentEvent getEvent() {
        List<Event> savedEvents = MockEventDispatcher.eventList;
        return savedEvents.size() > 0 ? (PppoeAgentEvent) savedEvents.get(savedEvents.size() - 1) : null;
    }

    /**
     * Sends an Ethernet packet to the process method of the Packet Processor.
     *
     * @param pkt Ethernet packet
     * @param cp ConnectPoint cp
     */
    void sendPacket(Ethernet pkt, ConnectPoint cp) {
        final ByteBuffer byteBuffer = ByteBuffer.wrap(pkt.serialize());
        InboundPacket inPacket = new DefaultInboundPacket(cp, pkt, byteBuffer);

        PacketContext context = new MockPacketContext(127L, inPacket, null, false);
        packetProcessor.process(context);
    }

    /**
     * Mocks core service adaptor that provides an appId.
     */
    class MockCoreServiceAdapter extends CoreServiceAdapter {

        @Override
        public ApplicationId registerApplication(String name) {
            return new DefaultApplicationId(10, name);
        }
    }

    /**
     * Mocks device service to provide the of device object.
     */
    class MockDeviceService extends DeviceServiceAdapter {

        private ProviderId providerId = new ProviderId("of", "foo");
        private final Device device1 = new PppoeAgentTestBase.MockDevice(providerId, DEVICE_ID, Device.Type.SWITCH,
                "foo.inc", "0", "0", OLT_SERIAL_NUMBER, new ChassisId(),
                DEVICE_ANNOTATIONS);

        @Override
        public Device getDevice(DeviceId devId) {
            return device1;
        }

        @Override
        public Port getPort(ConnectPoint cp) {
            return new PppoeAgentTestBase.MockPort(cp.port());
        }

        @Override
        public Port getPort(DeviceId deviceId, PortNumber portNumber) {
            return new PppoeAgentTestBase.MockPort(portNumber);
        }

        @Override
        public boolean isAvailable(DeviceId d) {
            return true;
        }
    }

    /**
     * Mocks device object.
     */
    class MockDevice extends DefaultDevice {

        public MockDevice(ProviderId providerId, DeviceId id, Type type,
                          String manufacturer, String hwVersion, String swVersion,
                          String serialNumber, ChassisId chassisId, Annotations... annotations) {
            super(providerId, id, type, manufacturer, hwVersion, swVersion, serialNumber,
                    chassisId, annotations);
        }
    }

    /**
     * Mocks port object.
     */
    class  MockPort implements Port {
        private PortNumber portNumber;
        private boolean isEnabled = true;
        MockPort(PortNumber portNumber) {
            this.portNumber = portNumber;
        }

        MockPort(PortNumber portNumber, boolean isEnabled) {
            this.portNumber = portNumber;
            this.isEnabled = isEnabled;
        }

        @Override
        public boolean isEnabled() {
            return this.isEnabled;
        }
        @Override
        public long portSpeed() {
            return 1000;
        }
        @Override
        public Element element() {
            return null;
        }
        @Override
        public PortNumber number() {
            return this.portNumber;
        }
        @Override
        public Annotations annotations() {
            return new MockAnnotations();
        }
        @Override
        public Type type() {
            return Port.Type.FIBER;
        }

        private class MockAnnotations implements Annotations {

            @Override
            public String value(String val) {
                return CLIENT_NAS_PORT_ID;
            }
            @Override
            public Set<String> keys() {
                return null;
            }
        }
    }

    /**
     * Mocks mastership service to enable local-master operations.
     */
    class MockMastershipService extends MastershipServiceAdapter {
        @Override
        public boolean isLocalMaster(DeviceId d) {
            return true;
        }
    }

    /**
     * Keeps a reference to the PacketProcessor and saves the OutboundPackets.
     */
    class MockPacketService extends PacketServiceAdapter {

        @Override
        public void addProcessor(PacketProcessor processor, int priority) {
            packetProcessor = processor;
        }

        @Override
        public void emit(OutboundPacket packet) {
            try {
                Ethernet eth = Ethernet.deserializer().deserialize(packet.data().array(),
                        0, packet.data().array().length);
                savePacket(eth);
            } catch (Exception e) {
                fail(e.getMessage());
            }
        }
    }

    /**
     * Mocks Sadis service.
     */
    class MockSadisService implements SadisService {
        @Override
        public BaseInformationService<SubscriberAndDeviceInformation> getSubscriberInfoService() {
            return new PppoeAgentTestBase.MockSubService();
        }

        @Override
        public BaseInformationService<BandwidthProfileInformation> getBandwidthProfileService() {
            return null;
        }
    }

    /**
     * Mocks Sadis content with a device and a subscriber entry.
     */
    class MockSubService implements BaseInformationService<SubscriberAndDeviceInformation> {

        PppoeAgentTestBase.MockSubscriberAndDeviceInformation device =
                new PppoeAgentTestBase.MockSubscriberAndDeviceInformation(OLT_DEV_ID, VlanId.NONE, VlanId.NONE,
                                                                          VlanId.NONE, null, null,
                                                                          OLT_MAC_ADDRESS,
                                                                          Ip4Address.valueOf("10.10.10.10"),
                                                                          UPLINK_PORT, null);

        PppoeAgentTestBase.MockSubscriberAndDeviceInformation sub =
                new PppoeAgentTestBase.MockSubscriberAndDeviceInformation(CLIENT_ID_1, CLIENT_C_TAG, CLIENT_C_TAG_2,
                                                                          CLIENT_S_TAG, CLIENT_NAS_PORT_ID,
                                                                          CLIENT_CIRCUIT_ID, null, null,
                                                                -1, CLIENT_REMOTE_ID);

        @Override
        public SubscriberAndDeviceInformation get(String id) {
            if (id.equals(OLT_SERIAL_NUMBER)) {
                return device;
            } else {
                return  sub;
            }
        }

        @Override
        public void invalidateAll() {}
        @Override
        public void invalidateId(String id) {}
        @Override
        public SubscriberAndDeviceInformation getfromCache(String id) {
            return null;
        }
    }

    /**
     * Mock Sadis object to populate service.
     */
    class MockSubscriberAndDeviceInformation extends SubscriberAndDeviceInformation {

        MockSubscriberAndDeviceInformation(String id, VlanId cTag, VlanId cTag2,
                                           VlanId sTag, String nasPortId,
                                           String circuitId, MacAddress hardId,
                                           Ip4Address ipAddress, int uplinkPort,
                                           String remoteId) {
            this.setHardwareIdentifier(hardId);
            this.setId(id);
            this.setIPAddress(ipAddress);
            this.setNasPortId(nasPortId);
            this.setCircuitId(circuitId);
            this.setUplinkPort(uplinkPort);
            this.setRemoteId(remoteId);

            List<UniTagInformation> uniTagInformationList = new ArrayList<>();

            UniTagInformation uniTagInformation = new UniTagInformation.Builder()
                    .setPonCTag(cTag)
                    .setPonSTag(sTag)
                    .setUsPonCTagPriority(CLIENT_C_PBIT)
                    .build();

            UniTagInformation uniTagInformation2 = new UniTagInformation.Builder()
                    .setPonCTag(cTag2)
                    .setPonSTag(sTag)
                    .setUsPonCTagPriority(CLIENT_C_PBIT)
                    .build();

            uniTagInformationList.add(uniTagInformation);
            uniTagInformationList.add(uniTagInformation2);
            this.setUniTagList(uniTagInformationList);
        }
    }

    /**
     * Mocks component context.
     */
    class MockComponentContext implements ComponentContext {
        @Override
        public Dictionary<String, Object> getProperties() {
            Dictionary<String, Object> cfgDict = new Hashtable<String, Object>();
            return cfgDict;
        }

        @Override
        public Object locateService(String name) {
            return null;
        }

        @Override
        public Object locateService(String name, ServiceReference reference) {
            return null;
        }

        @Override
        public Object[] locateServices(String name) {
            return null;
        }

        @Override
        public BundleContext getBundleContext() {
            return null;
        }

        @Override
        public Bundle getUsingBundle() {
            return null;
        }

        @Override
        public ComponentInstance getComponentInstance() {
            return null;
        }

        @Override
        public void enableComponent(String name) {
        }

        @Override
        public void disableComponent(String name) {
        }

        @Override
        public ServiceReference getServiceReference() {
            return null;
        }
    }

    /**
     * Mocks the network config registry.
     */
    @SuppressWarnings("unchecked")
    class MockNetworkConfigRegistry
            extends NetworkConfigRegistryAdapter {
        @Override
        public <S, C extends Config<S>> C getConfig(S subject, Class<C> configClass) {
            PppoeAgentConfig pppoeAgentConfig = new MockPppoeAgentConfig();
            return (C) pppoeAgentConfig;
        }
    }

    /**
     * Mocks the network config registry.
     */
    class MockPppoeAgentConfig extends PppoeAgentConfig {
        @Override
        public boolean getUseOltUplinkForServerPktInOut() {
            return true;
        }
    }

    /**
     * Mocks the DefaultPacketContext.
     */
    final class MockPacketContext extends DefaultPacketContext {

        private MockPacketContext(long time, InboundPacket inPkt,
                                  OutboundPacket outPkt, boolean block) {
            super(time, inPkt, outPkt, block);
        }

        @Override
        public void send() {
            // We don't send anything out.
        }
    }

    /**
     * Creates a mock for event delivery service.
     */
    static class MockEventDispatcher extends DefaultEventSinkRegistry
            implements EventDeliveryService {
        static List<Event> eventList = new LinkedList<>();
        @Override
        @SuppressWarnings("unchecked")
        public synchronized void post(Event event) {
            EventSink sink = getSink(event.getClass());
            checkState(sink != null, "No sink for event %s", event);
            sink.process(event);
            eventList.add(event);
        }

        @Override
        public void setDispatchTimeLimit(long millis) {
        }

        @Override
        public long getDispatchTimeLimit() {
            return 0;
        }
    }

    /**
     * Creates a mock object for a scheduled executor service.
     */
    class MockExecutor implements ScheduledExecutorService {
        private ScheduledExecutorService executor;

        MockExecutor(ScheduledExecutorService executor) {
            this.executor = executor;
        }

        String lastMethodCalled = "";
        long lastInitialDelay;
        long lastDelay;
        TimeUnit lastUnit;

        public void assertLastMethodCalled(String method, long initialDelay, long delay, TimeUnit unit) {
            assertEquals(method, lastMethodCalled);
            assertEquals(initialDelay, lastInitialDelay);
            assertEquals(delay, lastDelay);
            assertEquals(unit, lastUnit);
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            lastMethodCalled = "scheduleRunnable";
            lastDelay = delay;
            lastUnit = unit;
            return null;
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            lastMethodCalled = "scheduleCallable";
            lastDelay = delay;
            lastUnit = unit;
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(
                Runnable command, long initialDelay, long period, TimeUnit unit) {
            lastMethodCalled = "scheduleAtFixedRate";
            lastInitialDelay = initialDelay;
            lastDelay = period;
            lastUnit = unit;
            return null;
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(
                Runnable command, long initialDelay, long delay, TimeUnit unit) {
            lastMethodCalled = "scheduleWithFixedDelay";
            lastInitialDelay = initialDelay;
            lastDelay = delay;
            lastUnit = unit;
            command.run();
            return null;
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
                throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(
                Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
                throws ExecutionException, InterruptedException {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
                throws ExecutionException, InterruptedException, TimeoutException {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isShutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isTerminated() {
            throw new UnsupportedOperationException();
        }

        @Override
        public void shutdown() {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<Runnable> shutdownNow() {
            return null;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }
    }

    /**
     * Creates a PPPoED message encapsulated by an Ethernet object.
     *
     * @param type        PPoED message type.
     * @param source      source address.
     * @param destination destination address of the message.
     * @param cVlan       inner vlan.
     * @param sVlan       outer vlan.
     * @param sessionId   session-id number.
     *
     * @return Ethernet packet with PPPoED payload.
     */
    Ethernet constructPppoedPacket(byte type, MacAddress source, MacAddress destination,
                                   VlanId cVlan, VlanId sVlan, short sessionId) {
        Ethernet pppoedPacket = new Ethernet();
        pppoedPacket.setSourceMACAddress(source);
        pppoedPacket.setDestinationMACAddress(destination);
        pppoedPacket.setEtherType(Ethernet.TYPE_PPPOED);
        pppoedPacket.setVlanID(cVlan.toShort());
        pppoedPacket.setPriorityCode((byte) CLIENT_C_PBIT);
        pppoedPacket.setQinQTPID(Ethernet.TYPE_VLAN);
        pppoedPacket.setQinQVID(sVlan.toShort());

        PPPoED pppoedLayer = new PPPoED();
        pppoedLayer.setCode(type);
        pppoedLayer.setSessionId(sessionId);
        pppoedPacket.setPayload(pppoedLayer);

        return pppoedPacket;
    }

    public class MockClusterCommunicationService<M> implements ClusterCommunicationService {
        private Consumer handler;
        @Override
        public void addSubscriber(MessageSubject subject,
                                  ClusterMessageHandler subscriber, ExecutorService executor) {
        }
        @Override
        public <M> void broadcast(M message, MessageSubject subject, Function<M, byte[]> encoder) {
        }
        @Override
        public <M> void broadcastIncludeSelf(M message, MessageSubject subject, Function<M, byte[]> encoder) {
            handler.accept(message);
        }
        @Override
        public <M> CompletableFuture<Void> unicast(M message, MessageSubject subject,
                                                   Function<M, byte[]> encoder, NodeId toNodeId) {
            return null;
        }
        @Override
        public <M> void multicast(M message, MessageSubject subject,
                                  Function<M, byte[]> encoder, Set<NodeId> nodeIds) {
        }
        @Override
        public <M, R> CompletableFuture<R> sendAndReceive(M message, MessageSubject subject,
                                                          Function<M, byte[]> encoder,
                                                          Function<byte[], R> decoder, NodeId toNodeId) {
            return null;
        }
        @Override
        public <M, R> CompletableFuture<R> sendAndReceive(M message, MessageSubject subject,
                                                          Function<M, byte[]> encoder, Function<byte[], R> decoder,
                                                          NodeId toNodeId, Duration timeout) {
            return null;
        }
        @Override
        public <M, R> void addSubscriber(MessageSubject subject, Function<byte[], M> decoder,
                                         Function<M, R> handler, Function<R, byte[]> encoder, Executor executor) {
        }
        @Override
        public <M, R> void addSubscriber(MessageSubject subject, Function<byte[], M> decoder,
                                         Function<M, CompletableFuture<R>> handler, Function<R, byte[]> encoder) {
        }
        @Override
        public <M> void addSubscriber(MessageSubject subject, Function<byte[], M> decoder,
                                      Consumer<M> handler, Executor executor) {
            this.handler = handler;
        }
        @Override
        public void removeSubscriber(MessageSubject subject) {
        }
    }

    /**
     * Creates a random MAC address.
     *
     * @return random MAC address object.
     */
    MacAddress randomizeMacAddress() {
        byte[] mac = new byte[6];
        Random r = new Random();
        r.nextBytes(mac);
        return MacAddress.valueOf(mac);
    }
}