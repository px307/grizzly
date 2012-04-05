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

package org.glassfish.grizzly.nio.transport;

import java.io.IOException;
import java.net.SocketAddress;
import java.util.concurrent.ExecutionException;
import java.util.logging.Filter;
import org.glassfish.grizzly.*;
import org.glassfish.grizzly.asyncqueue.LifeCycleHandler;
import org.glassfish.grizzly.asyncqueue.MessageCloner;
import org.glassfish.grizzly.asyncqueue.WritableMessage;
import org.glassfish.grizzly.filterchain.*;
import org.glassfish.grizzly.memory.Buffers;

/**
 * The {@link TCPNIOTransport}'s transport {@link Filter} implementation
 * 
 * @author Alexey Stashok
 */
public final class TCPNIOTransportFilter extends BaseFilter {
    private final TCPNIOTransport transport;

    TCPNIOTransportFilter(final TCPNIOTransport transport) {
        this.transport = transport;
    }

    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {
        final TCPNIOConnection connection = (TCPNIOConnection) ctx.getConnection();
        final boolean isBlocking = ctx.getTransportContext().isBlocking();

        final Buffer buffer;
        if (!isBlocking) {
            buffer = transport.read(connection, null);
        } else {
            GrizzlyFuture<ReadResult<Buffer, SocketAddress>> future =
                    transport.getTemporarySelectorIO().getReader().read(
                    connection, null);
            try {
                ReadResult<Buffer, SocketAddress> result = future.get();
                buffer = result.getMessage();
                future.recycle(true);
            } catch (ExecutionException e) {
                final Throwable cause = e.getCause();
                if (cause instanceof IOException) {
                    throw (IOException) cause;
                }
                
                throw new IOException(cause);
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
        
        if (buffer == null || buffer.position() == 0) {
            return ctx.getStopAction();
        } else {
            buffer.trim();
            
            ctx.setMessage(buffer);
            ctx.setAddress(connection.getPeerAddress());
        }

        return ctx.getInvokeAction();
    }

    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleWrite(final FilterChainContext ctx)
            throws IOException {
        final WritableMessage message = ctx.getMessage();
        if (message != null) {
            ctx.setMessage(null);
            final Connection connection = ctx.getConnection();
            final FilterChainContext.TransportContext transportContext =
                    ctx.getTransportContext();

            final CompletionHandler completionHandler = transportContext.getCompletionHandler();
            final MessageCloner cloner = transportContext.getMessageCloner();
            final LifeCycleHandler lifeCycleHandler = transportContext.getLifeCycleHandler();
            
            transportContext.setCompletionHandler(null);
            transportContext.setMessageCloner(null);
            transportContext.setLifeCycleHandler(null);

            if (!transportContext.isBlocking()) {
                transport.getAsyncQueueIO().getWriter().write(connection, null,
                        message, completionHandler, lifeCycleHandler, cloner);
            } else {
                transport.getTemporarySelectorIO().getWriter().write(connection,
                        null, message, completionHandler, lifeCycleHandler);
            }
        }


        return ctx.getInvokeAction();
    }

    @Override
    @SuppressWarnings("unchecked")
    public NextAction handleEvent(final FilterChainContext ctx,
            final Event event) throws IOException {
        if (event.type() == TransportFilter.FlushEvent.TYPE) {
            final Connection connection = ctx.getConnection();
            final FilterChainContext.TransportContext transportContext =
                    ctx.getTransportContext();

            if (transportContext.getCompletionHandler() != null) {
                throw new IllegalStateException("TransportContext CompletionHandler must be null");
            }

            final CompletionHandler completionHandler =
                    ((TransportFilter.FlushEvent) event).getCompletionHandler();

            transport.getWriter(transportContext.isBlocking()).write(connection,
                    Buffers.EMPTY_BUFFER, completionHandler);
        }
        
        return ctx.getInvokeAction();
    }

    @Override
    public void exceptionOccurred(final FilterChainContext ctx,
            final Throwable error) {

        final Connection connection = ctx.getConnection();
        if (connection != null) {
            connection.closeSilently();
        }
    }
}