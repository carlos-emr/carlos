/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.form.pdfservlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;
import org.openpdf.text.Document;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.mock.web.MockHttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.FaxConfigDao;
import io.github.carlos_emr.carlos.commn.dao.FaxJobDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.FaxManager.TransactionType;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Unit tests for {@link PrescriptionFaxService}.
 */
@DisplayName("PrescriptionFaxService Unit Tests")
@Tag("unit")
@Tag("rx")
class PrescriptionFaxServiceTest {

    @TempDir
    private Path documentDir;

    @TempDir
    private Path faxDir;

    @Mock
    private FaxJobDao mockFaxJobDao;

    @Mock
    private FaxConfigDao mockFaxConfigDao;

    @Mock
    private FaxManager mockFaxManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private AutoCloseable mocks;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockedStatic<LogAction> logActionMock;
    private String originalDocumentDir;
    private String originalFaxFileLocation;
    private PrescriptionFaxService service;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        service = new PrescriptionFaxService(mockFaxJobDao, mockFaxConfigDao, mockFaxManager);

        CarlosProperties properties = CarlosProperties.getInstance();
        originalDocumentDir = properties.getProperty("DOCUMENT_DIR");
        originalFaxFileLocation = properties.getProperty("fax_file_location");
        properties.setProperty("DOCUMENT_DIR", documentDir.toString());
        properties.setProperty("fax_file_location", faxDir.toString());

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(MockHttpServletRequest.class)))
                .thenReturn(mockLoggedInInfo);
        logActionMock = mockStatic(LogAction.class);
    }

    @AfterEach
    void tearDown() throws Exception {
        restoreProperty("DOCUMENT_DIR", originalDocumentDir);
        restoreProperty("fax_file_location", originalFaxFileLocation);
        if (logActionMock != null) {
            logActionMock.close();
        }
        if (loggedInInfoMock != null) {
            loggedInInfoMock.close();
        }
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should overwrite existing PDF and match formatted clinic fax")
    void shouldOverwriteExistingPdfAndMatchFormattedClinicFax_whenFaxJobCreated() throws Exception {
        MockHttpServletRequest request = createFaxRequest("rx_123");
        Files.writeString(documentDir.resolve("prescription_rx_123.pdf"), "stale pdf");
        FaxConfig faxConfig = new FaxConfig();
        faxConfig.setFaxNumber("416-555-0199");
        faxConfig.setFaxUser("fax-user");
        faxConfig.setSenderEmail("fax@example.test");
        when(mockFaxConfigDao.findAll(null, null)).thenReturn(List.of(faxConfig));

        PrescriptionFaxViewModel result = service.createFaxJob(mockLoggedInInfo, request, createPdf("fresh rx"));

        assertThat(result.validFaxNumber()).isTrue();
        assertThat(Files.readAllBytes(documentDir.resolve("prescription_rx_123.pdf")))
                .startsWith("%PDF".getBytes(StandardCharsets.US_ASCII));
        assertThat(faxDir.resolve("prescription_rx_123.pdf")).exists();
        assertThat(faxDir.resolve("prescription_rx_123.txt")).hasContent("4165550123");

        ArgumentCaptor<FaxJob> faxJobCaptor = ArgumentCaptor.forClass(FaxJob.class);
        verify(mockFaxJobDao).persist(faxJobCaptor.capture());
        FaxJob faxJob = faxJobCaptor.getValue();
        assertThat(faxJob.getDestination()).isEqualTo("4165550123");
        assertThat(faxJob.getFax_line()).isEqualTo("4165550199");
        assertThat(faxJob.getFile_name()).isEqualTo("prescription_rx_123.pdf");
        assertThat(faxJob.getDemographicNo()).isEqualTo(123);
        verify(mockFaxManager).logFaxJob(mockLoggedInInfo, faxJob, TransactionType.RX, -1);
    }

    @Test
    @DisplayName("should reject invalid PDF id before creating fax artifacts")
    void shouldRejectInvalidPdfId_beforeCreatingFaxArtifacts() {
        MockHttpServletRequest request = createFaxRequest("../bad");

        assertThatThrownBy(() -> service.createFaxJob(mockLoggedInInfo, request, createPdf("fresh rx")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid prescription PDF id");

        assertThat(documentDir).isEmptyDirectory();
        assertThat(faxDir).isEmptyDirectory();
        verifyNoInteractions(mockFaxConfigDao, mockFaxJobDao, mockFaxManager);
    }

    private MockHttpServletRequest createFaxRequest(String pdfId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("pharmaFax", "416-555-0123");
        request.setParameter("pharmaName", "Main Pharmacy");
        request.setParameter("clinicFax", "4165550199");
        request.setParameter("demographic_no", "123");
        request.setParameter("pdfId", pdfId);
        return request;
    }

    private ByteArrayOutputStream createPdf(String content) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document();
        PdfWriter.getInstance(document, outputStream);
        document.open();
        document.add(new Paragraph(content));
        document.close();
        return outputStream;
    }

    private void restoreProperty(String key, String originalValue) {
        if (originalValue == null) {
            CarlosProperties.getInstance().remove(key);
        } else {
            CarlosProperties.getInstance().setProperty(key, originalValue);
        }
    }
}
