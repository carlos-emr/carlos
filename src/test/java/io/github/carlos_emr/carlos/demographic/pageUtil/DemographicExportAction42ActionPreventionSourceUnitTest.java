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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

import io.github.carlos_emr.carlos.commn.model.OscarLog;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.prevention.dto.PreventionDatePrecisionException;
import io.github.carlos_emr.carlos.prevention.dto.PreventionSourceUnavailableException;

/**
 * The legacy export reads every chart's preventions. When that read fails, the export is refused
 * as a whole with a reason the page can show: it never produces an archive whose charts are
 * missing their immunizations, and it recognises the failure by its type, not by message text.
 */
@Tag("unit")
@Tag("demographic")
@DisplayName("DemographicExportAction42Action when preventions cannot be read")
class DemographicExportAction42ActionPreventionSourceUnitTest extends DemographicExportExecuteUnitTestBase {

    @Test
    @DisplayName("should refuse the export with the prevention reason code and leave nothing behind")
    void shouldRefuseTheExportWithItsReasonCode_whenAChartsPreventionsCannotBeRead() throws Exception {
        chart(101, "FAKE-Ann");
        chart(102, "FAKE-Bea");
        when(preventionDao.findNotDeletedByDemographicId(102))
                .thenThrow(new DataAccessResourceFailureException("synthetic source failure"));

        String result = action.execute();

        assertThat(result).isEqualTo(ActionSupport.NONE);
        verify(response).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        verify(response).setHeader("X-Export-Status", "error");
        verify(response).setHeader("X-Export-Error",
                DemographicExportAction42Action.PREVENTION_SOURCE_UNAVAILABLE_CODE);
        // No archive: chart 101 was already written when chart 102 failed, and is not published
        // alone as if it were the whole patient set.
        verify(response, never()).getOutputStream();
        assertThat(nothingStreamed()).isTrue();
        assertThat(exportDirectoriesLeft()).isEmpty();

        ArgumentCaptor<OscarLog> audit = ArgumentCaptor.forClass(OscarLog.class);
        logActionMock.verify(() -> LogAction.addLogSynchronous(audit.capture()));
        assertThat(audit.getValue().getData())
                .contains("Exported 2 records", "outcome=prevention_source_unavailable")
                .doesNotContain("101", "102");
    }

    @Test
    @DisplayName("should not take another illegal state for a prevention read failure")
    void shouldPropagateAnotherIllegalState_whenOnlyItsMessageNamesAnUnavailableSource() throws Exception {
        chart(101, "FAKE-Ann");
        IllegalStateException other = new IllegalStateException(PreventionSourceUnavailableException.MESSAGE);
        // Raised after the prevention read, by the loop's own lookup of the chart's HRM reports.
        when(hrmDocumentToDemographicDao.findByDemographicNo("101")).thenThrow(other);

        assertThatThrownBy(() -> action.execute()).isSameAs(other);

        verify(response, never()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertThat(nothingStreamed()).isTrue();
        assertThat(exportDirectoriesLeft()).isEmpty();
        ArgumentCaptor<OscarLog> audit = ArgumentCaptor.forClass(OscarLog.class);
        logActionMock.verify(() -> LogAction.addLogSynchronous(audit.capture()));
        assertThat(audit.getValue().getData()).contains("outcome=error", "error=IllegalStateException");
    }

    @Test
    @DisplayName("should pass a prevention date that needs review through unchanged")
    void shouldPropagateTheDateReviewRefusal_whenAPreventionReadReportsIt() throws Exception {
        chart(101, "FAKE-Ann");
        PreventionDatePrecisionException review = new PreventionDatePrecisionException();
        when(preventionDao.findNotDeletedByDemographicId(101)).thenThrow(review);

        assertThatThrownBy(() -> action.execute()).isSameAs(review);

        verify(response, never()).setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        assertThat(nothingStreamed()).isTrue();
        assertThat(exportDirectoriesLeft()).isEmpty();
    }
}
