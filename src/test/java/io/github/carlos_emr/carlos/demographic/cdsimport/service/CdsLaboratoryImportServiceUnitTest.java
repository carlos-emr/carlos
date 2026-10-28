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

import io.github.carlos_emr.carlos.commn.dao.CdsLaboratoryResultDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.CdsLaboratoryResult;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.CdsLaboratoryMessageParser;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

@Tag("unit")
@org.junit.jupiter.api.parallel.Isolated("CDS laboratory adapter verifies independence from the default timezone")
class CdsLaboratoryImportServiceUnitTest {
    @TempDir Path directory;

    @Test void shouldReadActualFlagChoice_whenSourceUsesCriticalCode() throws Exception {
        var fields=new CdsFieldParser();
        var service=new CdsLaboratoryImportService(fields,new CdsClinicalNormalizationService(fields),null,null,
                null,null,null,null,null);
        var source=CdsClinicalFixture.record(directory,"LaboratoryResults",Map.of(
                "LaboratoryName","Synthetic lab", "ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum","HH",
                "CollectionDateTime/YearOnly","2024"));
        assertThatCode(()->service.validate(source,null,null,null)).doesNotThrowAnyException();
    }

    @Test void shouldRoundTripLiteralHl7Delimiters_whenResultContainsSpecialCharacters() throws Exception {
        var result=result(); result.setResultValue("5|6^7&8~9\\10"); result.setUnitOfMeasure("mmol/L");
        result.setAbnormalFlag("LL"); result.setResultStatus("P"); result.setPhysiciansNotes("PRIVATE PHYSICIAN NOTE");
        var date=CdsTemporal.parse(null,"2024-03",null,null,"CollectionDateTime");
        String message=CdsLaboratoryImportService.message(result,patient(),date);
        var parser=new CdsLaboratoryMessageParser(); parser.init(message);
        assertThat(parser.getOBRCount()).isEqualTo(1);
        assertThat(parser.getOBXCount(0)).isEqualTo(1);
        assertThat(parser.getOBXResult(0,0)).isEqualTo("5|6^7&8~9\\10");
        assertThat(parser.getOBXUnits(0,0)).isEqualTo("mmol/L");
        assertThat(parser.isOBXAbnormal(0,0)).isTrue();
        assertThat(parser.getTimeStamp(0,0)).isEqualTo("2024-03");
        assertThat(parser.getOBXResultStatus(0,0)).isNotEqualTo("Final");
        assertThat(message).doesNotContain("PRIVATE PHYSICIAN NOTE");
    }

    @Test void shouldPreserveOffsetAndYearPrecision_whenGeneratingCompatibilityMessage() throws Exception {
        var result=result();
        var year=CdsTemporal.parse("2023",null,null,null,"CollectionDateTime");
        var parser=new CdsLaboratoryMessageParser();
        parser.init(CdsLaboratoryImportService.message(result,patient(),year));
        assertThat(parser.getTimeStamp(0,0)).isEqualTo("2023");
        var offset=CdsTemporal.parse(null,null,null,"2023-10-29T01:30:00-04:00","CollectionDateTime");
        parser.init(CdsLaboratoryImportService.message(result,patient(),offset));
        assertThat(parser.getTimeStamp(0,0)).isEqualTo("2023-10-29 01:30:00-04:00");
    }

    @Test void shouldEscapeNewSegments_whenSourceContainsLineBreaks() {
        assertThat(CdsLaboratoryImportService.escape("value\rOBX|fake\nNTE|fake"))
                .isEqualTo("value\\X0D\\OBX\\F\\fake\\X0A\\NTE\\F\\fake");
    }

