/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2011 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.config;

import org.glassfish.grizzly.config.dom.NetworkListener;
import com.sun.hk2.component.Holder;
import com.sun.hk2.component.InhabitantsParser;
import com.sun.hk2.component.InhabitantsScanner;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.config.ConfigParser;
import org.jvnet.hk2.config.DomDocument;

import javax.xml.stream.XMLInputFactory;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Enumeration;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;

/**
 * Created Dec 18, 2008
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
public class Utils {
    private static final Logger LOGGER = Grizzly.logger(Utils.class);

    private final static String habitatName = "default";
    private final static String inhabitantPath = "META-INF/inhabitants";

    public static Habitat getHabitat(final String fileURL) {
        URL url = Utils.class.getClassLoader().getResource(fileURL);
        if (url == null) {
            try {
                url = new URL(fileURL);
            } catch (MalformedURLException e) {
                throw new GrizzlyConfigException(e.getMessage());
            }
        }
        Habitat habitat;
        try {
            habitat = getHabitat(url.openStream());
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        }
        return habitat;
    }

    public static Habitat getHabitat(final InputStream inputStream) {
        try {
            final Habitat habitat = getNewHabitat();
            final ConfigParser parser = new ConfigParser(habitat);
            XMLInputFactory xif = XMLInputFactory.class.getClassLoader() == null
                    ? XMLInputFactory.newFactory()
                    : XMLInputFactory.newFactory(XMLInputFactory.class.getName(),
                    XMLInputFactory.class.getClassLoader());
            final DomDocument document = parser.parse(xif.createXMLStreamReader(inputStream));

            habitat.addComponent(document);
            return habitat;
        } catch (Exception e) {
            e.printStackTrace();
            throw new GrizzlyConfigException(e.getMessage(), e);
        }
    }

    public static Habitat getNewHabitat() {
        final Holder<ClassLoader> holder = new Holder<ClassLoader>() {
            public ClassLoader get() {
                return getClass().getClassLoader();
            }
        };
        final Enumeration<URL> resources;
        try {
            resources = Utils.class.getClassLoader().getResources(inhabitantPath + '/' + habitatName);
        } catch (IOException e) {
            throw new GrizzlyConfigException(e);
        }
        if (resources == null) {
            System.out.println("Cannot find any inhabitant file in the classpath");
            return null;
        }
        final Habitat habitat = new Habitat();
        while (resources.hasMoreElements()) {
            final URL resource = resources.nextElement();
            final InhabitantsScanner scanner;
            try {
                scanner = new InhabitantsScanner(resource.openConnection().getInputStream(), habitatName);
            } catch (IOException e) {
                throw new GrizzlyConfigException(e);
            }
            final InhabitantsParser inhabitantsParser = new InhabitantsParser(habitat);
            try {
                inhabitantsParser.parse(scanner, holder);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
        return habitat;
    }
    
    public static String composeThreadPoolName(final NetworkListener networkListener) {
        return networkListener.getThreadPool() + '-' + networkListener.getPort();
    }

    /**
     * Load or create an Object with the specific service name and class name.
     *
     * @param habitat the HK2 {@link Habitat}
     * @param clazz the class as mapped within the {@link Habitat}
     * @param name the service name
     * @param realClassName the class name of the service
     * @return a service matching based on name and realClassName input
     *  arguments.
     */
    @SuppressWarnings({"unchecked"})
    public static <E> E newInstance(Habitat habitat, Class<E> clazz,
            final String name, final String realClassName) {
        boolean isInitialized = false;

        E instance = habitat.getComponent(clazz, name);
        if (instance == null) {
            try {
                instance = (E) newInstance(realClassName);
                isInitialized = true;
            } catch (Exception ignored) {
            }
        } else {
            isInitialized = true;
        }

        if (!isInitialized) {
            LOGGER.log(Level.WARNING, "Instance could not be initialized. "
                    + "Class={0}, name={1}, realClassName={2}",
                    new Object[]{clazz, name, realClassName});
            return null;
        }

        return instance;
    }

    public static Object newInstance(String classname) throws Exception {
        return loadClass(classname).newInstance();
    }

    public static Class loadClass(String classname) throws ClassNotFoundException {
        Class clazz = null;
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl != null) {
            try {
                clazz = cl.loadClass(classname);
            } catch (Exception ignored) {
            }
        }
        if (clazz == null) {
            clazz = Utils.class.getClassLoader().loadClass(classname);
        }
        return clazz;
    }

    public static boolean isDebugVM() {
        boolean debugMode = false;
        final List<String> l = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String s : l) {
            if (s.trim().startsWith("-Xrunjdwp:") || s.contains("jdwp")) {
                debugMode = true;
                break;
            }
        }
        return debugMode;
    }
}
