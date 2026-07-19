package io.github.carlos_emr.carlos.ckd.impl;

import io.github.carlos_emr.carlos.ckd.Excludes;
import io.github.carlos_emr.carlos.ckd.Drugs;
import io.github.carlos_emr.carlos.ckd.Hx;
import io.github.carlos_emr.carlos.ckd.Bp;
import org.apache.xmlbeans.XmlObject;
import io.github.carlos_emr.carlos.ckd.DxCodes;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.CKDConfig;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link CKDConfig} interface.
 *
 * This class provides the concrete implementation for managing comprehensive CKD
 * (Chronic Kidney Disease) configuration data within OpenO EMR. It serves as the
 * main container for all CKD-related configuration elements including diagnosis codes,
 * blood pressure settings, medical history, drugs, and exclusion criteria.
 *
 * The implementation handles XML serialization and deserialization of complex CKD
 * configuration documents within the "http://www.oscarmcmaster.org/ckd" namespace,
 * providing thread-safe access to all nested configuration elements.
 *
 * Key configuration elements managed:
 * - Diagnosis codes (DxCodes) for CKD identification
 * - Blood pressure (Bp) thresholds and monitoring criteria
 * - Medical history (Hx) including issues and search text patterns
 * - Drug/medication (Drugs) lists and interactions
 * - Exclusion (Excludes) criteria for patient filtering
 *
 * @see CKDConfig
 * @since 2010-01-01
 */
public class CKDConfigImpl extends XmlComplexContentImpl implements CKDConfig
{
    private static final long serialVersionUID = 1L;
    private static final QName DXCODES$0;
    private static final QName BP$2;
    private static final QName HX$4;
    private static final QName DRUGS$6;
    private static final QName EXCLUDES$8;
    
    /**
     * Constructs a new CKDConfigImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this CKD configuration
     */
    public CKDConfigImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves the diagnosis codes configuration for CKD identification.
     *
     * @return DxCodes the diagnosis codes configuration, or null if not set
     */
    public DxCodes getDxCodes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DxCodes target = null;
            target = (DxCodes)this.get_store().find_element_user(CKDConfigImpl.DXCODES$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }
    
