package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SimpleValue;
import org.apache.xmlbeans.SchemaType;
import javax.xml.namespace.QName;
import ca.openosp.openo.ar2005.CustomLab;
import org.apache.xmlbeans.impl.values.XmlComplexContentImpl;

/**
 * Apache XMLBeans implementation class for custom laboratory results in the AR2005 (Antenatal Record 2005) module.
 *
 * <p>This class provides XML serialization and deserialization capabilities for custom laboratory test data
 * within the context of prenatal care documentation. It implements the {@link CustomLab} interface and extends
 * {@link XmlComplexContentImpl} to provide thread-safe XML data binding for custom lab results that may not
 * fit standard laboratory test classifications.</p>
 *
 * <p>The implementation manages two primary elements:</p>
 * <ul>
 *   <li><b>label</b> - A descriptive identifier for the custom laboratory test</li>
 *   <li><b>result</b> - The corresponding test result value</li>
 * </ul>
 *
 * <p>All accessor and mutator methods are thread-safe, utilizing synchronized blocks with XMLBeans monitor objects
 * to ensure data integrity during concurrent XML document manipulation.</p>
 *
 * <p><b>Healthcare Context:</b> This class is part of the British Columbia Antenatal Record (BCAR) forms system,
 * enabling healthcare providers to document non-standard laboratory tests specific to prenatal care that may not
 * be covered by standardized lab requisition forms.</p>
 *
 * @see CustomLab
 * @see ca.openosp.openo.ar2005
 * @since 2026-01-24
 */
public class CustomLabImpl extends XmlComplexContentImpl implements CustomLab
{
    private static final long serialVersionUID = 1L;
    private static final QName LABEL$0;
    private static final QName RESULT$2;

    /**
     * Constructs a new CustomLabImpl instance with the specified schema type.
     *
     * <p>This constructor initializes the XMLBeans complex content implementation with the provided
     * schema type definition, setting up the internal XML store for managing custom laboratory data elements.</p>
     *
     * @param sType SchemaType the schema type definition for this custom lab element
     */
    public CustomLabImpl(final SchemaType sType) {
        super(sType);
    }

    /**
     * Retrieves the label (descriptive name) of this custom laboratory test.
     *
     * <p>This method returns the string value of the label element, which identifies the type or name
     * of the custom laboratory test. The operation is thread-safe and synchronized on the internal
     * XMLBeans monitor object.</p>
     *
     * @return String the label identifying this custom lab test, or null if not set
     */
    public String getLabel() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(CustomLabImpl.LABEL$0, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the label element as an XmlString object for advanced XML manipulation.
     *
     * <p>This method provides access to the underlying XMLBeans XmlString representation of the label element,
     * allowing for advanced XML operations such as schema validation, namespace handling, and direct XML
     * attribute access. The operation is thread-safe and synchronized on the internal XMLBeans monitor object.</p>
     *
     * @return XmlString the label element as an XMLBeans type, or null if not set
     */
    public XmlString xgetLabel() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(CustomLabImpl.LABEL$0, 0);
            return target;
        }
    }

    /**
     * Sets the label (descriptive name) for this custom laboratory test.
     *
     * <p>This method updates the label element with the provided string value, creating the element
     * if it does not already exist in the XML document. The operation is thread-safe and synchronized
     * on the internal XMLBeans monitor object.</p>
     *
     * @param label String the descriptive identifier for this custom lab test
     */
    public void setLabel(final String label) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(CustomLabImpl.LABEL$0, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(CustomLabImpl.LABEL$0);
            }
            target.setStringValue(label);
        }
    }

    /**
     * Sets the label element using an XmlString object for advanced XML manipulation.
     *
     * <p>This method updates the label element using an XMLBeans XmlString object, allowing for
     * advanced XML operations such as preserving XML attributes, namespaces, and schema-specific
     * formatting. The element is created if it does not already exist. The operation is thread-safe
     * and synchronized on the internal XMLBeans monitor object.</p>
     *
     * @param label XmlString the label element to set
     */
    public void xsetLabel(final XmlString label) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(CustomLabImpl.LABEL$0, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(CustomLabImpl.LABEL$0);
            }
            target.set((XmlObject)label);
        }
    }

    /**
     * Retrieves the result value of this custom laboratory test.
     *
     * <p>This method returns the string value of the result element, which contains the actual test
     * result or measurement value for this custom laboratory test. The operation is thread-safe and
     * synchronized on the internal XMLBeans monitor object.</p>
     *
     * @return String the result value of this custom lab test, or null if not set
     */
    public String getResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(CustomLabImpl.RESULT$2, 0);
            if (target == null) {
                return null;
            }
            return target.getStringValue();
        }
    }

    /**
     * Retrieves the result element as an XmlString object for advanced XML manipulation.
     *
     * <p>This method provides access to the underlying XMLBeans XmlString representation of the result element,
     * allowing for advanced XML operations such as schema validation, namespace handling, and direct XML
     * attribute access. The operation is thread-safe and synchronized on the internal XMLBeans monitor object.</p>
     *
     * @return XmlString the result element as an XMLBeans type, or null if not set
     */
    public XmlString xgetResult() {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(CustomLabImpl.RESULT$2, 0);
            return target;
        }
    }

    /**
     * Sets the result value for this custom laboratory test.
     *
     * <p>This method updates the result element with the provided string value, creating the element
     * if it does not already exist in the XML document. The operation is thread-safe and synchronized
     * on the internal XMLBeans monitor object.</p>
     *
     * @param result String the result value or measurement for this custom lab test
     */
    public void setResult(final String result) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            SimpleValue target = null;
            target = (SimpleValue)this.get_store().find_element_user(CustomLabImpl.RESULT$2, 0);
            if (target == null) {
                target = (SimpleValue)this.get_store().add_element_user(CustomLabImpl.RESULT$2);
            }
            target.setStringValue(result);
        }
    }

    /**
     * Sets the result element using an XmlString object for advanced XML manipulation.
     *
     * <p>This method updates the result element using an XMLBeans XmlString object, allowing for
     * advanced XML operations such as preserving XML attributes, namespaces, and schema-specific
     * formatting. The element is created if it does not already exist. The operation is thread-safe
     * and synchronized on the internal XMLBeans monitor object.</p>
     *
     * @param result XmlString the result element to set
     */
    public void xsetResult(final XmlString result) {
        synchronized (this.monitor()) {
            this.check_orphaned();
            XmlString target = null;
            target = (XmlString)this.get_store().find_element_user(CustomLabImpl.RESULT$2, 0);
            if (target == null) {
                target = (XmlString)this.get_store().add_element_user(CustomLabImpl.RESULT$2);
            }
            target.set((XmlObject)result);
        }
    }
    
    static {
        LABEL$0 = new QName("http://www.oscarmcmaster.org/AR2005", "label");
        RESULT$2 = new QName("http://www.oscarmcmaster.org/AR2005", "result");
    }
}
