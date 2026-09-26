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
import java.util.Date;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
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
import io.github.carlos_emr.carlos.documentManager.data.TicklerAttachmentParameters;
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
    private final TicklerDao ticklerDao;
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
                                    DocumentAttachmentManager documentAttachmentManager,
                                    TicklerDao ticklerDao) {
        this.ticklerDocsDao = ticklerDocsDao;
        this.securityInfoManager = securityInfoManager;
        this.documentDao = documentDao;
        this.patientLabRoutingDao = patientLabRoutingDao;
        this.eFormDataDao = eFormDataDao;
        this.hrmDocumentToDemographicDao = hrmDocumentToDemographicDao;
        this.formsManager = formsManager;
        this.documentAttachmentManager = documentAttachmentManager;
        this.ticklerDao = ticklerDao;
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
     * @param submitted Map&lt;DocumentType, ? extends Collection&lt;String&gt;&gt; the desired ids per type;
     *        lab ids carry their source ({@code HL7:123}, see
     *        {@link TicklerAttachmentParameters#labValue}), a bare lab id means HL7
     * <p>Runs inside its own transaction with the tickler row locked, so concurrent syncs of one
     * tickler are serialised and never insert the same attachment twice; a re-attached item
     * revives its detached row rather than adding another.</p>
     *
     * @throws SecurityException when the caller lacks {@code _tickler} write on the patient,
     *         changes a type the caller cannot read, or submits an id that is not the patient's
     * @throws IllegalArgumentException when an id is not numeric or a lab value is malformed
     */
    @Transactional
    public void syncAttachments(LoggedInInfo loggedInInfo, Tickler tickler,
                                Map<DocumentType, ? extends Collection<String>> submitted) {
        Integer demographicNo = tickler.getDemographicNo();
        requireTicklerWrite(loggedInInfo, demographicNo);
        if (submitted == null || submitted.isEmpty()) {
            return;
        }

        String providerNo = loggedInInfo.getLoggedInProviderNo();
        // Two edits of the same tickler must not both see "not attached yet" and both insert:
        // the parent row is locked first, so a concurrent sync queues behind this transaction,
        // and the attachment rows are then read with a locking read, which returns what the
        // earlier sync committed rather than this transaction's snapshot. Detached rows are
        // loaded too, so re-attaching an item revives its row instead of adding a second one.
        ticklerDao.lockForAttachmentSync(tickler.getId());
        List<TicklerDocs> allRows = ticklerDocsDao.findAllByTicklerIdForUpdate(tickler.getId());
        for (Map.Entry<DocumentType, ? extends Collection<String>> entry : submitted.entrySet()) {
            DocumentType documentType = entry.getKey();
            Set<AttachmentRef> wanted = parseRefs(documentType, entry.getValue());
            Map<AttachmentRef, TicklerDocs> existing = new HashMap<>();
            Map<AttachmentRef, TicklerDocs> detached = new HashMap<>();
            for (TicklerDocs storedDoc : allRows) {
                if (!documentType.getType().equals(storedDoc.getDocType())) {
                    continue;
                }
                AttachmentRef ref = AttachmentRef.of(documentType, storedDoc);
                if (storedDoc.getDeleted() == null) {
                    existing.put(ref, storedDoc);
                } else {
                    // Rows are ordered by id, so the newest detached row wins.
                    detached.put(ref, storedDoc);
                }
            }
            // A caller who cannot read a type never sees its items in the picker: the form
            // carries the stored rows through as restricted delegates, so a submission that
            // equals the stored set is "nothing shown", not a change, and the rows are left
            // alone. Any difference would add or drop items the caller may not see.
            if (!isTypeReadable(loggedInInfo, documentType, demographicNo)) {
                if (wanted.equals(existing.keySet())) {
                    continue;
                }
                requireTypeReadable(loggedInInfo, documentType, demographicNo);
            }
            // Ownership checks come before any write, so a rejected submission leaves the
            // stored set untouched rather than half-synchronised. A new item that is not the
            // patient's is refused; a live item is re-verified too, since a document can be
            // re-filed and an HRM report re-assigned after it was attached, and one that has
            // moved is detached (audited) rather than kept on the patient's tickler.
            Set<AttachmentRef> stale = new HashSet<>();
            for (AttachmentRef ref : wanted) {
                if (!existing.containsKey(ref)) {
                    requireBelongsToPatient(loggedInInfo, documentType, ref, demographicNo);
                } else if (!belongsToPatient(loggedInInfo, documentType, ref, demographicNo)) {
                    logger.warn("Detaching tickler attachment: {} item is no longer the tickler's patient's", documentType.getName());
                    stale.add(ref);
                }
            }

            for (Map.Entry<AttachmentRef, TicklerDocs> storedEntry : existing.entrySet()) {
                if (!wanted.contains(storedEntry.getKey()) || stale.contains(storedEntry.getKey())) {
                    TicklerDocs storedDoc = storedEntry.getValue();
                    storedDoc.setDeleted(TicklerDocs.DELETED_FLAG);
                    ticklerDocsDao.merge(storedDoc);
                    audit(loggedInInfo, LogConst.DELETE, tickler, documentType, storedDoc.getDocumentNo());
                }
            }
            for (AttachmentRef ref : wanted) {
                if (existing.containsKey(ref)) {
                    continue;
                }
                TicklerDocs revived = detached.get(ref);
                if (revived != null) {
                    // One row per (tickler, item, source): a re-attached item takes its old row
                    // back, stamped with the provider and date of this attachment.
                    revived.setDeleted(null);
                    revived.setProviderNo(providerNo);
                    revived.setAttachDate(new Date());
                    ticklerDocsDao.merge(revived);
                } else {
                    TicklerDocs ticklerDocs = new TicklerDocs(tickler.getId(), ref.documentNo(), documentType.getType(), providerNo);
                    ticklerDocs.setLabType(ref.labType());
                    ticklerDocsDao.persist(ticklerDocs);
                }
                audit(loggedInInfo, LogConst.ADD, tickler, documentType, ref.documentNo());
            }
        }
    }

    /**
     * Verifies, without writing anything, that the caller may attach the submitted items to a
     * tickler for the patient: {@code _tickler} write on the patient, read on every submitted
     * type, and every id the patient's own. A flow that creates the tickler and attaches in one
     * step (the lab macro) checks this first, so a refused attachment never leaves an empty
     * tickler behind.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session
     * @param demographicNo Integer the patient the tickler is for
     * @param submitted Map&lt;DocumentType, ? extends Collection&lt;String&gt;&gt; the ids per type, as for
     *        {@link #syncAttachments}
     * @throws SecurityException when the caller lacks a right or an id is not the patient's
     * @throws IllegalArgumentException when an id is not numeric or a lab value is malformed
     */
    public void requireAttachable(LoggedInInfo loggedInInfo, Integer demographicNo,
                                  Map<DocumentType, ? extends Collection<String>> submitted) {
        requireTicklerWrite(loggedInInfo, demographicNo);
        if (submitted == null) {
            return;
        }
        for (Map.Entry<DocumentType, ? extends Collection<String>> entry : submitted.entrySet()) {
            DocumentType documentType = entry.getKey();
            Set<AttachmentRef> wanted = parseRefs(documentType, entry.getValue());
            if (wanted.isEmpty()) {
                continue;
            }
            requireTypeReadable(loggedInInfo, documentType, demographicNo);
            for (AttachmentRef ref : wanted) {
                requireBelongsToPatient(loggedInInfo, documentType, ref, demographicNo);
            }
        }
    }

    private void requireTicklerWrite(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, TICKLER_SECURITY_OBJECT, SecurityInfoManager.WRITE,
                String.valueOf(demographicNo))) {
            throw new SecurityException(MISSING_TICKLER_SECURITY_OBJECT);
        }
    }

    /**
     * Lists the tickler's attachments with display names, for the Add/Edit windows.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session
     * @param tickler Tickler the tickler being shown
     * @return List&lt;TicklerAttachmentData&gt; every live attachment whose item is still the
     *         patient's, oldest first; items of a type the caller may not read are returned unnamed
     * @throws SecurityException when the caller lacks {@code _tickler} read on the patient
     */
    public List<TicklerAttachmentData> listAttachments(LoggedInInfo loggedInInfo, Tickler tickler) {
        Integer demographicNo = tickler.getDemographicNo();
        requireTicklerRead(loggedInInfo, demographicNo);
        AttachmentNameResolver resolver = new AttachmentNameResolver(loggedInInfo, demographicNo);
        List<TicklerAttachmentData> attachments = new ArrayList<>();
        for (TicklerDocs ticklerDoc : ticklerDocsDao.findByTicklerId(tickler.getId())) {
            TicklerAttachmentData attachment = resolver.resolve(ticklerDoc);
            if (attachment != null) {
                attachments.add(attachment);
            }
        }
        return attachments;
    }

    /**
     * Lists the attachments of a page of ticklers in one query, for the tickler views.
     *
     * <p>Same result per tickler as {@link #listAttachments(LoggedInInfo, Tickler)}, but the
     * attachment rows are fetched once for the page and the per-patient lab, HRM and form name
     * collections are loaded at most once per patient rather than once per tickler.</p>
     *
     * @param loggedInInfo LoggedInInfo the authenticated session
     * @param ticklers List&lt;Tickler&gt; the ticklers being shown; null or empty returns empty
     * @return Map&lt;Integer, List&lt;TicklerAttachmentData&gt;&gt; live attachments keyed by tickler
     *         id, oldest first, with an entry (possibly empty) for every tickler given
     * @throws SecurityException when the caller lacks {@code _tickler} read on any of the patients
     */
    public Map<Integer, List<TicklerAttachmentData>> listAttachments(LoggedInInfo loggedInInfo, List<Tickler> ticklers) {
        Map<Integer, List<TicklerAttachmentData>> byTickler = new LinkedHashMap<>();
        if (ticklers == null || ticklers.isEmpty()) {
            return byTickler;
        }
        Map<Integer, AttachmentNameResolver> resolverByDemographic = new HashMap<>();
        Map<Integer, AttachmentNameResolver> resolverByTickler = new HashMap<>();
        for (Tickler tickler : ticklers) {
            Integer demographicNo = tickler.getDemographicNo();
            AttachmentNameResolver resolver = resolverByDemographic.get(demographicNo);
            if (resolver == null) {
                requireTicklerRead(loggedInInfo, demographicNo);
                resolver = new AttachmentNameResolver(loggedInInfo, demographicNo);
                resolverByDemographic.put(demographicNo, resolver);
            }
            resolverByTickler.put(tickler.getId(), resolver);
            byTickler.put(tickler.getId(), new ArrayList<>());
        }
        for (TicklerDocs ticklerDoc : ticklerDocsDao.findByTicklerIds(new ArrayList<>(byTickler.keySet()))) {
            List<TicklerAttachmentData> attachments = byTickler.get(ticklerDoc.getTicklerId());
            AttachmentNameResolver resolver = resolverByTickler.get(ticklerDoc.getTicklerId());
            if (attachments == null || resolver == null) {
                continue;
            }
            TicklerAttachmentData attachment = resolver.resolve(ticklerDoc);
            if (attachment != null) {
                attachments.add(attachment);
            }
        }
        return byTickler;
    }

    private void requireTicklerRead(LoggedInInfo loggedInInfo, Integer demographicNo) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, TICKLER_SECURITY_OBJECT, SecurityInfoManager.READ,
                String.valueOf(demographicNo))) {
            throw new SecurityException(MISSING_TICKLER_SECURITY_OBJECT);
        }
    }

    /**
     * Turns stored rows of one patient into display entries, loading each per-patient name
     * collection (labs, HRM reports, encounter forms) and each type's read decision at most once
     * for the resolver's lifetime.
     */
    private final class AttachmentNameResolver {
        private final LoggedInInfo loggedInInfo;
        private final Integer demographicNo;
        private final Map<DocumentType, Boolean> readable = new EnumMap<>(DocumentType.class);
        private Map<String, String> labNames;
        private Map<String, String> hrmNames;
        private Map<String, String> formNames;

        private AttachmentNameResolver(LoggedInInfo loggedInInfo, Integer demographicNo) {
            this.loggedInInfo = loggedInInfo;
            this.demographicNo = demographicNo;
        }

        /**
         * @return TicklerAttachmentData the entry, unnamed when the caller may not read its type;
         *         {@code null} for a row whose type code is unknown
         */
        private TicklerAttachmentData resolve(TicklerDocs ticklerDoc) {
            DocumentType documentType = DocumentType.fromType(ticklerDoc.getDocType());
            if (documentType == null) {
                return null;
            }
            String documentId = String.valueOf(ticklerDoc.getDocumentNo());
            // A row is only shown while its item is still the patient's: a document re-filed or
            // an HRM report re-assigned since it was attached is left out, whatever the caller's
            // rights, so a tickler never surfaces another patient's item.
            if (!belongsToPatient(loggedInInfo, documentType, ticklerDoc.getDocumentNo(), ticklerDoc.getLabType(), demographicNo)) {
                logger.warn("Omitting tickler attachment: {} item is no longer the tickler's patient's", documentType.getName());
                return null;
            }
            boolean viewable = readable.computeIfAbsent(documentType,
                    type -> isTypeReadable(loggedInInfo, type, demographicNo));
            if (!viewable) {
                return new TicklerAttachmentData(documentType, documentId, ticklerDoc.getLabType(), null, false);
            }

            String displayName = null;
            switch (documentType) {
                case DOC:
                    Document document = documentDao.getDocument(documentId);
                    displayName = document == null ? null : document.getDocdesc();
                    break;
                case LAB:
                    if (labNames == null) {
                        labNames = labNamesBySourceAndSegmentId(loggedInInfo, demographicNo);
                    }
                    displayName = labNames.get(TicklerAttachmentParameters.labValue(ticklerDoc.getLabType(), documentId));
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
            return new TicklerAttachmentData(documentType, documentId, ticklerDoc.getLabType(), displayName, true);
        }
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
     * Verifies the item belongs to the patient. Labs are checked against the routing row of
     * their own source: segment ids are only unique per source, so an HL7 lookup for an MDS
     * id would either miss or hit an unrelated HL7 lab.
     *
     * @throws SecurityException when the item does not exist or belongs to another patient
     */
    private void requireBelongsToPatient(LoggedInInfo loggedInInfo, DocumentType documentType,
                                         AttachmentRef ref, Integer demographicNo) {
        if (!belongsToPatient(loggedInInfo, documentType, ref, demographicNo)) {
            // The identifiers are PHI-correlating; the message names only the type.
            logger.warn("Rejected tickler attachment: {} item is not the tickler's patient's", documentType.getName());
            throw new SecurityException(documentType.getName() + " attachment does not belong to the patient");
        }
    }

    /**
     * Whether a stored or submitted item currently belongs to the patient, looked up afresh:
     * a document can be re-filed and an HRM report re-assigned after it was attached, and a
     * row that was valid when written must not surface another patient's item later. Readers
     * that resolve {@code ticklerdocs} rows call this before exposing a row.
     *
     * @param loggedInInfo LoggedInInfo the authenticated session (encounter form lookups need it)
     * @param documentType DocumentType the attachment type
     * @param documentNo int the item id
     * @param labType String the lab source for labs (blank reads as HL7), ignored otherwise
     * @param demographicNo Integer the tickler's patient
     * @return boolean true when the item is the patient's now
     */
    public boolean belongsToPatient(LoggedInInfo loggedInInfo, DocumentType documentType, int documentNo,
                                    String labType, Integer demographicNo) {
        String source = documentType == DocumentType.LAB
                ? (labType == null || labType.trim().isEmpty() ? LabResultData.HL7TEXT : labType.trim())
                : null;
        return belongsToPatient(loggedInInfo, documentType, new AttachmentRef(documentNo, source), demographicNo);
    }

    private boolean belongsToPatient(LoggedInInfo loggedInInfo, DocumentType documentType,
                                     AttachmentRef ref, Integer demographicNo) {
        Integer documentNo = ref.documentNo();
        if (demographicNo == null) {
            return false;
        }
        boolean owned;
        switch (documentType) {
            case DOC:
                owned = documentBelongsToPatient(documentNo, demographicNo);
                break;
            case LAB:
                PatientLabRouting routing = patientLabRoutingDao.findDemographics(ref.labType(), documentNo);
                owned = routing != null && demographicNo.equals(routing.getDemographicNo());
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
        return owned;
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

    /** Lab names keyed by {@link TicklerAttachmentParameters#labValue}; sources number their own ids. */
    private Map<String, String> labNamesBySourceAndSegmentId(LoggedInInfo loggedInInfo, Integer demographicNo) {
        Map<String, String> labNames = new HashMap<>();
        for (AttachmentLabResultData lab : documentAttachmentManager.getAllLabsSortedByVersions(loggedInInfo,
                String.valueOf(demographicNo))) {
            labNames.put(TicklerAttachmentParameters.labValue(lab.getLabType(), lab.getSegmentID()), lab.getLabName());
            // Older versions are labelled "vN <name>" to match the picker's own labels.
            int totalVersions = lab.getLabVersionIds().size();
            int index = 0;
            for (String versionSegmentId : lab.getLabVersionIds().keySet()) {
                labNames.put(TicklerAttachmentParameters.labValue(lab.getLabType(), versionSegmentId),
                        "v" + (totalVersions - index) + " " + lab.getLabName());
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

    private static Set<AttachmentRef> parseRefs(DocumentType documentType, Collection<String> values) {
        Set<AttachmentRef> refs = new LinkedHashSet<>();
        if (values == null) {
            return refs;
        }
        for (String value : values) {
            if (value == null || value.trim().isEmpty()) {
                continue;
            }
            String labType = null;
            String id = value.trim();
            if (documentType == DocumentType.LAB) {
                String[] parts = TicklerAttachmentParameters.parseLabValue(id);
                labType = parts[0];
                id = parts[1];
            }
            try {
                refs.add(new AttachmentRef(Integer.valueOf(id), labType));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("attachment id is not numeric", e);
            }
        }
        return refs;
    }

    /**
     * Identity of one attachment within a type: the id, plus the lab source for labs. Lab
     * rows persisted before the source was recorded compare as HL7, the only source the
     * legacy links ever named.
     */
    private record AttachmentRef(Integer documentNo, String labType) {

        static AttachmentRef of(DocumentType documentType, TicklerDocs stored) {
            if (documentType != DocumentType.LAB) {
                return new AttachmentRef(stored.getDocumentNo(), null);
            }
            String labType = stored.getLabType();
            return new AttachmentRef(stored.getDocumentNo(),
                    labType == null || labType.trim().isEmpty() ? LabResultData.HL7TEXT : labType.trim());
        }
    }

    private static void audit(LoggedInInfo loggedInInfo, String action, Tickler tickler,
                              DocumentType documentType, Integer documentNo) {
        // Identifiers only: no titles, names or clinical content reach the audit log.
        LogAction.addLogSynchronous(loggedInInfo, "TicklerAttachmentService." + action,
                "ticklerId=" + tickler.getId() + ",type=" + documentType.getType() + ",documentNo=" + documentNo);
    }
}
