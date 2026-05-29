package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;

/**
 * Data access object providing database persistence and retrieval operations specific to EReferAttachmentData entities.
 */
public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {

    // Getrecentbydocumentid is exposed here to satisfy the external component interface contract without exposing internal state.
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
