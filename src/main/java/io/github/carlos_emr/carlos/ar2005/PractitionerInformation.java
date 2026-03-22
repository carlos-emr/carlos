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
 * Interface for healthcare practitioner information.
 *
 * @since 2005-01-01
 */
public interface PractitionerInformation extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(PractitionerInformation.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("practitionerinformation4497type");
    
    BirthAttendants getBirthAttendants();
    
    void setBirthAttendants(final BirthAttendants p0);
    
    BirthAttendants addNewBirthAttendants();
    
    NewbornCare getNewbornCare();
    
    void setNewbornCare(final NewbornCare p0);
    
    NewbornCare addNewNewbornCare();
    
    String getFamilyPhysician();
    
    XmlString xgetFamilyPhysician();
    
    void setFamilyPhysician(final String p0);
    
    void xsetFamilyPhysician(final XmlString p0);
    
    public static final class Factory
    {
        public static PractitionerInformation newInstance() {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().newInstance(PractitionerInformation.type, (XmlOptions)null);
        }
        
        public static PractitionerInformation newInstance(final XmlOptions options) {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().newInstance(PractitionerInformation.type, options);
        }
        
        public static PractitionerInformation parse(final String xmlAsString) throws XmlException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(xmlAsString, PractitionerInformation.type, (XmlOptions)null);
        }
        
        public static PractitionerInformation parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(xmlAsString, PractitionerInformation.type, options);
        }
        
        public static PractitionerInformation parse(final File file) throws XmlException, IOException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(file, PractitionerInformation.type, (XmlOptions)null);
        }
        
        public static PractitionerInformation parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(file, PractitionerInformation.type, options);
        }
        
        public static PractitionerInformation parse(final URL u) throws XmlException, IOException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(u, PractitionerInformation.type, (XmlOptions)null);
        }
        
        public static PractitionerInformation parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(u, PractitionerInformation.type, options);
        }
        
        public static PractitionerInformation parse(final InputStream is) throws XmlException, IOException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(is, PractitionerInformation.type, (XmlOptions)null);
        }
        
        public static PractitionerInformation parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(is, PractitionerInformation.type, options);
        }
        
        public static PractitionerInformation parse(final Reader r) throws XmlException, IOException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(r, PractitionerInformation.type, (XmlOptions)null);
        }
        
        public static PractitionerInformation parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(r, PractitionerInformation.type, options);
        }
        
        public static PractitionerInformation parse(final XMLStreamReader sr) throws XmlException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(sr, PractitionerInformation.type, (XmlOptions)null);
        }
        
        public static PractitionerInformation parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(sr, PractitionerInformation.type, options);
        }
        
        public static PractitionerInformation parse(final Node node) throws XmlException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(node, PractitionerInformation.type, (XmlOptions)null);
        }
        
        public static PractitionerInformation parse(final Node node, final XmlOptions options) throws XmlException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(node, PractitionerInformation.type, options);
        }
        
        @Deprecated
        public static PractitionerInformation parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(xis, PractitionerInformation.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static PractitionerInformation parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (PractitionerInformation)XmlBeans.getContextTypeLoader().parse(xis, PractitionerInformation.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PractitionerInformation.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, PractitionerInformation.type, options);
        }
        
        private Factory() {
        }
    }
}
