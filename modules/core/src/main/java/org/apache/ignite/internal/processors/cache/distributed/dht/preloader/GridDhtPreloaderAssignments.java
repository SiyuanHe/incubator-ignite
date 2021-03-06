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

package org.apache.ignite.internal.processors.cache.distributed.dht.preloader;

import org.apache.ignite.cluster.*;
import org.apache.ignite.internal.util.tostring.*;
import org.apache.ignite.internal.util.typedef.internal.*;

import java.util.concurrent.*;

/**
 * Partition to node assignments.
 */
public class GridDhtPreloaderAssignments<K, V> extends
    ConcurrentHashMap<ClusterNode, GridDhtPartitionDemandMessage<K, V>> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Exchange future. */
    @GridToStringExclude
    private final GridDhtPartitionsExchangeFuture<K, V> exchFut;

    /** Last join order. */
    private final long topVer;

    /**
     * @param exchFut Exchange future.
     * @param topVer Last join order.
     */
    public GridDhtPreloaderAssignments(GridDhtPartitionsExchangeFuture<K, V> exchFut, long topVer) {
        assert exchFut != null;
        assert topVer > 0;

        this.exchFut = exchFut;
        this.topVer = topVer;
    }

    /**
     * @return Exchange future.
     */
    GridDhtPartitionsExchangeFuture<K, V> exchangeFuture() {
        return exchFut;
    }

    /**
     * @return Topology version.
     */
    long topologyVersion() {
        return topVer;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtPreloaderAssignments.class, this, "exchId", exchFut.exchangeId(),
            "super", super.toString());
    }
}

