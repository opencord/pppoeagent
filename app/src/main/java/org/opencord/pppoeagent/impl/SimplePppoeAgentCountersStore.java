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
import com.google.common.collect.ImmutableMap;

import org.onlab.util.KryoNamespace;
import org.onlab.util.SafeRecurringTask;
import org.onlab.util.Tools;
import org.onosproject.cfg.ComponentConfigService;
import org.onosproject.cluster.ClusterService;
import org.onosproject.cluster.LeadershipService;
import org.onosproject.cluster.NodeId;
import org.onosproject.store.AbstractStore;
import org.onosproject.store.cluster.messaging.ClusterCommunicationService;
import org.onosproject.store.cluster.messaging.ClusterMessage;
import org.onosproject.store.cluster.messaging.MessageSubject;
import org.onosproject.store.serializers.KryoNamespaces;
import org.onosproject.store.service.EventuallyConsistentMap;
import org.onosproject.store.service.Serializer;
import org.onosproject.store.service.StorageService;
import org.onosproject.store.service.WallClockTimestamp;
import org.opencord.pppoeagent.PppoeAgentEvent;
import org.opencord.pppoeagent.PppoeAgentStoreDelegate;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.component.annotations.Modified;
import org.osgi.service.component.annotations.Reference;
import org.osgi.service.component.annotations.ReferenceCardinality;

import org.slf4j.Logger;
import java.nio.charset.StandardCharsets;

import java.util.Dictionary;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.PUBLISH_COUNTERS_RATE;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.PUBLISH_COUNTERS_RATE_DEFAULT;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.SYNC_COUNTERS_RATE;
import static org.opencord.pppoeagent.impl.OsgiPropertyConstants.SYNC_COUNTERS_RATE_DEFAULT;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * PPPoE Agent Counters Manager Component.
 */
