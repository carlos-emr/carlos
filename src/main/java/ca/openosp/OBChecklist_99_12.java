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

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import ca.openosp.openo.utility.MiscUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

/**
 * Obstetrics checklist processor for the 1999/2012 AR1 form.
 * 
 * <p>This class processes XML-based obstetrics checklist data using SAX parsing.
 * It validates and transforms OB checklist information including:</p>
 * <ul>
 *   <li>Final Estimated Date of Birth (EDB) validation</li>
 *   <li>Checklist item parsing and processing</li>
 *   <li>AR1 form parameter integration</li>
 * </ul>
 * 
 * <p>The class uses {@link OBChecklistHandler_99_12} as the SAX content handler
 * to process the XML checklist data.</p>
 * 
 * @see OBChecklistHandler_99_12
 * @see OBRisks_99_12
 */
public class OBChecklist_99_12 {

    /**
     * Processes an obstetrics checklist XML file and generates formatted results.
     * 
     * <p>This method:</p>
     * <ol>
     *   <li>Validates the final EDB date format (yyyy/MM/dd)</li>
     *   <li>Parses the XML checklist using SAX</li>
     *   <li>Returns formatted checklist results or error messages</li>
     * </ol>
     * 
     * @param uri the URI or file path to the XML checklist file
     * @param savedar1params properties containing AR1 form parameters including finalEDB
     * @return formatted checklist results, or error message if processing fails
     */
    public String doStuff(String uri, Properties savedar1params) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy/MM/dd");
        try {
            df.parse(savedar1params.getProperty("finalEDB"));
        } catch (java.text.ParseException pe) {
            return "Error: final EDB";
        }

        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            XMLReader reader = saxParser.getXMLReader();

            ContentHandler contentHandler = new OBChecklistHandler_99_12(savedar1params);
            reader.setContentHandler(contentHandler);
            reader.parse(uri);

            return ((OBChecklistHandler_99_12) contentHandler).getResults();

        } catch (IOException e) {
            MiscUtils.getLogger().debug("Error reading URI: " + e.getMessage());
        } catch (SAXException e) {
            MiscUtils.getLogger().debug("Error in parsing: " + e.getMessage());
        } catch (ParserConfigurationException e) {
            MiscUtils.getLogger().debug("Error configuring parser: " + e.getMessage());
        }

        return "Error: unable to parse the checklist xml file";
    }

}
