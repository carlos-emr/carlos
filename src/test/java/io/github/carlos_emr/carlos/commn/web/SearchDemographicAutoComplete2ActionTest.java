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
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.*;
import org.mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

import java.util.*;

/**
 * Test suite for {@link SearchDemographicAutoComplete2Action}.
 *
 * <p>Validates search type routing (name, HIN, phone, address, DOB),
 * activeOnly filtering, response format, backward-compatible keys,
 * and security enforcement.
 *
 * @since 2026-03-23
 */
@DisplayName("SearchDemographicAutoComplete2Action Tests")
@Tag("integration")
@Tag("web")
@Tag("demographic")
class SearchDemographicAutoComplete2ActionTest extends CarlosWebTestBase {

    private static final String TEST_PROVIDER = "999998";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private DemographicDao mockDemographicDao;

    @Mock
    private DemographicCustDao mockDemographicCustDao;

    private SearchDemographicAutoComplete2Action action;

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        // Re-configure security after openMocks re-created parent's mock instances
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), anyString(), anyString(), any()))
                .thenReturn(true);

        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);
        replaceSpringUtilsBean(DemographicDao.class, mockDemographicDao);
        replaceSpringUtilsBean(DemographicCustDao.class, mockDemographicCustDao);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);

        // Default: no custom alerts
        when(mockDemographicCustDao.find(anyInt())).thenReturn(null);

        action = new SearchDemographicAutoComplete2Action();
        injectField("securityInfoManager", mockSecurityInfoManager);
        injectField("request", mockRequest);
        injectField("response", mockResponse);
    }

    private void injectField(String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = SearchDemographicAutoComplete2Action.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(action, value);
    }

    private Demographic createDemographic(int demoNo, String lastName, String firstName) {
        Demographic demo = new Demographic();
        demo.setDemographicNo(demoNo);
        demo.setLastName(lastName);
        demo.setFirstName(firstName);
        demo.setPatientStatus("AC");
        demo.setRosterStatus("RO");
        demo.setHin("1234567890");
        demo.setPhone("416-555-1234");
        demo.setCellPhone("647-555-5678");
        demo.setEmail("test@example.com");
        demo.setAddress("123 Main St");
        demo.setProviderNo(TEST_PROVIDER);
        demo.setDateOfBirth("1990-01-15");
        return demo;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> executeAndParseJsonArray() throws Exception {
        addRequestParameter("jqueryJSON", "true");
        executeAction(action);
        String json = mockResponse.getContentAsString();
        return MAPPER.readValue(json, new TypeReference<List<Map<String, Object>>>() {});
    }

    // ── Security ─────────────────────────────────────────────

    @Nested
    @DisplayName("Security")
    class Security {

        @Test
        @DisplayName("Should throw SecurityException when _demographic read privilege denied")
        void shouldThrowSecurityException_whenPrivilegeDenied() {
            denyPrivilege("_demographic", "r");
            addRequestParameter("term", "Smith");

            assertThatThrownBy(() -> executeAction(action))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("_demographic");

            verifySecurityCheck("_demographic", "r");
        }
    }

    // ── Empty/Null Search ────────────────────────────────────

    @Nested
    @DisplayName("Empty Search")
    class EmptySearch {

        @Test
        @DisplayName("Should return empty array when search string is null")
        void shouldReturnEmptyArray_whenSearchNull() throws Exception {
            // No search parameter set
            addRequestParameter("jqueryJSON", "true");
            executeAction(action);

            assertThat(mockResponse.getContentAsString()).isEqualTo("[]");
        }

        @Test
        @DisplayName("Should return empty array when search string is blank")
        void shouldReturnEmptyArray_whenSearchBlank() throws Exception {
            addRequestParameter("term", "   ");
            addRequestParameter("jqueryJSON", "true");
            executeAction(action);

            assertThat(mockResponse.getContentAsString()).isEqualTo("[]");
        }
    }

    // ── Search Type Routing ──────────────────────────────────

    @Nested
    @DisplayName("Search Type Routing")
    class SearchTypeRouting {

        @Test
        @DisplayName("Should search by name when searchType is name")
        void shouldSearchByName_whenSearchTypeName() throws Exception {
            addRequestParameter("term", "Smith");
            addRequestParameter("searchType", "name");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should search by HIN when searchType is hin")
        void shouldSearchByHIN_whenSearchTypeHin() throws Exception {
            addRequestParameter("term", "1234567890");
            addRequestParameter("searchType", "hin");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByHIN(eq("1234567890"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByHIN(eq("1234567890"), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should search by phone when searchType is phone")
        void shouldSearchByPhone_whenSearchTypePhone() throws Exception {
            addRequestParameter("term", "416-555");
            addRequestParameter("searchType", "phone");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByPhone(eq("416-555"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByPhone(eq("416-555"), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should search by address when searchType is address")
        void shouldSearchByAddress_whenSearchTypeAddress() throws Exception {
            addRequestParameter("term", "123 Main");
            addRequestParameter("searchType", "address");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByAddress(eq("123 Main"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByAddress(eq("123 Main"), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should default to name search when searchType is null")
        void shouldDefaultToNameSearch_whenSearchTypeNull() throws Exception {
            addRequestParameter("term", "Smith");
            // No searchType parameter
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean());
        }
    }

    // ── ActiveOnly Filtering ─────────────────────────────────

    @Nested
    @DisplayName("ActiveOnly Filtering")
    class ActiveOnlyFiltering {

        @Test
        @DisplayName("Should use NotStatus DAO methods for HIN search when activeOnly is true")
        void shouldUseNotStatusMethod_whenHinSearchAndActiveOnly() throws Exception {
            addRequestParameter("term", "1234567890");
            addRequestParameter("searchType", "hin");
            addRequestParameter("activeOnly", "true");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByHINAndNotStatus(
                    eq("1234567890"), anyList(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByHINAndNotStatus(
                    eq("1234567890"), anyList(), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should use NotStatus DAO methods for phone search when activeOnly is true")
        void shouldUseNotStatusMethod_whenPhoneSearchAndActiveOnly() throws Exception {
            addRequestParameter("term", "416-555");
            addRequestParameter("searchType", "phone");
            addRequestParameter("activeOnly", "true");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByPhoneAndNotStatus(
                    eq("416-555"), anyList(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByPhoneAndNotStatus(
                    eq("416-555"), anyList(), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should use NotStatus DAO methods for address search when activeOnly is true")
        void shouldUseNotStatusMethod_whenAddressSearchAndActiveOnly() throws Exception {
            addRequestParameter("term", "123 Main");
            addRequestParameter("searchType", "address");
            addRequestParameter("activeOnly", "true");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByAddressAndNotStatus(
                    eq("123 Main"), anyList(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByAddressAndNotStatus(
                    eq("123 Main"), anyList(), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should use NotStatus DAO methods for name search when activeOnly is true")
        void shouldUseNotStatusMethod_whenNameSearchAndActiveOnly() throws Exception {
            addRequestParameter("term", "Smith");
            addRequestParameter("searchType", "name");
            addRequestParameter("activeOnly", "true");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByNameAndNotStatus(
                    eq("Smith"), anyList(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByNameAndNotStatus(
                    eq("Smith"), anyList(), anyInt(), anyInt(), anyString(), anyBoolean());
        }
    }

    // ── Response Fields ──────────────────────────────────────

    @Nested
    @DisplayName("Response Fields")
    class ResponseFields {

        @Test
        @DisplayName("Should include both formattedDob and legacy fomattedDob keys")
        void shouldIncludeBothDobKeys_forBackwardCompat() throws Exception {
            addRequestParameter("term", "Smith");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByName(anyString(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            Map<String, Object> result = results.get(0);
            assertThat(result).containsKey("formattedDob");
            assertThat(result).containsKey("fomattedDob");
            assertThat(result.get("formattedDob")).isEqualTo(result.get("fomattedDob"));
        }

        @Test
        @DisplayName("Should include new PHI fields in response")
        void shouldIncludeNewPhiFields_inResponse() throws Exception {
            addRequestParameter("term", "Smith");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByName(anyString(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            Map<String, Object> result = results.get(0);
            assertThat(result.get("hin")).isEqualTo("1234567890");
            assertThat(result.get("phone")).isEqualTo("416-555-1234");
            assertThat(result.get("cellPhone")).isEqualTo("647-555-5678");
            assertThat(result.get("email")).isEqualTo("test@example.com");
            assertThat(result.get("address")).isEqualTo("123 Main St");
        }

        @Test
        @DisplayName("Should return empty strings for null demographic fields")
        void shouldReturnEmptyStrings_whenFieldsNull() throws Exception {
            addRequestParameter("term", "Smith");
            Demographic demo = new Demographic();
            demo.setDemographicNo(1);
            demo.setLastName("Smith");
            demo.setFirstName("John");
            // Leave PHI fields null
            demo.setProviderNo(TEST_PROVIDER);
            when(mockDemographicDao.searchDemographicByName(anyString(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            Map<String, Object> result = results.get(0);
            assertThat(result.get("hin")).isEqualTo("");
            assertThat(result.get("phone")).isEqualTo("");
            assertThat(result.get("cellPhone")).isEqualTo("");
            assertThat(result.get("email")).isEqualTo("");
            assertThat(result.get("address")).isEqualTo("");
        }

        @Test
        @DisplayName("Should set application/json content type")
        void shouldSetJsonContentType() throws Exception {
            addRequestParameter("term", "Smith");
            when(mockDemographicDao.searchDemographicByName(anyString(), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            addRequestParameter("jqueryJSON", "true");
            executeAction(action);

            assertThat(mockResponse.getContentType()).startsWith("application/json");
        }
    }

    // ── Search Parameter Aliases ─────────────────────────────

    @Nested
    @DisplayName("Search Parameter Aliases")
    class SearchParameterAliases {

        @Test
        @DisplayName("Should accept 'demographicKeyword' parameter")
        void shouldAcceptDemographicKeyword() throws Exception {
            addRequestParameter("demographicKeyword", "Smith");
            when(mockDemographicDao.searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            addRequestParameter("jqueryJSON", "true");
            executeAction(action);

            verify(mockDemographicDao).searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should accept 'query' parameter")
        void shouldAcceptQueryParam() throws Exception {
            addRequestParameter("query", "Smith");
            when(mockDemographicDao.searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            addRequestParameter("jqueryJSON", "true");
            executeAction(action);

            verify(mockDemographicDao).searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should accept 'name' parameter")
        void shouldAcceptNameParam() throws Exception {
            addRequestParameter("name", "Smith");
            when(mockDemographicDao.searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(Collections.emptyList());

            addRequestParameter("jqueryJSON", "true");
            executeAction(action);

            verify(mockDemographicDao).searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean());
        }
    }

    // ── POST Method Support ──────────────────────────────────

    @Nested
    @DisplayName("POST Method Support")
    class PostMethodSupport {

        @Test
        @DisplayName("Should process search term from POST body")
        void shouldProcessSearchTerm_fromPostBody() throws Exception {
            mockRequest.setMethod("POST");
            addRequestParameter("term", "Smith");
            addRequestParameter("jqueryJSON", "true");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByName(eq("Smith"), anyInt(), anyInt(), anyString(), anyBoolean());
        }

        @Test
        @DisplayName("Should process HIN search from POST body")
        void shouldProcessHinSearch_fromPostBody() throws Exception {
            mockRequest.setMethod("POST");
            addRequestParameter("term", "1234567890");
            addRequestParameter("searchType", "hin");
            addRequestParameter("jqueryJSON", "true");
            Demographic demo = createDemographic(1, "Smith", "John");
            when(mockDemographicDao.searchDemographicByHIN(eq("1234567890"), anyInt(), anyInt(), anyString(), anyBoolean()))
                    .thenReturn(List.of(demo));

            List<Map<String, Object>> results = executeAndParseJsonArray();

            assertThat(results).hasSize(1);
            verify(mockDemographicDao).searchDemographicByHIN(eq("1234567890"), anyInt(), anyInt(), anyString(), anyBoolean());
        }
    }
}
