package ca.openosp.openo.PMmodule.caisi_integrator;

import java.util.Date;
import java.io.Serializable;

/**
 * Header metadata class for integrator data transfer files.
 * <p>
 * This class contains essential metadata that is written at the beginning of serialized
 * integrator data files. It tracks versioning, dependencies, timestamps, and facility
 * information necessary for proper synchronization and data integrity checks between
 * facilities in the integrator system.
 * </p>
 * <p>
 * The header allows receiving facilities to:
 * <ul>
 *   <li>Verify the file format version for compatibility</li>
 *   <li>Track incremental updates through date ranges and dependencies</li>
 *   <li>Identify the source facility and authentication credentials</li>
 *   <li>Ensure data integrity through checksum verification</li>
 * </ul>
 * </p>
 * 
 * @see IntegratorFileFooter
 */
public class IntegratorFileHeader implements Serializable
{
    /** The date of the last successful data push (start of the update window) */
    private Date lastDate;
    
    /** The current date/time of this data push (end of the update window) */
    private Date date;
    
    /** MD5 checksum of the previous file this update depends on (for incremental updates) */
    private String dependsOn;
    
    /** The integrator facility ID of the facility generating this file */
    private Integer cachedFacilityId;
    
    /** The name of the facility generating this file */
    private String cachedFacilityName;
    
    /** The username used for authentication to the integrator service */
    private String username;
    
    /** 
     * Current file format version.
     * This constant should be incremented when breaking changes are made to the file format.
     */
    public static final int VERSION = 1;
    
    /**
     * Gets the current push date.
     * 
     * @return the date/time when this data push was generated
     */
    public Date getDate() {
        return this.date;
    }
    
    /**
     * Sets the current push date.
     * 
     * @param date the date/time when this data push is being generated
     */
    public void setDate(final Date date) {
        this.date = date;
    }
    
    /**
     * Gets the last successful push date.
     * 
     * @return the date of the last successful data push, defining the start of the update window
     */
    public Date getLastDate() {
        return this.lastDate;
    }
    
    /**
     * Sets the last successful push date.
     * 
     * @param lastDate the date of the last successful data push
     */
    public void setLastDate(final Date lastDate) {
        this.lastDate = lastDate;
    }
    
    /**
     * Gets the checksum of the file this update depends on.
     * 
     * @return the MD5 checksum string of the previous file, or null if this is a full push
     */
    public String getDependsOn() {
        return this.dependsOn;
    }
    
    /**
     * Sets the checksum dependency for incremental updates.
     * 
     * @param dependsOn the MD5 checksum of the previous file this update builds upon
     */
    public void setDependsOn(final String dependsOn) {
        this.dependsOn = dependsOn;
    }
    
    /**
     * Gets the file format version.
     * 
     * @return the version number (currently always returns 1)
     */
    public int getVersion() {
        return 1;
    }
    
    /**
     * Gets the source facility ID.
     * 
     * @return the integrator facility ID of the facility generating this file
     */
    public Integer getCachedFacilityId() {
        return this.cachedFacilityId;
    }
    
    /**
     * Sets the source facility ID.
     * 
     * @param cachedFacilityId the integrator facility ID to set
     */
    public void setCachedFacilityId(final Integer cachedFacilityId) {
        this.cachedFacilityId = cachedFacilityId;
    }
    
    /**
     * Gets the source facility name.
     * 
     * @return the name of the facility generating this file
     */
    public String getCachedFacilityName() {
        return this.cachedFacilityName;
    }
    
    /**
     * Sets the source facility name.
     * 
     * @param cachedFacilityName the facility name to set
     */
    public void setCachedFacilityName(final String cachedFacilityName) {
        this.cachedFacilityName = cachedFacilityName;
    }
    
    /**
     * Gets the integrator authentication username.
     * 
     * @return the username used for integrator service authentication
     */
    public String getUsername() {
        return this.username;
    }
    
    /**
     * Sets the integrator authentication username.
     * 
     * @param username the username for integrator service authentication
     */
    public void setUsername(final String username) {
        this.username = username;
    }
}
