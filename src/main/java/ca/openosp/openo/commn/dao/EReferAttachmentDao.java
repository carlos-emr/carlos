//CHECKSTYLE:OFF
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.EReferAttachment;

import java.util.Date;

/**
 * Data Access Object (DAO) interface for managing electronic referral attachments in OpenO EMR.
 *
 * <p>This DAO provides database operations for {@link EReferAttachment} entities, which represent
 * file attachments associated with electronic referrals (eReferrals) for patient demographics.
 * eReferral attachments support the healthcare workflow by allowing providers to attach supporting
 * documentation (medical images, lab results, consultation notes) to electronic referral requests
 * sent to specialists or other healthcare providers.</p>
 *
 * <p>Key features:</p>
 * <ul>
 *   <li>Retrieval of recent attachments by patient demographic number</li>
 *   <li>Expiry-based filtering for attachment lifecycle management</li>
 *   <li>Support for archived attachments to maintain data retention compliance</li>
 * </ul>
 *
 * <p>This interface extends {@link AbstractDao} to inherit standard CRUD operations
 * (create, read, update, delete) while providing specialized query methods for
 * eReferral attachment management.</p>
 *
 * @since 2026-01-24
 * @see EReferAttachment
 * @see EReferAttachmentDaoImpl
 * @see ca.openosp.openo.commn.model.EReferAttachmentData
 */
public interface EReferAttachmentDao extends AbstractDao<EReferAttachment> {
    /**
     * Retrieves the most recent electronic referral attachment for a specific patient
     * that has not expired.
     *
     * <p>This method queries for the latest {@link EReferAttachment} associated with
     * the given demographic number where the creation date is after the specified expiry
     * date. This supports workflows where attachments have a limited retention period
     * or need to be refreshed periodically for active referral processes.</p>
     *
     * <p>The method considers only non-archived attachments to ensure that historical
     * or deleted attachments are not returned in active referral workflows.</p>
     *
     * @param demographicNo Integer the unique identifier for the patient demographic record
     * @param expiry Date the cutoff date for attachment validity; attachments created before
     *               this date are considered expired and will not be returned
     * @return EReferAttachment the most recent non-expired attachment for the patient,
     *         or {@code null} if no valid attachment exists
     */
    public EReferAttachment getRecentByDemographic(Integer demographicNo, Date expiry);
}
