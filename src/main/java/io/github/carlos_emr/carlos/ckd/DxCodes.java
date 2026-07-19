package io.github.carlos_emr.carlos.ckd;

import org.apache.xmlbeans.xml.stream.XMLStreamException;
import org.apache.xmlbeans.xml.stream.XMLInputStream;
import org.w3c.dom.Node;
import javax.xml.stream.XMLStreamReader;
import java.io.Reader;
import java.io.InputStream;
import java.net.URL;
import java.io.IOException;
import java.io.File;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Diagnostic codes configuration interface for CKD patient identification.
 *
 * This XMLBeans-generated interface manages collections of diagnostic codes (ICD-9/ICD-10)
 * used to identify patients with Chronic Kidney Disease and related conditions. These codes
 * are essential for automated patient screening, risk stratification, and population health
 * management within the EMR system.
 *
 * The diagnostic codes are used by the CKD clinical decision support system to:
 * - Identify patients with existing CKD diagnoses
 * - Flag patients with risk factors for CKD development
 * - Track CKD progression stages (Stage 1-5)
 * - Monitor related complications (hypertension, diabetes, cardiovascular disease)
 *
 * Each code entry includes the code value, type (ICD-9 or ICD-10), and an optional
 * descriptive name for human readability.
 *
 * @since 2010-01-01
 * @see CKDConfig
 * @see DxCodes.Code
 * @see io.github.carlos_emr.carlos.ckd.impl.DxCodesImpl
 */
