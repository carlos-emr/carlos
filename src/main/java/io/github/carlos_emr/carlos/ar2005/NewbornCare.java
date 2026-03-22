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
import org.apache.xmlbeans.XmlBoolean;
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Interface for newborn care and assessment data.
 *
 * @since 2005-01-01
 */
public interface NewbornCare extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(NewbornCare.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("newborncaree4fftype");
    
    boolean getPed();
    
    XmlBoolean xgetPed();
    
    void setPed(final boolean p0);
    
    void xsetPed(final XmlBoolean p0);
    
    boolean getFP();
    
    XmlBoolean xgetFP();
    
    void setFP(final boolean p0);
    
    void xsetFP(final XmlBoolean p0);
    
    boolean getMidwife();
    
    XmlBoolean xgetMidwife();
    
    void setMidwife(final boolean p0);
    
    void xsetMidwife(final XmlBoolean p0);
    
    String getOther();
    
    XmlString xgetOther();
    
    void setOther(final String p0);
    
    void xsetOther(final XmlString p0);
    
    public static final class Factory
    {
        public static NewbornCare newInstance() {
            return (NewbornCare)XmlBeans.getContextTypeLoader().newInstance(NewbornCare.type, (XmlOptions)null);
        }
        
        public static NewbornCare newInstance(final XmlOptions options) {
            return (NewbornCare)XmlBeans.getContextTypeLoader().newInstance(NewbornCare.type, options);
        }
        
        public static NewbornCare parse(final String xmlAsString) throws XmlException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(xmlAsString, NewbornCare.type, (XmlOptions)null);
        }
        
        public static NewbornCare parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(xmlAsString, NewbornCare.type, options);
        }
        
        public static NewbornCare parse(final File file) throws XmlException, IOException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(file, NewbornCare.type, (XmlOptions)null);
        }
        
        public static NewbornCare parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(file, NewbornCare.type, options);
        }
        
        public static NewbornCare parse(final URL u) throws XmlException, IOException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(u, NewbornCare.type, (XmlOptions)null);
        }
        
        public static NewbornCare parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(u, NewbornCare.type, options);
        }
        
        public static NewbornCare parse(final InputStream is) throws XmlException, IOException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(is, NewbornCare.type, (XmlOptions)null);
        }
        
        public static NewbornCare parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(is, NewbornCare.type, options);
        }
        
        public static NewbornCare parse(final Reader r) throws XmlException, IOException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(r, NewbornCare.type, (XmlOptions)null);
        }
        
        public static NewbornCare parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(r, NewbornCare.type, options);
        }
        
        public static NewbornCare parse(final XMLStreamReader sr) throws XmlException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(sr, NewbornCare.type, (XmlOptions)null);
        }
        
        public static NewbornCare parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(sr, NewbornCare.type, options);
        }
        
        public static NewbornCare parse(final Node node) throws XmlException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(node, NewbornCare.type, (XmlOptions)null);
        }
        
        public static NewbornCare parse(final Node node, final XmlOptions options) throws XmlException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(node, NewbornCare.type, options);
        }
        
        @Deprecated
        public static NewbornCare parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(xis, NewbornCare.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static NewbornCare parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (NewbornCare)XmlBeans.getContextTypeLoader().parse(xis, NewbornCare.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, NewbornCare.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, NewbornCare.type, options);
        }
        
        private Factory() {
        }
    }
}
