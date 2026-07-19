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
 * Simple wrapper class for byte arrays that implements Serializable.
 * <p>
 * This class provides a serializable container for byte array data, allowing
 * raw byte data to be easily passed between distributed systems or stored
 * in serialized form. Used primarily in integrator data transfer operations.
 * </p>
 * 
 * @see java.io.Serializable
 */
public class ByteWrapper implements Serializable
{
    /** The wrapped byte array data */
    private byte[] data;
    
    /**
     * Default constructor creating an empty ByteWrapper.
     */
    public ByteWrapper() {
    }
    
    /**
     * Constructs a ByteWrapper containing the specified byte array.
     * 
     * @param data the byte array to wrap
     */
    public ByteWrapper(final byte[] data) {
        this.data = data;
    }
    
    /**
     * Retrieves the wrapped byte array.
     * 
     * @return the byte array stored in this wrapper, or null if not set
     */
    public byte[] getData() {
        return this.data;
    }
    
    /**
     * Sets the byte array to be wrapped.
     * 
     * @param data the byte array to store in this wrapper
     */
    public void setData(final byte[] data) {
        this.data = data;
    }
}
