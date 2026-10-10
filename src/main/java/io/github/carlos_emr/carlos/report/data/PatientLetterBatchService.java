/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.data;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.dao.ReportLettersDao;
import io.github.carlos_emr.carlos.commn.model.ReportLetters;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.eform.APExecute;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.reports.FollowupManagement;
import io.github.carlos_emr.carlos.util.ConcatPDF;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Renders a whole authorized batch before atomically recording its clinical effects. */
@Service
public class PatientLetterBatchService {
    private final SecurityInfoManager security;
    private final DemographicManager demographics;
    private final ReportLettersDao reports;
    private final ProgramManager2 programs;
    private final TransactionTemplate transaction;

    /**
     * Creates the renderer and transaction boundary for complete patient-letter batches.
     *
     * @param security caller and patient privilege checks
     * @param demographics patient visibility checks
     * @param reports stored letter templates
     * @param programs current provider program metadata
     * @param transactionManager manager coordinating document, log and follow-up writes
     */
    public PatientLetterBatchService(SecurityInfoManager security, DemographicManager demographics,
                                    ReportLettersDao reports, ProgramManager2 programs,
                                    PlatformTransactionManager transactionManager) {
        this.security = security;
        this.demographics = demographics;
        this.reports = reports;
        this.programs = programs;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * Renders every authorized selected patient before committing the batch's clinical effects.
     * Generated files are retained after success or an uncertain commit, and removed after a
     * confirmed failure. Duplicate numeric patient IDs produce one letter and one follow-up.
     *
     * @param info authenticated caller context
     * @param reportId positive ID of an active template
     * @param selected patient IDs to resolve and authorize
     * @param followUp whether to record follow-up measurements
     * @param followUpType measurement type when follow-up is requested
     * @param followUpValue measurement value when follow-up is requested
     * @param comment optional follow-up comment
     * @return merged PDF containing all selected patients
     * @throws IllegalArgumentException if the selection, template or follow-up metadata is invalid
     * @throws SecurityException if the caller lacks patient or operation access
     * @throws IOException if an owned PDF cannot be created or written
     * @throws JRException if template compilation or rendering fails
     * @throws OutcomeUncertainException if commit is uncertain or completion fails after commit
     */
    public byte[] generate(LoggedInInfo info, String reportId, String[] selected, boolean followUp,
                           String followUpType, String followUpValue, String comment) throws IOException, JRException {
        requirePrivilege(info, "_report", "r", null);
        int id = positiveId(reportId);
        validateFollowUp(followUp, followUpType, followUpValue, comment);
        LinkedHashSet<String> patients = authorizedPatients(info, selected, followUp);
        ReportLetters template = reports.find(id);
        if (template == null || !"0".equals(template.getArchive()) || template.getReportFile() == null) {
            throw new IllegalArgumentException("Letter template is unavailable");
        }
        JasperReport report = JasperCompileManager.compileReport(new java.io.ByteArrayInputStream(template.getReportFile()));
        String[] parameters = ManageLetters.getReportParams(report);
        String provider = info.getLoggedInProviderNo();
        ProgramProvider program = programs.getCurrentProgramInDomain(info, provider);
        Path directory = documentDirectory();
        List<Path> files = new ArrayList<>();
        List<EDoc> documents = new ArrayList<>();
        boolean committed = false;
        AtomicBoolean retainFiles = new AtomicBoolean();
        try {
            for (String patient : patients) {
                byte[] rendered = render(report, parameters, patient);
                // Exclusive creation avoids overwriting earlier or concurrent letters. Neither the
                // patient ID nor the user-supplied template name becomes a filesystem component.
                Path file = createLetterFile(directory, id);
                files.add(file);
                Files.write(file, rendered);
                EDoc document = new EDoc(documentDescription(id, template.getReportName()), "others", file.getFileName().toString(),
                        "", provider, provider, "", 'A', UtilDateUtilities.DateToString(new Date()),
                        "", "", "demographic", patient);
                document.setFileName(file.getFileName().toString());
                document.setDocPublic("0");
                document.setContentType("application/pdf");
                if (program != null && program.getProgramId() != null) document.setProgramId(program.getProgramId().intValue());
                documents.add(document);
            }
            byte[] merged = merge(files); // strict merge: no silently skipped patients
            try {
                transaction.executeWithoutResult(status -> {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override public void afterCompletion(int completion) {
                            // A lost commit acknowledgement must not delete possibly committed PDFs.
                            retainFiles.set(completion != STATUS_ROLLED_BACK);
                        }
                    });
                    for (EDoc document : documents) EDocUtil.addDocumentSQL(document);
                    String[] ids = patients.toArray(String[]::new);
                    new ManageLetters().logLetterCreated(provider, Integer.toString(id), ids);
                    if (followUp) {
                        new FollowupManagement().markFollowupProcedure(followUpType, followUpValue, ids,
                                provider, new Date(), comment);
                    }
                });
            } catch (RuntimeException failure) {
                if (retainFiles.get()) throw new OutcomeUncertainException(failure);
                throw failure;
            }
            committed = true;
            return merged;
        } finally {
            if (!committed && !retainFiles.get()) {
                removeUncommittedFiles(files);
            }
        }
    }

    private static void validateFollowUp(boolean followUp, String type, String value, String comment) {
        if (followUp && (type == null || type.isBlank() || type.length() > 50
                || value == null || value.isBlank() || value.length() > 255
                || (comment != null && comment.length() > 255))) {
            throw new IllegalArgumentException("Invalid follow-up selection");
        }
    }

    private LinkedHashSet<String> authorizedPatients(LoggedInInfo info, String[] selected, boolean followUp) {
        if (selected == null || selected.length == 0) throw new IllegalArgumentException("No patients selected");
        LinkedHashSet<String> patients = new LinkedHashSet<>();
        for (String value : selected) patients.add(Integer.toString(positiveId(value)));
        for (String patient : patients) {
            if (demographics.getDemographic(info, patient) == null) {
                throw new IllegalArgumentException("Patient selection is unavailable");
            }
            requirePrivilege(info, "_edoc", "w", patient);
            if (followUp) requirePrivilege(info, "_measurement", "w", patient);
        }
        return patients;
    }

    @edu.umd.cs.findbugs.annotations.SuppressFBWarnings(value = "PATH_TRAVERSAL_IN",
            justification = "Private creation in canonical administrator-configured DOCUMENT_DIR; prefix is a validated integer and the remainder is generated exclusively by Files.createTempFile")
    private static Path createLetterFile(Path directory, int id) throws IOException {
        return Files.createTempFile(directory, "letter-" + id + "-", ".pdf");
    }

    private static void removeUncommittedFiles(List<Path> files) {
        for (Path file : files) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException cleanupFailure) {
                MiscUtils.getLogger().error("Could not remove uncommitted patient-letter file ({})",
                        cleanupFailure.getClass().getSimpleName());
            }
        }
    }

    /** Completion failed after a committed or uncertain transaction; retain files for reconciliation. */
    public static class OutcomeUncertainException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        /**
         * Records a transaction failure whose outcome requires reconciliation before retrying.
         * @param cause original commit or rollback failure
         */
        public OutcomeUncertainException(Throwable cause) {
            super("Patient letter transaction outcome is unknown", cause);
        }
    }

    Path documentDirectory() throws IOException {
        return PathValidationUtils.getRequiredDocumentDirectory().toPath();
    }

    byte[] render(JasperReport report, String[] names, String patient) throws JRException {
        Map<String, Object> values = new HashMap<>();
        APExecute executor = new APExecute();
        for (String name : names) values.put(name, executor.execute(name, patient));
        var print = JasperFillManager.fillReport(report, values, new JREmptyDataSource());
        if (print.getPages().isEmpty()) throw new JRException("Letter template produced no pages");
        return JasperExportManager.exportReportToPdf(print);
    }

    byte[] merge(List<Path> files) {
        List<Object> sources = files.stream().map(path -> (Object) path.toString()).toList();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ConcatPDF.concatRequired(sources, output);
        return output.toByteArray();
    }

    static String documentDescription(int id, String name) {
        String title = name == null ? "Letter" : name;
        String prefixed = id + "-" + title;
        // A full-width template name fits docdesc; adding an ID would overflow it.
        // The report ID remains in log_letters and the unique file prefix.
        return prefixed.length() <= 255 ? prefixed : title;
    }

    static int positiveId(String value) {
        if (value == null || !value.matches("\\d{1,10}")) throw new IllegalArgumentException("Invalid selection");
        try {
            int id = Integer.parseInt(value);
            if (id > 0) return id;
        } catch (NumberFormatException _) {
            // An out-of-range numeric value is invalid, never a DAO argument.
        }
        throw new IllegalArgumentException("Invalid selection");
    }

    private void requirePrivilege(LoggedInInfo info, String object, String rights, String patient) {
        if (!security.hasPrivilege(info, object, rights, patient)) {
            throw new SecurityException("missing required sec object (" + object + ")");
        }
    }
}
