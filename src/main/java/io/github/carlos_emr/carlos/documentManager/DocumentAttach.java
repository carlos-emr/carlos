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
import java.util.List;

/**
 * Handles the attach/detach lifecycle for documents associated with consultation requests
 * and electronic forms (eForms) in the CARLOS EMR document management system.
 *
 * <p>This class performs differential attachment updates by comparing the current set of
 * document IDs against the previously persisted set, then attaching new entries and
 * soft-deleting removed ones. When constructed with {@code editOnOcean = true}, attachment
 * changes are also propagated to the OceanMD eReferral system via
 * {@link OceanEReferralAttachmentUtil} for automatic synchronization.
 *
 * @see DocumentAttachmentManager
 * @see ConsultDocsDao
 * @see EFormDocsDao
 * @since 2006-07-27
 */
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

    /**
     * Constructs a DocumentAttach for standard (non-OceanMD) attachment operations.
     */
    public DocumentAttach() {
    }

    /**
     * Constructs a DocumentAttach with OceanMD eReferral integration support.
     *
     * @param demographicNo Integer the patient's demographic number for OceanMD context
     * @param editOnOcean Boolean when true, attachment changes are propagated to OceanMD
     */
    public DocumentAttach(Integer demographicNo, Boolean editOnOcean) {
        this.demographicNo = demographicNo;
        this.editOnOcean = editOnOcean;
    }

    /**
     * Synchronizes the set of document attachments for a consultation request. Compares
     * the provided attachment IDs with the currently persisted set, attaches new documents,
     * and soft-deletes removed ones. If OceanMD integration is enabled, propagates changes.
     *
     * @param attachments String[] the current set of document IDs to be attached
     * @param documentType DocumentType the type of documents being attached
     * @param providerNo String the provider performing the operation
     * @param requestId Integer the consultation request identifier
     */
    public void attachToConsult(String[] attachments, DocumentType documentType, String providerNo, Integer requestId) {
        List<String> currentList = new ArrayList<>(Arrays.asList(attachments));
        List<ConsultDocs> consultDocsList = consultDocsDao.findByRequestIdDocType(requestId, documentType.getType());
        List<String> oldList = new ArrayList<>();
        for (ConsultDocs consultDoc : consultDocsList) {
            oldList.add(Integer.toString(consultDoc.getDocumentNo()));
        }
        detachFromConsult(currentList, oldList, documentType, requestId);
        attachToConsult(currentList, oldList, documentType, providerNo, requestId);
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

    /**
     * Synchronizes the set of document attachments for an electronic form (eForm). Compares
     * the provided attachment IDs with the currently persisted set, attaches new documents,
     * and soft-deletes removed ones.
     *
     * @param attachments String[] the current set of document IDs to be attached
     * @param documentType DocumentType the type of documents being attached
     * @param providerNo String the provider performing the operation
     * @param fdid Integer the eForm's form data identifier
     */
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
