package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;
/**
 * Data Access Object (DAO) interface for E Refer Attachment Data Dao operations.
 *
 * @since 2026-05-05
 */


public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
