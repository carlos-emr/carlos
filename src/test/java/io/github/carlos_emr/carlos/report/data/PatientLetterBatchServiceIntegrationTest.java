/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.data;

import io.github.carlos_emr.carlos.commn.dao.LogLettersDao;
import io.github.carlos_emr.carlos.commn.dao.ReportLettersDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ReportLetters;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.reports.FollowupManagement;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperReport;
import org.apache.pdfbox.Loader;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real PDF generation/merge and a real Spring/H2 transaction with injected persistence failures. */
@Tag("integration")
class PatientLetterBatchServiceIntegrationTest extends CarlosUnitTestBase {
    @TempDir Path directory;
    private SecurityInfoManager security;
    private DemographicManager demographics;
    private ReportLettersDao reports;
    private ProgramManager2 programs;
    private LogLettersDao logs;
    private LoggedInInfo info;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ReportLetters template;
    private boolean failSecondRender;
    private boolean failMerge;
    private boolean failFile;
    private boolean unknownCommit;
    private boolean afterCommitFailure;

    @BeforeEach
    void prepare() {
        security = mock(SecurityInfoManager.class);
        demographics = mock(DemographicManager.class);
        reports = mock(ReportLettersDao.class);
        programs = mock(ProgramManager2.class);
        logs = mock(LogLettersDao.class);
        info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(info), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(demographics.getDemographic(eq(info), anyString())).thenReturn(new Demographic());
        registerMock(ReportLettersDao.class, reports);
        registerMock(LogLettersDao.class, logs);
        var dataSource = new DriverManagerDataSource("jdbc:h2:mem:letters-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        transactions = new DataSourceTransactionManager(dataSource) {
            @Override protected void doCleanupAfterCompletion(Object transaction) {
                super.doCleanupAfterCompletion(transaction);
                if (afterCommitFailure) throw new org.springframework.transaction.TransactionSystemException("cleanup failed after commit");
            }
            @Override protected void doCommit(org.springframework.transaction.support.DefaultTransactionStatus status) {
                super.doCommit(status);
                if (unknownCommit) throw new org.springframework.transaction.TransactionSystemException("commit acknowledgement lost");
            }
        };
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE clinical_effect (kind VARCHAR(20), patient VARCHAR(20))");
        doAnswer(call -> { jdbc.update("INSERT INTO clinical_effect VALUES ('log', '')"); return null; }).when(logs).persist(any());
        template = new ReportLetters();
        template.setId(7);
        template.setArchive("0");
        template.setReportName("Synthetic letter");
        template.setReportFile(("<jasperReport name=\"Fixture\" pageWidth=\"595\" pageHeight=\"842\" columnWidth=\"555\" "
                + "leftMargin=\"20\" rightMargin=\"20\" topMargin=\"20\" bottomMargin=\"20\" whenNoDataType=\"AllSectionsNoDetail\">"
                + "<title height=\"40\"><element kind=\"staticText\" x=\"0\" y=\"0\" width=\"555\" height=\"20\">"
                + "<text>Fixture letter</text></element></title></jasperReport>").getBytes(StandardCharsets.UTF_8));
        when(reports.find(7)).thenReturn(template);
    }

    @org.junit.jupiter.api.AfterEach
    void closeDatabase() { if (jdbc != null) jdbc.execute("SHUTDOWN"); }

    private PatientLetterBatchService service() {
        return new PatientLetterBatchService(security, demographics, reports, programs, transactions) {
            @Override Path documentDirectory() { return failFile ? directory.resolve("missing") : directory; }
            @Override byte[] render(JasperReport report, String[] names, String patient) throws JRException {
                if (failSecondRender && "2".equals(patient)) throw new JRException("injected render failure");
                return super.render(report, names, patient);
            }
            @Override byte[] merge(List<Path> files) {
                if (failMerge) throw new IllegalStateException("injected merge failure");
                return super.merge(files);
            }
        };
    }

