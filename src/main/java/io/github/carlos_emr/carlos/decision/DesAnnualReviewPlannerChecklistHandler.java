/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.decision;

import java.util.Enumeration;
import java.util.Properties;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX XML parser handler for generating annual health review planning checklists in HTML format.
 * <p>
 * This handler processes XML-based clinical decision support rules to generate interactive
 * checklists for long-term care and annual health assessments. The generated checklists
 * are personalized based on patient risk factors and display only relevant items that
 * healthcare providers need to address during routine care.
 * </p>
 * <p>
 * The handler generates HTML tables with checkboxes for "Done" and "N/A" status tracking,
 * allowing healthcare providers to systematically work through evidence-based care
 * recommendations during patient encounters.
 * </p>
 * <p>
 * <strong>Clinical Context:</strong> Annual health reviews are systematic preventive care
 * assessments that help ensure comprehensive patient care by addressing age-appropriate
 * screenings, immunizations, and chronic disease management based on established clinical
 * guidelines and patient-specific risk factors.
 * </p>
 *
 * @see DesAnnualReviewPlannerChecklist
 * @see DesAnnualReviewPlannerRiskHandler
 * @since July 2003
 */
public class DesAnnualReviewPlannerChecklistHandler extends DefaultHandler {

    /** XML document locator for tracking parsing position */
    private Locator locator;

    /** Accumulated HTML results from XML processing */
    private String results = "";

    /** Flag indicating whether current XML content should be displayed */
    private boolean display = false;

    /** Risk factors data (legacy string format) */
    private String risks;

    /** Current section number being processed */
    private String sectionno;

    /** Properties containing saved risk assessment parameters */
    private Properties savedar1params;

    /**
     * Constructs a checklist handler with legacy string-based risk parameters.
     * <p>
     * This constructor is maintained for backward compatibility with older
     * implementations that passed risk data as formatted strings.
     * </p>
     *
     * @param savedltcrisks String containing formatted risk factor data
     */
    public DesAnnualReviewPlannerChecklistHandler(String savedltcrisks) {
        risks = savedltcrisks;
    }

    /**
     * Constructs a checklist handler with Properties-based risk parameters.
     * <p>
     * This is the preferred constructor that accepts risk factors as Properties,
     * allowing for structured access to patient risk data during checklist generation.
     * The Properties should contain key-value pairs where keys are risk factor names
     * and values indicate the presence or assessment of each risk.
     * </p>
     *
     * @param savedltcrisks Properties containing patient risk factor assessments
     */
    public DesAnnualReviewPlannerChecklistHandler(Properties savedltcrisks) {
        savedar1params = savedltcrisks;

        // Debug logging to trace risk parameter processing
        for (Enumeration e = savedar1params.propertyNames(); e.hasMoreElements(); ) {
            MiscUtils.getLogger().debug("Processing risk parameter: " + e.nextElement());
        }
    }

    /**
     * Sets the document locator for tracking XML parsing position.
     *
     * @param locator Locator object providing document position information
     */
    public void setDocumentLocator(Locator locator) {
        this.locator = locator;
    }

    /**
     * Handles the start of XML document processing.
     * <p>
     * Initializes the HTML output with the checklist header table including
     * "Done" and "N/A" checkbox columns. The header uses healthcare-appropriate
     * colors and layout for clinical workflow integration.
     * </p>
     *
     * @throws SAXException if XML processing error occurs
     */
    public void startDocument() throws SAXException {
        // Initialize HTML structure with header table
        results = "<table width='100%' border='0' cellpadding='0' cellspaceing='0' BGCOLOR='#009966'>";
        results += "<tr><td width='5%'><font color='yellow'><B>Done</B></font></td><td width='5%'><font color='yellow'><B>N/A</B></font></td><td align='center' width='90%' ><font size=-1 color='#FFFFFF'>Long Term Care Checklist Based on Presented Risk Factors</font></td></tr></table>";
        results += "<center><table width='100%' border='0' cellpadding='0' CELLSPACING='0' BGCOLOR='ivory' datasrc='#xml_list'><tr><td>\n";

        MiscUtils.getLogger().debug("Initializing checklist with parameters: " + savedar1params);
    }

