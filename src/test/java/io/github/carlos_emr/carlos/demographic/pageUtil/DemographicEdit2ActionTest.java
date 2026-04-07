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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.dao.CountryCodeDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.dao.WaitingListDao;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.LookupListManager;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SessionConstants;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for {@link DemographicEdit2Action}.
 *
 * <p>Covers security enforcement (null session, missing privilege),
 * session expiry (null "user" attribute returns "logout"),
 * demographic_no validation (null, empty, non-numeric),
 * and null demographic record handling.</p>
 *
 * @since 2026-04-04
 */
@DisplayName("DemographicEdit2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class DemographicEdit2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    private DemographicEdit2Action action;

    @Mock
    private DemographicDao mockDemographicDao;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new DemographicEdit2Action();

        // Inject mocks via reflection (fields initialized at declaration time)
        java.lang.reflect.Field secField = DemographicEdit2Action.class.getDeclaredField("securityInfoManager");
        secField.setAccessible(true);
        secField.set(action, mockSecurityInfoManager);
    }

    @Nested
    @DisplayName("Security Enforcement")
    class SecurityEnforcement {

        @Test
        @DisplayName("should throw SecurityException when session is null")
        void shouldThrowSecurityException_whenSessionIsNull() {
            String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
            setSessionAttribute(key, null);

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("missing required session");
        }

        @Test
        @DisplayName("should throw SecurityException when user lacks demographic read privilege")
        void shouldThrowSecurityException_whenUserLacksReadPrivilege() {
            denyPrivilege("_demographic", "r");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("missing required sec object (_demographic)");

            verifySecurityCheck("_demographic", "r");
        }

        @Test
        @DisplayName("should require read privilege for demographic edit")
        void shouldRequireReadPrivilege_forDemographicEdit() {
            denyPrivilege("_demographic", "r");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class);

            verify(mockSecurityInfoManager).hasPrivilege(
                    any(LoggedInInfo.class), eq("_demographic"), eq("r"), any());
        }
    }

    @Nested
    @DisplayName("Session Expiry")
    class SessionExpiry {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should return logout when user session attribute is null")
        void shouldReturnLogout_whenUserSessionAttributeIsNull() throws Exception {
            setSessionAttribute("user", null);

            String result = executeAction(action);

            assertThat(result).isEqualTo("logout");
        }
    }

    @Nested
    @DisplayName("Demographic Number Validation")
    class DemographicNoValidation {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is null")
        void shouldReturnError_whenDemographicNoIsNull() throws Exception {
            // No demographic_no parameter set

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is empty")
        void shouldReturnError_whenDemographicNoIsEmpty() throws Exception {
            addRequestParameter("demographic_no", "");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is whitespace only")
        void shouldReturnError_whenDemographicNoIsWhitespace() throws Exception {
            addRequestParameter("demographic_no", "   ");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }

        @Test
        @DisplayName("should return ERROR when demographic_no is non-numeric")
        void shouldReturnError_whenDemographicNoIsNonNumeric() throws Exception {
            addRequestParameter("demographic_no", "abc");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }

    @Nested
    @DisplayName("Demographic Record Loading")
    class DemographicRecordLoading {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "r");
        }

        @Test
        @DisplayName("should return ERROR when demographic record not found")
        void shouldReturnError_whenDemographicNotFound() throws Exception {
            addRequestParameter("demographic_no", "99999");
            when(mockDemographicDao.getDemographic("99999")).thenReturn(null);

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.ERROR);
        }
    }

    @Nested
    @DisplayName("Happy Path - Successful Demographic Load")
    class HappyPath {

        @Mock private DemographicExtDao mockExtDao;
        @Mock private DemographicCustDao mockCustDao;
        @Mock private DemographicArchiveDao mockArchiveDao;
        @Mock private DemographicExtArchiveDao mockExtArchiveDao;
        @Mock private AdmissionManager mockAdmissionManager;
        @Mock private ProviderDao mockProviderDao;
        @Mock private CountryCodeDao mockCountryCodeDao;
        @Mock private CaseManagementManager mockCaseManagementManager;
        @Mock private ProgramManager2 mockProgramManager2;
        @Mock private PatientConsentManager mockPatientConsentManager;
        @Mock private LookupListManager mockLookupListManager;
        @Mock private ScheduleTemplateCodeDao mockScheduleTemplateCodeDao;
        @Mock private WaitingListDao mockWaitingListDao;
        @Mock private WaitingListNameDao mockWaitingListNameDao;
        @Mock private UserPropertyDAO mockUserPropertyDAO;
        @Mock private DemographicManager mockDemographicManager;
        @Mock private ProgramManager mockProgramManager;
        @Mock private ProgramDao mockProgramDao;
        @Mock private OscarLogDao mockOscarLogDao;

        private static final String DEMO_NO = "12345";
        private Demographic testDemographic;

        @BeforeEach
        void setUpHappyPath() {
            MockitoAnnotations.openMocks(this);

            // Register all beans needed for the full execute() happy path.
            // OscarLogDao must be registered first so that LogAction's static
            // initialiser (SpringUtils.getBean(OscarLogDao.class)) can succeed
            // when LogAction is loaded by the JVM during the first test run.
            replaceSpringUtilsBean(OscarLogDao.class, mockOscarLogDao);
            replaceSpringUtilsBean(DemographicExtDao.class, mockExtDao);
            replaceSpringUtilsBean(DemographicCustDao.class, mockCustDao);
            replaceSpringUtilsBean(DemographicArchiveDao.class, mockArchiveDao);
            replaceSpringUtilsBean(DemographicExtArchiveDao.class, mockExtArchiveDao);
            replaceSpringUtilsBean(AdmissionManager.class, mockAdmissionManager);
            replaceSpringUtilsBean(ProviderDao.class, mockProviderDao);
            replaceSpringUtilsBean(CountryCodeDao.class, mockCountryCodeDao);
            replaceSpringUtilsBean(CaseManagementManager.class, mockCaseManagementManager);
            replaceSpringUtilsBean(ProgramManager2.class, mockProgramManager2);
            replaceSpringUtilsBean(PatientConsentManager.class, mockPatientConsentManager);
            replaceSpringUtilsBean(LookupListManager.class, mockLookupListManager);
            replaceSpringUtilsBean(ScheduleTemplateCodeDao.class, mockScheduleTemplateCodeDao);
            replaceSpringUtilsBean(WaitingListDao.class, mockWaitingListDao);
            replaceSpringUtilsBean(WaitingListNameDao.class, mockWaitingListNameDao);
            replaceSpringUtilsBean(UserPropertyDAO.class, mockUserPropertyDAO);
            replaceSpringUtilsBean(DemographicManager.class, mockDemographicManager);
            replaceSpringUtilsBean(ProgramManager.class, mockProgramManager);
            replaceSpringUtilsBean(ProgramDao.class, mockProgramDao);

            allowPrivilege("_demographic", "r");
            addRequestParameter("demographic_no", DEMO_NO);

            testDemographic = new Demographic();
            testDemographic.setDemographicNo(12345);
            testDemographic.setFirstName("John");
            testDemographic.setLastName("Doe");
            testDemographic.setYearOfBirth("1985");
            testDemographic.setMonthOfBirth("06");
            testDemographic.setDateOfBirth("15");

            when(mockDemographicDao.getDemographic(DEMO_NO)).thenReturn(testDemographic);
            when(mockExtDao.getAllValuesForDemo(12345)).thenReturn(new HashMap<>());
            when(mockCustDao.find(12345)).thenReturn(null);
            when(mockArchiveDao.findByDemographicNo(12345)).thenReturn(new ArrayList<>());
            when(mockExtArchiveDao.getDemographicExtArchiveByDemoAndKey(12345, "demo_cell"))
                    .thenReturn(new ArrayList<>());
            when(mockAdmissionManager.getCurrentCommunityProgramAdmission(12345)).thenReturn(null);
            when(mockAdmissionManager.getCurrentServiceProgramAdmission(12345)).thenReturn(new ArrayList<>());
            when(mockProviderDao.getActiveProviders()).thenReturn(new ArrayList<>());
            when(mockProviderDao.getActiveProvidersByRole(anyString())).thenReturn(new ArrayList<>());
            when(mockCountryCodeDao.getAllCountryCodes()).thenReturn(new ArrayList<>());
            when(mockCaseManagementManager.getLinkByTableId(anyInt(), anyLong())).thenReturn(new ArrayList<>());
            when(mockLookupListManager.findLookupListByName(any(), anyString())).thenReturn(null);
            when(mockPatientConsentManager.getActiveConsentTypes()).thenReturn(new ArrayList<>());
            when(mockPatientConsentManager.getAllConsentsByDemographic(any(), anyInt()))
                    .thenReturn(new ArrayList<>());
        }

        @Test
        @DisplayName("should return SUCCESS when valid demographic record exists")
        void shouldReturnSuccess_whenDemographicExists() throws Exception {
            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should set demographic and demographic_no request attributes on success")
        void shouldSetKeyRequestAttributes_onSuccess() throws Exception {
            executeAction(action);

            assertThat(mockRequest.getAttribute("demographic")).isEqualTo(testDemographic);
            assertThat(mockRequest.getAttribute("demographic_no")).isEqualTo(DEMO_NO);
        }

        @Test
        @DisplayName("should set prov attribute from billregion configuration")
        void shouldSetProvAttribute_fromBillregionConfiguration() throws Exception {
            executeAction(action);

            // billregion=ON is set in the test carlos.properties
            assertThat(mockRequest.getAttribute("prov")).isEqualTo("ON");
        }

        @Test
        @DisplayName("should set birth date fields when demographic has populated dates")
        void shouldSetBirthDateFields_whenDatesProvided() throws Exception {
            executeAction(action);

            assertThat(mockRequest.getAttribute("birthYear")).isEqualTo("1985");
            assertThat(mockRequest.getAttribute("birthMonth")).isEqualTo("06");
            assertThat(mockRequest.getAttribute("birthDate")).isEqualTo("15");
        }

        @Test
        @DisplayName("should set default birth date fields when year/month/day are null")
        void shouldSetDefaultBirthDateFields_whenDatesAreNull() throws Exception {
            testDemographic.setYearOfBirth(null);
            testDemographic.setMonthOfBirth(null);
            testDemographic.setDateOfBirth(null);

            executeAction(action);

            assertThat(mockRequest.getAttribute("birthYear")).isEqualTo("0000");
            assertThat(mockRequest.getAttribute("birthMonth")).isEqualTo("00");
            assertThat(mockRequest.getAttribute("birthDate")).isEqualTo("00");
        }

        @Test
        @DisplayName("should set default birth date fields when year/month/day are empty strings")
        void shouldSetDefaultBirthDateFields_whenDatesAreEmpty() throws Exception {
            testDemographic.setYearOfBirth("");
            testDemographic.setMonthOfBirth("");
            testDemographic.setDateOfBirth("");

            executeAction(action);

            assertThat(mockRequest.getAttribute("birthYear")).isEqualTo("0000");
            assertThat(mockRequest.getAttribute("birthMonth")).isEqualTo("00");
            assertThat(mockRequest.getAttribute("birthDate")).isEqualTo("00");
        }

        @Test
        @DisplayName("should extract referral doctor XML fields when family doctor field is set")
        void shouldExtractReferralDoctorXmlFields_whenFamilyDoctorIsSet() throws Exception {
            testDemographic.setFamilyDoctor(
                    "<rd>Dr Smith</rd><rdohip>12345</rdohip><family_doc>Dr Jones</family_doc>");

            executeAction(action);

            assertThat(mockRequest.getAttribute("rd")).isEqualTo("Dr Smith");
            assertThat(mockRequest.getAttribute("rdohip")).isEqualTo("12345");
            assertThat(mockRequest.getAttribute("family_doc")).isEqualTo("Dr Jones");
        }

        @Test
        @DisplayName("should set empty referral doctor fields when family doctor field is null")
        void shouldSetEmptyReferralDoctorFields_whenFamilyDoctorIsNull() throws Exception {
            testDemographic.setFamilyDoctor(null);

            executeAction(action);

            assertThat(mockRequest.getAttribute("rd")).isEqualTo("");
            assertThat(mockRequest.getAttribute("rdohip")).isEqualTo("");
            assertThat(mockRequest.getAttribute("family_doc")).isEqualTo("");
        }

        @Test
        @DisplayName("should resolve current program name when CURRENT_PROGRAM_ID is in session")
        void shouldResolveCurrentProgramName_whenProgramIdInSession() throws Exception {
            setSessionAttribute(SessionConstants.CURRENT_PROGRAM_ID, "42");
            Program testProgram = new Program();
            testProgram.setName("Mental Health Program");
            when(mockProgramManager2.getProgram(any(LoggedInInfo.class), eq(42))).thenReturn(testProgram);

            executeAction(action);

            assertThat(mockRequest.getAttribute("currentProgram")).isEqualTo("Mental Health Program");
        }

        @Test
        @DisplayName("should set empty currentProgram and not throw when CURRENT_PROGRAM_ID is non-numeric")
        void shouldSetEmptyCurrentProgram_whenProgramIdIsNonNumeric() throws Exception {
            setSessionAttribute(SessionConstants.CURRENT_PROGRAM_ID, "not-a-number");

            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
            assertThat(mockRequest.getAttribute("currentProgram")).isEqualTo("");
        }

        @Test
        @DisplayName("should invoke audit log with provider number and demographic_no on successful load")
        void shouldInvokeAuditLog_onSuccessfulLoad() throws Exception {
            try (MockedStatic<LogAction> logMock = mockStatic(LogAction.class)) {
                executeAction(action);

                logMock.verify(() -> LogAction.addLog(
                        eq(TEST_PROVIDER),
                        eq(LogConst.READ),
                        eq(LogConst.CON_DEMOGRAPHIC),
                        eq(DEMO_NO),
                        any(),
                        eq(DEMO_NO)));
            }
        }

        @Test
        @DisplayName("should load consent types when USE_NEW_PATIENT_CONSENT_MODULE is enabled")
        void shouldLoadConsentTypes_whenConsentModuleEnabled() throws Exception {
            // USE_NEW_PATIENT_CONSENT_MODULE=true is set in the test carlos.properties
            executeAction(action);

            verify(mockPatientConsentManager).getActiveConsentTypes();
            verify(mockPatientConsentManager).getAllConsentsByDemographic(
                    any(LoggedInInfo.class), eq(12345));
        }

        @Test
        @DisplayName("should coerce null service admissions to an empty list")
        void shouldCoerceNullServiceAdmissions_toEmptyList() throws Exception {
            when(mockAdmissionManager.getCurrentServiceProgramAdmission(12345)).thenReturn(null);

            executeAction(action);

            @SuppressWarnings("unchecked")
            List<?> serviceAdmissions = (List<?>) mockRequest.getAttribute("serviceAdmissions");
            assertThat(serviceAdmissions).isNotNull().isEmpty();
        }
    }
}
