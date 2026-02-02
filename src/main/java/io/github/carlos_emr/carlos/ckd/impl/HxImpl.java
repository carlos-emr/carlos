package io.github.carlos_emr.carlos.ckd.impl;

import io.github.carlos_emr.carlos.ckd.SearchText;
import org.apache.xmlbeans.XmlObject;
import io.github.carlos_emr.carlos.ckd.Issues;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.Hx;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link Hx} interface.
 *
 * This class provides the concrete implementation for managing medical history (Hx)
 * configuration within CKD (Chronic Kidney Disease) documents. It handles the XML
 * serialization and deserialization of medical history data, including patient issues
 * and search text patterns used for identifying relevant medical history elements.
 *
 * The implementation manages medical history elements within the
 * "http://www.oscarmcmaster.org/ckd" XML namespace, providing thread-safe access
 * to both issues and search text components that are essential for comprehensive
 * CKD patient care and medical history tracking.
 *
 * Key functionality includes:
 * - Issues management for tracking patient medical problems and conditions
 * - Search text pattern management for automated medical history identification
 * - Thread-safe operations for concurrent access
 * - Integration with CKD configuration workflows
 *
 * @see Hx
 * @see Issues
 * @see SearchText
 * @since 2010-01-01
 */
public class HxImpl extends XmlComplexContentImpl implements Hx
{
    private static final long serialVersionUID = 1L;
    private static final QName ISSUES$0;
    private static final QName SEARCHTEXT$2;
    
    /**
     * Constructs a new HxImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this medical history element
     */
    public HxImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves the issues configuration for medical history tracking.
     *
     * @return Issues the issues configuration containing patient medical problems and conditions, or null if not set
     */
    public Issues getIssues() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Issues target = null;
            target = (Issues)this.get_store().find_element_user(HxImpl.ISSUES$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }
    
    /**
     * Sets the issues configuration for medical history tracking.
     *
     * @param issues Issues the issues configuration containing patient medical problems and conditions to set
     */
    public void setIssues(final Issues issues) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Issues target = null;
            target = (Issues)this.get_store().find_element_user(HxImpl.ISSUES$0, 0);
            if (target == null) {
                target = (Issues)this.get_store().add_element_user(HxImpl.ISSUES$0);
            }
            target.set((XmlObject)issues);
        }
    }
    
    /**
     * Creates and adds a new issues configuration element for medical history tracking.
     *
     * @return Issues the newly created issues configuration
     */
    public Issues addNewIssues() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Issues target = null;
            target = (Issues)this.get_store().add_element_user(HxImpl.ISSUES$0);
            return target;
        }
    }
    
    /**
     * Retrieves the search text configuration for automated medical history identification.
     *
     * @return SearchText the search text configuration containing patterns for medical history search, or null if not set
     */
    public SearchText getSearchtext() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SearchText target = null;
            target = (SearchText)this.get_store().find_element_user(HxImpl.SEARCHTEXT$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }
    
    /**
     * Sets the search text configuration for automated medical history identification.
     *
     * @param searchtext SearchText the search text configuration containing patterns for medical history search to set
     */
    public void setSearchtext(final SearchText searchtext) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SearchText target = null;
            target = (SearchText)this.get_store().find_element_user(HxImpl.SEARCHTEXT$2, 0);
            if (target == null) {
                target = (SearchText)this.get_store().add_element_user(HxImpl.SEARCHTEXT$2);
            }
            target.set((XmlObject)searchtext);
        }
    }
    
    /**
     * Creates and adds a new search text configuration element for automated medical history identification.
     *
     * @return SearchText the newly created search text configuration
     */
    public SearchText addNewSearchtext() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SearchText target = null;
            target = (SearchText)this.get_store().add_element_user(HxImpl.SEARCHTEXT$2);
            return target;
        }
    }
    
    static {
        // Initialize QName constants for XML element identification within CKD namespace
        ISSUES$0 = new QName("http://www.oscarmcmaster.org/ckd", "issues");
        SEARCHTEXT$2 = new QName("http://www.oscarmcmaster.org/ckd", "searchtext");
    }
}
