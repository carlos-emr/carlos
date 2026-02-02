package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlInt;
import ca.openosp.openo.ar2005.DatingMethods;
import org.apache.xmlbeans.XmlString;
import ca.openosp.openo.ar2005.YesNoNullType;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlDate;
import org.apache.xmlbeans.SimpleValue;
import java.util.Calendar;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.PregnancyHistory;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Implementation of the PregnancyHistory interface for the BC Antenatal Record (AR2005) form.
 *
 * This class provides XML binding implementation for storing and managing pregnancy history information
 * in the British Columbia Antenatal Record system. It captures critical obstetric data including:
 * <ul>
 * <li>Last Menstrual Period (LMP) and menstrual cycle information</li>
 * <li>Contraceptive use history</li>
 * <li>Estimated Delivery Date (EDB) calculations using various dating methods</li>
 * <li>Gravidity and parity information (GTPAL - Gravida, Term, Premature, Abortuses, Living)</li>
 * </ul>
 *
 * The implementation extends Apache XMLBeans XmlComplexContentImpl to provide XML serialization
 * and deserialization capabilities for the AR2005 schema. All methods are thread-safe through
 * synchronization on the internal monitor object.
 *
 * @see ca.openosp.openo.ar2005.PregnancyHistory
 * @see ca.openosp.openo.ar2005.DatingMethods
 * @see ca.openosp.openo.ar2005.YesNoNullType
 * @since 2026-01-24
 */
public class PregnancyHistoryImpl extends XmlComplexContentImpl implements PregnancyHistory
{
    private static final long serialVersionUID = 1L;
    private static final QName LMP$0;
    private static final QName LMPCERTAIN$2;
    private static final QName MENCYCLE$4;
    private static final QName MENCYCLEREGULAR$6;
    private static final QName CONTRACEPTIVETYPE$8;
    private static final QName CONTRACEPTIVELASTUSED$10;
    private static final QName MENSTRUALEDB$12;
    private static final QName FINALEDB$14;
    private static final QName DATINGMETHODS$16;
    private static final QName GRAVIDA$18;
    private static final QName TERM$20;
    private static final QName PREMATURE$22;
    private static final QName ABORTUSES$24;
    private static final QName LIVING$26;

