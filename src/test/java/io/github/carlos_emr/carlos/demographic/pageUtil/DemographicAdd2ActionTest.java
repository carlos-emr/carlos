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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.PMmodule.service.ProgramManager;
import io.github.carlos_emr.carlos.commn.dao.CountryCodeDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import java.time.Year;
import java.util.ArrayList;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Test suite for {@link DemographicAdd2Action}.
 *
 * <p>Covers security enforcement (null session, missing privilege),
 * write privilege requirement (not read), and session expiry
 * (null "user" attribute returns "logout").</p>
 *
 * @since 2026-04-04
 */
@DisplayName("DemographicAdd2Action Tests")
@Tag("unit")
@Tag("web")
@Tag("demographic")
class DemographicAdd2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    private DemographicAdd2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String key = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(key, mockLoggedInInfo);

        action = new DemographicAdd2Action();

        // Inject mocks via reflection (fields initialized at declaration time)
        java.lang.reflect.Field secField = DemographicAdd2Action.class.getDeclaredField("securityInfoManager");
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
        @DisplayName("should throw SecurityException when user lacks demographic write privilege")
        void shouldThrowSecurityException_whenUserLacksWritePrivilege() {
            denyPrivilege("_demographic", "w");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("missing required sec object (_demographic)");

            verifySecurityCheck("_demographic", "w");
        }

        @Test
        @DisplayName("should require write privilege, not read")
        void shouldRequireWritePrivilege_notRead() {
            // Allow read but deny write
            allowPrivilege("_demographic", "r");
            denyPrivilege("_demographic", "w");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class);

            // Verify it checked for "w", not "r"
            verify(mockSecurityInfoManager).hasPrivilege(
                    any(LoggedInInfo.class), eq("_demographic"), eq("w"), any());
        }
    }

    @Nested
    @DisplayName("Session Expiry")
    class SessionExpiry {

        @BeforeEach
        void allowAccess() {
            allowPrivilege("_demographic", "w");
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
    @DisplayName("Happy Path - Successful Add Page Load")
    class HappyPath {

        @Mock private CountryCodeDao mockCountryCodeDao;
        @Mock private UserPropertyDAO mockUserPropertyDAO;
        @Mock private ProviderDao mockProviderDao;
        @Mock private PatientConsentManager mockPatientConsentManager;
        @Mock private DemographicDao mockDemographicDao;
        @Mock private WaitingListNameDao mockWaitingListNameDao;
        @Mock private EFormDao mockEFormDao;
        @Mock private ProgramDao mockProgramDao;
        @Mock private ProgramManager mockProgramManager;
        @Mock private ProgramManager2 mockProgramManager2;
        @Mock private ProfessionalSpecialistDao mockProfessionalSpecialistDao;
        @Mock private OscarLogDao mockOscarLogDao;

        private MockedStatic<LogAction> logActionMock;

        @BeforeEach
        void setUpHappyPath() {
            MockitoAnnotations.openMocks(this);

            // Mock LogAction statically so that tests are deterministic regardless
            // of whether LogAction's static initializer has already run in the JVM.
            logActionMock = mockStatic(LogAction.class);

            registerSpringBeans();
            stubDefaultBehaviour();
            allowPrivilege("_demographic", "w");
        }

        @AfterEach
        void tearDownHappyPath() {
            logActionMock.close();
        }

        /** Registers all SpringUtils beans needed by execute(). */
        private void registerSpringBeans() {
            replaceSpringUtilsBean(OscarLogDao.class, mockOscarLogDao);
            replaceSpringUtilsBean(CountryCodeDao.class, mockCountryCodeDao);
            replaceSpringUtilsBean(UserPropertyDAO.class, mockUserPropertyDAO);
            replaceSpringUtilsBean(ProviderDao.class, mockProviderDao);
            replaceSpringUtilsBean(PatientConsentManager.class, mockPatientConsentManager);
            replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
            replaceSpringUtilsBean(WaitingListNameDao.class, mockWaitingListNameDao);
            replaceSpringUtilsBean(EFormDao.class, mockEFormDao);
            replaceSpringUtilsBean(ProgramDao.class, mockProgramDao);
            replaceSpringUtilsBean(ProgramManager.class, mockProgramManager);
            replaceSpringUtilsBean(ProgramManager2.class, mockProgramManager2);
            replaceSpringUtilsBean(ProfessionalSpecialistDao.class, mockProfessionalSpecialistDao);
        }

        /** Stubs default return values for DAO/manager calls. */
        private void stubDefaultBehaviour() {
            when(mockCountryCodeDao.getAllCountryCodes()).thenReturn(new ArrayList<>());
            when(mockProviderDao.getActiveProvidersByRole(anyString())).thenReturn(new ArrayList<>());
            when(mockPatientConsentManager.getConsentTypes()).thenReturn(new ArrayList<>());
            // No UserProperty for HC_Type by default — exercises the fallback path
            when(mockUserPropertyDAO.getProp(anyString(), eq(UserProperty.HC_TYPE))).thenReturn(null);
        }

        @Test
        @DisplayName("should return SUCCESS when user has write privilege")
        void shouldReturnSuccess_whenUserHasWritePrivilege() throws Exception {
            String result = executeAction(action);

            assertThat(result).isEqualTo(ActionSupport.SUCCESS);
        }

        @Test
        @DisplayName("should set curMonth and curDay as two-digit zero-padded strings")
        void shouldSetZeroPaddedMonthAndDay_asTwoDigitStrings() throws Exception {
            executeAction(action);

            String curMonth = (String) mockRequest.getAttribute("curMonth");
            String curDay = (String) mockRequest.getAttribute("curDay");

            assertThat(curMonth).matches("^\\d{2}$");
            assertThat(curDay).matches("^\\d{2}$");
        }

        @Test
        @DisplayName("should set curYear to the actual current year")
        void shouldSetCurYear_toActualCurrentYear() throws Exception {
            executeAction(action);

            String curYear = (String) mockRequest.getAttribute("curYear");
            assertThat(curYear).isEqualTo(String.valueOf(Year.now().getValue()));
        }

        @Test
        @DisplayName("should set today attribute as yyyy-MM-dd formatted date matching curYear")
        void shouldSetToday_asYyyyMmDdFormattedDateMatchingCurYear() throws Exception {
            executeAction(action);

            String today = (String) mockRequest.getAttribute("today");
            String curYear = (String) mockRequest.getAttribute("curYear");

            assertThat(today).matches("^\\d{4}-\\d{2}-\\d{2}$");
            assertThat(today).startsWith(curYear + "-");
        }

        @Test
        @DisplayName("should set prov as uppercase billregion from configuration")
        void shouldSetProv_asUppercaseBillregionFromConfiguration() throws Exception {
            executeAction(action);

            // billregion=ON is set in carlos.properties
            assertThat(mockRequest.getAttribute("prov")).isEqualTo("ON");
        }

        @Test
        @DisplayName("should set defaultCity to empty string when billing centre is not N")
        void shouldSetDefaultCityToEmpty_whenBillingCentreIsNotN() throws Exception {
            // billcenter=G in carlos.properties (not "N"), so defaultCity is empty
            executeAction(action);

            assertThat(mockRequest.getAttribute("defaultCity")).isEqualTo("");
        }

        @Test
        @DisplayName("should set defaultCity to Toronto when prov is ON and billing centre is N")
        void shouldSetDefaultCityToToronto_whenProvIsOnAndBillingCentreIsN() throws Exception {
            CarlosProperties props = CarlosProperties.getInstance();
            String origBillcenter = props.getProperty("billcenter");
            try {
                props.setProperty("billcenter", "N");

                executeAction(action);

                assertThat(mockRequest.getAttribute("defaultCity")).isEqualTo("Toronto");
            } finally {
                if (origBillcenter != null) {
                    props.setProperty("billcenter", origBillcenter);
                } else {
                    props.remove("billcenter");
                }
            }
        }

        @Test
        @DisplayName("should use HC_Type value from UserProperty when the property is present")
        void shouldUseHCTypeFromUserProperty_whenPropertyIsPresent() throws Exception {
            UserProperty hcTypeProp = new UserProperty();
            hcTypeProp.setValue("AB");
            when(mockUserPropertyDAO.getProp(anyString(), eq(UserProperty.HC_TYPE))).thenReturn(hcTypeProp);

            executeAction(action);

            assertThat(mockRequest.getAttribute("HCType")).isEqualTo("AB");
            assertThat(mockRequest.getAttribute("defaultProvince")).isEqualTo("AB");
        }

        @Test
        @DisplayName("should use hctype property when UserProperty is absent but hctype is configured")
        void shouldUseHctypeProperty_whenUserPropertyAbsentAndHctypeConfigured() throws Exception {
            CarlosProperties props = CarlosProperties.getInstance();
            String origHctype = (String) props.get("hctype");
            try {
                props.setProperty("hctype", "BC");

                executeAction(action);

                assertThat(mockRequest.getAttribute("HCType")).isEqualTo("BC");
                assertThat(mockRequest.getAttribute("defaultProvince")).isEqualTo("BC");
            } finally {
                if (origHctype != null) {
                    props.setProperty("hctype", origHctype);
                } else {
                    props.remove("hctype");
                }
            }
        }

        @Test
        @DisplayName("should fallback to billregion when hctype property is not configured and UserProperty absent")
        void shouldFallbackToBillregion_whenHctypePropertyIsNotConfiguredAndUserPropertyAbsent() throws Exception {
            // mockUserPropertyDAO.getProp(...) returns null (from setUp)
            // hctype is not set in carlos.properties, so it falls back to billregion=ON

            executeAction(action);

            assertThat(mockRequest.getAttribute("HCType")).isEqualTo("ON");
            assertThat(mockRequest.getAttribute("defaultProvince")).isEqualTo("ON");
        }

        @Test
        @DisplayName("should load consent types from PatientConsentManager when module is enabled")
        void shouldLoadConsentTypes_whenConsentModuleEnabled() throws Exception {
            // USE_NEW_PATIENT_CONSENT_MODULE=true in carlos.properties
            executeAction(action);

            verify(mockPatientConsentManager).getConsentTypes();
        }
    }
}
