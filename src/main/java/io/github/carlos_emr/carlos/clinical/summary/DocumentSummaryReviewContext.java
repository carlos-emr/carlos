/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Existing permission-aware eDoc readers, with an additional default-off synthetic trial boundary. */
@Service
public class DocumentSummaryReviewContext {
    private static final int MAX_BYTES = 20 * 1024 * 1024;
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;
    private final DocumentManager documents;
    private final SecurityInfoManager security;
    private final DemographicManager demographics;

    public record Snapshot(DocumentSummaryReviewBinding.Scope scope,
            DocumentSummaryReviewBinding.Metadata metadata, String noticeLanguage) {
        /** English notice, for callers that have no patient language. */
        public Snapshot(DocumentSummaryReviewBinding.Scope scope, DocumentSummaryReviewBinding.Metadata metadata) {
            this(scope, metadata, "en");
        }
        @Override public String toString() { return "SummaryReviewSnapshot[REDACTED]"; }
    }

    public DocumentSummaryReviewContext(DocumentManager documents, SecurityInfoManager security,
            DemographicManager demographics) {
        this.documents = Objects.requireNonNull(documents);
        this.security = Objects.requireNonNull(security);
        this.demographics = Objects.requireNonNull(demographics);
    }

    public static boolean enabled() {
        var settings = CarlosProperties.getInstance();
        return "true".equals(settings.getProperty(DocumentSummaryReviewSession.ENABLED, "false"))
                && "CA".equals(settings.getProperty(DocumentSummaryReviewSession.REGION, ""))
                && "true".equals(settings.getProperty(DocumentSummaryService.ENABLED_PROPERTY, "false"))
                && "true".equals(settings.getProperty(ClinicalSummaryGenerationService.ENABLED_PROPERTY, "false"));
    }

    @Transactional
    public Snapshot load(LoggedInInfo actor, int documentId) {
        // Re-query existing readers after detaching cached eDoc/link metadata; no inference holds a transaction.
        entityManager.clear();
        if (documentId <= 0 || actor == null || actor.getLoggedInProvider() == null
                || !"doctor".equals(actor.getLoggedInProvider().getProviderType())
                || !security.hasPrivilege(actor, "_edoc", "r", null)) deny();
        var link = documents.getCtlDocumentByDocumentId(actor, documentId);
        if (link == null || link.getId() == null || !link.isDemographicDocument()
                || !Objects.equals(link.getId().getDocumentNo(), documentId)
                || link.getId().getModuleId() == null || link.getId().getModuleId() <= 0) deny();
        int patient = link.getId().getModuleId();
        if (!security.hasPrivilege(actor, "_edoc", "r", patient)
                || !security.hasPrivilege(actor, "_eChart", "w", patient)
                || !security.isAllowedAccessToPatientRecord(actor, patient)) deny();
        var demographic = demographics.getDemographic(actor, Integer.valueOf(patient));
        if (demographic == null || SyntheticSummaryScope.fixtureId(demographic).isEmpty()) {
            throw new IllegalStateException("This review trial accepts only the verified synthetic patient fixtures.");
        }
        boolean visible = EDocUtil.listDocs(actor, "demographic", Integer.toString(patient), "all", EDocUtil.PRIVATE,
                EDocUtil.EDocSort.OBSERVATIONDATE, "active").stream()
                .anyMatch(item -> Integer.toString(documentId).equals(item.getDocId()));
        if (!visible) deny();
        var document = documents.getDocument(actor, documentId);
        if (document == null || !Objects.equals(document.getDocumentNo(), documentId) || document.getStatus() != 'A') deny();
        String clinic = CarlosProperties.getInstance().getProperty("patient_portal.clinic_id", "");
        if (!clinic.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,19}")) {
            throw new IllegalStateException("A valid server-side clinic scope is required.");
        }
        return new Snapshot(new DocumentSummaryReviewBinding.Scope(clinic, patient, documentId),
                new DocumentSummaryReviewBinding.Metadata(document.getDocfilename(), document.getContenttype(),
                        document.getDocdesc(), document.getUpdatedatetime() == null ? null : document.getUpdatedatetime().getTime()),
                // The patient notice follows the patient's languages on file (French from the official language,
                // else the spoken language for Spanish, Polish and Portuguese), never the sender's choice.
                DocumentSummaryReviewBinding.noticeLanguage(demographic.getOfficialLanguage(), demographic.getSpokenLanguage()));
    }

    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "stored filename is one component; existing canonical path must remain in DOCUMENT_DIR")
    public byte[] read(Snapshot snapshot) throws IOException {
        var directory = PathValidationUtils.resolveConfiguredDirectory(
                CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
        var name = PathValidationUtils.validatePathComponent(snapshot.metadata().filename(), "stored document filename");
        var file = PathValidationUtils.validateExistingPath(new java.io.File(directory, name), directory);
        byte[] bytes;
        try (var input = Files.newInputStream(file.toPath())) { bytes = input.readNBytes(MAX_BYTES + 1); }
        boolean accepted = false;
        try {
            if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("Original document is unavailable or exceeds the review limit.");
            accepted = true;
            return bytes;
        } finally { if (!accepted) Arrays.fill(bytes, (byte) 0); }
    }

    public DocumentSummaryReviewBinding.Source currentSource(Snapshot snapshot) throws IOException {
        byte[] bytes = read(snapshot);
        try { return DocumentSummaryReviewBinding.capture(snapshot.scope(), snapshot.metadata(), bytes); }
        finally { Arrays.fill(bytes, (byte) 0); }
    }
    private static void deny() { throw new SecurityException("Current clinician and patient document authorization is required."); }
}
