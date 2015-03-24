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

package org.apache.ignite.internal.util.ipc.shmem;

import org.apache.ignite.*;
import org.apache.ignite.internal.util.typedef.internal.*;

import java.io.*;
import java.lang.management.*;
import java.nio.*;
import java.nio.channels.*;
import java.util.*;

/**
 * NOTE: Native library should be loaded, before methods of this class are called. Native library is loaded with: {@link
 * IpcSharedMemoryNativeLoader#load()}.
 */
public class IpcSharedMemoryUtils {
    /** Default lock file name. */
    private static final String LOCK_FILE_NAME = "lock.file";

    /**
     * Allocates shared memory segment and semaphores for IPC exchange.
     *
     * @param tokFileName OS token file name.
     * @param size Memory space size in bytes.
     * @param debug {@code True} to output debug to stdout (will set global flag).
     * @return Shared memory pointer.
     * @throws IgniteCheckedException If failed.
     */
    static native long allocateSystemResources(String tokFileName, int size, boolean debug)
        throws IgniteCheckedException;

    /**
     * Attaches to previously allocated shared memory segment.
     *
     * @param shmemId OS shared memory segment ID.
     * @param debug {@code True} to output debug to stdout (will set global flag).
     * @return Shared memory pointer.
     * @throws IgniteCheckedException If failed.
     */
    static native long attach(int shmemId, boolean debug) throws IgniteCheckedException;

    /**
     * Stops IPC communication. Call {@link #freeSystemResources(String, long, boolean)} after this call.
     *
     * @param shmemPtr Shared memory pointer.
     */
    static native void ipcClose(long shmemPtr);

    /**
     * Frees system resources.
     *
     * @param tokFileName Token file name.
     * @param shmemPtr Shared memory pointer
     * @param force {@code True} to force close.
     */
    static native void freeSystemResources(String tokFileName, long shmemPtr, boolean force);

    /**
     * Frees system resources.
     *
     * @param tokFileName Token file name.
     * @param size Size.
     */
    static native void freeSystemResources(String tokFileName, int size);

    /**
     * @param shMemPtr Shared memory pointer.
     * @param dest Destination buffer.
     * @param dOff Destination offset.
     * @param size Size.
     * @param timeout Operation timeout.
     * @return Read bytes count.
     * @throws IgniteCheckedException If space has been closed.
     * @throws IpcSharedMemoryOperationTimedoutException If operation times out.
     */
    static native long readSharedMemory(long shMemPtr, byte dest[], long dOff, long size, long timeout)
        throws IgniteCheckedException, IpcSharedMemoryOperationTimedoutException;

    /**
     * @param shmemPtr Shared memory pointer.
     * @return Unread count.
     */
    static native int unreadCount(long shmemPtr);

    /**
     * @param shmemPtr Shared memory pointer.
     * @return Shared memory ID.
     */
    static native int sharedMemoryId(long shmemPtr);

    /**
     * @param shmemPtr Shared memory pointer.
     * @return Semaphore set ID.
     */
    static native int semaphoreId(long shmemPtr);

    /**
     * @param shMemPtr Shared memory pointer
     * @param dest Destination buffer.
     * @param dOff Destination offset.
     * @param size Size.
     * @param timeout Operation timeout.
     * @return Read bytes count.
     * @throws IgniteCheckedException If space has been closed.
     * @throws IpcSharedMemoryOperationTimedoutException If operation times out.
     */
    static native long readSharedMemoryByteBuffer(long shMemPtr, ByteBuffer dest, long dOff, long size, long timeout)
        throws IgniteCheckedException, IpcSharedMemoryOperationTimedoutException;

    /**
     * @param shMemPtr Shared memory pointer
     * @param src Source buffer.
     * @param sOff Offset.
     * @param size Size.
     * @param timeout Operation timeout.
     * @throws IgniteCheckedException If space has been closed.
     * @throws IpcSharedMemoryOperationTimedoutException If operation times out.
     */
    static native void writeSharedMemory(long shMemPtr, byte src[], long sOff, long size, long timeout)
        throws IgniteCheckedException, IpcSharedMemoryOperationTimedoutException;

