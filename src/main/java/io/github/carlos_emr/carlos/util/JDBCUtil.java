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


package io.github.carlos_emr.carlos.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.Misc;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.form.service.FormXmlImportService;

public class JDBCUtil {
    private static final String XML_EXTENSION = ".xml";
    // Legacy built-in form table that is valid but not registered in encounterForm.
    private static final Set<String> INTERNAL_FORM_TABLES = Set.of("formGrowth0_36");
    private static final Set<String> IMPORT_TARGET_MANAGED_FIELDS = Set.of("demographic_no", "formEdited");

    /**
     * Converts the remaining rows in the given result set to XML.
     *
     * <p>The caller owns and must close the {@link ResultSet}.</p>
     */
    public static Document toDocument(ResultSet rs)
            throws ParserConfigurationException, SQLException {
        DocumentBuilderFactory factory = XmlUtils.createSecureDocumentBuilderFactory();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document doc = builder.newDocument();

        Element results = doc.createElement("Results");
        doc.appendChild(results);

        ResultSetMetaData rsmd = rs.getMetaData();
        int colCount = rsmd.getColumnCount();

        while (rs.next()) {
            Element row = doc.createElement("Row");
            results.appendChild(row);

            for (int i = 1; i <= colCount; i++) {
                String columnName = Encode.forXml(rsmd.getColumnName(i));
                String value = Encode.forXml(Misc.getString(rs, i));

                Element node = doc.createElement(columnName);
                node.appendChild(doc.createTextNode(value));
                row.appendChild(node);
            }
        }
        return doc;
    }

    public static void saveAsXML(Document doc, String fileName) throws TransformerException, IOException {
        File newXML = validateFormArchiveFile(fileName);
        try {
            TransformerFactory transFactory = XmlUtils.createSecureTransformerFactory();
            Transformer transformer = transFactory.newTransformer();
            DOMSource source = new DOMSource(doc);
            try (FileOutputStream os = new FileOutputStream(newXML)) {
                StreamResult result = new StreamResult(os);
                transformer.transform(source, result);
            }
            //calling the zip function was commented out for a few reasons
            //practically, it seems to be unsustainable at scale and has resulted in a server being shut down
            //also, when reviewing the write2Zip function, it seems to be attempting to create a zip file of EVERY xml file EVERY SINGLE TIME a new XML file is generated
            //the purpose of this approach is unclear
            /*MiscUtils.getLogger().debug("Next is to call zip function!");
            zip z = new zip();
            z.write2Zip("xml");*/
        } catch (TransformerException | IOException e) {
            try {
                Files.deleteIfExists(newXML.toPath());
            } catch (IOException cleanupException) {
                MiscUtils.getLogger().warn("Unable to delete partial XML file {}", fileName, cleanupException);
            }
            throw e;
        }
    }

    private static File validateFormArchiveFile(String fileName) throws IOException {
        if (fileName == null) {
            throw new IOException("Form XML archive path must not be null");
        }

        Path targetPath = Path.of(fileName).toAbsolutePath().normalize();
        Path archiveDirectory = Path.of(CarlosProperties.getInstance().getProperty("form_record_path", "/root"))
                .toAbsolutePath().normalize();
        try {
            return PathValidationUtils.validateExistingPath(targetPath.toFile(), archiveDirectory.toFile());
        } catch (SecurityException e) {
            throw new IOException("Invalid form XML archive path", e);
        }
    }

    public static void toDataBase(InputStream inputStream, String fileName) throws XmlImportException {
        try {
            SpringUtils.getBean(FormXmlImportService.class).importEntry(inputStream, fileName);
        } catch (RuntimeException e) {
            throw new XmlImportException("Unable to import form XML row", e);
        }
    }

    public record FormImportRequest(FormImportTarget target, Map<String, String> values) { }

    /** Parse before persistence; the archive name remains authoritative for patient and edit date. */
    public static FormImportRequest readImportRequest(InputStream input, String fileName) throws XmlImportException {
        FormImportTarget target = parseImportFileName(fileName);
        validateImportFormTable(target.formName());
        Document document = parseImportDocument(input);
        Map<String, String> values = new LinkedHashMap<>();
        collectImportValues(document, values);
        return new FormImportRequest(target, values);
    }

