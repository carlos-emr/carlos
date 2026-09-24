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
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Every Rx write authorises the specific patient it changes (#3908).
 *
 * <p>Each write path is driven as the real UI drives it: a POST naming the open patient, with the
 * global privilege granted. Only the patient-level check is denied, either the patient-level
 * privilege ({@code _rx$demographicNo} / {@code _allergy$demographicNo}), only its write level
 * (with patient-level read still held), or access to the patient's record. Each path must refuse with a {@link SecurityException} before any side
 * effect: the staged prescriptions and ReRx list are unchanged, no Spring-managed dependency
 * (DAO, manager, stamp service) is touched, and nothing is audited.</p>
 *
 * @since 2026-09-24
 */
@DisplayName("Rx writes: patient-level authorisation")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxPatientWriteAuthorizationUnitTest {

    private static final int DEMOGRAPHIC_NO = 1001;
    private static final String PROVIDER_NO = "999998";

    private MockedStatic<SpringUtils> springUtilsMock;
    private MockedStatic<LogAction> logActionMock;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private final Map<Class<?>, Object> dependencies = new HashMap<>();

    private SecurityInfoManager securityInfoManager;
    private LoggedInInfo loggedInInfo;
    private MockHttpServletRequest request;
    private RxSessionBean bean;

    @BeforeEach
    void setUp() {
        securityInfoManager = mock(SecurityInfoManager.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        // Global privileges (null target) are granted: only the patient-level check is under test.
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        // Every other bean is an auto-mock the refusal must never touch.
        springUtilsMock = mockStatic(SpringUtils.class);
        springUtilsMock.when(() -> SpringUtils.getBean(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            return type.equals(SecurityInfoManager.class)
                    ? securityInfoManager
                    : dependencies.computeIfAbsent(type, Mockito::mock);
        });
        logActionMock = mockStatic(LogAction.class);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setParameter("demographicNo", String.valueOf(DEMOGRAPHIC_NO));
        MockHttpServletResponse response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        // The patient's Rx is open with two unsaved staged prescriptions and one ReRx source.
        bean = new RxSessionBean();
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        bean.setProviderNo(PROVIDER_NO);
        bean.getStashList().add(new RxPrescriptionData.Prescription(0, PROVIDER_NO, DEMOGRAPHIC_NO));
        bean.getStashList().add(new RxPrescriptionData.Prescription(0, PROVIDER_NO, DEMOGRAPHIC_NO));
        bean.setStashIndex(0);
        bean.addReRxDrugIdList("55");
        RxSessionBeanResolver.register(request.getSession(), bean);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
        logActionMock.close();
        springUtilsMock.close();
    }

    static Stream<Arguments> writePaths() {
        List<String> paths = List.of(
                "clearPending",
                "deleteRx.Delete2", "deleteRx.clearStash", "deleteRx.clearReRxDrugList", "deleteRx.Discontinue",
                "stash.deletePrescribe",
                "addFavorite.execute", "addFavorite.addFav2",
                "addFavorite.savedDrug", "addFavorite.savedDrugAjax",
                "useFavorite.execute", "useFavorite.useFav2",
                "chooseDrug",
                "writeScript.updateAndPrint", "writeScript.updateSaveAllDrugs", "writeScript.updateLongTermStatus",
                "writeScript.updateReRxDrug", "writeScript.saveCustomName", "writeScript.newCustomNote",
                "writeScript.newCustomDrug", "writeScript.normalDrugSetCustom", "writeScript.createNewRx",
                "writeScript.updateDrug", "writeScript.updateSpecialInstruction", "writeScript.updateProperty",
                "rePrescribe.represcribe", "rePrescribe.represcribe2", "rePrescribe.represcribeMultiple",
                "rePrescribe.saveReRxDrugIdToStash", "rePrescribe.repcbAllLongTerm",
                "rePrescribe.saveDigitalSignature",
                "viewScript.save",
                "addAllergy", "deleteAllergy", "showAllergy.reorder",
                "reason.addDrugReason", "reason.archiveReason");
        return paths.stream().flatMap(path -> Stream.of(
                Arguments.of(path, "patient privilege"),
                Arguments.of(path, "patient write only"),
                Arguments.of(path, "record access")));
    }

    /** The patient-level privilege each write path needs: delete paths need update, the rest write. */
    private static String requiredPrivilege(String path) {
        return path.startsWith("deleteRx.") || "deleteAllergy".equals(path) || "showAllergy.reorder".equals(path)
                ? "u" : "w";
    }

    static Stream<String> writePathNames() {
        return writePaths().map(arguments -> (String) arguments.get()[0]).distinct();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("writePathNames")
    @DisplayName("should get past the patient check when the patient is authorised (control)")
    void shouldPassPatientCheck_whenPatientAuthorised(String path) {
        // Proves the refusals above come from the patient-level check and not from the fixture:
        // with it granted, the path continues (into auto-mocked dependencies that may then fail
        // in other ways) instead of being refused as unauthorised.
        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(call(path));

        assertThat(thrown instanceof SecurityException)
                .as("%s refused an authorised patient: %s", path, thrown)
                .isFalse();
    }

    @ParameterizedTest(name = "{0} ({1} denied)")
    @MethodSource("writePaths")
    @DisplayName("should refuse the write before any side effect when the patient is not authorised")
    void shouldRefuseWrite_whenPatientLevelAccessDenied(String path, String denied) {
        if ("patient privilege".equals(denied)) {
            when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(false);
        } else if ("patient write only".equals(denied)) {
            // Patient-level read is held; only the write level this path needs is missing. Staging
            // and drug reasons are writes too: a read-only caller must not reach them (#3908).
            when(securityInfoManager.hasPrivilege(any(), anyString(), org.mockito.ArgumentMatchers.eq(requiredPrivilege(path)),
                    anyInt())).thenReturn(false);
        } else {
            when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(false);
        }
        String securityObject = path.contains("llergy") ? "_allergy" : "_rx";
        boolean savedDrugFavourite = path.startsWith("addFavorite.savedDrug");
        if (savedDrugFavourite) {
            // The favourite path loads the drug to learn its patient before authorising that patient.
            io.github.carlos_emr.carlos.commn.model.Drug drug = new io.github.carlos_emr.carlos.commn.model.Drug();
            drug.setDemographicId(DEMOGRAPHIC_NO);
            when(dependency(io.github.carlos_emr.carlos.commn.dao.DrugDao.class).find(5)).thenReturn(drug);
        }

        assertThatThrownBy(call(path))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (" + securityObject + ")");

        assertThat(bean.getStashSize()).isEqualTo(2);
        assertThat(bean.getReRxDrugIdList()).containsExactly("55");
        for (Map.Entry<Class<?>, Object> dependency : dependencies.entrySet()) {
            if (savedDrugFavourite && dependency.getKey().equals(io.github.carlos_emr.carlos.commn.dao.DrugDao.class)) {
                continue;
            }
            if ("reason.archiveReason".equals(path)
                    && dependency.getKey().equals(io.github.carlos_emr.carlos.commn.dao.DrugReasonDao.class)) {
                io.github.carlos_emr.carlos.commn.dao.DrugReasonDao reasons =
                        (io.github.carlos_emr.carlos.commn.dao.DrugReasonDao) dependency.getValue();
                Mockito.verify(reasons).find(3);
                Mockito.verifyNoMoreInteractions(reasons);
                continue;
            }
            verifyNoInteractions(dependency.getValue());
        }
        logActionMock.verifyNoInteractions();
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} method={1}")
    @org.junit.jupiter.params.provider.CsvSource({"GET,addDrugReason", "GET,archiveReason",
            "HEAD,addDrugReason", "HEAD,archiveReason"})
    @DisplayName("should refuse a drug-reason write that is not a POST before touching anything")
    void shouldRejectReasonWrite_whenMethodIsNotPost(String httpMethod, String method) throws Exception {
        // CSRFGuard does not check GET, so a link or image tag must not file or archive a reason.
        request.setMethod(httpMethod);
        request.setParameter("method", method);
        request.setParameter("drugId", "5");
        request.setParameter("reasonId", "3");
        request.setParameter("jsonDxSearch", "250");
        MockHttpServletResponse response = (MockHttpServletResponse) ServletActionContext.getResponse();

        String result = new RxReason2Action().execute();

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(securityInfoManager);
        for (Object dependency : dependencies.values()) {
            verifyNoInteractions(dependency);
        }
        logActionMock.verifyNoInteractions();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should open the drug-reason popup by GET with patient-level read only")
    void shouldRenderReasonPopup_whenGetNamesReadablePatient() throws Exception {
        // SearchDrug3 opens the popup with a GET; viewing needs _rx read, not write.
        request.setMethod("GET");
        request.setParameter("drugId", "5");
        io.github.carlos_emr.carlos.commn.model.Drug drug = new io.github.carlos_emr.carlos.commn.model.Drug();
        drug.setDemographicId(DEMOGRAPHIC_NO);
        when(dependency(io.github.carlos_emr.carlos.commn.dao.DrugDao.class).find(5)).thenReturn(drug);
        when(securityInfoManager.hasPrivilege(any(), anyString(), org.mockito.ArgumentMatchers.eq("w"), anyInt()))
                .thenReturn(false);

        String result = new RxReason2Action().execute();

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.SUCCESS);
        assertThat(request.getAttribute("drugId")).isEqualTo(5);
        assertThat(request.getAttribute("demoNo")).isEqualTo(DEMOGRAPHIC_NO);
        logActionMock.verifyNoInteractions();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should refuse the drug-reason popup for a patient the caller may not read")
    void shouldRefuseReasonPopup_whenPatientReadDenied() {
        request.setMethod("GET");
        request.setParameter("drugId", "5");
        when(securityInfoManager.hasPrivilege(any(), anyString(), org.mockito.ArgumentMatchers.eq("r"), anyInt()))
                .thenReturn(false);

        assertThatThrownBy(() -> new RxReason2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        for (Object dependency : dependencies.values()) {
            verifyNoInteractions(dependency);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest(name = "{0} denied")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"patient read", "record access"})
    @DisplayName("should refuse to open Rx for a patient the caller may not read, without opening it")
    void shouldRefuseChoosePatient_whenPatientReadDenied(String denied) {
        // choosePatient renders the patient's medications; _demographic read alone must not open
        // another patient's Rx (#3908).
        int otherPatient = 2002;
        request.setMethod("GET");
        request.setParameter("demographicNo", String.valueOf(otherPatient));
        request.getSession().setAttribute("user", PROVIDER_NO);
        if ("patient read".equals(denied)) {
            when(securityInfoManager.hasPrivilege(any(), anyString(), org.mockito.ArgumentMatchers.eq("r"),
                    org.mockito.ArgumentMatchers.eq(otherPatient))).thenReturn(false);
        } else {
            when(securityInfoManager.isAllowedAccessToPatientRecord(any(), org.mockito.ArgumentMatchers.eq(otherPatient)))
                    .thenReturn(false);
        }

        assertThatThrownBy(() -> new RxChoosePatient2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");

        assertThat(RxSessionBeanResolver.find(request.getSession(), otherPatient)).isNull();
        assertThat(RxSessionBeanResolver.resolve(new MockHttpServletRequest() {{
            setSession(request.getSession());
        }})).isSameAs(bean);
        for (Object dependency : dependencies.values()) {
            verifyNoInteractions(dependency);
        }
        logActionMock.verifyNoInteractions();
    }

    static Stream<Arguments> postOnlyWrites() {
        List<String> writes = List.of(
                "rePrescribe.represcribe", "rePrescribe.represcribe2", "rePrescribe.saveReRxDrugIdToStash",
                "rePrescribe.repcbAllLongTerm", "rePrescribe.represcribeMultiple",
                "deleteRx.execute", "deleteRx.Delete2", "deleteRx.Discontinue", "deleteRx.clearStash",
                "deleteRx.clearReRxDrugList",
                "pharmacy.delete", "pharmacy.unlink", "pharmacy.setPreferred", "pharmacy.add", "pharmacy.save",
                "pharmacy.pharmacyAction");
        return writes.stream().flatMap(write -> Stream.of(Arguments.of("GET", write), Arguments.of("HEAD", write)));
    }

    @ParameterizedTest(name = "{0} {1}")
    @MethodSource("postOnlyWrites")
    @DisplayName("should refuse a staging, drug or pharmacy write that is not a POST before touching anything")
    void shouldRejectStagingOrPharmacyWrite_whenMethodIsNotPost(String httpMethod, String write) throws Exception {
        // CSRFGuard does not check GET: a link or image tag must not stage, archive, clear or relink.
        request.setMethod(httpMethod);
        request.setParameter("drugId", "5");
        request.setParameter("drugIds", "5");
        request.setParameter("deleteRxId", "prefix_77");
        request.setParameter("demoNo", String.valueOf(DEMOGRAPHIC_NO));
        request.setParameter("pharmacyId", "3");
        MockHttpServletResponse response = (MockHttpServletResponse) ServletActionContext.getResponse();

        String result = switch (write) {
            case "rePrescribe.represcribe" -> {
                RxRePrescribe2Action action = new RxRePrescribe2Action();
                action.setDrugList("5");
                yield action.represcribe();
            }
            case "rePrescribe.represcribe2" -> new RxRePrescribe2Action().represcribe2();
            case "rePrescribe.saveReRxDrugIdToStash" -> new RxRePrescribe2Action().saveReRxDrugIdToStash();
            case "rePrescribe.repcbAllLongTerm" -> new RxRePrescribe2Action().repcbAllLongTerm();
            case "rePrescribe.represcribeMultiple" -> new RxRePrescribe2Action().represcribeMultiple();
            case "deleteRx.execute" -> {
                RxDeleteRx2Action action = new RxDeleteRx2Action();
                action.setDrugList("77");
                yield action.execute();
            }
            case "deleteRx.Delete2" -> new RxDeleteRx2Action().Delete2();
            case "deleteRx.Discontinue" -> new RxDeleteRx2Action().Discontinue();
            case "deleteRx.clearStash" -> new RxDeleteRx2Action().clearStash();
            case "deleteRx.clearReRxDrugList" -> new RxDeleteRx2Action().clearReRxDrugList();
            case "pharmacy.pharmacyAction" -> {
                request.setParameter("pharmacyAction", "Delete");
                yield new RxManagePharmacy2Action().execute();
            }
            default -> {
                request.setParameter("method", write.substring("pharmacy.".length()));
                yield new RxManagePharmacy2Action().execute();
            }
        };

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        assertThat(bean.getStashSize()).isEqualTo(2);
        assertThat(bean.getReRxDrugIdList()).containsExactly("55");
        verifyNoInteractions(securityInfoManager);
        for (Object dependency : dependencies.values()) {
            verifyNoInteractions(dependency);
        }
        logActionMock.verifyNoInteractions();
    }

    @ParameterizedTest(name = "{0}")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"represcribe2", "saveReRxDrugIdToStash",
            "repcbAllLongTerm", "represcribeMultiple"})
    @DisplayName("should answer an AJAX staging call that names no open patient with 409, not a redirect")
    void shouldReturnConflict_whenAjaxStagingNamesNoOpenPatient(String method) throws Exception {
        // fetch/CarlosAjax follow a redirect to the 200 error page and would report success.
        request.removeParameter("demographicNo");
        request.setParameter("drugId", "5");
        request.setParameter("demoNo", String.valueOf(DEMOGRAPHIC_NO));
        MockHttpServletResponse response = (MockHttpServletResponse) ServletActionContext.getResponse();
        RxRePrescribe2Action action = new RxRePrescribe2Action();

        String result = switch (method) {
            case "represcribe2" -> action.represcribe2();
            case "saveReRxDrugIdToStash" -> action.saveReRxDrugIdToStash();
            case "repcbAllLongTerm" -> action.repcbAllLongTerm();
            default -> action.represcribeMultiple();
        };

        assertThat(result).isEqualTo(org.apache.struts2.ActionSupport.NONE);
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(response.getRedirectedUrl()).isNull();
        assertThat(bean.getStashSize()).isEqualTo(2);
    }

    @ParameterizedTest(name = "{0} denied")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"patient read", "record access"})
    @DisplayName("should refuse to open allergies (and activate Rx) for a patient the caller may not read")
    void shouldRefuseShowAllergy_whenPatientReadDenied(String denied) {
        // Opening allergies activates the patient's Rx, which patient-less Rx pages fall back to (#3908).
        int otherPatient = 2002;
        request.setMethod("GET");
        request.setParameter("demographicNo", String.valueOf(otherPatient));
        request.getSession().setAttribute("user", PROVIDER_NO);
        denyPatient(denied, otherPatient, "_allergy");

        assertThatThrownBy(() -> new RxShowAllergy2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_allergy)");

        assertThat(RxSessionBeanResolver.find(request.getSession(), otherPatient)).isNull();
        for (Object dependency : dependencies.values()) {
            verifyNoInteractions(dependency);
        }
    }

    @ParameterizedTest(name = "{0} denied")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"patient read", "record access"})
    @DisplayName("should refuse the allergy check for a patient the caller may not read")
    void shouldRefuseAllergyData_whenPatientReadDenied(String denied) {
        int otherPatient = 2002;
        request.setMethod("GET");
        request.setParameter("method", "allergyData");
        request.setParameter("demographicNo", String.valueOf(otherPatient));
        request.setParameter("atcCode", "J01CA04");
        denyPatient(denied, otherPatient, "_allergy");

        assertThatThrownBy(() -> new RxShowAllergy2Action().execute())
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_allergy)");
        for (Object dependency : dependencies.values()) {
            verifyNoInteractions(dependency);
        }
    }

    @ParameterizedTest(name = "{0} denied")
    @org.junit.jupiter.params.provider.ValueSource(strings = {"patient read", "record access"})
    @DisplayName("should authorise the active Rx patient a patient-less view falls back to")
    void shouldRefusePatientlessGate_whenActivePatientDenied(String denied) {
        request.setMethod("GET");
        request.removeParameter("demographicNo");
        denyPatient(denied, DEMOGRAPHIC_NO, "_rx");

        assertThatThrownBy(() -> io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess
                .require(securityInfoManager, loggedInInfo, request, "_rx", "r"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
    }

    private void denyPatient(String denied, int patient, String object) {
        if ("patient read".equals(denied)) {
            when(securityInfoManager.hasPrivilege(any(), org.mockito.ArgumentMatchers.eq(object),
                    org.mockito.ArgumentMatchers.eq("r"), org.mockito.ArgumentMatchers.eq(patient))).thenReturn(false);
        } else {
            when(securityInfoManager.isAllowedAccessToPatientRecord(any(), org.mockito.ArgumentMatchers.eq(patient)))
                    .thenReturn(false);
        }
    }

    static Stream<Arguments> readPaths() {
        List<String> reads = List.of("rePrescribe.reprint", "rePrescribe.reprint2", "viewScript.preview",
                "writeScript.listPreviousInstructions", "writeScript.getInstructionsAutocomplete",
                "writeScript.checkNoStashItem", "writeScript.iterateStash", "writeScript.edit",
                "stash.setStashIndex", "stash.edit");
        return reads.stream().flatMap(read -> Stream.of(
                Arguments.of(read, "patient read"), Arguments.of(read, "record access")));
    }

    @ParameterizedTest(name = "{0} ({1} denied)")
    @MethodSource("readPaths")
    @DisplayName("should refuse a read of a patient the caller may not read")
    void shouldRefuseRead_whenPatientReadDenied(String path, String denied) {
        // Each of these resolved the patient from the request (or the active fallback) after only a
        // global _rx check; the patient is now authorised first (#3908).
        denyPatient(denied, DEMOGRAPHIC_NO, "_rx");
        request.setParameter("randomId", String.valueOf(bean.getStashItem(0).getRandomId()));
        request.setParameter("scriptNo", "1234");
        request.setParameter("term", "take");
        io.github.carlos_emr.carlos.commn.model.Drug drug = new io.github.carlos_emr.carlos.commn.model.Drug();
        drug.setDemographicId(DEMOGRAPHIC_NO);
        when(dependency(io.github.carlos_emr.carlos.commn.dao.DrugDao.class).find(5)).thenReturn(drug);

        ThrowingCallable read = switch (path) {
            case "rePrescribe.reprint" -> () -> {
                RxRePrescribe2Action action = new RxRePrescribe2Action();
                action.setDrugList("5");
                action.reprint();
            };
            case "rePrescribe.reprint2" -> () -> new RxRePrescribe2Action().reprint2();
            case "viewScript.preview" -> () -> {
                request.setMethod("GET");
                new RxViewScript2Action(mock(io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService.class)).execute();
            };
            case "writeScript.listPreviousInstructions" -> () -> new RxWriteScript2Action().listPreviousInstructions();
            case "writeScript.getInstructionsAutocomplete" -> () -> new RxWriteScript2Action().getInstructionsAutocomplete();
            case "writeScript.checkNoStashItem" -> () -> new RxWriteScript2Action().checkNoStashItem();
            case "writeScript.iterateStash" -> () -> new RxWriteScript2Action().iterateStash();
            case "writeScript.edit" -> () -> {
                RxWriteScript2Action action = new RxWriteScript2Action();
                action.setAction("edit");
                action.execute();
            };
            case "stash.setStashIndex" -> () -> new RxStash2Action().setStashIndex();
            case "stash.edit" -> () -> {
                RxStash2Action action = new RxStash2Action();
                action.setAction("edit");
                action.execute();
            };
            default -> throw new IllegalArgumentException("unknown read path " + path);
        };

        assertThatThrownBy(read)
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_rx)");
        assertThat(bean.getStashSize()).isEqualTo(2);
        assertThat(bean.getStashIndex()).isZero();
        for (Map.Entry<Class<?>, Object> entry : dependencies.entrySet()) {
            verifyNoInteractions(entry.getValue());
        }
        logActionMock.verifyNoInteractions();
    }

    @org.junit.jupiter.api.Test
    @DisplayName("should give a JSP no bean for a patient the caller may not read")
    void shouldHideBeanFromJsp_whenPatientNotReadable() {
        // The Rx JSPs stop rendering on a null bean, whatever route forwarded to them (#3908).
        assertThat(io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess
                .resolveAuthorised(request, "_rx", "r")).isSameAs(bean);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), org.mockito.ArgumentMatchers.eq(DEMOGRAPHIC_NO)))
                .thenReturn(false);
        assertThat(io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess
                .resolveAuthorised(request, "_rx", "r")).isNull();
    }

    @SuppressWarnings("unchecked")
    private <T> T dependency(Class<T> type) {
        return (T) dependencies.computeIfAbsent(type, Mockito::mock);
    }

    private ThrowingCallable call(String path) {
        long cardKey = bean.getStashItem(0).getRandomId();
        switch (path) {
            case "clearPending":
                return () -> {
                    RxClearPending2Action action = new RxClearPending2Action();
                    action.setAction("");
                    action.execute();
                };
            case "deleteRx.Delete2":
                request.setParameter("deleteRxId", "prefix_77");
                return () -> new RxDeleteRx2Action().Delete2();
            case "deleteRx.clearStash":
                return () -> new RxDeleteRx2Action().clearStash();
            case "deleteRx.clearReRxDrugList":
                return () -> new RxDeleteRx2Action().clearReRxDrugList();
            case "deleteRx.Discontinue":
                request.setParameter("drugId", "77");
                request.setParameter("reason", "other");
                return () -> new RxDeleteRx2Action().Discontinue();
            case "stash.deletePrescribe":
                request.setParameter("randomId", String.valueOf(cardKey));
                return () -> new RxStash2Action().deletePrescribe();
            case "addFavorite.execute":
                return () -> {
                    RxAddFavorite2Action action = new RxAddFavorite2Action();
                    action.setStashId("0");
                    action.setFavoriteName("fav");
                    action.execute();
                };
            case "addFavorite.addFav2":
                request.setParameter("randomId", String.valueOf(cardKey));
                request.setParameter("favoriteName", "fav");
                return () -> new RxAddFavorite2Action().addFav2();
            case "addFavorite.savedDrug":
                return () -> {
                    RxAddFavorite2Action action = new RxAddFavorite2Action();
                    action.setDrugId("5");
                    action.setFavoriteName("fav");
                    action.execute();
                };
            case "addFavorite.savedDrugAjax":
                request.setParameter("parameterValue", "addFav2");
                request.setParameter("drugId", "5");
                request.setParameter("favoriteName", "fav");
                return () -> new RxAddFavorite2Action().execute();
            case "useFavorite.execute":
                request.setParameter("favoriteId", "3");
                return () -> new RxUseFavorite2Action().execute();
            case "useFavorite.useFav2":
                request.setParameter("favoriteId", "3");
                request.setParameter("randomId", "4242");
                return () -> new RxUseFavorite2Action().useFav2();
            case "chooseDrug":
                return () -> new RxChooseDrug2Action().execute();
            case "writeScript.updateAndPrint":
                return () -> {
                    RxWriteScript2Action action = new RxWriteScript2Action();
                    action.setAction("updateAndPrint");
                    action.execute();
                };
            case "rePrescribe.represcribe":
                return () -> {
                    RxRePrescribe2Action action = new RxRePrescribe2Action();
                    action.setDrugList("5");
                    action.represcribe();
                };
            case "rePrescribe.represcribe2":
            case "rePrescribe.saveReRxDrugIdToStash":
                request.setParameter("drugId", "5");
                return path.endsWith("2")
                        ? () -> new RxRePrescribe2Action().represcribe2()
                        : () -> new RxRePrescribe2Action().saveReRxDrugIdToStash();
            case "rePrescribe.represcribeMultiple":
                request.setParameter("drugIds", "5");
                return () -> new RxRePrescribe2Action().represcribeMultiple();
            case "rePrescribe.repcbAllLongTerm":
                return () -> new RxRePrescribe2Action().repcbAllLongTerm();
            case "rePrescribe.saveDigitalSignature":
                request.setParameter("scriptId", "1234");
                request.setParameter("digitalSignatureId", "77");
                return () -> new RxRePrescribe2Action().saveDigitalSignature();
            case "viewScript.save":
                return () -> new RxViewScript2Action(mock(io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService.class))
                        .execute();
            case "addAllergy":
                request.setParameter("formDemographicNo", String.valueOf(DEMOGRAPHIC_NO));
                request.setParameter("ID", "1");
                request.setParameter("name", "Penicillin");
                request.setParameter("type", "8");
                return () -> new RxAddAllergy2Action().execute();
            case "deleteAllergy":
                request.setParameter("ID", "9");
                request.setParameter("action", "delete");
                return () -> new RxDeleteAllergy2Action().execute();
            case "showAllergy.reorder":
                request.setParameter("method", "reorder");
                request.setParameter("direction", "up");
                request.setParameter("allergyId", "5");
                return () -> new RxShowAllergy2Action().reorder();
            case "reason.addDrugReason":
                io.github.carlos_emr.carlos.commn.model.Drug drug = new io.github.carlos_emr.carlos.commn.model.Drug();
                drug.setDemographicId(DEMOGRAPHIC_NO);
                when(dependency(io.github.carlos_emr.carlos.commn.dao.DrugDao.class).find(5)).thenReturn(drug);
                request.setParameter("drugId", "5");
                request.setParameter("codingSystem", "icd9");
                request.setParameter("jsonDxSearch", "250");
                request.setParameter("method", "addDrugReason");
                return () -> new RxReason2Action().execute();
            case "reason.archiveReason":
                // Archiving must read the reason to learn its patient; only that read may happen.
                io.github.carlos_emr.carlos.commn.model.DrugReason reason = new io.github.carlos_emr.carlos.commn.model.DrugReason();
                reason.setDemographicNo(DEMOGRAPHIC_NO);
                when(dependency(io.github.carlos_emr.carlos.commn.dao.DrugReasonDao.class).find(3)).thenReturn(reason);
                request.setParameter("reasonId", "3");
                request.setParameter("archiveReason", "entered in error");
                request.setParameter("method", "archiveReason");
                return () -> new RxReason2Action().execute();
            default:
                return writeScript(path.substring("writeScript.".length()), cardKey);
        }
    }

    private ThrowingCallable writeScript(String dispatch, long cardKey) {
        request.setParameter("parameterValue", dispatch);
        request.setParameter("randomId", String.valueOf(cardKey));
        request.setParameter("ltDrugId", "77");
        request.setParameter("isLongTerm", "true");
        request.setParameter("reRxDrugId", "55");
        request.setParameter("action", "removeFromReRxDrugIdList");
        request.setParameter("customName", "custom");
        request.setParameter("name", "custom");
        request.setParameter("drugId", "5");
        request.setParameter("specialInstruction", "take with food");
        request.setParameter("elementId", "repeats_" + cardKey);
        request.setParameter("propertyValue", "1");
        return () -> new RxWriteScript2Action().execute();
    }
}
