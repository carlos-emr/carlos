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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalNativeDateDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporalPrecisionException;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.AllergyService;
import io.github.carlos_emr.carlos.webserv.rest.conversion.AllergyConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.AllergyResponse;
import io.github.carlos_emr.carlos.webserv.rest.util.CdsTemporalPrecisionExceptionMapper;
import io.github.carlos_emr.carlos.webserv.transfer_objects.AllergyTransfer;
import java.sql.Date;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsTemporalReadServiceUnitTest {
    @Test void prescriptionStartReadsStartMetadata_notTheIndependentWrittenDate() {
        var dao=mock(PartialDateDao.class);
        when(dao.getFormat(PartialDate.DRUGS,7,PartialDate.DRUGS_STARTDATE)).thenReturn(PartialDate.YEARONLY);
        when(dao.getFormat(PartialDate.DRUGS,7,PartialDate.DRUGS_WRITTENDATE)).thenReturn(PartialDate.YEARMONTH);
        var service=new CdsTemporalReadService(dao);
        assertEquals("2024",service.medicationStart(7,Date.valueOf("2024-01-01"),true).display());
        verify(dao).getFormat(PartialDate.DRUGS,7,PartialDate.DRUGS_STARTDATE);
        verify(dao,never()).getFormat(PartialDate.DRUGS,7,PartialDate.DRUGS_WRITTENDATE);
    }
    @Test void prescriptionUnknownAndActualJanuaryFirstRemainDifferent_withoutGuessing() {
        var service=new CdsTemporalReadService(mock(PartialDateDao.class));
        assertEquals("Unknown",service.medicationStart(7,null,true).display());
        assertEquals("2024-01-01",service.medicationStart(8,Date.valueOf("2024-01-01"),false).display());
        assertThrows(CdsTemporalPrecisionException.class,
                () -> service.medicationStart(9,Date.valueOf("2024-01-01"),true));
    }
    @Test void exactJanuaryFirstIsNotGuessedPartial() {
        var value = CdsTemporalNativeDateDto.from(Date.valueOf("2020-01-01"), null);
        assertEquals("FULL_DATE", value.getPrecision());
        assertEquals("2020-01-01", value.getValue());
        assertDoesNotThrow(value::requireDateOnlySafe);
    }
    @Test void explicitMonthRetainsLeapIntervalAndRefusesDateOnly() {
        var value = CdsTemporalNativeDateDto.from(Date.valueOf("2020-02-01"), PartialDate.YEARMONTH);
        assertEquals("MONTH", value.getPrecision());
        assertEquals("2020-02", value.getValue());
        assertEquals("2020-02-29", value.getIntervalEnd());
        assertThrows(CdsTemporalPrecisionException.class, value::requireDateOnlySafe);
    }
    @Test void unknownDateNeverBecomesTodayOrAFirstDay() {
        var value = CdsTemporalNativeDateDto.from(null, null);
        assertEquals("UNKNOWN", value.getPrecision());
        assertNull(value.getValue()); assertNull(value.getIntervalStart()); assertNull(value.getIntervalEnd());
        assertEquals("Unknown", value.display());
    }
    @Test void unrecognizedNativeTagRequiresReviewInsteadOfAssumingExact() {
        assertThrows(CdsTemporalPrecisionException.class,
                () -> CdsTemporalNativeDateDto.from(Date.valueOf("2020-01-01"), "UNKNOWN"));
    }
    @Test void medicationLabelsPreserveExistingExactFormatAndPartialMeaning() {
        PartialDateDao dao = mock(PartialDateDao.class);
        when(dao.getFormat(PartialDate.DRUGS, 7, PartialDate.DRUGS_STARTDATE)).thenReturn(PartialDate.YEARONLY);
        var exact = new Drug(); exact.setId(6); exact.setRxDate(Date.valueOf("2020-01-01"));
        var partial = new Drug(); partial.setId(7); partial.setRxDate(Date.valueOf("2020-01-01"));
        var unknown = new Drug(); unknown.setId(8); unknown.setRxDate(null);
        var labels = new CdsTemporalReadService(dao).medicationStartLabels(List.of(exact, partial, unknown));
        assertEquals("01/01/20", labels.get(6));
        assertEquals("2020", labels.get(7));
        assertEquals("Unknown", labels.get(8));
    }
    @Test void allergyPrecisionResponseRetainsEveryRecordAndRawSemanticsWithoutDateAnchors() throws Exception {
        PartialDateDao dao = mock(PartialDateDao.class);
        when(dao.getFormat(PartialDate.ALLERGIES, 7, PartialDate.ALLERGIES_STARTDATE)).thenReturn(PartialDate.YEARONLY);
        var partial = new Allergy(); partial.setId(7); partial.setDemographicNo(1); partial.setStartDate(Date.valueOf("2020-01-01"));
        partial.setReactionType("AR"); partial.setNonDrug(null); partial.setReaction(null);
        var unknown = new Allergy(); unknown.setId(8); unknown.setDemographicNo(1);
        LoggedInInfo info = new LoggedInInfo();
        AllergyManager manager = mock(AllergyManager.class);
        when(manager.getActiveAllergies(info, 1)).thenReturn(List.of(partial, unknown));
        AllergyService service = new AllergyService() {
            @Override protected LoggedInInfo getLoggedInInfo() { return info; }
        };
        ReflectionTestUtils.setField(service, "allergyManager", manager);
        ReflectionTestUtils.setField(service, "legacyProjection", mock(CdsLegacyProjectionService.class));
        ReflectionTestUtils.setField(service, "dates", new CdsTemporalReadService(dao));
        AllergyResponse result = service.getCurrentAllergiesWithPrecision(1);
        assertEquals(2, result.getAllergies().size());
        var first = result.getAllergies().getFirst();
        assertNull(first.getStartDate());
        assertEquals("YEAR", first.getStartDateInfo().getPrecision());
        assertEquals("2020", first.getStartDateInfo().getValue());
        assertEquals("AR", first.getReactionType());
        assertNull(first.getNonDrug()); assertNull(first.getReaction());
        assertEquals("UNKNOWN", result.getAllergies().get(1).getStartDateInfo().getPrecision());
        ObjectMapper json = new ObjectMapper();
        String wire = json.writeValueAsString(result);
        AllergyResponse restored = json.readValue(wire, AllergyResponse.class);
        assertEquals("2020", restored.getAllergies().getFirst().getStartDateInfo().getValue());
        verify(manager).getActiveAllergies(info, 1);
    }
    @Test void legacyAllergyRestAndSoapRefusePartialButKeepExistingExactDateShape() {
        PartialDateDao dao = mock(PartialDateDao.class);
        when(dao.getFormat(PartialDate.ALLERGIES, 7, PartialDate.ALLERGIES_STARTDATE)).thenReturn(PartialDate.YEARMONTH);
        CdsTemporalReadService dates = new CdsTemporalReadService(dao);
        Allergy source = new Allergy(); source.setId(7); source.setDemographicNo(1); source.setStartDate(Date.valueOf("2020-01-01"));
        assertThrows(CdsTemporalPrecisionException.class,
                () -> new AllergyConverter(dates, false).getAsTransferObject(null, source));
        assertEquals(409, new CdsTemporalPrecisionExceptionMapper()
                .toResponse(new CdsTemporalPrecisionException()).getStatus());
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(CdsTemporalReadService.class)).thenReturn(dates);
            assertThrows(CdsTemporalPrecisionException.class, () -> AllergyTransfer.toTransfer(source));
            when(dao.getFormat(PartialDate.ALLERGIES, 7, PartialDate.ALLERGIES_STARTDATE)).thenReturn(null);
            assertEquals(source.getStartDate(), AllergyTransfer.toTransfer(source).getStartDate());
        }
    }
}
