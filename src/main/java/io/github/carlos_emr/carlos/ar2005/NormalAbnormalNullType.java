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
 * Interface for a tri-state value type (normal, abnormal, or null).
 *
 * @since 2005-01-01
 */
public interface NormalAbnormalNullType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(NormalAbnormalNullType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("normalabnormalnulltypef227type");
    
    boolean getNormal();
    
    XmlBoolean xgetNormal();
    
    boolean isSetNormal();
    
    void setNormal(final boolean p0);
    
    void xsetNormal(final XmlBoolean p0);
    
    void unsetNormal();
    
    boolean getAbnormal();
    
    XmlBoolean xgetAbnormal();
    
    boolean isSetAbnormal();
    
    void setAbnormal(final boolean p0);
    
    void xsetAbnormal(final XmlBoolean p0);
    
    void unsetAbnormal();
    
    boolean getNull();
    
    XmlBoolean xgetNull();
    
    boolean isSetNull();
    
    void setNull(final boolean p0);
    
    void xsetNull(final XmlBoolean p0);
    
    void unsetNull();
    
    public static final class Factory
    {
        public static NormalAbnormalNullType newInstance() {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().newInstance(NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        public static NormalAbnormalNullType newInstance(final XmlOptions options) {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().newInstance(NormalAbnormalNullType.type, options);
        }
        
        public static NormalAbnormalNullType parse(final String xmlAsString) throws XmlException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(xmlAsString, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        public static NormalAbnormalNullType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(xmlAsString, NormalAbnormalNullType.type, options);
        }
        
        public static NormalAbnormalNullType parse(final File file) throws XmlException, IOException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(file, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        public static NormalAbnormalNullType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(file, NormalAbnormalNullType.type, options);
        }
        
        public static NormalAbnormalNullType parse(final URL u) throws XmlException, IOException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(u, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        public static NormalAbnormalNullType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(u, NormalAbnormalNullType.type, options);
        }
        
        public static NormalAbnormalNullType parse(final InputStream is) throws XmlException, IOException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(is, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        public static NormalAbnormalNullType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(is, NormalAbnormalNullType.type, options);
        }
        
        public static NormalAbnormalNullType parse(final Reader r) throws XmlException, IOException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(r, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        public static NormalAbnormalNullType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(r, NormalAbnormalNullType.type, options);
        }
        
        public static NormalAbnormalNullType parse(final XMLStreamReader sr) throws XmlException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(sr, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        public static NormalAbnormalNullType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(sr, NormalAbnormalNullType.type, options);
        }
        
        public static NormalAbnormalNullType parse(final Node node) throws XmlException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(node, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        public static NormalAbnormalNullType parse(final Node node, final XmlOptions options) throws XmlException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(node, NormalAbnormalNullType.type, options);
        }
        
        @Deprecated
        public static NormalAbnormalNullType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(xis, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static NormalAbnormalNullType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (NormalAbnormalNullType)XmlBeans.getContextTypeLoader().parse(xis, NormalAbnormalNullType.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, NormalAbnormalNullType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, NormalAbnormalNullType.type, options);
        }
        
        private Factory() {
        }
    }
}
