/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */

package io.github.carlos_emr.carlos.PMmodule.caisi_integrator;

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
