/* @java.file.header */

/*  _________        _____ __________________        _____
 *  __  ____/___________(_)______  /__  ____/______ ____(_)_______
 *  _  / __  __  ___/__  / _  __  / _  / __  _  __ `/__  / __  __ \
 *  / /_/ /  _  /    _  /  / /_/ /  / /_/ /  / /_/ / _  /  _  / / /
 *  \____/   /_/     /_/   \_,__/   \____/   \__,_/  /_/   /_/ /_/
 */

package org.gridgain.grid.kernal;

import org.apache.ignite.*;
import org.apache.ignite.cluster.*;
import org.apache.ignite.lang.*;
import org.gridgain.grid.*;
import org.gridgain.grid.kernal.processors.continuous.*;
import org.gridgain.grid.util.typedef.*;
import org.gridgain.grid.util.typedef.internal.*;
import org.jetbrains.annotations.*;

import java.io.*;
import java.util.*;

/**
 * {@link org.apache.ignite.IgniteMessaging} implementation.
 */
public class IgniteMessagingImpl extends IgniteAsyncSupportAdapter implements IgniteMessaging, Externalizable {
    /** */
    private static final long serialVersionUID = 0L;

    /** */
    private GridKernalContext ctx;

    /** */
    private ClusterGroupAdapter prj;

    /**
     * Required by {@link Externalizable}.
     */
    public IgniteMessagingImpl() {
        // No-op.
    }

    /**
     * @param ctx Kernal context.
     * @param prj Projection.
     * @param async Async support flag.
     */
    public IgniteMessagingImpl(GridKernalContext ctx, ClusterGroupAdapter prj, boolean async) {
        super(async);

        this.ctx = ctx;
        this.prj = prj;
    }

    /** {@inheritDoc} */
    @Override public ClusterGroup projection() {
        return prj;
    }

    /** {@inheritDoc} */
    @Override public void send(@Nullable Object topic, Object msg) throws GridException {
        A.notNull(msg, "msg");

        guard();

        try {
            Collection<ClusterNode> snapshot = prj.nodes();

            if (snapshot.isEmpty())
                throw U.emptyTopologyException();

            ctx.io().sendUserMessage(snapshot, msg, topic, false, 0);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void send(@Nullable Object topic, Collection<?> msgs) throws GridException {
        A.ensure(!F.isEmpty(msgs), "msgs cannot be null or empty");

        guard();

        try {
            Collection<ClusterNode> snapshot = prj.nodes();

            if (snapshot.isEmpty())
                throw U.emptyTopologyException();

            for (Object msg : msgs) {
                A.notNull(msg, "msg");

                ctx.io().sendUserMessage(snapshot, msg, topic, false, 0);
            }
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void sendOrdered(@Nullable Object topic, Object msg, long timeout) throws GridException {
        A.notNull(msg, "msg");

        guard();

        try {
            Collection<ClusterNode> snapshot = prj.nodes();

            if (snapshot.isEmpty())
                throw U.emptyTopologyException();

            if (timeout == 0)
                timeout = ctx.config().getNetworkTimeout();

            ctx.io().sendUserMessage(snapshot, msg, topic, true, timeout);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void localListen(@Nullable Object topic, IgniteBiPredicate<UUID, ?> p) {
        A.notNull(p, "p");

        guard();

        try {
            ctx.io().addUserMessageListener(topic, p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void stopLocalListen(@Nullable Object topic, IgniteBiPredicate<UUID, ?> p) {
        A.notNull(p, "p");

        guard();

        try {
            ctx.io().removeUserMessageListener(topic, p);
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public UUID remoteListen(@Nullable Object topic, IgniteBiPredicate<UUID, ?> p) throws GridException {
        A.notNull(p, "p");

        guard();

        try {
            GridContinuousHandler hnd = new GridMessageListenHandler(topic, (IgniteBiPredicate<UUID, Object>)p);

            return saveOrGet(ctx.continuous().startRoutine(hnd, 1, 0, false, prj.predicate()));
        }
        finally {
            unguard();
        }
    }

    /** {@inheritDoc} */
    @Override public void stopRemoteListen(UUID opId) throws GridException {
        A.notNull(opId, "opId");

        saveOrGet(ctx.continuous().stopRoutine(opId));
    }

    /**
     * <tt>ctx.gateway().readLock()</tt>
     */
    private void guard() {
        ctx.gateway().readLock();
    }

    /**
     * <tt>ctx.gateway().readUnlock()</tt>
     */
    private void unguard() {
        ctx.gateway().readUnlock();
    }

    /** {@inheritDoc} */
    @Override public IgniteMessaging enableAsync() {
        return new IgniteMessagingImpl(ctx, prj, true);
    }

    /** {@inheritDoc} */
    @Override public void writeExternal(ObjectOutput out) throws IOException {
        out.writeObject(prj);
    }

    /** {@inheritDoc} */
    @Override public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        prj = (ClusterGroupAdapter)in.readObject();
    }

    /**
     * Reconstructs object on unmarshalling.
     *
     * @return Reconstructed object.
     * @throws ObjectStreamException Thrown in case of unmarshalling error.
     */
    protected Object readResolve() throws ObjectStreamException {
        return prj.message();
    }
}