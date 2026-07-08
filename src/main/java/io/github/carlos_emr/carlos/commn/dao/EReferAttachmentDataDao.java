package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;
/**
 * Data Access Object interface for managing the persistence of EReferAttachmentData entities.
 */

public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {
    // Retrieves the most recent attachment data segment associated with the given document ID.
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
