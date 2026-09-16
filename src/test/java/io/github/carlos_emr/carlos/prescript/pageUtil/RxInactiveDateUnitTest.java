/* Copyright (c) 2026 CARLOS Contributors. Licensed under GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.util.RxDrugRef;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import java.util.Vector;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class RxInactiveDateUnitTest extends CarlosWebTestBase {
    private RxDrugRef drugref;

    private RxSearchDrug2Action action() {
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull())).thenReturn(true);
        mockRequest.setParameter("method", "inactiveDate");
        mockRequest.setParameter("din", "02245547");
        mockRequest.setParameter("id", "rx-row");
        RxSearchDrug2Action action = new RxSearchDrug2Action();
        drugref = mock(RxDrugRef.class);
        ReflectionTestUtils.setField(action, "drugref", drugref);
        return action;
    }

    private JsonNode result() throws Exception {
        return new ObjectMapper().readTree(mockResponse.getContentAsString());
    }

    @Test void inactiveProductReturnsExplicitCalendarDate() throws Exception {
        RxSearchDrug2Action action = action();
        Vector<Object> dates = new Vector<>();
        dates.add(java.sql.Date.valueOf("2018-07-24"));
        when(drugref.getInactiveDate("02245547")).thenReturn(dates);
        assertThat(action.execute()).isNull();
        assertThat(mockResponse.getStatus()).isEqualTo(200);
        assertThat(result().get("checked").asBoolean()).isTrue();
        assertThat(result().get("inactiveDate").asText()).isEqualTo("2018-07-24");
        assertThat(dates).hasSize(1);
    }

    @Test void successfulEmptyLookupIsDistinctFromFailure() throws Exception {
        RxSearchDrug2Action action = action();
        when(drugref.getInactiveDate("02245547")).thenReturn(new Vector<>());
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(200);
        assertThat(result().get("checked").asBoolean()).isTrue();
        assertThat(result().get("inactiveDate").isNull()).isTrue();
    }

    @Test void remoteFailureReturns503WithoutDetailsOrAllClear() throws Exception {
        RxSearchDrug2Action action = action();
        when(drugref.getInactiveDate("02245547")).thenThrow(new Exception("sensitive remote detail"));
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(503);
        assertThat(result().get("checked").asBoolean()).isFalse();
        assertThat(mockResponse.getContentAsString()).doesNotContain("sensitive remote detail", "inactiveDate");
    }

    @Test void malformedRemoteResultIsNotTreatedAsActive() throws Exception {
        RxSearchDrug2Action action = action();
        Vector<Object> dates = new Vector<>();
        dates.add("unexpected value");
        when(drugref.getInactiveDate("02245547")).thenReturn(dates);
        action.execute();
        assertThat(mockResponse.getStatus()).isEqualTo(503);
        assertThat(result().get("checked").asBoolean()).isFalse();
    }

    @Test void deniedReadNeverCallsDrugref() {
        RxSearchDrug2Action action = action();
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull())).thenReturn(false);
        assertThatThrownBy(action::execute).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(drugref);
    }
}
