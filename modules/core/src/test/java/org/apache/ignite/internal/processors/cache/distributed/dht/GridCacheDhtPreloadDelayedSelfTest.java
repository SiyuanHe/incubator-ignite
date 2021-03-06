/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ignite.internal.processors.cache.distributed.dht;

import org.apache.ignite.*;
import org.apache.ignite.cache.*;
import org.apache.ignite.cache.affinity.*;
import org.apache.ignite.cache.affinity.consistenthash.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.configuration.*;
import org.apache.ignite.events.*;
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.processors.cache.distributed.dht.preloader.*;
import org.apache.ignite.internal.processors.cache.distributed.near.*;
import org.apache.ignite.internal.util.typedef.*;
import org.apache.ignite.internal.util.typedef.internal.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.spi.discovery.tcp.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.*;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.*;
import org.apache.ignite.testframework.*;
import org.apache.ignite.testframework.junits.common.*;

import java.util.*;
import java.util.concurrent.*;

import static org.apache.ignite.cache.CacheAtomicityMode.*;
import static org.apache.ignite.cache.CacheDistributionMode.*;
import static org.apache.ignite.cache.CacheMode.*;
import static org.apache.ignite.cache.CachePreloadMode.*;

/**
 * Test cases for partitioned cache {@link GridDhtPreloader preloader}.
 */
public class GridCacheDhtPreloadDelayedSelfTest extends GridCommonAbstractTest {
    /** Key count. */
    private static final int KEY_CNT = 100;

    /** Preload delay. */
    private static final int PRELOAD_DELAY = 5000;

    /** Preload mode. */
    private CachePreloadMode preloadMode = ASYNC;

    /** Preload delay. */
    private long delay = -1;

    /** IP finder. */
    private TcpDiscoveryIpFinder ipFinder = new TcpDiscoveryVmIpFinder(true);

    /** {@inheritDoc} */
    @Override protected IgniteConfiguration getConfiguration(String gridName) throws Exception {
        IgniteConfiguration c = super.getConfiguration(gridName);

        assert preloadMode != null;

        CacheConfiguration cc = defaultCacheConfiguration();

        cc.setCacheMode(PARTITIONED);
        cc.setWriteSynchronizationMode(CacheWriteSynchronizationMode.FULL_SYNC);
        cc.setPreloadMode(preloadMode);
        cc.setPreloadPartitionedDelay(delay);
        cc.setAffinity(new CacheConsistentHashAffinityFunction(false, 128));
        cc.setBackups(1);
        cc.setAtomicityMode(TRANSACTIONAL);
        cc.setDistributionMode(NEAR_PARTITIONED);

        TcpDiscoverySpi disco = new TcpDiscoverySpi();

        disco.setIpFinder(ipFinder);
        disco.setMaxMissedHeartbeats(Integer.MAX_VALUE);

        c.setDiscoverySpi(disco);
        c.setCacheConfiguration(cc);

        return c;
    }

    /** {@inheritDoc} */
    @Override protected void afterTest() throws Exception {
        stopAllGrids();
    }

    /** @throws Exception If failed. */
    public void testManualPreload() throws Exception {
        delay = -1;

        Ignite g0 = startGrid(0);

        int cnt = KEY_CNT;

        IgniteCache<String, Integer> c0 = g0.jcache(null);

        for (int i = 0; i < cnt; i++)
            c0.put(Integer.toString(i), i);

        Ignite g1 = startGrid(1);
        Ignite g2 = startGrid(2);

        IgniteCache<String, Integer> c1 = g1.jcache(null);
        IgniteCache<String, Integer> c2 = g2.jcache(null);

        for (int i = 0; i < cnt; i++)
            assertNull(c1.localPeek(Integer.toString(i), CachePeekMode.ONHEAP));

        for (int i = 0; i < cnt; i++)
            assertNull(c2.localPeek(Integer.toString(i), CachePeekMode.ONHEAP));

        final CountDownLatch l1 = new CountDownLatch(1);
        final CountDownLatch l2 = new CountDownLatch(1);

        g1.events().localListen(new IgnitePredicate<Event>() {
            @Override public boolean apply(Event evt) {
                l1.countDown();

                return true;
            }
        }, EventType.EVT_CACHE_PRELOAD_STOPPED);

        g2.events().localListen(new IgnitePredicate<Event>() {
            @Override public boolean apply(Event evt) {
                l2.countDown();

                return true;
            }
        }, EventType.EVT_CACHE_PRELOAD_STOPPED);

        info("Beginning to wait for cache1 repartition.");

        GridDhtCacheAdapter<String, Integer> d0 = dht(0);
        GridDhtCacheAdapter<String, Integer> d1 = dht(1);
        GridDhtCacheAdapter<String, Integer> d2 = dht(2);

        checkMaps(false, d0, d1, d2);

        // Force preload.
        internalCache(c1).forceRepartition();

        l1.await();

        info("Cache1 is repartitioned.");

        checkMaps(false, d0, d1, d2);

        info("Beginning to wait for cache2 repartition.");

        // Force preload.
        internalCache(c2).forceRepartition();

        l2.await();

        info("Cache2 is repartitioned.");

        checkMaps(true, d0, d1, d2);

        checkCache(c0, cnt);
        checkCache(c1, cnt);
        checkCache(c2, cnt);
    }