    @Test
    void shouldKeepNativeAndInboxMeaningsAligned_whenStatusIsFreeTextFinalAndFlagIsPlainTextH() throws Exception {
        var stored=write(java.util.Map.of("LaboratoryName","Synthetic lab","TestName","test",
                "TestResultStatus","Final","ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsPlainText","H"),
                CdsTemporal.parse(null,null,"2024-03-01",null,"CollectionDateTime"),CdsTemporal.parse(null,null,null,null,"LabRequisitionDateTime"));
        var info=(io.github.carlos_emr.carlos.commn.model.Hl7TextInfo)stored.stream()
                .filter(io.github.carlos_emr.carlos.commn.model.Hl7TextInfo.class::isInstance).findFirst().orElseThrow();
        assertThat(info.getFinalResultCount()).isEqualTo(1);
        assertThat(info.getResultStatus()).isEqualTo("A");
        assertThat(info.getReportStatus()).isEqualTo("Final");
        var typed=(CdsLaboratoryResult)stored.stream().filter(CdsLaboratoryResult.class::isInstance).findFirst().orElseThrow();
        assertThat(typed.getResultStatus()).isEqualTo("Final"); assertThat(typed.getAbnormalFlag()).isEqualTo("H");
        var parser=new CdsLaboratoryMessageParser();
        parser.init(CdsLaboratoryImportService.message(typed,patient(),CdsTemporal.parse(null,null,"2024-03-01",null,"CollectionDateTime")));
        assertThat(parser.getOBXFinalResultCount()).isEqualTo(info.getFinalResultCount());
        assertThat(parser.isOBXAbnormal(0,0)).isTrue();
    }

    @Test
    void shouldPreserveUnclassifiedStatusAndFlag_withoutAssertingFinalOrNormal() throws Exception {
        var stored=write(java.util.Map.of("LaboratoryName","Synthetic lab","TestResultStatus"," unknown ",
                "ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsPlainText","not classified"),
                CdsTemporal.parse(null,null,"2024-03-01",null,"CollectionDateTime"),CdsTemporal.parse(null,null,null,null,"LabRequisitionDateTime"));
        var info=(io.github.carlos_emr.carlos.commn.model.Hl7TextInfo)stored.stream()
                .filter(io.github.carlos_emr.carlos.commn.model.Hl7TextInfo.class::isInstance).findFirst().orElseThrow();
        assertThat(info.getFinalResultCount()).isZero(); assertThat(info.getResultStatus()).isNull();
        assertThat(info.getReportStatus()).isEqualTo(" unknown ");
        var typed=(CdsLaboratoryResult)stored.stream().filter(CdsLaboratoryResult.class::isInstance).findFirst().orElseThrow();
        var parser=new CdsLaboratoryMessageParser();
        parser.init(CdsLaboratoryImportService.message(typed,patient(),CdsTemporal.parse(null,null,"2024-03-01",null,"CollectionDateTime")));
        assertThat(parser.getOBXResultStatus(0,0)).isEqualTo(" unknown ");
        assertThat(parser.getOBXAbnormalFlag(0,0)).isEqualTo("not classified"); assertThat(parser.isOBXAbnormal(0,0)).isFalse();
    }

    @Test
    void shouldPreserveTextWhitespaceAndFractionalClockValues_whenNativeMessageIsRead() throws Exception {
        var result=result(); result.setResultValue("\u2003  <= 5 \t"); result.setUnitOfMeasure("  unit \t");
        result.setReferenceRange("  1 - 9 \t"); result.setInformationFromLab("  information \n");
        result.setTestName("  literal test ");
        var date=CdsTemporal.parse(null,null,null,"2024-01-01T12:13:14.125000Z","CollectionDateTime");
        var parser=new CdsLaboratoryMessageParser(); parser.init(CdsLaboratoryImportService.message(result,patient(),date));
        assertThat(parser.getOBXResult(0,0)).isEqualTo(result.getResultValue());
        assertThat(parser.getOBXUnits(0,0)).isEqualTo(result.getUnitOfMeasure());
        assertThat(parser.getOBXReferenceRange(0,0)).isEqualTo(result.getReferenceRange());
        assertThat(parser.getOBXName(0,0)).isEqualTo(result.getTestName());
        assertThat(parser.getOBRName(0)).isEqualTo(result.getTestName());
        assertThat(parser.getOBXComment(0,0,0)).isEqualTo(result.getInformationFromLab());
        assertThat(parser.getTimeStamp(0,0)).isEqualTo("2024-01-01 12:13:14.125000+00:00");
    }

