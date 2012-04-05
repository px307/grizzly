/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package org.glassfish.grizzly;

import java.io.IOException;
import java.util.concurrent.Future;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.asyncqueue.WritableMessage;

/**
 * Implementations of this interface are able to write data from a {@link Buffer}
 * to {@link Connection}.
 *
 * There are two basic Writer implementations in Grizzly:
 * {@link org.glassfish.grizzly.asyncqueue.AsyncQueueWriter},
 * {@link org.glassfish.grizzly.nio.tmpselectors.TemporarySelectorWriter}.
 *
 * @author Alexey Stashok
 */
public interface Writer<L> {
    /**
     * Method writes the {@link WritableMessage}.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @return {@link Future}, using which it's possible to check the
     *         result
     */
    public GrizzlyFuture<WriteResult<WritableMessage, L>> write(Connection connection,
            WritableMessage message) throws IOException;

    /**
     * Method writes the {@link WritableMessage}.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     */
    public void write(Connection connection,
            WritableMessage message,
            CompletionHandler<WriteResult<WritableMessage, L>> completionHandler);

    /**
     * Method writes the {@link WritableMessage} to the specific address.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the {@link WritableMessage} will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @return {@link Future}, using which it's possible to check the
     *         result
     */
    public GrizzlyFuture<WriteResult<WritableMessage, L>> write(Connection connection,
            L dstAddress, WritableMessage message);

    /**
     * Method writes the {@link WritableMessage} to the specific address.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the {@link WritableMessage} will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     */
    public void write(Connection connection,
            L dstAddress, WritableMessage message,
            CompletionHandler<WriteResult<WritableMessage, L>> completionHandler);

    /**
     * Method writes the {@link WritableMessage} to the specific address.
     *
     *
     * @param connection the {@link org.glassfish.grizzly.Connection} to write to
     * @param dstAddress the destination address the {@link WritableMessage} will be
     *        sent to
     * @param message the {@link WritableMessage}, from which the data will be written
     * @param completionHandler {@link org.glassfish.grizzly.CompletionHandler},
     *        which will get notified, when write will be completed
     * @param lifeCycleHandler {@link LifeCycleHandler}, which gives developer
     *        finer control over message write process.
     */
    public void write(
            Connection connection,
            L dstAddress, WritableMessage message,
            CompletionHandler<WriteResult<WritableMessage, L>> completionHandler,
            LifeCycleHandler lifeCycleHandler);
    
    /**
     * Return <code>true</code> if the connection has not exceeded it's maximum
     *  size in bytes of pending writes, otherwise <code>false</code>.
     * 
     * This method call is equivalent to call canWrite(connection, <tt>1</tt>);
     * 
     * @param connection the {@link Connection} to test whether or not the
     *  specified number of bytes can be written to.
     * @return <code>true</code> if the connection has not exceeded it's maximum
     *  size in bytes of pending writes, otherwise <code>false</code>
     * 
     * @since 2.3
     */
    boolean canWrite(final Connection connection);

    /**
     * Return <code>true</code> if the connection has not exceeded it's maximum
     *  size in bytes of pending writes and is ready to accept another "size" bytes,
     *  otherwise <code>false</code>.
     * 
     * @param connection the {@link Connection} to test whether or not the
     *  specified number of bytes can be written to.
     * @param size number of bytes to write.
     * @return <code>true</code> if the connection has not exceeded it's maximum
     *  size in bytes of pending writes and is ready to accept another "size" bytes,
     *  otherwise <code>false</code>
     * 
     * @since 2.3
     */
    boolean canWrite(final Connection connection, int size);
    
    /**
     * Registers {@link WriteHandler}, which will be notified ones at least one
     * byte can be written.
     * 
     * This method call is equivalent to call notifyWritePossible(connection, writeHandler, <tt>1</tt>);
     * 
     * Note: using this method from different threads simultaneously may lead
     * to quick situation changes, so at time {@link WriteHandler} is called -
     * the queue may become busy again.
     * 
     * @param connection {@link Connection}
     * @param writeHandler {@link WriteHandler} to be notified.
     * @param size number of bytes queue has to be able to accept before notifying
     *             {@link WriteHandler}.
     * 
     * @since 2.3
     */
    void notifyWritePossible(final Connection connection,
            final WriteHandler writeHandler);

    /**
     * Registers {@link WriteHandler}, which will be notified once {@link Buffer}
     * of "size"-bytes can be written.
     * Note: using this method from different threads simultaneously may lead
     * to quick situation changes, so at time {@link WriteHandler} is called -
     * the queue may become busy again.
     * 
     * @param connection {@link Connection}
     * @param writeHandler {@link WriteHandler} to be notified.
     * @param size number of bytes queue has to be able to accept before notifying
     *             {@link WriteHandler}.
     * 
     * @since 2.3
     */
    void notifyWritePossible(final Connection connection,
            final WriteHandler writeHandler, final int size);
    
    /**
     * Returns the maximum number of write() method reentrants a thread
     * is allowed to made.
     * This is related to possible write()->onComplete()->write()->...
     * chain, which may grow infinitely and cause StackOverflow.
     * Using maxWriteReentrants value it's possible to limit such a chain.
     *
     * @return the maximum number of write() method reentrants a thread
     * is allowed to make.
     */
    int getMaxWriteReentrants();

    /**
     * Sets the maximum number of write() method reentrants a thread
     * is allowed to made.
     * This is related to possible write()->onComplete()->write()->...
     * chain, which may grow infinitely and cause StackOverflow.
     * Using maxWriteReentrants value it's possible to limit such a chain.
     *
     * @param maxWriteReentrants  the maximum number of write() method calls
     *  a thread is allowed to make.
     */
    void setMaxWriteReentrants(int maxWriteReentrants);
    
    /**
     * Returns the current write reentrants counter.
     * Might be useful, if developer wants to use custom notification mechanism,
     * based on on {@link #canWrite(org.glassfish.grizzly.Connection, int)} and
     * various write methods.
     */
    Reentrant getWriteReentrant();

    /**
     * Returns <tt>true</tt>, if max number of write->completion-handler reentrants
     * has been reached for the passed {@link Reentrant} object, and next write
     * will happen in the separate thread.
     * 
     * @param reentrant {@link Reentrant} object.
     * @return <tt>true</tt>, if max number of write->completion-handler reentrants
     * has been reached for the passed {@link Reentrant} object, and next write
     * will happen in the separate thread.
     */
    boolean isMaxReentrantsReached(Reentrant reentrant);

    /**
     * Write reentrants counter
     */
    public static final class Reentrant {
        private int counter;
        
        public int get() {
            return counter;
        }
        
        public int incAndGet() {
            return ++counter;
        }

        public int decAndGet() {
            return --counter;
        }
    }    
}