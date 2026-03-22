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
import org.apache.xmlbeans.SchemaType;
import org.apache.xmlbeans.XmlObject;

/**
 * Interface for infectious disease screening data in the antenatal record.
 *
 * @since 2005-01-01
 */
public interface InfectiousDiseaseType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(InfectiousDiseaseType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("infectiousdiseasetype0462type");
    
    YesNoNullType getVaricella();
    
    void setVaricella(final YesNoNullType p0);
    
    YesNoNullType addNewVaricella();
    
    YesNoNullType getStd();
    
    void setStd(final YesNoNullType p0);
    
    YesNoNullType addNewStd();
    
    YesNoNullType getTuberculosis();
    
    void setTuberculosis(final YesNoNullType p0);
    
    YesNoNullType addNewTuberculosis();
    
    String getOtherDescr();
    
    XmlString xgetOtherDescr();
    
    void setOtherDescr(final String p0);
    
    void xsetOtherDescr(final XmlString p0);
    
    YesNoNullType getOther();
    
    void setOther(final YesNoNullType p0);
    
    YesNoNullType addNewOther();
    
    public static final class Factory
    {
        public static InfectiousDiseaseType newInstance() {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().newInstance(InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        public static InfectiousDiseaseType newInstance(final XmlOptions options) {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().newInstance(InfectiousDiseaseType.type, options);
        }
        
        public static InfectiousDiseaseType parse(final String xmlAsString) throws XmlException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(xmlAsString, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        public static InfectiousDiseaseType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(xmlAsString, InfectiousDiseaseType.type, options);
        }
        
        public static InfectiousDiseaseType parse(final File file) throws XmlException, IOException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(file, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        public static InfectiousDiseaseType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(file, InfectiousDiseaseType.type, options);
        }
        
        public static InfectiousDiseaseType parse(final URL u) throws XmlException, IOException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(u, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        public static InfectiousDiseaseType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(u, InfectiousDiseaseType.type, options);
        }
        
        public static InfectiousDiseaseType parse(final InputStream is) throws XmlException, IOException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(is, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        public static InfectiousDiseaseType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(is, InfectiousDiseaseType.type, options);
        }
        
        public static InfectiousDiseaseType parse(final Reader r) throws XmlException, IOException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(r, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        public static InfectiousDiseaseType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(r, InfectiousDiseaseType.type, options);
        }
        
        public static InfectiousDiseaseType parse(final XMLStreamReader sr) throws XmlException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(sr, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        public static InfectiousDiseaseType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(sr, InfectiousDiseaseType.type, options);
        }
        
        public static InfectiousDiseaseType parse(final Node node) throws XmlException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(node, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        public static InfectiousDiseaseType parse(final Node node, final XmlOptions options) throws XmlException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(node, InfectiousDiseaseType.type, options);
        }
        
        @Deprecated
        public static InfectiousDiseaseType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(xis, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static InfectiousDiseaseType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (InfectiousDiseaseType)XmlBeans.getContextTypeLoader().parse(xis, InfectiousDiseaseType.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, InfectiousDiseaseType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, InfectiousDiseaseType.type, options);
        }
        
        private Factory() {
        }
    }
}