    private MockedStatic<EDocUtil> documentWrites(boolean failSecond) {
        MockedStatic<EDocUtil> edocs = mockStatic(EDocUtil.class);
        edocs.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenAnswer(call -> {
            EDoc doc = call.getArgument(0);
            assertThat(directory.resolve(doc.getFileName())).exists();
            jdbc.update("INSERT INTO clinical_effect VALUES ('document', ?)", doc.getModuleId());
            if (failSecond && "2".equals(doc.getModuleId())) throw new IllegalStateException("injected database failure");
            return "1";
        });
        return edocs;
    }

    private byte[] generate(boolean followUp) throws Exception {
        return service().generate(info, "7", new String[]{"1", "2", "01"}, followUp, "FLUF", "L1", "Synthetic follow-up");
    }

    private void assertNoEffects() throws Exception {
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clinical_effect", Integer.class)).isZero();
        try (var files = Files.list(directory)) { assertThat(files.toList()).isEmpty(); }
    }

    @Test
    void shouldKeepEveryPatientAndPriorFiles_whenBatchesSucceed() throws Exception {
        try (var _ = documentWrites(false)) {
            byte[] first = generate(false);
            byte[] second = generate(false);
            try (var pdf = Loader.loadPDF(first); var other = Loader.loadPDF(second)) {
                assertThat(pdf.getNumberOfPages()).isEqualTo(2);
                assertThat(other.getNumberOfPages()).isEqualTo(2);
            }
            try (var files = Files.list(directory)) { assertThat(files.toList()).hasSize(4); }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clinical_effect WHERE kind='document'", Integer.class)).isEqualTo(4);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clinical_effect WHERE kind='log'", Integer.class)).isEqualTo(2);
        }
    }

    @Test
    void shouldLeaveNoEffects_whenSecondPatientRenderingFails() throws Exception {
        failSecondRender = true;
        try (var writes = documentWrites(false)) {
            assertThatThrownBy(() -> generate(false)).isInstanceOf(JRException.class);
            assertNoEffects();
            writes.verify(() -> EDocUtil.addDocumentSQL(any(EDoc.class)), never());
        }
    }

    @Test
    void shouldRejectBeforeClinicalWrites_whenLetterHasNoPages() throws Exception {
        try (var filler = mockStatic(net.sf.jasperreports.engine.JasperFillManager.class)) {
            filler.when(() -> net.sf.jasperreports.engine.JasperFillManager.fillReport(any(JasperReport.class), anyMap(), any(net.sf.jasperreports.engine.JREmptyDataSource.class)))
                    .thenReturn(new net.sf.jasperreports.engine.JasperPrint());
            assertThatThrownBy(() -> generate(false)).isInstanceOf(JRException.class);
            assertNoEffects();
        }
    }

    @Test
    void shouldLeaveNoEffects_whenMergeFails() throws Exception {
        failMerge = true;
        try (var _ = documentWrites(false)) {
            assertThatThrownBy(() -> generate(false)).isInstanceOf(IllegalStateException.class);
            assertNoEffects();
        }
    }

    @Test
    void shouldRollBackAndRemoveFiles_whenSecondDocumentWriteFails() throws Exception {
        try (var _ = documentWrites(true)) {
            assertThatThrownBy(() -> generate(false)).isInstanceOf(IllegalStateException.class);
            assertNoEffects();
        }
    }

    @Test
    void shouldRollBackDocumentsAndLog_whenFollowUpFails() throws Exception {
        try (var _ = documentWrites(false);
             var _ = mockConstruction(FollowupManagement.class, (mock, context) ->
                 doAnswer(call -> {
                     jdbc.update("INSERT INTO clinical_effect VALUES ('followup', '1')");
                     throw new IllegalStateException("injected follow-up failure");
                 }).when(mock).markFollowupProcedure(anyString(), anyString(), any(String[].class), anyString(), any(), any()))) {
            assertThatThrownBy(() -> generate(true)).isInstanceOf(IllegalStateException.class);
            assertNoEffects();
        }
    }

    @Test
    void shouldUseDeduplicatedPatients_whenFollowUpsSucceed() throws Exception {
        try (var _ = documentWrites(false);
             var _ = mockConstruction(FollowupManagement.class, (mock, context) ->
                 doAnswer(call -> {
                     String[] ids = call.getArgument(2);
                     assertThat(ids).containsExactly("1", "2");
                     for (String id : ids) jdbc.update("INSERT INTO clinical_effect VALUES ('followup', ?)", id);
                     return null;
                 }).when(mock).markFollowupProcedure(anyString(), anyString(), any(String[].class), anyString(), any(), any()))) {
            generate(true);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clinical_effect", Integer.class)).isEqualTo(5);
        }
    }

    @Test
    void shouldStopBeforeTemplateAccess_whenPatientIsUnavailableOrRestricted() throws Exception {
        when(demographics.getDemographic(info, "2")).thenReturn(null);
        assertThatThrownBy(() -> generate(false)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(reports);
        assertNoEffects();
        when(demographics.getDemographic(info, "2")).thenThrow(new SecurityException("denied"));
        assertThatThrownBy(() -> generate(false)).isInstanceOf(SecurityException.class);
        assertNoEffects();
    }

    @Test
    void shouldPreventClinicalChanges_whenWritePermissionIsMissing() throws Exception {
        when(security.hasPrivilege(info, "_edoc", "w", "2")).thenReturn(false);
        assertThatThrownBy(() -> generate(false)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(reports);
        assertNoEffects();
        when(security.hasPrivilege(info, "_edoc", "w", "2")).thenReturn(true);
        when(security.hasPrivilege(info, "_measurement", "w", "2")).thenReturn(false);
        assertThatThrownBy(() -> generate(true)).isInstanceOf(SecurityException.class);
        assertNoEffects();
    }

    @Test
    void shouldLeaveNoEffects_whenTemplateIsArchivedOrMalformed() throws Exception {
        template.setArchive("1");
        assertThatThrownBy(() -> generate(false)).isInstanceOf(IllegalArgumentException.class);
        template.setArchive("0");
        template.setReportFile("not XML".getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(() -> generate(false)).isInstanceOf(JRException.class);
        assertNoEffects();
    }

    @Test
    void shouldRetainFilesAndSignalReconciliation_whenCommitIsUncertain() throws Exception {
        unknownCommit = true;
        try (var _ = documentWrites(false)) {
            assertThatThrownBy(() -> generate(false)).isInstanceOf(PatientLetterBatchService.OutcomeUncertainException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clinical_effect", Integer.class)).isEqualTo(3);
            try (var files = Files.list(directory)) { assertThat(files.toList()).hasSize(2); }
        }
    }

    @Test
    void shouldRetainFilesAndSignalReconciliation_whenCleanupFailsAfterCommit() throws Exception {
        afterCommitFailure = true;
        try (var writes = documentWrites(false)) {
            assertThatThrownBy(() -> generate(false)).isInstanceOf(PatientLetterBatchService.OutcomeUncertainException.class);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM clinical_effect", Integer.class)).isEqualTo(3);
            writes.verify(() -> EDocUtil.addDocumentSQL(any(EDoc.class)), times(2));
            try (var files = Files.list(directory)) { assertThat(files.toList()).hasSize(2); }
        }
    }

    @Test
    void shouldLeaveNoEffects_whenFileCreationFails() throws Exception {
        failFile = true;
        try (var _ = documentWrites(false)) {
            assertThatThrownBy(() -> generate(false)).isInstanceOf(java.io.IOException.class);
            assertNoEffects();
        }
    }

    @Test
    void shouldPreserveFullTemplateName_whenDocumentDescriptionWouldOverflow() {
        String title = "x".repeat(255);
        assertThat(PatientLetterBatchService.documentDescription(7, title)).isEqualTo(title);
        assertThat(PatientLetterBatchService.documentDescription(7, "Short")).isEqualTo("7-Short");
    }

    @Test
    void shouldRejectBeforePatientAccess_whenSelectionOrFollowUpIsInvalid() throws Exception {
        var batch = service();
        for (String id : new String[]{"0", "-1", "2147483648", "99999999999", "١", "../../etc"}) {
            assertThatThrownBy(() -> batch.generate(info, "7", new String[]{id}, false, null, null, null))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> batch.generate(info, "7", new String[]{"1"}, true, "FLUF", null, ""))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(demographics, reports);
        assertNoEffects();
    }
}
