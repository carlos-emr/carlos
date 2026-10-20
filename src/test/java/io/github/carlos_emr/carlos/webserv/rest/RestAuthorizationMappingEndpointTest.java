/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.dao.EncounterTemplateDao;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.PreferenceManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.webserv.rest.util.AccessDeniedExceptionMapper;
import io.github.carlos_emr.carlos.webserv.rest.util.SecurityExceptionMapper;

/**
 * With the production exception mappers installed, a guard that refuses by throwing
 * {@code SecurityException} reaches the client as HTTP 403 rather than escaping CXF (which the
 * servlet container turns into the generic 500 page), while an unrelated failure is still not
 * reported as a permission refusal (#2798).
 *
 * @since 2026-10-08
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("REST authorization refusals map to HTTP 403")
class RestAuthorizationMappingEndpointTest extends CarlosRestTestBase {

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Override
    protected Object getServiceBean() {
        RecordUxService service = new RecordUxService();
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "consultationManager", mock(ConsultationManager.class));
        injectDependency(service, "encounterTemplateDao", mock(EncounterTemplateDao.class));
        injectDependency(service, "preferenceManager", mock(PreferenceManager.class));
        return service;
    }

    @Override
    protected List<Object> additionalServerProviders() {
        return List.of(new SecurityExceptionMapper(), new AccessDeniedExceptionMapper());
    }

    @Test
    @DisplayName("should answer 403 when the clinical-summary guard throws SecurityException")
    void shouldAnswer403_whenSummaryGuardThrowsSecurityException() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(false);

        Response response = request().path("/recordUX/123/getAllergies").get();

        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("should not report an unrelated failure as a permission refusal")
    void shouldNotMapUnrelatedFailure_toForbidden() {
        // Chart read granted, but an unknown summary code finds no summary bean: a server-side fault.
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eChart"), eq("r"), anyInt())).thenReturn(true);

        assertThatThrownBy(() -> request().path("/recordUX/123/fullSummary/nosuchsummary").get())
                .hasRootCauseInstanceOf(NullPointerException.class);
    }
}
