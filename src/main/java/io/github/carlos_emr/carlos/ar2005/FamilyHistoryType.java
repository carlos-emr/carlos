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

public interface FamilyHistoryType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(FamilyHistoryType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("familyhistorytypebc0ftype");
    
    YesNoNullType getAtRisk();
    
    void setAtRisk(final YesNoNullType p0);
    
    YesNoNullType addNewAtRisk();
    
    public static final class Factory
    {
        public static FamilyHistoryType newInstance() {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().newInstance(FamilyHistoryType.type, (XmlOptions)null);
        }
        
        public static FamilyHistoryType newInstance(final XmlOptions options) {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().newInstance(FamilyHistoryType.type, options);
        }
        
        public static FamilyHistoryType parse(final String xmlAsString) throws XmlException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(xmlAsString, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        public static FamilyHistoryType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(xmlAsString, FamilyHistoryType.type, options);
        }
        
        public static FamilyHistoryType parse(final File file) throws XmlException, IOException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(file, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        public static FamilyHistoryType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(file, FamilyHistoryType.type, options);
        }
        
        public static FamilyHistoryType parse(final URL u) throws XmlException, IOException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(u, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        public static FamilyHistoryType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(u, FamilyHistoryType.type, options);
        }
        
        public static FamilyHistoryType parse(final InputStream is) throws XmlException, IOException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(is, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        public static FamilyHistoryType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(is, FamilyHistoryType.type, options);
        }
        
        public static FamilyHistoryType parse(final Reader r) throws XmlException, IOException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(r, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        public static FamilyHistoryType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(r, FamilyHistoryType.type, options);
        }
        
        public static FamilyHistoryType parse(final XMLStreamReader sr) throws XmlException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(sr, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        public static FamilyHistoryType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(sr, FamilyHistoryType.type, options);
        }
        
        public static FamilyHistoryType parse(final Node node) throws XmlException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(node, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        public static FamilyHistoryType parse(final Node node, final XmlOptions options) throws XmlException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(node, FamilyHistoryType.type, options);
        }
        
        @Deprecated
        public static FamilyHistoryType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(xis, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static FamilyHistoryType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (FamilyHistoryType)XmlBeans.getContextTypeLoader().parse(xis, FamilyHistoryType.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, FamilyHistoryType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, FamilyHistoryType.type, options);
        }
        
        private Factory() {
        }
    }
}
