package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;

/**
 * Data Access Object providing CRUD operations and custom queries
 * specifically for EReferAttachmentData entities, managing the persistence of referral attachment payloads.
 */
public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {

    // Contract interface for module processing
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
