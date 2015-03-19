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

package org.apache.ignite.examples.java8.streaming.numbers;

import org.apache.ignite.*;
import org.apache.ignite.cache.query.*;
import org.apache.ignite.examples.java8.*;

import java.util.*;

/**
 * Periodically query popular numbers from the streaming cache.
 * <p>
 * Remote nodes should always be started with special configuration file which
 * enables P2P class loading: {@code 'ignite.{sh|bat} examples/config/example-compute.xml'}.
 * <p>
 * Alternatively you can run {@link ExampleNodeStartup} in another JVM which will
 * start node with {@code examples/config/example-compute.xml} configuration.
 */
public class QueryPopularNumbers {
    public static void main(String[] args) throws Exception {
        // Mark this cluster member as client.
        Ignition.setClientMode(true);

        try (Ignite ignite = Ignition.start("examples/config/example-compute.xml")) {
            // Start new cache or get existing one.
            // The cache is configured with sliding window holding 1 second of the streaming data.
            try (IgniteCache<Integer, Long> stmCache = ignite.createCache(CacheConfig.configure())) {
                if (!ExamplesUtils.hasServerNodes(ignite))
                    return;

                // Query top 10 popular numbers every 5 seconds.
                while (true) {
                    // Select top 10 words.
                    SqlFieldsQuery top10 = new SqlFieldsQuery(
                        "select _key, _val from Long order by _val desc limit 10");

                    // Execute query.
                    List<List<?>> results = stmCache.queryFields(top10).getAll();

                    ExamplesUtils.printQueryResults(results);

                    Thread.sleep(5000);
                }
            }
        }
    }
}