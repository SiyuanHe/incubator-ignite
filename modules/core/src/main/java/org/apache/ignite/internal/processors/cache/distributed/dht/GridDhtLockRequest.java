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
import org.apache.ignite.internal.*;
import org.apache.ignite.internal.processors.cache.*;
import org.apache.ignite.internal.processors.cache.distributed.*;
import org.apache.ignite.internal.processors.cache.transactions.*;
import org.apache.ignite.internal.processors.cache.version.*;
import org.apache.ignite.internal.util.*;
import org.apache.ignite.internal.util.tostring.*;
import org.apache.ignite.internal.util.typedef.*;
import org.apache.ignite.internal.util.typedef.internal.*;
import org.apache.ignite.lang.*;
import org.apache.ignite.plugin.extensions.communication.*;
import org.apache.ignite.transactions.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.nio.*;
import java.util.*;

/**
 * DHT lock request.
 */
public class GridDhtLockRequest<K, V> extends GridDistributedLockRequest<K, V> {
    /** */
    private static final long serialVersionUID = 0L;

    /** Near keys. */
    @GridToStringInclude
    @GridDirectTransient
    private List<K> nearKeys;

    /** Near keys to lock. */
    @GridToStringExclude
    @GridDirectCollection(byte[].class)
    private List<byte[]> nearKeyBytes;

    /** Invalidate reader flags. */
    private BitSet invalidateEntries;

    /** Mini future ID. */
    private IgniteUuid miniId;

    /** Owner mapped version, if any. */
    @GridToStringInclude
    @GridDirectTransient
    private Map<K, GridCacheVersion> owned;

    /** Owner mapped version bytes. */
    private byte[] ownedBytes;

    /** Topology version. */
    private long topVer;

    /** Subject ID. */
    private UUID subjId;

    /** Task name hash. */
    private int taskNameHash;

    /** Indexes of keys needed to be preloaded. */
    private BitSet preloadKeys;

    /** TTL for read operation. */
    private long accessTtl;

    /**
     * Empty constructor required for {@link Externalizable}.
     */
    public GridDhtLockRequest() {
        // No-op.
    }

    /**
     * @param cacheId Cache ID.
     * @param nodeId Node ID.
     * @param nearXidVer Near transaction ID.
     * @param threadId Thread ID.
     * @param futId Future ID.
     * @param miniId Mini future ID.
     * @param lockVer Cache version.
     * @param topVer Topology version.
     * @param isInTx {@code True} if implicit transaction lock.
     * @param isRead Indicates whether implicit lock is for read or write operation.
     * @param isolation Transaction isolation.
     * @param isInvalidate Invalidation flag.
     * @param timeout Lock timeout.
     * @param dhtCnt DHT count.
     * @param nearCnt Near count.
     * @param txSize Expected transaction size.
     * @param grpLockKey Group lock key.
     * @param partLock {@code True} if partition lock.
     * @param subjId Subject ID.
     * @param taskNameHash Task name hash code.
     * @param accessTtl TTL for read operation.
     */
    public GridDhtLockRequest(
        int cacheId,
        UUID nodeId,
        GridCacheVersion nearXidVer,
        long threadId,
        IgniteUuid futId,
        IgniteUuid miniId,
        GridCacheVersion lockVer,
        long topVer,
        boolean isInTx,
        boolean isRead,
        TransactionIsolation isolation,
        boolean isInvalidate,
        long timeout,
        int dhtCnt,
        int nearCnt,
        int txSize,
        @Nullable IgniteTxKey grpLockKey,
        boolean partLock,
        @Nullable UUID subjId,
        int taskNameHash,
        long accessTtl
    ) {
        super(cacheId,
            nodeId,
            nearXidVer,
            threadId,
            futId,
            lockVer,
            isInTx,
            isRead,
            isolation,
            isInvalidate,
            timeout,
            dhtCnt == 0 ? nearCnt : dhtCnt,
            txSize,
            grpLockKey,
            partLock);

        this.topVer = topVer;

        nearKeyBytes = nearCnt == 0 ? Collections.<byte[]>emptyList() : new ArrayList<byte[]>(nearCnt);
        nearKeys = nearCnt == 0 ? Collections.<K>emptyList() : new ArrayList<K>(nearCnt);
        invalidateEntries = new BitSet(dhtCnt == 0 ? nearCnt : dhtCnt);

        assert miniId != null;

        this.miniId = miniId;
        this.subjId = subjId;
        this.taskNameHash = taskNameHash;
        this.accessTtl = accessTtl;
    }

