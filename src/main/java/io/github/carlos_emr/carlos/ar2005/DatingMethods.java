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
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Interface for pregnancy dating method information.
 *
 * @since 2005-01-01
 */
public interface DatingMethods extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(DatingMethods.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("datingmethods6d6ctype");
    
    boolean getDates();
    
    XmlBoolean xgetDates();
    
    void setDates(final boolean p0);
    
    void xsetDates(final XmlBoolean p0);
    
    boolean getT1US();
    
    XmlBoolean xgetT1US();
    
    void setT1US(final boolean p0);
    
    void xsetT1US(final XmlBoolean p0);
    
    boolean getT2US();
    
    XmlBoolean xgetT2US();
    
    void setT2US(final boolean p0);
    
    void xsetT2US(final XmlBoolean p0);
    
    boolean getArt();
    
    XmlBoolean xgetArt();
    
    void setArt(final boolean p0);
    
    void xsetArt(final XmlBoolean p0);
    
    public static final class Factory
    {
        public static DatingMethods newInstance() {
            return (DatingMethods)XmlBeans.getContextTypeLoader().newInstance(DatingMethods.type, (XmlOptions)null);
        }
        
        public static DatingMethods newInstance(final XmlOptions options) {
            return (DatingMethods)XmlBeans.getContextTypeLoader().newInstance(DatingMethods.type, options);
        }
        
        public static DatingMethods parse(final String xmlAsString) throws XmlException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(xmlAsString, DatingMethods.type, (XmlOptions)null);
        }
        
        public static DatingMethods parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(xmlAsString, DatingMethods.type, options);
        }
        
        public static DatingMethods parse(final File file) throws XmlException, IOException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(file, DatingMethods.type, (XmlOptions)null);
        }
        
        public static DatingMethods parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(file, DatingMethods.type, options);
        }
        
        public static DatingMethods parse(final URL u) throws XmlException, IOException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(u, DatingMethods.type, (XmlOptions)null);
        }
        
        public static DatingMethods parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(u, DatingMethods.type, options);
        }
        
        public static DatingMethods parse(final InputStream is) throws XmlException, IOException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(is, DatingMethods.type, (XmlOptions)null);
        }
        
        public static DatingMethods parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(is, DatingMethods.type, options);
        }
        
        public static DatingMethods parse(final Reader r) throws XmlException, IOException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(r, DatingMethods.type, (XmlOptions)null);
        }
        
        public static DatingMethods parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(r, DatingMethods.type, options);
        }
        
        public static DatingMethods parse(final XMLStreamReader sr) throws XmlException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(sr, DatingMethods.type, (XmlOptions)null);
        }
        
        public static DatingMethods parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(sr, DatingMethods.type, options);
        }
        
        public static DatingMethods parse(final Node node) throws XmlException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(node, DatingMethods.type, (XmlOptions)null);
        }
        
        public static DatingMethods parse(final Node node, final XmlOptions options) throws XmlException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(node, DatingMethods.type, options);
        }
        
        @Deprecated
        public static DatingMethods parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(xis, DatingMethods.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static DatingMethods parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (DatingMethods)XmlBeans.getContextTypeLoader().parse(xis, DatingMethods.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, DatingMethods.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, DatingMethods.type, options);
        }
        
        private Factory() {
        }
    }
}
