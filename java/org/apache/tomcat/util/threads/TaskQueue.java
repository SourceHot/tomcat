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
package org.apache.tomcat.util.threads;

import org.apache.tomcat.util.res.StringManager;

import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

/**
 * As task queue specifically designed to run with a thread pool executor. The
 * task queue is optimised to properly utilize threads within a thread pool
 * executor. If you use a normal queue, the executor will spawn threads when
 * there are idle threads and you wont be able to force items onto the queue
 * itself.
 */
public class TaskQueue extends LinkedBlockingQueue<Runnable> {

    protected static final StringManager sm = StringManager.getManager(TaskQueue.class);
    private static final long serialVersionUID = 1L;
    private static final int DEFAULT_FORCED_REMAINING_CAPACITY = -1;

    private transient volatile ThreadPoolExecutor parent = null;

    // No need to be volatile. This is written and read in a single thread
    // (when stopping a context and firing the listeners)
    private int forcedRemainingCapacity = -1;

    public TaskQueue() {
        super();
    }

    public TaskQueue(int capacity) {
        super(capacity);
    }

    public TaskQueue(Collection<? extends Runnable> c) {
        super(c);
    }

    public void setParent(ThreadPoolExecutor tp) {
        parent = tp;
    }


    /**
     * Used to add a task to the queue if the task has been rejected by the Executor.
     *
     * @param o The task to add to the queue
     * @return {@code true} if the task was added to the queue,
     * otherwise {@code false}
     */
    public boolean force(Runnable o) {
        if (parent == null || parent.isShutdown()) {
            throw new RejectedExecutionException(sm.getString("taskQueue.notRunning"));
        }
        return super.offer(o); //forces the item onto the queue, to be used if the task is rejected
    }


    /**
     * Used to add a task to the queue if the task has been rejected by the Executor.
     *
     * @param o       The task to add to the queue
     * @param timeout The timeout to use when adding the task
     * @param unit    The units in which the timeout is expressed
     * @return {@code true} if the task was added to the queue,
     * otherwise {@code false}
     * @throws InterruptedException If the call is interrupted before the
     *                              timeout expires
     * @deprecated Unused. Will be removed in Tomcat 10.1.x.
     */
    @Deprecated
    public boolean force(Runnable o, long timeout, TimeUnit unit) throws InterruptedException {
        if (parent == null || parent.isShutdown()) {
            throw new RejectedExecutionException(sm.getString("taskQueue.notRunning"));
        }
        return super.offer(o, timeout, unit); //forces the item onto the queue, to be used if the task is rejected
    }


    @Override
    public boolean offer(Runnable o) {
        //we can't do any checks
        if (parent == null) {
            return super.offer(o);
        }
        //we are maxed out on threads, simply queue the object
        if (parent.getPoolSize() == parent.getMaximumPoolSize()) {
            return super.offer(o);
        }
        //we have idle threads, just add it to the queue
        if (parent.getSubmittedCount() <= (parent.getPoolSize())) {
            return super.offer(o);
        }
        //if we have less threads than maximum force creation of a new thread
        if (parent.getPoolSize() < parent.getMaximumPoolSize()) {
            return false;
        }
        //if we reached here, we need to add it to the queue
        return super.offer(o);
    }


    @Override
    public Runnable poll(long timeout, TimeUnit unit)
            throws InterruptedException {
        Runnable runnable = super.poll(timeout, unit);
        if (runnable == null && parent != null) {
            // the poll timed out, it gives an opportunity to stop the current
            // thread if needed to avoid memory leaks.
            parent.stopCurrentThreadIfNeeded();
        }
        return runnable;
    }

    @Override
    public Runnable take() throws InterruptedException {
        if (parent != null && parent.currentThreadShouldBeStopped()) {
            return poll(parent.getKeepAliveTime(TimeUnit.MILLISECONDS),
                    TimeUnit.MILLISECONDS);
            // yes, this may return null (in case of timeout) which normally
            // does not occur with take()
            // but the ThreadPoolExecutor implementation allows this
        }
        return super.take();
    }

    @Override
    public int remainingCapacity() {
        if (forcedRemainingCapacity > DEFAULT_FORCED_REMAINING_CAPACITY) {
            // ThreadPoolExecutor.setCorePoolSize checks that
            // remainingCapacity==0 to allow to interrupt idle threads
            // I don't see why, but this hack allows to conform to this
            // "requirement"
            return forcedRemainingCapacity;
        }
        return super.remainingCapacity();
    }

    public void setForcedRemainingCapacity(int forcedRemainingCapacity) {
        this.forcedRemainingCapacity = forcedRemainingCapacity;
    }

    void resetForcedRemainingCapacity() {
        this.forcedRemainingCapacity = DEFAULT_FORCED_REMAINING_CAPACITY;
    }

}