    /**
     * Handles the end of XML document processing.
     * <p>
     * Closes the HTML table structure and completes the checklist formatting.
     * </p>
     *
     * @throws SAXException if XML processing error occurs
     */
    public void endDocument() throws SAXException {
        results += "</td></tr></table></center>\n";
    }

    /**
     * Handles XML processing instructions.
     *
     * @param target String target of the processing instruction
     * @param data String data content of the processing instruction
     * @throws SAXException if XML processing error occurs
     */
    public void processingInstruction(String target, String data) throws SAXException {
        MiscUtils.getLogger().debug("Processing instruction - Target: " + target + ", Data: " + data);
    }

    /**
     * Handles the start of XML elements during checklist generation.
     * <p>
     * This method processes various XML elements to generate HTML checklist items:
     * <ul>
     * <li><code>section</code> - Creates section headers with numbering</li>
     * <li><code>section_title</code> - Formats section title display</li>
     * <li><code>risk</code> - Evaluates risk factors against patient data</li>
     * <li><code>item</code> - Generates checklist items with checkboxes</li>
     * <li>HTML formatting tags (B, I, FONT, A) - Preserves formatting</li>
     * </ul>
     * Only displays items relevant to the patient's identified risk factors.
     * </p>
     *
     * @param namespaceURI String namespace URI of the element
     * @param localName String local name of the element
     * @param rawName String qualified name of the element
     * @param atts Attributes associated with the element
     * @throws SAXException if XML processing error occurs
     */
    public void startElement(String namespaceURI, String localName, String rawName, Attributes atts)
            throws SAXException {
        MiscUtils.getLogger().debug("Processing element - localName: " + localName + ", rawName: " + rawName);
        String starttag, endtag, riskName, clName;

        // Process section elements - create section containers with numbering
        if ((rawName.toLowerCase()).equals("section")) {
            sectionno = "";
            results += "<center><table width='100%' border='0' cellpadding='0' CELLSPACING='0' datasrc='#xml_list'>";
            for (int i = 0; i < atts.getLength(); i++) {
                if (atts.getQName(i).toLowerCase().equals("number"))
                    sectionno = atts.getValue(i);
            }
        }
        // Process section titles - display with section numbering
        if (rawName.equals("section_title")) {
            results += "<tr BGCOLOR='#CCFFCC'><td colspan='3' align='center'>" + sectionno + ". ";
            display = true;
        }
        // Process risk elements - check if risk factor applies to this patient
        if (rawName.equals("risk")) {
            for (int i = 0; i < atts.getLength(); i++) {
                if ("riskname".equals(atts.getQName(i))) {
                    riskName = atts.getValue(i);
                    MiscUtils.getLogger().debug("Evaluating risk factor: " + riskName);

                    // Debug log all available risk parameters
                    for (Enumeration e = savedar1params.propertyNames(); e.hasMoreElements(); ) {
                        String paramName = (String) e.nextElement();
                        MiscUtils.getLogger().debug("Available risk parameter: " + paramName);
                    }

                    // Check if this risk factor applies to the current patient
                    if (savedar1params.getProperty(riskName) != null) {
                        display = true;
                        break;
                    }
                }
            }
        }
        // Process checklist items - generate interactive checkboxes for relevant items
        if (rawName.equals("item") && display) {
            results += "<tr>";
            for (int i = 0; i < atts.getLength(); i++) {
                if ("clname".equals(atts.getQName(i))) {
                    clName = atts.getValue(i);

                    // Create "Done" checkbox
                    results += "<td width='5%'>";
                    results += "<input type=checkbox name=\"checklist_" + clName
                            + "_done\" value='checked' id='checklist_" + clName + "_done'>";
                    results += "</td>";

                    // Create "N/A" checkbox
                    results += "<td width='5%'>";
                    results += "<input type=checkbox name=\"checklist_" + clName
                            + "_na\" value='checked' id='checklist_" + clName + "_na'>";
                    results += "</td>";
                }
            }
            results += "<td width='90%'>";
        }

        // Process HTML formatting elements - preserve original formatting
        if (rawName.equals("B") || rawName.equals("b")) {
            if (display)
                results += "<b>";
        }
        if (rawName.equals("I") || rawName.equals("i")) {
            if (display)
                results += "<i>";
        }
        if (rawName.equals("FONT") || rawName.equals("font")) {
            if (display) {
                results += "<font ";
                for (int i = 0; i < atts.getLength(); i++) {
                    results += atts.getQName(i) + "='" + atts.getValue(i) + "' ";
                }
                results += ">";
            }
        }
        if (rawName.equals("A") || rawName.equals("a")) {
            if (display) {
                results += "<a href=# onClick=\"popupPage(400,500,'";
                for (int i = 0; i < atts.getLength(); i++) {
                    results += atts.getValue(i) + "');return false;\">";
                }
            }
        }

    }