    /** @throws Exception If failed. */
    public void testDelayedPreload() throws Exception {
        delay = PRELOAD_DELAY;

        Ignite g0 = startGrid(0);

        int cnt = KEY_CNT;

        IgniteCache<String, Integer> c0 = g0.jcache(null);

        for (int i = 0; i < cnt; i++)
            c0.put(Integer.toString(i), i);

        Ignite g1 = startGrid(1);
        Ignite g2 = startGrid(2);

        IgniteCache<String, Integer> c1 = g1.jcache(null);
        IgniteCache<String, Integer> c2 = g2.jcache(null);

        for (int i = 0; i < cnt; i++)
            assertNull(c1.localPeek(Integer.toString(i), CachePeekMode.ONHEAP));

        for (int i = 0; i < cnt; i++)
            assertNull(c2.localPeek(Integer.toString(i), CachePeekMode.ONHEAP));

        final CountDownLatch l1 = new CountDownLatch(1);
        final CountDownLatch l2 = new CountDownLatch(1);

        g1.events().localListen(new IgnitePredicate<Event>() {
            @Override public boolean apply(Event evt) {
                l1.countDown();

                return true;
            }
        }, EventType.EVT_CACHE_PRELOAD_STOPPED);

        g2.events().localListen(new IgnitePredicate<Event>() {
            @Override public boolean apply(Event evt) {
                l2.countDown();

                return true;
            }
        }, EventType.EVT_CACHE_PRELOAD_STOPPED);

        U.sleep(1000);

        GridDhtCacheAdapter<String, Integer> d0 = dht(0);
        GridDhtCacheAdapter<String, Integer> d1 = dht(1);
        GridDhtCacheAdapter<String, Integer> d2 = dht(2);

        info("Beginning to wait for caches repartition.");

        checkMaps(false, d0, d1, d2);

        assert l1.await(PRELOAD_DELAY * 3 / 2, TimeUnit.MILLISECONDS);

        assert l2.await(PRELOAD_DELAY * 3 / 2, TimeUnit.MILLISECONDS);

        U.sleep(1000);

        info("Caches are repartitioned.");

        checkMaps(true, d0, d1, d2);

        checkCache(c0, cnt);
        checkCache(c1, cnt);
        checkCache(c2, cnt);
    }

    /** @throws Exception If failed. */
    public void testAutomaticPreload() throws Exception {
        delay = 0;
        preloadMode = CachePreloadMode.SYNC;

        Ignite g0 = startGrid(0);

        int cnt = KEY_CNT;

        IgniteCache<String, Integer> c0 = g0.jcache(null);

        for (int i = 0; i < cnt; i++)
            c0.put(Integer.toString(i), i);

        Ignite g1 = startGrid(1);
        Ignite g2 = startGrid(2);

        IgniteCache<String, Integer> c1 = g1.jcache(null);
        IgniteCache<String, Integer> c2 = g2.jcache(null);

        GridDhtCacheAdapter<String, Integer> d0 = dht(0);
        GridDhtCacheAdapter<String, Integer> d1 = dht(1);
        GridDhtCacheAdapter<String, Integer> d2 = dht(2);

        checkMaps(true, d0, d1, d2);

        checkCache(c0, cnt);
        checkCache(c1, cnt);
        checkCache(c2, cnt);
    }

    /** @throws Exception If failed. */
    public void testAutomaticPreloadWithEmptyCache() throws Exception {
        preloadMode = SYNC;

        delay = 0;

        Collection<Ignite> ignites = new ArrayList<>();

        try {
            for (int i = 0; i < 5; i++) {
                ignites.add(startGrid(i));

                awaitPartitionMapExchange();

                for (Ignite g : ignites) {
                    info(">>> Checking affinity for grid: " + g.name());

                    GridDhtPartitionTopology<Integer, String> top = topology(g);

                    GridDhtPartitionFullMap fullMap = top.partitionMap(true);

                    for (Map.Entry<UUID, GridDhtPartitionMap> fe : fullMap.entrySet()) {
                        UUID nodeId = fe.getKey();

                        GridDhtPartitionMap m = fe.getValue();

                        for (Map.Entry<Integer, GridDhtPartitionState> e : m.entrySet()) {
                            int p = e.getKey();
                            GridDhtPartitionState state = e.getValue();

                            Collection<ClusterNode> nodes = affinityNodes(g, p);

                            Collection<UUID> nodeIds = U.nodeIds(nodes);

                            assert nodeIds.contains(nodeId) : "Invalid affinity mapping [nodeId=" + nodeId +
                                ", part=" + p + ", state=" + state + ", grid=" + G.ignite(nodeId).name() +
                                ", affNames=" + U.nodes2names(nodes) + ", affIds=" + nodeIds + ']';
                        }
                    }
                }
            }
        }
        finally {
            stopAllGrids();
        }
    }

