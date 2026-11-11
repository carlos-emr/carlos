// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.OceanEReferralAttachmentUtil;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicitly removes a stored HRM relationship, including when the report itself is gone. */
@Service
public class ConsultationHrmAttachmentPersister {
    private final ConsultationRequestDao requests;
    private final ConsultDocsDao attachments;
    private final ConsultationRequestExtDao extensions;
    private final SecurityInfoManager security;

    public ConsultationHrmAttachmentPersister(ConsultationRequestDao requests, ConsultDocsDao attachments,
            ConsultationRequestExtDao extensions, SecurityInfoManager security) {
        this.requests = requests;
        this.attachments = attachments;
        this.extensions = extensions;
        this.security = security;
    }

    @Transactional
    public void detach(LoggedInInfo info, int requestId, int associationId) {
        var owner = requests.lockForAttachmentSync(requestId);
        if (owner == null || owner.getDemographicId() == null) throw new NoSuchElementException();
        int patient = owner.getDemographicId();
        if (!security.hasPrivilege(info, "_con", SecurityInfoManager.WRITE, patient)) {
            throw new SecurityException("missing required sec object (_con)");
        }
        if (!security.hasPrivilege(info, "_hrm", SecurityInfoManager.READ, (String) null)) {
            throw new SecurityException("missing required sec object (_hrm)");
        }
        // Like the existing detach-only contract, this discloses no report and adds no records.
        // Do not require a live HRM ownership link: deleting the report removes that very link.
        ConsultDocs selected = attachments.find(associationId);
        if (selected == null || selected.getRequestId() != requestId
                || !ConsultDocs.DOCTYPE_HRM.equals(selected.getDocType())) throw new NoSuchElementException();
        if ("Y".equals(selected.getDeleted())) return;
        if (selected.getDeleted() != null) throw new NoSuchElementException();
        var active = attachments.findByRequestIdDocTypeForUpdate(requestId, ConsultDocs.DOCTYPE_HRM);
        for (ConsultDocs row : active) {
            if (row.getDocumentNo() == selected.getDocumentNo()) {
                row.setDeleted("Y");
                attachments.merge(row);
            }
        }
        if (extensions.getConsultationRequestExts(requestId).stream()
                .anyMatch(ext -> "ereferral_ref".equals(ext.getKey()))) {
            OceanEReferralAttachmentUtil.detachOceanEReferralConsult(
                    Integer.toString(selected.getDocumentNo()), patient, ConsultDocs.DOCTYPE_HRM);
        }
        LogAction.addLogStrict(info, "delete", "consultation HRM attachment", Integer.toString(associationId),
                Integer.toString(patient), "explicit detach");
    }
}
