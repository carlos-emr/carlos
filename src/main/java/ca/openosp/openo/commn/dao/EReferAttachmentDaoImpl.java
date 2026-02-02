//CHECKSTYLE:OFF
package ca.openosp.openo.commn.dao;

import org.hibernate.Hibernate;
import ca.openosp.openo.commn.model.EReferAttachment;
import org.springframework.stereotype.Repository;

import javax.persistence.Query;
import java.util.Date;
import java.util.List;

/**
 * Data Access Object implementation for managing EReferAttachment entities in the OpenO EMR system.
 * <p>
 * This DAO provides database operations for electronic referral (eReferral) attachments associated with
 * patient demographics. eReferral attachments are documents, images, or other files that healthcare
 * providers attach to electronic referrals when sending patient information to specialists or other
 * healthcare facilities.
 * </p>
 * <p>
 * The implementation extends {@link AbstractDaoImpl} to inherit common CRUD operations and implements
 * {@link EReferAttachmentDao} to provide specialized queries for attachment management, including
 * retrieval of recent non-archived attachments within a specified time period.
 * </p>
 * <p>
 * This DAO supports the eReferral workflow by managing attachment metadata and lazy-loading associated
 * attachment data to optimize performance when handling large files.
 * </p>
 *
 * @see EReferAttachment
 * @see EReferAttachmentDao
 * @see AbstractDaoImpl
 * @since 2026-01-23
 */
@Repository
public class EReferAttachmentDaoImpl extends AbstractDaoImpl<EReferAttachment> implements EReferAttachmentDao {

    /**
     * Constructs a new EReferAttachmentDaoImpl.
     * <p>
     * Initializes the DAO with the EReferAttachment entity class type for generic operations.
     * </p>
     */
    public EReferAttachmentDaoImpl() {
        super(EReferAttachment.class);
    }

    /**
     * Retrieves the most recent non-archived eReferral attachment for a specific patient demographic
     * created after a specified expiry date.
     * <p>
     * This method queries for eReferral attachments that meet the following criteria:
     * <ul>
     *   <li>Not archived (archived = false)</li>
     *   <li>Belongs to the specified demographic number (patient)</li>
     *   <li>Created after the specified expiry date</li>
     * </ul>
     * If multiple attachments match the criteria, only the first result is returned. The method also
     * eagerly initializes the associated attachment data collection to prevent lazy loading exceptions
     * when the attachment data is accessed outside the persistence context.
     * </p>
     * <p>
     * This is commonly used in eReferral workflows to determine if a patient has recent attachments
     * that are still valid (not expired) and can be reused for a new referral, avoiding duplicate
     * document uploads.
     * </p>
     *
     * @param demographicNo Integer the unique identifier of the patient demographic
     * @param expiry Date the cutoff date; only attachments created after this date are considered
     * @return EReferAttachment the most recent matching attachment with initialized attachment data,
     *         or null if no matching attachments are found
     */
    @Override
    public EReferAttachment getRecentByDemographic(Integer demographicNo, Date expiry) {
        EReferAttachment eReferAttachment = null;

        String sql = "SELECT e FROM " + modelClass.getSimpleName() + " e WHERE e.archived = FALSE AND e.demographicNo = ?1 AND e.created > ?2";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographicNo);
        query.setParameter(2, expiry);

        List<EReferAttachment> eReferAttachments = query.getResultList();

        if (!eReferAttachments.isEmpty()) {
            eReferAttachment = eReferAttachments.get(0);
            Hibernate.initialize(eReferAttachment.getAttachments());
        }

        return eReferAttachment;
    }
}
