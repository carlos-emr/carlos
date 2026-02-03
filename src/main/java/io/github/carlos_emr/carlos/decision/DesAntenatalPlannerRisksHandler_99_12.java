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

import java.util.Properties;

import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX XML parser handler for generating antenatal (pregnancy) risk assessment forms.
 * <p>
 * This handler processes XML-based clinical decision support rules to create interactive
 * HTML forms for comprehensive maternal and fetal risk factor assessment during pregnancy
 * care. The "99_12" designation indicates this implementation follows clinical guidelines
 * effective from December 1999.
 * </p>
 * <p>
 * <strong>Clinical Context:</strong> Antenatal risk assessment is essential for identifying
 * maternal and fetal conditions that may require specialized monitoring, interventions,
 * or modified care plans. The systematic collection of risk factor data enables healthcare
 * providers to deliver evidence-based, personalized pregnancy care and optimize outcomes
 * for both mother and baby.
 * </p>
 * <p>
 * The handler generates HTML forms with pregnancy-specific clinical workflow integration:
 * <ul>
 * <li>Structured risk factor categories (medical history, current pregnancy, lifestyle)</li>
 * <li>Interactive checkboxes for binary risk factors</li>
 * <li>Text input fields for quantitative risk data</li>
 * <li>Popup windows for detailed clinical information and guidance</li>
 * </ul>
 * </p>
 *
 * @see DesAntenatalPlannerRisks_99_12
 * @see DesAntenatalPlannerChecklistHandler_99_12
 * @since July 2003
 */
public class DesAntenatalPlannerRisksHandler_99_12 extends DefaultHandler {

    /** XML document locator for tracking parsing position */
    private Locator locator;

    /** Accumulated HTML results from XML processing */
    private String results;

    /** Current risk factor name being processed */
    private String riskName;

    /** Column counter for table layout management */
    private int colcount = 0;

    /** Number of columns in the current table layout */
    private int numcols = 1;

    /** Flag indicating if currently inside an interior table structure */
    private int interiortable = 0;

    /** Flag indicating if current element has hyperlink reference */
    private int href = 0;

    /** Properties object storing pregnancy risk factor names and identifiers */
    private Properties riskNameObj = null;

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
     * Initializes the risk name collection and sets up the base HTML structure
     * for the antenatal risk assessment form. The form uses clinical-appropriate
     * styling with ivory background for professional healthcare presentation.
     * </p>
     *
     * @throws SAXException if XML processing error occurs
     */
    public void startDocument() throws SAXException {
        riskNameObj = new Properties();
        results = "<center><table width='100%' border='0' cellpadding='0' CELLSPACING='0' BGCOLOR='ivory'><tr><td>\n";
    }

    /**
     * Handles the end of XML document processing.
     * <p>
     * Ensures all HTML table structures are properly closed and completes
     * the antenatal risk assessment form generation.
     * </p>
     *
     * @throws SAXException if XML processing error occurs
     */
    public void endDocument() throws SAXException {
        // Ensure interior tables are properly closed
        if (interiortable == 1) {
            results += "</center></td></tr></table>";
        }
        results += "</td></tr></table></center>\n";
    }


