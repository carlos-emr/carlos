/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package io.github.carlos_emr.carlos.test.simple;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test to verify Spring context loading works
 */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(locations = {"classpath:test-minimal-context.xml"})
@DisplayName("Spring Context Test")
public class SpringContextTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Test Spring context loads")
    public void testContextLoads() {
        assertNotNull(applicationContext, "Application context should be loaded");
    }

    @Test
    @DisplayName("Test DataSource is available")
    public void testDataSourceAvailable() {
        assertNotNull(dataSource, "DataSource should be autowired");
    }

    @Test
    @DisplayName("Test bean retrieval")
    public void testBeanRetrieval() {
        String testString = applicationContext.getBean("testString", String.class);
        assertEquals("Test String Bean", testString);
    }
}