    // Fixed structural XML tags use locale-stable case folding.
    // Physical column validation and identity from the filename independently guard the import.
    @SuppressFBWarnings(value = "IMPROPER_UNICODE",
            justification = "Fixed XML tags use Locale.ROOT; physical metadata validates columns and the archive filename owns patient/timestamp")
    private static void collectImportValues(Node node, Map<String, String> values) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            String name = node.getNodeName();
            String structuralName = name.toLowerCase(java.util.Locale.ROOT);
            if (!"results".equals(structuralName) && !"row".equals(structuralName)
                    && !"id".equals(structuralName) && !isImportTargetManagedField(name)) {
                Node first = node.getFirstChild();
                values.put(name, first != null && first.getNodeType() == Node.TEXT_NODE ? first.getNodeValue() : "");
            }
        }
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) collectImportValues(child, values);
    }

    public static FormImportTarget parseImportFileName(String fileName) throws XmlImportException {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")
                || !fileName.endsWith(XML_EXTENSION)) {
            throw new XmlImportException("Invalid form XML import entry name");
        }

        String entryName = fileName.substring(0, fileName.length() - XML_EXTENSION.length());
        int timestampDelimiter = entryName.lastIndexOf('_');
        int demographicDelimiter = timestampDelimiter > 0 ? entryName.lastIndexOf('_', timestampDelimiter - 1) : -1;
        if (demographicDelimiter <= 0 || demographicDelimiter == timestampDelimiter - 1
                || timestampDelimiter == entryName.length() - 1) {
            throw new XmlImportException("Invalid form XML import entry name");
        }

        String formName = entryName.substring(0, demographicDelimiter);
        String demographicNo = entryName.substring(demographicDelimiter + 1, timestampDelimiter);
        String timeStamp = entryName.substring(timestampDelimiter + 1);
        if (!isValidFormImportFormName(formName)
                || !isAsciiDigits(demographicNo)
                || !isValidFormImportTimestamp(timeStamp)) {
            throw new XmlImportException("Invalid form XML import entry name");
        }
        return new FormImportTarget(formName, demographicNo, timeStamp);
    }

    private static boolean isValidFormImportFormName(String value) {
        if (value.isEmpty() || !isAsciiLetter(value.charAt(0))) {
            return false;
        }
        for (int i = 1; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!isAsciiLetterOrDigit(current) && current != '_') {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidFormImportTimestamp(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (!isAsciiLetterOrDigit(current) && current != '_' && current != ':' && current != '-') {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiDigits(String value) {
        if (value.isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!isAsciiDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiLetterOrDigit(char value) {
        return isAsciiLetter(value) || isAsciiDigit(value);
    }

    private static boolean isAsciiLetter(char value) {
        return (value >= 'A' && value <= 'Z') || (value >= 'a' && value <= 'z');
    }

    private static boolean isAsciiDigit(char value) {
        return value >= '0' && value <= '9';
    }

    static String validateImportFormTable(String formName) throws XmlImportException {
        if (formName == null) {
            throw new XmlImportException("Unknown encounter form table name");
        }
        if ("form".equals(formName) || INTERNAL_FORM_TABLES.contains(formName)) {
            return formName;
        }

        EncounterFormDao encounterFormDao = SpringUtils.getBean(EncounterFormDao.class);
        List<?> configuredForms = encounterFormDao.findByFormTable(formName);
        if (configuredForms != null && !configuredForms.isEmpty()) {
            return formName;
        }
        throw new XmlImportException("Unknown encounter form table name");
    }

    private static Document parseImportDocument(InputStream inputStream) throws XmlImportException {
        try {
            InputSource source = new InputSource(inputStream);
            DocumentBuilderFactory factory = XmlUtils.createSecureDocumentBuilderFactory();
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(source);
        } catch (ParserConfigurationException | SAXException | IOException e) {
            throw new XmlImportException("Unable to parse form XML import entry", e);
        }
    }

    // Import-managed database columns have fixed ASCII names.
    // archive entry name remains the trusted source for patient/timestamp. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(
            value = "IMPROPER_UNICODE",
            justification = "case-insensitive comparison against fixed ASCII import-managed DB column names; "
                    + "XML body cannot choose patient/timestamp")
    private static boolean isImportTargetManagedField(String name) {
        for (String field : IMPORT_TARGET_MANAGED_FIELDS) {
            if (field.equalsIgnoreCase(name)) return true;
        }
        return false;
    }

    public record FormImportTarget(String formName, String demographicNo, String timeStamp) {
    }

    public static class XmlImportException extends Exception {
        public XmlImportException(String message) {
            super(message);
        }

        public XmlImportException(String message, Throwable cause) {
            super(message, cause);
        }
    }

}
