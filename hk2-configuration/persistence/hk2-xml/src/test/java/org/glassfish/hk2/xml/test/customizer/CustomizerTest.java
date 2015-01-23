/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.hk2.xml.test.customizer;

import java.net.URL;

import org.glassfish.hk2.api.ServiceLocator;
import org.glassfish.hk2.xml.api.XmlRootHandle;
import org.glassfish.hk2.xml.api.XmlService;
import org.glassfish.hk2.xml.test.basic.UnmarshallTest;
import org.glassfish.hk2.xml.test.utilities.Utilities;
import org.junit.Assert;
import org.junit.Test;

/**
 * @author jwells
 *
 */
public class CustomizerTest {
    private final static String PREFIX = "Go ";
    private final static String POSTFIX = " Eagles";
    
    /**
     * Tests that a basic customizer works properly
     */
    @Test // @org.junit.Ignore
    public void testBasicCustomizerOnRoot() throws Exception {
        ServiceLocator locator = Utilities.createLocator(CustomizerOne.class);
        XmlService xmlService = locator.getService(XmlService.class);
        
        URL url = getClass().getClassLoader().getResource(UnmarshallTest.MUSEUM1_FILE);
        
        XmlRootHandle<MuseumBean> rootHandle = xmlService.unmarshall(url.toURI(), MuseumBean.class);
        MuseumBean museum = rootHandle.getRoot();
        
        String retVal = museum.customizer1(PREFIX, POSTFIX);
        Assert.assertEquals(retVal, PREFIX + UnmarshallTest.BEN_FRANKLIN + POSTFIX);
        
        CustomizerOne customizer = locator.getService(CustomizerOne.class);
        Assert.assertFalse(customizer.getCustomizer2Called());
        
        museum.customizer2();
        
        Assert.assertTrue(customizer.getCustomizer2Called());
        
        long[] c3 = museum.customizer3(null);
        Assert.assertNotNull(c3);
        Assert.assertEquals(0, c3.length);
    }

}