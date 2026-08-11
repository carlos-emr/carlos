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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for demographic export request method handling.
 *
 * @since 2026-05-03
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action request method handling")
class DemographicExportAction42ActionRequestMethodTest extends DemographicExportActionUnitTestBase {

    @Test
    @DisplayName("should display export UI for GET requests")
    void shouldReturnSuccess_whenRequestMethodIsGet() throws Exception {
        when(request.getMethod()).thenReturn("GET");

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull());
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographicExport"), eq("r"), isNull());
        verifyNoInteractions(response);
    }

    @Test
    @DisplayName("should audit unsupported template for POST requests")
    void shouldAuditExportAttempt_whenPostingUnsupportedTemplate() throws Exception {
        when(request.getMethod()).thenReturn("POST");
        action.setDemographicNo("123");
        action.setTemplate(String.valueOf(DemographicExportAction42Action.E2E));

        String result = action.execute();

        assertThat(result).isNotEqualTo(ActionSupport.SUCCESS);
        ArgumentCaptor<OscarLog> auditLogCaptor = ArgumentCaptor.forClass(OscarLog.class);
        logActionMock.verify(() -> LogAction.addLogSynchronous(auditLogCaptor.capture()));
        OscarLog auditLog = auditLogCaptor.getValue();
        assertThat(auditLog.getAction()).isEqualTo(LogConst.EXPORT);
        assertThat(auditLog.getContent()).isEqualTo(LogConst.CON_DEMOGRAPHIC);
        // Refused before the patient set is resolved, so nothing is exported and no demographic
        // is named in the audit record.
        assertThat(auditLog.getData())
                .contains("Exported 0 records", "outcome=fail",
                        "ids=" + DemographicExportAction42Action.NO_IDS_RESOLVED);
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographic"), eq("r"), isNull());
        verify(securityInfoManager).hasPrivilege(any(LoggedInInfo.class), eq("_demographicExport"), eq("r"), isNull());
    }
}
