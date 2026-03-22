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
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Interface defining a single Antenatal Record document entry.
 *
 * @since 2005-01-01
 */
public interface ARRecord extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(ARRecord.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("arrecord2fd9type");
    
    AR1 getAR1();
    
    void setAR1(final AR1 p0);
    
    AR1 addNewAR1();
    
    AR2 getAR2();
    
    void setAR2(final AR2 p0);
    
    AR2 addNewAR2();
    
    public static final class Factory
    {
        public static ARRecord newInstance() {
            return (ARRecord)XmlBeans.getContextTypeLoader().newInstance(ARRecord.type, (XmlOptions)null);
        }
        
        public static ARRecord newInstance(final XmlOptions options) {
            return (ARRecord)XmlBeans.getContextTypeLoader().newInstance(ARRecord.type, options);
        }
        
        public static ARRecord parse(final String xmlAsString) throws XmlException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(xmlAsString, ARRecord.type, (XmlOptions)null);
        }
        
        public static ARRecord parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(xmlAsString, ARRecord.type, options);
        }
        
        public static ARRecord parse(final File file) throws XmlException, IOException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(file, ARRecord.type, (XmlOptions)null);
        }
        
        public static ARRecord parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(file, ARRecord.type, options);
        }
        
        public static ARRecord parse(final URL u) throws XmlException, IOException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(u, ARRecord.type, (XmlOptions)null);
        }
        
        public static ARRecord parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(u, ARRecord.type, options);
        }
        
        public static ARRecord parse(final InputStream is) throws XmlException, IOException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(is, ARRecord.type, (XmlOptions)null);
        }
        
        public static ARRecord parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(is, ARRecord.type, options);
        }
        
        public static ARRecord parse(final Reader r) throws XmlException, IOException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(r, ARRecord.type, (XmlOptions)null);
        }
        
        public static ARRecord parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(r, ARRecord.type, options);
        }
        
        public static ARRecord parse(final XMLStreamReader sr) throws XmlException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(sr, ARRecord.type, (XmlOptions)null);
        }
        
        public static ARRecord parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(sr, ARRecord.type, options);
        }
        
        public static ARRecord parse(final Node node) throws XmlException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(node, ARRecord.type, (XmlOptions)null);
        }
        
        public static ARRecord parse(final Node node, final XmlOptions options) throws XmlException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(node, ARRecord.type, options);
        }
        
        @Deprecated
        public static ARRecord parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(xis, ARRecord.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static ARRecord parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (ARRecord)XmlBeans.getContextTypeLoader().parse(xis, ARRecord.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ARRecord.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ARRecord.type, options);
        }
        
        private Factory() {
        }
    }
}
