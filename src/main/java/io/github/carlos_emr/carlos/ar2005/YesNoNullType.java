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

public interface YesNoNullType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(YesNoNullType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("yesnonulltype00aetype");
    
    boolean getYes();
    
    XmlBoolean xgetYes();
    
    boolean isSetYes();
    
    void setYes(final boolean p0);
    
    void xsetYes(final XmlBoolean p0);
    
    void unsetYes();
    
    boolean getNo();
    
    XmlBoolean xgetNo();
    
    boolean isSetNo();
    
    void setNo(final boolean p0);
    
    void xsetNo(final XmlBoolean p0);
    
    void unsetNo();
    
    boolean getNull();
    
    XmlBoolean xgetNull();
    
    boolean isSetNull();
    
    void setNull(final boolean p0);
    
    void xsetNull(final XmlBoolean p0);
    
    void unsetNull();
    
    public static final class Factory
    {
        public static YesNoNullType newInstance() {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().newInstance(YesNoNullType.type, (XmlOptions)null);
        }
        
        public static YesNoNullType newInstance(final XmlOptions options) {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().newInstance(YesNoNullType.type, options);
        }
        
        public static YesNoNullType parse(final String xmlAsString) throws XmlException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(xmlAsString, YesNoNullType.type, (XmlOptions)null);
        }
        
        public static YesNoNullType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(xmlAsString, YesNoNullType.type, options);
        }
        
        public static YesNoNullType parse(final File file) throws XmlException, IOException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(file, YesNoNullType.type, (XmlOptions)null);
        }
        
        public static YesNoNullType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(file, YesNoNullType.type, options);
        }
        
        public static YesNoNullType parse(final URL u) throws XmlException, IOException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(u, YesNoNullType.type, (XmlOptions)null);
        }
        
        public static YesNoNullType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(u, YesNoNullType.type, options);
        }
        
        public static YesNoNullType parse(final InputStream is) throws XmlException, IOException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(is, YesNoNullType.type, (XmlOptions)null);
        }
        
        public static YesNoNullType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(is, YesNoNullType.type, options);
        }
        
        public static YesNoNullType parse(final Reader r) throws XmlException, IOException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(r, YesNoNullType.type, (XmlOptions)null);
        }
        
        public static YesNoNullType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(r, YesNoNullType.type, options);
        }
        
        public static YesNoNullType parse(final XMLStreamReader sr) throws XmlException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(sr, YesNoNullType.type, (XmlOptions)null);
        }
        
        public static YesNoNullType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(sr, YesNoNullType.type, options);
        }
        
        public static YesNoNullType parse(final Node node) throws XmlException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(node, YesNoNullType.type, (XmlOptions)null);
        }
        
        public static YesNoNullType parse(final Node node, final XmlOptions options) throws XmlException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(node, YesNoNullType.type, options);
        }
        
        @Deprecated
        public static YesNoNullType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(xis, YesNoNullType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static YesNoNullType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (YesNoNullType)XmlBeans.getContextTypeLoader().parse(xis, YesNoNullType.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, YesNoNullType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, YesNoNullType.type, options);
        }
        
        private Factory() {
        }
    }
}
