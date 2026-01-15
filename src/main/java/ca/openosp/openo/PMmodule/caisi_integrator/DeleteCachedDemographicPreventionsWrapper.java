package ca.openosp.openo.PMmodule.caisi_integrator;

import java.util.List;
import java.io.Serializable;

/**
 * Wrapper class for deletion operations on cached demographic preventions in the integrator system.
 * <p>
 * This class encapsulates the data needed to identify which demographic preventions (immunizations,
 * screenings, etc.) should be retained during synchronization between facilities in the integrator
 * system. It contains the demographic identifier and a list of prevention IDs that should NOT be deleted.
 * </p>
 * <p>
 * During integrator synchronization, this wrapper is used to communicate which preventions exist
 * at the source facility. Preventions not in the nonDeletedIds list may be candidates for removal
 * at remote facilities if they no longer exist at the source.
 * </p>
 * 
 * @see java.io.Serializable
 */
public class DeleteCachedDemographicPreventionsWrapper implements Serializable
{
    /** The demographic number identifying the patient whose preventions are being managed */
    private Integer demographicNo;
    
    /** List of prevention IDs that should be retained (not deleted) during synchronization */
    private List<Integer> nonDeletedIds;
    
    /**
     * Default constructor creating an empty wrapper.
     */
    public DeleteCachedDemographicPreventionsWrapper() {
    }
    
    /**
     * Constructs a wrapper with the specified demographic and prevention IDs.
     * 
     * @param demographicNo the demographic number of the patient
     * @param nonDeletedIds the list of prevention IDs that should be retained
     */
    public DeleteCachedDemographicPreventionsWrapper(final Integer demographicNo, final List<Integer> nonDeletedIds) {
        this.demographicNo = demographicNo;
        this.nonDeletedIds = nonDeletedIds;
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
     * Gets the list of prevention IDs to be retained.
     * 
     * @return the list of prevention IDs that should not be deleted
     */
    public List<Integer> getNonDeletedIds() {
        return this.nonDeletedIds;
    }
    
    /**
     * Sets the list of prevention IDs to be retained.
     * 
     * @param nonDeletedIds the list of prevention IDs that should not be deleted
     */
    public void setNonDeletedIds(final List<Integer> nonDeletedIds) {
        this.nonDeletedIds = nonDeletedIds;
    }
}
