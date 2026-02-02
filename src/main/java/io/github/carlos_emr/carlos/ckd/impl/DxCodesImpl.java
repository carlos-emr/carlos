package io.github.carlos_emr.carlos.ckd.impl;

import org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.impl.values.JavaStringHolderEx;
import org.apache.xmlbeans.XmlObject;
import java.util.List;
import java.util.ArrayList;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import io.github.carlos_emr.carlos.ckd.DxCodes;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * XMLBeans implementation class for the {@link DxCodes} interface.
 *
 * This class provides the concrete implementation for managing diagnosis code collections
 * within CKD (Chronic Kidney Disease) configuration documents. It handles the XML
 * serialization and deserialization of diagnosis code arrays, where each code includes
 * both the actual diagnostic code value and associated metadata such as type and name.
 *
 * The implementation manages diagnosis code elements as an array of Code objects within the
 * "http://www.oscarmcmaster.org/ckd" XML namespace, providing thread-safe access
 * to individual code entries and array operations including adding, removing,
 * and modifying diagnosis codes.
 *
 * Key functionality includes:
 * - Array-based diagnosis code management with index-based access
 * - Dynamic addition and removal of diagnosis code entries
 * - Support for complex code objects with attributes (type, name)
 * - Thread-safe operations for concurrent access
 *
 * @see DxCodes
 * @since 2010-01-01
 */
public class DxCodesImpl extends XmlComplexContentImpl implements DxCodes
{
    private static final long serialVersionUID = 1L;
    private static final QName CODE$0;
    
    /**
     * Constructs a new DxCodesImpl instance with the specified schema type.
     *
     * @param sType the SchemaType that defines the XML schema structure for this diagnosis codes element
     */
    public DxCodesImpl(final SchemaType sType) {
        super(sType);
    }
    
