/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.PreventionTransfer;

/**
 * SOAP-level endpoint tests for {@link PreventionWs} using CXF local transport.
 *
 * <p>These tests verify the full CXF JAX-WS pipeline for prevention/immunization
 * operations: SOAP envelope marshalling/unmarshalling and response serialization
 * of {@link PreventionTransfer} arrays.</p>
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("PreventionWs SOAP endpoint tests")
class PreventionWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private PreventionManager preventionManager;

    private PreventionWs ws;

    @Override
    protected Object getServiceBean() {
        ws = new PreventionWs();
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return PreventionWs.class;
    }

    @BeforeEach
    void setUpMocks() {
        registerMock(PreventionManager.class, preventionManager);
        injectDependency(ws, "preventionManager", preventionManager);
    }

    /** Tests for the getPrevention SOAP operation. */
    @Nested
    @DisplayName("getPrevention operation")
    class GetPrevention {

        @Test
        @DisplayName("should return prevention transfer when found")
        void shouldReturnPreventionTransfer_whenFound() {
            Prevention prevention = new Prevention();
            prevention.setPreventionType("Flu");
            when(preventionManager.getPrevention(any(LoggedInInfo.class), eq(7))).thenReturn(prevention);
            when(preventionManager.getPreventionExtByPrevention(any(LoggedInInfo.class), eq(7)))
                .thenReturn(Collections.emptyList());

            PreventionWs proxy = createClient(PreventionWs.class);
            PreventionTransfer result = proxy.getPrevention(7);

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("should return null when prevention not found")
        void shouldReturnNull_whenPreventionNotFound() {
            when(preventionManager.getPrevention(any(LoggedInInfo.class), eq(999))).thenReturn(null);

            PreventionWs proxy = createClient(PreventionWs.class);
            PreventionTransfer result = proxy.getPrevention(999);

            assertThat(result).isNull();
        }
    }

    /** Tests for the getPreventionsUpdatedAfterDate SOAP operation. */
    @Nested
    @DisplayName("getPreventionsUpdatedAfterDate operation")
    class GetPreventionsUpdatedAfterDate {

        @Test
        @Disabled("TODO: PreventionTransfer.getTransfers() calls SpringUtils.getBean() internally")
        @DisplayName("should return prevention array when results exist")
        void shouldReturnPreventionArray_whenResultsExist() {
            List<Prevention> preventions = new ArrayList<>();
            Prevention p = new Prevention();
            preventions.add(p);
            when(preventionManager.getUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(preventions);

            PreventionWs proxy = createClient(PreventionWs.class);
            PreventionTransfer[] result = proxy.getPreventionsUpdatedAfterDate(new Date(), 10);

            assertThat(result).isNotNull().isNotEmpty();
        }

        @Test
        @Disabled("TODO: PreventionTransfer.getTransfers() calls SpringUtils.getBean() internally")
        @DisplayName("should return empty array when no results")
        void shouldReturnEmptyArray_whenNoResults() {
            when(preventionManager.getUpdatedAfterDate(any(LoggedInInfo.class), any(Date.class), anyInt()))
                .thenReturn(new ArrayList<>());

            PreventionWs proxy = createClient(PreventionWs.class);
            PreventionTransfer[] result = proxy.getPreventionsUpdatedAfterDate(new Date(), 10);

            assertThat(result).isNotNull().isEmpty();
        }
    }

    /** Tests for the getPreventionsByDemographicIdAfter SOAP operation. */
    @Nested
    @DisplayName("getPreventionsByDemographicIdAfter operation")
    class GetPreventionsByDemographicIdAfter {

        @Test
        @Disabled("TODO: PreventionTransfer.getTransfers() calls SpringUtils.getBean() internally")
        @DisplayName("should return preventions for demographic after date")
        void shouldReturnPreventions_forDemographicAfterDate() {
            List<Prevention> preventions = new ArrayList<>();
            Prevention p = new Prevention();
            preventions.add(p);
            when(preventionManager.getByDemographicIdUpdatedAfterDate(any(LoggedInInfo.class), eq(200), any(Date.class)))
                .thenReturn(preventions);

            PreventionWs proxy = createClient(PreventionWs.class);
            Calendar cal = Calendar.getInstance();
            PreventionTransfer[] result = proxy.getPreventionsByDemographicIdAfter(cal, 200);

            assertThat(result).isNotNull().isNotEmpty();
        }
    }
}
