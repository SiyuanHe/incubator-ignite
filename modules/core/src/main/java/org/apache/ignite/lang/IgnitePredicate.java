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

package org.apache.ignite.lang;

import java.io.*;

/**
 * Defines a predicate which accepts a parameter and returns {@code true} or {@code false}. In
 * Ignite, predicates are generally used for filtering nodes within grid projections, or for
 * providing atomic filters when performing cache operation, like in
 * {@link org.apache.ignite.cache.GridCache#put(Object, Object, IgnitePredicate[])} method.
 *
 * @param <E> Type of predicate parameter.
 */
public interface IgnitePredicate<E> extends Serializable {
    /**
     * Predicate body.
     *
     * @param e Predicate parameter.
     * @return Return value.
     */
    public boolean apply(E e);
}
