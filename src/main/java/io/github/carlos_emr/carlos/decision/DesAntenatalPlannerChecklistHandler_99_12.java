//CHECKSTYLE:OFF
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
 */


package io.github.carlos_emr.carlos.decision;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Properties;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX XML parser handler for generating gestational week-specific antenatal care checklists.
 * <p>
 * This handler processes XML-based clinical decision support rules to create detailed
 * pregnancy care checklists organized by gestational week. It calculates visit dates
 * based on the estimated delivery date (EDD) and personalizes recommendations based
 * on identified maternal and fetal risk factors.
 * </p>
 * <p>
 * <strong>Clinical Context:</strong> Antenatal care follows evidence-based schedules
 * with specific assessments, screenings, and interventions timed to optimize maternal
 * and fetal outcomes. The systematic approach ensures comprehensive care delivery
 * from initial assessment through delivery preparation.
 * </p>
 * <p>
 * Key clinical features:
 * <ul>
 * <li>Gestational week calculation based on 280-day (40-week) pregnancy duration</li>
 * <li>Risk factor-driven personalization of care recommendations</li>
 * <li>Interactive checkbox tracking for "Done" and "N/A" status</li>
 * <li>Date calculations for visit scheduling and care timing</li>
 * </ul>
 * </p>
 *
 * @see DesAntenatalPlannerChecklist_99_12
 * @see DesAntenatalPlannerRisksHandler_99_12
 * @since July 2003
 */
public class DesAntenatalPlannerChecklistHandler_99_12 extends DefaultHandler {

    /** XML document locator for tracking parsing position */
    private Locator locator;

    /** Accumulated HTML results from XML processing */
    private String results = "";

    /** Current gestational week or item count being processed */
    private int count = 0;

    /** Flag indicating whether to display risk-related content with emphasis */
    private boolean disprisk = false;

    /** Flag indicating whether current item should be displayed */
    private boolean dispitem = true;

    /** Flag indicating whether current item includes checkbox inputs */
    private boolean checkbox = false;

    /** Properties containing EDD and maternal/fetal risk assessment data */
    private Properties savedar1params;

    /** Calendar object for calculating visit dates based on gestational week */
    GregorianCalendar cal;

    /** Calendar object representing the estimated conception date (EDD - 280 days) */
    GregorianCalendar now;

    /** Standard gestational week divisions for antenatal care scheduling */
    int[] weekDivisions = {0, 12, 16, 20, 24, 28, 32, 34, 36, 37, 38, 39, 40};

    /** Month names for formatted date display in clinical documentation */
    String[] monthNames = {"January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December"};

