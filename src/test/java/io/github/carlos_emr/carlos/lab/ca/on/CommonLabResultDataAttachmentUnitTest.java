// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.lab.ca.on;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.ConsultDocsDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultResponseDocDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDocsDao;
import io.github.carlos_emr.carlos.commn.model.ConsultDocs;
import io.github.carlos_emr.carlos.commn.model.ConsultResponseDoc;
import io.github.carlos_emr.carlos.commn.model.EFormDocs;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("fast")
class CommonLabResultDataAttachmentUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"consult", "response", "eform"})
    void shouldExposeSeparateUnavailableRows_whenSourcesAreDisabledOrLegacySourceIsUnknown(String parent) {
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.commn.dao.LabPatientPhysicianInfoDao.class);
        createAndRegisterMock(io.github.carlos_emr.carlos.managers.SecurityInfoManager.class);
        var consults = createAndRegisterMock(ConsultDocsDao.class);
        var responses = createAndRegisterMock(ConsultResponseDocDao.class);
        var eforms = createAndRegisterMock(EFormDocsDao.class);
        ConsultDocs hl7 = new ConsultDocs(12, 7, "L", "provider");
        hl7.setLabType("HL7");
        ConsultDocs unknown = new ConsultDocs(12, 7, "L", "provider");
        when(consults.findByRequestIdDocType(12, "L")).thenReturn(List.of(hl7, unknown, hl7));
        ConsultResponseDoc responseHl7 = new ConsultResponseDoc();
        responseHl7.setDocumentNo(7);
        responseHl7.setDocType("L");
        responseHl7.setLabType("HL7");
        ConsultResponseDoc responseUnknown = new ConsultResponseDoc();
        responseUnknown.setDocumentNo(7);
        responseUnknown.setDocType("L");
        when(responses.findByResponseId(12)).thenReturn(List.of(responseHl7, responseUnknown, responseHl7));
        EFormDocs eformHl7 = new EFormDocs(12, 7, "L", "provider");
        eformHl7.setLabType("HL7");
        EFormDocs eformUnknown = new EFormDocs(12, 7, "L", "provider");
        when(eforms.findByFdidIdDocType(12, "L")).thenReturn(List.of(eformHl7, eformUnknown, eformHl7));
        try (var properties = mockStatic(CarlosProperties.class)) {
            properties.when(CarlosProperties::getInstance).thenReturn(mock(CarlosProperties.class));
            CommonLabResultData reader = new CommonLabResultData();
            List<LabResultData> results = switch (parent) {
                case "consult" -> reader.populateLabResultsData(null, "42", "12", true);
                case "response" -> reader.populateLabResultsDataConsultResponse(null, "42", "12", true);
                case "eform" -> reader.populateLabResultsDataEForm(null, "42", "12", true);
                default -> throw new AssertionError(parent);
            };
            assertThat(results).extracting(LabResultData::getAttachmentKey).containsExactly("HL7:7", "UNRESOLVED:7");
            assertThat(results).allSatisfy(result -> {
                assertThat(result.isAttachmentUnavailable()).isTrue();
                assertThat(result.getLabel()).contains("unavailable");
                assertThat(result.getDateObj()).isNull();
            });
        }
    }
}