public interface DxCodes extends XmlObject
{
    /**
     * SchemaType system definition for XMLBeans type mapping.
     */
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(DxCodes.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("dxcodes4b79type");

    /**
     * Gets all diagnostic codes as an array.
     *
     * Returns the complete collection of diagnostic codes configured for CKD
     * patient identification. Each code represents a specific diagnosis that
     * may indicate CKD or related conditions.
     *
     * @return Code[] array of all configured diagnostic codes
     */
    Code[] getCodeArray();

    /**
     * Gets a specific diagnostic code by index.
     *
     * Retrieves a single diagnostic code from the collection at the specified
     * position. Used for iterating through or accessing specific codes.
     *
     * @param p0 int zero-based index of the code to retrieve
     * @return Code diagnostic code at the specified index
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    Code getCodeArray(final int p0);

    /**
     * Gets the number of diagnostic codes.
     *
     * Returns the total count of diagnostic codes in the configuration.
     * Useful for iteration and validation purposes.
     *
     * @return int number of diagnostic codes configured
     */
    int sizeOfCodeArray();

    /**
     * Sets the entire array of diagnostic codes.
     *
     * Replaces all existing diagnostic codes with the provided array.
     * This operation completely overwrites the current configuration.
     *
     * @param p0 Code[] array of diagnostic codes to set
     */
    void setCodeArray(final Code[] p0);

    /**
     * Sets a specific diagnostic code at an index.
     *
     * Updates or replaces the diagnostic code at the specified position
     * in the collection.
     *
     * @param p0 int zero-based index where to set the code
     * @param p1 Code diagnostic code to set at the position
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void setCodeArray(final int p0, final Code p1);

    /**
     * Inserts a new diagnostic code at a specific index.
     *
     * Creates and inserts a new empty Code instance at the specified position,
     * shifting existing codes to make room. The returned instance can be
     * populated with code details.
     *
     * @param p0 int zero-based index where to insert the new code
     * @return Code newly created code instance at the insertion point
     */
    Code insertNewCode(final int p0);

    /**
     * Adds a new diagnostic code to the collection.
     *
     * Creates and appends a new empty Code instance to the end of the
     * diagnostic codes collection. The returned instance can be configured
     * with the specific diagnostic code details.
     *
     * @return Code newly created code instance
     */
    Code addNewCode();

    /**
     * Removes a diagnostic code at a specific index.
     *
     * Deletes the diagnostic code at the specified position from the
     * collection, shifting remaining codes to fill the gap.
     *
     * @param p0 int zero-based index of the code to remove
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void removeCode(final int p0);

    /**
     * Individual diagnostic code configuration interface.
     *
     * Represents a single diagnostic code entry with its value, type classification
     * (ICD-9 or ICD-10), and optional descriptive name. The code value itself is
     * stored as the string content, while attributes provide metadata.
     *
     * Standard CKD-related codes include:
     * - ICD-9: 585.x (CKD stages), 250.4x (diabetic nephropathy), 403.x (hypertensive CKD)
     * - ICD-10: N18.x (CKD stages), E11.2x (diabetic kidney disease), I12.x (hypertensive CKD)
     *
     * @since 2010-01-01
     */
    public interface Code extends XmlString
    {
        /**
         * SchemaType for Code element.
         */
        public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Code.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("codec550elemtype");

        /**
         * Gets the diagnostic code type.
         *
         * Returns the classification system for this diagnostic code
         * (e.g., ICD-9, ICD-10). This determines how the code value
         * should be interpreted and validated.
         *
         * @return Type.Enum code type enumeration value
         */
        Type.Enum getType();

        /**
         * Gets the diagnostic code type as XML type.
         *
         * Returns the type attribute as an XMLBeans Type object for
         * advanced XML manipulation.
         *
         * @return Type XML type object
         */
        Type xgetType();

        /**
         * Checks if the type attribute is set.
         *
         * Determines whether this code has a type classification specified.
         *
         * @return boolean true if type is set, false otherwise
         */
        boolean isSetType();

        /**
         * Sets the diagnostic code type.
         *
         * Specifies the classification system for this diagnostic code.
         *
         * @param p0 Type.Enum code type to set (e.g., ICD_9)
         */
        void setType(final Type.Enum p0);

        /**
         * Sets the diagnostic code type using XML type.
         *
         * @param p0 Type XML type object to set
         */
        void xsetType(final Type p0);

        /**
         * Removes the type attribute.
         *
         * Clears the type classification from this diagnostic code.
         */
        void unsetType();

        /**
         * Gets the diagnostic code name.
         *
         * Returns the human-readable description or name of this diagnostic
         * code. This helps healthcare providers understand what condition
         * the code represents without memorizing code numbers.
         *
         * @return String descriptive name of the diagnostic code
         */
        String getName();

        /**
         * Gets the diagnostic code name as XML string.
         *
         * @return XmlString XML representation of the code name
         */
        XmlString xgetName();

        /**
         * Checks if the name attribute is set.
         *
         * @return boolean true if name is specified, false otherwise
         */
        boolean isSetName();

        /**
         * Sets the diagnostic code name.
         *
         * Provides a human-readable description for this diagnostic code.
         *
         * @param p0 String descriptive name for the code
         */
        void setName(final String p0);

        /**
         * Sets the diagnostic code name using XML string.
         *
         * @param p0 XmlString XML representation of the name
         */
        void xsetName(final XmlString p0);

        /**
         * Removes the name attribute.
         *
         * Clears the descriptive name from this diagnostic code.
         */
        void unsetName();

        /**
         * Diagnostic code type enumeration interface.
         *
         * Defines the classification systems supported for diagnostic codes.
         * Currently supports ICD-9, with potential for ICD-10 expansion.
         * The type determines code format validation and billing system integration.
         *
         * @since 2010-01-01
         */
        public interface Type extends XmlString
        {
            /**
             * SchemaType for Type attribute.
             */
            public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Type.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("type242aattrtype");

            /**
             * ICD-9 diagnostic code type constant.
             * International Classification of Diseases, 9th Revision.
             */
            public static final Enum ICD_9 = Enum.forString("icd9");

            /**
             * Integer constant for ICD-9 type.
             */
            public static final int INT_ICD_9 = 1;

            /**
             * Gets the enumeration value.
             *
             * @return StringEnumAbstractBase underlying enumeration value
             */
            StringEnumAbstractBase enumValue();

            /**
             * Sets the enumeration value.
             *
             * @param p0 StringEnumAbstractBase enumeration value to set
             */
            void set(final StringEnumAbstractBase p0);

            /**
             * Enumeration implementation for diagnostic code types.
             *
             * Provides type-safe enumeration of supported diagnostic code
             * classification systems. Handles serialization and deserialization
             * of type values.
             */
            public static final class Enum extends StringEnumAbstractBase
            {
                /**
                 * Internal integer representation for ICD-9.
                 */
                static final int INT_ICD_9 = 1;

                /**
                 * Enumeration lookup table.
                 */
                public static final StringEnumAbstractBase.Table table;

                /**
                 * Serialization version identifier.
                 */
                private static final long serialVersionUID = 1L;

                /**
                 * Creates an Enum from a string value.
                 *
                 * @param s String representation of the type
                 * @return Enum corresponding enumeration value
                 */
                public static Enum forString(final String s) {
                    return (Enum)Enum.table.forString(s);
                }

                /**
                 * Creates an Enum from an integer value.
                 *
                 * @param i int representation of the type
                 * @return Enum corresponding enumeration value
                 */
                public static Enum forInt(final int i) {
                    return (Enum)Enum.table.forInt(i);
                }

                /**
                 * Private constructor for enumeration values.
                 *
                 * @param s String representation
                 * @param i int numeric value
                 */
                private Enum(final String s, final int i) {
                    super(s, i);
                }

                /**
                 * Resolves deserialized instances to singleton values.
                 *
                 * @return Object proper singleton enumeration instance
                 */
                private Object readResolve() {
                    return forInt(this.intValue());
                }

                /**
                 * Static initializer for enumeration table.
                 * Registers all supported diagnostic code types.
                 */
                static {
                    table = new StringEnumAbstractBase.Table((StringEnumAbstractBase[])new Enum[] { new Enum("icd9", 1) });
                }
            }

            /**
             * Factory for creating Type instances.
             */
            public static final class Factory
            {
                /**
                 * Creates a Type from an object value.
                 *
                 * @param obj Object value to convert
                 * @return Type created type instance
                 */
                public static Type newValue(final Object obj) {
                    return (Type)Type.type.newValue(obj);
                }

                /**
                 * Creates a new empty Type instance.
                 *
                 * @return Type new type instance
                 */
                public static Type newInstance() {
                    return (Type)XmlBeans.getContextTypeLoader().newInstance(Type.type, (XmlOptions)null);
                }

                /**
                 * Creates a new Type instance with options.
                 *
                 * @param options XmlOptions processing options
                 * @return Type new type instance
                 */
                public static Type newInstance(final XmlOptions options) {
                    return (Type)XmlBeans.getContextTypeLoader().newInstance(Type.type, options);
                }

                /**
                 * Private constructor prevents instantiation.
                 */
                private Factory() {
                }
            }
        }

        /**
         * Factory for creating Code instances.
         */
        public static final class Factory
        {
            /**
             * Creates a new empty Code instance.
             *
             * @return Code new diagnostic code instance
             */
            public static Code newInstance() {
                return (Code)XmlBeans.getContextTypeLoader().newInstance(Code.type, (XmlOptions)null);
            }

            /**
             * Creates a new Code instance with options.
             *
             * @param options XmlOptions processing options
             * @return Code new diagnostic code instance
             */
            public static Code newInstance(final XmlOptions options) {
                return (Code)XmlBeans.getContextTypeLoader().newInstance(Code.type, options);
            }

            /**
             * Private constructor prevents instantiation.
             */
            private Factory() {
            }
        }
    }

    /**
     * Factory class for creating and parsing DxCodes instances.
     *
     * Provides static methods for instantiating and deserializing diagnostic
     * codes configuration from various sources.
     */
    public static final class Factory
    {
        /**
         * Creates a new empty DxCodes instance.
         *
         * @return DxCodes new diagnostic codes configuration
         */
        public static DxCodes newInstance() {
            return (DxCodes)XmlBeans.getContextTypeLoader().newInstance(DxCodes.type, (XmlOptions)null);
        }

        /**
         * Creates a new DxCodes instance with options.
         *
         * @param options XmlOptions processing options
         * @return DxCodes new diagnostic codes configuration
         */
        public static DxCodes newInstance(final XmlOptions options) {
            return (DxCodes)XmlBeans.getContextTypeLoader().newInstance(DxCodes.type, options);
        }
        
        public static DxCodes parse(final String xmlAsString) throws XmlException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(xmlAsString, DxCodes.type, (XmlOptions)null);
        }
        
        public static DxCodes parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(xmlAsString, DxCodes.type, options);
        }
        
