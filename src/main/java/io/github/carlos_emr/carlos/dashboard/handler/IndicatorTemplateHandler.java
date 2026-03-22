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
package io.github.carlos_emr.carlos.dashboard.handler;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.commn.model.IndicatorTemplate;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.w3c.dom.Document;

/**
 * Converts a XML IndicatorTemplate string into several formats:
 * Document
 * IndicatorTemplate Entity Bean
 * IndicatorTemplateXML Bean - for XML to POJO parsing.
 * IndicatorBean - for display layer
 * <p>
 * Requires the proper IndicatorXMLTemplateSchema.xsd schema file to be set
 * into classpath: indicatorXMLTemplates/IndicatorXMLTemplateSchema.xsd
 * This class will not instantiate without the Schema file
 *
 * @since 2026-03-17
 */
public class IndicatorTemplateHandler {

    private static Logger logger = MiscUtils.getLogger();
    private static final String DEFAULT_VALIDATION_MESSAGE = "Failed XML Validation ";
    private static final String DATE_FORMAT = "MM-dd-yyyy";
    private static final String schemaFile = "indicatorXMLTemplates/IndicatorXMLTemplateSchema.xsd";
    private Document indicatorTemplateDocument;
    private IndicatorTemplate indicatorTemplateEntity;
    private IndicatorTemplateXML indicatorTemplateXML;
    private byte[] bytearray;
    private Schema schema;
    private String validationMessage;
    private boolean validXML;
    private LoggedInInfo loggedInInfo;

    /**
     * Requires the proper IndicatorXMLTemplateSchema.xsd schema file to be set
     */
    public IndicatorTemplateHandler() {
        // default
        setSchema();
    }

    /**
     * Constructs a handler with the logged-in user context and immediately parses the given byte array.
     *
     * @param loggedInInfo LoggedInInfo the logged-in user context for provider-specific parameters
     * @param bytearray byte[] the raw XML template data
     */
    public IndicatorTemplateHandler(LoggedInInfo loggedInInfo, byte[] bytearray) {
        this.loggedInInfo = loggedInInfo;
        setSchema();
        read(bytearray);
    }

    /**
     * Requires the proper IndicatorXMLTemplateSchema.xsd schema file to be set
     */
    public IndicatorTemplateHandler(byte[] bytearray) {
        setSchema();
        read(bytearray);
    }

    /**
     * This will validate only if the XML Document is already set in
     * properties. Should be no need to use it as the Document is validated
     * as it is being parsed.
     * <p>
     * If validation fails - call the getValidationMessage method for the reason.
     */
    public boolean validate() {
        return validateXML();
    }

    private boolean validateXML() {

        try {

            Validator validator = getSchema().newValidator();
            validator.validate(new DOMSource(getIndicatorTemplateDocument()));
            setValidXML(Boolean.TRUE);
        } catch (Exception e) {
            logger.error("Failed XML Validation ", e);
        }

        return isValidXML();
    }

    /**
     * Parses the given byte array as XML, validates it against the schema, and populates
     * the template document, XML POJO, and entity bean. If parsing or validation fails,
     * the {@code validXML} flag is set to {@code false}.
     *
     * @param bytearray byte[] the raw XML template data to parse
     */
    public void read(byte[] bytearray) {
        this.bytearray = bytearray;

        // SetIndicatorTemplateDocument() will validate and parse the incoming byte array into an XML Document Object
        // If the parsing fails, the ValidXML switch will be set to False.
        // The remainder of this method cannot be completed with an invalid XML Document.
        setIndicatorTemplateDocument(bytearray);

        if (isValidXML()) {
            IndicatorTemplateXML indicatorTemplateXML = new IndicatorTemplateXML(loggedInInfo, getIndicatorTemplateDocument());
            setIndicatorTemplateXML(indicatorTemplateXML);
            setIndicatorTemplateEntity(indicatorTemplateEntityFromXML(getIndicatorTemplateXML()));
        }
    }

    /**
     * Returns the parsed XML Document representation of the indicator template.
     *
     * @return Document the XML document, or {@code null} if parsing failed
     */
    public Document getIndicatorTemplateDocument() {
        return indicatorTemplateDocument;
    }

    private void setIndicatorTemplateDocument(byte[] bytearray) {
        this.indicatorTemplateDocument = byteToDocument(bytearray);
    }

    /**
     * The IndicatorTemplate JPA database entity.
     * Used to retrieve or persist the XML template and its header data.
     */
    public IndicatorTemplate getIndicatorTemplateEntity() {
        return indicatorTemplateEntity;
    }

