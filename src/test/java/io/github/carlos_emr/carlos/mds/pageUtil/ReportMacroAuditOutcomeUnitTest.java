/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.mds.pageUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.commn.model.TicklerLink;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Pins committed macro outcomes and tickler links when the separate audit writer fails. */
@Tag("unit")
@DisplayName("Lab macro post-commit audit outcomes")
class ReportMacroAuditOutcomeUnitTest extends CarlosUnitTestBase {
    @org.junit.jupiter.api.Test
    @DisplayName("should reject a malformed macro report identifier without logging its value")
    void shouldKeepMacroDiagnosticsPrivate_whenIdentifierIsInvalid() {
        var request = new MockHttpServletRequest("POST", "/oscarMDS/RunMacro");
        request.setParameter("segmentID", "PRIVATE_SEGMENT_VALUE");
        request.setParameter("labType", "HL7");
        createAndRegisterMock(SecurityInfoManager.class);
        var ticklers = createAndRegisterMock(TicklerDao.class);
        var links = createAndRegisterMock(TicklerLinkDao.class);
        var info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        var macro = new ObjectMapper().createObjectNode().put("name", "fixture");
        macro.putObject("acknowledge").put("comment", "PRIVATE_CLINICAL_COMMENT");
        try (var servlet = mockStatic(ServletActionContext.class);
             var session = mockStatic(LoggedInInfo.class);
             var logs = LogCapture.forLogger(ReportMacro2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            var outcome = new ReportMacro2Action().runMacroOutcome(macro, request);
            assertThat(outcome.success()).isFalse();
            assertThat(outcome.acknowledged()).isFalse();
            org.mockito.Mockito.verifyNoInteractions(ticklers, links);
            assertThat(logs.messages()).anyMatch(message -> message.contains("NumberFormatException"));
            assertThat(logs.messages().toString()).doesNotContain("PRIVATE_");
            assertThat(logs.events()).allMatch(event -> event.getThrown() == null);
        }
    }

    @ParameterizedTest
    @CsvSource({"true,false,present", "false,true,present", "true,true,present",
            "true,false,missing", "true,true,missing", "true,false,nullComment", "true,true,nullComment",
            "true,false,nullAcknowledgement", "true,true,nullAcknowledgement",
            "false,true,invalidQuantity", "false,true,invalidUnits"})
    @DisplayName("should preserve completed macro effects and JSON counters when audit logging fails")
    void shouldReturnCommittedOutcome_whenAuditFails(boolean acknowledge, boolean tickler, String commentShape) throws Exception {
        var request = new MockHttpServletRequest("POST", "/oscarMDS/RunMacro");
        var response = new MockHttpServletResponse();
        request.setParameter("name", "fixture");
        request.setParameter("segmentID", "123");
        request.setParameter("labType", "HL7");
        request.setParameter("demographicNo", "1");
        var security = createAndRegisterMock(SecurityInfoManager.class);
        createAndRegisterMock(PatientLabRoutingDao.class);
        createAndRegisterMock(ProviderLabRoutingDao.class);
        createAndRegisterMock(QueueDocumentLinkDao.class);
        var ticklers = createAndRegisterMock(TicklerDao.class);
        var links = createAndRegisterMock(TicklerLinkDao.class);
        var preferences = createAndRegisterMock(UserPropertyDAO.class);
        var info = mock(LoggedInInfo.class);
        when(info.getLoggedInProviderNo()).thenReturn("999998");
        when(info.getIp()).thenReturn("127.0.0.1");
        when(security.hasPrivilege(info, "_lab", "w", null)).thenReturn(true);
        var mapper = new ObjectMapper();
        var macro = mapper.createObjectNode().put("name", "fixture");
        String expectedComment = "present".equals(commentShape) ? "fixture comment" : "";
        if (acknowledge) {
            var ack = macro.putObject("acknowledge");
            switch (commentShape) {
                case "present" -> ack.put("comment", expectedComment);
                case "missing" -> { /* Optional comment omitted by saved preferences. */ }
                case "nullComment" -> ack.putNull("comment");
                case "nullAcknowledgement" -> macro.putNull("acknowledge");
                default -> throw new IllegalArgumentException("Unknown fixture comment shape");
            }
        }
        if (tickler) {
            var ticklerMacro = macro.putObject("tickler").put("taskAssignedTo", "999998").put("message", "fixture tickler");
            if (commentShape.startsWith("invalid")) {
                ticklerMacro.put("quantity", "invalidQuantity".equals(commentShape) ? "PRIVATE_QUANTITY" : "1");
                ticklerMacro.put("timeUnits", "invalidUnits".equals(commentShape) ? "PRIVATE_UNITS" : "1");
            }
        }
        var property = new UserProperty();
        property.setValue(mapper.createArrayNode().add(macro).toString());
        when(preferences.getProp("999998", UserProperty.LAB_MACRO_JSON)).thenReturn(property);
        if (tickler) doAnswer(invocation -> {
            Tickler saved = invocation.getArgument(0);
            saved.setId(77);
            return null;
        }).when(ticklers).persist(any(Tickler.class));
        try (var servlet = mockStatic(ServletActionContext.class);
             var session = mockStatic(LoggedInInfo.class);
             var routing = mockStatic(CommonLabResultData.class);
             var logs = LogCapture.forLogger(ReportMacro2Action.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            session.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            routing.when(() -> CommonLabResultData.acknowledgeReport(123, "999998", expectedComment, "HL7", false, null))
                    .thenReturn(3);
            logActionMock.when(() -> LogAction.addLogSynchronous(anyString(), anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new IllegalStateException("PRIVATE_AUDIT_MESSAGE", new IllegalArgumentException("PRIVATE_AUDIT_CAUSE")));
            assertThat(new ReportMacro2Action().execute()).isEqualTo(ReportMacro2Action.NONE);
            var json = mapper.readTree(response.getContentAsString());
            assertThat(json.path("success").asBoolean()).isTrue();
            assertThat(json.path("acknowledged").asBoolean()).isEqualTo(acknowledge);
            assertThat(json.path("clearedCount").asInt()).isEqualTo(acknowledge ? 3 : 0);
            if (tickler) {
                verify(ticklers).persist(any(Tickler.class));
                verify(links).persist(any(TicklerLink.class));
            } else {
                verify(links, never()).persist(any());
            }
            assertThat(logs.messages()).anyMatch(message -> message.contains("audit logging failed"));
            if (commentShape.startsWith("invalid")) assertThat(logs.messages()).anyMatch(message -> message.contains("NumberFormatException"));
            assertThat(logs.messages().toString()).doesNotContain("PRIVATE_");
            assertThat(logs.events()).isNotEmpty().allMatch(event -> event.getThrown() == null);
        }
    }
}
