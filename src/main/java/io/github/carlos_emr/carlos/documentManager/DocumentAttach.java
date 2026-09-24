package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.OceanEReferralAttachmentUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.logging.log4j.Logger;

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

    static final String ATTACHMENT_NOT_OWNED = "attachment does not belong to the consultation patient";

    public DocumentAttach() {
    }

    /**
     * Verifies consult attachment ids against {@link #demographicNo}; {@code null} only on the
     * eForm-attachment path, which never calls {@link #attachToConsult}.
     */
    private AttachmentOwnershipService attachmentOwnershipService;

    public DocumentAttach(Integer demographicNo, Boolean editOnOcean) {
        this.demographicNo = demographicNo;
        this.editOnOcean = editOnOcean;
    }

    /**
     * Consultation attach/detach bound to one patient, with attachment ownership enforced.
     *
     * @param demographicNo patient the consultation request belongs to
     * @param editOnOcean whether new attachments are also queued for Ocean eReferral
     * @param attachmentOwnershipService verifies that newly attached ids belong to {@code demographicNo}
     */
    public DocumentAttach(Integer demographicNo, Boolean editOnOcean, AttachmentOwnershipService attachmentOwnershipService) {
        this(demographicNo, editOnOcean);
        this.attachmentOwnershipService = attachmentOwnershipService;
    }

    /**
     * Replaces the consultation's attachments of one type with {@code attachments}.
     *
     * <p>Every DOC/LAB/EFORM/HRM id in {@code attachments} is checked against the consultation's
     * patient (issue #3867), because the renderers that print, fax and send consultation
     * attachments to Ocean resolve each id on its own and a foreign id would disclose another
     * patient's record:</p>
     * <ul>
     *   <li>An id that is <em>not</em> already attached must belong to the patient; otherwise the
     *       whole call is rejected before any detach or attach, so the consultation is unchanged.</li>
     *   <li>An id that <em>is</em> already attached but no longer verifies (a legacy foreign row
     *       written before this check existed, a document deleted since, a lab re-matched to
     *       another patient) is detached instead of kept. Rejecting it would make every later save
     *       of that consultation fail, which the user cannot fix from the form; keeping it would
     *       leave the disclosure in place. Only the count is logged.</li>
     * </ul>
     * <p>{@link DocumentType#FORM} ids have no common owner column and are not checked here.</p>
     *
     * @throws SecurityException if a newly attached id is unknown, malformed or belongs to another
     *                           patient, or if no ownership verifier is configured
     */
    public void attachToConsult(String[] attachments, DocumentType documentType, String providerNo, Integer requestId) {
        List<String> oldList = findConsultAttachmentIds(documentType, requestId);
        List<String> currentList = retainVerifiedAttachments(toList(attachments), oldList, documentType);
        detachFromConsult(currentList, oldList, documentType, requestId);
        attachToConsult(currentList, oldList, documentType, providerNo, requestId);
    }

    /**
     * Runs the ownership check of {@link #attachToConsult(String[], DocumentType, String, Integer)}
     * without writing anything, so a caller that saves several attachment types (and the
     * consultation itself) can reject a bad request before its first write.
     *
     * @param attachments submitted ids of one type; {@code null} is treated as empty
     * @param documentType attachment type
     * @param requestId the consultation being edited, or {@code null} for a consultation not yet
     *                  saved (every id is then new)
     * @throws SecurityException if a newly attached id is unknown, malformed or belongs to another
     *                           patient, or if no ownership verifier is configured
     */
    public void verifyConsultAttachments(String[] attachments, DocumentType documentType, Integer requestId) {
        List<String> oldList = requestId == null ? new ArrayList<>() : findConsultAttachmentIds(documentType, requestId);
        retainVerifiedAttachments(toList(attachments), oldList, documentType);
    }

    private List<String> findConsultAttachmentIds(DocumentType documentType, Integer requestId) {
        List<String> oldList = new ArrayList<>();
        for (ConsultDocs consultDoc : consultDocsDao.findByRequestIdDocType(requestId, documentType.getType())) {
            oldList.add(Integer.toString(consultDoc.getDocumentNo()));
        }
        return oldList;
    }

    private static List<String> toList(String[] attachments) {
        return attachments == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(attachments));
    }

    /**
     * Returns the submitted ids to keep: every owned id, minus already-attached ids that no longer
     * verify. Throws before any write if a new id is malformed or not owned.
     */
    private List<String> retainVerifiedAttachments(List<String> currentList, List<String> oldList, DocumentType documentType) {
        if (!AttachmentOwnershipService.isVerifiable(documentType) || currentList.isEmpty()) {
            return currentList;
        }
        Set<Integer> ids = new HashSet<>();
        for (String docId : currentList) {
            ids.add(parseAttachmentId(docId));
        }
        // Fail closed: a caller that did not wire a verifier must not attach unverified ids.
        if (attachmentOwnershipService == null) {
            throw new SecurityException(ATTACHMENT_NOT_OWNED);
        }
        Set<Integer> owned = attachmentOwnershipService.findOwnedIds(documentType, demographicNo, ids);

        List<String> retained = new ArrayList<>();
        int dropped = 0;
        for (String docId : currentList) {
            if (owned.contains(parseAttachmentId(docId))) {
                retained.add(docId);
            } else if (oldList.contains(docId)) {
                dropped++;
            } else {
                throw new SecurityException(ATTACHMENT_NOT_OWNED);
            }
        }
        if (dropped > 0) {
            // Count only: attachment ids and the patient are PHI-correlating identifiers.
            logger.warn("Detached {} existing consultation attachment(s) that no longer verify for the consultation patient", dropped);
        }
        return retained;
    }

    private static Integer parseAttachmentId(String docId) {
        try {
            return Integer.valueOf(docId);
        } catch (NumberFormatException e) {
            throw new SecurityException(ATTACHMENT_NOT_OWNED);
        }
    }

    private void attachToConsult(List<String> currentList, List<String> oldList, DocumentType documentType, String providerNo, Integer requestId) {
        for (String docId : currentList) {
            if (oldList.contains(docId)) {
                continue;
            }
            ConsultDocs consultDoc = new ConsultDocs(requestId, Integer.parseInt(docId), documentType.getType(), providerNo);
            consultDocsDao.persist(consultDoc);

            if (editOnOcean) {
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

            if (editOnOcean) {
                OceanEReferralAttachmentUtil.detachOceanEReferralConsult(docId, documentType.getType());
            }
        }
    }

    public void attachToEForm(String[] attachments, DocumentType documentType, String providerNo, Integer fdid) {
        List<String> currentList = new ArrayList<>(Arrays.asList(attachments));
        List<EFormDocs> eFormDocsList = eFormDocsDao.findByFdidIdDocType(fdid, documentType.getType());
        List<String> oldList = new ArrayList<>();
        for (EFormDocs eFormDoc : eFormDocsList) {
            oldList.add(Integer.toString(eFormDoc.getDocumentNo()));
        }
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
}
