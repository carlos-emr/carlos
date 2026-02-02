package io.github.carlos_emr.carlos.ckd;

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
 * Drug configuration interface for CKD medication management.
 *
 * This XMLBeans-generated interface manages the collection of medications relevant
 * to CKD patient care. It includes both contraindicated drugs (nephrotoxic medications
 * to avoid) and recommended medications for CKD treatment protocols.
 *
 * The drug list serves multiple clinical purposes:
 * - Identifying nephrotoxic medications that require dose adjustment or avoidance
 * - Tracking ACE inhibitors and ARBs for renoprotective therapy
 * - Managing phosphate binders and other CKD-specific medications
 * - Supporting drug interaction checking in the context of reduced renal clearance
 * - Guiding prescribing decisions based on eGFR levels
 *
 * Common CKD-relevant drug categories include:
 * - NSAIDs (generally contraindicated)
 * - Metformin (requires dose adjustment based on eGFR)
 * - ACE inhibitors/ARBs (renoprotective but require monitoring)
 * - Phosphate binders (for hyperphosphatemia management)
 * - Erythropoiesis-stimulating agents (for anemia management)
 *
 * @since 2010-01-01
 * @see CKDConfig
 * @see io.github.carlos_emr.carlos.ckd.impl.DrugsImpl
 */
public interface Drugs extends XmlObject
{
    /**
     * SchemaType system definition for XMLBeans type mapping.
     */
    public static final SchemaType type = (SchemaType)XmlBeans.typeSystemForClassLoader(Drugs.class.getClassLoader(), "schemaorg_apache_xmlbeans.system.s9F21579FEC5BB34270776B6AF571836F").resolveHandle("drugs98d8type");

    /**
     * Gets all configured drugs as an array.
     *
     * Returns the complete list of medications relevant to CKD management.
     * Each drug entry typically contains the medication name, ATC code, or
     * other identifying information used by the prescribing system.
     *
     * @return String[] array of all configured drug identifiers
     */
    String[] getDrugArray();

    /**
     * Gets a specific drug by index.
     *
     * Retrieves a single drug identifier from the collection at the
     * specified position.
     *
     * @param p0 int zero-based index of the drug to retrieve
     * @return String drug identifier at the specified index
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    String getDrugArray(final int p0);

    /**
     * Gets all drugs as XML string objects.
     *
     * Returns the drug collection as XMLBeans XmlString objects,
     * providing access to full XML type information.
     *
     * @return XmlString[] array of drugs with XML type information
     */
    XmlString[] xgetDrugArray();

    /**
     * Gets a specific drug as XML string by index.
     *
     * Retrieves a single drug as an XmlString object with full
     * XML type information.
     *
     * @param p0 int zero-based index of the drug
     * @return XmlString drug with XML type information
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    XmlString xgetDrugArray(final int p0);

    /**
     * Gets the number of drugs in the collection.
     *
     * Returns the total count of configured medications.
     *
     * @return int number of drugs configured
     */
    int sizeOfDrugArray();

    /**
     * Sets the entire drug array.
     *
     * Replaces all existing drug entries with the provided array.
     * This completely overwrites the current drug configuration.
     *
     * @param p0 String[] array of drug identifiers to set
     */
    void setDrugArray(final String[] p0);

    /**
     * Sets a specific drug at an index.
     *
     * Updates or replaces the drug at the specified position.
     *
     * @param p0 int zero-based index where to set the drug
     * @param p1 String drug identifier to set
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void setDrugArray(final int p0, final String p1);

    /**
     * Sets the entire drug array using XML strings.
     *
     * Replaces all drugs using XmlString objects with type information.
     *
     * @param p0 XmlString[] array of drugs with XML type information
     */
    void xsetDrugArray(final XmlString[] p0);

    /**
     * Sets a specific drug using XML string at an index.
     *
     * Updates the drug at the specified position using an XmlString.
     *
     * @param p0 int zero-based index where to set the drug
     * @param p1 XmlString drug with XML type information
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void xsetDrugArray(final int p0, final XmlString p1);

    /**
     * Inserts a drug at a specific index.
     *
     * Inserts a new drug at the specified position, shifting existing
     * drugs to make room.
     *
     * @param p0 int zero-based index for insertion
     * @param p1 String drug identifier to insert
     */
    void insertDrug(final int p0, final String p1);

    /**
     * Adds a drug to the collection.
     *
     * Appends a new drug identifier to the end of the drug list.
     *
     * @param p0 String drug identifier to add
     */
    void addDrug(final String p0);

