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

public interface ARRecordSet extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(ARRecordSet.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("arrecordset63a5type");
    
    ARRecord[] getARRecordArray();
    
    ARRecord getARRecordArray(final int p0);
    
    int sizeOfARRecordArray();
    
    void setARRecordArray(final ARRecord[] p0);
    
    void setARRecordArray(final int p0, final ARRecord p1);
    
    ARRecord insertNewARRecord(final int p0);
    
    ARRecord addNewARRecord();
    
    void removeARRecord(final int p0);
    
    public static final class Factory
    {
        public static ARRecordSet newInstance() {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().newInstance(ARRecordSet.type, (XmlOptions)null);
        }
        
        public static ARRecordSet newInstance(final XmlOptions options) {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().newInstance(ARRecordSet.type, options);
        }
        
        public static ARRecordSet parse(final String xmlAsString) throws XmlException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(xmlAsString, ARRecordSet.type, (XmlOptions)null);
        }
        
        public static ARRecordSet parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(xmlAsString, ARRecordSet.type, options);
        }
        
        public static ARRecordSet parse(final File file) throws XmlException, IOException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(file, ARRecordSet.type, (XmlOptions)null);
        }
        
        public static ARRecordSet parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(file, ARRecordSet.type, options);
        }
        
        public static ARRecordSet parse(final URL u) throws XmlException, IOException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(u, ARRecordSet.type, (XmlOptions)null);
        }
        
        public static ARRecordSet parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(u, ARRecordSet.type, options);
        }
        
        public static ARRecordSet parse(final InputStream is) throws XmlException, IOException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(is, ARRecordSet.type, (XmlOptions)null);
        }
        
        public static ARRecordSet parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(is, ARRecordSet.type, options);
        }
        
        public static ARRecordSet parse(final Reader r) throws XmlException, IOException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(r, ARRecordSet.type, (XmlOptions)null);
        }
        
        public static ARRecordSet parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(r, ARRecordSet.type, options);
        }
        
        public static ARRecordSet parse(final XMLStreamReader sr) throws XmlException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(sr, ARRecordSet.type, (XmlOptions)null);
        }
        
        public static ARRecordSet parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(sr, ARRecordSet.type, options);
        }
        
        public static ARRecordSet parse(final Node node) throws XmlException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(node, ARRecordSet.type, (XmlOptions)null);
        }
        
        public static ARRecordSet parse(final Node node, final XmlOptions options) throws XmlException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(node, ARRecordSet.type, options);
        }
        
        @Deprecated
        public static ARRecordSet parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(xis, ARRecordSet.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static ARRecordSet parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (ARRecordSet)XmlBeans.getContextTypeLoader().parse(xis, ARRecordSet.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ARRecordSet.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ARRecordSet.type, options);
        }
        
        private Factory() {
        }
    }
}
