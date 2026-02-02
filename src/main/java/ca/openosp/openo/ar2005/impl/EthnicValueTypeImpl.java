package ca.openosp.openo.ar2005.impl;

import org.apache.xmlbeans.SchemaType;
import ca.openosp.openo.ar2005.EthnicValueType;
import org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx;

/**
 * Implementation class for the EthnicValueType XML enumeration type used in BC Antenatal Record (BCAR) 2005 forms.
 *
 * <p>This class provides the concrete implementation for handling ethnicity enumeration values in pregnancy care
 * documentation. It extends Apache XMLBeans' JavaStringEnumerationHolderEx to support XML Schema enumeration
 * restrictions for ethnicity codes defined in the BCAR forms system.</p>
 *
 * <p>The supported ethnicity codes include:</p>
 * <ul>
 *   <li>ANC001, ANC002, ANC005, ANC007 - Specific ethnicity classifications</li>
 *   <li>OTHER - Other ethnicity not listed</li>
 *   <li>UN - Unknown/Not specified</li>
 * </ul>
 *
 * <p>This implementation is part of OpenO EMR's healthcare data integration layer for British Columbia's
 * standardized antenatal record forms, ensuring compliance with provincial maternity care documentation
 * requirements.</p>
 *
 * @see EthnicValueType
 * @see ca.openosp.openo.ar2005.PatientInformation
 * @see ca.openosp.openo.ar2005.ARRecord
 * @since 2026-01-23
 */
public class EthnicValueTypeImpl extends JavaStringEnumerationHolderEx implements EthnicValueType
{
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new EthnicValueTypeImpl instance with the specified schema type.
     *
     * <p>This constructor is used by the XMLBeans framework when parsing or creating ethnicity
     * enumeration values from XML documents. It initializes the enumeration holder with the
     * provided schema type definition.</p>
     *
     * @param sType SchemaType the XML Schema type definition for this ethnicity enumeration
     */
    public EthnicValueTypeImpl(final SchemaType sType) {
        super(sType, false);
    }

    /**
     * Protected constructor with validation control for internal XMLBeans framework use.
     *
     * <p>This constructor provides control over schema validation behavior during object
     * instantiation. It is typically used internally by the XMLBeans type system for advanced
     * XML processing scenarios where validation needs to be deferred or customized.</p>
     *
     * @param sType SchemaType the XML Schema type definition for this ethnicity enumeration
     * @param b boolean flag controlling validation behavior during instantiation
     */
    protected EthnicValueTypeImpl(final SchemaType sType, final boolean b) {
        super(sType, b);
    }
}
