package io.github.carlos_emr.carlos.ar2005;

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
import org.apache.xmlbeans.XmlBeans;
import org.apache.xmlbeans.XmlString;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Interface for custom laboratory test entries in the antenatal record.
 *
 * @since 2005-01-01
 */
public interface CustomLab extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(CustomLab.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("customlab8a81type");
    
    String getLabel();
    
    XmlString xgetLabel();
    
    void setLabel(final String p0);
    
    void xsetLabel(final XmlString p0);
    
    String getResult();
    
    XmlString xgetResult();
    
    void setResult(final String p0);
    
    void xsetResult(final XmlString p0);
    
    public static final class Factory
    {
        public static CustomLab newInstance() {
            return (CustomLab)XmlBeans.getContextTypeLoader().newInstance(CustomLab.type, (XmlOptions)null);
        }
        
        public static CustomLab newInstance(final XmlOptions options) {
            return (CustomLab)XmlBeans.getContextTypeLoader().newInstance(CustomLab.type, options);
        }
        
        public static CustomLab parse(final String xmlAsString) throws XmlException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(xmlAsString, CustomLab.type, (XmlOptions)null);
        }
        
        public static CustomLab parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(xmlAsString, CustomLab.type, options);
        }
        
        public static CustomLab parse(final File file) throws XmlException, IOException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(file, CustomLab.type, (XmlOptions)null);
        }
        
        public static CustomLab parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(file, CustomLab.type, options);
        }
        
        public static CustomLab parse(final URL u) throws XmlException, IOException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(u, CustomLab.type, (XmlOptions)null);
        }
        
        public static CustomLab parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(u, CustomLab.type, options);
        }
        
        public static CustomLab parse(final InputStream is) throws XmlException, IOException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(is, CustomLab.type, (XmlOptions)null);
        }
        
        public static CustomLab parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(is, CustomLab.type, options);
        }
        
        public static CustomLab parse(final Reader r) throws XmlException, IOException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(r, CustomLab.type, (XmlOptions)null);
        }
        
        public static CustomLab parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(r, CustomLab.type, options);
        }
        
        public static CustomLab parse(final XMLStreamReader sr) throws XmlException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(sr, CustomLab.type, (XmlOptions)null);
        }
        
        public static CustomLab parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(sr, CustomLab.type, options);
        }
        
        public static CustomLab parse(final Node node) throws XmlException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(node, CustomLab.type, (XmlOptions)null);
        }
        
        public static CustomLab parse(final Node node, final XmlOptions options) throws XmlException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(node, CustomLab.type, options);
        }
        
        @Deprecated
        public static CustomLab parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(xis, CustomLab.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static CustomLab parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (CustomLab)XmlBeans.getContextTypeLoader().parse(xis, CustomLab.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, CustomLab.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, CustomLab.type, options);
        }
        
        private Factory() {
        }
    }
}
