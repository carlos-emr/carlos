/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.ValidatorHandler;
import org.apache.xerces.jaxp.validation.XMLSchemaFactory;
import org.w3c.dom.ls.LSException;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;
import org.xml.sax.SAXException;

/** One hash-pinned wire schema shared by the explicitly selected 5.1/5.2 logical profiles. */
public final class CdsSchemaValidator {
    public static final String MAIN_SHA256 = "2bffde70ae84137624db888611e8a3b919e868789b726ee012d39513ce846726";
    public static final String DATATYPE_SHA256 = "e999fafd945d13a7a2866482e06029cea86b902ce7f30139377a3600c3d9ce31";
    private static final String MAIN = "EMR_Data_Migration_Schema.xsd";
    private static final String DATATYPES = "EMR_Data_Migration_Schema_DT.xsd";
    private static final String PREFIX = "/cds-import/schema/";
    private static final LSResourceResolver RESOLVER = (type, namespace, publicId, systemId, base) -> {
        if (!"cds_dt".equals(namespace) || !DATATYPES.equals(systemId)) {
            throw new LSException(LSException.PARSE_ERR, "CDS_EXTERNAL_SCHEMA_BLOCKED");
        }
        try { return new LocalInput(publicId, systemId, bytes(DATATYPES, DATATYPE_SHA256)); }
        catch (IOException failure) { throw new LSException(LSException.PARSE_ERR, "CDS_PINNED_SCHEMA_UNAVAILABLE"); }
    };

    private CdsSchemaValidator() { }

    public static ValidatorHandler newValidator() {
        ValidatorHandler handler = Compiled.SCHEMA.newValidatorHandler();
        handler.setResourceResolver(RESOLVER);
        return handler;
    }

    private static final class Compiled {
        private static final Schema SCHEMA = compile();
    }

    private static Schema compile() {
        try {
            XMLSchemaFactory factory = new XMLSchemaFactory();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setResourceResolver(RESOLVER);
            // Only immutable hash-pinned local schemas reach this factory. The deny-by-default
            // resolver also blocks external instance schema hints on the resulting validator.
            StreamSource source = new StreamSource(new ByteArrayInputStream(bytes(MAIN, MAIN_SHA256)));
            source.setSystemId("cds-schema:/" + MAIN);
            return factory.newSchema(source);
        } catch (IOException | SAXException failure) {
            throw new IllegalStateException("CDS_PINNED_SCHEMA_UNAVAILABLE");
        }
    }

    private static byte[] bytes(String name, String expected) throws IOException {
        try (InputStream input = CdsSchemaValidator.class.getResourceAsStream(PREFIX + name)) {
            if (input == null) throw CdsPaths.failure("CDS_PINNED_SCHEMA_UNAVAILABLE");
            byte[] result = input.readNBytes(256 * 1024);
            if (input.read() != -1 || !expected.equals(CdsPaths.hex(CdsPaths.sha256().digest(result)))) {
                throw CdsPaths.failure("CDS_PINNED_SCHEMA_HASH_MISMATCH");
            }
            return result;
        }
    }

    private static final class LocalInput implements LSInput {
        private String publicId;
        private String systemId;
        private InputStream input;
        LocalInput(String publicId, String systemId, byte[] bytes) {
            this.publicId = publicId; this.systemId = systemId; input = new ByteArrayInputStream(bytes);
        }
        @Override public Reader getCharacterStream() { return null; }
        @Override public void setCharacterStream(Reader value) { throw new UnsupportedOperationException(); }
        @Override public InputStream getByteStream() { return input; }
        @Override public void setByteStream(InputStream value) { input = value; }
        @Override public String getStringData() { return null; }
        @Override public void setStringData(String value) { throw new UnsupportedOperationException(); }
        @Override public String getSystemId() { return systemId; }
        @Override public void setSystemId(String value) { systemId = value; }
        @Override public String getPublicId() { return publicId; }
        @Override public void setPublicId(String value) { publicId = value; }
        @Override public String getBaseURI() { return "cds-schema:/"; }
        @Override public void setBaseURI(String value) { }
        @Override public String getEncoding() { return "UTF-8"; }
        @Override public void setEncoding(String value) { }
        @Override public boolean getCertifiedText() { return false; }
        @Override public void setCertifiedText(boolean value) { }
    }
}
