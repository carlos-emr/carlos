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


package ca.openosp;

import java.util.Properties;

import ca.openosp.openo.utility.MiscUtils;
import org.xml.sax.Attributes;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * SAX content handler for processing obstetrics risk assessment XML (1999/2012 AR1 form).
 * 
 * <p>This handler extends {@link DefaultHandler} to parse and format obstetrics
 * risk assessment data. It provides:</p>
 * <ul>
 *   <li>HTML-formatted risk assessment output generation</li>
 *   <li>Multi-column table layout for risk factors</li>
 *   <li>Risk categorization and organization</li>
 *   <li>Hyperlink support for detailed risk information</li>
 * </ul>
 * 
 * <p>The handler processes XML elements and generates an HTML table showing
 * identified risk factors organized in a multi-column layout (default 5 columns).</p>
 * 
 * @see OBRisks_99_12
 * @see DefaultHandler
 */
public class OBRisksHandler_99_12 extends DefaultHandler {

    private Locator locator;
    private String results, riskName; //currentElement
    private int colcount = 0;
    /** Number of columns for risk factor display table */
    private int numcols = 5;
    private int interiortable = 0;
    private int href = 0;
    private Properties riskNameObj = null;
//	private Hashtable savedparams;

    /**
     * Constructs a new OBRisksHandler.
     */
    public OBRisksHandler_99_12() {
    }

    /**
     * Receives the document locator for position tracking during parsing.
     * 
     * @param locator the document locator
     */
    public void setDocumentLocator(Locator locator) {

        this.locator = locator;
    }

    /**
     * Called at the start of document parsing.
     * Initializes the handler and begins HTML table generation.
     * 
     * @throws SAXException if a SAX error occurs
     */
    public void startDocument() throws SAXException {

        riskNameObj = new Properties();
        results = "<center><table width='100%' border='0' cellpadding='0' CELLSPACING='0' BGCOLOR='ivory'><tr><td>\n";
    }

    /**
     * Called at the end of document parsing.
     * Closes the HTML table structure.
     * 
     * @throws SAXException if a SAX error occurs
     */
    public void endDocument() throws SAXException {

        if (interiortable == 1) { //close content table
            results += "</center></td></tr></table>";
        }
        results += "</td></tr></table></center>\n";
    }

    /**
     * Processes XML processing instructions.
     * Currently not implemented.
     * 
     * @param target the processing instruction target
     * @param data the processing instruction data
     * @throws SAXException if a SAX error occurs
     */
    public void processingInstruction(String target, String data) throws SAXException {

    }

    /**
     * Called when a prefix mapping starts.
     * Currently not implemented.
     * 
     * @param prefix the namespace prefix
     * @param uri the namespace URI
     */
    public void startPrefixMapping(String prefix, String uri) {

    }

    /**
     * Called when a prefix mapping ends.
     * Currently not implemented.
     * 
     * @param prefix the namespace prefix
     */
    public void endPrefixMapping(String prefix) {

    }

    public void startElement(String namespaceURI, String localName, String rawName, Attributes atts) throws SAXException {
        if (rawName.equals("section_title")) {
            if (interiortable == 1) { //close content table
                results += "</center></td></tr></table>";
                interiortable = 0;
                colcount = 0;
            }
            results += "<table border=0 cellspacing=1 cellpadding=1 width=\"100%\">\n";
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
                results += "<center><table border=0 cellpadding=0 cellspacing=1 width=\"98%\" datasrc='#xml_list'>";
                interiortable = 1;
                colcount = 0;
            }
            if (colcount == 0) results += "<tr><td width=" + 10 / numcols + "% >"; //the first td
            if (colcount % numcols == 0) { //tr td  new line beginning?
                results += "</td></tr>\n<tr><td width=" + 10 / numcols + "% >";
            } else {
                results += "</td><td width=" + 10 / numcols + "% >";
                //results += "</td><td width="+10/numcols+"% >";
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
            if (atts.getLocalName(i) == "name") {
                riskName = atts.getValue(i);
                results += "<input type=checkbox name=\"xml_" + riskName + "\" value='checked' datafld='xml_" + riskName + "'></font></td><td width=" + 100 / numcols + "% >";
            }
            if (atts.getLocalName(i) == "href") {
                results += "<a href=# onClick=\"popupPage(400,500,'" + atts.getValue(i) + "');return false;\">";
                href = 1; //there is a href there
            }

        }
        for (int i = 0; i < atts.getLength(); i++) {
            if (atts.getLocalName(i) == "riskno") {
                riskNameObj.setProperty(atts.getValue(i), "xml_" + riskName);
                break;
            }
        }
    }

    public void endElement(String namespaceURI, String localName, String rawName) throws SAXException {
        if (href == 1) {
            results += "</a>";
            href = 0;
        }
        if (rawName.equals("section_title")) {
            results += "</font></td></tr></table>\n";
        } else if (rawName.equals("subsection_title")) {
            results += "</font></td></tr></table></center>\n";
        } else if (rawName.equals("heading")) {
            results += "</b></font></td></tr></table>\n";
        } else if (rawName.equals("risk")) {
            // results += "</font></td><td><input type=checkbox name=\"xml_" + riskName + "\" value='checked' datafld='xml_" +riskName+ "'>";
            riskName = "";
        } else if (rawName.equals("entry")) {
            results += "</font></td><td><input type=text size=6 name=\"xml_" + riskName + "\" datafld='xml_" + riskName + "'>";
            riskName = "";
        }

    }

    public void characters(char[] ch, int start, int length) throws SAXException {
        String s = new String(ch, start, length);

        results += s;
    }

    public void ignorableWhitespace(char[] ch, int start, int length) throws SAXException {
        new String(ch, start, length);

    }

    public void skippedEntity(String name) throws SAXException {
        MiscUtils.getLogger().debug("Skipping entity " + name);
    }

    public String getResults() {
        return results;
    }

    public Properties getRiskNameObj() {
        return riskNameObj;
    }

}
