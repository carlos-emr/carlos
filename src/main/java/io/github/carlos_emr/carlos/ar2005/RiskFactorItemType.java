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
 * Interface for individual risk factor entries in the antenatal record.
 *
 * @since 2005-01-01
 */
public interface RiskFactorItemType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(RiskFactorItemType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("riskfactoritemtype3062type");
    
    String getRiskFactor();
    
    XmlString xgetRiskFactor();
    
    void setRiskFactor(final String p0);
    
    void xsetRiskFactor(final XmlString p0);
    
    String getPlanOfManagement();
    
    XmlString xgetPlanOfManagement();
    
    void setPlanOfManagement(final String p0);
    
    void xsetPlanOfManagement(final XmlString p0);
    
    public static final class Factory
    {
        public static RiskFactorItemType newInstance() {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().newInstance(RiskFactorItemType.type, (XmlOptions)null);
        }
        
        public static RiskFactorItemType newInstance(final XmlOptions options) {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().newInstance(RiskFactorItemType.type, options);
        }
        
        public static RiskFactorItemType parse(final String xmlAsString) throws XmlException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(xmlAsString, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        public static RiskFactorItemType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(xmlAsString, RiskFactorItemType.type, options);
        }
        
        public static RiskFactorItemType parse(final File file) throws XmlException, IOException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(file, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        public static RiskFactorItemType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(file, RiskFactorItemType.type, options);
        }
        
        public static RiskFactorItemType parse(final URL u) throws XmlException, IOException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(u, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        public static RiskFactorItemType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(u, RiskFactorItemType.type, options);
        }
        
        public static RiskFactorItemType parse(final InputStream is) throws XmlException, IOException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(is, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        public static RiskFactorItemType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(is, RiskFactorItemType.type, options);
        }
        
        public static RiskFactorItemType parse(final Reader r) throws XmlException, IOException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(r, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        public static RiskFactorItemType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(r, RiskFactorItemType.type, options);
        }
        
        public static RiskFactorItemType parse(final XMLStreamReader sr) throws XmlException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(sr, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        public static RiskFactorItemType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(sr, RiskFactorItemType.type, options);
        }
        
        public static RiskFactorItemType parse(final Node node) throws XmlException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(node, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        public static RiskFactorItemType parse(final Node node, final XmlOptions options) throws XmlException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(node, RiskFactorItemType.type, options);
        }
        
        @Deprecated
        public static RiskFactorItemType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(xis, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static RiskFactorItemType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (RiskFactorItemType)XmlBeans.getContextTypeLoader().parse(xis, RiskFactorItemType.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, RiskFactorItemType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, RiskFactorItemType.type, options);
        }
        
        private Factory() {
        }
    }
}
