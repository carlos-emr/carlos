/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.web;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.lab.ca.all.AcknowledgementData;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabTestValues;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("lab")
class LabDisplayHelperUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldPreserveLegacyBase64WireBytesAndProvenance_whenCachingLabResults() throws Exception {
        createAndRegisterMock(MeasurementsDeletedDao.class);
        createAndRegisterMock(MeasurementDao.class);
        createAndRegisterMock(MeasurementsExtDao.class);
        createAndRegisterMock(MeasurementMapDao.class);
        createAndRegisterMock(ConsultDocsDao.class);
        createAndRegisterMock(ConsultResponseDocDao.class);
        createAndRegisterMock(Hl7TextInfoDao.class);
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(EFormDocsDao.class);
        var dao = createAndRegisterMock(Hl7TextMessageDao.class);
        var message = mock(Hl7TextMessage.class);
        when(dao.find(123)).thenReturn(message);
        when(message.getType()).thenReturn("FAKE");
        when(message.getBase64EncodedeMessage()).thenReturn("RkFLRS1ITDc=");
        var lab = mock(LabResultData.class, invocation -> invocation.getMethod().getReturnType() == String.class
                ? "" : RETURNS_DEFAULTS.answer(invocation));
        lab.labType = "HL7";
        when(lab.getSegmentID()).thenReturn("123");
        var handler = mock(MessageHandler.class);
        when(handler.getOBRCount()).thenReturn(1);
        when(handler.getOBXCount(0)).thenReturn(1);
        when(handler.getOBXName(0, 0)).thenReturn("FAKE-test");
        when(handler.getOBXIdentifier(0, 0)).thenReturn("FAKE-id");
        var values = new ArrayList<Map<String, Serializable>>();
        values.add(Map.of("lab_no", "123", "result", "café Ω", "units", "mmol/L"));
        var expected = new HashMap<String, ArrayList<Map<String, Serializable>>>();
        expected.put("FAKE-id", values);
        // Independent legacy encoder retained in the application; exact bytes pin the migration.
        String legacy = org.apache.commons.codec.binary.Base64.encodeBase64String(MiscUtils.serialize(expected));
        try (var acknowledgements = mockStatic(AcknowledgementData.class);
             var matching = mockStatic(Hl7textResultsData.class);
             var factory = mockStatic(Factory.class);
             var tests = mockStatic(CommonLabTestValues.class)) {
            acknowledgements.when(() -> AcknowledgementData.getAcknowledgements("123")).thenReturn(new ArrayList<>());
            matching.when(() -> Hl7textResultsData.getMatchingLabs("123")).thenReturn("123");
            factory.when(() -> Factory.getHandler("123")).thenReturn(handler);
            tests.when(() -> CommonLabTestValues.findValuesForTest("HL7", 17, "FAKE-test", "FAKE-id")).thenReturn(values);
            var xml = LabDisplayHelper.labToXml(17, lab);
            assertThat(xml.getElementsByTagName("mapOfTestValues").item(0).getTextContent()).isEqualTo(legacy);
            assertThat(LabDisplayHelper.getMapOfTestValues(xml)).isEqualTo(expected);
            assertThat(LabDisplayHelper.getHl7Body(xml)).isEqualTo("FAKE-HL7");
            assertThat(LabDisplayHelper.getMultiLabId(xml)).isEqualTo("123");
            tests.verify(() -> CommonLabTestValues.findValuesForTest("HL7", 17, "FAKE-test", "FAKE-id"));
        }
    }
}
