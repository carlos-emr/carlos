/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import io.github.carlos_emr.carlos.commn.dao.FixedJasperReportDao;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@ResourceLock("SYSTEM_PROPERTIES")
class FixedDaySheetReportServiceUnitTest {
    @TempDir Path directory;

    @Test
    void shouldReturnNoContent_forAnEmptyFixedDaySheet() throws Exception {
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", directory.toString());
            var reports = mock(FixedJasperReportDao.class);
            when(reports.daySheet(java.sql.Date.valueOf("2025-01-01"))).thenReturn(java.util.List.of());
            var response = new MockHttpServletResponse();
            new FixedDaySheetReportService(reports).writePdf(response, "2025-01-01", "labDaySheet.xml");
            assertThat(response.getStatus()).isEqualTo(204);
            assertThat(response.getContentType()).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
        } finally { System.setProperty("user.home", previous); }
    }

    @Test
    void shouldPropagateDatabaseFailure_beforeSettingSuccessHeaders() throws Exception {
        String previous = System.getProperty("user.home");
        try {
            System.setProperty("user.home", directory.toString());
            var reports = mock(FixedJasperReportDao.class);
            when(reports.daySheet(any())).thenThrow(new IllegalStateException("synthetic query failure"));
            var response = new MockHttpServletResponse();
            assertThatThrownBy(() -> new FixedDaySheetReportService(reports)
                    .writePdf(response, "2025-01-01", "labDaySheet.xml"))
                    .isInstanceOf(IllegalStateException.class);
            assertThat(response.getContentType()).isNull();
            assertThat(response.getContentAsByteArray()).isEmpty();
        } finally { System.setProperty("user.home", previous); }
    }
}
