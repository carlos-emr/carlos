/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLegacyProjectionService;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.util.WLWaitingListUtil;
import io.github.carlos_emr.carlos.webserv.rest.conversion.DemographicConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicTo1;
import jakarta.ws.rs.WebApplicationException;
import java.util.function.BiConsumer;
import java.util.function.Function;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real REST conversion and refusal before patient/custom-field/waiting-list writes. */
@Tag("unit")
class DemographicConvertedFieldLimitsUnitTest extends CarlosUnitTestBase {
    private final DemographicDao records = mock(DemographicDao.class);

    enum Field {
        TITLE("Title", 10, DemographicTo1::setTitle, Demographic::getTitle),
        OFFICIAL_LANGUAGE("Official language", 60, DemographicTo1::setOfficialLanguage, Demographic::getOfficialLanguage),
        SPOKEN_LANGUAGE("Spoken language", 60, DemographicTo1::setSpokenLanguage, Demographic::getSpokenLanguage),
        COUNTRY("Country of origin", 4, DemographicTo1::setCountryOfOrigin, Demographic::getCountryOfOrigin),
        NEWSLETTER("Newsletter", 32, DemographicTo1::setNewsletter, Demographic::getNewsletter),
        ANONYMOUS("Anonymous", 32, DemographicTo1::setAnonymous, Demographic::getAnonymous);
        final String label;
        final int limit;
        final BiConsumer<DemographicTo1, String> set;
        final Function<Demographic, String> get;
        Field(String label, int limit, BiConsumer<DemographicTo1, String> set, Function<Demographic, String> get) {
            this.label = label; this.limit = limit; this.set = set; this.get = get;
        }
    }

    @ParameterizedTest
    @EnumSource(Field.class)
    void shouldRejectConvertedFieldOverflow_withoutTruncatingTheInput(Field field) {
        var input = patient();
        String value = "X".repeat(field.limit + 1);
        field.set.accept(input, value);
        var converted = new DemographicConverter().getAsDomainObject(null, input);

        assertThat(converted.validateFieldLengths()).contains(field.label + " exceeds maximum length of "
                + field.limit + " characters.");
        assertThat(field.get.apply(converted)).isEqualTo(value);
    }

    @ParameterizedTest
    @EnumSource(Field.class)
    void shouldPreserveNullEmptyAndUnicodeBoundaryValues_afterRestConversion(Field field) {
        for (String value : new String[]{null, "", "Ω".repeat(field.limit), "😀".repeat(field.limit)}) {
            var input = patient();
            field.set.accept(input, value);
            var converted = new DemographicConverter().getAsDomainObject(null, input);
            assertThat(converted.validateFieldLengths()).isEmpty();
            assertThat(field.get.apply(converted)).isEqualTo(value);
        }
    }

    @ParameterizedTest
    @EnumSource(Field.class)
    void shouldRefuseCreateBeforeAnyPatientWrite_whenAConvertedFieldOverflows(Field field) {
        refuse(field, false);
    }

    @ParameterizedTest
    @EnumSource(Field.class)
    void shouldRefuseUpdateBeforePatientCustomFieldOrWaitingListWrites_whenAConvertedFieldOverflows(Field field) {
        refuse(field, true);
    }

    private void refuse(Field field, boolean update) {
        var manager = mock(DemographicManager.class);
        var service = service(manager);
        var input = patient();
        input.setAlert("FAKE unchanged side effect");
        input.setWaitingListID(123);
        input.setWaitingListNote("FAKE unchanged waiting list");
        String value = "X".repeat(field.limit + 1);
        field.set.accept(input, value);
        try (var waitingList = mockStatic(WLWaitingListUtil.class)) {
            assertThatThrownBy(() -> {
                if (update) service.updateDemographicData(input);
                else service.createDemographicData(input);
            }).isInstanceOf(WebApplicationException.class).satisfies(error -> {
                var response = ((WebApplicationException) error).getResponse();
                assertThat(response.getStatus()).isEqualTo(400);
                assertThat(response.getEntity().toString()).contains(field.label).doesNotContain(value);
            });
            verifyNoInteractions(manager, records);
            waitingList.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldContinueToAcceptAllConvertedFieldsAtTheirColumnBoundaries(boolean update) {
        var manager = mock(DemographicManager.class);
        var service = service(manager);
        var input = patient();
        for (Field field : Field.values()) field.set.accept(input, "Ω".repeat(field.limit));
        if (update) {
            service.updateDemographicData(input);
            verify(manager).updateDemographic(any(), any());
        } else {
            service.createDemographicData(input);
            verify(manager).createDemographic(any(), any(), any());
        }
    }

    private DemographicService service(DemographicManager manager) {
        var info = new LoggedInInfo();
        var security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(info, "_demographic", "w", (String) null)).thenReturn(true);
        when(security.hasPrivilege(info, "_demographic", "u", 4255)).thenReturn(true);
        var service = new DemographicService(mock(CdsLegacyProjectionService.class)) {
            @Override protected LoggedInInfo getLoggedInInfo() { return info; }
        };
        injectDependency(service, "securityInfoManager", security);
        injectDependency(service, "demographicManager", manager);
        injectDependency(service, "updateRecords", records);
        when(records.findForUpdate(4255)).thenReturn(new Demographic());
        when(records.findFreshForUpdate(4255)).thenReturn(new Demographic());
        return service;
    }

    private static DemographicTo1 patient() {
        var input = new DemographicTo1();
        input.setDemographicNo(4255);
        input.setFirstName("FAKE"); input.setLastName("REST-LIMITS");
        input.setDobYear("2000"); input.setDobMonth("01"); input.setDobDay("01");
        return input;
    }
}