    /**
     * Inserts a new empty drug at a specific index.
     *
     * Creates and inserts a new XmlString at the specified position.
     * The returned instance can be populated with drug information.
     *
     * @param p0 int zero-based index for insertion
     * @return XmlString newly created drug entry
     */
    XmlString insertNewDrug(final int p0);

    /**
     * Adds a new empty drug to the collection.
     *
     * Creates and appends a new XmlString to the drug list.
     * The returned instance can be configured with drug details.
     *
     * @return XmlString newly created drug entry
     */
    XmlString addNewDrug();

    /**
     * Removes a drug at a specific index.
     *
     * Deletes the drug at the specified position, shifting remaining
     * drugs to fill the gap.
     *
     * @param p0 int zero-based index of the drug to remove
     * @throws ArrayIndexOutOfBoundsException if index is invalid
     */
    void removeDrug(final int p0);

    /**
     * Factory class for creating and parsing Drugs instances.
     *
     * Provides static methods for instantiating and deserializing drug
     * configurations from various sources. Follows the XMLBeans factory
     * pattern for flexible configuration loading.
     */
    public static final class Factory
    {
        /**
         * Creates a new empty Drugs instance.
         *
         * @return Drugs new empty drug configuration
         */
        public static Drugs newInstance() {
            return (Drugs)XmlBeans.getContextTypeLoader().newInstance(Drugs.type, (XmlOptions)null);
        }

        /**
         * Creates a new Drugs instance with XML options.
         *
         * @param options XmlOptions for XML processing configuration
         * @return Drugs new drug configuration instance
         */
        public static Drugs newInstance(final XmlOptions options) {
            return (Drugs)XmlBeans.getContextTypeLoader().newInstance(Drugs.type, options);
        }

        /**
         * Parses drug configuration from an XML string.
         *
         * @param xmlAsString String containing XML configuration
         * @return Drugs parsed drug configuration
         * @throws XmlException if XML is malformed or invalid
         */
        public static Drugs parse(final String xmlAsString) throws XmlException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(xmlAsString, Drugs.type, (XmlOptions)null);
        }
        
        public static Drugs parse(final String xmlAsString, final XmlOptions options) throws XmlException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(xmlAsString, Drugs.type, options);
        }
        
        public static Drugs parse(final File file) throws XmlException, IOException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(file, Drugs.type, (XmlOptions)null);
        }
        
        public static Drugs parse(final File file, final XmlOptions options) throws XmlException, IOException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(file, Drugs.type, options);
        }
        
        public static Drugs parse(final URL u) throws XmlException, IOException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(u, Drugs.type, (XmlOptions)null);
        }
        
        public static Drugs parse(final URL u, final XmlOptions options) throws XmlException, IOException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(u, Drugs.type, options);
        }
        
        public static Drugs parse(final InputStream is) throws XmlException, IOException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(is, Drugs.type, (XmlOptions)null);
        }
        
        public static Drugs parse(final InputStream is, final XmlOptions options) throws XmlException, IOException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(is, Drugs.type, options);
        }
        
        public static Drugs parse(final Reader r) throws XmlException, IOException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(r, Drugs.type, (XmlOptions)null);
        }
        
        public static Drugs parse(final Reader r, final XmlOptions options) throws XmlException, IOException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(r, Drugs.type, options);
        }
        
        public static Drugs parse(final XMLStreamReader sr) throws XmlException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(sr, Drugs.type, (XmlOptions)null);
        }
        
        public static Drugs parse(final XMLStreamReader sr, final XmlOptions options) throws XmlException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(sr, Drugs.type, options);
        }
        
        public static Drugs parse(final Node node) throws XmlException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(node, Drugs.type, (XmlOptions)null);
        }
        
        public static Drugs parse(final Node node, final XmlOptions options) throws XmlException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(node, Drugs.type, options);
        }
        
        @Deprecated
        public static Drugs parse(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(xis, Drugs.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static Drugs parse(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return (Drugs)XmlBeans.getContextTypeLoader().parse(xis, Drugs.type, options);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Drugs.type, (XmlOptions)null);
        }
        
        @Deprecated
        public static XMLInputStream newValidatingXMLInputStream(final XMLInputStream xis, final XmlOptions options) throws XmlException, XMLStreamException {
            return XmlBeans.getContextTypeLoader().newValidatingXMLInputStream(xis, Drugs.type, options);
        }

        /**
         * Private constructor prevents instantiation.
         *
         * Factory is a utility class with static methods only.
         */
        private Factory() {
        }
    }
}
