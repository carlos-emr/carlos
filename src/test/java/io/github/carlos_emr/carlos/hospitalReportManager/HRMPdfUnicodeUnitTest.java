/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.hospitalReportManager;

import java.io.ByteArrayOutputStream;
import java.util.List;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class HRMPdfUnicodeUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldPreserveUnicodeReport_whenPrintingTextHrm() throws Exception {
        var dao = mock(HRMDocumentDao.class);
        var stored = new HRMDocument();
        stored.setReportFile("synthetic-unicode.xml");
        when(dao.findById(4173)).thenReturn(List.of(stored));
        registerMock(HRMDocumentDao.class, dao);
        var report = mock(HRMReport.class);
        when(report.getLegalName()).thenReturn("Nguyễn Łukasz");
        when(report.getFirstReportTextContent()).thenReturn("İstanbul ≥ 5 ≤ 9");
        var user = mock(LoggedInInfo.class);
        var output = new ByteArrayOutputStream();
        try (var parser = mockStatic(HRMReportParser.class)) {
            parser.when(() -> HRMReportParser.parseReport(user, "synthetic-unicode.xml")).thenReturn(report);
            new HRMPDFCreator(output, 4173, user).printPdf();
        }
        try (var pdf = Loader.loadPDF(output.toByteArray())) {
            assertThat(new PDFTextStripper().getText(pdf)).contains("Nguyễn Łukasz", "İstanbul ≥ 5 ≤ 9");
        }
    }
}
