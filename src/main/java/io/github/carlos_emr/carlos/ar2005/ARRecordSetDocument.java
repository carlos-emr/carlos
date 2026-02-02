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

public interface ARRecordSetDocument extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(ARRecordSetDocument.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("arrecordsetc395doctype");
    
    ARRecordSet getARRecordSet();
    
    void setARRecordSet(final ARRecordSet p0);
    
    ARRecordSet addNewARRecordSet();
    
    public static final class Factory
    {
        public static ARRecordSetDocument newInstance() {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().newInstance(ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordSetDocument newInstance(final XmlOptions options) {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().newInstance(ARRecordSetDocument.type, options);
        }
        
        public static ARRecordSetDocument parse(final String xmlAsString) throws XmlException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(xmlAsString, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordSetDocument parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(xmlAsString, ARRecordSetDocument.type, options);
        }
        
        public static ARRecordSetDocument parse(final File file) throws XmlException, IOException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(file, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordSetDocument parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(file, ARRecordSetDocument.type, options);
        }
        
        public static ARRecordSetDocument parse(final URL u) throws XmlException, IOException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(u, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordSetDocument parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(u, ARRecordSetDocument.type, options);
        }
        
        public static ARRecordSetDocument parse(final InputStream is) throws XmlException, IOException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(is, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordSetDocument parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(is, ARRecordSetDocument.type, options);
        }
        
        public static ARRecordSetDocument parse(final Reader r) throws XmlException, IOException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(r, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordSetDocument parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(r, ARRecordSetDocument.type, options);
        }
        
        public static ARRecordSetDocument parse(final XMLStreamReader sr) throws XmlException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(sr, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordSetDocument parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(sr, ARRecordSetDocument.type, options);
        }
        
        public static ARRecordSetDocument parse(final Node node) throws XmlException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(node, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        public static ARRecordSetDocument parse(final Node node, final XmlOptions options) throws XmlException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(node, ARRecordSetDocument.type, options);
        }
        
        @Deprecated
        public static ARRecordSetDocument parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(xis, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static ARRecordSetDocument parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (ARRecordSetDocument)XmlBeans.getContextTypeLoader().parse(xis, ARRecordSetDocument.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ARRecordSetDocument.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ARRecordSetDocument.type, options);
        }
        
        private Factory() {
        }
    }
}
