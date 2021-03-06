/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014-2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.http2.hpack.Encoder;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;

import java.util.Map;

/**
 *
 *
 */
public class HeadersEncoder {

    private static final String DEFAULT_BUFFER_SIZE_PROP_NAME =
            "org.glassfish.grizzly.http2.HeadersEncoder.DEFAULT_BUFFER_SIZE";
    private static final String DEFAULT_BUFFER_SIZE_STRING = "8192";

    private static final int DEFAULT_BUFFER_SIZE =
            Integer.parseInt(System.getProperty(DEFAULT_BUFFER_SIZE_PROP_NAME, DEFAULT_BUFFER_SIZE_STRING));

    private final Encoder hpackEncoder;
    private final MemoryManager memoryManager;

    private CompositeBuffer buffer;

    public HeadersEncoder(final MemoryManager memoryManager,
                          final int maxHeaderTableSize) {
        this.memoryManager = memoryManager;
        hpackEncoder = new Encoder(maxHeaderTableSize);
    }
    
    public void encodeHeader(final String name, final String value, final Map<String,String> capture) {
        if (capture != null) {
            capture.put(name, value);
        }
        init();
        hpackEncoder.header(name, value);
        while (!hpackEncoder.encode(buffer)) {
            buffer.append(memoryManager.allocate(DEFAULT_BUFFER_SIZE));
        }
    }
    
    public Buffer flushHeaders() {
        final Buffer bufferLocal = buffer;
        bufferLocal.trim();
        buffer = null;

        return bufferLocal;
    }

    private void init() {
        if (buffer == null) {
            buffer = CompositeBuffer.newBuffer(memoryManager);
            buffer.allowInternalBuffersDispose(true);
            buffer.allowBufferDispose(true);
            buffer.append(memoryManager.allocate(DEFAULT_BUFFER_SIZE));
        }
    }
}
