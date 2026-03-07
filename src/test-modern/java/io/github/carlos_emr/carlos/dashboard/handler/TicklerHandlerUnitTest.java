/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * <p>
 * Migrated from legacy JUnit 4 test to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.dashboard.handler;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.TicklerManagerImpl;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for {@link TicklerHandler}.
 *
 * <p>Tests tickler creation from CSV demographic lists and validation of tickler objects.
 * Migrated from legacy JUnit 4 TicklerHandlerTest.
 *
 * @since 2015-01-27 (original)
 */
@Tag("unit")
@Tag("tickler")
@DisplayName("TicklerHandler unit tests")
class TicklerHandlerUnitTest {

    private static final String DEMOGRAPHIC_CSV = "100,92,34928,234,1000,23,98737";
    private static TicklerHandler ticklerHandler;

    @BeforeAll
    static void setUpBeforeAll() {
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoAsCurrentClassAndMethod();
        Provider provider = new Provider();
        provider.setProviderNo("100");
        loggedInInfo.setLoggedInProvider(provider);
        ticklerHandler = new TicklerHandler(loggedInInfo, new TicklerManagerImpl());

        Map<String, String[]> parameterMap = new HashMap<>();
        parameterMap.put("message", new String[]{"This is a message."});
        parameterMap.put("messageAppend", new String[]{"This is an appended message."});
        parameterMap.put("priority", new String[]{"High"});
        parameterMap.put("serviceDate", new String[]{"06-18-2017"});
        parameterMap.put("serviceTime", new String[]{"04:08 PM"});
        parameterMap.put("taskAssignedTo", new String[]{"100"});
        parameterMap.put("creator", new String[]{"100"});
        parameterMap.put("ticklerCategoryId", new String[]{"1"});

        ticklerHandler.createMasterTickler(parameterMap);
        ticklerHandler.addTickler(DEMOGRAPHIC_CSV);
    }

    @Test
    @DisplayName("should create seven ticklers from CSV demographic list")
    void shouldCreateSevenTicklers_fromCsvDemographicList() {
        assertThat(ticklerHandler.getTicklerList()).hasSize(7);
    }

    @Test
    @DisplayName("should validate tickler as valid when properly constructed")
    void shouldValidateTickler_whenProperlyConstructed() {
        Tickler tickler = ticklerHandler.getTicklerList().get(4);
        assertThat(ticklerHandler.getTicklerManager().validateTicklerIsValid(tickler)).isTrue();
    }
}
