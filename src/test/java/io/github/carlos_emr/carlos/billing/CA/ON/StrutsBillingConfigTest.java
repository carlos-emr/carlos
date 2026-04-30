/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
 * Maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billing.CA.ON;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import io.github.carlos_emr.carlos.billings.ca.on.service.BillingFileWriteException;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

@DisplayName("Struts billing config Tests")
@Tag("unit")
@Tag("billing")
class StrutsBillingConfigTest {

    private static final Path STRUTS_BILLING_XML =
            Path.of("src/main/webapp/WEB-INF/classes/struts-billing.xml");

    @Test
    void shouldResolveEveryConfiguredExceptionMappingClass() throws Exception {
        Map<String, String> mappings = collectExceptionMappings();

        assertThat(mappings)
                .as("struts-billing.xml should declare exception mappings")
                .isNotEmpty();

        for (Map.Entry<String, String> mapping : mappings.entrySet()) {
            Class<?> mappedException = Class.forName(mapping.getValue());
            assertThat(Throwable.class)
                    .as("exception mapping '%s' should point to a Throwable type", mapping.getKey())
                    .isAssignableFrom(mappedException);
        }
    }

    @Test
    void shouldMapBillingValidationException_toCurrentValidatorPackage() throws Exception {
        Map<String, String> mappings = collectExceptionMappings();

        assertThat(mappings)
                .containsEntry("billingValidationError", BillingValidationException.class.getName());
    }

    @Test
    void shouldMapBillingFileWriteException_toCurrentServicePackage() throws Exception {
        Map<String, String> mappings = collectExceptionMappings();

        assertThat(mappings)
                .as("BillingFileWriteException must route to billingFileWriteError so the "
                        + "user-facing JSP renders instead of the generic CARLOS error page")
                .containsEntry("billingFileWriteError", BillingFileWriteException.class.getName());
    }

    private Map<String, String> collectExceptionMappings() throws Exception {
        Document doc = parse(STRUTS_BILLING_XML);
        NodeList mappings = doc.getElementsByTagName("exception-mapping");
        Map<String, String> out = new LinkedHashMap<>();
        for (int i = 0; i < mappings.getLength(); i++) {
            if (mappings.item(i) instanceof Element element) {
                out.put(element.getAttribute("result"), element.getAttribute("exception"));
            }
        }
        return out;
    }

    private Document parse(Path configPath) throws Exception {
        DocumentBuilder db = newHardenedDocumentBuilder();
        try (InputStream in = new FileInputStream(configPath.toFile())) {
            return db.parse(in);
        }
    }

    private DocumentBuilder newHardenedDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setNamespaceAware(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        db.setEntityResolver((publicId, systemId) ->
                new InputSource(new java.io.StringReader("")));
        return db;
    }
}
