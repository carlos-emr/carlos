/* Copyright (c) 2026 CARLOS Contributors. Licensed under the GNU GPL. */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.CtlDocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.EmailAttachment;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFGenerationException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.MockedStatic;

/**
 * Email attachments must belong to the email's patient. Each record type is checked against its own
 * owner (a document's {@code ctl_document} link, a lab's HL7 routing, an eForm's patient, an HRM
 * report's link) through the real {@link AttachmentOwnershipService}, the rule the send applies too; a
 * record of another patient is refused as one that cannot be found, and nothing is rendered for it.
 */
@Tag("unit")
@Tag("email")
@Tag("security")
class EmailComposeManagerAttachmentPatientUnitTest extends CarlosUnitTestBase {
    private static final int EMAIL_PATIENT = 10001;
    private static final int OTHER_PATIENT = 20002;
    private static final String REFUSED = "A selected email attachment could not be rendered";

    @TempDir
    Path tempDir;

    private final CtlDocumentDao documents = mock(CtlDocumentDao.class);
    private final PatientLabRoutingDao labs = mock(PatientLabRoutingDao.class);
    private final EFormDataDao eForms = mock(EFormDataDao.class);
    private final HRMDocumentToDemographicDao hrmLinks = mock(HRMDocumentToDemographicDao.class);
    private final DocumentAttachmentManager renderer = mock(DocumentAttachmentManager.class);
    private final LoggedInInfo login = new LoggedInInfo();
    private EmailComposeManager manager;
    private MockedStatic<CarlosProperties> config;

    @BeforeEach
    void setUp() throws Exception {
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        manager = new EmailComposeManager(security);
        injectDependency(manager, "documentAttachmentManager", renderer);
        injectDependency(manager, "attachmentOwnershipService", new AttachmentOwnershipService(
                documents, labs, eForms, hrmLinks, mock(ConsultationRequestDao.class)));
        // A PDF document is copied into the application temp tree; here the copy is the file itself.
        NioFileManager files = mock(NioFileManager.class);
        when(files.createTempFileFrom(anyString(), any(Path.class))).thenAnswer(call -> call.getArgument(1));
        injectDependency(manager, "nioFileManager", files);
        Path rendered = Files.write(tempDir.resolve("FAKE-rendered.pdf"), "%PDF-1.4 FAKE".getBytes(StandardCharsets.US_ASCII));
        when(renderer.renderDocument(any(), any(DocumentType.class), anyInt())).thenReturn(rendered);
        // HRM attachments exist only in Ontario billing regions.
        CarlosProperties properties = mock(CarlosProperties.class);
        when(properties.isOntarioBillingRegion()).thenReturn(true);
        config = mockStatic(CarlosProperties.class);
        config.when(CarlosProperties::getInstance).thenReturn(properties);
    }

    @AfterEach
    void tearDown() {
        config.close();
    }

    /** Records each patient's own records of one type, as its owner table does. */
    private void recordsOf(DocumentType type, int patient, Integer... ids) {
        List<Integer> owned = List.of(ids);
        switch (type) {
            case DOC -> when(documents.findDocumentNosForDemographic(eq(patient), any())).thenReturn(owned);
            case LAB -> when(labs.findLabNosForDemographic(eq(patient), eq(PatientLabRoutingDao.HL7), any())).thenReturn(owned);
            case EFORM -> when(eForms.findFdidsForDemographic(eq(patient), any())).thenReturn(owned);
            case HRM -> when(hrmLinks.findHrmIdsForDemographic(eq(patient), any())).thenReturn(owned);
            default -> throw new AssertionError("not an ownership-checked type: " + type);
        }
    }

    private List<EmailAttachment> attach(DocumentType type, String... ids) throws PDFGenerationException {
        return switch (type) {
            case EFORM -> manager.prepareEFormAttachments(login, "", ids, EMAIL_PATIENT);
            case DOC -> manager.prepareEDocAttachments(login, ids, EMAIL_PATIENT);
            case LAB -> manager.prepareLabAttachments(login, ids, EMAIL_PATIENT);
            case HRM -> manager.prepareHRMAttachments(login, ids, EMAIL_PATIENT);
            default -> throw new AssertionError("not an ownership-checked type: " + type);
        };
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"EFORM", "DOC", "LAB", "HRM"})
    void shouldPrepareAttachment_whenRecordBelongsToEmailPatient(DocumentType type) throws Exception {
        recordsOf(type, EMAIL_PATIENT, 42);

        List<EmailAttachment> attachments = attach(type, "42");

        assertThat(attachments).singleElement().satisfies(attachment -> {
            assertThat(attachment.getDocumentType()).isEqualTo(type);
            assertThat(attachment.getDocumentId()).isEqualTo(42);
        });
        verify(renderer).renderDocument(any(), eq(type), eq(42));
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"EFORM", "DOC", "LAB", "HRM"})
    void shouldRefuseAttachment_whenRecordBelongsToAnotherPatient(DocumentType type) throws Exception {
        recordsOf(type, OTHER_PATIENT, 42);

        // Refused as an attachment that cannot be found: nothing names the record.
        assertThatThrownBy(() -> attach(type, "42"))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessage(REFUSED);
        verify(renderer, never()).renderDocument(any(), any(DocumentType.class), anyInt());
    }

    @ParameterizedTest
    @EnumSource(value = DocumentType.class, names = {"EFORM", "DOC", "LAB", "HRM"})
    void shouldRenderNothing_whenOneOfTheSelectedRecordsBelongsToAnotherPatient(DocumentType type) throws Exception {
        recordsOf(type, EMAIL_PATIENT, 42);
        recordsOf(type, OTHER_PATIENT, 43);

        assertThatThrownBy(() -> attach(type, "42", "43"))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessage(REFUSED);
        verify(renderer, never()).renderDocument(any(), any(DocumentType.class), anyInt());
    }

    @Test
    void shouldRefuseEFormAttachments_whenSourceEFormBelongsToAnotherPatient() throws Exception {
        // The eForm being emailed is attached first; it must be the email patient's too.
        recordsOf(DocumentType.EFORM, EMAIL_PATIENT, 42);
        recordsOf(DocumentType.EFORM, OTHER_PATIENT, 77);

        assertThatThrownBy(() -> manager.prepareEFormAttachments(login, "77", new String[] {"42"}, EMAIL_PATIENT))
                .isInstanceOf(PDFGenerationException.class)
                .hasMessage(REFUSED);
        verify(renderer, never()).renderDocument(any(), any(DocumentType.class), anyInt());
    }
}