    /**
     * @param shMemPtr Shared memory pointer
     * @param src Source buffer.
     * @param sOff Offset.
     * @param size Size.
     * @param timeout Operation timeout.
     * @throws IgniteCheckedException If space has been closed.
     * @throws IpcSharedMemoryOperationTimedoutException If operation times out.
     */
    static native void writeSharedMemoryByteBuffer(long shMemPtr, ByteBuffer src, long sOff, long size, long timeout)
        throws IgniteCheckedException, IpcSharedMemoryOperationTimedoutException;

    /** @return PID of the current process (-1 on error). */
    public static int pid() {
        // Should be something like this: 1160@mbp.local
        String name = ManagementFactory.getRuntimeMXBean().getName();

        try {
            int idx = name.indexOf('@');

            return idx > 0 ? Integer.parseInt(name.substring(0, idx)) : -1;
        }
        catch (NumberFormatException ignored) {
            return -1;
        }
    }

    /**
     * @param pid PID to check.
     * @return {@code True} if process with passed ID is alive.
     */
    static native boolean alive(int pid);

    /**
     * Returns shared memory ids for Mac OS and Linux platforms.
     *
     * @return Collection of all shared memory IDs in the system.
     * @throws IOException If failed.
     * @throws InterruptedException If failed.
     * @throws IllegalStateException If current OS is not supported.
     */
    static Collection<Integer> sharedMemoryIds() throws IOException, InterruptedException {
        if (U.isMacOs() || U.isLinux())
            return sharedMemoryIdsOnMacOS();
        else
            throw new IllegalStateException("Current OS is not supported.");
    }

    /**
     * @param e Link error.
     * @return Wrapping grid exception.
     */
    static IgniteCheckedException linkError(UnsatisfiedLinkError e) {
        return new IgniteCheckedException("Linkage error due to possible native library, libigniteshmem.so, " +
            "version mismatch (stop all grid nodes, clean up your '/tmp' folder, and try again).", e);
    }

    /**
     * @return Shared memory IDs.
     * @throws IOException If failed.
     * @throws InterruptedException If failed.
     */
    private static Collection<Integer> sharedMemoryIdsOnMacOS() throws IOException, InterruptedException {
        // IPC status from <running system> as of Mon Jan 21 15:33:54 MSK 2013
        // T     ID     KEY        MODE       OWNER    GROUP
        // Shared Memory:
        // m 327680 0x4702fd26 --rw-rw-rw- yzhdanov    staff

        Process proc = Runtime.getRuntime().exec("ipcs -m");

        BufferedReader rdr = new BufferedReader(new InputStreamReader(proc.getInputStream()));

        Collection<Integer> ret = new ArrayList<>();

        try {
            String line;

            while ((line = rdr.readLine()) != null) {
                if (!line.startsWith(getPlatformDependentLineStartFlag()))
                    continue;

                String[] toks = line.split(" ");

                try {
                    ret.add(Integer.parseInt(toks[1]));
                }
                catch (NumberFormatException ignored) {
                    // No-op (just ignore).
                }
            }

            return ret;
        }
        finally {
            proc.waitFor();
        }
    }

    /** @return Flag for {@code ipcs} utility. */
    private static String getPlatformDependentLineStartFlag() {
        if (U.isMacOs())
            return "m ";
        else if (U.isLinux())
            return "0x";
        else
            throw new IllegalStateException("This OS is not supported.");
    }

    /**
     * @param workTokDir Work token directory.
     * @param tokDir Current node token directory.
     * @param log Logger.
     */
    public static void cleanResources(File workTokDir, File tokDir, IgniteLogger log) {
        RandomAccessFile lockFile = null;

        FileLock lock = null;

        try {
            lockFile = new RandomAccessFile(new File(workTokDir, LOCK_FILE_NAME), "rw");

            lock = lockFile.getChannel().lock();

            if (lock != null)
                processTokenDirectory(workTokDir, tokDir, log);
            else if (log.isDebugEnabled())
                log.debug("Token directory is being processed concurrently: " + workTokDir.getAbsolutePath());
        }
        catch (OverlappingFileLockException ignored) {
            if (log.isDebugEnabled())
                log.debug("Token directory is being processed concurrently: " + workTokDir.getAbsolutePath());
        }
        catch (IOException e) {
            U.error(log, "Failed to process directory: " + workTokDir.getAbsolutePath(), e);
        }
        finally {
            U.releaseQuiet(lock);
            U.closeQuiet(lockFile);
        }
    }