    /**
     * Constructs an antenatal checklist handler with pregnancy-specific parameters.
     * <p>
     * The Properties parameter must contain the final estimated delivery date (EDD)
     * and any identified maternal/fetal risk factors that will be used to
     * personalize the generated checklist content.
     * </p>
     *
     * @param newsavedparams Properties containing EDD ("finalEDB") and risk factor data
     */
    public DesAntenatalPlannerChecklistHandler_99_12(Properties newsavedparams) {
        savedar1params = newsavedparams;
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
     * Initializes the gestational calendar calculations and sets up the HTML structure
     * for the antenatal checklist. The checklist header includes "Done" and "N/A"
     * checkbox columns with clinical-appropriate styling for healthcare workflows.
     * </p>
     *
     * @throws SAXException if XML processing error occurs
     */
    public void startDocument() throws SAXException {
        init();
        results += "<center><table BORDER=0 CELLSPACING=0 CELLPADDING=1 WIDTH='100%' BGCOLOR='#009966'>\n";
        results += "<tr><td width='5%' bgcolor='ivory' align='center'><b><font color='black'>Done</font></b></td>\n<td width='5%' bgcolor='ivory' align='center'><b><font color='black'>N/A</font></b></td>";
        results += "<td align='center'><b><font color='white'>Antenatal Checklist</font></b></td></tr></table></center>";
        results += "<center><table width='100%' border='0' cellpadding='0' CELLSPACING='0' BGCOLOR='ivory'><tr><td>\n";
    }

    /**
     * Handles the end of XML document processing.
     * <p>
     * Closes the HTML table structure and completes the antenatal checklist formatting.
     * </p>
     *
     * @throws SAXException if XML processing error occurs
     */
    public void endDocument() throws SAXException {
        results += "</td></tr></table></center>\n";
    }


    /**
     * Handles the start of XML elements during antenatal checklist generation.
     * <p>
     * This method processes various XML elements to create gestational week-specific content:
     * <ul>
     * <li><code>recommendations</code> - Creates the main checklist container</li>
     * <li><code>week</code> - Processes gestational week sections with calculated visit dates</li>
     * <li><code>item</code> - Generates checklist items with risk-based filtering and checkboxes</li>
     * <li>HTML formatting tags (B, I, FONT, A) - Preserves clinical documentation formatting</li>
     * </ul>
     * The method calculates visit dates by adding gestational weeks to the conception date
     * (EDD - 280 days) and displays only items relevant to identified risk factors.
     * </p>
     *
     * @param namespaceURI String namespace URI of the element
     * @param localName String local name of the element
     * @param rawName String qualified name of the element
     * @param atts Attributes associated with the element
     * @throws SAXException if XML processing error occurs
     */
    public void startElement(String namespaceURI, String localName, String rawName, Attributes atts) throws SAXException {
        // Process recommendations element - main checklist container
        if (rawName.equals("recommendations")) {
            results += "<center><table border=0 cellspacing=1 cellpadding=1 width=\"100%\">\n\n";
        }

        // Process week elements - gestational week sections with calculated visit dates
        if (rawName.equals("week")) {
            for (int i = 0; i < atts.getLength(); i++) {
                if (atts.getQName(i) == "number") {
                    count = Integer.parseInt(atts.getValue(i));
                }
            }

            // Calculate visit date by adding gestational weeks to conception date
            cal.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            cal.add(Calendar.DATE, count * 7);
            results += "<table border=0 cellspacing=1 cellpadding=1 width=\"100%\" datasrc='#xml_list'>\n";
            results += "<tr bgcolor='#CCFFCC'><td width='5%'></td><td width='5%'></td><td colspan='1'><span CLASS='.title'>\n";

            // Display week header with visit date calculation
            if (count == 0) {
                results += "Initial Assessment - Week " + count;
            } else {
                results += monthNames[cal.get(Calendar.MONTH)] + " " + cal.get(Calendar.DAY_OF_MONTH) + ", " + cal.get(Calendar.YEAR) + " - Week " + count;
            }

            results += "</span></td></tr>";
        }

        // Process item elements - checklist items with risk-based filtering
        if (rawName.equals("item")) {
            checkbox = false;
            count = 3;
            String clname = "";
            String riskname = "";

            // Parse item attributes for name, risk factors, and checkbox requirement
            for (int i = 0; i < atts.getLength(); i++) {
                count = 1;
                if (atts.getQName(i) == "name") clname = atts.getValue(i);
                if (atts.getQName(i) == "risk") riskname = atts.getValue(i);
                if (atts.getQName(i) == "checkbox") checkbox = true;
            }

            // Display item only if no specific risk factor or if risk factor is present
            if (riskname.equals("") || savedar1params.getProperty(riskname) != null) {
                results += "<tr>";

                // Generate checkboxes for tracking completion status
                if (checkbox) {
                    results += "<td width='5%' align='center'><input type='checkbox' name='checklist_" + clname + "d' value='checked' id='checklist_" + clname + "d'></td><td width='5%' align='center'><input type='checkbox' name='checklist_" + clname + "na' value='checked' id='checklist_" + clname + "na'></td>\n";
                } else {
                    results += "<td></td><td></td>";
                }
                results += "<td colspan='" + count + "'>";

                // Emphasize risk-related items with bold formatting
                if (savedar1params.getProperty(riskname) != null) {
                    results += "<b>";
                    disprisk = true;
                } else {
                    disprisk = false;
                }
                dispitem = true;
            } else {
                disprisk = false;
                dispitem = false;
            }
        }

        // Process HTML formatting elements - preserve clinical documentation formatting
        if (rawName.equals("B") || rawName.equals("b")) {
            if (dispitem)
                results += "<b>";
        }
        if (rawName.equals("I") || rawName.equals("i")) {
            if (dispitem)
                results += "<i>";
        }
        if (rawName.equals("FONT") || rawName.equals("font")) {
            if (dispitem) {
                results += "<font ";
                for (int i = 0; i < atts.getLength(); i++) {
                    results += atts.getQName(i) + "='" + atts.getValue(i) + "' ";
                }
                results += ">";
            }
        }
        if (rawName.equals("A") || rawName.equals("a")) {
            if (dispitem) {
                results += "<a href=# onClick=\"popupPage(400,500,'";
                for (int i = 0; i < atts.getLength(); i++) {
                    results += atts.getValue(i) + "');return false;\">";
                }
            }
        }
    }

    /**
     * Handles the end of XML elements during antenatal checklist generation.
     * <p>
     * Closes HTML tag structures and resets display flags as appropriate for
     * each element type. Ensures proper HTML structure is maintained in the
     * generated gestational week-based checklist.
     * </p>
     *
     * @param namespaceURI String namespace URI of the element
     * @param localName String local name of the element
     * @param rawName String qualified name of the element
     * @throws SAXException if XML processing error occurs
     */
    public void endElement(String namespaceURI, String localName, String rawName) throws SAXException {
        if (rawName.equals("recommendations")) {
            results += "</td></tr></table></center>\n";
        }
        if (rawName.equals("week")) {
            results += "</table>\n";
        }
        if (rawName.equals("item")) {
            if (disprisk) results += "</b>";
            if (dispitem) results += "</td></tr>\n";
            disprisk = true;
            dispitem = true;
        }

        // Close HTML formatting tags
        if (rawName.equals("B") || rawName.equals("b")) {
            if (dispitem) results += "</b>";
        }
        if (rawName.equals("I") || rawName.equals("i")) {
            if (dispitem) results += "</i>";
        }
        if (rawName.equals("FONT") || rawName.equals("font")) {
            if (dispitem) results += "</font>";
        }
        if (rawName.equals("A") || rawName.equals("a")) {
            if (dispitem) results += "</a>";
        }
    }

    /**
     * Handles character data within XML elements.
     * <p>
     * Appends text content to the HTML output when the current element
     * should be displayed based on risk factors and display flags.
     * This ensures only relevant clinical content appears in the checklist.
     * </p>
     *
     * @param ch char[] character array containing the text
     * @param start int starting position in the array
     * @param length int length of text to process
     * @throws SAXException if XML processing error occurs
     */
    public void characters(char[] ch, int start, int length) throws SAXException {
        if (disprisk || dispitem) {
            String s = new String(ch, start, length);
            results += s;
        }
    }

    /**
     * Handles ignorable whitespace in XML content.
     * <p>
     * This implementation ignores whitespace as it's not needed for
     * the generated HTML antenatal checklist output.
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
     * Returns the generated HTML antenatal checklist content.
     * <p>
     * This method should be called after XML parsing is complete to retrieve
     * the formatted HTML checklist organized by gestational week. The returned
     * HTML includes calculated visit dates, interactive checkboxes for tracking
     * care completion, and personalized content based on risk factors.
     * </p>
     *
     * @return String containing the complete HTML antenatal checklist with visit dates
     */
    public String getResults() {
        return results;
    }

    /**
     * Initializes calendar objects for gestational week calculations.
     * <p>
     * This method sets up the calendar system used to calculate visit dates
     * throughout pregnancy. It uses the standard 280-day (40-week) pregnancy
     * duration to determine the conception date from the estimated delivery date.
     * </p>
     * <p>
     * The calculation assumes:
     * <ul>
     * <li>Standard 280-day pregnancy duration from last menstrual period</li>
     * <li>Final EDB (estimated date of birth) provided in yyyy-MM-dd format</li>
     * <li>Conception date = EDB - 280 days for gestational week calculations</li>
     * </ul>
     * </p>
     */
    private void init() {
        cal = new GregorianCalendar();
        now = new GregorianCalendar();
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd");

        try {
            // Parse the final estimated delivery date
            now.setTime(df.parse(savedar1params.getProperty("finalEDB")));
        } catch (java.text.ParseException pe) {
            // If parsing fails, calendar remains at current date
            MiscUtils.getLogger().debug("Failed to parse finalEDB date: " + savedar1params.getProperty("finalEDB"));
        }

        // Calculate conception date by subtracting 280 days (40 weeks) from EDD
        now.add(Calendar.DATE, -280);
    }

}
