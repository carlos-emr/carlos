package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;

public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {
    /**
     * The most recent unarchived Ocean queue row for a document of one patient.
     *
     * @param demographicNo the patient whose queue row may be returned; a row queued for another
     *                      patient with the same document id is never returned (issue #3867)
     */
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Integer demographicNo, Date expiry);
}