    /**
     * @param workTokDir Token directory (common for multiple nodes).
     */
    private static void processTokenDirectory(File workTokDir, File tokDir, IgniteLogger log) {
        for (File f : workTokDir.listFiles()) {
            if (!f.isDirectory()) {
                if (!f.getName().equals(LOCK_FILE_NAME)) {
                    if (log.isDebugEnabled())
                        log.debug("Unexpected file: " + f.getName());
                }

                continue;
            }

            if (f.equals(tokDir)) {
                if (log.isDebugEnabled())
                    log.debug("Skipping own token directory: " + tokDir.getName());

                continue;
            }

            String name = f.getName();

            int pid;

            try {
                pid = Integer.parseInt(name.substring(name.lastIndexOf('-') + 1));
            }
            catch (NumberFormatException ignored) {
                if (log.isDebugEnabled())
                    log.debug("Failed to parse file name: " + name);

                continue;
            }

            // Is process alive?
            if (IpcSharedMemoryUtils.alive(pid)) {
                if (log.isDebugEnabled())
                    log.debug("Skipping alive node: " + pid);

                continue;
            }

            if (log.isDebugEnabled())
                log.debug("Possibly stale token folder: " + f);

            // Process each token under stale token folder.
            File[] shmemToks = f.listFiles();

            if (shmemToks == null)
                // Although this is strange, but is reproducible sometimes on linux.
                return;

            int rmvCnt = 0;

            try {
                for (File f0 : shmemToks) {
                    if (log.isDebugEnabled())
                        log.debug("Processing token file: " + f0.getName());

                    if (f0.isDirectory()) {
                        if (log.isDebugEnabled())
                            log.debug("Unexpected directory: " + f0.getName());
                    }

                    // Token file format: gg-shmem-space-[auto_idx]-[other_party_pid]-[size]
                    String[] toks = f0.getName().split("-");

                    if (toks.length != 6) {
                        if (log.isDebugEnabled())
                            log.debug("Unrecognized token file: " + f0.getName());

                        continue;
                    }

                    int pid0;
                    int size;

                    try {
                        pid0 = Integer.parseInt(toks[4]);
                        size = Integer.parseInt(toks[5]);
                    }
                    catch (NumberFormatException ignored) {
                        if (log.isDebugEnabled())
                            log.debug("Failed to parse file name: " + name);

                        continue;
                    }

                    if (IpcSharedMemoryUtils.alive(pid0)) {
                        if (log.isDebugEnabled())
                            log.debug("Skipping alive process: " + pid0);

                        continue;
                    }

                    if (log.isDebugEnabled())
                        log.debug("Possibly stale token file: " + f0);

                    U.dumpStack(log, "Free [tok=" + f0.getAbsolutePath() + ']');

                    IpcSharedMemoryUtils.freeSystemResources(f0.getAbsolutePath(), size);

                    if (f0.delete()) {
                        if (log.isDebugEnabled())
                            log.debug("Deleted file: " + f0.getName());

                        rmvCnt++;
                    }
                    else if (!f0.exists()) {
                        if (log.isDebugEnabled())
                            log.debug("File has been concurrently deleted: " + f0.getName());

                        rmvCnt++;
                    }
                    else if (log.isDebugEnabled())
                        log.debug("Failed to delete file: " + f0.getName());
                }
            }
            finally {
                // Assuming that no new files can appear, since
                if (rmvCnt == shmemToks.length) {
                    U.delete(f);

                    if (log.isDebugEnabled())
                        log.debug("Deleted empty token directory: " + f.getName());
                }
            }
        }
    }
}
