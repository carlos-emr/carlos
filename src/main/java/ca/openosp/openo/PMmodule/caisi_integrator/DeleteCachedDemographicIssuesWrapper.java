package ca.openosp.openo.PMmodule.caisi_integrator;

import ca.openosp.openo.caisi_integrator.dao.FacilityIdDemographicIssueCompositePk;
import java.util.List;
import java.io.Serializable;

/**
 * Wrapper class for deletion operations on cached demographic issues in the integrator system.
 * <p>
 * This class encapsulates the data needed to identify which demographic issues should
 * be retained (and implicitly which should be deleted) during synchronization between
 * facilities in the integrator system. It contains the demographic identifier and a
 * list of issue keys that should NOT be deleted.
 * </p>
 * <p>
 * Used during integrator push operations to synchronize issue data across facilities,
 * ensuring that issues deleted at the source facility are also removed at remote facilities.
 * </p>
 * 
 * @see FacilityIdDemographicIssueCompositePk
 */
public class DeleteCachedDemographicIssuesWrapper implements Serializable
{
    /** The demographic number identifying the patient whose issues are being managed */
    private Integer demographicNo;
    
    /** List of composite keys for issues that should be retained (not deleted) */
    private List<FacilityIdDemographicIssueCompositePk> keys;
    
    /**
     * Default constructor creating an empty wrapper.
     */
    public DeleteCachedDemographicIssuesWrapper() {
    }
    
    /**
     * Constructs a wrapper with the specified demographic and issue keys.
     * 
     * @param demographicNo the demographic number of the patient
     * @param keys the list of issue keys that should be retained
     */
    public DeleteCachedDemographicIssuesWrapper(final Integer demographicNo, final List<FacilityIdDemographicIssueCompositePk> keys) {
        this.demographicNo = demographicNo;
        this.keys = keys;
    }
    
    /**
     * Gets the demographic number.
     * 
     * @return the demographic number of the patient
     */
    public Integer getDemographicNo() {
        return this.demographicNo;
    }
    
    /**
     * Sets the demographic number.
     * 
     * @param demographicNo the demographic number to set
     */
    public void setDemographicNo(final Integer demographicNo) {
        this.demographicNo = demographicNo;
    }
    
    /**
     * Gets the list of issue keys to be retained.
     * 
     * @return the list of composite keys for issues that should not be deleted
     */
    public List<FacilityIdDemographicIssueCompositePk> getKeys() {
        return this.keys;
    }
    
    /**
     * Sets the list of issue keys to be retained.
     * 
     * @param keys the list of composite keys for issues that should not be deleted
     */
    public void setKeys(final List<FacilityIdDemographicIssueCompositePk> keys) {
        this.keys = keys;
    }
}
