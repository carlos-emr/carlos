package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;
/**
 * Data Access Object for eReferral attachment data.
 * <p>
 * Provides CRUD operations and custom queries for managing the storage and retrieval
 * of eReferral attachments in the CARLOS EMR database.
 * </p>
 */


public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {
        // Execute attachment retrieval query carefully to prevent excessive memory consumption on large files.
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