    /** {@inheritDoc} */
    @Override public boolean allowForStartup() {
        return true;
    }

    /**
     * @return Near node ID.
     */
    public UUID nearNodeId() {
        return nodeId();
    }

    /**
     * @return Subject ID.
     */
    public UUID subjectId() {
        return subjId;
    }

    /**
     * @return Task name hash.
     */
    public int taskNameHash() {
        return taskNameHash;
    }

    /**
     * @return Topology version.
     */
    @Override public long topologyVersion() {
        return topVer;
    }

    /**
     * @return Near keys.
     */
    public List<byte[]> nearKeyBytes() {
        return nearKeyBytes == null ? Collections.<byte[]>emptyList() : nearKeyBytes;
    }

    /**
     * Adds a Near key.
     *
     * @param key Key.
     * @param keyBytes Key bytes.
     * @param ctx Context.
     * @throws IgniteCheckedException If failed.
     */
    public void addNearKey(K key, byte[] keyBytes, GridCacheSharedContext<K, V> ctx) throws IgniteCheckedException {
        if (ctx.deploymentEnabled())
            prepareObject(key, ctx);

        nearKeys.add(key);

        if (keyBytes != null)
            nearKeyBytes.add(keyBytes);
    }

    /**
     * @return Near keys.
     */
    public List<K> nearKeys() {
        return nearKeys == null ? Collections.<K>emptyList() : nearKeys;
    }

    /**
     * Adds a DHT key.
     *
     * @param key Key.
     * @param keyBytes Key bytes.
     * @param invalidateEntry Flag indicating whether node should attempt to invalidate reader.
     * @param ctx Context.
     * @throws IgniteCheckedException If failed.
     */
    public void addDhtKey(
        K key,
        byte[] keyBytes,
        boolean invalidateEntry,
        GridCacheContext<K, V> ctx
    ) throws IgniteCheckedException {
        invalidateEntries.set(idx, invalidateEntry);

        addKeyBytes(key, keyBytes, false, null, ctx);
    }

    /**
     * Marks last added key for preloading.
     */
    public void markLastKeyForPreload() {
        assert idx > 0;

        if (preloadKeys == null)
            preloadKeys = new BitSet();

        preloadKeys.set(idx - 1, true);
    }

    /**
     * @param idx Key index.
     * @return {@code True} if need to preload key with given index.
     */
    public boolean needPreloadKey(int idx) {
        return preloadKeys != null && preloadKeys.get(idx);
    }

    /**
     * Sets owner and its mapped version.
     *
     * @param key Key.
     * @param keyBytes Key bytes.
     * @param ownerMapped Owner mapped version.
     */
    public void owned(K key, byte[] keyBytes, GridCacheVersion ownerMapped) {
        if (owned == null)
            owned = new GridLeanMap<>(3);

        owned.put(key, ownerMapped);
    }

    /**
     * @param key Key.
     * @return Owner and its mapped versions.
     */
    @Nullable public GridCacheVersion owned(K key) {
        return owned == null ? null : owned.get(key);
    }

    /**
     * @param idx Entry index to check.
     * @return {@code True} if near entry should be invalidated.
     */
    public boolean invalidateNearEntry(int idx) {
        return invalidateEntries.get(idx);
    }

    /**
     * @return Mini ID.
     */
    public IgniteUuid miniId() {
        return miniId;
    }

    /**
     * @return TTL for read operation.
     */
    public long accessTtl() {
        return accessTtl;
    }