    /**
     * Retrieves all diagnosis code entries as a Code array.
     *
     * @return Code[] array containing all diagnosis code entries, empty array if no codes are set
     */
    public Code[] getCodeArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            final List targetList = new ArrayList();
            this.get_store().find_all_element_users(DxCodesImpl.CODE$0, targetList);
            final Code[] result = new Code[targetList.size()];
            targetList.toArray(result);
            return result;
        }
    }
    
    /**
     * Retrieves a specific diagnosis code entry by index.
     *
     * @param i int the index of the diagnosis code entry to retrieve
     * @return Code the diagnosis code entry at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public Code getCodeArray(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Code target = null;
            target = (Code)this.get_store().find_element_user(DxCodesImpl.CODE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            return target;
        }
    }
    
    /**
     * Returns the number of diagnosis code entries in the array.
     *
     * @return int the total number of diagnosis code entries
     */
    public int sizeOfCodeArray() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            return this.get_store().count_elements(DxCodesImpl.CODE$0);
        }
    }
    
    /**
     * Sets the entire diagnosis code array, replacing all existing entries.
     *
     * @param codeArray Code[] array of diagnosis code entries to set
     */
    public void setCodeArray(final Code[] codeArray) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.arraySetterHelper((XmlObject[])codeArray, DxCodesImpl.CODE$0);
        }
    }
    
    /**
     * Sets a specific diagnosis code entry at the specified index.
     *
     * @param i int the index of the diagnosis code entry to set
     * @param code Code the diagnosis code entry to set at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    public void setCodeArray(final int i, final Code code) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Code target = null;
            target = (Code)this.get_store().find_element_user(DxCodesImpl.CODE$0, i);
            if (target == null) {
                throw new IndexOutOfBoundsException();
            }
            target.set((XmlObject)code);
        }
    }
    
    /**
     * Inserts a new diagnosis code entry at the specified index.
     *
     * @param i int the index at which to insert the new diagnosis code entry
     * @return Code the newly created diagnosis code entry
     */
    public Code insertNewCode(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Code target = null;
            target = (Code)this.get_store().insert_element_user(DxCodesImpl.CODE$0, i);
            return target;
        }
    }
    
    /**
     * Adds a new diagnosis code entry to the end of the array.
     *
     * @return Code the newly created diagnosis code entry
     */
    public Code addNewCode() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            Code target = null;
            target = (Code)this.get_store().add_element_user(DxCodesImpl.CODE$0);
            return target;
        }
    }
    
    /**
     * Removes the diagnosis code entry at the specified index.
     *
     * @param i int the index of the diagnosis code entry to remove
     */
    public void removeCode(final int i) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            this.get_store().remove_element(DxCodesImpl.CODE$0, i);
        }
    }
    
    static {
        // Initialize QName constant for XML element identification within CKD namespace
        CODE$0 = new QName("http://www.oscarmcmaster.org/ckd", "code");
    }
    
    /**
     * XMLBeans implementation class for individual diagnosis code entries.
     *
     * This inner class provides the concrete implementation for managing individual
     * diagnosis code elements within the DxCodes collection. Each code element contains
     * the diagnostic code value as text content, along with optional attributes for
     * type classification and descriptive name.
     *
     * The implementation handles both the text content of the diagnosis code and
     * its associated metadata attributes (type and name) within the CKD namespace.
     *
     * @since 2010-01-01
     */
    public static class CodeImpl extends JavaStringHolderEx implements Code
    {
        private static final long serialVersionUID = 1L;
        private static final QName TYPE$0;
        private static final QName NAME$2;
        
        /**
         * Constructs a new CodeImpl instance with the specified schema type.
         *
         * @param sType the SchemaType that defines the XML schema structure for this code element
         */
        public CodeImpl(final SchemaType sType) {
            super(sType, true);
        }
        
        /**
         * Protected constructor for CodeImpl with schema type and boolean flag.
         *
         * @param sType the SchemaType that defines the XML schema structure
         * @param b boolean flag for initialization control
         */
        protected CodeImpl(final SchemaType sType, final boolean b) {
            super(sType, b);
        }
        
        /**
         * Retrieves the type attribute of this diagnosis code.
         *
         * @return Type.Enum the type classification of this diagnosis code, or null if not set
         */
        public Type.Enum getType() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                SimpleValue target = null;
                target = (SimpleValue)this.get_store().find_attribute_user(CodeImpl.TYPE$0);
                if (target == null) {
                    return null;
                }
                return (Type.Enum)target.getEnumValue();
            }
        }
        
        /**
         * Retrieves the type attribute of this diagnosis code as a Type object.
         *
         * @return Type the type classification as a Type object, or null if not set
         */
        public Type xgetType() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                Type target = null;
                target = (Type)this.get_store().find_attribute_user(CodeImpl.TYPE$0);
                return target;
            }
        }
        
        /**
         * Checks if the type attribute is set for this diagnosis code.
         *
         * @return boolean true if type attribute exists, false otherwise
         */
        public boolean isSetType() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                return this.get_store().find_attribute_user(CodeImpl.TYPE$0) != null;
            }
        }
        
        /**
         * Sets the type attribute for this diagnosis code.
         *
         * @param type Type.Enum the type classification to set
         */
        public void setType(final Type.Enum type) {
            synchronized (this.monitor()) {
                this.check_orphaned();
                SimpleValue target = null;
                target = (SimpleValue)this.get_store().find_attribute_user(CodeImpl.TYPE$0);
                if (target == null) {
                    target = (SimpleValue)this.get_store().add_attribute_user(CodeImpl.TYPE$0);
                }
                target.setEnumValue((StringEnumAbstractBase)type);
            }
        }
        
        /**
         * Sets the type attribute for this diagnosis code using a Type object.
         *
         * @param type Type the type classification as a Type object to set
         */
        public void xsetType(final Type type) {
            synchronized (this.monitor()) {
                this.check_orphaned();
                Type target = null;
                target = (Type)this.get_store().find_attribute_user(CodeImpl.TYPE$0);
                if (target == null) {
                    target = (Type)this.get_store().add_attribute_user(CodeImpl.TYPE$0);
                }
                target.set((XmlObject)type);
            }
        }
        
        /**
         * Removes the type attribute from this diagnosis code.
         */
        public void unsetType() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                this.get_store().remove_attribute(CodeImpl.TYPE$0);
            }
        }
        
        /**
         * Retrieves the name attribute of this diagnosis code.
         *
         * @return String the descriptive name of this diagnosis code, or null if not set
         */
        public String getName() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                SimpleValue target = null;
                target = (SimpleValue)this.get_store().find_attribute_user(CodeImpl.NAME$2);
                if (target == null) {
                    return null;
                }
                return target.getStringValue();
            }
        }
        
        /**
         * Retrieves the name attribute of this diagnosis code as an XmlString object.
         *
         * @return XmlString the descriptive name as an XML string object, or null if not set
         */
        public XmlString xgetName() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                XmlString target = null;
                target = (XmlString)this.get_store().find_attribute_user(CodeImpl.NAME$2);
                return target;
            }
        }
        
        /**
         * Checks if the name attribute is set for this diagnosis code.
         *
         * @return boolean true if name attribute exists, false otherwise
         */
        public boolean isSetName() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                return this.get_store().find_attribute_user(CodeImpl.NAME$2) != null;
            }
        }
        
        /**
         * Sets the name attribute for this diagnosis code.
         *
         * @param name String the descriptive name to set
         */
        public void setName(final String name) {
            synchronized (this.monitor()) {
                this.check_orphaned();
                SimpleValue target = null;
                target = (SimpleValue)this.get_store().find_attribute_user(CodeImpl.NAME$2);
                if (target == null) {
                    target = (SimpleValue)this.get_store().add_attribute_user(CodeImpl.NAME$2);
                }
                target.setStringValue(name);
            }
        }
        
        /**
         * Sets the name attribute for this diagnosis code using an XmlString object.
         *
         * @param name XmlString the descriptive name as an XML string object to set
         */
        public void xsetName(final XmlString name) {
            synchronized (this.monitor()) {
                this.check_orphaned();
                XmlString target = null;
                target = (XmlString)this.get_store().find_attribute_user(CodeImpl.NAME$2);
                if (target == null) {
                    target = (XmlString)this.get_store().add_attribute_user(CodeImpl.NAME$2);
                }
                target.set((XmlObject)name);
            }
        }
        
        /**
         * Removes the name attribute from this diagnosis code.
         */
        public void unsetName() {
            synchronized (this.monitor()) {
                this.check_orphaned();
                this.get_store().remove_attribute(CodeImpl.NAME$2);
            }
        }
        
        static {
            // Initialize QName constants for XML attribute identification
            TYPE$0 = new QName("", "type");
            NAME$2 = new QName("", "name");
        }
        
        /**
         * XMLBeans implementation class for the Type enumeration.
         *
         * This inner class provides the concrete implementation for managing
         * type enumeration values used in diagnosis code classification.
         *
         * @since 2010-01-01
         */
        public static class TypeImpl extends JavaStringEnumerationHolderEx implements Type
        {
            private static final long serialVersionUID = 1L;
            
            /**
             * Constructs a new TypeImpl instance with the specified schema type.
             *
             * @param sType the SchemaType that defines the XML schema structure for this type enumeration
             */
            public TypeImpl(final SchemaType sType) {
                super(sType, false);
            }
            
            /**
             * Protected constructor for TypeImpl with schema type and boolean flag.
             *
             * @param sType the SchemaType that defines the XML schema structure
             * @param b boolean flag for initialization control
             */
            protected TypeImpl(final SchemaType sType, final boolean b) {
                super(sType, b);
            }
        }
    }
}
