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

/**
 * Interface for generic medical history entries in the antenatal record.
 *
 * @since 2005-01-01
 */
public interface GenericHistoryType extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(GenericHistoryType.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("generichistorytypebdeetype");
    
    YesNoNullType getAtRisk();
    
    void setAtRisk(final YesNoNullType p0);
    
    YesNoNullType addNewAtRisk();
    
    YesNoNullType getDevelopmentalDelay();
    
    void setDevelopmentalDelay(final YesNoNullType p0);
    
    YesNoNullType addNewDevelopmentalDelay();
    
    YesNoNullType getCongenitalAnomolies();
    
    void setCongenitalAnomolies(final YesNoNullType p0);
    
    YesNoNullType addNewCongenitalAnomolies();
    
    YesNoNullType getChromosomalDisorders();
    
    void setChromosomalDisorders(final YesNoNullType p0);
    
    YesNoNullType addNewChromosomalDisorders();
    
    YesNoNullType getGeneticDisorders();
    
    void setGeneticDisorders(final YesNoNullType p0);
    
    YesNoNullType addNewGeneticDisorders();
    
    public static final class Factory
    {
        public static GenericHistoryType newInstance() {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().newInstance(GenericHistoryType.type, (XmlOptions)null);
        }
        
        public static GenericHistoryType newInstance(final XmlOptions options) {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().newInstance(GenericHistoryType.type, options);
        }
        
        public static GenericHistoryType parse(final String xmlAsString) throws XmlException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(xmlAsString, GenericHistoryType.type, (XmlOptions)null);
        }
        
        public static GenericHistoryType parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(xmlAsString, GenericHistoryType.type, options);
        }
        
        public static GenericHistoryType parse(final File file) throws XmlException, IOException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(file, GenericHistoryType.type, (XmlOptions)null);
        }
        
        public static GenericHistoryType parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(file, GenericHistoryType.type, options);
        }
        
        public static GenericHistoryType parse(final URL u) throws XmlException, IOException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(u, GenericHistoryType.type, (XmlOptions)null);
        }
        
        public static GenericHistoryType parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(u, GenericHistoryType.type, options);
        }
        
        public static GenericHistoryType parse(final InputStream is) throws XmlException, IOException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(is, GenericHistoryType.type, (XmlOptions)null);
        }
        
        public static GenericHistoryType parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(is, GenericHistoryType.type, options);
        }
        
        public static GenericHistoryType parse(final Reader r) throws XmlException, IOException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(r, GenericHistoryType.type, (XmlOptions)null);
        }
        
        public static GenericHistoryType parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(r, GenericHistoryType.type, options);
        }
        
        public static GenericHistoryType parse(final XMLStreamReader sr) throws XmlException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(sr, GenericHistoryType.type, (XmlOptions)null);
        }
        
        public static GenericHistoryType parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(sr, GenericHistoryType.type, options);
        }
        
        public static GenericHistoryType parse(final Node node) throws XmlException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(node, GenericHistoryType.type, (XmlOptions)null);
        }
        
        public static GenericHistoryType parse(final Node node, final XmlOptions options) throws XmlException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(node, GenericHistoryType.type, options);
        }
        
        @Deprecated
        public static GenericHistoryType parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(xis, GenericHistoryType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static GenericHistoryType parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (GenericHistoryType)XmlBeans.getContextTypeLoader().parse(xis, GenericHistoryType.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, GenericHistoryType.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, GenericHistoryType.type, options);
        }
        
        private Factory() {
        }
    }
}
