/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningLevelResolver;
import io.github.carlos_emr.carlos.prescript.util.RxInteractionWarningService;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * {@link RxInteractionWarnings2Action}: the JSON the Rx page fills its interaction markers from
 * (issue #4506), and who may read it.
 *
 * @since 2026-10-15
 */
@Tag("unit")
@Tag("rx")
@DisplayName("RxInteractionWarnings2Action")
class RxInteractionWarnings2ActionUnitTest {

    private static final int PATIENT = 1001;
    private static final String PROVIDER = "999998";
    private static final String CIPRO = "J01MA02";
    private static final String THEOPHYLLINE = "R03DA04";

    private final ObjectMapper mapper = new ObjectMapper();
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private UserPropertyDAO userPropertyDao;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private final List<List<String>> lookups = new ArrayList<>();
    private List<Map<?, ?>> drugRefAnswer;
    private Collection<String> currentMedications;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        userPropertyDao = mock(UserPropertyDAO.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("r"), isNull())).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("r"), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        request = new MockHttpServletRequest("GET", "/rx/interactionWarnings");
        response = new MockHttpServletResponse();
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

        Hashtable<String, Object> interaction = new Hashtable<>();
        interaction.put("affectingatc", CIPRO);
        interaction.put("affectingdrug", "CIPROFLOXACIN");
        interaction.put("effect", "A");
        interaction.put("affectedatc", THEOPHYLLINE);
        interaction.put("affecteddrug", "THEOPHYLLINE <b>");
        interaction.put("significance", "3");
        interaction.put("evidence", "G");
        interaction.put("comment", "Monitor levels.");
        drugRefAnswer = List.of(interaction);
        currentMedications = List.of(THEOPHYLLINE);
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
        loggedInInfoMock.close();
    }

    private RxInteractionWarnings2Action action() {
        RxInteractionWarningService service = new RxInteractionWarningService(codes -> {
            lookups.add(codes);
            return drugRefAnswer;
        });
        return new RxInteractionWarnings2Action(securityInfoManager,
                new RxInteractionWarningLevelResolver(userPropertyDao, mock(DemographicExtDao.class)),
                service, demographicNo -> {
                    assertThat(demographicNo).isEqualTo(PATIENT);
                    return currentMedications;
                });
    }

    /** Opens Rx for the patient with one staged ciprofloxacin card. */
    private RxSessionBean stageCipro() {
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(PATIENT);
        bean.setProviderNo(PROVIDER);
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, PROVIDER, PATIENT);
        rx.setRandomId(424242L);
        rx.setAtcCode(CIPRO);
        bean.getStashList().add(rx);
        RxSessionBeanResolver.register(request.getSession(), bean);
        return bean;
    }

    private JsonNode json() throws Exception {
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        return mapper.readTree(response.getContentAsByteArray());
    }

    @Test
    @DisplayName("should answer the staged card's major interaction with a current medication")
    void shouldReturnWarnings_forStagedCardAndCurrentMedication() throws Exception {
        stageCipro();
        request.setParameter("demographicNo", String.valueOf(PATIENT));

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        JsonNode json = json();
        assertThat(json.get("status").asText()).isEqualTo("checked");
        assertThat(json.get("level").asInt()).isZero();
        assertThat(json.get("warnings")).hasSize(1);
        JsonNode warning = json.get("warnings").get(0);
        assertThat(warning.get("randomId").asText()).isEqualTo("424242");
        assertThat(warning.get("severity").asText()).isEqualTo("major");
        // DrugRef text travels as data; the page writes it with textContent.
        assertThat(warning.get("partner").asText()).isEqualTo("THEOPHYLLINE <b>");
        assertThat(warning.get("summary").asText()).isEqualTo("CIPROFLOXACIN augments THEOPHYLLINE <b>");
        assertThat(warning.get("evidence").asText()).isEqualTo("good");
        assertThat(warning.get("comment").asText()).isEqualTo("Monitor levels.");
        assertThat(json.get("unchecked")).isEmpty();
        assertThat(lookups).containsExactly(List.of(CIPRO, THEOPHYLLINE));
    }

    @Test
    @DisplayName("should list a staged drug with no ATC code as unchecked and leave custom notes out")
    void shouldListUncheckedCard_whenAStagedDrugHasNoAtcCode() throws Exception {
        RxSessionBean bean = stageCipro();
        RxPrescriptionData.Prescription custom = new RxPrescriptionData.Prescription(0, PROVIDER, PATIENT);
        custom.setRandomId(515151L);
        custom.setCustomName("Compounded cream");
        bean.getStashList().add(custom);
        RxPrescriptionData.Prescription note = new RxPrescriptionData.Prescription(0, PROVIDER, PATIENT);
        note.setRandomId(626262L);
        note.setCustomNote(true);
        bean.getStashList().add(note);
        request.setParameter("demographicNo", String.valueOf(PATIENT));

        action().execute();

        JsonNode json = json();
        assertThat(json.get("status").asText()).isEqualTo("checked");
        assertThat(json.get("unchecked")).hasSize(1);
        assertThat(json.get("unchecked").get(0).asText()).isEqualTo("515151");
        assertThat(json.get("warnings").get(0).get("randomId").asText()).isEqualTo("424242");
    }

    @Test
    @DisplayName("should apply the provider's warning level")
    void shouldApplyProviderWarningLevel_whenItIsNone() throws Exception {
        stageCipro();
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        UserProperty none = new UserProperty();
        none.setValue("4");
        when(userPropertyDao.getProp(PROVIDER, RxInteractionWarningLevelResolver.PROPERTY_NAME)).thenReturn(none);

        action().execute();

        JsonNode json = json();
        assertThat(json.get("status").asText()).isEqualTo("off");
        assertThat(json.get("level").asInt()).isEqualTo(4);
        assertThat(json.get("warnings")).isEmpty();
        assertThat(lookups).isEmpty();
    }

    @Test
    @DisplayName("should answer unavailable when DrugRef cannot be asked")
    void shouldReturnUnavailable_whenDrugRefFails() throws Exception {
        stageCipro();
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        RxInteractionWarnings2Action failing = new RxInteractionWarnings2Action(securityInfoManager,
                new RxInteractionWarningLevelResolver(userPropertyDao, mock(DemographicExtDao.class)),
                new RxInteractionWarningService(codes -> {
                    throw new java.net.SocketTimeoutException("timed out");
                }), demographicNo -> currentMedications);

        failing.execute();

        assertThat(json().get("status").asText()).isEqualTo("unavailable");
    }

    @Test
    @DisplayName("should answer unavailable when the current medications cannot be read")
    void shouldReturnUnavailable_whenCurrentMedicationsCannotBeRead() throws Exception {
        stageCipro();
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        RxInteractionWarnings2Action failing = new RxInteractionWarnings2Action(securityInfoManager,
                new RxInteractionWarningLevelResolver(userPropertyDao, mock(DemographicExtDao.class)),
                new RxInteractionWarningService(codes -> drugRefAnswer), demographicNo -> {
                    throw new IllegalStateException("database down");
                });

        failing.execute();

        assertThat(json().get("status").asText()).isEqualTo("unavailable");
    }

    @Test
    @DisplayName("should answer an empty check without reading medications when nothing is staged")
    void shouldReturnNoWarnings_whenNothingIsStaged() throws Exception {
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(PATIENT);
        RxSessionBeanResolver.register(request.getSession(), bean);
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        currentMedications = null; // reading them would fail the check

        action().execute();

        JsonNode json = json();
        assertThat(json.get("status").asText()).isEqualTo("checked");
        assertThat(json.get("warnings")).isEmpty();
        assertThat(lookups).isEmpty();
    }

    @Test
    @DisplayName("should refuse a caller without global Rx read before any lookup")
    void shouldThrowSecurityException_whenRxReadIsMissing() {
        stageCipro();
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("r"), isNull())).thenReturn(false);

        RxInteractionWarnings2Action action = action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        assertThat(lookups).isEmpty();
    }

    @Test
    @DisplayName("should refuse a caller whose Rx read is withheld for the named patient")
    void shouldThrowSecurityException_whenPatientLevelRxReadIsMissing() {
        stageCipro();
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_rx"), eq("r"), eq(PATIENT))).thenReturn(false);

        RxInteractionWarnings2Action action = action();
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        assertThat(lookups).isEmpty();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @Test
    @DisplayName("should refuse a caller who may not read the named patient")
    void shouldThrowSecurityException_whenPatientIsNotAccessible() {
        stageCipro();
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(false);

        RxInteractionWarnings2Action action = action();
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        assertThat(lookups).isEmpty();
        assertThat(response.getContentAsByteArray()).isEmpty();
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"", "0", "-3", "abc"})
    @DisplayName("should answer 400 unless the request names one valid patient")
    void shouldRejectRequest_whenPatientIsNotNamed(String demographicNo) throws Exception {
        stageCipro(); // the active patient is never used as a fallback
        if (!demographicNo.isEmpty()) {
            request.setParameter("demographicNo", demographicNo);
        }

        assertThat(action().execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(lookups).isEmpty();
    }

    @Test
    @DisplayName("should answer 400 when demographicNo and demographic_no disagree")
    void shouldRejectRequest_whenPatientParametersConflict() throws Exception {
        stageCipro();
        request.setParameter("demographicNo", String.valueOf(PATIENT));
        request.setParameter("demographic_no", "2002");

        action().execute();

        assertThat(response.getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("should answer 404 when Rx is not open for the named patient")
    void shouldReturnNotFound_whenRxIsNotOpenForPatient() throws Exception {
        stageCipro();
        request.setParameter("demographicNo", "2002");

        action().execute();

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(lookups).isEmpty();
    }
}
