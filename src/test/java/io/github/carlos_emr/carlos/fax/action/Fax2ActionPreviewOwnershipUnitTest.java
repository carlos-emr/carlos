/** Copyright (c) 2026 CARLOS Contributors. GPL version 2 or later. */
package io.github.carlos_emr.carlos.fax.action;

import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class Fax2ActionPreviewOwnershipUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FaxManager fax;
    private SecurityInfoManager security;
    private EFormDataDao eforms;
    private EFormData eform;
    private LoggedInInfo user;
    private Path directory;
    private Path pdf;
    private MockedStatic<ServletActionContext> context;
    private Map<String, Fax2Action.FaxPreviewClaim> claims;

    @BeforeEach
    void setUp() throws Exception {
        Path root = Path.of(System.getProperty("java.io.tmpdir"), "carlos-temp");
        Files.createDirectories(root);
        directory = Files.createTempDirectory(root, "preview-claim-test-");
        pdf = directory.resolve("owned.pdf");
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            document.save(pdf.toFile());
        }
        request = new MockHttpServletRequest("GET", "/fax/faxAction");
        response = new MockHttpServletResponse();
        request.setParameter("faxFilePath", pdf.toString());
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999998");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        fax = createAndRegisterMock(FaxManager.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(DocumentAttachmentManager.class);
        eforms = createAndRegisterMock(EFormDataDao.class);
        eform = new EFormData();
        eform.setDemographicId(10);
        when(eforms.find(77)).thenReturn(eform);
        when(security.hasPrivilege(eq(user), anyString(), eq("r"), any())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 10)).thenReturn(true);
        when(fax.resolveAndValidateFilePath(pdf.toString())).thenReturn(pdf);
        claims = new ConcurrentHashMap<>();
        claims.put(pdf.toString(), new Fax2Action.FaxPreviewClaim(77, 10, "999998"));
        request.getSession().setAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY, claims);
        context = mockStatic(ServletActionContext.class);
        context.when(ServletActionContext::getRequest).thenReturn(request);
        context.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (context != null) context.close();
        if (pdf != null) Files.deleteIfExists(pdf);
        if (directory != null) Files.deleteIfExists(directory);
    }

    private void read(String operation) {
        if ("count".equals(operation)) new Fax2Action().getPageCount();
        else {
            if ("image".equals(operation)) request.setParameter("showAs", "image");
            new Fax2Action().getPreview();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "image", "count"})
    void shouldDenyEveryUnownedOrRevokedPreviewBeforeFileAccess(String operation) {
        for (String reason : new String[] {"unclaimed", "other-session", "provider", "cancelled",
                "moved", "deleted", "eform-permission", "patient-permission"}) {
            claims.put(pdf.toString(), new Fax2Action.FaxPreviewClaim(77, 10, "999998"));
            request.getSession().setAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY, claims);
            eform.setDemographicId(10);
            when(eforms.find(77)).thenReturn(eform);
            when(security.hasPrivilege(user, "_eform", "r", "10")).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(user, 10)).thenReturn(true);
            switch (reason) {
                case "unclaimed" -> claims.clear();
                case "other-session" -> request.getSession().removeAttribute(Fax2Action.CLAIMED_FAX_FILE_PATHS_SESSION_KEY);
                case "provider" -> claims.put(pdf.toString(), new Fax2Action.FaxPreviewClaim(77, 10, "999997"));
                case "cancelled" -> claims.put(pdf.toString(), new Fax2Action.FaxPreviewClaim(77, 10, "999998", true));
                case "moved" -> eform.setDemographicId(11);
                case "deleted" -> when(eforms.find(77)).thenReturn(null);
                case "eform-permission" -> when(security.hasPrivilege(user, "_eform", "r", "10")).thenReturn(false);
                case "patient-permission" -> when(security.isAllowedAccessToPatientRecord(user, 10)).thenReturn(false);
                default -> throw new AssertionError(reason);
            }
            response = new MockHttpServletResponse();
            context.when(ServletActionContext::getResponse).thenReturn(response);
            clearInvocations(fax);
            read(operation);
            assertThat(response.getStatus()).as(reason).isEqualTo(403);
            verifyNoInteractions(fax);
            assertThat(pdf).exists();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "count"})
    void shouldAllowRepeatedOwnedReadsWithoutConsumingTheClaim(String operation) throws Exception {
        for (int i = 0; i < 2; i++) {
            response = new MockHttpServletResponse();
            context.when(ServletActionContext::getResponse).thenReturn(response);
            read(operation);
            assertThat(response.getStatus()).isEqualTo(200);
            if ("count".equals(operation)) assertThat(response.getContentAsString()).contains("\"pageCount\":1");
            else assertThat(response.getContentAsByteArray()).startsWith("%PDF-".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
            assertThat(claims).hasSize(1);
            assertThat(claims.get(pdf.toString()).cancelled()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"pdf", "count"})
    void shouldNotFallBackToAPathWhenTheRequestedJobDoesNotExist(String operation) throws Exception {
        request.setParameter("jobId", "123");
        read(operation);
        assertThat(response.getStatus()).isEqualTo(404);
        verify(fax, never()).resolveAndValidateFilePath(anyString());
    }

    @Test
    void shouldDenyPageCountForAnotherPatientsQueuedJob() {
        request.setParameter("jobId", "123");
        FaxJob job = new FaxJob();
        job.setDemographicNo(11);
        when(fax.getFaxJob(user, 123)).thenReturn(job);
        new Fax2Action().getPageCount();
        assertThat(response.getStatus()).isEqualTo(403);
        verify(fax, never()).getPageCount(any(), anyInt());
    }
}