    /** {@inheritDoc}
     * @param ctx*/
    @Override public void prepareMarshal(GridCacheSharedContext<K, V> ctx) throws IgniteCheckedException {
        super.prepareMarshal(ctx);

        assert F.isEmpty(nearKeys) || !F.isEmpty(nearKeyBytes);

        if (owned != null)
            ownedBytes = CU.marshal(ctx, owned);
    }

    /** {@inheritDoc} */
    @Override public void finishUnmarshal(GridCacheSharedContext<K, V> ctx, ClassLoader ldr) throws IgniteCheckedException {
        super.finishUnmarshal(ctx, ldr);

        if (nearKeys == null && nearKeyBytes != null)
            nearKeys = unmarshalCollection(nearKeyBytes, ctx, ldr);

        if (ownedBytes != null)
            owned = ctx.marshaller().unmarshal(ownedBytes, ldr);
    }

    /** {@inheritDoc} */
    @Override public boolean writeTo(ByteBuffer buf, MessageWriter writer) {
        writer.setBuffer(buf);

        if (!super.writeTo(buf, writer))
            return false;

        if (!writer.isTypeWritten()) {
            if (!writer.writeByte(null, directType()))
                return false;

            writer.onTypeWritten();
        }

        switch (writer.state()) {
            case 22:
                if (!writer.writeLong("accessTtl", accessTtl))
                    return false;

                writer.incrementState();

            case 23:
                if (!writer.writeBitSet("invalidateEntries", invalidateEntries))
                    return false;

                writer.incrementState();

            case 24:
                if (!writer.writeIgniteUuid("miniId", miniId))
                    return false;

                writer.incrementState();

            case 25:
                if (!writer.writeCollection("nearKeyBytes", nearKeyBytes, Type.BYTE_ARR))
                    return false;

                writer.incrementState();

            case 26:
                if (!writer.writeByteArray("ownedBytes", ownedBytes))
                    return false;

                writer.incrementState();

            case 27:
                if (!writer.writeBitSet("preloadKeys", preloadKeys))
                    return false;

                writer.incrementState();

            case 28:
                if (!writer.writeUuid("subjId", subjId))
                    return false;

                writer.incrementState();

            case 29:
                if (!writer.writeInt("taskNameHash", taskNameHash))
                    return false;

                writer.incrementState();

            case 30:
                if (!writer.writeLong("topVer", topVer))
                    return false;

                writer.incrementState();

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public boolean readFrom(ByteBuffer buf) {
        reader.setBuffer(buf);

        if (!super.readFrom(buf))
            return false;

        switch (readState) {
            case 22:
                accessTtl = reader.readLong("accessTtl");

                if (!reader.isLastRead())
                    return false;

                readState++;

            case 23:
                invalidateEntries = reader.readBitSet("invalidateEntries");

                if (!reader.isLastRead())
                    return false;

                readState++;

            case 24:
                miniId = reader.readIgniteUuid("miniId");

                if (!reader.isLastRead())
                    return false;

                readState++;

            case 25:
                nearKeyBytes = reader.readCollection("nearKeyBytes", Type.BYTE_ARR);

                if (!reader.isLastRead())
                    return false;

                readState++;

            case 26:
                ownedBytes = reader.readByteArray("ownedBytes");

                if (!reader.isLastRead())
                    return false;

                readState++;

            case 27:
                preloadKeys = reader.readBitSet("preloadKeys");

                if (!reader.isLastRead())
                    return false;

                readState++;

            case 28:
                subjId = reader.readUuid("subjId");

                if (!reader.isLastRead())
                    return false;

                readState++;

            case 29:
                taskNameHash = reader.readInt("taskNameHash");

                if (!reader.isLastRead())
                    return false;

                readState++;

            case 30:
                topVer = reader.readLong("topVer");

                if (!reader.isLastRead())
                    return false;

                readState++;

        }

        return true;
    }

    /** {@inheritDoc} */
    @Override public byte directType() {
        return 30;
    }

    /** {@inheritDoc} */
    @Override public String toString() {
        return S.toString(GridDhtLockRequest.class, this, "nearKeyBytesSize", nearKeyBytes.size(),
            "super", super.toString());
    }
}
