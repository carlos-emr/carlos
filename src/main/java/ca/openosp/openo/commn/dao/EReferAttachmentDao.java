//CHECKSTYLE:OFF
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.EReferAttachment;

import java.util.Date;

/**
 * Data Access Object interface for {@link EReferAttachment} entities providing database operations
 * for Ocean eRefer attachment management in OpenO EMR.
 * 
 * <p>This DAO manages electronic referral (eRefer) attachments that are associated with patients
 * when creating referrals through the Ocean eReferral system. Ocean is a third-party healthcare
 * integration platform that enables electronic referrals and patient engagement tools.</p>
 * 
 * <p>EReferAttachment records store metadata about attachment collections for a specific patient,
 * including creation timestamps and archival status. Each attachment collection can contain multiple
 * lab results and documents (stored in {@link ca.openosp.openo.commn.model.EReferAttachmentData})
 * that should be included with an electronic referral.</p>
 * 
 * <p>Key use cases:</p>
 * <ul>
 *   <li>Retrieving recent non-archived attachments for a patient to include in new referrals</li>
 *   <li>Managing attachment lifecycle (creation, archival) for referral workflows</li>
 *   <li>Integrating lab results and documents with Ocean eReferral system</li>
 * </ul>
 * 
 * @see EReferAttachment
 * @see ca.openosp.openo.commn.model.EReferAttachmentData
 * @see ca.openosp.openo.commn.dao.EReferAttachmentDataDao
 * @see ca.openosp.openo.managers.ConsultationManagerImpl
 * @since 2026-01-14
 */
public interface EReferAttachmentDao extends AbstractDao<EReferAttachment> {
    
    /**
     * Retrieves the most recent non-archived eRefer attachment collection for a specific patient
     * that was created after the specified expiry date.
     * 
     * <p>This method is typically used when creating a new electronic referral to find existing
     * attachment collections that can be reused or referenced. Only non-archived attachments
     * created after the expiry date are considered, ensuring that stale attachment data is not
     * included in new referrals.</p>
     * 
     * <p>The returned attachment collection includes fully initialized attachment data (lab results
     * and documents) through Hibernate lazy-loading initialization.</p>
     * 
     * @param demographicNo Integer the unique patient identifier (demographic number) to search for
     * @param expiry Date the expiry threshold - only attachments created after this date are returned
     * @return EReferAttachment the most recent matching attachment collection with initialized attachment data,
     *         or null if no non-archived attachments exist for the patient after the expiry date
     */
    public EReferAttachment getRecentByDemographic(Integer demographicNo, Date expiry);
}
