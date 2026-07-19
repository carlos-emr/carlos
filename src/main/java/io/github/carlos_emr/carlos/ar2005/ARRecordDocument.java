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

public interface ARRecordDocument extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(ARRecordDocument.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("arrecord55e9doctype");
    
    ARRecord getARRecord();
    
    void setARRecord(final ARRecord p0);
    
    ARRecord addNewARRecord();
    
    public static final class Factory
    {
        public static ARRecordDocument newInstance() {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().newInstance(ARRecordDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordDocument newInstance(final XmlOptions options) {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().newInstance(ARRecordDocument.type, options);
        }
        
        public static ARRecordDocument parse(final String xmlAsString) throws XmlException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(xmlAsString, ARRecordDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordDocument parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(xmlAsString, ARRecordDocument.type, options);
        }
        
        public static ARRecordDocument parse(final File file) throws XmlException, IOException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(file, ARRecordDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordDocument parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(file, ARRecordDocument.type, options);
        }
        
        public static ARRecordDocument parse(final URL u) throws XmlException, IOException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(u, ARRecordDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordDocument parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(u, ARRecordDocument.type, options);
        }
        
        public static ARRecordDocument parse(final InputStream is) throws XmlException, IOException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(is, ARRecordDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordDocument parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(is, ARRecordDocument.type, options);
        }
        
        public static ARRecordDocument parse(final Reader r) throws XmlException, IOException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(r, ARRecordDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordDocument parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(r, ARRecordDocument.type, options);
        }
        
        public static ARRecordDocument parse(final XMLStreamReader sr) throws XmlException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(sr, ARRecordDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordDocument parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(sr, ARRecordDocument.type, options);
        }
        
        public static ARRecordDocument parse(final Node node) throws XmlException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(node, ARRecordDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordDocument parse(final Node node, final XmlOptions options) throws XmlException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(node, ARRecordDocument.type, options);
        }
        
        @Deprecated
        public static ARRecordDocument parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(xis, ARRecordDocument.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static ARRecordDocument parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (ARRecordDocument)XmlBeans.getContextTypeLoader().parse(xis, ARRecordDocument.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ARRecordDocument.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ARRecordDocument.type, options);
        }
        
        private Factory() {
        }
    }
}
