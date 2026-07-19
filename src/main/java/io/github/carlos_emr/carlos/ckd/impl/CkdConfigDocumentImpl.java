package io.github.carlos_emr.carlos.ckd.impl;

import org.apache.xmlbeans.XmlObject;
import io.github.carlos_emr.carlos.ckd.CKDConfig;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.CkdConfigDocument;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link CkdConfigDocument} interface.
 *
 * This class provides the concrete implementation for managing CKD (Chronic Kidney Disease)
 * configuration document containers within OpenO EMR. It serves as the root document wrapper
 * for CKD configuration data, handling the XML document structure and providing access to
 * the main CKD configuration element.
 *
 * The implementation manages the complete XML document lifecycle including parsing,
 * serialization, and deserialization of CKD configuration documents within the
 * "http://www.oscarmcmaster.org/ckd" XML namespace. It provides thread-safe access
 * to the root CKD configuration element contained within the document.
 *
 * This document implementation is typically used as the entry point for loading and
 * saving complete CKD configuration files, containing all the nested configuration
 * elements such as diagnosis codes, blood pressure settings, medical history,
 * drugs, and exclusion criteria.
 *
 * @see CkdConfigDocument
 * @see CKDConfig
 * @since 2010-01-01
 */
public class CkdConfigDocumentImpl extends XmlComplexContentImpl implements CkdConfigDocument
{
    private static final long serialVersionUID = 1L;
    private static final QName CKDCONFIG$0;
    
    /**
     * Constructs a new CkdConfigDocumentImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this CKD configuration document
     */
    public CkdConfigDocumentImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves the root CKD configuration element from the document.
     *
     * @return CKDConfig the CKD configuration element, or null if not set
     */
    public CKDConfig getCkdConfig() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CKDConfig target = null;
            target = (CKDConfig)this.get_store().find_element_user(CkdConfigDocumentImpl.CKDCONFIG$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }
    
    /**
     * Sets the root CKD configuration element in the document.
     *
     * @param ckdConfig CKDConfig the CKD configuration element to set
     */
    public void setCkdConfig(final CKDConfig ckdConfig) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CKDConfig target = null;
            target = (CKDConfig)this.get_store().find_element_user(CkdConfigDocumentImpl.CKDCONFIG$0, 0);
            if (target == null) {
                target = (CKDConfig)this.get_store().add_element_user(CkdConfigDocumentImpl.CKDCONFIG$0);
            }
            target.set((XmlObject)ckdConfig);
        }
    }
    
    /**
     * Creates and adds a new CKD configuration element to the document.
     *
     * @return CKDConfig the newly created CKD configuration element
     */
    public CKDConfig addNewCkdConfig() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            CKDConfig target = null;
            target = (CKDConfig)this.get_store().add_element_user(CkdConfigDocumentImpl.CKDCONFIG$0);
            return target;
        }
    }
    
    static {
        // Initialize QName constant for XML root element identification within CKD namespace
        CKDCONFIG$0 = new QName("http://www.oscarmcmaster.org/ckd", "ckd-config");
    }
}
