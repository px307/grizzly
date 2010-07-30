/*
 * 
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
 * 
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 * 
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 * 
 * Contributor(s):
 * 
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
 *
 */

package com.sun.grizzly.nio;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.CompletionHandler;
import com.sun.grizzly.ConnectionProbe;
import com.sun.grizzly.Processor;
import com.sun.grizzly.ProcessorSelector;
import com.sun.grizzly.Transport;
import com.sun.grizzly.attributes.AttributeHolder;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.GrizzlyFuture;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.ReadResult;
import com.sun.grizzly.StandaloneProcessor;
import com.sun.grizzly.StandaloneProcessorSelector;
import com.sun.grizzly.WriteResult;
import com.sun.grizzly.asyncqueue.TaskQueue;
import com.sun.grizzly.asyncqueue.AsyncReadQueueRecord;
import com.sun.grizzly.asyncqueue.AsyncWriteQueueRecord;
import com.sun.grizzly.attributes.IndexedAttributeHolder;
import com.sun.grizzly.impl.ReadyFutureImpl;
import com.sun.grizzly.utils.ArraySet;
import com.sun.grizzly.utils.LinkedTransferQueue;
import java.util.Queue;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Common {@link Connection} implementation for Java NIO <tt>Connection</tt>s.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractNIOConnection implements NIOConnection {
    private static final Logger logger = Grizzly.logger(AbstractNIOConnection.class);
    
    protected final NIOTransport transport;

    protected volatile int readBufferSize;
    protected volatile int writeBufferSize;

    protected volatile long readTimeoutMillis = 30000;
    protected volatile long writeTimeoutMillis = 30000;

    protected SelectorRunner selectorRunner;
    protected SelectableChannel channel;
    protected SelectionKey selectionKey;
    
    protected volatile Processor processor;
    protected volatile ProcessorSelector processorSelector;
    
    protected final AttributeHolder attributes;

    protected final TaskQueue<AsyncReadQueueRecord> asyncReadQueue;
    protected final TaskQueue<AsyncWriteQueueRecord> asyncWriteQueue;
    
    protected final AtomicBoolean isClosed = new AtomicBoolean(false);

    protected volatile boolean isBlocking;

    protected volatile boolean isStandalone;

    private final Queue<CloseListener> closeListeners =
            new LinkedTransferQueue<CloseListener>();

    /**
     * Connection probes
     */
    protected final ArraySet<ConnectionProbe> monitoringProbes =
            new ArraySet<ConnectionProbe>();

    public AbstractNIOConnection(NIOTransport transport) {
        this.transport = transport;
        asyncReadQueue = TaskQueue.<AsyncReadQueueRecord>createSafeTaskQueue();
        asyncWriteQueue = TaskQueue.<AsyncWriteQueueRecord>createSafeTaskQueue();
        
        attributes = new IndexedAttributeHolder(transport.getAttributeBuilder());
    }

    @Override
    public void configureBlocking(boolean isBlocking) {
        this.isBlocking = isBlocking;
    }

    @Override
    public boolean isBlocking() {
        return isBlocking;
    }

    @Override
    public synchronized void configureStandalone(boolean isStandalone) {
        if (this.isStandalone != isStandalone) {
            this.isStandalone = isStandalone;
            if (isStandalone) {
                processor = StandaloneProcessor.INSTANCE;
                processorSelector = StandaloneProcessorSelector.INSTANCE;
            } else {
                processor = transport.getProcessor();
                processorSelector = transport.getProcessorSelector();
            }
        }
    }

    @Override
    public boolean isStandalone() {
        return isStandalone;
    }

    @Override
    public Transport getTransport() {
        return transport;
    }

    @Override
    public int getReadBufferSize() {
        return readBufferSize;
    }

    @Override
    public void setReadBufferSize(int readBufferSize) {
        this.readBufferSize = readBufferSize;
    }

    @Override
    public int getWriteBufferSize() {
        return writeBufferSize;
    }

    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        this.writeBufferSize = writeBufferSize;
    }

    @Override
    public long getReadTimeout(TimeUnit timeUnit) {
        return timeUnit.convert(readTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setReadTimeout(long timeout, TimeUnit timeUnit) {
        readTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
    }

    @Override
    public long getWriteTimeout(TimeUnit timeUnit) {
        return timeUnit.convert(writeTimeoutMillis, TimeUnit.MILLISECONDS);
    }

    @Override
    public void setWriteTimeout(long timeout, TimeUnit timeUnit) {
        writeTimeoutMillis = TimeUnit.MILLISECONDS.convert(timeout, timeUnit);
    }

    @Override
    public SelectorRunner getSelectorRunner() {
        return selectorRunner;
    }

    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        this.selectorRunner = selectorRunner;
    }

    @Override
    public SelectableChannel getChannel() {
        return channel;
    }

    protected void setChannel(SelectableChannel channel) {
        this.channel = channel;
    }

    @Override
    public SelectionKey getSelectionKey() {
        return selectionKey;
    }

    protected void setSelectionKey(SelectionKey selectionKey) {
        this.selectionKey = selectionKey;
        setChannel(selectionKey.channel());
    }

    @Override
    public Processor obtainProcessor(IOEvent ioEvent) {
        if (processor == null && processorSelector == null) {
            return transport.obtainProcessor(ioEvent, this);
        }

        if (processor != null && processor.isInterested(ioEvent)) {
            return processor;
        } else if (processorSelector != null) {
            final Processor selectedProcessor =
                    processorSelector.select(ioEvent, this);
            if (selectedProcessor != null) {
                return selectedProcessor;
            }
        }

        return null;
    }

    @Override
    public Processor getProcessor() {
        return processor;
    }

    @Override
    public void setProcessor(
            Processor preferableProcessor) {
        this.processor = preferableProcessor;
    }

    @Override
    public ProcessorSelector getProcessorSelector() {
        return processorSelector;
    }

    @Override
    public void setProcessorSelector(
            ProcessorSelector preferableProcessorSelector) {
        this.processorSelector =
                preferableProcessorSelector;
    }

    public TaskQueue<AsyncReadQueueRecord> getAsyncReadQueue() {
        return asyncReadQueue;
    }

    public TaskQueue<AsyncWriteQueueRecord> getAsyncWriteQueue() {
        return asyncWriteQueue;
    }

    @Override
    public AttributeHolder getAttributes() {
        return attributes;
    }

    @Override
    public <M> GrizzlyFuture<ReadResult<M, SocketAddress>> read()
            throws IOException {
        return read(null);
    }

    @Override
    public <M> GrizzlyFuture<ReadResult<M, SocketAddress>> read(
            CompletionHandler<ReadResult<M, SocketAddress>> completionHandler)
            throws IOException {

        final Processor obtainedProcessor = obtainProcessor(IOEvent.READ);
        return obtainedProcessor.read(this, completionHandler);
    }

    @Override
    public <M> GrizzlyFuture<WriteResult<M, SocketAddress>> write(M message)
            throws IOException {
        return write(null, message, null);
    }

    @Override
    public <M> GrizzlyFuture<WriteResult<M, SocketAddress>> write(M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler)
            throws IOException {
        return write(null, message, completionHandler);
    }

    @Override
    public <M> GrizzlyFuture<WriteResult<M, SocketAddress>> write(
            SocketAddress dstAddress, M message,
            CompletionHandler<WriteResult<M, SocketAddress>> completionHandler)
            throws IOException {

        final Processor obtainedProcessor = obtainProcessor(IOEvent.WRITE);
        return obtainedProcessor.write(this, dstAddress, message, completionHandler);
    }

    @Override
    public boolean isOpen() {
        return channel != null && channel.isOpen() && !isClosed.get();
    }

    @Override
    public GrizzlyFuture close() throws IOException {
        if (!isClosed.getAndSet(true)) {
            preClose();
            notifyCloseListeners();
            notifyProbesClose(this);
            return transport.getSelectorHandler().executeInSelectorThread(
                    selectorRunner, new Runnable() {

                @Override
                public void run() {
                    try {
                        ((AbstractNIOTransport) transport).closeConnection(AbstractNIOConnection.this);
                    } catch (IOException e) {
                        logger.log(Level.FINE, "Error during connection close", e);
                    }
                }
            }, null);
        }

        return ReadyFutureImpl.create(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addCloseListener(CloseListener closeListener) {
        // check if connection is still open
        if (!isClosed.get()) {
            // add close listener
            closeListeners.add(closeListener);
            // check the connection state again
            if (isClosed.get() && closeListeners.remove(closeListener)) {
                // if connection was closed during the method call - notify the listener
                try {
                    closeListener.onClosed(this);
                } catch (IOException ignored) {
                }
            }
        } else { // if connection is closed - notify the listener
            try {
                closeListener.onClosed(this);
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeCloseListener(CloseListener closeListener) {
        return closeListeners.remove(closeListener);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyConnectionError(Throwable error) {
        notifyProbesError(this, error);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void addProbes(ConnectionProbe... probes) {
        monitoringProbes.add(probes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean removeProbes(ConnectionProbe... probes) {
        return monitoringProbes.remove(probes);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ConnectionProbe[] getProbes() {
        return monitoringProbes.obtainArrayCopy(ConnectionProbe.class);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearProbes() {
        monitoringProbes.clear();
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the bind event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesBind(AbstractNIOConnection connection) {
        final ConnectionProbe[] probes = connection.monitoringProbes.getArray();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onBindEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the accept event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesAccept(AbstractNIOConnection connection) {
        final ConnectionProbe[] probes =
                connection.monitoringProbes.getArray();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onAcceptEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the connect event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesConnect(AbstractNIOConnection connection) {
        final ConnectionProbe[] probes =
                connection.monitoringProbes.getArray();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onConnectEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the read event.
     */
    protected static void notifyProbesRead(AbstractNIOConnection connection,
            Buffer data, int size) {
        
        final ConnectionProbe[] probes =
                connection.monitoringProbes.getArray();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onReadEvent(connection, data, size);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the write event.
     */
    protected static void notifyProbesWrite(AbstractNIOConnection connection,
            Buffer data, int size) {
        
        final ConnectionProbe[] probes =
                connection.monitoringProbes.getArray();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onWriteEvent(connection, data, size);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the close event.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesClose(AbstractNIOConnection connection) {
        final ConnectionProbe[] probes =
                connection.monitoringProbes.getArray();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onCloseEvent(connection);
            }
        }
    }

    /**
     * Notify registered {@link ConnectionProbe}s about the error.
     *
     * @param connection the <tt>Connection</tt> event occurred on.
     */
    protected static void notifyProbesError(AbstractNIOConnection connection,
            Throwable error) {
        final ConnectionProbe[] probes =
                connection.monitoringProbes.getArray();
        if (probes != null) {
            for (ConnectionProbe probe : probes) {
                probe.onErrorEvent(connection, error);
            }
        }
    }

    /**
     * Notify all close listeners
     */
    private final void notifyCloseListeners() {
        CloseListener closeListener;
        while((closeListener = closeListeners.poll()) != null) {
            try {
                closeListener.onClosed(this);
            } catch (IOException ignored) {
            }
        }
    }

    protected abstract void preClose();

    @Override
    public void enableIOEvent(IOEvent ioEvent) throws IOException {
        final SelectionKeyHandler selectionKeyHandler =
                transport.getSelectionKeyHandler();
        final int interest =
                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent);

        if (interest == 0) return;

        final SelectorHandler selectorHandler = transport.getSelectorHandler();

        selectorHandler.registerKey(selectorRunner, selectionKey,
                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent));
    }

    @Override
    public void disableIOEvent(IOEvent ioEvent) throws IOException {
        final SelectionKeyHandler selectionKeyHandler =
                transport.getSelectionKeyHandler();
        final int interest =
                selectionKeyHandler.ioEvent2SelectionKeyInterest(ioEvent);

        if (interest == 0) return;

        final SelectorHandler selectorHandler = transport.getSelectorHandler();

        selectorHandler.unregisterKey(selectorRunner, selectionKey, interest);
    }
}
