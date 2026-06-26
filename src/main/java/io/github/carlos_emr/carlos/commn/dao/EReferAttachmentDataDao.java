package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;

/**
 * Data Access Object (DAO) for interacting with EReferAttachmentDataDao entities in the database.
 */
public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {
    // Performs CRUD operations and custom queries for EReferAttachmentDataDao

    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
