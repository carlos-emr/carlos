package ca.openosp.openo.PMmodule.caisi_integrator;

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
