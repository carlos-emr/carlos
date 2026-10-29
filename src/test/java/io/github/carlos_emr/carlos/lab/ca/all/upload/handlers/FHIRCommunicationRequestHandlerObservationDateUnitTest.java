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
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import ca.uhn.fhir.context.FhirContext;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ProviderInboxRoutingDao;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.support.ConcurrentRounds;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.hl7.fhir.dstu3.model.Attachment;
import org.hl7.fhir.dstu3.model.CommunicationRequest;
import org.hl7.fhir.dstu3.model.DateTimeType;
import org.hl7.fhir.dstu3.model.Reference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;

/**
 * A FHIR CommunicationRequest document is filed under the clinic's calendar date of its
 * {@code authoredOn} instant. The conversion runs for concurrent uploads, so it must not share a
 * mutable formatter, and a request with no {@code authoredOn} is refused before its PDF is written
 * (issue #5301).
 */
@Tag("unit")
@Tag("lab")
@DisplayName("FHIR CommunicationRequest observation date")
class FHIRCommunicationRequestHandlerObservationDateUnitTest extends CarlosUnitTestBase {

    private static final ZoneId CLINIC_ZONE = ZoneId.of("America/Toronto");
    private static final FhirContext FHIR = FhirContext.forDstu3();

    @TempDir
    Path documentDir;

    private ProviderInboxRoutingDao routing;
    private MockedStatic<Utilities> utilities;
    private MockedStatic<EDocUtil> documents;
    private String previousDocumentDir;
    private TimeZone previousZone;

    @BeforeEach
    void setUp() {
        routing = mock(ProviderInboxRoutingDao.class);
        registerMock(ProviderInboxRoutingDao.class, routing);
        previousDocumentDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR");
        CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", documentDir.toString());
        previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone(CLINIC_ZONE));

        utilities = mockStatic(Utilities.class);
        utilities.when(() -> Utilities.savePdfFile(any(InputStream.class), anyString())).thenAnswer(call -> {
            Path saved = documentDir.resolve(call.<String>getArgument(1));
            try (InputStream pdf = call.getArgument(0)) {
                Files.write(saved, pdf.readAllBytes());
            }
            return saved.toString();
        });
        documents = mockStatic(EDocUtil.class);
        documents.when(() -> EDocUtil.addDocumentSQL(any(EDoc.class))).thenReturn("42");
    }

    @AfterEach
    void tearDown() {
        documents.close();
        utilities.close();
        TimeZone.setDefault(previousZone);
        if (previousDocumentDir == null) {
            CarlosProperties.getInstance().remove("DOCUMENT_DIR");
        } else {
            CarlosProperties.getInstance().setProperty("DOCUMENT_DIR", previousDocumentDir);
        }
    }

    private static byte[] onePagePdf() throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            document.save(out);
            return out.toByteArray();
        }
    }

    /** Writes a synthetic CommunicationRequest upload and returns its path. */
    private Path upload(String authoredOn) throws IOException {
        CommunicationRequest request = new CommunicationRequest();
        request.addIdentifier().setValue("synthetic/request-1");
        request.setSubject(new Reference("Patient/9001"));
        request.addRecipient(new Reference("Practitioner/999998"));
        if (authoredOn != null) {
            request.setAuthoredOnElement(new DateTimeType(authoredOn));
        }
        request.addPayload().setContent(new Attachment().setTitle("Synthetic referral").setData(onePagePdf()));
        Path file = documentDir.resolve("request.json");
        Files.writeString(file, FHIR.newJsonParser().encodeResourceToString(request));
        return file;
    }

    private static String parse(Path file) {
        return new FHIRCommunicationRequestHandler().parse(null, "FHIR", file.toString(), 1, "127.0.0.1");
    }

    @Test
    void shouldFileTheClinicDate_whenTheRequestWasAuthoredLateInTheEvening() throws Exception {
        // 23:30 in Toronto on 1 March is already 2 March in UTC.
        String result = parse(upload("2026-03-01T23:30:00-05:00"));

        ArgumentCaptor<EDoc> filed = ArgumentCaptor.forClass(EDoc.class);
        documents.verify(() -> EDocUtil.addDocumentSQL(filed.capture()));
        assertThat(result).isEqualTo("success");
        assertThat(filed.getValue().getObservationDate()).isEqualTo("2026-03-01");
        verify(routing).addToProviderInboxStrict("999998", 42, "DOC");
    }

    @Test
    void shouldRefuseTheRequestBeforeWritingItsPdf_whenItHasNoAuthoredOnDate() throws Exception {
        String result;
        try (LogCapture logs = LogCapture.forLogger(FHIRCommunicationRequestHandler.class)) {
            result = parse(upload(null));

            assertThat(logs.messages()).anySatisfy(message -> assertThat(message).contains("authoredOn"));
        }

        assertThat(result).isNull();
        utilities.verifyNoInteractions();
        documents.verifyNoInteractions();
        assertThat(documentDir).isDirectoryNotContaining(path -> path.toString().endsWith(".pdf"));
    }

    @Test
    void shouldFormatTheClinicDate_forEachZone() {
        Date lateEvening = Date.from(Instant.parse("2026-03-02T04:30:00Z"));

        assertThat(FHIRCommunicationRequestHandler.observationDate(lateEvening, CLINIC_ZONE)).isEqualTo("2026-03-01");
        assertThat(FHIRCommunicationRequestHandler.observationDate(lateEvening, ZoneId.of("UTC"))).isEqualTo("2026-03-02");
        // Clocks spring forward at 02:00 on 8 March 2026; 03:30 that morning is still the 8th.
        assertThat(FHIRCommunicationRequestHandler.observationDate(
                Date.from(Instant.parse("2026-03-08T07:30:00Z")), CLINIC_ZONE)).isEqualTo("2026-03-08");
        assertThat(FHIRCommunicationRequestHandler.observationDate(null, CLINIC_ZONE)).isNull();
    }

    @Test
    void shouldFormatEveryDate_whenCalledConcurrently() throws Exception {
        LocalDate first = LocalDate.of(2000, 1, 1);

        var failures = ConcurrentRounds.run(8, 1500, (thread, round) -> {
            // Every call works on a date no other call uses, so a crossed result cannot pass.
            LocalDate date = first.plusDays(thread * 2000L + round);
            Date authoredOn = Date.from(date.atTime(23, 59).atZone(CLINIC_ZONE).toInstant());

            assertThat(FHIRCommunicationRequestHandler.observationDate(authoredOn, CLINIC_ZONE))
                    .isEqualTo(date.toString());
        });

        assertThat(failures).isEmpty();
    }
}