    @Test
    void shouldKeepClockAndOffsetIndependentOfServerTimezone_whenSourceTimeFallsInADstGap() throws Exception {
        var result=result(); var previous=java.util.TimeZone.getDefault();
        try {
            java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("America/Toronto"));
            var parser=new CdsLaboratoryMessageParser();
            parser.init(CdsLaboratoryImportService.message(result,patient(),
                    CdsTemporal.parse(null,null,null,"2024-03-10T02:30:00","CollectionDateTime")));
            assertThat(parser.getTimeStamp(0,0)).isEqualTo("2024-03-10 02:30:00");
            parser.init(CdsLaboratoryImportService.message(result,patient(),
                    CdsTemporal.parse(null,null,null,"2024-03-10T02:30:00-05:00","CollectionDateTime")));
            assertThat(parser.getTimeStamp(0,0)).isEqualTo("2024-03-10 02:30:00-05:00");
        } finally { java.util.TimeZone.setDefault(previous); }
    }

    @Test
    void shouldKeepCalendarPrecisionAndTimezone_whenSourceHasNoDayOrClock() throws Exception {
        var result=result(); var parser=new CdsLaboratoryMessageParser();
        parser.init(CdsLaboratoryImportService.message(result,patient(),CdsTemporal.parse("2024Z",null,null,null,"CollectionDateTime")));
        assertThat(parser.getTimeStamp(0,0)).isEqualTo("2024+00:00");
        parser.init(CdsLaboratoryImportService.message(result,patient(),CdsTemporal.parse(null,"2024-03-05:00",null,null,"CollectionDateTime")));
        assertThat(parser.getTimeStamp(0,0)).isEqualTo("2024-03-05:00");
    }

    @Test
    void shouldKeepTypedAndCompatibilityDatesShiftedTogether_whileSourceEvidenceRemainsOriginal() throws Exception {
        var original=CdsTemporal.parse(null,null,null,"2024-01-30T12:13:14.125000-05:00","CollectionDateTime");
        var shifted=CdsTemporal.shift(original,3,"CollectionDateTime");
        var requisition=CdsTemporal.shift(CdsTemporal.parse(null,null,"2024-01-29",null,"LabRequisitionDateTime"),3,"LabRequisitionDateTime");
        var stored=write(java.util.Map.of("LaboratoryName","Synthetic lab","ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum","N"),shifted,requisition);
        var typed=(CdsLaboratoryResult)stored.stream().filter(CdsLaboratoryResult.class::isInstance).findFirst().orElseThrow();
        assertThat(typed.getCollectionDate()).isEqualTo("2024-02-02T12:13:14.125000-05:00");
        assertThat(typed.getRequisitionDate()).isEqualTo("2024-02-01");
        assertThat(shifted.original()).isEqualTo(original.original());
        var parser=new CdsLaboratoryMessageParser(); parser.init(CdsLaboratoryImportService.message(typed,patient(),shifted));
        assertThat(parser.getTimeStamp(0,0)).isEqualTo("2024-02-02 12:13:14.125000-05:00");
    }

    @Test
    void shouldLabelASingleReferenceLimit_withoutInventingANegativeRange() throws Exception {
        var result=result(); result.setHighLimit("5");
        var parser=new CdsLaboratoryMessageParser();
        parser.init(CdsLaboratoryImportService.message(result,patient(),CdsTemporal.parse(null,null,"2024-01-01",null,"CollectionDateTime")));
        assertThat(parser.getOBXReferenceRange(0,0)).isEqualTo("High: 5");
    }

    @Test
    void shouldKeepRestrictedResultsOutOfLegacyMirrors_whenSourceIsBlocked() throws Exception {
        var stored=write(java.util.Map.of("LaboratoryName","Synthetic lab","BlockedTestResult","Y",
                "ResultNormalAbnormalFlag/resultNormalAbnormalFlagAsEnum","N"),
                CdsTemporal.parse(null,null,"2024-01-01",null,"CollectionDateTime"),CdsTemporal.parse(null,null,null,null,"LabRequisitionDateTime"));
        assertThat(stored).hasSize(1); assertThat(stored.getFirst()).isInstanceOf(CdsLaboratoryResult.class);
        assertThat(((CdsLaboratoryResult)stored.getFirst()).getNativeLabNo()).isNull();
    }

    private java.util.List<Object> write(Map<String,String> values,
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto collection,
            io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto requisition) throws Exception {
        var preparation=org.mockito.Mockito.mock(CdsNativePreparationService.class);
        var ledger=org.mockito.Mockito.mock(CdsImportLedgerService.class);
        var demographics=org.mockito.Mockito.mock(DemographicDao.class);
        var messages=org.mockito.Mockito.mock(Hl7TextMessageDao.class);
        var information=org.mockito.Mockito.mock(Hl7TextInfoDao.class);
        var routing=org.mockito.Mockito.mock(PatientLabRoutingDao.class);
        var results=org.mockito.Mockito.mock(CdsLaboratoryResultDao.class);
        var command=org.mockito.Mockito.mock(io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand.class);
        var record=org.mockito.Mockito.mock(io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto.class);
        org.mockito.Mockito.when(command.providerNo()).thenReturn("999999");
        org.mockito.Mockito.when(record.effectiveSha256()).thenReturn("source");
        org.mockito.Mockito.when(record.sourceXPath()).thenReturn("/LaboratoryResults[1]");
        org.mockito.Mockito.when(preparation.load("run",1L,"unit","source",command,
                io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsLaboratoryNativeDto.class))
                .thenReturn(new io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsLaboratoryNativeDto(values,collection,requisition));
        var unit=org.mockito.Mockito.mock(io.github.carlos_emr.carlos.commn.model.CdsImportUnit.class);
        org.mockito.Mockito.when(unit.getPatientKey()).thenReturn("patient");
        org.mockito.Mockito.when(ledger.unit("run","unit")).thenReturn(unit);
        org.mockito.Mockito.when(demographics.getDemographicById(999999)).thenReturn(patient());
        var stored=new java.util.ArrayList<Object>();
        org.mockito.stubbing.Answer<Void> capture=call->{
            Object row=call.getArgument(0); stored.add(row);
            if(!(row instanceof CdsLaboratoryResult)) org.springframework.test.util.ReflectionTestUtils.setField(row,"id",stored.size());
            return null;
        };
        org.mockito.Mockito.doAnswer(capture).when(messages).persist(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(capture).when(information).persist(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(capture).when(routing).persist(org.mockito.ArgumentMatchers.any());
        org.mockito.Mockito.doAnswer(capture).when(results).persist(org.mockito.ArgumentMatchers.any());
        var fields=new CdsFieldParser();
        var service=new CdsLaboratoryImportService(fields,new CdsClinicalNormalizationService(fields),preparation,ledger,
                demographics,messages,information,routing,results);
        try(var active=org.mockito.Mockito.mockStatic(CdsImportUnitService.class)) {
            service.write("run",1L,"unit",999999,record,command);
        }
        return stored;
    }

    private static CdsLaboratoryResult result() {
        var result=new CdsLaboratoryResult(); result.setId("a".repeat(64)); result.setLaboratoryName("Synthetic lab");
        result.setTestName("Synthetic result"); result.setTestCode("1234"); result.setAccessionNumber("a|b");
        result.setAbnormalFlag("N"); return result;
    }
    private static Demographic patient() {
        var patient=new Demographic(); patient.setDemographicNo(999999); patient.setFirstName("Synthetic");
        patient.setLastName("Fixture"); return patient;
    }
}
