package io.github.carlos_emr.carlos.utility;

import java.io.File;
import java.io.IOException;
import java.io.Reader;

import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlObject;
import org.apache.xmlbeans.XmlOptions;

/**
 * Typed entry points for the existing XMLBeans-generated clinical schemas.
 *
 * <p>XMLBeans 5 adds an inherited {@code XmlObject.Factory} field which shadows
 * the nested Factory classes in older generated bindings. Calling that field
 * parses an untyped XmlObject. These methods retain the legacy factories' explicit
 * schema type and class cast while using the current type-loader API.</p>
 */
public final class LegacyXmlBeans {
    private LegacyXmlBeans() {
    }

    /** Creates an instance using the generated binding's original schema type. */
    public static <T extends XmlObject> T newInstance(Class<T> javaType, SchemaType schemaType) {
        return javaType.cast(XmlBeans.getContextTypeLoader().newInstance(schemaType, null));
    }

    /** Parses typed XML with the same default options as the legacy factory. */
    public static <T extends XmlObject> T parse(Class<T> javaType, SchemaType schemaType, String xml)
            throws XmlException {
        return parse(javaType, schemaType, xml, null);
    }

    /** Parses typed XML while preserving the caller's validation and save options. */
    public static <T extends XmlObject> T parse(Class<T> javaType, SchemaType schemaType, String xml,
                                               XmlOptions options) throws XmlException {
        return javaType.cast(XmlBeans.getContextTypeLoader().parse(xml, schemaType, options));
    }

    /** Parses a reader using the binding's schema rather than the untyped factory. */
    public static <T extends XmlObject> T parse(Class<T> javaType, SchemaType schemaType, Reader reader)
            throws XmlException, IOException {
        return javaType.cast(XmlBeans.getContextTypeLoader().parse(reader, schemaType, null));
    }

    /** Parses a validated file while preserving the caller's XML options. */
    public static <T extends XmlObject> T parse(Class<T> javaType, SchemaType schemaType, File file,
                                               XmlOptions options) throws XmlException, IOException {
        return javaType.cast(XmlBeans.getContextTypeLoader().parse(file, schemaType, options));
    }
}
