package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.impl.values.JavaIntHolderEx;
import org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.PartnerInformation;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation for AR2005 (British Columbia Antenatal Record 2005) partner information.
 *
 * This class provides thread-safe XML data binding for partner demographic information
 * collected as part of the BC Antenatal Record form. It stores essential partner details
 * including name, occupation, education level, and age using XMLBeans store-based
 * element management with synchronized access for thread safety.
 *
 * The implementation follows the XMLBeans pattern where each field is accessed through
 * the underlying XML store with proper locking to ensure thread-safe operations in
 * concurrent healthcare data processing environments.
 *
 * @see ca.openosp.openo.ar2005.PartnerInformation
 * @see org.apache.xmlbeans.impl.values.XmlComplexContentImpl
 * @since 2026-01-24
 */
public class PartnerInformationImpl extends XmlComplexContentImpl implements PartnerInformation
{
    private static final long serialVersionUID = 1L;
    private static final QName LASTNAME$0;
    private static final QName FIRSTNAME$2;
    private static final QName OCCUPATION$4;
    private static final QName EDUCATIONLEVEL$6;
    private static final QName AGE$8;

    /**
     * Constructs a new PartnerInformationImpl instance with the specified schema type.
     *
     * This constructor initializes the XMLBeans complex content implementation with
     * the provided schema type, setting up the underlying XML store for partner
     * information data management.
     *
     * @param sType SchemaType the XMLBeans schema type definition for this element
     */
    public PartnerInformationImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Gets the partner's last name.
     *
     * Retrieves the last name value from the XML store in a thread-safe manner
     * using synchronized access to the underlying XMLBeans store.
     *
     * @return String the partner's last name, or null if not set
     */
    public String getLastName() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PartnerInformationImpl.LASTNAME$0, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the partner's last name as an XmlString object.
     *
     * Provides low-level access to the XMLBeans XmlString representation of the
     * last name field. This method is typically used for advanced XML manipulation
     * or when the XmlString metadata is needed.
     *
     * @return XmlString the XMLBeans representation of the last name, or null if not set
     */
    public XmlString xgetLastName() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PartnerInformationImpl.LASTNAME$0, 0);
            return target;
        }
    }

    /**
     * Sets the partner's last name.
     *
     * Updates the last name value in the XML store in a thread-safe manner.
     * Creates the element if it doesn't exist in the store.
     *
     * @param lastName String the partner's last name to set
     */
    public void setLastName(final String lastName) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PartnerInformationImpl.LASTNAME$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PartnerInformationImpl.LASTNAME$0);
            }
            target.setStringValue(lastName);
        }
    }

    /**
     * Sets the partner's last name using an XmlString object.
     *
     * Provides low-level XML manipulation by setting the last name field using
     * an XmlString object. This method is typically used for advanced XML operations.
     *
     * @param lastName XmlString the XMLBeans representation of the last name to set
     */
    public void xsetLastName(final XmlString lastName) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PartnerInformationImpl.LASTNAME$0, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PartnerInformationImpl.LASTNAME$0);
            }
            target.set((XmlObject)lastName);
        }
    }

    /**
     * Gets the partner's first name.
     *
     * Retrieves the first name value from the XML store in a thread-safe manner
     * using synchronized access to the underlying XMLBeans store.
     *
     * @return String the partner's first name, or null if not set
     */
    public String getFirstName() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PartnerInformationImpl.FIRSTNAME$2, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Gets the partner's first name as an XmlString object.
     *
     * Provides low-level access to the XMLBeans XmlString representation of the
     * first name field. This method is typically used for advanced XML manipulation
     * or when the XmlString metadata is needed.
     *
     * @return XmlString the XMLBeans representation of the first name, or null if not set
     */
    public XmlString xgetFirstName() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PartnerInformationImpl.FIRSTNAME$2, 0);
            return target;
        }
    }

    /**
     * Sets the partner's first name.
     *
     * Updates the first name value in the XML store in a thread-safe manner.
     * Creates the element if it doesn't exist in the store.
     *
     * @param firstName String the partner's first name to set
     */
    public void setFirstName(final String firstName) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PartnerInformationImpl.FIRSTNAME$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PartnerInformationImpl.FIRSTNAME$2);
            }
            target.setStringValue(firstName);
        }
    }

    /**
     * Sets the partner's first name using an XmlString object.
     *
     * Provides low-level XML manipulation by setting the first name field using
     * an XmlString object. This method is typically used for advanced XML operations.
     *
     * @param firstName XmlString the XMLBeans representation of the first name to set
     */
    public void xsetFirstName(final XmlString firstName) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(PartnerInformationImpl.FIRSTNAME$2, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(PartnerInformationImpl.FIRSTNAME$2);
            }
            target.set((XmlObject)firstName);
        }
    }

    /**
     * Gets the partner's occupation.
     *
     * Retrieves the occupation object from the XML store, which contains both
     * a predefined occupation value and an optional free-text "other" field
     * for occupations not in the standard list.
     *
     * @return Occupation the partner's occupation information, or null if not set
     */
    public Occupation getOccupation() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Occupation target = null;
            target = (Occupation)this.get_store().find_element_user(PartnerInformationImpl.OCCUPATION$4, 0);
            if (target == null) {
                return null;
            }
            return target;
        }
    }

    /**
     * Sets the partner's occupation.
     *
     * Updates the occupation object in the XML store in a thread-safe manner.
     * Creates the element if it doesn't exist in the store.
     *
     * @param occupation Occupation the partner's occupation information to set
     */
    public void setOccupation(final Occupation occupation) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Occupation target = null;
            target = (Occupation)this.get_store().find_element_user(PartnerInformationImpl.OCCUPATION$4, 0);
            if (target == null) {
                target = (Occupation)this.get_store().add_element_user(PartnerInformationImpl.OCCUPATION$4);
            }
            target.set((XmlObject)occupation);
        }
    }

    /**
     * Adds a new occupation element to the XML store.
     *
     * Creates and returns a new Occupation object in the XML store, allowing
     * for initialization of occupation data through the returned object.
     *
     * @return Occupation the newly created occupation element
     */
    public Occupation addNewOccupation() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Occupation target = null;
            target = (Occupation)this.get_store().add_element_user(PartnerInformationImpl.OCCUPATION$4);
            return target;
        }
    }

    /**
     * Gets the partner's education level as an enumeration.
     *
     * Retrieves the education level value from the XML store as a type-safe
     * enumeration representing the partner's highest level of education completed.
     *
     * @return EducationLevel.Enum the partner's education level, or null if not set
     */
    public EducationLevel.Enum getEducationLevel() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PartnerInformationImpl.EDUCATIONLEVEL$6, 0);
            if (target == null) {
                return null;
            }
            return (EducationLevel.Enum)target.getEnumValue();
        }
    }

    /**
     * Gets the partner's education level as an EducationLevel object.
     *
     * Provides low-level access to the XMLBeans EducationLevel representation.
     * This method is typically used for advanced XML manipulation or when the
     * EducationLevel metadata is needed.
     *
     * @return EducationLevel the XMLBeans representation of the education level, or null if not set
     */
    public EducationLevel xgetEducationLevel() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            EducationLevel target = null;
            target = (EducationLevel)this.get_store().find_element_user(PartnerInformationImpl.EDUCATIONLEVEL$6, 0);
            return target;
        }
    }

    /**
     * Sets the partner's education level using an enumeration.
     *
     * Updates the education level value in the XML store in a thread-safe manner.
     * Creates the element if it doesn't exist in the store.
     *
     * @param educationLevel EducationLevel.Enum the partner's education level to set
     */
    public void setEducationLevel(final EducationLevel.Enum educationLevel) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PartnerInformationImpl.EDUCATIONLEVEL$6, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PartnerInformationImpl.EDUCATIONLEVEL$6);
            }
            target.setEnumValue((StringEnumAbstractBase)educationLevel);
        }
    }

    /**
     * Sets the partner's education level using an EducationLevel object.
     *
     * Provides low-level XML manipulation by setting the education level field using
     * an EducationLevel object. This method is typically used for advanced XML operations.
     *
     * @param educationLevel EducationLevel the XMLBeans representation of the education level to set
     */
    public void xsetEducationLevel(final EducationLevel educationLevel) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            EducationLevel target = null;
            target = (EducationLevel)this.get_store().find_element_user(PartnerInformationImpl.EDUCATIONLEVEL$6, 0);
            if (target == null) {
                target = (EducationLevel)this.get_store().add_element_user(PartnerInformationImpl.EDUCATIONLEVEL$6);
            }
            target.set((XmlObject)educationLevel);
        }
    }

    /**
     * Gets the partner's age.
     *
     * Retrieves the age value from the XML store in a thread-safe manner
     * using synchronized access to the underlying XMLBeans store.
     *
     * @return int the partner's age in years, or 0 if not set
     */
    public int getAge() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PartnerInformationImpl.AGE$8, 0);
            if (target == null) {
                return 0;
            }
            return target.getIntValue();
        }
    }

    /**
     * Gets the partner's age as an Age object.
     *
     * Provides low-level access to the XMLBeans Age representation.
     * This method is typically used for advanced XML manipulation or when the
     * Age metadata is needed.
     *
     * @return Age the XMLBeans representation of the age, or null if not set
     */
    public Age xgetAge() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Age target = null;
            target = (Age)this.get_store().find_element_user(PartnerInformationImpl.AGE$8, 0);
            return target;
        }
    }

    /**
     * Sets the partner's age.
     *
     * Updates the age value in the XML store in a thread-safe manner.
     * Creates the element if it doesn't exist in the store.
     *
     * @param age int the partner's age in years to set
     */
    public void setAge(final int age) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(PartnerInformationImpl.AGE$8, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(PartnerInformationImpl.AGE$8);
            }
            target.setIntValue(age);
        }
    }

    /**
     * Sets the partner's age using an Age object.
     *
     * Provides low-level XML manipulation by setting the age field using
     * an Age object. This method is typically used for advanced XML operations.
     *
     * @param age Age the XMLBeans representation of the age to set
     */
    public void xsetAge(final Age age) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Age target = null;
            target = (Age)this.get_store().find_element_user(PartnerInformationImpl.AGE$8, 0);
            if (target == null) {
                target = (Age)this.get_store().add_element_user(PartnerInformationImpl.AGE$8);
            }
            target.set((XmlObject)age);
        }
    }
    
    static {
        LASTNAME$0 = new QName("http://www.oscarmcmaster.org/AR2005", "lastName");
        FIRSTNAME$2 = new QName("http://www.oscarmcmaster.org/AR2005", "firstName");
        OCCUPATION$4 = new QName("http://www.oscarmcmaster.org/AR2005", "occupation");
        EDUCATIONLEVEL$6 = new QName("http://www.oscarmcmaster.org/AR2005", "educationLevel");
        AGE$8 = new QName("http://www.oscarmcmaster.org/AR2005", "age");
    }

    /**
     * XMLBeans implementation for partner occupation information.
     *
     * This inner class provides XML data binding for occupation data, supporting
     * both predefined occupation values from a controlled vocabulary and a free-text
     * "other" field for occupations not in the standard list. Thread-safe access
     * is ensured through synchronized monitor operations.
     *
     * @see ca.openosp.openo.ar2005.PartnerInformation.Occupation
     * @since 2026-01-24
     */
    public static class OccupationImpl extends XmlComplexContentImpl implements Occupation
    {
        private static final long serialVersionUID = 1L;
        private static final QName VALUE$0;
        private static final QName OTHER$2;

        /**
         * Constructs a new OccupationImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this element
         */
        public OccupationImpl(final SchemaType sType) {
            super(sType);
        }

        /**
         * Gets the occupation value as an enumeration.
         *
         * Retrieves the predefined occupation value from the controlled vocabulary.
         *
         * @return Value.Enum the occupation value enumeration, or null if not set
         */
        public Value.Enum getValue() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                SimpleValue target = null;
                target = (SimpleValue)this.get_store().find_element_user(OccupationImpl.VALUE$0, 0);
                if (target == null) {
                    return null;
                }
                return (Value.Enum)target.getEnumValue();
            }
        }

        /**
         * Gets the occupation value as a Value object.
         *
         * Provides low-level access to the XMLBeans Value representation.
         *
         * @return Value the XMLBeans representation of the occupation value, or null if not set
         */
        public Value xgetValue() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                Value target = null;
                target = (Value)this.get_store().find_element_user(OccupationImpl.VALUE$0, 0);
                return target;
            }
        }

        /**
         * Sets the occupation value using an enumeration.
         *
         * Updates the predefined occupation value from the controlled vocabulary.
         *
         * @param value Value.Enum the occupation value enumeration to set
         */
        public void setValue(final Value.Enum value) {
            synchronized (this.monitor()) {
                this.check_orphaned();
                SimpleValue target = null;
                target = (SimpleValue)this.get_store().find_element_user(OccupationImpl.VALUE$0, 0);
                if (target == null) {
                    target = (SimpleValue)this.get_store().add_element_user(OccupationImpl.VALUE$0);
                }
                target.setEnumValue((StringEnumAbstractBase)value);
            }
        }

        /**
         * Sets the occupation value using a Value object.
         *
         * Provides low-level XML manipulation by setting the occupation value field.
         *
         * @param value Value the XMLBeans representation of the occupation value to set
         */
        public void xsetValue(final Value value) {
            synchronized (this.monitor()) {
                this.check_orphaned();
                Value target = null;
                target = (Value)this.get_store().find_element_user(OccupationImpl.VALUE$0, 0);
                if (target == null) {
                    target = (Value)this.get_store().add_element_user(OccupationImpl.VALUE$0);
                }
                target.set((XmlObject)value);
            }
        }

        /**
         * Gets the free-text occupation description.
         *
         * Retrieves the "other" field which is used when the partner's occupation
         * is not in the predefined list. This allows for capturing occupation
         * information not covered by the standard controlled vocabulary.
         *
         * @return String the free-text occupation description, or null if not set
         */
        public String getOther() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                SimpleValue target = null;
                target = (SimpleValue)this.get_store().find_element_user(OccupationImpl.OTHER$2, 0);
                if (target == null) {
                    return null;
                }
                return target.getStringValue();
            }
        }

        /**
         * Gets the free-text occupation description as an XmlString object.
         *
         * Provides low-level access to the XMLBeans XmlString representation of the
         * "other" occupation field.
         *
         * @return XmlString the XMLBeans representation of the other occupation, or null if not set
         */
        public XmlString xgetOther() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                XmlString target = null;
                target = (XmlString)this.get_store().find_element_user(OccupationImpl.OTHER$2, 0);
                return target;
            }
        }

        /**
         * Sets the free-text occupation description.
         *
         * Updates the "other" field with a free-text occupation description for
         * occupations not in the predefined list.
         *
         * @param other String the free-text occupation description to set
         */
        public void setOther(final String other) {
            synchronized (this.monitor()) {
                this.check_orphaned();
                SimpleValue target = null;
                target = (SimpleValue)this.get_store().find_element_user(OccupationImpl.OTHER$2, 0);
                if (target == null) {
                    target = (SimpleValue)this.get_store().add_element_user(OccupationImpl.OTHER$2);
                }
                target.setStringValue(other);
            }
        }

        /**
         * Sets the free-text occupation description using an XmlString object.
         *
         * Provides low-level XML manipulation by setting the "other" occupation field.
         *
         * @param other XmlString the XMLBeans representation of the other occupation to set
         */
        public void xsetOther(final XmlString other) {
            synchronized (this.monitor()) {
                this.check_orphaned();
                XmlString target = null;
                target = (XmlString)this.get_store().find_element_user(OccupationImpl.OTHER$2, 0);
                if (target == null) {
                    target = (XmlString)this.get_store().add_element_user(OccupationImpl.OTHER$2);
                }
                target.set((XmlObject)other);
            }
        }
        
        static {
            VALUE$0 = new QName("http://www.oscarmcmaster.org/AR2005", "value");
            OTHER$2 = new QName("http://www.oscarmcmaster.org/AR2005", "other");
        }

        /**
         * XMLBeans implementation for occupation value enumeration.
         *
         * This inner class provides XML data binding for the predefined occupation
         * value enumeration. It extends JavaStringEnumerationHolderEx to support
         * type-safe enumeration values for occupation types.
         *
         * @see ca.openosp.openo.ar2005.PartnerInformation.Occupation.Value
         * @since 2026-01-24
         */
        public static class ValueImpl extends JavaStringEnumerationHolderEx implements Value
        {
            private static final long serialVersionUID = 1L;

            /**
             * Constructs a new ValueImpl instance with the specified schema type.
             *
             * @param sType SchemaType the XMLBeans schema type definition for this enumeration
             */
            public ValueImpl(final SchemaType sType) {
                super(sType, false);
            }

            /**
             * Protected constructor for ValueImpl with schema type and initialization flag.
             *
             * This constructor is used internally by XMLBeans for advanced initialization scenarios.
             *
             * @param sType SchemaType the XMLBeans schema type definition for this enumeration
             * @param b boolean initialization flag for XMLBeans internal use
             */
            protected ValueImpl(final SchemaType sType, final boolean b) {
                super(sType, b);
            }
        }
    }

    /**
     * XMLBeans implementation for education level enumeration.
     *
     * This inner class provides XML data binding for the partner's education level
     * enumeration. It extends JavaStringEnumerationHolderEx to support type-safe
     * enumeration values for educational attainment levels.
     *
     * @see ca.openosp.openo.ar2005.PartnerInformation.EducationLevel
     * @since 2026-01-24
     */
    public static class EducationLevelImpl extends JavaStringEnumerationHolderEx implements EducationLevel
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new EducationLevelImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this enumeration
         */
        public EducationLevelImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for EducationLevelImpl with schema type and initialization flag.
         *
         * This constructor is used internally by XMLBeans for advanced initialization scenarios.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this enumeration
         * @param b boolean initialization flag for XMLBeans internal use
         */
        protected EducationLevelImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }

    /**
     * XMLBeans implementation for partner age.
     *
     * This inner class provides XML data binding for the partner's age value.
     * It extends JavaIntHolderEx to provide type-safe integer handling for age data.
     *
     * @see ca.openosp.openo.ar2005.PartnerInformation.Age
     * @since 2026-01-24
     */
    public static class AgeImpl extends JavaIntHolderEx implements Age
    {
        private static final long serialVersionUID = 1L;

        /**
         * Constructs a new AgeImpl instance with the specified schema type.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this element
         */
        public AgeImpl(final SchemaType sType) {
            super(sType, false);
        }

        /**
         * Protected constructor for AgeImpl with schema type and initialization flag.
         *
         * This constructor is used internally by XMLBeans for advanced initialization scenarios.
         *
         * @param sType SchemaType the XMLBeans schema type definition for this element
         * @param b boolean initialization flag for XMLBeans internal use
         */
        protected AgeImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
    }
}
