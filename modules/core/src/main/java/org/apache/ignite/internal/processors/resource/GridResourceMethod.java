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

package org.apache.ignite.internal.processors.resource;

import org.apache.ignite.internal.util.typedef.internal.*;

import java.lang.annotation.*;
import java.lang.reflect.*;

/**
 * Wrapper for data where resource should be injected.
 * Bean contains {@link Method} and {@link Annotation} for that method.
 */
class GridResourceMethod {
    /** Method which used to inject resource. */
    private final Method mtd;

    /** Resource annotation. */
    private final Annotation ann;

    /**
     * Creates new bean.
     *
     * @param mtd Method which used to inject resource.
     * @param ann Resource annotation.
     */
    GridResourceMethod(Method mtd, Annotation ann) {
        assert mtd != null;
        assert ann != null;

        this.mtd = mtd;
        this.ann = ann;
    }

    /**
     * Gets class method object.
     *
     * @return Class method.
     */
    public Method getMethod() {
        return mtd;
    }

    /**
     * Gets annotation for class method object.
     *
     * @return Method annotation.
     */
    public Annotation getAnnotation() {
        return ann;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridResourceMethod.class, this);
    }
}
