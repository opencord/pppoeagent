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

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;

import com.google.common.collect.Sets;
import org.opencord.pppoeagent.PppoeAgentEvent;
import org.opencord.pppoeagent.PppoeAgentListener;
import org.opencord.pppoeagent.PppoeAgentService;
import org.opencord.pppoeagent.PppoeAgentStoreDelegate;
import org.opencord.pppoeagent.PPPoEDVendorSpecificTag;
import org.opencord.pppoeagent.PppoeSessionInfo;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.Serializer;
import com.google.common.collect.ImmutableMap;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.ConsistentMap;

import org.onlab.util.KryoNamespace;
import org.onlab.packet.Ethernet;
import org.onlab.packet.MacAddress;
import org.onlab.packet.PPPoED;
import org.onlab.packet.PPPoEDTag;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.event.AbstractListenerManager;
import org.onosproject.mastership.MastershipEvent;
import org.onosproject.mastership.MastershipListener;
import org.onosproject.mastership.MastershipService;
import org.onosproject.net.AnnotationKeys;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.Port;
import org.onosproject.net.PortNumber;
import org.onosproject.net.config.ConfigFactory;
import org.onosproject.net.config.NetworkConfigEvent;
import org.onosproject.net.config.NetworkConfigListener;
import org.onosproject.net.config.NetworkConfigRegistry;
import org.onosproject.net.device.DeviceEvent;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.device.DeviceService;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.OutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.opencord.sadis.BaseInformationService;
import org.opencord.sadis.SadisService;
import org.opencord.sadis.SubscriberAndDeviceInformation;
import org.opencord.sadis.UniTagInformation;
import org.opencord.pppoeagent.util.CircuitIdBuilder;
import org.opencord.pppoeagent.util.CircuitIdFieldName;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import org.onosproject.store.service.Versioned;

import static java.util.concurrent.Executors.newFixedThreadPool;

import static org.onlab.packet.PPPoED.PPPOED_CODE_PADI;
import static org.onlab.packet.PPPoED.PPPOED_CODE_PADO;
import static org.onlab.packet.PPPoED.PPPOED_CODE_PADR;
import static org.onlab.packet.PPPoED.PPPOED_CODE_PADS;
import static org.onlab.packet.PPPoED.PPPOED_CODE_PADT;
import static org.onlab.packet.PPPoEDTag.PPPOED_TAG_AC_SYSTEM_ERROR;
import static org.onlab.packet.PPPoEDTag.PPPOED_TAG_GENERIC_ERROR;
import static org.onlab.packet.PPPoEDTag.PPPOED_TAG_SERVICE_NAME_ERROR;
import static org.onlab.packet.PPPoEDTag.PPPOED_TAG_VENDOR_SPECIFIC;

import static org.onlab.util.Tools.getIntegerProperty;
import static org.onlab.util.Tools.groupedThreads;
import static org.onosproject.net.config.basics.SubjectFactories.APP_SUBJECT_FACTORY;

import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.PPPOE_MAX_MTU;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.PPPOE_MAX_MTU_DEFAULT;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.ENABLE_CIRCUIT_ID_VALIDATION;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.ENABLE_CIRCUIT_ID_VALIDATION_DEFAULT;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.PACKET_PROCESSOR_THREADS;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.PACKET_PROCESSOR_THREADS_DEFAULT;

/**
 * PPPoE Intermediate Agent application.
 */
