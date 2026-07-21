package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;

/**
 * Data Access Object (DAO) interface for eReferral attachment data.
 * Defines the standard persistence operations required to manage the binary
 * contents of documents attached to eReferrals.
 */
public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
