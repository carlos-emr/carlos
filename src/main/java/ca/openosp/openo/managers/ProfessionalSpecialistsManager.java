//CHECKSTYLE:OFF
package ca.openosp.openo.managers;

import ca.openosp.openo.commn.dao.ProfessionalSpecialistDao;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.util.List;

/**
 * Service layer manager for professional specialist operations in the OpenO EMR system.
 * <p>
 * This manager provides secure access to professional specialist data, including specialist
 * physicians, consultants, and other healthcare professionals to whom patients may be referred.
 * It enforces role-based access control through the consultation security object (_con) and
 * coordinates between the web layer and data access layer.
 * </p>
 * <p>
 * Professional specialists are healthcare providers external to the clinic who receive patient
 * referrals. This includes specialists, consultants, laboratories, and other healthcare services.
 * Each specialist record contains contact information, specialty type, and optional electronic
 * data exchange configuration for automated referral processing.
 * </p>
 * <p>
 * All operations require READ privilege on the consultation security object (_con) to protect
 * sensitive healthcare provider information.
 * </p>
 *
 * @see ca.openosp.openo.commn.model.ProfessionalSpecialist
 * @see ca.openosp.openo.commn.dao.ProfessionalSpecialistDao
 * @see ca.openosp.openo.managers.SecurityInfoManager
 * @since 2026-01-24
 */
@Service
public class ProfessionalSpecialistsManager implements Serializable {

    @Autowired
    private ProfessionalSpecialistDao professionalSpecialistDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    /**
     * Default constructor for Spring dependency injection.
     * <p>
     * The manager is instantiated by Spring and configured with required dependencies
     * through autowired fields.
     * </p>
     */
    public ProfessionalSpecialistsManager() {
        // default
    }

    /**
     * Retrieves a professional specialist record by unique identifier.
     * <p>
     * This method fetches a single professional specialist record from the database,
     * enforcing security constraints to ensure only authorized users can access
     * healthcare provider information. The specialist record includes contact details,
     * specialty type, and any configured electronic data exchange settings.
     * </p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information containing
     *                     provider credentials and security context
     * @param id int the unique database identifier of the professional specialist
     * @return ProfessionalSpecialist the specialist record with all associated data,
     *         or null if no specialist exists with the given identifier
     * @throws RuntimeException if the current user lacks READ privilege on the
     *                          consultation security object (_con)
     * @see ca.openosp.openo.commn.model.ProfessionalSpecialist
     */
    public ProfessionalSpecialist getProfessionalSpecialist(LoggedInInfo loggedInInfo, int id) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_con)");
        }
        return professionalSpecialistDao.find(id);
    }

    /**
     * Searches for professional specialists matching a keyword query.
     * <p>
     * This method performs a text search across professional specialist records,
     * typically matching against name, specialty type, and annotation fields.
     * Results are filtered by security constraints to ensure only authorized
     * users can search healthcare provider information.
     * </p>
     * <p>
     * The search is case-insensitive and may match partial strings across multiple
     * fields including first name, last name, professional letters, specialty type,
     * and annotations. Empty or null keywords may return all non-deleted specialists
     * depending on the DAO implementation.
     * </p>
     *
     * @param loggedInInfo LoggedInInfo the current user's session information containing
     *                     provider credentials and security context
     * @param keyword String the search term to match against specialist records; may be
     *                null or empty to retrieve all specialists
     * @return List&lt;ProfessionalSpecialist&gt; list of specialist records matching the
     *         search criteria, ordered by last name and first name; empty list if no
     *         matches found
     * @throws RuntimeException if the current user lacks READ privilege on the
     *                          consultation security object (_con)
     * @see ca.openosp.openo.commn.dao.ProfessionalSpecialistDao#search(String)
     */
    public List<ProfessionalSpecialist> searchProfessionalSpecialist(LoggedInInfo loggedInInfo, String keyword) {
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_con", SecurityInfoManager.READ, null)) {
            throw new RuntimeException("missing required sec object (_con)");
        }
        return professionalSpecialistDao.search(keyword);
    }
}
