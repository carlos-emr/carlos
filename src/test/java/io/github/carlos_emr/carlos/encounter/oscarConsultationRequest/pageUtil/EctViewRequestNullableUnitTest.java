// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao;
import io.github.carlos_emr.carlos.commn.dao.ConsultationRequestExtDao;
import io.github.carlos_emr.carlos.commn.model.ConsultationRequest;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.ConsultationManager;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Isolated("Replaces the static EctViewRequest2Action security manager")
@Tag("unit")
@Tag("fast")
class EctViewRequestNullableUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRenderAbsentFieldsAsBlank_withoutChangingPopulatedValues(boolean absent) {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        var info = mock(LoggedInInfo.class);
        when(security.hasPrivilege(info, "_con", "r", (String) null)).thenReturn(true);
        var manager = createAndRegisterMock(ConsultationManager.class);
        createAndRegisterMock(ConsultationRequestExtDao.class);
        when(manager.getExtValuesAsMap(any())).thenReturn(Map.of());
        var requests = createAndRegisterMock(ConsultationRequestDao.class);
        var demographics = createAndRegisterMock(DemographicManager.class);
        createAndRegisterMock(ProviderDao.class);
        var patient = mock(Demographic.class);
        when(demographics.getDemographic(info, 42)).thenReturn(patient);
        var consult = new ConsultationRequest();
        consult.setDemographicId(42);
        consult.setReferralDate(absent ? null : java.sql.Date.valueOf("2026-09-27"));
        consult.setServiceId(absent ? null : 7);
        consult.setReasonForReferral("Retain clinical content");
        when(requests.find(Integer.valueOf(12))).thenReturn(consult);
        var previous = org.springframework.test.util.ReflectionTestUtils.getField(EctViewRequest2Action.class, "securityInfoManager");
        org.springframework.test.util.ReflectionTestUtils.setField(EctViewRequest2Action.class, "securityInfoManager", security);
        try {
            var form = new EctConsultationFormRequest2Form();
            EctViewRequest2Action.fillFormValues(info, form, 12);
            assertThat(form.getReferalDate()).isEqualTo(absent ? "" : "2026-09-27");
            assertThat(form.getService()).isEqualTo(absent ? "" : "7");
            assertThat(form.getReasonForConsultation()).isEqualTo("Retain clinical content");
            assertThat(consult.getReferralDate()).isEqualTo(absent ? null : java.sql.Date.valueOf("2026-09-27"));
        } finally {
            org.springframework.test.util.ReflectionTestUtils.setField(EctViewRequest2Action.class, "securityInfoManager", previous);
        }
    }
}
