package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;

/**
 * DAO interface for managing eReferral attachment data.
 * <p>
 * Provides operations to retrieve cached attachment data associated with
 * electronic referral documents, supporting the eReferral integration module.
 *
 * @since 2026
 */
public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {

    /**
     * Retrieves the most recent attachment data for a document, filtered by type and expiry.
     *
     * @param docId  Integer the document identifier
     * @param type   String the attachment type
     * @param expiry Date the expiry date cutoff (only returns data not yet expired)
     * @return the most recent matching {@link EReferAttachmentData}, or {@code null} if not found
     */
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
