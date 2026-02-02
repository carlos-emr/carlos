package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.ARRecord;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.ARRecordDocument;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for the AR2005 (Antenatal Record 2005) document structure.
 *
 * This class provides XML Beans-based implementation for managing British Columbia
 * Antenatal Record documents in OpenO EMR. The AR2005 format is used for tracking
 * prenatal care information including patient demographics, medical history, and
 * pregnancy-related clinical data.
 *
 * The implementation extends {@link XmlComplexContentImpl} to leverage XML Beans
 * functionality for XML serialization and deserialization, providing thread-safe
 * access to the underlying AR record data through synchronized methods.
 *
 * @see ARRecordDocument
 * @see ARRecord
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-24
 */
public class ARRecordDocumentImpl extends XmlComplexContentImpl implements ARRecordDocument
{
    private static final long serialVersionUID = 1L;
    private static final QName ARRECORD$0;

    /**
     * Constructs a new ARRecordDocument implementation with the specified schema type.
     *
     * This constructor initializes the XML Beans complex content implementation
     * with the AR2005 schema type definition, setting up the internal XML store
     * for managing antenatal record data.
     *
     * @param sType SchemaType the XML schema type definition for AR2005 documents
     */
    public ARRecordDocumentImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the AR2005 antenatal record from this document.
     *
     * This method provides thread-safe access to the ARRecord element within
     * the XML document structure. The method synchronizes on the internal
     * monitor to ensure safe concurrent access to the underlying XML store.
     *
     * @return ARRecord the antenatal record data, or null if no record is present
     */
    public ARRecord getARRecord() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ARRecord target = null;
            target = (ARRecord)this.get_store().find_element_user(ARRecordDocumentImpl.ARRECORD$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets or replaces the AR2005 antenatal record in this document.
     *
     * This method provides thread-safe modification of the ARRecord element
     * within the XML document structure. If an ARRecord element already exists,
     * it will be replaced with the provided record. If no element exists, a new
     * one will be created and populated with the provided data.
     *
     * The method synchronizes on the internal monitor to ensure safe concurrent
     * access when modifying the underlying XML store.
     *
     * @param arRecord ARRecord the antenatal record data to set in this document
     */
    public void setARRecord(final ARRecord arRecord) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ARRecord target = null;
            target = (ARRecord)this.get_store().find_element_user(ARRecordDocumentImpl.ARRECORD$0, 0);
            if (target == null) {
                target = (ARRecord)this.get_store().add_element_user(ARRecordDocumentImpl.ARRECORD$0);
            }
            target.set((XmlObject)arRecord);
        }
    }

    /**
     * Creates and adds a new empty AR2005 antenatal record to this document.
     *
     * This method provides thread-safe creation of a new ARRecord element
     * within the XML document structure. The newly created record will be
     * empty and can be populated with antenatal care data after creation.
     *
     * The method synchronizes on the internal monitor to ensure safe concurrent
     * access when adding the new element to the underlying XML store.
     *
     * @return ARRecord the newly created and added antenatal record element
     */
    public ARRecord addNewARRecord() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ARRecord target = null;
            target = (ARRecord)this.get_store().add_element_user(ARRecordDocumentImpl.ARRECORD$0);
            return target;
        }
    }
    
    static {
        ARRECORD$0 = new QName("http://www.oscarmcmaster.org/AR2005", "ARRecord");
    }
}
