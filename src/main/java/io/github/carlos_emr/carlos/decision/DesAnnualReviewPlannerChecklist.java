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

import java.io.IOException;
import java.util.Properties;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Orchestrates the generation of personalized annual health review checklists.
 * <p>
 * This class serves as the main coordinator for processing clinical decision support
 * XML files and generating interactive HTML checklists for healthcare providers.
 * It utilizes SAX parsing to transform XML-based clinical rules into patient-specific
 * care recommendations based on identified risk factors.
 * </p>
 * <p>
 * <strong>Clinical Purpose:</strong> Annual health reviews are comprehensive preventive
 * care assessments that ensure systematic delivery of evidence-based healthcare. These
 * checklists help providers address age-appropriate screenings, immunizations, chronic
 * disease management, and health promotion activities during routine patient encounters.
 * </p>
 * <p>
 * The generated checklists include interactive checkboxes for tracking completion
 * status ("Done" or "N/A") and are personalized based on patient demographics,
 * medical history, and identified risk factors.
 * </p>
 *
 * @see DesAnnualReviewPlannerChecklistHandler
 * @see DesAnnualReviewPlannerRisk
 * @since July 2003
 */
public class DesAnnualReviewPlannerChecklist {

    /**
     * Generates a personalized annual health review checklist using string-based risk data.
     * <p>
     * This method is maintained for backward compatibility with legacy implementations.
     * It processes XML clinical decision rules and generates HTML checklist content
     * based on the provided risk factors in string format.
     * </p>
     *
     * @param uri String path or URI to the XML file containing checklist rules
     * @param savedltcrisks String containing formatted risk factor data
     * @return String HTML content for the personalized checklist, or error message if processing fails
     */
    public String doStuff(String uri, String savedltcrisks) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            ContentHandler contentHandler = new DesAnnualReviewPlannerChecklistHandler(savedltcrisks);
            saxParser.parse(uri, (DefaultHandler) contentHandler);

            return ((DesAnnualReviewPlannerChecklistHandler) contentHandler).getResults();
        } catch (IOException e) {
            MiscUtils.getLogger().debug("Error reading XML file from URI: " + uri + " - " + e.getMessage());
        } catch (SAXException e) {
            MiscUtils.getLogger().debug("Error parsing XML content: " + e.getMessage());
        } catch (ParserConfigurationException e) {
            MiscUtils.getLogger().debug("Error configuring SAX parser: " + e.getMessage());
        }

        return "Error: unable to find/parse the risks xml file, CHECK if the format is correct";
    }

    /**
     * Generates a personalized annual health review checklist using Properties-based risk data.
     * <p>
     * This is the preferred method for generating checklists as it provides structured
     * access to patient risk factors. The Properties object should contain key-value
     * pairs where keys are risk factor names and values indicate presence or assessment
     * results for each risk factor.
     * </p>
     * <p>
     * The method processes the XML clinical decision rules file and generates an HTML
     * checklist containing only items relevant to the patient's identified risk factors.
     * Each checklist item includes checkboxes for tracking completion status.
     * </p>
     *
     * @param uri String path or URI to the XML file containing clinical decision rules
     * @param savedltcrisks Properties containing patient risk factor assessments
     * @return String HTML content for the personalized checklist with interactive elements
     * @throws RuntimeException if XML parsing fails due to malformed content
     */
    public String doStuff(String uri, Properties savedltcrisks) {
        try {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser saxParser = factory.newSAXParser();
            ContentHandler contentHandler = new DesAnnualReviewPlannerChecklistHandler(savedltcrisks);
            saxParser.parse(uri, (DefaultHandler) contentHandler);

            return ((DesAnnualReviewPlannerChecklistHandler) contentHandler).getResults();
        } catch (IOException e) {
            MiscUtils.getLogger().debug("Error reading XML file from URI: " + uri + " - " + e.getMessage());
        } catch (SAXException e) {
            MiscUtils.getLogger().debug("Error parsing XML content: " + e.getMessage());
        } catch (ParserConfigurationException e) {
            MiscUtils.getLogger().debug("Error configuring SAX parser: " + e.getMessage());
        }

        return "Error: unable to find/parse the risks xml file, CHECK if the format is correct";
    }
}
