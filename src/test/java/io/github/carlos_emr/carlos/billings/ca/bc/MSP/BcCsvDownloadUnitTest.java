/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.io.OutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("web")
class BcCsvDownloadUnitTest extends CarlosWebTestBase {
    @BeforeEach
    void prepareSecurity() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        setSessionAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", mockLoggedInInfo);
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_billing"), eq("r"), isNull())).thenReturn(true);
    }

    @Test
    void shouldDiscardPartialFile_whenGenerationFails() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void writeCsv(OutputStream output) throws Exception {
                output.write("partial synthetic data".getBytes(java.nio.charset.StandardCharsets.UTF_8));
                throw new java.io.IOException("synthetic sensitive error");
            }
        };
        action.setDocFormat("csv"); action.setRepType("REP_WO");
        try (LogCapture logs = LogCapture.forLogger(CreateBillingReport2Action.class)) {
            assertThat(executeAction(action)).isEqualTo("none");
            assertThat(getMockResponse().getStatus()).isEqualTo(500);
            assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
            assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
            assertThat(logs.events()).isNotEmpty()
                    .allSatisfy(event -> assertThat(event.getThrown()).isNull());
            assertThat(logs.messages())
                    .allSatisfy(message -> assertThat(message).doesNotContain("synthetic sensitive error"))
                    .anySatisfy(message -> assertThat(message).contains(java.io.IOException.class.getName()));
        }
    }

    @Test
    void shouldSendCompleteFile_whenGenerationSucceeds() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void writeCsv(OutputStream output) throws Exception {
                assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
                output.write("\"Header\"\n\"Synthetic\"\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
        };
        action.setDocFormat("csv"); action.setRepType("REP_WO");
        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(200);
        assertThat(getMockResponse().getContentAsString()).isEqualTo("\"Header\"\n\"Synthetic\"\n");
        assertThat(getMockResponse().getHeader("Content-Disposition")).isEqualTo("attachment; filename=\"REP_WO.csv\"");
    }

    @Test
    void shouldRejectUnageableClaim_withoutSendingAnAttachment() throws Exception {
        var action = new CreateBillingReport2Action() {
            @Override protected void writeCsv(OutputStream output) throws Exception {
                var bill = new io.github.carlos_emr.carlos.entities.MSPBill();
                bill.setServiceDate("00000000");
                bill.setAmount("23.45");
                io.github.carlos_emr.carlos.report.export.BcBillingCsv.write(output, "REP_ACCOUNT_REC", java.util.List.of(bill));
            }
        };
        action.setDocFormat("csv"); action.setRepType("REP_ACCOUNT_REC");
        assertThat(executeAction(action)).isEqualTo("none");
        assertThat(getMockResponse().getStatus()).isEqualTo(500);
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
        assertThat(getMockResponse().getHeader("Content-Disposition")).isNull();
    }

    @Test
    void shouldDenyExport_whenBillingPrivilegeIsMissing() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);
        var action = new CreateBillingReport2Action();
        action.setDocFormat("csv"); action.setRepType("REP_WO");
        assertThatThrownBy(() -> executeAction(action)).isInstanceOf(SecurityException.class);
        assertThat(getMockResponse().getContentAsByteArray()).isEmpty();
    }
}
