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
package io.github.carlos_emr.carlos.webserv.rest;

import java.nio.charset.StandardCharsets;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DemographicConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DocumentTo1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the OAuth-protected REST data-write surfaces behind the
 * {@code submit_patient_data}, {@code update_patient_data} and {@code upload_document} rows of
 * {@code docs/api/cortico-carlos-compatibility.md}.
 *
 * <p>These prove the native CARLOS REST contracts published under {@code /ws/services}:
 * {@code POST/PUT /ws/services/demographics} and
 * {@code POST /ws/services/document/saveDocumentToDemographic}. They exercise representative
 * successful calls; mapping literal Cortico/Juno demographic or document operation paths onto them
 * is an adapter/proxy responsibility and is out of scope here. The OAuth rejection contract that
 * guards these routes is covered by {@code OauthRestAuthContractUnitTest}.</p>
 *
 * <p>Fixtures are synthetic; no PHI, credentials, or live calls are used.</p>
 *
 * @since 2026-06-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OAuth REST data-write contract")
@Tag("unit")
@Tag("rest")
class OauthRestDataContractUnitTest {

    private static final Integer DEMOGRAPHIC_NO = 777;
    private static final String PROVIDER_NO = "999998";
    private static final byte[] FILE_CONTENTS = "synthetic document body".getBytes(StandardCharsets.UTF_8);

    @Mock
    private DemographicManager demographicManager;

    @Mock
    private DemographicConverter demoConverter;

    @Mock
    private DocumentManager documentManager;

    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
    }

    @Test
    @DisplayName("should create demographic when submit_patient_data maps to POST /ws/services/demographics")
    void shouldCreateDemographic_whenSubmittingPatientData() {
        DemographicService service = newDemographicService();
        DemographicTo1 data = new DemographicTo1();
        Demographic domain = new Demographic(DEMOGRAPHIC_NO);
        DemographicTo1 persisted = new DemographicTo1();
        when(demoConverter.getAsDomainObject(loggedInInfo, data)).thenReturn(domain);
        when(demoConverter.getAsTransferObject(loggedInInfo, domain)).thenReturn(persisted);

        DemographicTo1 result = service.createDemographicData(data);

        assertThat(result).isSameAs(persisted);
        verify(demographicManager).createDemographic(loggedInInfo, domain, data.getAdmissionProgramId());
    }

    @Test
    @DisplayName("should update demographic when update_patient_data maps to PUT /ws/services/demographics")
    void shouldUpdateDemographic_whenUpdatingPatientData() {
        DemographicService service = newDemographicService();
        DemographicTo1 data = new DemographicTo1();
        data.setDemographicNo(DEMOGRAPHIC_NO);
        Demographic domain = new Demographic(DEMOGRAPHIC_NO);
        DemographicTo1 persisted = new DemographicTo1();
        when(demoConverter.getAsDomainObject(loggedInInfo, data)).thenReturn(domain);
        when(demoConverter.getAsTransferObject(loggedInInfo, domain)).thenReturn(persisted);

        DemographicTo1 result = service.updateDemographicData(data);

        assertThat(result).isSameAs(persisted);
        verify(demographicManager).updateDemographic(loggedInInfo, domain);
    }

    @Test
    @DisplayName("should save document when upload_document maps to POST /ws/services/document/saveDocumentToDemographic")
    void shouldSaveDocument_whenUploadingDocument() throws Exception {
        DocumentService service = newDocumentService();
        when(documentManager.createDocument(eq(loggedInInfo), any(Document.class), eq(DEMOGRAPHIC_NO),
                eq(PROVIDER_NO), eq(FILE_CONTENTS))).thenReturn(new Document());

        Response response = service.saveDocumentToDemographic(validDocument());

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(documentManager).createDocument(eq(loggedInInfo), any(Document.class), eq(DEMOGRAPHIC_NO),
                eq(PROVIDER_NO), eq(FILE_CONTENTS));
    }

    @Test
    @DisplayName("should reject document save when required fields are missing")
    void shouldRejectDocumentSave_whenRequiredFieldsMissing() {
        DocumentService service = newDocumentService();
        DocumentTo1 document = validDocument();
        document.setFileContents(null);

        Response response = service.saveDocumentToDemographic(document);

        assertThat(response.getStatus()).isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
    }

    private DemographicService newDemographicService() {
        DemographicService service = new DemographicService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        ReflectionTestUtils.setField(service, "demographicManager", demographicManager);
        ReflectionTestUtils.setField(service, "demoConverter", demoConverter);
        return service;
    }

    private DocumentService newDocumentService() {
        DocumentService service = new DocumentService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        ReflectionTestUtils.setField(service, "documentManager", documentManager);
        return service;
    }

    private static DocumentTo1 validDocument() {
        DocumentTo1 document = new DocumentTo1();
        document.setFileName("synthetic.pdf");
        document.setFileContents(FILE_CONTENTS);
        document.setDemographicNo(DEMOGRAPHIC_NO);
        document.setProviderNo(PROVIDER_NO);
        document.setContentType("application/pdf");
        return document;
    }
}
