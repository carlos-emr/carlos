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

public interface ObstetricalHistory extends XmlObject
{
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(ObstetricalHistory.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9C023B7D67311A3187802DA7FD51EA38").resolveHandle("obstetricalhistory22d7type");
    
    ObstetricalHistoryItemList[] getObsListArray();
    
    ObstetricalHistoryItemList getObsListArray(final int p0);
    
    int sizeOfObsListArray();
    
    void setObsListArray(final ObstetricalHistoryItemList[] p0);
    
    void setObsListArray(final int p0, final ObstetricalHistoryItemList p1);
    
    ObstetricalHistoryItemList insertNewObsList(final int p0);
    
    ObstetricalHistoryItemList addNewObsList();
    
    void removeObsList(final int p0);
    
    public static final class Factory
    {
        public static ObstetricalHistory newInstance() {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().newInstance(ObstetricalHistory.type, (XmlOptions)null);
        }
        
        public static ObstetricalHistory newInstance(final XmlOptions options) {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().newInstance(ObstetricalHistory.type, options);
        }
        
        public static ObstetricalHistory parse(final String xmlAsString) throws XmlException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(xmlAsString, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        public static ObstetricalHistory parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(xmlAsString, ObstetricalHistory.type, options);
        }
        
        public static ObstetricalHistory parse(final File file) throws XmlException, IOException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(file, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        public static ObstetricalHistory parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(file, ObstetricalHistory.type, options);
        }
        
        public static ObstetricalHistory parse(final URL u) throws XmlException, IOException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(u, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        public static ObstetricalHistory parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(u, ObstetricalHistory.type, options);
        }
        
        public static ObstetricalHistory parse(final InputStream is) throws XmlException, IOException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(is, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        public static ObstetricalHistory parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(is, ObstetricalHistory.type, options);
        }
        
        public static ObstetricalHistory parse(final Reader r) throws XmlException, IOException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(r, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        public static ObstetricalHistory parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(r, ObstetricalHistory.type, options);
        }
        
        public static ObstetricalHistory parse(final XMLStreamReader sr) throws XmlException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(sr, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        public static ObstetricalHistory parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(sr, ObstetricalHistory.type, options);
        }
        
        public static ObstetricalHistory parse(final Node node) throws XmlException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(node, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        public static ObstetricalHistory parse(final Node node, final XmlOptions options) throws XmlException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(node, ObstetricalHistory.type, options);
        }
        
        @Deprecated
        public static ObstetricalHistory parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(xis, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static ObstetricalHistory parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (ObstetricalHistory)XmlBeans.getContextTypeLoader().parse(xis, ObstetricalHistory.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ObstetricalHistory.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, ObstetricalHistory.type, options);
        }
        
        private Factory() {
        }
    }
}
