/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.log.*;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

/** Trusted review and metadata transitions share a document lock and a strict audit transaction. */
@Service
public class DocumentReviewService {
    public enum Outcome { NONE, FIRST, ADDITIONAL, ALREADY_REVIEWED }
    public static class MissingDocument extends RuntimeException { private static final long serialVersionUID = 1L; }
    public static class Conflict extends RuntimeException { private static final long serialVersionUID = 1L; }
    public static class SaveFailure extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final boolean rollbackConfirmed;
        SaveFailure(RuntimeException cause, boolean rollbackConfirmed) {
            super("Document save was not confirmed", cause); this.rollbackConfirmed = rollbackConfirmed;
        }
        public boolean rollbackConfirmed() { return rollbackConfirmed; }
    }
    public record Baseline(String content, String metadata) { }
    private final DocumentDao documents;
    private final DocumentExtraReviewerDao extras;
    private final CtlDocumentDao links;
    private final SecurityInfoManager security;
    private final PlatformTransactionManager transactions;

    public DocumentReviewService(@Qualifier("documentDao") DocumentDao documents, DocumentExtraReviewerDao extras, CtlDocumentDao links,
            SecurityInfoManager security, PlatformTransactionManager transactions) {
        this.documents = documents; this.extras = extras; this.links = links;
        this.security = security; this.transactions = transactions;
    }

    /** Called only for an authorized native editor. Missing bytes disable attestation, not metadata recovery. */
    public Baseline baseline(LoggedInInfo info, int id) {
        IncomingDocumentCapacityResponse.requireStoredDocumentReadAccess(security, info, id);
        Document stored = documents.find(id);
        if (stored == null || stored.getStatus() == 'D') throw new MissingDocument();
        String content;
        try { content = DocumentEditRevision.content(stored); }
        catch (IOException | IllegalArgumentException | SecurityException unavailable) { content = ""; }
        return new Baseline(content, DocumentEditRevision.metadata(stored));
    }

    public void requireContent(Document stored, String observed) {
        try {
            if (!StoredDocumentRevision.valid(observed)
                    || !observed.equals(DocumentEditRevision.content(stored))) throw new Conflict();
        } catch (IOException | IllegalArgumentException | SecurityException failure) { throw new Conflict(); }
    }

    /** Replacement bytes have been staged under the source lease before this transaction starts. */
    public Outcome edit(LoggedInInfo info, EDoc edit, boolean review, boolean replacement, boolean htmlEditor,
            String observedContent, String observedMetadata) {
        return transaction(() -> {
            int id = Integer.parseInt(edit.getDocId());
            Document stored = documents.findForPageMutation(id);
            if (stored == null || stored.getStatus() == 'D') throw new MissingDocument();
            IncomingDocumentCapacityResponse.requireStoredDocumentWriteAccess(security, info, edit.getDocId());
            if ((htmlEditor && stored.getStatus() != 'H') || (replacement && stored.getStatus() == 'H')) {
                throw new Conflict();
            }
            boolean contentChanged = replacement || (htmlEditor && !Objects.equals(edit.getHtml(), stored.getDocxml()));
            if (review || contentChanged) requireContent(stored, observedContent);
            String submitted = DocumentEditRevision.metadata(edit, htmlEditor, stored);
            if (StoredDocumentRevision.valid(observedMetadata)
                    && !observedMetadata.equals(DocumentEditRevision.metadata(stored))) {
                if (!observedMetadata.equals(submitted)) throw new Conflict();
                copyMetadata(stored, edit);
            }
            if (!htmlEditor) edit.setHtml(stored.getDocxml());
            else {
                edit.setAbnormal(Boolean.toString(stored.isAbnormal()));
                edit.setReceivedDate(formatDate(stored.getReceivedDate(), "yyyy/MM/dd"));
            }
            if (List.of("demographic", "provider", "providers").contains(edit.getModule())
                    && !EDocUtil.getDoctypes(edit.getModule()).contains(edit.getType())) {
                EDocUtil.addDocTypeSQL(edit.getType(), edit.getModule());
            }
            if (contentChanged || !review || stored.getReviewer() == null || stored.getReviewer().isBlank()) retire(info, stored);
            Outcome outcome = review ? record(info, stored, edit, contentChanged) : Outcome.NONE;
            Date firstReviewTime = stored.getReviewdatetime();
            EDocUtil.editDocumentSQL(edit, review);
            if (outcome == Outcome.ADDITIONAL || outcome == Outcome.ALREADY_REVIEWED) {
                // The EDoc adapter formats to seconds. An additional review must not round
                // an existing attestation's timestamp in stores with fractional precision.
                stored.setReviewdatetime(firstReviewTime);
            }
            if (outcome == Outcome.FIRST || outcome == Outcome.ADDITIONAL) audit(info, stored, LogConst.REVIEWED, "");
            audit(info, stored, LogConst.UPDATE, "");
            return outcome;
        });
    }

    /** Creating HTML/link content and its initial review use the generated ID in the same transaction. */
    public String create(LoggedInInfo info, EDoc edit, boolean review) {
        return transaction(() -> {
            if (info == null || !security.hasPrivilege(info, "_edoc", "w", (String) null)) {
                throw new SecurityException("missing required sec object (_edoc)");
            }
            if (!List.of("demographic", "provider", "providers").contains(edit.getModule())) {
                throw new SecurityException("Invalid document target module");
            }
            if ("demographic".equals(edit.getModule())) IncomingDocumentCapacityResponse
                    .requirePatientDocumentWriteAccess(security, info, edit.getModuleId());
            if (!EDocUtil.getDoctypes(edit.getModule()).contains(edit.getType())) {
                EDocUtil.addDocTypeSQL(edit.getType(), edit.getModule());
            }
            String id = EDocUtil.addDocumentSQL(edit);
            if (review) {
                Document stored = documents.findForPageMutation(Integer.parseInt(id));
                if (stored == null) throw new MissingDocument();
                stored.setReviewer(info.getLoggedInProviderNo());
                stored.setReviewdatetime(new Date());
                documents.merge(stored);
                audit(info, stored, LogConst.REVIEWED, "");
            }
            return id;
        });
    }

    private Outcome record(LoggedInInfo info, Document stored, EDoc edit, boolean replaced) {
        String actor = info.getLoggedInProviderNo();
        Date now = new Date();
        if (replaced || stored.getReviewer() == null || stored.getReviewer().isBlank()) {
            edit.setReviewerId(actor); edit.setReviewDateTime(formatDate(now, EDocUtil.REVIEW_DATETIME_FORMAT));
            return Outcome.FIRST;
        }
        edit.setReviewerId(stored.getReviewer());
        edit.setReviewDateTime(formatDate(stored.getReviewdatetime(), EDocUtil.REVIEW_DATETIME_FORMAT));
        if (actor.equals(stored.getReviewer()) || extras.findByDocumentNo(stored.getId()).stream()
                .anyMatch(row -> actor.equals(row.getReviewerProviderNo()))) return Outcome.ALREADY_REVIEWED;
        var row = new DocumentExtraReviewer();
        row.setDocumentNo(stored.getId()); row.setReviewerProviderNo(actor); row.setReviewDateTime(now);
        extras.persist(row);
        return Outcome.ADDITIONAL;
    }

    /** Archive each attestation before retiring current projections, keeping audit payloads bounded. */
    private void retire(LoggedInInfo info, Document stored) {
        // A plain metadata update can legitimately arrive from an older page. Its posted
        // baseline must never label the current stored attestation's archived content.
        String revision;
        try { revision = DocumentEditRevision.content(stored); }
        catch (IOException | IllegalArgumentException | SecurityException unavailable) { revision = "unavailable"; }
        String identity = "contentRevision=" + revision + "; previousReviewer=";
        if (stored.getReviewer() != null && !stored.getReviewer().isBlank()) {
            audit(info, stored, "REVIEW_INVALIDATED", identity + stored.getReviewer() + "; time=" + epoch(stored.getReviewdatetime()));
        }
        for (var row : extras.findByDocumentNo(stored.getId())) {
            audit(info, stored, "REVIEW_INVALIDATED", identity + row.getReviewerProviderNo() + "; time=" + epoch(row.getReviewDateTime()));
            extras.remove(row.getId());
        }
        for (var row : documents.findCurrentReviewRows(stored.getId())) {
            audit(info, stored, "REVIEW_INVALIDATED", identity + row.getProviderNo() + "; time=" + epoch(row.getDateTimeReviewed()));
        }
        documents.retireCurrentReviewRows(stored.getId());
        stored.setReviewer(null); stored.setReviewdatetime(null);
    }

    private void audit(LoggedInInfo info, Document stored, String action, String data) {
        var patients = links.findByDocumentNoAndModule(stored.getId(), "demographic").stream()
                .map(row -> row.getId().getModuleId()).filter(id -> id != null && id > 0).distinct().toList();
        String patient = patients.size() == 1 ? patients.getFirst().toString() : null;
        LogAction.addLogStrict(info, action, LogConst.CON_DOCUMENT, stored.getId().toString(), patient,
                data);
    }

    private <T> T transaction(java.util.function.Supplier<T> work) {
        AtomicInteger completion = new AtomicInteger(-1);
        var template = new TransactionTemplate(transactions);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        try {
            return template.execute(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override public void afterCompletion(int result) { completion.set(result); }
                });
                return work.get();
            });
        } catch (RuntimeException failure) {
            throw new SaveFailure(failure, completion.get() == TransactionSynchronization.STATUS_ROLLED_BACK);
        }
    }

    private static void copyMetadata(Document d, EDoc edit) {
        edit.setType(d.getDoctype()); edit.setDocClass(d.getDocClass()); edit.setDocSubClass(d.getDocSubClass());
        edit.setDescription(d.getDocdesc()); edit.setSource(d.getSource()); edit.setSourceFacility(d.getSourceFacility());
        edit.setResponsibleId(d.getResponsible()); edit.setDocPublic(String.valueOf(d.getPublic1()));
        edit.setAbnormal(Boolean.toString(d.isAbnormal()));
        edit.setReceivedDate(formatDate(d.getReceivedDate(), "yyyy/MM/dd"));
        edit.setObservationDate(formatDate(d.getObservationdate(), "yyyy/MM/dd"));
    }
    private static String epoch(Date date) { return date == null ? "unknown" : Long.toString(date.getTime()); }

    private static String formatDate(Date date, String format) {
        return date == null ? "" : UtilDateUtilities.DateToString(date, format);
    }
}