    private void setIndicatorTemplateEntity(IndicatorTemplate indicatorTemplateEntity) {
        this.indicatorTemplateEntity = indicatorTemplateEntity;
    }

    /**
     * Returns the POJO representation parsed from the XML document.
     *
     * @return IndicatorTemplateXML the parsed template POJO, or {@code null} if XML was invalid
     */
    public IndicatorTemplateXML getIndicatorTemplateXML() {
        return this.indicatorTemplateXML;
    }

    private void setIndicatorTemplateXML(IndicatorTemplateXML indicatorTemplateXML) {
        indicatorTemplateXML.setTemplate(new String(this.bytearray));
        this.indicatorTemplateXML = indicatorTemplateXML;
    }

    /**
     * Returns the XSD schema used for validation.
     *
     * @return Schema the XML schema
     */
    public Schema getSchema() {
        return schema;
    }

    /**
     * This is a dependency for instantiation of this class.
     * The schema file is used during the parse to XML method and any time the
     * ValidateXML method is called.
     */
    private void setSchema() {

        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        URL schemaSource = Thread.currentThread().getContextClassLoader().getResource(IndicatorTemplateHandler.schemaFile);

        File schemaFile = null;
        try {
            schemaFile = new File(schemaSource.toURI());
            this.schema = factory.newSchema(schemaFile);
        } catch (Exception e) {
            setValidationMessage("Failed to Fetch Schema file ", e);
            logger.error(validationMessage.toString(), e);
        }
    }

    /**
     * Returns the validation error message if XML parsing or validation failed.
     *
     * @return String the validation error message, or {@code null} if validation succeeded
     */
    public String getValidationMessage() {
        return validationMessage;
    }

    private void setValidationMessage(String message, Exception e) {
        StringBuilder validationMessage = new StringBuilder("");

        if (message == null) {
            validationMessage.append(DEFAULT_VALIDATION_MESSAGE);
        } else {
            validationMessage.append(message);
        }
        validationMessage.append(e.getCause());
        validationMessage.append(e.getMessage());
        this.validationMessage = validationMessage.toString();

    }

    /**
     * Returns whether the last parsed XML was valid.
     *
     * @return boolean {@code true} if the XML is valid, {@code false} otherwise
     */
    public boolean isValidXML() {
        return validXML;
    }

    public void setValidXML(boolean validXML) {
        this.validXML = validXML;
    }

    private static final IndicatorTemplate indicatorTemplateEntityFromXML(IndicatorTemplateXML indicatorTemplateXML) {

        IndicatorTemplate indicatorTemplate = null;

        if (indicatorTemplateXML != null) {

            indicatorTemplate = new IndicatorTemplate();
            indicatorTemplate.setTemplate(indicatorTemplateXML.getTemplate());
            indicatorTemplate.setActive(Boolean.TRUE);
            indicatorTemplate.setLocked(Boolean.FALSE);
            indicatorTemplate.setCategory(indicatorTemplateXML.getCategory());
            indicatorTemplate.setSubCategory(indicatorTemplateXML.getSubCategory());
            indicatorTemplate.setMetricSetName(indicatorTemplateXML.getMetricSetName());
            indicatorTemplate.setMetricLabel(indicatorTemplateXML.getMetricLabel());
            indicatorTemplate.setName(indicatorTemplateXML.getName());
            indicatorTemplate.setDefinition(indicatorTemplateXML.getDefinition());
            indicatorTemplate.setFramework(indicatorTemplateXML.getFramework());
            indicatorTemplate.setShared(Boolean.valueOf(indicatorTemplateXML.getShared()));

            try {
                String frameworkDate = indicatorTemplateXML.getFrameworkVersion().trim();
                SimpleDateFormat simpleDateFormat = new SimpleDateFormat(DATE_FORMAT);
                Date parsedFrameworkDate = simpleDateFormat.parse(frameworkDate);
                indicatorTemplate.setFrameworkVersion(parsedFrameworkDate);
            } catch (ParseException e) {
                logger.error("Date parsing error", e);
            }

            indicatorTemplate.setNotes(indicatorTemplateXML.getNotes());

        }

        return indicatorTemplate;
    }


    private final Document byteToDocument(final byte[] bytearray) {

        Document document = null;
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setSchema(getSchema());

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(new ByteArrayInputStream(bytearray));
            if (document != null) {
                setValidXML(Boolean.TRUE);
            }
        } catch (Exception e) {
            //TODO try to recover from simple validations.  ie: change any offending > or < to &gt; or &lt;
            setValidationMessage("Failed to parse XML file (could be a validation error): ", e);
            logger.error(validationMessage.toString(), e);
        }

        return document;
    }

}
