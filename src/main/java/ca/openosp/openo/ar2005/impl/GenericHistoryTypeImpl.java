package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import ca.openosp.openo.ar2005.YesNoNullType;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.GenericHistoryType;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation class for GenericHistoryType XML schema type used in the AR2005 (Antenatal Record 2005) healthcare forms.
 *
 * This class provides XMLBeans-based implementation for managing genetic and developmental history information
 * in antenatal care records. It handles five key health risk indicators commonly tracked during pregnancy:
 * at-risk status, developmental delays, congenital anomalies, chromosomal disorders, and genetic disorders.
 *
 * The implementation extends XmlComplexContentImpl to provide XML serialization/deserialization capabilities
 * and follows the AR2005 XML schema namespace (http://www.oscarmcmaster.org/AR2005). Each health indicator
 * is represented as a YesNoNullType element, allowing for tri-state values (yes, no, or null/unknown).
 *
 * This class is part of the British Columbia Antenatal Record (BCAR) forms integration used for
 * comprehensive pregnancy care documentation in OpenO EMR.
 *
 * @see ca.openosp.openo.ar2005.GenericHistoryType
 * @see ca.openosp.openo.ar2005.YesNoNullType
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-23
 */
public class GenericHistoryTypeImpl extends XmlComplexContentImpl implements GenericHistoryType
{
    private static final long serialVersionUID = 1L;
    private static final QName ATRISK$0;
    private static final QName DEVELOPMENTALDELAY$2;
    private static final QName CONGENITALANOMOLIES$4;
    private static final QName CHROMOSOMALDISORDERS$6;
    private static final QName GENETICDISORDERS$8;

    /**
     * Constructs a new GenericHistoryTypeImpl instance with the specified XMLBeans schema type.
     *
     * This constructor initializes the XML complex content implementation with the provided schema type,
     * enabling proper XML serialization and validation according to the AR2005 schema definition.
     *
     * @param sType SchemaType the XMLBeans schema type definition for GenericHistoryType
     */
    public GenericHistoryTypeImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the at-risk status indicator for the patient's antenatal history.
     *
     * This method returns the at-risk flag which indicates whether the patient has been identified
     * as having elevated pregnancy risk factors requiring additional monitoring or interventions.
     *
     * @return YesNoNullType the at-risk status (yes, no, or null if not specified)
     */
    public YesNoNullType getAtRisk() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.ATRISK$0, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the at-risk status indicator for the patient's antenatal history.
     *
     * This method updates the at-risk flag to indicate whether the patient has been identified
     * as having elevated pregnancy risk factors. The value persists in the XML store.
     *
     * @param atRisk YesNoNullType the at-risk status to set (yes, no, or null)
     */
    public void setAtRisk(final YesNoNullType atRisk) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.ATRISK$0, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.ATRISK$0);
            }
            target.set((XmlObject)atRisk);
        }
    }

    /**
     * Creates and adds a new at-risk status element to the XML store.
     *
     * This method creates a new YesNoNullType element for the at-risk indicator and adds it
     * to the internal XML store. The newly created element is returned for immediate configuration.
     *
     * @return YesNoNullType the newly created at-risk status element
     */
    public YesNoNullType addNewAtRisk() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.ATRISK$0);
            return target;
        }
    }

    /**
     * Retrieves the developmental delay indicator for the patient's antenatal history.
     *
     * This method returns the developmental delay flag which indicates whether there is a history
     * or risk of developmental delays that may affect pregnancy care or neonatal outcomes.
     *
     * @return YesNoNullType the developmental delay status (yes, no, or null if not specified)
     */
    public YesNoNullType getDevelopmentalDelay() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.DEVELOPMENTALDELAY$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the developmental delay indicator for the patient's antenatal history.
     *
     * This method updates the developmental delay flag to document any history or risk
     * of developmental delays. The value persists in the XML store.
     *
     * @param developmentalDelay YesNoNullType the developmental delay status to set (yes, no, or null)
     */
    public void setDevelopmentalDelay(final YesNoNullType developmentalDelay) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.DEVELOPMENTALDELAY$2, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.DEVELOPMENTALDELAY$2);
            }
            target.set((XmlObject)developmentalDelay);
        }
    }

    /**
     * Creates and adds a new developmental delay element to the XML store.
     *
     * This method creates a new YesNoNullType element for the developmental delay indicator
     * and adds it to the internal XML store. The newly created element is returned for immediate configuration.
     *
     * @return YesNoNullType the newly created developmental delay element
     */
    public YesNoNullType addNewDevelopmentalDelay() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.DEVELOPMENTALDELAY$2);
            return target;
        }
    }

    /**
     * Retrieves the congenital anomalies indicator for the patient's antenatal history.
     *
     * This method returns the congenital anomalies flag which indicates whether there is a history
     * or risk of birth defects or structural abnormalities present from birth.
     *
     * @return YesNoNullType the congenital anomalies status (yes, no, or null if not specified)
     */
    public YesNoNullType getCongenitalAnomolies() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.CONGENITALANOMOLIES$4, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the congenital anomalies indicator for the patient's antenatal history.
     *
     * This method updates the congenital anomalies flag to document any history or risk
     * of birth defects or structural abnormalities. The value persists in the XML store.
     *
     * @param congenitalAnomolies YesNoNullType the congenital anomalies status to set (yes, no, or null)
     */
    public void setCongenitalAnomolies(final YesNoNullType congenitalAnomolies) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.CONGENITALANOMOLIES$4, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.CONGENITALANOMOLIES$4);
            }
            target.set((XmlObject)congenitalAnomolies);
        }
    }

    /**
     * Creates and adds a new congenital anomalies element to the XML store.
     *
     * This method creates a new YesNoNullType element for the congenital anomalies indicator
     * and adds it to the internal XML store. The newly created element is returned for immediate configuration.
     *
     * @return YesNoNullType the newly created congenital anomalies element
     */
    public YesNoNullType addNewCongenitalAnomolies() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.CONGENITALANOMOLIES$4);
            return target;
        }
    }

    /**
     * Retrieves the chromosomal disorders indicator for the patient's antenatal history.
     *
     * This method returns the chromosomal disorders flag which indicates whether there is a history
     * or risk of chromosomal abnormalities such as Down syndrome, Turner syndrome, or other karyotype abnormalities.
     *
     * @return YesNoNullType the chromosomal disorders status (yes, no, or null if not specified)
     */
    public YesNoNullType getChromosomalDisorders() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.CHROMOSOMALDISORDERS$6, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the chromosomal disorders indicator for the patient's antenatal history.
     *
     * This method updates the chromosomal disorders flag to document any history or risk
     * of chromosomal abnormalities. The value persists in the XML store.
     *
     * @param chromosomalDisorders YesNoNullType the chromosomal disorders status to set (yes, no, or null)
     */
    public void setChromosomalDisorders(final YesNoNullType chromosomalDisorders) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.CHROMOSOMALDISORDERS$6, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.CHROMOSOMALDISORDERS$6);
            }
            target.set((XmlObject)chromosomalDisorders);
        }
    }

    /**
     * Creates and adds a new chromosomal disorders element to the XML store.
     *
     * This method creates a new YesNoNullType element for the chromosomal disorders indicator
     * and adds it to the internal XML store. The newly created element is returned for immediate configuration.
     *
     * @return YesNoNullType the newly created chromosomal disorders element
     */
    public YesNoNullType addNewChromosomalDisorders() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.CHROMOSOMALDISORDERS$6);
            return target;
        }
    }

    /**
     * Retrieves the genetic disorders indicator for the patient's antenatal history.
     *
     * This method returns the genetic disorders flag which indicates whether there is a family history
     * or risk of inherited genetic conditions that may affect pregnancy or fetal development.
     *
     * @return YesNoNullType the genetic disorders status (yes, no, or null if not specified)
     */
    public YesNoNullType getGeneticDisorders() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.GENETICDISORDERS$8, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the genetic disorders indicator for the patient's antenatal history.
     *
     * This method updates the genetic disorders flag to document any family history or risk
     * of inherited genetic conditions. The value persists in the XML store.
     *
     * @param geneticDisorders YesNoNullType the genetic disorders status to set (yes, no, or null)
     */
    public void setGeneticDisorders(final YesNoNullType geneticDisorders) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(GenericHistoryTypeImpl.GENETICDISORDERS$8, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.GENETICDISORDERS$8);
            }
            target.set((XmlObject)geneticDisorders);
        }
    }

    /**
     * Creates and adds a new genetic disorders element to the XML store.
     *
     * This method creates a new YesNoNullType element for the genetic disorders indicator
     * and adds it to the internal XML store. The newly created element is returned for immediate configuration.
     *
     * @return YesNoNullType the newly created genetic disorders element
     */
    public YesNoNullType addNewGeneticDisorders() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(GenericHistoryTypeImpl.GENETICDISORDERS$8);
            return target;
        }
    }
    
    static {
        ATRISK$0 = new QName("http://www.oscarmcmaster.org/AR2005", "atRisk");
        DEVELOPMENTALDELAY$2 = new QName("http://www.oscarmcmaster.org/AR2005", "developmentalDelay");
        CONGENITALANOMOLIES$4 = new QName("http://www.oscarmcmaster.org/AR2005", "congenitalAnomolies");
        CHROMOSOMALDISORDERS$6 = new QName("http://www.oscarmcmaster.org/AR2005", "chromosomalDisorders");
        GENETICDISORDERS$8 = new QName("http://www.oscarmcmaster.org/AR2005", "geneticDisorders");
    }
}
