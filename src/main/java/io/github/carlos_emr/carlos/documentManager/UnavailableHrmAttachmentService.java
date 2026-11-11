/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpSession;
import java.io.Serializable;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Explicit removal of confirmed missing HRM sources, preserving permission-hidden relationships. */
@Service
public class UnavailableHrmAttachmentService {
    private static final String SESSION_KEY = UnavailableHrmAttachmentService.class.getName();
    private static final long MAX_AGE = 30 * 60 * 1000L;
    private final ConsultationRequestDao consultations;
    private final ConsultDocsDao attachments;
    private final HRMDocumentDao reports;
    private final SecurityInfoManager security;
    private final PlatformTransactionManager transactions;
    private final EReferAttachmentDataDao pendingAttachments;

    public UnavailableHrmAttachmentService(ConsultationRequestDao consultations, ConsultDocsDao attachments,
            HRMDocumentDao reports, SecurityInfoManager security, PlatformTransactionManager transactions,
            EReferAttachmentDataDao pendingAttachments) {
        this.consultations = consultations; this.attachments = attachments; this.reports = reports;
        this.security = security; this.transactions = transactions;
        this.pendingAttachments = pendingAttachments;
    }

    private record Selection(int consultation, int patient, String actor, List<Integer> relationships,
            long created) implements Serializable { }

    /** Opaque, session-bound selection: the page discloses no hidden report IDs, titles or counts. */
    public String prepare(LoggedInInfo actor, int consultation, HttpSession session) {
        var owner = consultations.find(consultation);
        if (owner == null || owner.getDemographicId() == null) return "";
        int patient = owner.getDemographicId();
        if (!mayWrite(actor, patient)) return "";
        List<Integer> missing = new ArrayList<>();
        for (var row : attachments.findByRequestIdDocType(consultation, ConsultDocs.DOCTYPE_HRM)) {
            if (reports.find(row.getDocumentNo()) == null) missing.add(row.getId());
        }
        if (missing.isEmpty()) return "";
        String token = UUID.randomUUID().toString();
        synchronized (session) {
            var selections = selections(session);
            selections.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().created() > MAX_AGE);
            while (selections.size() >= 16) selections.remove(selections.keySet().iterator().next());
            selections.put(token, new Selection(consultation, patient, actor.getLoggedInProviderNo(),
                    List.copyOf(missing), System.currentTimeMillis()));
            session.setAttribute(SESSION_KEY, selections);
        }
        return token;
    }

    /** Parent and exact relationship IDs are rechecked while holding the normal attachment-sync lock. */
    public void remove(LoggedInInfo actor, String token, HttpSession session) {
        if (actor == null) throw new SecurityException("missing required sec object (_con)");
        if (session == null || token == null) throw new IllegalArgumentException("Attachment selection expired; reload the consultation");
        Selection selected;
        synchronized (session) { selected = selections(session).get(token); }
        if (selected == null || System.currentTimeMillis() - selected.created() > MAX_AGE
                || !Objects.equals(selected.actor(), actor.getLoggedInProviderNo())) {
            throw new IllegalArgumentException("Attachment selection expired; reload the consultation");
        }
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            var owner = consultations.lockForAttachmentSync(selected.consultation());
            if (owner == null || !Objects.equals(owner.getDemographicId(), selected.patient())
                    || !mayWrite(actor, selected.patient())) throw new SecurityException("missing required sec object (_con)");
            var current = attachments.findByRequestIdDocTypeForUpdate(selected.consultation(), ConsultDocs.DOCTYPE_HRM);
            for (var row : current) {
                if (!selected.relationships().contains(row.getId())) continue;
                if (reports.find(row.getDocumentNo()) != null) {
                    throw new IllegalArgumentException("Attachment availability changed; reload the consultation");
                }
                row.setDeleted(ConsultDocs.DELETED);
                attachments.merge(row);
                LogAction.addLogStrict(actor, "DETACHED", "consultation", String.valueOf(selected.consultation()),
                        String.valueOf(selected.patient()), "Unavailable relationship " + row.getId());
                // A missing source cannot remain in this patient's pending external-referral packet.
                pendingAttachments.removeRecentByDocumentId(row.getDocumentNo(), ConsultDocs.DOCTYPE_HRM,
                        selected.patient(), new Date(System.currentTimeMillis() - 60 * 60 * 1000L));
            }
        });
    }

    private boolean mayWrite(LoggedInInfo info, int patient) {
        return info != null && security.hasPrivilege(info, "_con", "w", String.valueOf(patient))
                && security.isAllowedAccessToPatientRecord(info, patient);
    }

    @SuppressWarnings("unchecked")
    private static LinkedHashMap<String, Selection> selections(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        return existing instanceof LinkedHashMap<?, ?> ? (LinkedHashMap<String, Selection>) existing : new LinkedHashMap<>();
    }
}