    /**
     * Sets the diagnosis codes configuration for CKD identification.
     *
     * @param dxCodes DxCodes the diagnosis codes configuration to set
     */
    public void setDxCodes(final DxCodes dxCodes) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DxCodes target = null;
            target = (DxCodes)this.get_store().find_element_user(CKDConfigImpl.DXCODES$0, 0);
            if (target == null) {
                target = (DxCodes)this.get_store().add_element_user(CKDConfigImpl.DXCODES$0);
            }
            target.set((XmlObject)dxCodes);
        }
    }
    
    /**
     * Creates and adds a new diagnosis codes configuration element.
     *
     * @return DxCodes the newly created diagnosis codes configuration
     */
    public DxCodes addNewDxCodes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DxCodes target = null;
            target = (DxCodes)this.get_store().add_element_user(CKDConfigImpl.DXCODES$0);
            return target;
        }
    }
    
    /**
     * Retrieves the blood pressure configuration for CKD monitoring.
     *
     * @return Bp the blood pressure configuration, or null if not set
     */
    public Bp getBp() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bp target = null;
            target = (Bp)this.get_store().find_element_user(CKDConfigImpl.BP$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }
    
    /**
     * Sets the blood pressure configuration for CKD monitoring.
     *
     * @param bp Bp the blood pressure configuration to set
     */
    public void setBp(final Bp bp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bp target = null;
            target = (Bp)this.get_store().find_element_user(CKDConfigImpl.BP$2, 0);
            if (target == null) {
                target = (Bp)this.get_store().add_element_user(CKDConfigImpl.BP$2);
            }
            target.set((XmlObject)bp);
        }
    }
    
    /**
     * Creates and adds a new blood pressure configuration element.
     *
     * @return Bp the newly created blood pressure configuration
     */
    public Bp addNewBp() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Bp target = null;
            target = (Bp)this.get_store().add_element_user(CKDConfigImpl.BP$2);
            return target;
        }
    }
    
    /**
     * Retrieves the medical history configuration for CKD management.
     *
     * @return Hx the medical history configuration, or null if not set
     */
    public Hx getHx() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Hx target = null;
            target = (Hx)this.get_store().find_element_user(CKDConfigImpl.HX$4, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }
    
    /**
     * Sets the medical history configuration for CKD management.
     *
     * @param hx Hx the medical history configuration to set
     */
    public void setHx(final Hx hx) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Hx target = null;
            target = (Hx)this.get_store().find_element_user(CKDConfigImpl.HX$4, 0);
            if (target == null) {
                target = (Hx)this.get_store().add_element_user(CKDConfigImpl.HX$4);
            }
            target.set((XmlObject)hx);
        }
    }
    
    /**
     * Creates and adds a new medical history configuration element.
     *
     * @return Hx the newly created medical history configuration
     */
    public Hx addNewHx() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Hx target = null;
            target = (Hx)this.get_store().add_element_user(CKDConfigImpl.HX$4);
            return target;
        }
    }
    
    /**
     * Retrieves the drugs configuration for CKD medication management.
     *
     * @return Drugs the drugs configuration, or null if not set
     */
    public Drugs getDrugs() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Drugs target = null;
            target = (Drugs)this.get_store().find_element_user(CKDConfigImpl.DRUGS$6, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }
    
    /**
     * Checks if the drugs configuration is set.
     *
     * @return boolean true if drugs configuration exists, false otherwise
     */
    public boolean isSetDrugs() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(CKDConfigImpl.DRUGS$6) != 0;
        }
    }
    
    /**
     * Sets the drugs configuration for CKD medication management.
     *
     * @param drugs Drugs the drugs configuration to set
     */
    public void setDrugs(final Drugs drugs) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Drugs target = null;
            target = (Drugs)this.get_store().find_element_user(CKDConfigImpl.DRUGS$6, 0);
            if (target == null) {
                target = (Drugs)this.get_store().add_element_user(CKDConfigImpl.DRUGS$6);
            }
            target.set((XmlObject)drugs);
        }
    }
    
    /**
     * Creates and adds a new drugs configuration element.
     *
     * @return Drugs the newly created drugs configuration
     */
    public Drugs addNewDrugs() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Drugs target = null;
            target = (Drugs)this.get_store().add_element_user(CKDConfigImpl.DRUGS$6);
            return target;
        }
    }
    
    /**
     * Removes the drugs configuration element.
     */
    public void unsetDrugs() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(CKDConfigImpl.DRUGS$6, 0);
        }
    }
    
    /**
     * Retrieves the exclusion criteria configuration for CKD patient filtering.
     *
     * @return Excludes the exclusion criteria configuration, or null if not set
     */
    public Excludes getExcludes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Excludes target = null;
            target = (Excludes)this.get_store().find_element_user(CKDConfigImpl.EXCLUDES$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }
    
    /**
     * Checks if the exclusion criteria configuration is set.
     *
     * @return boolean true if exclusion criteria configuration exists, false otherwise
     */
    public boolean isSetExcludes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(CKDConfigImpl.EXCLUDES$8) != 0;
        }
    }
    
    /**
     * Sets the exclusion criteria configuration for CKD patient filtering.
     *
     * @param excludes Excludes the exclusion criteria configuration to set
     */
    public void setExcludes(final Excludes excludes) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Excludes target = null;
            target = (Excludes)this.get_store().find_element_user(CKDConfigImpl.EXCLUDES$8, 0);
            if (target == null) {
                target = (Excludes)this.get_store().add_element_user(CKDConfigImpl.EXCLUDES$8);
            }
            target.set((XmlObject)excludes);
        }
    }
    
    /**
     * Creates and adds a new exclusion criteria configuration element.
     *
     * @return Excludes the newly created exclusion criteria configuration
     */
    public Excludes addNewExcludes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Excludes target = null;
            target = (Excludes)this.get_store().add_element_user(CKDConfigImpl.EXCLUDES$8);
            return target;
        }
    }
    
    /**
     * Removes the exclusion criteria configuration element.
     */
    public void unsetExcludes() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(CKDConfigImpl.EXCLUDES$8, 0);
        }
    }
    
    static {
        // Initialize QName constants for XML element identification within CKD namespace
        DXCODES$0 = new QName("http://www.oscarmcmaster.org/ckd", "dx-codes");
        BP$2 = new QName("http://www.oscarmcmaster.org/ckd", "bp");
        HX$4 = new QName("http://www.oscarmcmaster.org/ckd", "hx");
        DRUGS$6 = new QName("http://www.oscarmcmaster.org/ckd", "drugs");
        EXCLUDES$8 = new QName("http://www.oscarmcmaster.org/ckd", "excludes");
    }
}
