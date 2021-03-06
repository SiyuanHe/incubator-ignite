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

package org.apache.ignite.examples.datagrid;

import org.apache.ignite.*;
import org.apache.ignite.events.*;
import org.apache.ignite.lang.*;

import java.util.*;

import static org.apache.ignite.events.EventType.*;

/**
 * This examples demonstrates events API. Note that ignite events are disabled by default and
 * must be specifically enabled, just like in {@code examples/config/example-cache.xml} file.
 * <p>
 * Remote nodes should always be started with special configuration file which
 * enables P2P class loading: {@code 'ignite.{sh|bat} examples/config/example-cache.xml'}.
 * <p>
 * Alternatively you can run {@link CacheNodeStartup} in another JVM which will
 * start node with {@code examples/config/example-cache.xml} configuration.
 */
public class CacheEventsExample {
    /** Cache name. */
    private static final String CACHE_NAME = "partitioned";

    /**
     * Executes example.
     *
     * @param args Command line arguments, none required.
     * @throws IgniteException If example execution failed.
     */
    public static void main(String[] args) throws IgniteException, InterruptedException {
        try (Ignite ignite = Ignition.start("examples/config/example-cache.xml")) {
            System.out.println();
            System.out.println(">>> Cache events example started.");

            final IgniteCache<Integer, String> cache = ignite.jcache(CACHE_NAME);

            // Clean up caches on all nodes before run.
            cache.clear();

            // This optional local callback is called for each event notification
            // that passed remote predicate listener.
            IgniteBiPredicate<UUID, CacheEvent> locLsnr = new IgniteBiPredicate<UUID, CacheEvent>() {
                @Override public boolean apply(UUID uuid, CacheEvent evt) {
                    System.out.println("Received event [evt=" + evt.name() + ", key=" + evt.key() +
                        ", oldVal=" + evt.oldValue() + ", newVal=" + evt.newValue());

                    return true; // Continue listening.
                }
            };

            // Remote listener which only accepts events for keys that are
            // greater or equal than 10 and if event node is primary for this key.
            IgnitePredicate<CacheEvent> rmtLsnr = new IgnitePredicate<CacheEvent>() {
                @Override public boolean apply(CacheEvent evt) {
                    System.out.println("Cache event [name=" + evt.name() + ", key=" + evt.key() + ']');

                    int key = evt.key();

                    return key >= 10 && ignite.affinity(CACHE_NAME).isPrimary(ignite.cluster().localNode(), key);
                }
            };

            // Subscribe to specified cache events on all nodes that have cache running.
            // Cache events are explicitly enabled in examples/config/example-cache.xml file.
            ignite.events(ignite.cluster().forCacheNodes(CACHE_NAME)).remoteListen(locLsnr, rmtLsnr,
                EVT_CACHE_OBJECT_PUT, EVT_CACHE_OBJECT_READ, EVT_CACHE_OBJECT_REMOVED);

            // Generate cache events.
            for (int i = 0; i < 20; i++)
                cache.put(i, Integer.toString(i));

            // Wait for a while while callback is notified about remaining puts.
            Thread.sleep(2000);
        }
    }
}
