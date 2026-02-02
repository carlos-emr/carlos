//CHECKSTYLE:OFF
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.EReferAttachmentData;

import java.util.Date;

public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
