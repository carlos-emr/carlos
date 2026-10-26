package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.OceanEReferralAttachmentUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Supplier;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDataDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.documentManager.data.LabAttachmentReference;
import org.apache.logging.log4j.Logger;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class DocumentAttach {
    private static final Logger logger = MiscUtils.getLogger();
    private final ConsultDocsDao consultDocsDao = SpringUtils.getBean(ConsultDocsDao.class);
    private final EFormDocsDao eFormDocsDao = SpringUtils.getBean(EFormDocsDao.class);

    /*
     * When editOnOcean is set to false, it signifies a normal consult request, performing just attach or detach operations on the consult request form.
     * When editOnOcean is set to true, it signifies that the attach or detach operation is being performed on a consult request created by OceanMD.
     * In this case, it will do two things:
     * 1. Attach or detach attachments from the consult request.
     * 2. Add those new attachments to the 'EreferAttachment' table, so Oscar can sent those attachment to OceanMD.
     * By doing this, the user will not have to manually upload new attachments to e-refer. They will be automatically fetched.
     */
    private Boolean editOnOcean = false;

    private Integer demographicNo;
    private io.github.carlos_emr.carlos.utility.LoggedInInfo loggedInInfo;

    public DocumentAttach() {
    }

    public DocumentAttach(Integer demographicNo, Boolean editOnOcean) {
        this.demographicNo = demographicNo;
        this.editOnOcean = editOnOcean;
    }

    public DocumentAttach(io.github.carlos_emr.carlos.utility.LoggedInInfo loggedInInfo,
                          Integer demographicNo, Boolean editOnOcean) {
        this(demographicNo, editOnOcean);
        this.loggedInInfo = loggedInInfo;
    }

    private boolean validateSelection(DocumentType type, int patient, Collection<String> selected,
                                      Collection<String> existing) {
        if (loggedInInfo == null) {
            // The deprecated lab helpers validate their caller separately; source/owner validation
            // below still applies. No non-lab caller may bypass the access policy.
            if (type != DocumentType.LAB) throw new SecurityException("Attachment session is required");
            return true;
        }
        return SpringUtils.getBean(AttachmentSelectionAccess.class)
                .validate(loggedInInfo, type, patient, selected, existing);
    }

    public void attachToConsult(String[] attachments, DocumentType documentType, String providerNo, Integer requestId) {
        attachToConsult(attachments, documentType, providerNo, requestId, false);
    }

    /**
     * Replaces the consultation's attachments of one type with {@code attachments}.
     *
     * @param keepUnavailable true to keep attached, unvalidated, the documents, eForms and HRM reports already on
     *                        this consultation whose target is no longer available (see
     *                        {@link #unavailableAttached}); the consult form cannot list them, so
     *                        a save that leaves them out does not mean to detach them
     */
    public void attachToConsult(String[] attachments, DocumentType documentType, String providerNo, Integer requestId,
                                boolean keepUnavailable) {
        attachToConsult(attachments, documentType, providerNo, requestId,
                keepUnavailable ? () -> consultDocsDao.findUnavailableActiveConsultAttachments(requestId) : null);
    }

    /**
     * Replaces the consultation's attachments of one type with {@code attachments}, keeping the
     * already-attached ones {@code unavailable} names (see {@link #unavailableAttached}).
     *
     * @param unavailable the consultation's attachments whose target is no longer available, read
     *                    under the parent lock; {@code null} keeps none. The HRM save passes a lookup
     *                    that also checks each report's file, which only the manager can do.
     */
    public void attachToConsult(String[] attachments, DocumentType documentType, String providerNo, Integer requestId,
                                Supplier<? extends Collection<ConsultDocs>> unavailable) {
        new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var owner = SpringUtils.getBean(ConsultationRequestDao.class).lockForAttachmentSync(requestId);
            if (owner == null || owner.getDemographicId() == null
                    || (demographicNo != null && !demographicNo.equals(owner.getDemographicId()))) {
                throw new IllegalArgumentException("Attachment parent does not belong to this patient");
            }
            int patient = owner.getDemographicId();

            if (documentType == DocumentType.LAB) {
                syncConsultLabs(attachments, providerNo, requestId, patient);
            } else {
                syncConsultDocuments(attachments, documentType, providerNo, requestId, patient, unavailable);
            }
        });
    }

    /**
     * Validates all selections before the consultation save starts. The writer repeats these
     * checks under the parent lock, so this preflight never replaces transactional validation.
     *
     * @param attachments selected IDs, including source-qualified lab IDs
     * @param documentType type being replaced
     * @param requestId stored consultation ID, or null for a new consultation
     * @throws SecurityException when attachment read access is denied
     * @throws IllegalArgumentException when the parent or selection is invalid
     */
    public void verifyConsultAttachments(String[] attachments, DocumentType documentType, Integer requestId) {
        if (demographicNo == null) throw new IllegalArgumentException("Consultation patient is required");
        if (requestId != null) {
            var parent = SpringUtils.getBean(ConsultationRequestDao.class).find(requestId);
            if (parent == null || !demographicNo.equals(parent.getDemographicId())) {
                throw new IllegalArgumentException("Attachment parent does not belong to this patient");
            }
        }
        List<ConsultDocs> rows = requestId == null ? List.of()
                : consultDocsDao.findByRequestIdDocType(requestId, documentType.getType());
        List<String> selected = attachments == null ? List.of() : Arrays.asList(attachments);
        if (documentType == DocumentType.LAB) {
            Set<LabAttachmentReference> existing = new LinkedHashSet<>();
            for (ConsultDocs row : rows) existing.add(LabAttachmentReference.stored(row.getLabType(), row.getDocumentNo()));
            if (validateSelection(documentType, demographicNo, selected,
                    existing.stream().map(LabAttachmentReference::key).toList())) {
                selectedLabs(selected.toArray(String[]::new), demographicNo, existing);
            }
        } else {
            List<String> existing = rows.stream().map(row -> Integer.toString(row.getDocumentNo())).toList();
            // Same exclusion as the save below, so the preflight agrees with it. A report whose file is
            // missing still belongs to the patient, so the database lookup is enough to pass the check.
            Set<String> kept = requestId == null ? Set.of()
                    : unavailableAttached(documentType, existing, consultDocsDao.findUnavailableActiveConsultAttachments(requestId));
            validateSelection(documentType, demographicNo, without(selected, kept), without(existing, kept));
        }
    }

    private void syncConsultDocuments(String[] attachments, DocumentType documentType, String providerNo, Integer requestId,
                                      int patient, Supplier<? extends Collection<ConsultDocs>> unavailable) {
        List<String> currentList = new ArrayList<>(new LinkedHashSet<>(Arrays.asList(attachments)));
        List<ConsultDocs> consultDocsList = consultDocsDao.findByRequestIdDocTypeForUpdate(requestId, documentType.getType());
        List<String> oldList = new ArrayList<>();
        for (ConsultDocs consultDoc : consultDocsList) {
            oldList.add(Integer.toString(consultDoc.getDocumentNo()));
        }
        Set<String> kept = unavailable == null ? Set.of() : unavailableAttached(documentType, oldList, unavailable.get());
        // A kept id is already attached, so it is not a selection to authorize: the selection check
        // would refuse it (its target is gone or another patient's) and roll the whole save back.
        // Every other submitted id, and every newly selected one, is still checked.
        if (!validateSelection(documentType, patient, without(currentList, kept), without(oldList, kept))) return;
        List<String> retained = new ArrayList<>(currentList);
        for (String docId : kept) {
            if (!retained.contains(docId)) {
                retained.add(docId);
                logger.info("Kept unavailable consultation attachment type={} id={} attached on save",
                        documentType.getType(), LogSafe.sanitize(docId));
            }
        }
        detachFromConsult(retained, oldList, documentType, requestId);
        attachToConsult(currentList, oldList, documentType, providerNo, requestId);
    }

    /**
     * The documents, eForms or HRM reports already attached to this consultation ({@code attached})
     * that {@code unavailable} names: deleted, filed to another patient, a patient-independent eForm,
     * missing, or an HRM report whose file is missing or unreadable (#3145, #4087). The consult form
     * cannot list them, so a save keeps them attached, and the preview, print, fax and confirmation
     * pages name them as left out. Labs and forms are never kept here: the form lists an
     * unavailable lab itself until it is selected again.
     */
    private Set<String> unavailableAttached(DocumentType documentType, Collection<String> attached,
                                            Collection<ConsultDocs> unavailable) {
        if (documentType != DocumentType.DOC && documentType != DocumentType.EFORM && documentType != DocumentType.HRM) {
            return Set.of();
        }
        Set<String> kept = new LinkedHashSet<>();
        if (unavailable == null) {
            return kept;
        }
        for (ConsultDocs row : unavailable) {
            if (row != null && documentType.getType().equals(row.getDocType())) {
                String docId = Integer.toString(row.getDocumentNo());
                if (attached.contains(docId)) {
                    kept.add(docId);
                }
            }
        }
        return kept;
    }

    private static List<String> without(Collection<String> ids, Set<String> excluded) {
        return ids.stream().filter(id -> !excluded.contains(id)).toList();
    }

    private void attachToConsult(List<String> currentList, List<String> oldList, DocumentType documentType, String providerNo, Integer requestId) {
        for (String docId : currentList) {
            if (oldList.contains(docId)) {
                continue;
            }
            ConsultDocs consultDoc = new ConsultDocs(requestId, Integer.parseInt(docId), documentType.getType(), providerNo);
            consultDocsDao.persist(consultDoc);

            if (Boolean.TRUE.equals(editOnOcean)) {
                OceanEReferralAttachmentUtil.attachOceanEReferralConsult(docId, demographicNo, documentType.getType());
            }
        }
    }

    private void detachFromConsult(List<String> currentList, List<String> oldList, DocumentType documentType, Integer requestId) {
        for (String docId : oldList) {
            if (currentList.contains(docId)) {
                continue;
            }
            List<ConsultDocs> detachList = consultDocsDao.findByRequestIdDocNoDocType(requestId, Integer.valueOf(docId), documentType.getType());
            for (ConsultDocs consultDoc : detachList) {
                consultDoc.setDeleted("Y");
                consultDocsDao.merge(consultDoc);
            }

            if (Boolean.TRUE.equals(editOnOcean)) {
                OceanEReferralAttachmentUtil.detachOceanEReferralConsult(docId, demographicNo, documentType.getType());
            }
        }
    }

    public void attachToEForm(String[] attachments, DocumentType documentType, String providerNo, Integer fdid) {
        new TransactionTemplate(SpringUtils.getBean(PlatformTransactionManager.class)).executeWithoutResult(status -> {
            var owner = SpringUtils.getBean(EFormDataDao.class).lockForAttachmentSync(fdid);
            if (owner == null || owner.getDemographicId() == null
                    || (demographicNo != null && !demographicNo.equals(owner.getDemographicId()))) {
                throw new IllegalArgumentException("Attachment parent does not belong to this patient");
            }
            int patient = owner.getDemographicId();

            if (documentType == DocumentType.LAB) {
                syncEFormLabs(attachments, providerNo, fdid, patient);
            } else {
                syncEFormDocuments(attachments, documentType, providerNo, fdid, patient);
            }
        });
    }

    private void syncEFormDocuments(String[] attachments, DocumentType documentType, String providerNo, Integer fdid, int patient) {
        List<String> currentList = new ArrayList<>(new LinkedHashSet<>(Arrays.asList(attachments)));
        List<EFormDocs> eFormDocsList = eFormDocsDao.findByFdidIdDocTypeForUpdate(fdid, documentType.getType());
        List<String> oldList = new ArrayList<>();
        for (EFormDocs eFormDoc : eFormDocsList) {
            oldList.add(Integer.toString(eFormDoc.getDocumentNo()));
        }
        if (!validateSelection(documentType, patient, currentList, oldList)) return;
        detachFromEForm(currentList, oldList, documentType, fdid);
        attachToEForm(currentList, oldList, documentType, providerNo, fdid);
    }

    private void attachToEForm(List<String> currentList, List<String> oldList, DocumentType documentType, String providerNo, Integer fdid) {
        for (String docId : currentList) {
            if (oldList.contains(docId)) {
                continue;
            }
            EFormDocs eFormDocs = new EFormDocs(fdid, Integer.parseInt(docId), documentType.getType(), providerNo);
            eFormDocsDao.persist(eFormDocs);
        }
    }

    private void detachFromEForm(List<String> currentList, List<String> oldList, DocumentType documentType, Integer fdid) {
        for (String docId : oldList) {
            if (currentList.contains(docId)) {
                continue;
            }
            List<EFormDocs> detachList = eFormDocsDao.findByFdidIdDocNoDocType(fdid, Integer.valueOf(docId), documentType.getType());
            for (EFormDocs eFormDoc : detachList) {
                eFormDoc.setDeleted("Y");
                eFormDocsDao.merge(eFormDoc);
            }
        }
    }
    private Set<LabAttachmentReference> selectedLabs(String[] values, int patient,
                                                     Set<LabAttachmentReference> existing) {
        PatientLabRoutingDao routing = SpringUtils.getBean(PatientLabRoutingDao.class);
        Set<LabAttachmentReference> selected = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && value.startsWith(LabAttachmentReference.UNRESOLVED + ":")) {
                LabAttachmentReference unresolved = LabAttachmentReference.parse(value);
                if (!existing.contains(unresolved)) {
                    throw new IllegalArgumentException("Unknown unresolved lab attachment");
                }
                selected.add(unresolved);
            } else {
                LabAttachmentReference reference = LabAttachmentReference.resolve(value, patient, routing);
                if (Boolean.TRUE.equals(editOnOcean) && !"HL7".equals(reference.source())) {
                    throw new IllegalArgumentException("Ocean lab export supports HL7 attachments only");
                }
                selected.add(reference);
            }
        }
        return selected;
    }

    private void syncConsultLabs(String[] values, String providerNo, int requestId, int patient) {
        List<ConsultDocs> rows = consultDocsDao.findByRequestIdDocTypeForUpdate(requestId, DocumentType.LAB.getType());
        Set<LabAttachmentReference> existing = new LinkedHashSet<>();
        for (ConsultDocs row : rows) existing.add(LabAttachmentReference.stored(row.getLabType(), row.getDocumentNo()));
        if (!validateSelection(DocumentType.LAB, patient, Arrays.asList(values),
                existing.stream().map(LabAttachmentReference::key).toList())) return;
        Set<LabAttachmentReference> wanted = selectedLabs(values, patient, existing);
        // Resolve and validate the complete selection before any detach, persist or Ocean write.
        for (ConsultDocs row : rows) {
            LabAttachmentReference ref = LabAttachmentReference.stored(row.getLabType(), row.getDocumentNo());
            if (!wanted.contains(ref)) {
                row.setDeleted(ConsultDocs.DELETED);
                consultDocsDao.merge(row);
                if (Boolean.TRUE.equals(editOnOcean) && "HL7".equals(ref.source())) {
                    OceanEReferralAttachmentUtil.detachOceanEReferralConsult("" + ref.id(), patient, DocumentType.LAB.getType());
                }
            }
        }
        for (LabAttachmentReference ref : wanted) {
            if (existing.contains(ref)) continue;
            ConsultDocs row = new ConsultDocs(requestId, ref.id(), DocumentType.LAB.getType(), providerNo);
            row.setLabType(ref.storageSource());
            consultDocsDao.persist(row);
            if (Boolean.TRUE.equals(editOnOcean)) {
                OceanEReferralAttachmentUtil.attachOceanEReferralConsult("" + ref.id(), patient, DocumentType.LAB.getType());
            }
        }
    }

    private void syncEFormLabs(String[] values, String providerNo, int fdid, int patient) {
        List<EFormDocs> rows = eFormDocsDao.findByFdidIdDocTypeForUpdate(fdid, DocumentType.LAB.getType());
        Set<LabAttachmentReference> existing = new LinkedHashSet<>();
        for (EFormDocs row : rows) existing.add(LabAttachmentReference.stored(row.getLabType(), row.getDocumentNo()));
        if (!validateSelection(DocumentType.LAB, patient, Arrays.asList(values),
                existing.stream().map(LabAttachmentReference::key).toList())) return;
        Set<LabAttachmentReference> wanted = selectedLabs(values, patient, existing);
        for (EFormDocs row : rows) {
            LabAttachmentReference ref = LabAttachmentReference.stored(row.getLabType(), row.getDocumentNo());
            if (!wanted.contains(ref)) {
                row.setDeleted("Y");
                eFormDocsDao.merge(row);
            }
        }
        for (LabAttachmentReference ref : wanted) {
            if (existing.contains(ref)) continue;
            EFormDocs row = new EFormDocs(fdid, ref.id(), DocumentType.LAB.getType(), providerNo);
            row.setLabType(ref.storageSource());
            eFormDocsDao.persist(row);
        }
    }

}
