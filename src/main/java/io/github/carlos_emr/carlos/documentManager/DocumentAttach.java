package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.commn.model.enumerator.DocumentType;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.OceanEReferralAttachmentUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DocumentAttach {
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
     * Verifies newly attached consult ids against {@link #demographicNo}; {@code null} only on the
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
     * <p>Ids that are not already attached must belong to the consultation's patient
     * (issue #3867): the renderers that print, fax and send consultation attachments to Ocean
     * resolve each id on its own, so a foreign id would disclose another patient's record. The
     * whole call is rejected before any detach or attach so a rejected request leaves the
     * consultation unchanged. Ids already attached are not re-checked, so re-saving a consultation
     * is not blocked by a document that was later reassigned. {@link DocumentType#FORM} ids have no
     * common owner column and are not checked here.</p>
     *
     * @throws SecurityException if a newly attached id is unknown, malformed or belongs to another
     *                           patient, or if no ownership verifier is configured
     */
    public void attachToConsult(String[] attachments, DocumentType documentType, String providerNo, Integer requestId) {
        List<String> currentList = new ArrayList<>(Arrays.asList(attachments));
        List<ConsultDocs> consultDocsList = consultDocsDao.findByRequestIdDocType(requestId, documentType.getType());
        List<String> oldList = new ArrayList<>();
        for (ConsultDocs consultDoc : consultDocsList) {
            oldList.add(Integer.toString(consultDoc.getDocumentNo()));
        }
        requireNewAttachmentsOwnedByPatient(currentList, oldList, documentType);
        detachFromConsult(currentList, oldList, documentType, requestId);
        attachToConsult(currentList, oldList, documentType, providerNo, requestId);
    }

    private void requireNewAttachmentsOwnedByPatient(List<String> currentList, List<String> oldList, DocumentType documentType) {
        if (!AttachmentOwnershipService.isVerifiable(documentType)) {
            return;
        }
        Set<Integer> newIds = new HashSet<>();
        for (String docId : currentList) {
            if (oldList.contains(docId)) {
                continue;
            }
            try {
                newIds.add(Integer.valueOf(docId));
            } catch (NumberFormatException e) {
                throw new SecurityException(ATTACHMENT_NOT_OWNED);
            }
        }
        if (newIds.isEmpty()) {
            return;
        }
        // Fail closed: a caller that did not wire a verifier must not attach unverified ids.
        if (attachmentOwnershipService == null
                || !attachmentOwnershipService.allBelongToDemographic(documentType, demographicNo, newIds)) {
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
