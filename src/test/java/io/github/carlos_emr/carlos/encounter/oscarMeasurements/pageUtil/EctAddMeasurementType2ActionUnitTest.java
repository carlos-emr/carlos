/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import java.util.List;
import java.util.Locale;

import io.github.carlos_emr.carlos.commn.dao.MeasurementTypeDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Isolated
class EctAddMeasurementType2ActionUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUseLocaleIndependentCode_forLookupAndStorage(boolean duplicate) throws Exception {
        var dao = mock(MeasurementTypeDao.class);
        var security = mock(SecurityInfoManager.class);
        registerMock(MeasurementTypeDao.class, dao);
        registerMock(SecurityInfoManager.class, security);
        var request = new MockHttpServletRequest("POST", "/AddMeasurementType");
        var login = mock(LoggedInInfo.class);
        when(security.hasPrivilege(login, "_admin", "w", null)).thenReturn(true);
        when(dao.findByType("ID")).thenReturn(duplicate ? List.of(new MeasurementType()) : List.of());
        Locale previous = Locale.getDefault();
        try (var servlet = mockStatic(ServletActionContext.class);
             var sessions = mockStatic(LoggedInInfo.class);
             var types = mockStatic(MeasurementTypes.class)) {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
            MeasurementTypes measurementTypes = mock(MeasurementTypes.class);
            types.when(MeasurementTypes::getInstance).thenReturn(measurementTypes);
            var action = spy(new EctAddMeasurementType2Action());
            action.setType("id");
            action.setTypeDesc("Test");
            action.setTypeDisplayName("Test");
            action.setMeasuringInstrc("Test");
            if (duplicate) {
                doReturn("Duplicate").when(action).getText("error.encounter.Measurements.duplicateTypeName");
                assertThat(action.execute()).isEqualTo("failure");
                verify(dao, never()).persist(any());
            } else {
                doReturn("Created").when(action).getText("encounter.oscarMeasurements.AddMeasurementType.successful", "!");
                assertThat(action.execute()).isEqualTo(EctAddMeasurementType2Action.SUCCESS);
                verify(dao).persist(argThat(row -> row instanceof MeasurementType type && "ID".equals(type.getType())));
            }
            verify(dao).findByType("ID");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
