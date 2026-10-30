/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocument;
import io.github.carlos_emr.carlos.managers.NioFileManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * {@link HRMUtil#renderHRM}: a report that cannot be rendered surfaces as a
 * {@link PDFGenerationException} that names the HRM report, whichever step failed. Everything here is
 * synthetic.
 */
@Tag("unit")
@Tag("hrm")
@DisplayName("HRMUtil.renderHRM failures")
class HRMUtilRenderFailureUnitTest extends CarlosUnitTestBase {

    private static final int HRM_ID = 9031;
    private static final String NAMED_MESSAGE =
            "Error Details: HRM [FAKE-Discharge summary] could not be converted into a PDF";

    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final NioFileManager nioFileManager = mock(NioFileManager.class);

    @BeforeEach
    void registerCollaborators() {
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(user, "_hrm", "r", null)).thenReturn(true);
        registerMock(SecurityInfoManager.class, security);

        var dao = mock(HRMDocumentDao.class);
        var stored = new HRMDocument();
        stored.setDescription("FAKE-Discharge summary");
        // HRMUtil looks the report up with an Integer, which selects find(Object), not find(int).
        when(dao.find((Object) Integer.valueOf(HRM_ID))).thenReturn(stored);
        registerMock(HRMDocumentDao.class, dao);

        registerMock(NioFileManager.class, nioFileManager);
    }

    @Test
    @DisplayName("should name the report when the HRM PDF creator cannot convert it")
    void shouldNameReport_whenRenderHrmFails() throws Exception {
        var failure = new PDFGenerationException("HTML HRM report could not be converted to PDF");

        try (var creators = mockConstruction(HRMPDFCreator.class,
                (creator, context) -> doThrow(failure).when(creator).printPdf())) {
            assertThatThrownBy(() -> HRMUtil.renderHRM(user, HRM_ID))
                    .isExactlyInstanceOf(PDFGenerationException.class)
                    .hasMessage(NAMED_MESSAGE)
                    .hasCause(failure);
        }
        verifyNoInteractions(nioFileManager);
    }

    @Test
    @DisplayName("should keep the same message when the rendered PDF cannot be saved")
    void shouldNameReport_whenTemporaryFileCannotBeSaved() throws Exception {
        var failure = new IOException("synthetic: disk full");
        when(nioFileManager.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenThrow(failure);

        try (var creators = mockConstruction(HRMPDFCreator.class)) {
            assertThatThrownBy(() -> HRMUtil.renderHRM(user, HRM_ID))
                    .isExactlyInstanceOf(PDFGenerationException.class)
                    .hasMessage(NAMED_MESSAGE)
                    .hasCause(failure);
        }
    }

    @Test
    @DisplayName("should return the saved path when the report renders")
    void shouldReturnSavedPath_whenRenderSucceeds() throws Exception {
        Path saved = Path.of("synthetic-temporary.pdf").toAbsolutePath();
        when(nioFileManager.saveTempFile(anyString(), any(ByteArrayOutputStream.class))).thenReturn(saved);

        try (var creators = mockConstruction(HRMPDFCreator.class)) {
            assertThat(HRMUtil.renderHRM(user, HRM_ID)).isEqualTo(saved);
        }
    }
}
