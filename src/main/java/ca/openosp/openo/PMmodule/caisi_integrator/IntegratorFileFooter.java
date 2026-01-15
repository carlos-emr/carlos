package ca.openosp.openo.PMmodule.caisi_integrator;

import java.io.Serializable;

/**
 * Footer marker class for integrator data transfer files.
 * <p>
 * This class serves as a sentinel object placed at the end of serialized integrator
 * data files to mark the completion of the file. It can optionally contain a checksum
 * for data integrity verification, though the primary checksum is typically calculated
 * externally for the complete file.
 * </p>
 * <p>
 * During integrator push operations, this footer is written as the final object in
 * serialized data streams to signal that all demographic and facility data has been
 * successfully written.
 * </p>
 * 
 * @see IntegratorFileHeader
 */
public class IntegratorFileFooter implements Serializable
{
    /** 
     * Optional checksum value for the data.
     * Initialized to -1 to indicate no checksum has been set.
     */
    private int checksum;
    
    /**
     * Default constructor creating a footer with no checksum (-1).
     */
    public IntegratorFileFooter() {
        this.checksum = -1;
    }
    
    /**
     * Constructs a footer with the specified checksum value.
     * 
     * @param checksum the checksum value to include in the footer
     */
    public IntegratorFileFooter(final int checksum) {
        this.checksum = checksum;
    }
    
    /**
     * Gets the checksum value.
     * 
     * @return the checksum, or -1 if no checksum was set
     */
    public int getChecksum() {
        return this.checksum;
    }
    
    /**
     * Sets the checksum value.
     * 
     * @param checksum the checksum value to set
     */
    public void setChecksum(final int checksum) {
        this.checksum = checksum;
    }
}
