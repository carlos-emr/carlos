/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.clinical.summary;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.EFormData;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.documentManager.EDoc;
import io.github.carlos_emr.carlos.documentManager.EDocUtil;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.managers.DocumentManager;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.LabManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static io.github.carlos_emr.carlos.clinical.summary.ClinicalSummaryAgentProtocol.JSON;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiClinicalSummaryPrototypeSupplementalSourcesUnitTest extends CarlosUnitTestBase {
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo user = mock(LoggedInInfo.class);
    private final MeasurementDao measurements = mock(MeasurementDao.class);
    private final PatientLabRoutingDao routing = mock(PatientLabRoutingDao.class);
    private final LabManager labs = mock(LabManager.class);
    private final FormsManager forms = mock(FormsManager.class);
    private final DocumentManager documents = mock(DocumentManager.class);
    private ObjectNode chart;

    @BeforeEach
    void setUp() throws java.io.IOException {
        registerMock(MeasurementDao.class, measurements);
        registerMock(PatientLabRoutingDao.class, routing);
        registerMock(LabManager.class, labs);
        registerMock(FormsManager.class, forms);
        registerMock(DocumentManager.class, documents);
        chart = JSON.valueToTree(new SyntheticClinicalSummaryProvider().load(null, ClinicalSummaryRequest.synthetic()).getView());
        ((ObjectNode) chart.get("patient_context")).put("id", "demographic-42");
        for (var source : chart.get("sources")) ((ObjectNode) source).put("patient_id", "demographic-42");
    }

    private ClinicalSummaryArtifact load() {
        new SupplementalClinicalSummarySources(security).append(user, 42, chart);
        return new ClinicalSummaryArtifact(chart);
    }

    @Test
    void moduleDenialsPrecedeAllSourceLookups() {
        var result = load();
        assertThat(result.getView().get("validation").toString()).contains("labs_unavailable", "documents_unavailable", "forms_unavailable");
        verifyNoInteractions(measurements, routing, labs, forms, documents);
    }

    @Test
    void measurementsIncludeAllHistoryAndFailOnPatientMismatch() {
        when(security.hasPrivilege(user, "_measurement", "r", 42)).thenReturn(true);
        List<Measurement> history = new ArrayList<>();
        for (int i = 1; i <= 65; i++) {
            Measurement measurement = mock(Measurement.class);
            when(measurement.getId()).thenReturn(i);
            when(measurement.getDemographicId()).thenReturn(42);
            when(measurement.getType()).thenReturn("Blood pressure");
            when(measurement.getDataField()).thenReturn("118/70");
            when(measurement.getMeasuringInstruction()).thenReturn("Seated, mmHg");
            history.add(measurement);
        }
        when(measurements.findByDemographicNo(42)).thenReturn(history);
        assertThat(load().getClaimsById().get("claim-measurement-65").get("text").toString()).contains("118/70", "mmHg");
        when(history.getFirst().getDemographicId()).thenReturn(43);
        assertThatThrownBy(this::load).isInstanceOf(SecurityException.class);
    }

    @Test
    void laboratoryResultsKeepUnitsRangesStatusCommentsAndObservationTime() {
        when(security.hasPrivilege(user, "_lab", "r", 42)).thenReturn(true);
        PatientLabRouting route = mock(PatientLabRouting.class);
        when(route.getDemographicNo()).thenReturn(42);
        when(route.getLabNo()).thenReturn(7);
        when(route.getLabType()).thenReturn("HL7");
        when(routing.findByDemographicAndLabType(42, "HL7")).thenReturn(List.of(route));
        Hl7TextMessage message = mock(Hl7TextMessage.class);
        when(message.getId()).thenReturn(7);
        when(labs.getHl7Message(user, 7)).thenReturn(message);
        MessageHandler parser = mock(MessageHandler.class);
        when(parser.getOBRCount()).thenReturn(1);
        when(parser.getOBXCount(0)).thenReturn(1);
        when(parser.getOBXName(0, 0)).thenReturn("Potassium");
        when(parser.getOBXResult(0, 0)).thenReturn("5.2");
        when(parser.getOBXUnits(0, 0)).thenReturn("mmol/L");
        when(parser.getOBXReferenceRange(0, 0)).thenReturn("3.5-5.0");
        when(parser.getOBXResultStatus(0, 0)).thenReturn("Corrected");
        when(parser.getTimeStamp(0, 0)).thenReturn("2026-09-10 10:30");
        when(parser.getOBXCommentCount(0, 0)).thenReturn(1);
        when(parser.getOBXComment(0, 0, 0)).thenReturn("Sample haemolysed");
        try (var factory = mockStatic(Factory.class)) {
            factory.when(() -> Factory.getHandler(message)).thenReturn(parser);
            assertThat(load().getClaimsById().get("claim-lab-7").get("text").toString())
                    .contains("5.2", "mmol/L", "3.5-5.0", "Corrected", "2026-09-10 10:30", "Sample haemolysed");
        }
    }

    @Test
    void foreignLabRoutingFailsBeforeReadingAnyReport() {
        when(security.hasPrivilege(user, "_lab", "r", 42)).thenReturn(true);
        PatientLabRouting route = mock(PatientLabRouting.class);
        when(route.getDemographicNo()).thenReturn(43);
        when(routing.findByDemographicAndLabType(42, "HL7")).thenReturn(List.of(route));
        assertThatThrownBy(this::load).isInstanceOf(SecurityException.class);
        verifyNoInteractions(labs);
    }

    @Test
    void eformsExposeStoredValuesWithAnIncompleteExtractionNotice() {
        when(security.hasPrivilege(user, "_eform", "r", 42)).thenReturn(true);
        EFormData form = mock(EFormData.class);
        when(form.getDemographicId()).thenReturn(42);
        when(form.getId()).thenReturn(3);
        when(form.isCurrent()).thenReturn(true);
        when(form.getFormData()).thenReturn("<label>Symptom<textarea name='symptom'>No dizziness</textarea></label>");
        when(forms.findByDemographicId(user, 42)).thenReturn(List.of(form));
        var result = load();
        assertThat(result.getClaimsById().get("claim-eform-3").get("text").toString()).contains("No dizziness");
        assertThat(result.getView().get("validation").toString()).contains("source_extraction_incomplete", "eform-3");
    }

    @Test
    void documentOwnershipIsRecheckedBeforeReadingFiles() {
        when(security.hasPrivilege(user, "_edoc", "r", 42)).thenReturn(true);
        EDoc document = mock(EDoc.class);
        when(document.getDocId()).thenReturn("8");
        CtlDocument ownership = new CtlDocument();
        ownership.setId(new CtlDocumentPK("demographic", 43, 8));
        when(documents.getCtlDocumentByDocumentId(user, 8)).thenReturn(ownership);
        try (var list = mockStatic(EDocUtil.class); var reader = mockStatic(ClinicalSummaryTextExtractor.class)) {
            list.when(() -> EDocUtil.listDocs(user, "demographic", "42", "all", EDocUtil.PRIVATE,
                    EDocUtil.EDocSort.OBSERVATIONDATE, "active")).thenReturn(new ArrayList<>(List.of(document)));
            assertThatThrownBy(this::load).isInstanceOf(SecurityException.class);
            reader.verifyNoInteractions();
        }
    }
}
