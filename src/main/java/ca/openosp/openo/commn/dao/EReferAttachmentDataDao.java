//CHECKSTYLE:OFF
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.EReferAttachmentData;

import java.util.Date;

/**
 * Data Access Object (DAO) interface for managing electronic referral attachment data entities.
 *
 * <p>This DAO provides database operations for {@link EReferAttachmentData} entities, which represent
 * the association between electronic referral (eReferral) attachments and laboratory results in the
 * healthcare system. The attachment data links eReferral documents with specific lab results (lab_id)
 * and lab types, enabling comprehensive patient referral documentation that includes supporting
 * laboratory evidence.</p>
 *
 * <p>Electronic referrals are a critical component of coordinated patient care, allowing healthcare
 * providers to send consultation requests and supporting documentation (including lab results) to
 * specialists electronically. This DAO manages the many-to-many relationship between referral
 * attachments and laboratory data.</p>
 *
 * <p>This interface extends {@link AbstractDao} and inherits standard CRUD operations including
 * persist, merge, remove, batch operations, and basic query methods.</p>
 *
 * @see EReferAttachmentData
 * @see AbstractDao
 * @see ca.openosp.openo.commn.model.EReferAttachment
 * @since 2024-03-12
 */
public interface EReferAttachmentDataDao extends AbstractDao<EReferAttachmentData> {

    /**
     * Retrieves the most recent eReferral attachment data by document ID, type, and expiry date.
     *
     * <p>This method searches for attachment data associated with a specific document ID, filtered
     * by the lab type and that has not expired as of the given expiry date. The "most recent"
     * attachment is determined by database ordering, typically by creation or modification timestamp.</p>
     *
     * <p>This method is commonly used when displaying eReferral attachments to ensure that only
     * current, valid laboratory data is shown, and that expired attachments are excluded from
     * the active referral documentation.</p>
     *
     * @param docId Integer the unique identifier of the document to which the attachment is associated
     * @param type String the laboratory type code indicating the category of lab result (e.g., "HL7", "LAB")
     * @param expiry Date the cutoff date for determining validity; attachments valid after this date are considered
     * @return EReferAttachmentData the most recent attachment data matching the criteria, or null if none found
     */
    public EReferAttachmentData getRecentByDocumentId(Integer docId, String type, Date expiry);
}