    /**
     * Constructs a new PregnancyHistoryImpl instance with the specified schema type.
     *
     * @param sType SchemaType the XML schema type definition for this pregnancy history element
     */
    public PregnancyHistoryImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the Last Menstrual Period (LMP) date.
     *
     * The LMP is a critical date in obstetric care used to calculate gestational age
     * and estimated delivery date. This method is thread-safe through internal synchronization.
     *
     * @return Calendar the last menstrual period date, or null if not set
     */
    public Calendar getLMP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.LMP$0, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Gets the Last Menstrual Period (LMP) date as an XmlDate object.
     *
     * This method provides access to the XML representation of the LMP date,
     * allowing for XML-specific operations and metadata access.
     *
     * @return XmlDate the XML representation of the last menstrual period date, or null if not set
     */
    public XmlDate xgetLMP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.LMP$0, 0);
            return target;
        }
    }

    /**
     * Checks if the Last Menstrual Period (LMP) date is explicitly set to nil.
     *
     * In XML schema, nil indicates that the element exists but has no value (different from
     * not being set at all). This is useful for distinguishing between "unknown" and "not applicable".
     *
     * @return boolean true if the LMP is explicitly nil, false otherwise
     */
    public boolean isNilLMP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.LMP$0, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the Last Menstrual Period (LMP) date.
     *
     * This method stores the LMP date which is fundamental for calculating gestational age
     * and estimated delivery date in obstetric care.
     *
     * @param lmp Calendar the last menstrual period date to set
     */
    public void setLMP(final Calendar lmp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.LMP$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.LMP$0);
            }
            target.setCalendarValue(lmp);
        }
    }

    /**
     * Sets the Last Menstrual Period (LMP) date using an XmlDate object.
     *
     * This method allows setting the LMP using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param lmp XmlDate the XML representation of the last menstrual period date to set
     */
    public void xsetLMP(final XmlDate lmp) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.LMP$0, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(PregnancyHistoryImpl.LMP$0);
            }
            target.set((XmlObject)lmp);
        }
    }

    /**
     * Sets the Last Menstrual Period (LMP) date to nil.
     *
     * This method explicitly marks the LMP as having no value, which is semantically
     * different from not setting it at all. Useful for indicating "not applicable" or
     * "intentionally blank" states.
     */
    public void setNilLMP() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.LMP$0, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(PregnancyHistoryImpl.LMP$0);
            }
            target.setNil();
        }
    }

    /**
     * Gets the certainty status of the Last Menstrual Period (LMP) date.
     *
     * This information is clinically important as it affects the reliability of
     * gestational age calculations and estimated delivery dates based on LMP.
     *
     * @return YesNoNullType the certainty status (Yes/No/Null), or null if not set
     */
    public YesNoNullType getLMPCertain() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PregnancyHistoryImpl.LMPCERTAIN$2, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the certainty status of the Last Menstrual Period (LMP) date.
     *
     * Recording LMP certainty helps healthcare providers assess the reliability of
     * menstrual dating and determine whether additional dating methods are needed.
     *
     * @param lmpCertain YesNoNullType the certainty status to set (Yes/No/Null)
     */
    public void setLMPCertain(final YesNoNullType lmpCertain) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PregnancyHistoryImpl.LMPCERTAIN$2, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PregnancyHistoryImpl.LMPCERTAIN$2);
            }
            target.set((XmlObject)lmpCertain);
        }
    }

    /**
     * Creates and adds a new LMPCertain element to the XML structure.
     *
     * This factory method creates a new YesNoNullType instance for storing LMP certainty
     * and adds it to the XML document tree.
     *
     * @return YesNoNullType the newly created LMP certainty element
     */
    public YesNoNullType addNewLMPCertain() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PregnancyHistoryImpl.LMPCERTAIN$2);
            return target;
        }
    }

    /**
     * Gets the menstrual cycle length information.
     *
     * The menstrual cycle length (typically recorded in days) is used in conjunction with
     * the LMP to calculate gestational age and estimated delivery dates.
     *
     * @return String the menstrual cycle length, or null if not set
     */
    public String getMenCycle() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.MENCYCLE$4, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the menstrual cycle length as an XmlString object.
     *
     * This method provides access to the XML representation of the menstrual cycle length.
     *
     * @return XmlString the XML representation of the menstrual cycle length, or null if not set
     */
    public XmlString xgetMenCycle() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PregnancyHistoryImpl.MENCYCLE$4, 0);
            return target;
        }
    }

    /**
     * Sets the menstrual cycle length.
     *
     * This value (typically in days) is important for accurate gestational age calculation,
     * especially when the cycle deviates from the standard 28-day assumption.
     *
     * @param menCycle String the menstrual cycle length to set
     */
    public void setMenCycle(final String menCycle) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.MENCYCLE$4, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.MENCYCLE$4);
            }
            target.setStringValue(menCycle);
        }
    }

    /**
     * Sets the menstrual cycle length using an XmlString object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param menCycle XmlString the XML representation of the menstrual cycle length to set
     */
    public void xsetMenCycle(final XmlString menCycle) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PregnancyHistoryImpl.MENCYCLE$4, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PregnancyHistoryImpl.MENCYCLE$4);
            }
            target.set((XmlObject)menCycle);
        }
    }

    /**
     * Gets the regularity status of the menstrual cycle.
     *
     * Menstrual cycle regularity affects the reliability of LMP-based dating.
     * Irregular cycles may indicate the need for ultrasound dating.
     *
     * @return YesNoNullType the regularity status (Yes/No/Null), or null if not set
     */
    public YesNoNullType getMenCycleRegular() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PregnancyHistoryImpl.MENCYCLEREGULAR$6, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the regularity status of the menstrual cycle.
     *
     * Recording whether cycles are regular helps assess the accuracy of menstrual dating
     * and guides decisions about additional dating methods.
     *
     * @param menCycleRegular YesNoNullType the regularity status to set (Yes/No/Null)
     */
    public void setMenCycleRegular(final YesNoNullType menCycleRegular) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().find_element_user(PregnancyHistoryImpl.MENCYCLEREGULAR$6, 0);
            if (target == null) {
                target = (YesNoNullType)this.get_store().add_element_user(PregnancyHistoryImpl.MENCYCLEREGULAR$6);
            }
            target.set((XmlObject)menCycleRegular);
        }
    }

    /**
     * Creates and adds a new MenCycleRegular element to the XML structure.
     *
     * This factory method creates a new YesNoNullType instance for storing menstrual
     * cycle regularity status and adds it to the XML document tree.
     *
     * @return YesNoNullType the newly created menstrual cycle regularity element
     */
    public YesNoNullType addNewMenCycleRegular() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            YesNoNullType target = null;
            target = (YesNoNullType)this.get_store().add_element_user(PregnancyHistoryImpl.MENCYCLEREGULAR$6);
            return target;
        }
    }

    /**
     * Gets the type of contraception previously used.
     *
     * Contraceptive history is relevant for pregnancy dating and risk assessment,
     * particularly for hormonal contraceptives that may affect cycle regularity.
     *
     * @return String the contraceptive type, or null if not set
     */
    public String getContraceptiveType() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVETYPE$8, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the contraceptive type as an XmlString object.
     *
     * This method provides access to the XML representation of the contraceptive type.
     *
     * @return XmlString the XML representation of the contraceptive type, or null if not set
     */
    public XmlString xgetContraceptiveType() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVETYPE$8, 0);
            return target;
        }
    }

    /**
     * Checks if the contraceptive type has been set.
     *
     * @return boolean true if contraceptive type is set, false otherwise
     */
    public boolean isSetContraceptiveType() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(PregnancyHistoryImpl.CONTRACEPTIVETYPE$8) != 0;
        }
    }

    /**
     * Sets the type of contraception previously used.
     *
     * This information is important for understanding conception timing and assessing
     * any potential effects on early pregnancy.
     *
     * @param contraceptiveType String the contraceptive type to set
     */
    public void setContraceptiveType(final String contraceptiveType) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVETYPE$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.CONTRACEPTIVETYPE$8);
            }
            target.setStringValue(contraceptiveType);
        }
    }

    /**
     * Sets the contraceptive type using an XmlString object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param contraceptiveType XmlString the XML representation of the contraceptive type to set
     */
    public void xsetContraceptiveType(final XmlString contraceptiveType) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVETYPE$8, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PregnancyHistoryImpl.CONTRACEPTIVETYPE$8);
            }
            target.set((XmlObject)contraceptiveType);
        }
    }

    /**
     * Removes the contraceptive type element from the XML structure.
     *
     * This method completely removes the contraceptive type element, which is
     * different from setting it to null or empty.
     */
    public void unsetContraceptiveType() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(PregnancyHistoryImpl.CONTRACEPTIVETYPE$8, 0);
        }
    }

    /**
     * Gets the date when contraception was last used.
     *
     * This date is relevant for understanding the timing of conception and assessing
     * any potential effects of recent contraceptive use on the pregnancy.
     *
     * @return Calendar the date contraception was last used, or null if not set
     */
    public Calendar getContraceptiveLastUsed() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Gets the contraceptive last used date as an XmlDate object.
     *
     * This method provides access to the XML representation of the date.
     *
     * @return XmlDate the XML representation of the contraceptive last used date, or null if not set
     */
    public XmlDate xgetContraceptiveLastUsed() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10, 0);
            return target;
        }
    }

    /**
     * Checks if the contraceptive last used date is explicitly set to nil.
     *
     * @return boolean true if the date is explicitly nil, false otherwise
     */
    public boolean isNilContraceptiveLastUsed() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the date when contraception was last used.
     *
     * This information helps healthcare providers understand the conception timeline
     * and assess any potential implications for the pregnancy.
     *
     * @param contraceptiveLastUsed Calendar the date contraception was last used
     */
    public void setContraceptiveLastUsed(final Calendar contraceptiveLastUsed) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10);
            }
            target.setCalendarValue(contraceptiveLastUsed);
        }
    }

    /**
     * Sets the contraceptive last used date using an XmlDate object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param contraceptiveLastUsed XmlDate the XML representation of the contraceptive last used date
     */
    public void xsetContraceptiveLastUsed(final XmlDate contraceptiveLastUsed) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10);
            }
            target.set((XmlObject)contraceptiveLastUsed);
        }
    }

    /**
     * Sets the contraceptive last used date to nil.
     *
     * This method explicitly marks the date as having no value.
     */
    public void setNilContraceptiveLastUsed() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(PregnancyHistoryImpl.CONTRACEPTIVELASTUSED$10);
            }
            target.setNil();
        }
    }

    /**
     * Gets the Estimated Date of Birth (EDB) calculated using menstrual dating.
     *
     * This EDB is calculated from the LMP and menstrual cycle information using
     * Naegele's rule or similar menstrual-based calculations.
     *
     * @return Calendar the menstrual EDB, or null if not set
     */
    public Calendar getMenstrualEDB() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Gets the menstrual EDB as an XmlDate object.
     *
     * This method provides access to the XML representation of the menstrual EDB.
     *
     * @return XmlDate the XML representation of the menstrual EDB, or null if not set
     */
    public XmlDate xgetMenstrualEDB() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12, 0);
            return target;
        }
    }

    /**
     * Checks if the menstrual EDB is explicitly set to nil.
     *
     * @return boolean true if the menstrual EDB is explicitly nil, false otherwise
     */
    public boolean isNilMenstrualEDB() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12, 0);
            return target != null && target.isNil();
        }
    }

    /**
     * Sets the Estimated Date of Birth (EDB) calculated using menstrual dating.
     *
     * This EDB serves as one of several dating methods that may be used to determine
     * the final EDB for the pregnancy.
     *
     * @param menstrualEDB Calendar the menstrual EDB to set
     */
    public void setMenstrualEDB(final Calendar menstrualEDB) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12);
            }
            target.setCalendarValue(menstrualEDB);
        }
    }

    /**
     * Sets the menstrual EDB using an XmlDate object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param menstrualEDB XmlDate the XML representation of the menstrual EDB to set
     */
    public void xsetMenstrualEDB(final XmlDate menstrualEDB) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12);
            }
            target.set((XmlObject)menstrualEDB);
        }
    }

    /**
     * Sets the menstrual EDB to nil.
     *
     * This method explicitly marks the menstrual EDB as having no value.
     */
    public void setNilMenstrualEDB() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(PregnancyHistoryImpl.MENSTRUALEDB$12);
            }
            target.setNil();
        }
    }

    /**
     * Gets the final Estimated Date of Birth (EDB) for the pregnancy.
     *
     * The final EDB is determined by the healthcare provider based on all available dating
     * methods (menstrual dating, ultrasound measurements, clinical examination) and represents
     * the official due date used for pregnancy management.
     *
     * @return Calendar the final EDB, or null if not set
     */
    public Calendar getFinalEDB() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.FINALEDB$14, 0);
            if (target == null) {
                return null;
            }
            return target.getCalendarValue();
        }
    }

    /**
     * Gets the final EDB as an XmlDate object.
     *
     * This method provides access to the XML representation of the final EDB.
     *
     * @return XmlDate the XML representation of the final EDB, or null if not set
     */
    public XmlDate xgetFinalEDB() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.FINALEDB$14, 0);
            return target;
        }
    }

    /**
     * Sets the final Estimated Date of Birth (EDB) for the pregnancy.
     *
     * This is the clinically determined due date used for all pregnancy management
     * decisions and is based on the most reliable dating information available.
     *
     * @param finalEDB Calendar the final EDB to set
     */
    public void setFinalEDB(final Calendar finalEDB) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.FINALEDB$14, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.FINALEDB$14);
            }
            target.setCalendarValue(finalEDB);
        }
    }

    /**
     * Sets the final EDB using an XmlDate object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param finalEDB XmlDate the XML representation of the final EDB to set
     */
    public void xsetFinalEDB(final XmlDate finalEDB) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlDate target = null;
            target = (XmlDate)this.get_store().find_element_user(PregnancyHistoryImpl.FINALEDB$14, 0);
            if (target == null) {
                target = (XmlDate)this.get_store().add_element_user(PregnancyHistoryImpl.FINALEDB$14);
            }
            target.set((XmlObject)finalEDB);
        }
    }

    /**
     * Gets the dating methods used to determine gestational age and EDB.
     *
     * Multiple dating methods may be used in obstetric care, including menstrual dating,
     * ultrasound measurements, and clinical examination. This element documents which
     * methods were employed.
     *
     * @return DatingMethods the dating methods information, or null if not set
     */
    public DatingMethods getDatingMethods() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DatingMethods target = null;
            target = (DatingMethods)this.get_store().find_element_user(PregnancyHistoryImpl.DATINGMETHODS$16, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the dating methods used to determine gestational age and EDB.
     *
     * Documenting the dating methods provides important context for understanding
     * the reliability and basis of the estimated due date.
     *
     * @param datingMethods DatingMethods the dating methods information to set
     */
    public void setDatingMethods(final DatingMethods datingMethods) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DatingMethods target = null;
            target = (DatingMethods)this.get_store().find_element_user(PregnancyHistoryImpl.DATINGMETHODS$16, 0);
            if (target == null) {
                target = (DatingMethods)this.get_store().add_element_user(PregnancyHistoryImpl.DATINGMETHODS$16);
            }
            target.set((XmlObject)datingMethods);
        }
    }

    /**
     * Creates and adds a new DatingMethods element to the XML structure.
     *
     * This factory method creates a new DatingMethods instance and adds it to the
     * XML document tree for recording pregnancy dating information.
     *
     * @return DatingMethods the newly created dating methods element
     */
    public DatingMethods addNewDatingMethods() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            DatingMethods target = null;
            target = (DatingMethods)this.get_store().add_element_user(PregnancyHistoryImpl.DATINGMETHODS$16);
            return target;
        }
    }

    /**
     * Gets the gravida count (total number of pregnancies).
     *
     * Gravida is the first component of the GTPAL system for recording obstetric history.
     * It represents the total number of times the patient has been pregnant, including
     * the current pregnancy.
     *
     * @return int the gravida count, or 0 if not set
     */
    public int getGravida() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.GRAVIDA$18, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the gravida count as an XmlInt object.
     *
     * This method provides access to the XML representation of the gravida count.
     *
     * @return XmlInt the XML representation of the gravida count, or null if not set
     */
    public XmlInt xgetGravida() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.GRAVIDA$18, 0);
            return target;
        }
    }

    /**
     * Sets the gravida count (total number of pregnancies).
     *
     * This value is fundamental to obstetric risk assessment and care planning.
     *
     * @param gravida int the gravida count to set
     */
    public void setGravida(final int gravida) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.GRAVIDA$18, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.GRAVIDA$18);
            }
            target.setIntValue(gravida);
        }
    }

    /**
     * Sets the gravida count using an XmlInt object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param gravida XmlInt the XML representation of the gravida count to set
     */
    public void xsetGravida(final XmlInt gravida) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.GRAVIDA$18, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(PregnancyHistoryImpl.GRAVIDA$18);
            }
            target.set((XmlObject)gravida);
        }
    }

    /**
     * Gets the count of term pregnancies (delivered at or after 37 weeks gestation).
     *
     * Term is the second component of the GTPAL system. It represents the number of
     * pregnancies that reached full term and resulted in delivery.
     *
     * @return int the term pregnancy count, or 0 if not set
     */
    public int getTerm() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.TERM$20, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the term pregnancy count as an XmlInt object.
     *
     * This method provides access to the XML representation of the term count.
     *
     * @return XmlInt the XML representation of the term pregnancy count, or null if not set
     */
    public XmlInt xgetTerm() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.TERM$20, 0);
            return target;
        }
    }

    /**
     * Sets the count of term pregnancies.
     *
     * This information is important for assessing obstetric risk and previous pregnancy outcomes.
     *
     * @param term int the term pregnancy count to set
     */
    public void setTerm(final int term) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.TERM$20, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.TERM$20);
            }
            target.setIntValue(term);
        }
    }

    /**
     * Sets the term pregnancy count using an XmlInt object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param term XmlInt the XML representation of the term pregnancy count to set
     */
    public void xsetTerm(final XmlInt term) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.TERM$20, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(PregnancyHistoryImpl.TERM$20);
            }
            target.set((XmlObject)term);
        }
    }

    /**
     * Gets the count of premature deliveries (before 37 weeks gestation).
     *
     * Premature is the third component of the GTPAL system. It represents the number of
     * pregnancies that resulted in premature birth.
     *
     * @return int the premature delivery count, or 0 if not set
     */
    public int getPremature() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.PREMATURE$22, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the premature delivery count as an XmlInt object.
     *
     * This method provides access to the XML representation of the premature delivery count.
     *
     * @return XmlInt the XML representation of the premature delivery count, or null if not set
     */
    public XmlInt xgetPremature() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.PREMATURE$22, 0);
            return target;
        }
    }

    /**
     * Sets the count of premature deliveries.
     *
     * History of premature delivery is a significant risk factor for preterm birth in
     * subsequent pregnancies and affects care planning.
     *
     * @param premature int the premature delivery count to set
     */
    public void setPremature(final int premature) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.PREMATURE$22, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.PREMATURE$22);
            }
            target.setIntValue(premature);
        }
    }

    /**
     * Sets the premature delivery count using an XmlInt object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param premature XmlInt the XML representation of the premature delivery count to set
     */
    public void xsetPremature(final XmlInt premature) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.PREMATURE$22, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(PregnancyHistoryImpl.PREMATURE$22);
            }
            target.set((XmlObject)premature);
        }
    }

    /**
     * Gets the count of abortuses (miscarriages and induced abortions).
     *
     * Abortuses is the fourth component of the GTPAL system. It represents the total number
     * of pregnancy losses before viability, including both spontaneous and induced abortions.
     *
     * @return int the abortus count, or 0 if not set
     */
    public int getAbortuses() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.ABORTUSES$24, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the abortus count as an XmlInt object.
     *
     * This method provides access to the XML representation of the abortus count.
     *
     * @return XmlInt the XML representation of the abortus count, or null if not set
     */
    public XmlInt xgetAbortuses() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.ABORTUSES$24, 0);
            return target;
        }
    }

    /**
     * Sets the count of abortuses.
     *
     * This information is important for assessing pregnancy risk, particularly for
     * recurrent pregnancy loss and related complications.
     *
     * @param abortuses int the abortus count to set
     */
    public void setAbortuses(final int abortuses) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.ABORTUSES$24, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.ABORTUSES$24);
            }
            target.setIntValue(abortuses);
        }
    }

    /**
     * Sets the abortus count using an XmlInt object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param abortuses XmlInt the XML representation of the abortus count to set
     */
    public void xsetAbortuses(final XmlInt abortuses) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.ABORTUSES$24, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(PregnancyHistoryImpl.ABORTUSES$24);
            }
            target.set((XmlObject)abortuses);
        }
    }

    /**
     * Gets the count of living children.
     *
     * Living is the fifth component of the GTPAL system. It represents the number of
     * children currently living from all previous pregnancies.
     *
     * @return int the living children count, or 0 if not set
     */
    public int getLiving() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.LIVING$26, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the living children count as an XmlInt object.
     *
     * This method provides access to the XML representation of the living children count.
     *
     * @return XmlInt the XML representation of the living children count, or null if not set
     */
    public XmlInt xgetLiving() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.LIVING$26, 0);
            return target;
        }
    }

    /**
     * Sets the count of living children.
     *
     * This completes the GTPAL obstetric history documentation and provides important
     * context for family planning and pregnancy care.
     *
     * @param living int the living children count to set
     */
    public void setLiving(final int living) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PregnancyHistoryImpl.LIVING$26, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PregnancyHistoryImpl.LIVING$26);
            }
            target.setIntValue(living);
        }
    }

    /**
     * Sets the living children count using an XmlInt object.
     *
     * This method allows setting the value using the XML representation, preserving
     * XML-specific metadata and attributes.
     *
     * @param living XmlInt the XML representation of the living children count to set
     */
    public void xsetLiving(final XmlInt living) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlInt target = null;
            target = (XmlInt)this.get_store().find_element_user(PregnancyHistoryImpl.LIVING$26, 0);
            if (target == null) {
                target = (XmlInt)this.get_store().add_element_user(PregnancyHistoryImpl.LIVING$26);
            }
            target.set((XmlObject)living);
        }
    }
    
    static {
        LMP$0 = new QName("http://www.oscarmcmaster.org/AR2005", "LMP");
        LMPCERTAIN$2 = new QName("http://www.oscarmcmaster.org/AR2005", "LMPCertain");
        MENCYCLE$4 = new QName("http://www.oscarmcmaster.org/AR2005", "menCycle");
        MENCYCLEREGULAR$6 = new QName("http://www.oscarmcmaster.org/AR2005", "menCycleRegular");
        CONTRACEPTIVETYPE$8 = new QName("http://www.oscarmcmaster.org/AR2005", "contraceptiveType");
        CONTRACEPTIVELASTUSED$10 = new QName("http://www.oscarmcmaster.org/AR2005", "contraceptiveLastUsed");
        MENSTRUALEDB$12 = new QName("http://www.oscarmcmaster.org/AR2005", "menstrualEDB");
        FINALEDB$14 = new QName("http://www.oscarmcmaster.org/AR2005", "finalEDB");
        DATINGMETHODS$16 = new QName("http://www.oscarmcmaster.org/AR2005", "datingMethods");
        GRAVIDA$18 = new QName("http://www.oscarmcmaster.org/AR2005", "gravida");
        TERM$20 = new QName("http://www.oscarmcmaster.org/AR2005", "term");
        PREMATURE$22 = new QName("http://www.oscarmcmaster.org/AR2005", "premature");
        ABORTUSES$24 = new QName("http://www.oscarmcmaster.org/AR2005", "abortuses");
        LIVING$26 = new QName("http://www.oscarmcmaster.org/AR2005", "living");
    }
}