@Component(immediate = true,
property = {
        PPPOE_MAX_MTU + ":Integer=" + PPPOE_MAX_MTU_DEFAULT,
        ENABLE_CIRCUIT_ID_VALIDATION + ":Boolean=" + ENABLE_CIRCUIT_ID_VALIDATION_DEFAULT,
        PACKET_PROCESSOR_THREADS + ":Integer=" + PACKET_PROCESSOR_THREADS_DEFAULT,
})
public class PppoeAgent
        extends AbstractListenerManager<PppoeAgentEvent, PppoeAgentListener>
        implements PppoeAgentService {
    private static final String APP_NAME = "org.opencord.pppoeagent";
    private static final short QINQ_VID_NONE = (short) -1;

    private final InternalConfigListener cfgListener = new InternalConfigListener();
    private final Set<ConfigFactory> factories = ImmutableSet.of(
            new ConfigFactory<>(APP_SUBJECT_FACTORY, PppoeAgentConfig.class, "pppoeagent") {
                @Override
                public PppoeAgentConfig createConfig() {
                    return new PppoeAgentConfig();
                }
            }
    );

    private final Logger log = LoggerFactory.getLogger(getClass());

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected NetworkConfigRegistry cfgService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected SadisService sadisService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected DeviceService deviceService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected MastershipService mastershipService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected PppoeAgentCountersStore pppoeAgentCounters;

    // OSGi Properties
    protected int pppoeMaxMtu = PPPOE_MAX_MTU_DEFAULT;
    protected boolean enableCircuitIdValidation = ENABLE_CIRCUIT_ID_VALIDATION_DEFAULT;
    protected int packetProcessorThreads = PACKET_PROCESSOR_THREADS_DEFAULT;

    private ApplicationId appId;
    private InnerDeviceListener deviceListener = new InnerDeviceListener();
    private InnerMastershipListener changeListener = new InnerMastershipListener();
    private PppoeAgentPacketProcessor pppoeAgentPacketProcessor = new PppoeAgentPacketProcessor();
    protected BaseInformationService<SubscriberAndDeviceInformation> subsService;
    private PppoeAgentStoreDelegate delegate = new InnerPppoeAgentStoreDelegate();

    Set<ConnectPoint> pppoeConnectPoints;
    protected AtomicReference<ConnectPoint> pppoeServerConnectPoint = new AtomicReference<>();
    protected boolean useOltUplink = true;

    static ConsistentMap<MacAddress, PppoeSessionInfo> sessionsMap;

    @Override
    public Map<MacAddress, PppoeSessionInfo> getSessionsMap() {
        return ImmutableMap.copyOf(sessionsMap.asJavaMap());
    }

    @Override
    public void clearSessionsMap() {
        sessionsMap.clear();
    }

    private final ArrayList<CircuitIdFieldName> circuitIdfields = new ArrayList<>(Arrays.asList(
            CircuitIdFieldName.AcessNodeIdentifier,
            CircuitIdFieldName.Slot,
            CircuitIdFieldName.Port,
            CircuitIdFieldName.OnuSerialNumber));

    protected ExecutorService packetProcessorExecutor;

    @Activate
    protected void activate(ComponentContext context) {
        eventDispatcher.addSink(PppoeAgentEvent.class, listenerRegistry);

        appId = coreService.registerApplication(APP_NAME);
        cfgService.addListener(cfgListener);
        componentConfigService.registerProperties(getClass());

        KryoNamespace serializer = KryoNamespace.newBuilder()
                .register(KryoNamespaces.API)
                .register(PppoeSessionInfo.class)
                .register(MacAddress.class)
                .register(SubscriberAndDeviceInformation.class)
                .register(UniTagInformation.class)
                .register(ConnectPoint.class)
                .build();

        sessionsMap = storageService.<MacAddress, PppoeSessionInfo>consistentMapBuilder()
                .withName("pppoeagent-sessions")
                .withSerializer(Serializer.using(serializer))
                .withApplicationId(appId)
                .build();

        factories.forEach(cfgService::registerConfigFactory);
        deviceService.addListener(deviceListener);
        subsService = sadisService.getSubscriberInfoService();
        mastershipService.addListener(changeListener);
        pppoeAgentCounters.setDelegate(delegate);
        updateConfig();
        packetService.addProcessor(pppoeAgentPacketProcessor, PacketProcessor.director(0));
        if (context != null) {
            modified(context);
        }
        log.info("PPPoE Intermediate Agent Started");
    }

    @Deactivate
    protected void deactivate() {
        cfgService.removeListener(cfgListener);
        factories.forEach(cfgService::unregisterConfigFactory);
        packetService.removeProcessor(pppoeAgentPacketProcessor);
        packetProcessorExecutor.shutdown();
        componentConfigService.unregisterProperties(getClass(), false);
        deviceService.removeListener(deviceListener);
        eventDispatcher.removeSink(PppoeAgentEvent.class);
        pppoeAgentCounters.unsetDelegate(delegate);
        log.info("PPPoE Intermediate Agent Stopped");
    }

    private void updateConfig() {
        PppoeAgentConfig cfg = cfgService.getConfig(appId, PppoeAgentConfig.class);
        if (cfg == null) {
            log.warn("PPPoE server info not available");
            return;
        }

        synchronized (this) {
            pppoeConnectPoints = Sets.newConcurrentHashSet(cfg.getPppoeServerConnectPoint());
        }
        useOltUplink = cfg.getUseOltUplinkForServerPktInOut();
    }

    /**
     * Returns whether the passed port is the uplink port of the olt device.
     */
    private boolean isUplinkPortOfOlt(DeviceId dId, Port p) {
        log.debug("isUplinkPortOfOlt: DeviceId: {} Port: {}", dId, p);
        if (!mastershipService.isLocalMaster(dId)) {
            return false;
        }

        Device d = deviceService.getDevice(dId);
        SubscriberAndDeviceInformation deviceInfo = subsService.get(d.serialNumber());
        if (deviceInfo != null) {
            return (deviceInfo.uplinkPort() == p.number().toLong());
        }

        return false;
    }

    /**
     * Returns the connectPoint which is the uplink port of the OLT.
     */
    private ConnectPoint getUplinkConnectPointOfOlt(DeviceId dId) {
        Device d = deviceService.getDevice(dId);
        SubscriberAndDeviceInformation deviceInfo = subsService.get(d.serialNumber());
        log.debug("getUplinkConnectPointOfOlt DeviceId: {} devInfo: {}", dId, deviceInfo);
        if (deviceInfo != null) {
            PortNumber pNum = PortNumber.portNumber(deviceInfo.uplinkPort());
            Port port = deviceService.getPort(d.id(), pNum);
            if (port != null) {
                return new ConnectPoint(d.id(), pNum);
            }
        }
        return null;
    }

    @Modified
    protected void modified(ComponentContext context) {
        Dictionary<?, ?> properties = context.getProperties();

        Integer newPppoeMaxMtu = getIntegerProperty(properties, PPPOE_MAX_MTU);
        if (newPppoeMaxMtu != null) {
            if (newPppoeMaxMtu != pppoeMaxMtu && newPppoeMaxMtu >= 0) {
                log.info("PPPPOE MTU modified from {} to {}", pppoeMaxMtu, newPppoeMaxMtu);
                pppoeMaxMtu = newPppoeMaxMtu;
            } else if (newPppoeMaxMtu < 0) {
                log.error("Invalid newPppoeMaxMtu : {}, defaulting to 1492", newPppoeMaxMtu);
                pppoeMaxMtu = PPPOE_MAX_MTU_DEFAULT;
            }
        }

        Boolean newEnableCircuitIdValidation = Tools.isPropertyEnabled(properties, ENABLE_CIRCUIT_ID_VALIDATION);
        if (newEnableCircuitIdValidation != null) {
            if (enableCircuitIdValidation != newEnableCircuitIdValidation) {
                log.info("Property enableCircuitIdValidation modified from {} to {}",
                        enableCircuitIdValidation, newEnableCircuitIdValidation);
                enableCircuitIdValidation = newEnableCircuitIdValidation;
            }
        }

        String s = Tools.get(properties, PACKET_PROCESSOR_THREADS);

        int oldpacketProcessorThreads = packetProcessorThreads;
        packetProcessorThreads = Strings.isNullOrEmpty(s) ? oldpacketProcessorThreads
                : Integer.parseInt(s.trim());
        if (packetProcessorExecutor == null || oldpacketProcessorThreads != packetProcessorThreads) {
            if (packetProcessorExecutor != null) {
                packetProcessorExecutor.shutdown();
            }
            packetProcessorExecutor = newFixedThreadPool(packetProcessorThreads,
                    groupedThreads("onos/pppoe",
                            "pppoe-packet-%d", log));
        }
    }

    /**
     * Selects a connect point through an available device for which it is the master.
     */
    private void selectServerConnectPoint() {
        synchronized (this) {
            pppoeServerConnectPoint.set(null);
            if (pppoeConnectPoints != null) {
                // find a connect point through a device for which we are master
                for (ConnectPoint cp: pppoeConnectPoints) {
                    if (mastershipService.isLocalMaster(cp.deviceId())) {
                        if (deviceService.isAvailable(cp.deviceId())) {
                            pppoeServerConnectPoint.set(cp);
                        }
                        log.info("PPPOE connectPoint selected is {}", cp);
                        break;
                    }
                }
            }
            log.info("PPPOE Server connectPoint is {}", pppoeServerConnectPoint.get());
            if (pppoeServerConnectPoint.get() == null) {
                log.error("Master of none, can't relay PPPOE messages to server");
            }
        }
    }

    private SubscriberAndDeviceInformation getSubscriber(ConnectPoint cp) {
        Port p = deviceService.getPort(cp);
        String subscriberId = p.annotations().value(AnnotationKeys.PORT_NAME);
        return subsService.get(subscriberId);
    }

    private SubscriberAndDeviceInformation getDevice(PacketContext context) {
        String serialNo = deviceService.getDevice(context.inPacket().
                receivedFrom().deviceId()).serialNumber();

        return subsService.get(serialNo);
    }

    private UniTagInformation getUnitagInformationFromPacketContext(PacketContext context,
                                                                    SubscriberAndDeviceInformation sub) {
        return sub.uniTagList()
                .stream()
                .filter(u -> u.getPonCTag().toShort() == context.inPacket().parsed().getVlanID())
                .findFirst()
                .orElse(null);
    }

    private boolean removeSessionsByConnectPoint(ConnectPoint cp) {
        boolean removed = false;
        for (MacAddress key : sessionsMap.keySet()) {
            PppoeSessionInfo entry = sessionsMap.asJavaMap().get(key);
            if (entry.getClientCp().equals(cp)) {
                sessionsMap.remove(key);
                removed = true;
            }
        }
        return removed;
    }

    private boolean removeSessionsByDevice(DeviceId devid) {
        boolean removed = false;
        for (MacAddress key : sessionsMap.keySet()) {
            PppoeSessionInfo entry = sessionsMap.asJavaMap().get(key);
            if (entry.getClientCp().deviceId().equals(devid)) {
                sessionsMap.remove(key);
                removed = true;
            }
        }
        return removed;
    }

    private class PppoeAgentPacketProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            packetProcessorExecutor.execute(() -> {
                processInternal(context);
            });
        }

        private void processInternal(PacketContext context) {
            Ethernet packet = (Ethernet) context.inPacket().parsed().clone();
            if (packet.getEtherType() == Ethernet.TYPE_PPPOED) {
                processPppoedPacket(context, packet);
            }
        }

        private void processPppoedPacket(PacketContext context, Ethernet packet) {
            PPPoED pppoed = (PPPoED) packet.getPayload();
            if (pppoed == null) {
                log.warn("PPPoED payload is null");
                return;
            }

            final byte pppoedCode = pppoed.getCode();
            final short sessionId = pppoed.getSessionId();
            final MacAddress clientMacAddress;
            final ConnectPoint packetCp = context.inPacket().receivedFrom();
            boolean serverMessage = false;

            // Get the client MAC address
            switch (pppoedCode) {
                case PPPOED_CODE_PADT: {
                    if (sessionsMap.containsKey(packet.getDestinationMAC())) {
                        clientMacAddress = packet.getDestinationMAC();
                        serverMessage = true;
                    } else if (sessionsMap.containsKey(packet.getSourceMAC())) {
                        clientMacAddress = packet.getSourceMAC();
                    } else {
                        // In the unlikely case of receiving a PADT without an existing session
                        log.warn("PADT received for unknown session. Dropping packet.");
                        return;
                    }
                    break;
                }
                case PPPOED_CODE_PADI:
                case PPPOED_CODE_PADR: {
                    clientMacAddress = packet.getSourceMAC();
                    break;
                }
                default: {
                    clientMacAddress = packet.getDestinationMAC();
                    serverMessage = true;
                    break;
                }
            }

            SubscriberAndDeviceInformation subsInfo;
            if (serverMessage) {
                if (!sessionsMap.containsKey(clientMacAddress)) {
                    log.error("PPPoED message received from server without an existing session. Message not relayed.");
                    return;
                } else {
                    PppoeSessionInfo sessInfo = sessionsMap.get(clientMacAddress).value();
                    subsInfo = getSubscriber(sessInfo.getClientCp());
                }
            } else {
                subsInfo = getSubscriber(packetCp);
            }

            if (subsInfo == null) {
                log.error("No Sadis info for subscriber on connect point {}. Message not relayed.", packetCp);
                return;
            }

            log.trace("{} received from {} at {} with client mac: {}",
                    PPPoED.Type.getTypeByValue(pppoedCode).toString(),
                    serverMessage ? "server" : "client", packetCp, clientMacAddress);

            if (log.isTraceEnabled()) {
                log.trace("PPPoED message received from {} at {} {}",
                        serverMessage ? "server" : "client", packetCp, packet);
            }

            // In case of PADI, force the removal of the previous session entry
            if ((pppoedCode == PPPOED_CODE_PADI) && (sessionsMap.containsKey(clientMacAddress))) {
                log.trace("PADI received from MAC: {} with existing session data. Removing the existing data.",
                        clientMacAddress.toString());
                sessionsMap.remove(clientMacAddress);
            }

            // Fill the session map entry
            PppoeSessionInfo sessionInfo;
            if (!sessionsMap.containsKey(clientMacAddress)) {
                if (!serverMessage)  {
                    ConnectPoint serverCp = getServerConnectPoint(packetCp.deviceId());
                    SubscriberAndDeviceInformation subscriber = getSubscriber(packetCp);
                    sessionInfo = new PppoeSessionInfo(packetCp, serverCp, pppoedCode,
                            sessionId, subscriber, clientMacAddress);
                    sessionsMap.put(clientMacAddress, sessionInfo);
                } else {
                    // This case was already covered.
                    return;
                }
            } else {
                sessionInfo = sessionsMap.get(clientMacAddress).value();
            }

            switch (pppoedCode) {
                case PPPOED_CODE_PADI:
                case PPPOED_CODE_PADR:
                    updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                            pppoedCode == PPPOED_CODE_PADI ? PppoeAgentCounterNames.PADI : PppoeAgentCounterNames.PADR);

                    Ethernet padir = processPacketFromClient(context, packet, pppoed, sessionInfo, clientMacAddress);
                    if (padir != null) {
                        if (padir.serialize().length <= pppoeMaxMtu) {
                            forwardPacketToServer(padir, sessionInfo);
                        } else {
                            log.debug("MTU message size: {} exceeded configured pppoeMaxMtu: {}. Dropping Packet.",
                                    padir.serialize().length, pppoeMaxMtu);
                            forwardPacketToClient(errorToClient(packet, pppoed, "MTU message size exceeded"),
                                                                sessionInfo, clientMacAddress);
                            updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                                                          PppoeAgentCounterNames.MTU_EXCEEDED);
                        }
                    }
                    break;
                case PPPOED_CODE_PADO:
                case PPPOED_CODE_PADS:
                    updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                            pppoedCode == PPPOED_CODE_PADO ? PppoeAgentCounterNames.PADO : PppoeAgentCounterNames.PADS);
                    Ethernet pados = processPacketFromServer(packet, pppoed, sessionInfo, clientMacAddress);
                    if (pados != null) {
                        forwardPacketToClient(pados, sessionInfo, clientMacAddress);
                    }
                    break;
                case PPPOED_CODE_PADT:
                    if (serverMessage) {
                        updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                                                      PppoeAgentCounterNames.PADT_FROM_SERVER);
                        forwardPacketToClient(packet, sessionInfo, clientMacAddress);
                    } else {
                        updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                                                      PppoeAgentCounterNames.PADT_FROM_CLIENT);
                        forwardPacketToServer(packet, sessionInfo);
                    }

                    String reason = "";
                    PPPoEDTag genericErrorTag = pppoed.getTags()
                            .stream()
                            .filter(tag -> tag.getType() == PPPOED_TAG_GENERIC_ERROR)
                            .findFirst()
                            .orElse(null);

                    if (genericErrorTag != null) {
                        reason = new String(genericErrorTag.getValue(), StandardCharsets.UTF_8);
                    }
                    log.debug("PADT sessionId:{}  client MAC:{}  Terminate reason:{}.",
                            Integer.toHexString(sessionId & 0xFFFF), clientMacAddress, reason);

                    boolean knownSessionId = sessionInfo.getSessionId() == sessionId;
                    if (knownSessionId) {
                        PppoeSessionInfo removedSessionInfo = Versioned
                                .valueOrNull(sessionsMap.remove(clientMacAddress));
                        if (removedSessionInfo != null) {
                            post(new PppoeAgentEvent(PppoeAgentEvent.Type.TERMINATE, removedSessionInfo,
                                                     packetCp, clientMacAddress, reason));
                        }
                    } else {
                        log.warn("PADT received for a known MAC address but for unknown session.");
                    }
                    break;
                default:
                    break;
            }
        }

        private Ethernet processPacketFromClient(PacketContext context, Ethernet ethernetPacket, PPPoED pppoed,
                                                 PppoeSessionInfo sessionInfo, MacAddress clientMacAddress) {
            byte pppoedCode = pppoed.getCode();

            sessionInfo.setPacketCode(pppoedCode);
            sessionsMap.put(clientMacAddress, sessionInfo);

            // Update Counters
            for (PPPoEDTag tag : pppoed.getTags()) {
                if (tag.getType() == PPPOED_TAG_GENERIC_ERROR) {
                    updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                            PppoeAgentCounterNames.GENERIC_ERROR_FROM_CLIENT);
                    break;
                }
            }

            Ethernet ethFwd = ethernetPacket;

            // If this is a PADI packet, there'll be a START event.
            if (pppoedCode == PPPOED_CODE_PADI) {
                post(new PppoeAgentEvent(PppoeAgentEvent.Type.START, sessionInfo, sessionInfo.getClientCp(),
                        clientMacAddress));
            }

            // Creates the vendor specific tag.
            String circuitId = getCircuitId(sessionInfo.getClientCp());
            if (circuitId == null) {
                log.error("Failed to build circuid-id for client on connect point {}. Dropping packet.",
                        sessionInfo.getClientCp());
                return null;
            }

            // Checks whether the circuit-id is valid, if it's not it drops the packet.
            if (!isCircuitIdValid(circuitId, sessionInfo.getSubscriber())) {
                log.warn("Invalid circuit ID, dropping packet.");
                PppoeAgentEvent invalidCidEvent = new PppoeAgentEvent(PppoeAgentEvent.Type.INVALID_CID, sessionInfo,
                        context.inPacket().receivedFrom(), clientMacAddress);
                post(invalidCidEvent);
                return null;
            }

            String remoteId = sessionInfo.getSubscriber().remoteId();
            byte[] vendorSpecificTag = new PPPoEDVendorSpecificTag(circuitId, remoteId).toByteArray();

            // According to TR-101, R-149 (by Broadband Forum), agent must REPLACE vendor-specific tag that may come
            // from client message with its own tag.
            // The following block ensures that agent removes any previous vendor-specific tag.
            List<PPPoEDTag> originalTags = pppoed.getTags();
            if (originalTags != null) {
                PPPoEDTag originalVendorSpecificTag = originalTags.stream()
                        .filter(tag -> tag.getType() == PPPOED_TAG_VENDOR_SPECIFIC)
                        .findFirst()
                        .orElse(null);

                if (originalVendorSpecificTag != null) {
                    int tagToRemoveLength = originalVendorSpecificTag.getLength();
                    originalTags.removeIf(tag -> tag.getType() == PPPOED_TAG_VENDOR_SPECIFIC);
                    pppoed.setPayloadLength((short) (pppoed.getPayloadLength() - tagToRemoveLength));
                }
            }

            pppoed.setTag(PPPOED_TAG_VENDOR_SPECIFIC, vendorSpecificTag);

            ethFwd.setPayload(pppoed);
            ethFwd.setQinQTPID(Ethernet.TYPE_VLAN);

            UniTagInformation uniTagInformation = getUnitagInformationFromPacketContext(context,
                    sessionInfo.getSubscriber());
            if (uniTagInformation == null) {
                log.warn("Missing service information for connectPoint {} / cTag {}",
                        context.inPacket().receivedFrom(),  context.inPacket().parsed().getVlanID());
                return null;
            }
            ethFwd.setQinQVID(uniTagInformation.getPonSTag().toShort());
            ethFwd.setPad(true);
            return ethFwd;
        }

        private Ethernet processPacketFromServer(Ethernet ethernetPacket, PPPoED pppoed,
                                                 PppoeSessionInfo sessionInfo, MacAddress clientMacAddress) {
            // Update counters
            List<PPPoEDTag> tags = pppoed.getTags();
            for (PPPoEDTag tag : tags) {
                switch (tag.getType()) {
                    case PPPOED_TAG_GENERIC_ERROR:
                        updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                                PppoeAgentCounterNames.GENERIC_ERROR_FROM_SERVER);
                        break;
                    case PPPOED_TAG_SERVICE_NAME_ERROR:
                        updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                                PppoeAgentCounterNames.SERVICE_NAME_ERROR);
                        break;
                    case PPPOED_TAG_AC_SYSTEM_ERROR:
                        updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                                PppoeAgentCounterNames.AC_SYSTEM_ERROR);
                        break;
                    default:
                        break;
                }
            }

            byte pppoedCode = pppoed.getCode();

            if (pppoedCode == PPPOED_CODE_PADS) {
                log.debug("PADS sessionId:{}  client MAC:{}", Integer.toHexString(pppoed.getSessionId() & 0xFFFF),
                        clientMacAddress);
                sessionInfo.setSessionId(pppoed.getSessionId());
            }
            sessionInfo.setPacketCode(pppoedCode);
            sessionsMap.put(clientMacAddress, sessionInfo);

            PppoeAgentEvent.Type eventType = pppoedCode == PPPOED_CODE_PADS ?
                    PppoeAgentEvent.Type.SESSION_ESTABLISHED :
                    PppoeAgentEvent.Type.NEGOTIATION;

            post(new PppoeAgentEvent(eventType, sessionInfo, sessionInfo.getClientCp(), clientMacAddress));

            ethernetPacket.setQinQVID(QINQ_VID_NONE);
            ethernetPacket.setPad(true);
            return ethernetPacket;
        }

        private void updatePppoeAgentCountersStore(SubscriberAndDeviceInformation sub,
                                                   PppoeAgentCounterNames counterType) {
            // Update global counter stats
            pppoeAgentCounters.incrementCounter(PppoeAgentEvent.GLOBAL_COUNTER, counterType);
            if (sub == null) {
                log.warn("Counter not updated as subscriber info not found.");
            } else {
                // Update subscriber counter stats
                pppoeAgentCounters.incrementCounter(sub.id(), counterType);
            }
        }

        private String getCircuitId(ConnectPoint cp) {
            return new CircuitIdBuilder()
                    .setConnectPoint(cp)
                    .setDeviceService(deviceService)
                    .setSubsService(subsService)
                    .setCircuitIdConfig(circuitIdfields)
                    .addCustomSeparator(CircuitIdFieldName.AcessNodeIdentifier, " ")
                    .addCustomSeparator(CircuitIdFieldName.Port, ":")
                    .build();
        }

        protected ConnectPoint getServerConnectPoint(DeviceId deviceId) {
            ConnectPoint serverCp;
            if (!useOltUplink) {
                serverCp = pppoeServerConnectPoint.get();
            } else {
                serverCp = getUplinkConnectPointOfOlt(deviceId);
            }
            return serverCp;
        }

        private boolean isCircuitIdValid(String cId, SubscriberAndDeviceInformation entry) {
            if (!enableCircuitIdValidation) {
                log.debug("Circuit ID validation is disabled.");
                return true;
            }

            if (entry == null) {
                log.error("SubscriberAndDeviceInformation cannot be null.");
                return false;
            }

            if (entry.circuitId() == null || entry.circuitId().isEmpty()) {
                log.debug("Circuit ID not configured in SADIS entry. No check is done.");
                return true;
            } else {
                if (cId.equals(entry.circuitId())) {
                    log.info("Circuit ID in packet: {} matched the configured entry on SADIS.", cId);
                    return true;
                } else {
                    log.warn("Circuit ID in packet: {} did not match the configured entry on SADIS: {}.",
                            cId, entry.circuitId());
                    return false;
                }
            }
        }

        private void forwardPacketToServer(Ethernet packet, PppoeSessionInfo sessionInfo) {
            ConnectPoint toSendTo = sessionInfo.getServerCp();
            if (toSendTo != null) {
                log.info("Sending PPPOE packet to server at {}", toSendTo);
                TrafficTreatment t = DefaultTrafficTreatment.builder().setOutput(toSendTo.port()).build();
                OutboundPacket o = new DefaultOutboundPacket(toSendTo.deviceId(), t,
                        ByteBuffer.wrap(packet.serialize()));
                if (log.isTraceEnabled()) {
                    log.trace("Relaying packet to pppoe server at {} {}", toSendTo, packet);
                }
                packetService.emit(o);

                updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                        PppoeAgentCounterNames.PPPOED_PACKETS_TO_SERVER);
            } else {
                log.error("No connect point to send msg to PPPOE Server");
            }
        }

        private void forwardPacketToClient(Ethernet packet, PppoeSessionInfo sessionInfo, MacAddress clientMacAddress) {
            ConnectPoint subCp = sessionInfo.getClientCp();
            if (subCp == null) {
                log.error("Dropping PPPOE packet, can't find a connectpoint for MAC {}", clientMacAddress);
                return;
            }

            log.info("Sending PPPOE packet to client at {}", subCp);
            TrafficTreatment t = DefaultTrafficTreatment.builder()
                    .setOutput(subCp.port()).build();
            OutboundPacket o = new DefaultOutboundPacket(
                    subCp.deviceId(), t, ByteBuffer.wrap(packet.serialize()));
            if (log.isTraceEnabled()) {
                log.trace("Relaying packet to pppoe client at {} {}", subCp, packet);
            }

            packetService.emit(o);

            updatePppoeAgentCountersStore(sessionInfo.getSubscriber(),
                    PppoeAgentCounterNames.PPPOED_PACKETS_FROM_SERVER);
        }

        private Ethernet errorToClient(Ethernet packet, PPPoED p, String errString) {
            PPPoED err = new PPPoED();
            err.setVersion(p.getVersion());
            err.setType(p.getType());
            switch (p.getCode()) {
                case PPPOED_CODE_PADI:
                    err.setCode(PPPOED_CODE_PADO);
                    break;
                case PPPOED_CODE_PADR:
                    err.setCode(PPPOED_CODE_PADS);
                    break;
                default:
                    break;
            }
            err.setCode(p.getCode());
            err.setSessionId(p.getSessionId());
            err.setTag(PPPOED_TAG_GENERIC_ERROR, errString.getBytes(StandardCharsets.UTF_8));

            Ethernet ethPacket = new Ethernet();
            ethPacket.setPayload(err);
            ethPacket.setSourceMACAddress(packet.getDestinationMACAddress());
            ethPacket.setDestinationMACAddress(packet.getSourceMACAddress());
            ethPacket.setQinQVID(QINQ_VID_NONE);
            ethPacket.setPad(true);

            return ethPacket;
        }
    }

    private class InternalConfigListener implements NetworkConfigListener {
        @Override
        public void event(NetworkConfigEvent event) {

            if ((event.type() == NetworkConfigEvent.Type.CONFIG_ADDED ||
                    event.type() == NetworkConfigEvent.Type.CONFIG_UPDATED) &&
                    event.configClass().equals(PppoeAgentConfig.class)) {
                updateConfig();
                log.info("Reconfigured");
            }
        }
    }

    /**
     * Handles Mastership changes for the devices which connect to the PPPOE server.
     */
    private class InnerMastershipListener implements MastershipListener {
        @Override
        public void event(MastershipEvent event) {
            if (!useOltUplink) {
                if (pppoeServerConnectPoint.get() != null &&
                        pppoeServerConnectPoint.get().deviceId().equals(event.subject())) {
                    log.trace("Mastership Event recevived for {}", event.subject());
                    // mastership of the device for our connect point has changed, reselect
                    selectServerConnectPoint();
                }
            }
        }
    }

    private class InnerDeviceListener implements DeviceListener {
        @Override
        public void event(DeviceEvent event) {
            DeviceId devId = event.subject().id();

            if (log.isTraceEnabled() && !event.type().equals(DeviceEvent.Type.PORT_STATS_UPDATED)) {
                log.trace("Device Event received for {} event {}", event.subject(), event.type());
            }

            // Handle events from any other device
            switch (event.type()) {
                case PORT_UPDATED:
                    if (!event.port().isEnabled()) {
                        ConnectPoint cp = new ConnectPoint(devId, event.port().number());
                        removeSessionsByConnectPoint(cp);
                    }
                    break;
                case PORT_REMOVED:
                    // Remove all entries related to this port from sessions map
                    ConnectPoint cp = new ConnectPoint(devId, event.port().number());
                    removeSessionsByConnectPoint(cp);
                    break;
                case DEVICE_REMOVED:
                    // Remove all entries related to this device from sessions map
                    removeSessionsByDevice(devId);
                    break;
                default:
                    break;
            }
        }
    }

    private class InnerPppoeAgentStoreDelegate implements PppoeAgentStoreDelegate {
        @Override
        public void notify(PppoeAgentEvent event) {
            if (event.type().equals(PppoeAgentEvent.Type.STATS_UPDATE)) {
                PppoeAgentEvent toPost = event;
                if (event.getSubscriberId() != null) {
                    // infuse the event with the allocation info before posting
                    PppoeSessionInfo info = Versioned.valueOrNull(
                            sessionsMap.stream().filter(entry -> event.getSubscriberId()
                                    .equals(entry.getValue().value().getSubscriber().id()))
                                    .map(Map.Entry::getValue)
                                    .findFirst()
                                    .orElse(null));
                    if (info == null) {
                        log.debug("Not handling STATS_UPDATE event for session that no longer exists. {}.", event);
                        return;
                    }

                    toPost = new PppoeAgentEvent(event.type(), info, event.getCounterName(), event.getCounterValue(),
                                                 info.getClientMac(), event.getSubscriberId());
                }
                post(toPost);
            }
        }
    }
}