    /**
     * Handles the start of XML elements during antenatal risk assessment form generation.
     * <p>
     * This method processes various XML elements to create structured HTML forms for
     * pregnancy-specific risk assessment:
     * <ul>
     * <li><code>section_title</code> - Creates major section headers with clinical styling</li>
     * <li><code>subsection_title</code> - Creates subsection headers for organized content</li>
     * <li><code>risk</code> - Generates checkbox inputs for binary maternal/fetal risk factors</li>
     * <li><code>entry</code> - Generates text input fields for quantitative pregnancy risk data</li>
     * <li><code>heading</code> - Creates formatted headings within sections</li>
     * </ul>
     * The method maintains proper HTML table structure optimized for clinical workflows.
     * </p>
     *
     * @param namespaceURI String namespace URI of the element
     * @param localName String local name of the element
     * @param rawName String qualified name of the element
     * @param atts Attributes associated with the element
     * @throws SAXException if XML processing error occurs
     */
    public void startElement(String namespaceURI, String localName, String rawName, Attributes atts) throws SAXException {
        if (rawName.equals("section_title")) {
            if (interiortable == 1) { //close content table
                results += "</center></td></tr></table>";
                interiortable = 0;
                colcount = 0;
            }
            results += "<table border=0 cellspacing=0 cellpadding=0 width=\"100%\">\n";
            results += "<tr><td BGCOLOR='#009966' align='center'>\n";
            results += "<font size=-1 color='#FFFFFF'>";
        } else if (rawName.equals("subsection_title")) {
            if (interiortable == 1) { //close table
                results += "</center></td></tr></table>";
                interiortable = 0;
                colcount = 0;
            }
            results += "<center><table border=0 cellpadding=0 cellspacing=0 width=\"98%\">";
            results += "<tr><td BGCOLOR='#CCFFCC' align='center'>\n";
            results += "<font size=-1>";
        } else if (rawName.equals("risk") || rawName.equals("entry")) {
            if (interiortable == 0) { //table beginning
                results += "<center><table border=0 cellpadding=2 cellspacing=2 width=\"98%\" datasrc='#xml_list' BGCOLOR='silver'>";
                interiortable = 1;
                colcount = 0;
            }
            if (colcount == 0) results += "<tr BGCOLOR='ivory'><td width=" + 10 / numcols + "% >"; //the first td
            else {
                if (colcount % numcols == 0) { //tr td  new line beginning?
                    results += "</td></tr>\n<tr BGCOLOR='ivory'><td width=" + 10 / numcols + "% >";
                } else {
                    results += "</td><td width=" + 10 / numcols + "% >";
                    //results += "</td><td width="+10/numcols+"% >";
                }
            }
            results += "<font size=-2>";
            colcount += 1;
        } else if (rawName.equals("heading")) {
            if (interiortable == 1) { //close table
                results += "</center></td></tr></table>";
                interiortable = 0;
                colcount = 0;
            }
            results += "<table border=0 cellpadding=0 cellspacing=0 width=\"98%\">";
            results += "<tr><td align='center'><font size=-2><b>\n";
        }
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getQName(i) == "name") {
                riskName = atts.getValue(i);
                results += "<input type=checkbox name=\"risk_" + riskName + "\" value='checked' id='risk_" + riskName + "'></font></td><td width=" + 100 / numcols + "% >";
                riskNameObj.setProperty(riskName, "checked");
            }
            if (atts.getQName(i) == "href") {
                results += "<a href=# onClick=\"popupPage(400,500,'" + atts.getValue(i) + "');return false;\">";
                href = 1; //there is a href there
            }

        }
        //for (int i=0; i < atts.getLength(); i++) {
        //  if (atts.getQName(i) == "riskno") {
        //  	riskNameObj.setProperty(atts.getValue(i), "risk_"+riskName);
        //    break;
        //  }
        //}
    }

    /**
     * Handles the end of XML elements during antenatal risk assessment form generation.
     * <p>
     * Completes HTML tag structures and generates appropriate pregnancy-specific form
     * input elements. Risk elements receive checkbox inputs while entry elements
     * receive text input fields for quantitative maternal/fetal risk data collection.
     * </p>
     *
     * @param namespaceURI String namespace URI of the element
     * @param localName String local name of the element
     * @param rawName String qualified name of the element
     * @throws SAXException if XML processing error occurs
     */
    public void endElement(String namespaceURI, String localName, String rawName) throws SAXException {
        // Close hyperlink if present
        if (href == 1) {
            results += "</a>";
            href = 0;
        }

        // Close various pregnancy risk assessment section elements
        if (rawName.equals("section_title")) {
            results += "</font></td></tr></table>\n";
        } else if (rawName.equals("subsection_title")) {
            results += "</font></td></tr></table></center>\n";
        } else if (rawName.equals("heading")) {
            results += "</b></font></td></tr></table>\n";
        }
        // Complete risk element processing (checkbox already generated in startElement)
        else if (rawName.equals("risk")) {
            riskName = "";
        }
        // Generate text input for quantitative pregnancy risk data entry
        else if (rawName.equals("entry")) {
            results += "</font></td><td><input type=text size=6 name=\"risk_" + riskName + "\" datafld='risk_" + riskName + "'>";
            riskName = "";
        }
    }

    /**
     * Handles character data within XML elements.
     * <p>
     * Appends text content directly to the HTML output for display
     * within the antenatal risk assessment form structure. This includes
     * risk factor descriptions, clinical guidance text, and form labels.
     * </p>
     *
     * @param ch char[] character array containing the text
     * @param start int starting position in the array
     * @param length int length of text to process
     * @throws SAXException if XML processing error occurs
     */
    public void characters(char[] ch, int start, int length) throws SAXException {
        String s = new String(ch, start, length);
        results += s;
    }


    /**
     * Returns the generated HTML antenatal risk assessment form content.
     * <p>
     * This method should be called after XML parsing is complete to retrieve
     * the formatted HTML form for pregnancy-specific risk factor data collection.
     * The returned HTML includes interactive checkboxes and text inputs optimized
     * for maternal and fetal risk assessment workflows.
     * </p>
     *
     * @return String containing the complete HTML antenatal risk assessment form
     */
    public String getResults() {
        return results;
    }

    /**
     * Returns the Properties object containing all identified pregnancy risk factor names.
     * <p>
     * This method provides access to the collection of antenatal risk factor identifiers
     * that were processed during XML parsing. These identifiers can be used for:
     * <ul>
     * <li>Generating personalized pregnancy care checklists based on identified risks</li>
     * <li>Supporting clinical decision support for high-risk pregnancy management</li>
     * <li>Integration with gestational week-specific care recommendations</li>
     * <li>Validation and cross-referencing of maternal/fetal risk assessments</li>
     * </ul>
     * </p>
     *
     * @return Properties containing pregnancy risk factor names as keys with corresponding identifiers as values
     */
    public Properties getRiskNameObj() {
        return riskNameObj;
    }

}
