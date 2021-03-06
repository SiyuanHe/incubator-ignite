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

package org.apache.ignite.internal.processors.query.h2.sql;

import org.apache.ignite.internal.util.typedef.*;
import org.h2.util.*;
import org.h2.value.*;

import java.util.*;

import static org.apache.ignite.internal.processors.query.h2.sql.GridSqlFunctionType.*;

/**
 * Function.
 */
public class GridSqlFunction extends GridSqlElement {
    /** */
    private static final Map<String, GridSqlFunctionType> TYPE_MAP = new HashMap<>();

    /**
     *
     */
    static {
        for (GridSqlFunctionType type : GridSqlFunctionType.values())
            TYPE_MAP.put(type.name(), type);
    }

    /** */
    private final String name;

    /** */
    protected final GridSqlFunctionType type;

    /**  */
    private String castType;

    /**
     * @param type Function type.
     */
    public GridSqlFunction(GridSqlFunctionType type) {
        this(type, type.functionName());
    }

    /**
     * @param type Type.
     * @param name Name.
     */
    private GridSqlFunction(GridSqlFunctionType type, String name) {
        if (name == null)
            throw new NullPointerException();

        if (type == null)
            type = UNKNOWN_FUNCTION;

        this.name = name;
        this.type = type;
    }

    /**
     * @param name Name.
     */
    public GridSqlFunction(String name) {
        this(TYPE_MAP.get(name), name);
    }

    /**
     * @param castType Type for {@link GridSqlFunctionType#CAST} function.
     * @return {@code this}.
     */
    public GridSqlFunction setCastType(String castType) {
        this.castType = castType;

        return this;
    }

    /** {@inheritDoc} */
    @Override public String getSQL() {
        StatementBuilder buff = new StatementBuilder(name);

        if (type == CASE) {
            if (!children.isEmpty())
                buff.append(" ").append(child().getSQL());

            for (int i = 1, len = children.size() - 1; i < len; i += 2) {
                buff.append(" WHEN ").append(child(i).getSQL());
                buff.append(" THEN ").append(child(i + 1).getSQL());
            }
            if (children.size() % 2 == 0)
                buff.append(" ELSE ").append(child(children.size() - 1).getSQL());

            return buff.append(" END").toString();
        }

        buff.append('(');

        if (type == CAST) {
            assert !F.isEmpty(castType) : castType;
            assert children().size() == 1;

            buff.append(child().getSQL()).append(" AS ").append(castType);
        }
        else if (type == CONVERT) {
            assert !F.isEmpty(castType) : castType;
            assert children().size() == 1;

            buff.append(child().getSQL()).append(',').append(castType);
        }
        else if (type == GridSqlFunctionType.EXTRACT) {
            ValueString v = (ValueString)((GridSqlConst)child(0)).value();
            buff.append(v.getString()).append(" FROM ").append(child(1).getSQL());
        }
        else {
            for (GridSqlElement e : children) {
                buff.appendExceptFirst(", ");
                buff.append(e.getSQL());
            }
        }

        return buff.append(')').toString();
    }

    /**
     * @return Name.
     */
    public String name() {
        return name;
    }

    /**
     * @return Type.
     */
    public GridSqlFunctionType type() {
        return type;
    }
}
