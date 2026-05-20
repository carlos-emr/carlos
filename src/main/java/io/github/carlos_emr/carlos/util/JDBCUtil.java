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
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import io.github.carlos_emr.Misc;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import io.github.carlos_emr.carlos.db.LegacyJdbcQuery;

public class JDBCUtil {
    private static final Pattern FORM_IMPORT_FILE_NAME =
            Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)_(\\d+)_([A-Za-z0-9:-]+)\\.xml$");
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
        File newXML = new File(fileName);
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
            if (newXML.exists() && !newXML.delete()) {
                MiscUtils.getLogger().warn("Unable to delete partial XML file {}", fileName);
            }
            throw e;
        }
    }

    public static void toDataBase(InputStream inputStream, String fileName) throws XmlImportException {
        FormImportTarget target = parseImportFileName(fileName);
        String formName = validateImportFormTable(target.formName());
        Document doc = parseImportDocument(inputStream);

        // Table identifiers cannot be JDBC-bound. formName is accepted only after
        // strict filename parsing plus the encounterForm/internal table allowlist.
        String existsSql = "SELECT * FROM " + formName + " WHERE demographic_no=? AND formEdited=?";
        MiscUtils.getLogger().debug(existsSql);
        try (ResultSet existing = LegacyJdbcQuery.getPreparedResultSet(
                LegacyJdbcQuery.trustedSelectSql(existsSql), target.demographicNo(), target.timeStamp())) {
            if (existing.first()) {
                return;
            }
        } catch (SQLException e) {
            throw new XmlImportException("Unable to check existing form XML import row", e);
        }

        String insertSql = "SELECT * FROM " + formName + " WHERE demographic_no=? AND ID='0'";
        MiscUtils.getLogger().debug("sql: " + insertSql);
        try (ResultSet insert = LegacyJdbcQuery.getPreparedResultSet(
                LegacyJdbcQuery.trustedSelectSql(insertSql), true, new Object[]{target.demographicNo()})) {
            insert.moveToInsertRow();
            toResultSet(doc, insert);
            applyTrustedImportTarget(target, insert);
            insert.insertRow();
        } catch (SQLException e) {
            throw new XmlImportException("Unable to insert form XML import row", e);
        }
    }

    public static FormImportTarget parseImportFileName(String fileName) throws XmlImportException {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")) {
            throw new XmlImportException("Invalid form XML import entry name");
        }

        Matcher matcher = FORM_IMPORT_FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            throw new XmlImportException("Invalid form XML import entry name");
        }
        return new FormImportTarget(matcher.group(1), matcher.group(2), matcher.group(3));
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

    static ResultSet toResultSet(Node node, ResultSet rs) throws SQLException {
        int type = node.getNodeType();

        if (type == Node.ELEMENT_NODE) {

            String name = node.getNodeName();
            String value = "";

            Node next = node.getFirstChild();
            if (next != null) {
                type = next.getNodeType();
                if (type == Node.TEXT_NODE) {

                    value = next.getNodeValue();
                }
            }

            if (!name.equalsIgnoreCase("Results")
                    && !name.equalsIgnoreCase("Row")
                    && !name.equalsIgnoreCase("ID")
                    && !isImportTargetManagedField(name))
                rs.updateString(name, value);
        }

        //recurse
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) {
            toResultSet(child, rs);
        }

        return rs;

    }

    private static boolean isImportTargetManagedField(String name) {
        return IMPORT_TARGET_MANAGED_FIELDS.stream().anyMatch(field -> field.equalsIgnoreCase(name));
    }

    static void applyTrustedImportTarget(FormImportTarget target, ResultSet rs) throws SQLException {
        // The archive entry name is the import boundary. Do not let XML body fields
        // choose the patient or edited timestamp for the row being inserted.
        rs.updateString("demographic_no", target.demographicNo());
        rs.updateString("formEdited", target.timeStamp());
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
