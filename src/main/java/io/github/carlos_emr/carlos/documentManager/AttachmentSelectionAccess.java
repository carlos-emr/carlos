// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Collection;
import java.util.HashSet;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Qualifier;

/** Validates attachment selections while the caller holds the attachment parent's lock. */
@Service
public class AttachmentSelectionAccess {
    private final SecurityInfoManager security;
    private final DocumentDao documents;
    private final EFormDataDao eforms;
    private final HRMDocumentToDemographicDao hrms;
    private final FormsManager forms;

    public AttachmentSelectionAccess(SecurityInfoManager security,
            @Qualifier("documentDao") DocumentDao documents,
            EFormDataDao eforms, HRMDocumentToDemographicDao hrms, FormsManager forms) {
        this.security = security;
        this.documents = documents;
        this.eforms = eforms;
        this.hrms = hrms;
        this.forms = forms;
    }

    /** Returns false for an unchanged restricted selection, which must be preserved verbatim. */
    public boolean validate(LoggedInInfo info, DocumentType type, int patient,
                            Collection<String> selected, Collection<String> existing) {
        String privilege = privilege(type);
        if (!security.hasPrivilege(info, privilege, SecurityInfoManager.READ, String.valueOf(patient))) {
            if (new HashSet<>(selected).equals(new HashSet<>(existing))) return false;
            throw new SecurityException("missing required sec object (" + privilege + ")");
        }
        // LAB source and ownership are checked together by the caller before any writes.
        if (type == DocumentType.LAB) return true;
        for (String value : selected) {
            if (value == null || !value.matches("[1-9][0-9]{0,9}")) {
                throw new IllegalArgumentException("Attachment requires a positive ID");
            }
            int id = Integer.parseInt(value);
            boolean owned = switch (type) {
                case DOC -> documents.findCtlDocsAndDocsByDocNo(id).stream().anyMatch(row ->
                        row.length > 1 && row[0] instanceof Document document && document.getStatus() != 'D'
                                && row[1] instanceof CtlDocument link && !"D".equals(link.getStatus()) && link.getId() != null
                                && "demographic".equals(link.getId().getModule())
                                && Integer.valueOf(patient).equals(link.getId().getModuleId()));
                case EFORM -> {
                    var form = eforms.find(id);
                    yield form != null && Integer.valueOf(patient).equals(form.getDemographicId());
                }
                case HRM -> hrms.findByHrmDocumentId(id).stream().anyMatch(link ->
                        String.valueOf(patient).equals(String.valueOf(link.getDemographicNo())));
                case FORM -> forms.getEncounterFormsbyDemographicNumber(info, patient, true, false)
                        .stream().anyMatch(form -> value.equals(form.getFormId())
                                && Integer.valueOf(patient).equals(form.demographicId));
                default -> false;
            };
            if (!owned) throw new IllegalArgumentException("Attachment does not belong to this patient");
        }
        return true;
    }

    /** Uploads have no stored ID yet, so require read permission before creating a file. */
    public void requireRead(LoggedInInfo info, DocumentType type, int patient) {
        String privilege = privilege(type);
        if (!security.hasPrivilege(info, privilege, SecurityInfoManager.READ, String.valueOf(patient))) {
            throw new SecurityException("missing required sec object (" + privilege + ")");
        }
    }

    private static String privilege(DocumentType type) {
        return switch (type) {
            case DOC -> "_edoc";
            case LAB -> "_lab";
            case EFORM -> "_eform";
            case HRM -> "_hrm";
            case FORM -> "_form";
            default -> throw new IllegalArgumentException("Unsupported attachment type");
        };
    }

}
