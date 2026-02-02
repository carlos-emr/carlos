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

public interface BirthAttendants extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(BirthAttendants.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("birthattendants638atype");
    
    boolean getOBS();
    
    XmlBoolean xgetOBS();
    
    void setOBS(final boolean p0);
    
    void xsetOBS(final XmlBoolean p0);
    
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
        public static BirthAttendants newInstance() {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().newInstance(BirthAttendants.type, (XmlOptions)null);
        }
        
        public static BirthAttendants newInstance(final XmlOptions options) {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().newInstance(BirthAttendants.type, options);
        }
        
        public static BirthAttendants parse(final String xmlAsString) throws XmlException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(xmlAsString, BirthAttendants.type, (XmlOptions)null);
        }
        
        public static BirthAttendants parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(xmlAsString, BirthAttendants.type, options);
        }
        
        public static BirthAttendants parse(final File file) throws XmlException, IOException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(file, BirthAttendants.type, (XmlOptions)null);
        }
        
        public static BirthAttendants parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(file, BirthAttendants.type, options);
        }
        
        public static BirthAttendants parse(final URL u) throws XmlException, IOException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(u, BirthAttendants.type, (XmlOptions)null);
        }
        
        public static BirthAttendants parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(u, BirthAttendants.type, options);
        }
        
        public static BirthAttendants parse(final InputStream is) throws XmlException, IOException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(is, BirthAttendants.type, (XmlOptions)null);
        }
        
        public static BirthAttendants parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(is, BirthAttendants.type, options);
        }
        
        public static BirthAttendants parse(final Reader r) throws XmlException, IOException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(r, BirthAttendants.type, (XmlOptions)null);
        }
        
        public static BirthAttendants parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(r, BirthAttendants.type, options);
        }
        
        public static BirthAttendants parse(final XMLStreamReader sr) throws XmlException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(sr, BirthAttendants.type, (XmlOptions)null);
        }
        
        public static BirthAttendants parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(sr, BirthAttendants.type, options);
        }
        
        public static BirthAttendants parse(final Node node) throws XmlException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(node, BirthAttendants.type, (XmlOptions)null);
        }
        
        public static BirthAttendants parse(final Node node, final XmlOptions options) throws XmlException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(node, BirthAttendants.type, options);
        }
        
        @Deprecated
        public static BirthAttendants parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(xis, BirthAttendants.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static BirthAttendants parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (BirthAttendants)XmlBeans.getContextTypeLoader().parse(xis, BirthAttendants.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, BirthAttendants.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, BirthAttendants.type, options);
        }
        
        private Factory() {
        }
    }
}