        public static DxCodes parse(final File file) throws XmlException, IOException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(file, DxCodes.type, (XmlOptions)null);
        }
        
        public static DxCodes parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(file, DxCodes.type, options);
        }
        
        public static DxCodes parse(final URL u) throws XmlException, IOException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(u, DxCodes.type, (XmlOptions)null);
        }
        
        public static DxCodes parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(u, DxCodes.type, options);
        }
        
        public static DxCodes parse(final InputStream is) throws XmlException, IOException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(is, DxCodes.type, (XmlOptions)null);
        }
        
        public static DxCodes parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(is, DxCodes.type, options);
        }
        
        public static DxCodes parse(final Reader r) throws XmlException, IOException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(r, DxCodes.type, (XmlOptions)null);
        }
        
        public static DxCodes parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(r, DxCodes.type, options);
        }
        
        public static DxCodes parse(final XMLStreamReader sr) throws XmlException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(sr, DxCodes.type, (XmlOptions)null);
        }
        
        public static DxCodes parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(sr, DxCodes.type, options);
        }
        
        public static DxCodes parse(final Node node) throws XmlException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(node, DxCodes.type, (XmlOptions)null);
        }
        
        public static DxCodes parse(final Node node, final XmlOptions options) throws XmlException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(node, DxCodes.type, options);
        }
        
        @Deprecated
        public static DxCodes parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(xis, DxCodes.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static DxCodes parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (DxCodes)XmlBeans.getContextTypeLoader().parse(xis, DxCodes.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, DxCodes.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, DxCodes.type, options);
        }

        /**
         * Private constructor prevents instantiation.
         */
        private Factory() {
        }
    }
}