@Component(immediate = true,
        property = {
                PUBLISH_COUNTERS_RATE + ":Integer=" + PUBLISH_COUNTERS_RATE_DEFAULT,
                SYNC_COUNTERS_RATE + ":Integer=" + SYNC_COUNTERS_RATE_DEFAULT,
        }
)
public class SimplePppoeAgentCountersStore extends AbstractStore<PppoeAgentEvent, PppoeAgentStoreDelegate>
        implements PppoeAgentCountersStore {
    private static final String PPPOE_STATISTICS_LEADERSHIP = "pppoeagent-statistics";
    private static final MessageSubject RESET_SUBJECT = new MessageSubject("pppoeagent-statistics-reset");

    private final Logger log = getLogger(getClass());
    private ConcurrentMap<PppoeAgentCountersIdentifier, Long> countersMap;

    private EventuallyConsistentMap<NodeId, PppoeAgentStatistics> statistics;

    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected StorageService storageService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ClusterService clusterService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected LeadershipService leadershipService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ComponentConfigService componentConfigService;
    @Reference(cardinality = ReferenceCardinality.MANDATORY)
    protected ClusterCommunicationService clusterCommunicationService;
    protected int publishCountersRate = PUBLISH_COUNTERS_RATE_DEFAULT;
    protected int syncCountersRate = SYNC_COUNTERS_RATE_DEFAULT;
    KryoNamespace serializer = KryoNamespace.newBuilder()
            .register(KryoNamespaces.API)
            .register(PppoeAgentStatistics.class)
            .register(PppoeAgentCountersIdentifier.class)
            .register(PppoeAgentCounterNames.class)
            .register(ClusterMessage.class)
            .register(MessageSubject.class)
            .build();
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> publisherTask;
    private ScheduledFuture<?> syncTask;
    private AtomicBoolean dirty = new AtomicBoolean(true);

    @Activate
    public void activate(ComponentContext context) {
        log.info("Activate PPPoE Agent Counters Manager");
        countersMap = new ConcurrentHashMap<>();
        componentConfigService.registerProperties(getClass());
        modified(context);
        statistics = storageService.<NodeId, PppoeAgentStatistics>eventuallyConsistentMapBuilder()
                .withName("pppoeagent-statistics")
                .withSerializer(serializer)
                .withTimestampProvider((k, v) -> new WallClockTimestamp())
                .build();
        // Initialize counter values for the global counters
        initCounters(PppoeAgentEvent.GLOBAL_COUNTER, statistics.get(clusterService.getLocalNode().id()));
        syncStats();
        leadershipService.runForLeadership(PPPOE_STATISTICS_LEADERSHIP);
        executor = Executors.newScheduledThreadPool(1);
        clusterCommunicationService.addSubscriber(RESET_SUBJECT, Serializer.using(serializer)::decode,
                this::resetLocal, executor);
        startSyncTask();
        startPublishTask();
    }

    @Deactivate
    public void deactivate() {
        clusterCommunicationService.removeSubscriber(RESET_SUBJECT);
        leadershipService.withdraw(PPPOE_STATISTICS_LEADERSHIP);
        stopPublishTask();
        stopSyncTask();
        executor.shutdownNow();
        componentConfigService.unregisterProperties(getClass(), false);
    }
    @Modified
    public void modified(ComponentContext context) {
        Dictionary<String, Object> properties = context.getProperties();
        String s = Tools.get(properties, PUBLISH_COUNTERS_RATE);
        int oldPublishCountersRate = publishCountersRate;
        publishCountersRate = Strings.isNullOrEmpty(s) ? PUBLISH_COUNTERS_RATE_DEFAULT
                : Integer.parseInt(s.trim());
        if (oldPublishCountersRate != publishCountersRate) {
            stopPublishTask();
            startPublishTask();
        }
        s = Tools.get(properties, SYNC_COUNTERS_RATE);
        int oldSyncCountersRate = syncCountersRate;
        syncCountersRate = Strings.isNullOrEmpty(s) ? SYNC_COUNTERS_RATE_DEFAULT
                : Integer.parseInt(s.trim());
        if (oldSyncCountersRate != syncCountersRate) {
            stopSyncTask();
            startSyncTask();
        }
    }
    private ScheduledFuture<?> startTask(Runnable r, int rate) {
        return executor.scheduleAtFixedRate(SafeRecurringTask.wrap(r),
                0, rate, TimeUnit.SECONDS);
    }
    private void stopTask(ScheduledFuture<?> task) {
        task.cancel(true);
    }
    private void startSyncTask() {
        syncTask = startTask(this::syncStats, syncCountersRate);
    }
    private void stopSyncTask() {
        stopTask(syncTask);
    }
    private void startPublishTask() {
        publisherTask = startTask(this::publishStats, publishCountersRate);
    }
    private void stopPublishTask() {
        stopTask(publisherTask);
    }

    ImmutableMap<PppoeAgentCountersIdentifier, Long> getCountersMap() {
        return ImmutableMap.copyOf(countersMap);
    }

    public PppoeAgentStatistics getCounters() {
        return aggregate();
    }

    /**
     * Initialize the supported counters map for the given counter class.
     * @param counterClass class of counters (global, per subscriber)
     * @param existingStats existing values to intialise the counters to
     */
    public void initCounters(String counterClass, PppoeAgentStatistics existingStats) {
        checkNotNull(counterClass, "counter class can't be null");
        for (PppoeAgentCounterNames counterType : PppoeAgentCounterNames.SUPPORTED_COUNTERS) {
            PppoeAgentCountersIdentifier id = new PppoeAgentCountersIdentifier(counterClass, counterType);
            countersMap.put(id, existingStats == null ? 0L : existingStats.get(id));
        }
    }

    /**
     * Inserts the counter entry if it is not already in the set otherwise increment the existing counter entry.
     * @param counterClass class of counters (global, per subscriber)
     * @param counterType name of counter
     */
    public void incrementCounter(String counterClass, PppoeAgentCounterNames counterType) {
        checkNotNull(counterClass, "counter class can't be null");
        if (PppoeAgentCounterNames.SUPPORTED_COUNTERS.contains(counterType)) {
            PppoeAgentCountersIdentifier counterIdentifier =
                    new PppoeAgentCountersIdentifier(counterClass, counterType);
            countersMap.compute(counterIdentifier, (key, counterValue) ->
                    (counterValue != null) ? counterValue + 1 : 1L);
        } else {
            log.error("Failed to increment counter class {} of type {}", counterClass, counterType);
        }
        dirty.set(true);
    }

    /**
     * Reset the counters map for the given counter class.
     * @param counterClass class of counters (global, per subscriber)
     */
    public void resetCounters(String counterClass) {
        byte[] payload = counterClass.getBytes(StandardCharsets.UTF_8);
        ClusterMessage reset = new ClusterMessage(clusterService.getLocalNode().id(), RESET_SUBJECT, payload);
        clusterCommunicationService.broadcastIncludeSelf(reset, RESET_SUBJECT, Serializer.using(serializer)::encode);
    }
    private void resetLocal(ClusterMessage m) {
        String counterClass = new String(m.payload(), StandardCharsets.UTF_8);
        checkNotNull(counterClass, "counter class can't be null");
        for (PppoeAgentCounterNames counterType : PppoeAgentCounterNames.SUPPORTED_COUNTERS) {
            PppoeAgentCountersIdentifier counterIdentifier =
                    new PppoeAgentCountersIdentifier(counterClass, counterType);
            countersMap.computeIfPresent(counterIdentifier, (key, counterValue) -> 0L);
        }
        dirty.set(true);
        syncStats();
    }

    /**
     * Inserts the counter entry if it is not already in the set otherwise update the existing counter entry.
     * @param counterClass class of counters (global, per subscriber).
     * @param counterType name of counter
     * @param value counter value
     */
    public void setCounter(String counterClass, PppoeAgentCounterNames counterType, Long value) {
        checkNotNull(counterClass, "counter class can't be null");
        if (PppoeAgentCounterNames.SUPPORTED_COUNTERS.contains(counterType)) {
            PppoeAgentCountersIdentifier counterIdentifier =
                    new PppoeAgentCountersIdentifier(counterClass, counterType);
            countersMap.put(counterIdentifier, value);
        } else {
            log.error("Failed to increment counter class {} of type {}", counterClass, counterType);
        }
        dirty.set(true);
        syncStats();
    }

    private PppoeAgentStatistics aggregate() {
        return statistics.values().stream()
                .reduce(new PppoeAgentStatistics(), PppoeAgentStatistics::add);
    }
    /**
     * Creates a snapshot of the current in-memory statistics.
     *
     * @return snapshot of statistics
     */
    private PppoeAgentStatistics snapshot() {
        return PppoeAgentStatistics.withCounters(countersMap);
    }
    /**
     * Syncs in-memory stats to the eventually consistent map.
     */
    private void syncStats() {
        if (dirty.get()) {
            statistics.put(clusterService.getLocalNode().id(), snapshot());
            dirty.set(false);
        }
    }
    private void publishStats() {
        // Only publish events if we are the cluster leader for PPPoE Agent stats
        if (!Objects.equals(leadershipService.getLeader(PPPOE_STATISTICS_LEADERSHIP),
                clusterService.getLocalNode().id())) {
            return;
        }
        if (aggregate().counters() != null) {
            aggregate().counters().forEach((counterKey, counterValue) -> {
                // Subscriber-specific counters have the subscriber ID set
                String subscriberId = null;
                if (!counterKey.counterClassKey.equals(PppoeAgentEvent.GLOBAL_COUNTER)) {
                    subscriberId = counterKey.counterClassKey;
                }
                if (delegate != null) {
                    delegate.notify(new PppoeAgentEvent(PppoeAgentEvent.Type.STATS_UPDATE, null,
                                                        counterKey.counterTypeKey.toString(), counterValue,
                                       null, subscriberId));
                }
            });
        } else {
            log.debug("Ignoring 'publishStats' request since counters are null.");
        }
    }
}