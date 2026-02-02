package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.ARRecordSet;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.ARRecordSetDocument;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for the ARRecordSetDocument interface, providing XML document handling
 * for British Columbia Antenatal Record (BCAR) AR2005 record sets.
 *
 * <p>This class is part of the AR2005 XML schema implementation and provides thread-safe operations
 * for managing ARRecordSet elements within an XML document structure. It extends Apache XMLBeans'
 * XmlComplexContentImpl to provide XML serialization and deserialization capabilities for healthcare
 * antenatal records.</p>
 *
 * <p>The AR2005 namespace (http://www.oscarmcmaster.org/AR2005) is used for British Columbia's
 * standardized antenatal record format, which includes pregnancy history, obstetrical history,
 * medical history, physical examinations, and other maternal care information.</p>
 *
 * <p>All operations in this class are synchronized on the internal monitor to ensure thread safety
 * when accessing or modifying the underlying XML store.</p>
 *
 * @see ca.openosp.openo.ar2005.ARRecordSetDocument
 * @see ca.openosp.openo.ar2005.ARRecordSet
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-24
 */
public class ARRecordSetDocumentImpl extends XmlComplexContentImpl implements ARRecordSetDocument
{
    private static final long serialVersionUID = 1L;
    private static final QName ARRECORDSET$0;

    /**
     * Constructs a new ARRecordSetDocumentImpl with the specified schema type.
     *
     * <p>This constructor is typically invoked by the Apache XMLBeans framework during
     * XML document parsing or when creating new document instances. It initializes the
     * underlying XML complex content structure with the provided schema type definition.</p>
     *
     * @param sType SchemaType the schema type definition for this document implementation
     */
    public ARRecordSetDocumentImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the ARRecordSet element from this document.
     *
     * <p>This method performs a thread-safe lookup of the ARRecordSet element within the
     * XML document structure. The ARRecordSet contains the collection of individual antenatal
     * records (ARRecord instances) for a patient's pregnancy care.</p>
     *
     * <p>The method is synchronized on the internal monitor to ensure thread safety when
     * accessing the underlying XML store.</p>
     *
     * @return ARRecordSet the ARRecordSet element, or null if no ARRecordSet element exists
     *         in this document
     */
    public ARRecordSet getARRecordSet() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ARRecordSet target = null;
            target = (ARRecordSet)this.get_store().find_element_user(ARRecordSetDocumentImpl.ARRECORDSET$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets or replaces the ARRecordSet element in this document.
     *
     * <p>This method performs a thread-safe update of the ARRecordSet element within the
     * XML document structure. If an ARRecordSet element already exists, it will be replaced
     * with the provided one. If no ARRecordSet element exists, a new one will be created
     * and populated with the provided data.</p>
     *
     * <p>The method is synchronized on the internal monitor to ensure thread safety when
     * modifying the underlying XML store.</p>
     *
     * @param arRecordSet ARRecordSet the ARRecordSet element to set in this document,
     *                    containing the collection of antenatal records
     */
    public void setARRecordSet(final ARRecordSet arRecordSet) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ARRecordSet target = null;
            target = (ARRecordSet)this.get_store().find_element_user(ARRecordSetDocumentImpl.ARRECORDSET$0, 0);
            if (target == null) {
                target = (ARRecordSet)this.get_store().add_element_user(ARRecordSetDocumentImpl.ARRECORDSET$0);
            }
            target.set((XmlObject)arRecordSet);
        }
    }

    /**
     * Creates and adds a new empty ARRecordSet element to this document.
     *
     * <p>This method performs a thread-safe creation of a new ARRecordSet element within the
     * XML document structure. Unlike setARRecordSet(), this method always creates a new element
     * and returns it for further population. The returned ARRecordSet can then be populated with
     * individual ARRecord elements representing antenatal care records.</p>
     *
     * <p>This is typically used when building a new AR2005 document from scratch, allowing the
     * caller to populate the record set incrementally.</p>
     *
     * <p>The method is synchronized on the internal monitor to ensure thread safety when
     * modifying the underlying XML store.</p>
     *
     * @return ARRecordSet the newly created and added ARRecordSet element, ready to be populated
     *         with antenatal records
     */
    public ARRecordSet addNewARRecordSet() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            ARRecordSet target = null;
            target = (ARRecordSet)this.get_store().add_element_user(ARRecordSetDocumentImpl.ARRECORDSET$0);
            return target;
        }
    }
    
    static {
        ARRECORDSET$0 = new QName("http://www.oscarmcmaster.org/AR2005", "ARRecordSet");
    }
}
