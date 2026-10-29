package io.github.carlos_emr.carlos.utility;

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.StringEnumAbstractBase;
import org.apache.xmlbeans.impl.values.JavaStringEnumerationHolderEx;

/**
 * Preserves the enum accessor aliases declared by the existing generated bindings.
 * XMLBeans 5 exposes the same operations as getEnumValue/setEnumValue.
 */
public class LegacyXmlStringEnumerationHolder extends JavaStringEnumerationHolderEx {
    private static final long serialVersionUID = 1L;

    /** Uses the original generated schema and complex-type flag unchanged. */
    protected LegacyXmlStringEnumerationHolder(SchemaType schemaType, boolean complex) {
        super(schemaType, complex);
    }

    /** Returns the enum value through the current XMLBeans accessor. */
    public final StringEnumAbstractBase enumValue() {
        return getEnumValue();
    }

    /** Sets the enum value through the current XMLBeans setter and validation. */
    public final void set(StringEnumAbstractBase value) {
        setEnumValue(value);
    }
}