    /**
     * Handles the end of XML elements during checklist generation.
     * <p>
     * Closes HTML tags and resets display flags as appropriate for each element type.
     * Ensures proper HTML structure is maintained in the generated checklist.
     * </p>
     *
     * @param namespaceURI String namespace URI of the element
     * @param localName String local name of the element
     * @param rawName String qualified name of the element
     * @throws SAXException if XML processing error occurs
     */
    public void endElement(String namespaceURI, String localName, String rawName) throws SAXException {
        if (rawName.equals("section")) {
            results += "</table></center><br>\n";
        }
        if (rawName.equals("section_title")) {
            results += "</td></tr>\n";
            display = false;
        }
        if (rawName.equals("risk")) {
            display = false;
        }
        if (rawName.equals("item") && display) {
            results += "</td></tr>\n";
        }

        // Close HTML formatting tags
        if (rawName.equals("B") || rawName.equals("b")) {
            if (display)
                results += "</b>";
        }
        if (rawName.equals("I") || rawName.equals("i")) {
            if (display)
                results += "</i>";
        }
        if (rawName.equals("FONT") || rawName.equals("font")) {
            if (display)
                results += "</font>";
        }
        if (rawName.equals("A") || rawName.equals("a")) {
            if (display) {
                results += "</a>";
            }
        }
    }

    /**
     * Handles character data within XML elements.
     * <p>
     * Appends text content to the HTML output when the current element
     * should be displayed based on patient risk factors.
     * </p>
     *
     * @param ch char[] character array containing the text
     * @param start int starting position in the array
     * @param length int length of text to process
     * @throws SAXException if XML processing error occurs
     */
    public void characters(char[] ch, int start, int length) throws SAXException {
        String s = new String(ch, start, length);

        if (display) {
            results += s;
        }
    }

    /**
     * Handles ignorable whitespace in XML content.
     * <p>
     * This implementation ignores whitespace as it's not needed for
     * the generated HTML checklist output.
     * </p>
     *
     * @param ch char[] character array containing whitespace
     * @param start int starting position in the array
     * @param length int length of whitespace to process
     * @throws SAXException if XML processing error occurs
     */
    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        // Whitespace is ignored for HTML output generation
    }

    /**
     * Handles skipped XML entities during parsing.
     *
     * @param name String name of the skipped entity
     * @throws SAXException if XML processing error occurs
     */
    public void skippedEntity(String name) throws SAXException {
        MiscUtils.getLogger().debug("Skipping XML entity: " + name);
    }

    /**
     * Returns the generated HTML checklist content.
     * <p>
     * This method should be called after XML parsing is complete to retrieve
     * the formatted HTML checklist based on the patient's risk factors.
     * The returned HTML includes interactive checkboxes for tracking care
     * completion and can be embedded in healthcare provider workflows.
     * </p>
     *
     * @return String containing the complete HTML checklist
     */
    public String getResults() {
        return results;
    }

}
