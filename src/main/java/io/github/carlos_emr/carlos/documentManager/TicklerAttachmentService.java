/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 *
 * Ported from the openo-beta/Open-O tickler attachment component
 * (PR #2491, Sebastian Ibanez) and adapted for CARLOS.
 */
package io.github.carlos_emr.carlos.documentManager;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDocsDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.documentManager.data.AttachmentLabResultData;
import io.github.carlos_emr.carlos.documentManager.data.TicklerAttachmentData;
import io.github.carlos_emr.carlos.encounter.data.EctFormData;
import io.github.carlos_emr.carlos.hospitalReportManager.HRMUtil;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Attaches the patient's documents, labs, eForms, encounter forms and HRM reports to a
 * tickler through the shared attachment picker, and resolves them for display.
 *
 * <p>Tickler counterpart of {@link DocumentAttachmentManager#attachToConsult} /
 * {@link DocumentAttachmentManager#attachToEForm}, with the checks the parallel fork left out:</p>
 * <ul>
 *   <li>every write requires {@code _tickler} write on the tickler's patient, every read
 *       {@code _tickler} read;</li>
 *   <li>every attached id is verified to belong to the tickler's patient before it is stored, so a
 *       crafted POST cannot pin another patient's document to this chart;</li>
 *   <li>each type also needs the caller's read right on that type ({@code _edoc}, {@code _lab},
 *       {@code _eform}, {@code _hrm}, {@code _form}); a reader who lacks it sees the attachment
 *       listed but not named;</li>
 *   <li>only the types actually submitted are synchronised, so a save that never opened the
 *       picker leaves the stored set untouched;</li>
 *   <li>the attaching provider is always the authenticated session provider, and every attach
 *       and detach is audited.</li>
 * </ul>
 *
 * @since 2026-09-26
 */
@Service
public class TicklerAttachmentService {

    private static final Logger logger = MiscUtils.getLogger();
    private static final String TICKLER_SECURITY_OBJECT = "_tickler";
    private static final String MISSING_TICKLER_SECURITY_OBJECT = "missing required sec object (_tickler)";

    private static final Map<DocumentType, String> READ_SECURITY_OBJECTS;

    static {
        Map<DocumentType, String> objects = new EnumMap<>(DocumentType.class);
        objects.put(DocumentType.DOC, "_edoc");
        objects.put(DocumentType.LAB, "_lab");
        objects.put(DocumentType.EFORM, "_eform");
        objects.put(DocumentType.HRM, "_hrm");
        objects.put(DocumentType.FORM, "_form");
        READ_SECURITY_OBJECTS = Collections.unmodifiableMap(objects);
    }

    private final TicklerDocsDao ticklerDocsDao;
    private final SecurityInfoManager securityInfoManager;
    private final DocumentDao documentDao;
    private final PatientLabRoutingDao patientLabRoutingDao;
    private final EFormDataDao eFormDataDao;
    private final HRMDocumentToDemographicDao hrmDocumentToDemographicDao;
    private final FormsManager formsManager;
    private final DocumentAttachmentManager documentAttachmentManager;

    public TicklerAttachmentService(TicklerDocsDao ticklerDocsDao,
                                    SecurityInfoManager securityInfoManager,
                                    DocumentDao documentDao,
                                    PatientLabRoutingDao patientLabRoutingDao,
                                    EFormDataDao eFormDataDao,
                                    HRMDocumentToDemographicDao hrmDocumentToDemographicDao,
                                    FormsManager formsManager,
                                    DocumentAttachmentManager documentAttachmentManager) {
        this.ticklerDocsDao = ticklerDocsDao;
        this.securityInfoManager = securityInfoManager;
        this.documentDao = documentDao;
        this.patientLabRoutingDao = patientLabRoutingDao;
        this.eFormDataDao = eFormDataDao;
        this.hrmDocumentToDemographicDao = hrmDocumentToDemographicDao;
        this.formsManager = formsManager;
        this.documentAttachmentManager = documentAttachmentManager;
    }

    /**
     * @param documentType DocumentType an attachment type
     * @return String the security object whose read right governs seeing that type's items
     */
    public static String readSecurityObject(DocumentType documentType) {
        return READ_SECURITY_OBJECTS.get(documentType);
    }

    /**
     * Synchronises the tickler's attachments with a picker submission. Each submitted type is
     * treated as the complete desired set for that type: ids not yet stored are attached, stored
     * ids that were not submitted are soft-deleted. Types absent from {@code submitted} are left
     * exactly as they are.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session; its provider is recorded as the
     *        attaching provider
     * @param tickler Tickler the persisted tickler (needs id and demographic)
     * @param submitted Map&lt;DocumentType, ? extends Collection&lt;String&gt;&gt; the desired ids per type
     * @throws SecurityException when the caller lacks {@code _tickler} write on the patient, lacks
     *         read on a submitted type, or submits an id that is not the patient's
     * @throws IllegalArgumentException when an id is not numeric
     */
    @Transactional
    public void syncAttachments(LoggedInInfo loggedInInfo, Tickler tickler,
                                Map<DocumentType, ? extends Collection<String>> submitted) {
        Integer demographicNo = tickler.getDemographicNo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, TICKLER_SECURITY_OBJECT, SecurityInfoManager.WRITE,
                String.valueOf(demographicNo))) {
            throw new SecurityException(MISSING_TICKLER_SECURITY_OBJECT);
        }
        if (submitted == null || submitted.isEmpty()) {
            return;
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        for (Map.Entry<DocumentType, ? extends Collection<String>> entry : submitted.entrySet()) {
            DocumentType documentType = entry.getKey();
            Set<Integer> wanted = parseIds(entry.getValue());
            // Ownership and type-read checks come before any write, so a rejected submission
            // leaves the stored set untouched rather than half-synchronised. A caller who cannot
            // read a type never sees its items in the picker, so an empty submission for that
            // type is "nothing shown", not "detach all": the stored rows are left alone.
            if (!isTypeReadable(loggedInInfo, documentType, demographicNo)) {
                if (wanted.isEmpty()) {
                    continue;
                }
                requireTypeReadable(loggedInInfo, documentType, demographicNo);
            }
            Map<Integer, String> labTypes = new HashMap<>();
            for (Integer documentNo : wanted) {
                labTypes.put(documentNo, requireBelongsToPatient(loggedInInfo, documentType, documentNo, demographicNo));
            }

            List<TicklerDocs> stored = ticklerDocsDao.findByTicklerIdDocType(tickler.getId(), documentType.getType());
            Set<Integer> existing = new HashSet<>();
            for (TicklerDocs storedDoc : stored) {
                existing.add(storedDoc.getDocumentNo());
                if (!wanted.contains(storedDoc.getDocumentNo())) {
                    storedDoc.setDeleted(TicklerDocs.DELETED);
                    ticklerDocsDao.merge(storedDoc);
                    audit(loggedInInfo, LogConst.DELETE, tickler, documentType, storedDoc.getDocumentNo());
                }
            }
            for (Integer documentNo : wanted) {
                if (existing.contains(documentNo)) {
                    continue;
                }
                TicklerDocs ticklerDocs = new TicklerDocs(tickler.getId(), documentNo, documentType.getType(), providerNo);
                ticklerDocs.setLabType(labTypes.get(documentNo));
                ticklerDocsDao.persist(ticklerDocs);
                audit(loggedInInfo, LogConst.ADD, tickler, documentType, documentNo);
            }
        }
    }

    /**
     * Lists the tickler's attachments with display names, for the Add/Edit windows.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session
     * @param tickler Tickler the tickler being shown
     * @return List&lt;TicklerAttachmentData&gt; every live attachment, oldest first; items of a type
     *         the caller may not read are returned unnamed
     * @throws SecurityException when the caller lacks {@code _tickler} read on the patient
     */
    public List<TicklerAttachmentData> listAttachments(LoggedInInfo loggedInInfo, Tickler tickler) {
        Integer demographicNo = tickler.getDemographicNo();
        if (!securityInfoManager.hasPrivilege(loggedInInfo, TICKLER_SECURITY_OBJECT, SecurityInfoManager.READ,
                String.valueOf(demographicNo))) {
            throw new SecurityException(MISSING_TICKLER_SECURITY_OBJECT);
        }

        List<TicklerAttachmentData> attachments = new ArrayList<>();
        Map<DocumentType, Boolean> readable = new EnumMap<>(DocumentType.class);
        Map<String, String> labNames = null;
        Map<String, String> hrmNames = null;
        Map<String, String> formNames = null;

        for (TicklerDocs ticklerDoc : ticklerDocsDao.findByTicklerId(tickler.getId())) {
            DocumentType documentType = DocumentType.fromType(ticklerDoc.getDocType());
            if (documentType == null) {
                continue;
            }
            String documentId = String.valueOf(ticklerDoc.getDocumentNo());
            boolean viewable = readable.computeIfAbsent(documentType,
                    type -> isTypeReadable(loggedInInfo, type, demographicNo));
            if (!viewable) {
                attachments.add(new TicklerAttachmentData(documentType, documentId, ticklerDoc.getLabType(), null, false));
                continue;
            }

            String displayName = null;
            switch (documentType) {
                case DOC:
                    Document document = documentDao.getDocument(documentId);
                    displayName = document == null ? null : document.getDocdesc();
                    break;
                case LAB:
                    if (labNames == null) {
                        labNames = labNamesBySegmentId(loggedInInfo, demographicNo);
                    }
                    displayName = labNames.get(documentId);
                    break;
                case EFORM:
                    EFormData eForm = eFormDataDao.find(ticklerDoc.getDocumentNo());
                    displayName = eForm == null ? null : eForm.getFormName();
                    break;
                case HRM:
                    if (hrmNames == null) {
                        hrmNames = hrmNamesById(loggedInInfo, demographicNo);
                    }
                    displayName = hrmNames.get(documentId);
                    break;
                case FORM:
                    if (formNames == null) {
                        formNames = formNamesByFormId(loggedInInfo, demographicNo);
                    }
                    displayName = formNames.get(documentId);
                    break;
                default:
                    break;
            }
            if (displayName == null || displayName.trim().isEmpty()) {
                displayName = documentType.getName() + " #" + documentId;
            }
            attachments.add(new TicklerAttachmentData(documentType, documentId, ticklerDoc.getLabType(), displayName, true));
        }
        return attachments;
    }

    /**
     * Resolves encounter form names for one patient, keyed by form id.
     *
     * <p>Attachment rows store only {@code (document_no, doctype)}; encounter forms live in many
     * tables with independent id sequences, so an id claimed by two form types cannot be linked
     * safely and is dropped rather than resolved to either. Used by the tickler lists to build
     * form links, which need the form name.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session
     * @param demographicNo Integer the patient
     * @return Map&lt;String, String&gt; form id to form name; empty when the caller lacks
     *         {@code _form} read on the patient
     */
    public Map<String, String> formNamesByFormId(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (demographicNo == null || !isTypeReadable(loggedInInfo, DocumentType.FORM, demographicNo)) {
            return Collections.emptyMap();
        }
        // Every version, not only the latest: an attached form stops being the patient's newest of
        // its type as soon as another is created, and it still has to resolve.
        List<EctFormData.PatientForm> forms =
                formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, true, false);
        Map<String, String> formNames = new HashMap<>();
        Set<String> ambiguous = new HashSet<>();
        if (forms != null) {
            for (EctFormData.PatientForm form : forms) {
                if (form == null || form.getFormId() == null) {
                    continue;
                }
                if (formNames.put(form.getFormId(), form.getFormName()) != null) {
                    ambiguous.add(form.getFormId());
                }
            }
        }
        formNames.keySet().removeAll(ambiguous);
        return formNames;
    }

    private boolean isTypeReadable(LoggedInInfo loggedInInfo, DocumentType documentType, Integer demographicNo) {
        return securityInfoManager.hasPrivilege(loggedInInfo, readSecurityObject(documentType),
                SecurityInfoManager.READ, String.valueOf(demographicNo));
    }

    private void requireTypeReadable(LoggedInInfo loggedInInfo, DocumentType documentType, Integer demographicNo) {
        if (!isTypeReadable(loggedInInfo, documentType, demographicNo)) {
            throw new SecurityException("missing required sec object (" + readSecurityObject(documentType) + ")");
        }
    }

    /**
     * Verifies the item belongs to the patient and returns the lab source for labs.
     *
     * @return String the lab type for {@link DocumentType#LAB}, otherwise {@code null}
     * @throws SecurityException when the item does not exist or belongs to another patient
     */
    private String requireBelongsToPatient(LoggedInInfo loggedInInfo, DocumentType documentType,
                                           Integer documentNo, Integer demographicNo) {
        boolean owned;
        String labType = null;
        switch (documentType) {
            case DOC:
                owned = documentBelongsToPatient(documentNo, demographicNo);
                break;
            case LAB:
                PatientLabRouting routing = patientLabRoutingDao.findDemographicByLabId(documentNo);
                owned = routing != null && demographicNo.equals(routing.getDemographicNo());
                labType = owned && routing.getLabType() != null ? routing.getLabType() : LabResultData.HL7TEXT;
                break;
            case EFORM:
                EFormData eForm = eFormDataDao.find(documentNo.intValue());
                owned = eForm != null && demographicNo.equals(eForm.getDemographicId());
                break;
            case HRM:
                owned = hrmBelongsToPatient(documentNo, demographicNo);
                break;
            case FORM:
                owned = formBelongsToPatient(loggedInInfo, documentNo, demographicNo);
                break;
            default:
                owned = false;
                break;
        }
        if (!owned) {
            // The identifiers are PHI-correlating; the message names only the type.
            logger.warn("Rejected tickler attachment: {} item is not the tickler's patient's", documentType.getName());
            throw new SecurityException(documentType.getName() + " attachment does not belong to the patient");
        }
        return labType;
    }

    private boolean documentBelongsToPatient(Integer documentNo, Integer demographicNo) {
        List<Object[]> rows = documentDao.findCtlDocsAndDocsByDocNo(documentNo);
        if (rows == null) {
            return false;
        }
        for (Object[] row : rows) {
            if (row.length < 2 || !(row[1] instanceof CtlDocument)) {
                continue;
            }
            CtlDocument ctlDocument = (CtlDocument) row[1];
            if (ctlDocument.getId() != null
                    && "demographic".equals(ctlDocument.getId().getModule())
                    && demographicNo.equals(ctlDocument.getId().getModuleId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hrmBelongsToPatient(Integer hrmDocumentId, Integer demographicNo) {
        List<HRMDocumentToDemographic> links = hrmDocumentToDemographicDao.findByHrmDocumentId(hrmDocumentId);
        if (links == null) {
            return false;
        }
        String wanted = String.valueOf(demographicNo);
        for (HRMDocumentToDemographic link : links) {
            if (link != null && wanted.equals(String.valueOf(link.getDemographicNo()))) {
                return true;
            }
        }
        return false;
    }

    private boolean formBelongsToPatient(LoggedInInfo loggedInInfo, Integer formId, Integer demographicNo) {
        List<EctFormData.PatientForm> forms =
                formsManager.getEncounterFormsbyDemographicNumber(loggedInInfo, demographicNo, true, false);
        if (forms == null) {
            return false;
        }
        String wanted = String.valueOf(formId);
        for (EctFormData.PatientForm form : forms) {
            if (form != null && wanted.equals(form.getFormId()) && demographicNo.equals(form.demographicId)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, String> labNamesBySegmentId(LoggedInInfo loggedInInfo, Integer demographicNo) {
        Map<String, String> labNames = new HashMap<>();
        for (AttachmentLabResultData lab : documentAttachmentManager.getAllLabsSortedByVersions(loggedInInfo,
                String.valueOf(demographicNo))) {
            labNames.put(lab.getSegmentID(), lab.getLabName());
            // Older versions are labelled "vN <name>" to match the picker's own labels.
            int totalVersions = lab.getLabVersionIds().size();
            int index = 0;
            for (String versionSegmentId : lab.getLabVersionIds().keySet()) {
                labNames.put(versionSegmentId, "v" + (totalVersions - index) + " " + lab.getLabName());
                index++;
            }
        }
        return labNames;
    }

    private Map<String, String> hrmNamesById(LoggedInInfo loggedInInfo, Integer demographicNo) {
        Map<String, String> names = new HashMap<>();
        List<HashMap<String, ? extends Object>> hrmDocuments =
                HRMUtil.listHRMDocuments(loggedInInfo, "report_date", false, String.valueOf(demographicNo), false);
        if (hrmDocuments != null) {
            for (HashMap<String, ? extends Object> hrmDocument : hrmDocuments) {
                names.put(String.valueOf(hrmDocument.get("id")), String.valueOf(hrmDocument.get("name")));
            }
        }
        return names;
    }

    private static Set<Integer> parseIds(Collection<String> values) {
        Set<Integer> ids = new LinkedHashSet<>();
        if (values == null) {
            return ids;
        }
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            try {
                ids.add(Integer.valueOf(value.trim()));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("attachment id is not numeric", e);
            }
        }
        return ids;
    }

    private static void audit(LoggedInInfo loggedInInfo, String action, Tickler tickler,
                              DocumentType documentType, Integer documentNo) {
        // Identifiers only: no titles, names or clinical content reach the audit log.
        LogAction.addLogSynchronous(loggedInInfo, "TicklerAttachmentService." + action,
                "ticklerId=" + tickler.getId() + ",type=" + documentType.getType() + ",documentNo=" + documentNo);
    }
}
