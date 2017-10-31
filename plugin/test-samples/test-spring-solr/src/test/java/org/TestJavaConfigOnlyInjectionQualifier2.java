/*
 * Copyright (c) 2017 org.hrodberaht
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org;

import org.config.JUnitConfigExampleResourceToSpringBeans;
import org.hrodberaht.injection.plugin.junit.ContainerContext;
import org.hrodberaht.injection.plugin.junit.JUnit4Runner;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.services.SpringBeanWithSpringBean;
import org.springframework.beans.factory.annotation.Autowired;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@ContainerContext(JUnitConfigExampleResourceToSpringBeans.class)
@RunWith(JUnit4Runner.class)
public class TestJavaConfigOnlyInjectionQualifier2 {


    @Autowired
    private SpringBeanWithSpringBean springBean;

    @Test
    public void testWiredBeanResourceAndDataSourceStore() throws Exception {

        assertNotNull(springBean);

        assertNotNull(springBean.getName("dude"));

        assertNull(springBean.getName("the"));

        assertFalse(springBean.existsInIndex("the"));

        springBean.createUser("the", "man");

        assertNotNull(springBean.getName("the"));

        assertTrue(springBean.existsInIndex("the"));
    }

    @Test
    public void testWiredBeanResourceAndDataSourceStore2() throws Exception {

        assertNotNull(springBean);

        assertNotNull(springBean.getName("dude"));

        springBean.login("dude", "badpassword");

        assertEquals(Integer.valueOf( 1), springBean.getLoginCount("dude"));


    }
}
