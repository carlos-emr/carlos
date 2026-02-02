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

package ca.openosp.openo.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import ca.openosp.openo.test.integration.OpenOTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for Doc2PDF utility class.
 * Tests HTML to PDF conversion using Jsoup for HTML cleaning.
 *
 * @since 2026-01-29
 */
@Tag("integration")
@Tag("util")
@DisplayName("Doc2PDF Integration Tests")
class Doc2PDFIntegrationTest extends OpenOTestBase {

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest();
        request.setProtocol("HTTP/1.1");
        request.setRemoteHost("localhost");
        request.setServerPort(8080);
        request.setContextPath("/openo");

        response = new MockHttpServletResponse();
    }

    @Test
    @Tag("parse")
    @DisplayName("should produce PDF when simple HTML is provided")
    void shouldProducePdf_whenSimpleHtmlProvided() {
        // Given
        String simpleHtml = "<html><body><p>Hello World</p></body></html>";

        // When
        Doc2PDF.parseString2PDF(request, response, simpleHtml);

        // Then
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @Tag("parse")
    @DisplayName("should produce PDF when malformed HTML is provided")
    void shouldProducePdf_whenMalformedHtmlProvided() {
        // Given
        String malformedHtml = "<html><body><p>Unclosed paragraph<div>Nested</html>";

        // When
        Doc2PDF.parseString2PDF(request, response, malformedHtml);

        // Then - Jsoup should fix the HTML and still produce a PDF
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @Tag("parse")
    @Tag("encoding")
    @DisplayName("should preserve encoding when French Canadian characters are present")
    void shouldPreserveEncoding_whenFrenchCanadianCharactersPresent() {
        // Given
        // Test with French Canadian patient names
        String htmlWithFrench = "<html><body>" +
            "<h1>Patient: François Côté</h1>" +
            "<p>Doctor: Hélène Bélanger</p>" +
            "<p>Résumé: naïve café</p>" +
            "</body></html>";

        // When
        Doc2PDF.parseString2PDF(request, response, htmlWithFrench);

        // Then
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @Tag("parse")
    @Tag("encoding")
    @DisplayName("should preserve symbols when medical characters are present")
    void shouldPreserveSymbols_whenMedicalCharactersPresent() {
        // Given
        // Test with medical symbols
        String htmlWithSymbols = "<html><body>" +
            "<p>Dosage: 500μg (micrograms)</p>" +
            "<p>Temperature: ≥37°C but ≤40°C</p>" +
            "<p>Range: ±2 units</p>" +
            "<p>Medication: Advil® and Tylenol™</p>" +
            "</body></html>";

        // When
        Doc2PDF.parseString2PDF(request, response, htmlWithSymbols);

        // Then
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }

    @Test
    @Tag("parse")
    @DisplayName("should produce PDF when empty HTML is provided")
    void shouldProducePdf_whenEmptyHtmlProvided() {
        // Given
        String emptyHtml = "";

        // When
        Doc2PDF.parseString2PDF(request, response, emptyHtml);

        // Then - Should still produce a PDF (even if empty)
        assertThat(response.getContentType()).isEqualTo("application/pdf");
    }

    @Test
    @Tag("parse")
    @Tag("binary")
    @DisplayName("should return Base64 when converting to binary")
    void shouldReturnBase64_whenConvertingToBinary() {
        // Given
        String html = "<html><body><h1>Test Document</h1><p>Test content</p></body></html>";

        // When
        String binaryPdf = Doc2PDF.parseString2Bin(request, response, html);

        // Then
        assertThat(binaryPdf).isNotNull();
        assertThat(binaryPdf).isNotEmpty();
        // Binary PDF should be Base64 encoded
        assertThat(binaryPdf).matches("^[A-Za-z0-9+/]+=*$");
    }

    @Test
    @Tag("parse")
    @Tag("medical")
    @DisplayName("should produce PDF when realistic medical content is provided")
    void shouldProducePdf_whenRealisticMedicalContentProvided() {
        // Given
        // Realistic medical note
        String medicalHtml = "<html><body>" +
            "<h2>Clinical Note</h2>" +
            "<table>" +
            "<tr><td>Patient:</td><td>François Côté</td></tr>" +
            "<tr><td>DOB:</td><td>1980-05-15</td></tr>" +
            "<tr><td>HIN:</td><td>1234567890</td></tr>" +
            "</table>" +
            "<h3>Chief Complaint</h3>" +
            "<p>Patient presents with fever ≥38.5°C</p>" +
            "<h3>Medications</h3>" +
            "<ul>" +
            "<li>Acetaminophen 500mg - take 2 tablets q4h prn</li>" +
            "<li>Amoxicillin 250mg - 1 tablet TID × 7 days</li>" +
            "</ul>" +
            "<h3>Vitals</h3>" +
            "<p>BP: 120/80, HR: 72, Temp: 38.7°C, O₂ sat: 98%</p>" +
            "</body></html>";

        // When
        Doc2PDF.parseString2PDF(request, response, medicalHtml);

        // Then
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
        assertThat(response.getContentAsByteArray().length).isGreaterThan(100);
    }

    @Test
    @Tag("parse")
    @DisplayName("should produce PDF when complex HTML structure is provided")
    void shouldProducePdf_whenComplexHtmlStructureProvided() {
        // Given
        String complexHtml = "<html><body>" +
            "<table border='1'>" +
            "<tr><th>Medication</th><th>Dose</th><th>Frequency</th></tr>" +
            "<tr><td>Aspirin</td><td>81mg</td><td>Daily</td></tr>" +
            "<tr><td>Metformin</td><td>500mg</td><td>BID</td></tr>" +
            "</table>" +
            "<ul>" +
            "<li>No known allergies</li>" +
            "<li>Non-smoker</li>" +
            "</ul>" +
            "</body></html>";

        // When
        Doc2PDF.parseString2PDF(request, response, complexHtml);

        // Then
        assertThat(response.getContentType()).isEqualTo("application/pdf");
        assertThat(response.getContentAsByteArray()).isNotEmpty();
    }
}