    /** @throws Exception If failed. */
    public void testManualPreloadSyncMode() throws Exception {
        preloadMode = CachePreloadMode.SYNC;
        delay = -1;

        try {
            startGrid(0);
        }
        finally {
            stopAllGrids();
        }
    }

    /** @throws Exception If failed. */
    public void testPreloadManyNodes() throws Exception {
        delay = 0;
        preloadMode = ASYNC;

        startGridsMultiThreaded(9);

        U.sleep(2000);

        try {
            delay = -1;
            preloadMode = ASYNC;

            Ignite g = startGrid(9);

            info(">>> Starting manual preload");

            long start = System.currentTimeMillis();

            internalCache(g.jcache(null)).forceRepartition().get();

            info(">>> Finished preloading of empty cache in " + (System.currentTimeMillis() - start) + "ms.");
        }
        finally {
            stopAllGrids();
        }
    }

    /**
     * @param g Grid.
     * @return Topology.
     */
    private GridDhtPartitionTopology<Integer, String> topology(Ignite g) {
        return ((GridNearCacheAdapter<Integer, String>)((IgniteKernal)g).<Integer, String>internalCache()).dht().topology();
    }

    /**
     * @param g Grid.
     * @return Affinity.
     */
    private CacheAffinity<Object> affinity(Ignite g) {
        return g.affinity(null);
    }

    /**
     * @param g Grid.
     * @param p Partition.
     * @return Affinity nodes.
     */
    private Collection<ClusterNode> affinityNodes(Ignite g, int p) {
        return affinity(g).mapPartitionToPrimaryAndBackups(p);
    }

    /**
     * Checks if keys are present.
     *
     * @param c Cache.
     * @param keyCnt Key count.
     */
    private void checkCache(IgniteCache<String, Integer> c, int keyCnt) {
        Ignite g = c.unwrap(Ignite.class);

        for (int i = 0; i < keyCnt; i++) {
            String key = Integer.toString(i);

            if (affinity(c).isPrimaryOrBackup(g.cluster().localNode(), key))
                assertEquals(Integer.valueOf(i), c.localPeek(key, CachePeekMode.ONHEAP));
        }
    }

    /**
     * Checks maps for equality.
     *
     * @param strict Strict check flag.
     * @param caches Maps to compare.
     */
    private void checkMaps(final boolean strict, final GridDhtCacheAdapter<String, Integer>... caches)
        throws IgniteInterruptedCheckedException {
        if (caches.length < 2)
            return;

        GridTestUtils.retryAssert(log, 50, 500, new CAX() {
            @Override public void applyx() {
                info("Checking partition maps.");

                for (int i = 0; i < caches.length; i++)
                    info("Partition map for node " + i + ": " + caches[i].topology().partitionMap(false).toFullString());

                GridDhtPartitionFullMap orig = caches[0].topology().partitionMap(true);

                for (int i = 1; i < caches.length; i++) {
                    GridDhtPartitionFullMap cmp = caches[i].topology().partitionMap(true);

                    assert orig.keySet().equals(cmp.keySet());

                    for (Map.Entry<UUID, GridDhtPartitionMap> entry : orig.entrySet()) {
                        UUID nodeId = entry.getKey();

                        GridDhtPartitionMap nodeMap = entry.getValue();

                        GridDhtPartitionMap cmpMap = cmp.get(nodeId);

                        assert cmpMap != null;

                        assert nodeMap.keySet().equals(cmpMap.keySet());

                        for (Map.Entry<Integer, GridDhtPartitionState> nodeEntry : nodeMap.entrySet()) {
                            GridDhtPartitionState state = cmpMap.get(nodeEntry.getKey());

                            assert state != null;
                            assert state != GridDhtPartitionState.EVICTED;
                            assert !strict || state == GridDhtPartitionState.OWNING : "Invalid partition state: " + state;
                            assert state == nodeEntry.getValue();
                        }
                    }
                }
            }
        });

    }
}
