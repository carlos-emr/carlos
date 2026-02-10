/**
 * Copyright (c) 2026. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 *
 * <p>
 * Ported from openo-beta/Open-O PR #2268 by LiamStanziani.
 * Original: ca.openosp.openo.tickler.web.TicklerList2ActionTest
 */
package io.github.carlos_emr.carlos.tickler.web;

import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.OpenOWebTestBase;
import io.github.carlos_emr.carlos.tickler.dto.TicklerCommentDTO;
import io.github.carlos_emr.carlos.tickler.dto.TicklerLinkDTO;
import io.github.carlos_emr.carlos.tickler.dto.TicklerListDTO;
import io.github.carlos_emr.carlos.tickler.pageUtil.TicklerList2Action;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Web layer tests for {@link TicklerList2Action}.
 *
 * <p>Validates the DataTables server-side JSON endpoint including privilege
 * enforcement, paging inputs, filter parameters, and JSON response shape.
 *
 * @since 2026-02-06
 */
@DisplayName("TicklerList2Action Web Layer Tests")
@Tag("web")
class TicklerList2ActionTest extends OpenOWebTestBase {

    @Mock
    private TicklerManager mockTicklerManager;

    private TicklerList2Action action;

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String TEST_PROVIDER = "999998";

    @BeforeEach
    void setUp() throws Exception {
        MockitoAnnotations.openMocks(this);

        replaceSpringUtilsBean(TicklerManager.class, mockTicklerManager);
        replaceSpringUtilsBean(SecurityInfoManager.class, mockSecurityInfoManager);

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn(TEST_PROVIDER);
        setSessionAttribute("user", TEST_PROVIDER);
        String loggedInInfoKey = LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY";
        setSessionAttribute(loggedInInfoKey, mockLoggedInInfo);

        // Default: return empty results
        when(mockTicklerManager.getNumTicklers(any(LoggedInInfo.class), any(CustomFilter.class)))
                .thenReturn(0);
        when(mockTicklerManager.getTicklerDTOs(any(LoggedInInfo.class), any(CustomFilter.class), anyInt(), anyInt()))
                .thenReturn(Collections.emptyList());
        when(mockTicklerManager.getTicklerDTOs(any(LoggedInInfo.class), any(CustomFilter.class)))
                .thenReturn(Collections.emptyList());

        action = new TicklerList2Action();
        injectField("ticklerManager", mockTicklerManager);
        injectField("securityInfoManager", mockSecurityInfoManager);
    }

    // -- Privilege Enforcement --

    @Test
    @DisplayName("Should return 403 JSON error when _tickler read privilege denied")
    void shouldReturn403_whenPrivilegeDenied() throws Exception {
        denyPrivilege("_tickler", "r");

        executeAction(action);

        assertThat(getMockResponse().getStatus()).isEqualTo(403);
        JsonNode json = parseResponse();
        assertThat(json.get("error").asText()).isEqualTo("Access denied");
        verifySecurityCheck("_tickler", "r");
    }

    @Test
    @DisplayName("Should return JSON when _tickler read privilege allowed")
    void shouldReturnJson_whenPrivilegeAllowed() throws Exception {
        allowPrivilege("_tickler", "r");

        executeAction(action);

        assertThat(getMockResponse().getContentType()).contains("application/json");
    }

    // -- JSON Response Shape --

    @Test
    @DisplayName("Should return correct top-level JSON structure")
    void shouldReturnCorrectJsonStructure_whenEmptyResults() throws Exception {
        allowPrivilege("_tickler", "r");
        addRequestParameter("draw", "3");

        executeAction(action);

        JsonNode json = parseResponse();
        assertThat(json.get("draw").asInt()).isEqualTo(3);
        assertThat(json.get("recordsTotal").asInt()).isEqualTo(0);
        assertThat(json.get("recordsFiltered").asInt()).isEqualTo(0);
        assertThat(json.get("data").isArray()).isTrue();
        assertThat(json.get("data").size()).isZero();
        assertThat(json.has("comments")).isTrue();
    }

    @Test
    @DisplayName("Should include all required fields in data rows")
    void shouldIncludeAllFields_whenTicklerReturned() throws Exception {
        allowPrivilege("_tickler", "r");

        TicklerListDTO dto = createTestTickler(1, "Follow up on labs");
        stubPaginatedResults(1, List.of(dto));

        executeAction(action);

        JsonNode row = parseResponse().get("data").get(0);
        assertThat(row.get("id").asInt()).isEqualTo(1);
        assertThat(row.get("demoNo").asInt()).isEqualTo(1001);
        assertThat(row.get("demoLastName").asText()).isEqualTo("Smith");
        assertThat(row.get("demoFirstName").asText()).isEqualTo("John");
        assertThat(row.get("creator").asText()).isEqualTo("Doctor, Jane");
        assertThat(row.get("serviceDate").asText()).isNotEmpty();
        assertThat(row.get("createDate").asText()).isNotEmpty();
        assertThat(row.get("priority").asText()).isEqualTo("Normal");
        assertThat(row.get("assignee").asText()).isEqualTo("Nurse, Bob");
        assertThat(row.get("status").asText()).isNotEmpty();
        assertThat(row.get("message").asText()).isEqualTo("Follow up on labs");
        assertThat(row.has("warning")).isTrue();
        assertThat(row.get("links").isArray()).isTrue();
    }

    @Test
    @DisplayName("Should set recordsTotal and recordsFiltered to total count")
    void shouldSetRecordCounts_whenResultsExist() throws Exception {
        allowPrivilege("_tickler", "r");

        stubPaginatedResults(42, List.of(createTestTickler(1, "test")));
        addRequestParameter("draw", "5");

        executeAction(action);

        JsonNode json = parseResponse();
        assertThat(json.get("recordsTotal").asInt()).isEqualTo(42);
        assertThat(json.get("recordsFiltered").asInt()).isEqualTo(42);
        assertThat(json.get("draw").asInt()).isEqualTo(5);
    }

    // -- Comments in Response --

    @Test
    @DisplayName("Should include comments as separate map keyed by tickler ID")
    void shouldIncludeCommentsMap_whenTicklerHasComments() throws Exception {
        allowPrivilege("_tickler", "r");

        TicklerListDTO dto = createTestTickler(10, "Needs review");
        TicklerCommentDTO comment = new TicklerCommentDTO(
                100, 10, "Reviewed by specialist", new Date(1700000000000L), "Specialist", "Alice");
        dto.setComments(List.of(comment));
        stubPaginatedResults(1, List.of(dto));

        executeAction(action);

        JsonNode json = parseResponse();
        JsonNode comments = json.get("comments");
        assertThat(comments.has("10")).isTrue();

        JsonNode commentArray = comments.get("10");
        assertThat(commentArray.isArray()).isTrue();
        assertThat(commentArray.size()).isEqualTo(1);
        assertThat(commentArray.get(0).get("creator").asText()).isEqualTo("Specialist, Alice");
        assertThat(commentArray.get(0).get("message").asText()).isEqualTo("Reviewed by specialist");
        assertThat(commentArray.get(0).has("createDate")).isTrue();
    }

    @Test
    @DisplayName("Should omit tickler from comments map when it has no comments")
    void shouldOmitFromCommentsMap_whenNoComments() throws Exception {
        allowPrivilege("_tickler", "r");

        TicklerListDTO dto = createTestTickler(20, "No comments here");
        dto.setComments(Collections.emptyList());
        stubPaginatedResults(1, List.of(dto));

        executeAction(action);

        JsonNode comments = parseResponse().get("comments");
        assertThat(comments.has("20")).isFalse();
    }

    // -- Links in Response --

    @Test
    @DisplayName("Should include links array in data rows")
    void shouldIncludeLinks_whenTicklerHasLinks() throws Exception {
        allowPrivilege("_tickler", "r");

        TicklerListDTO dto = createTestTickler(5, "Lab attached");
        dto.setLinks(List.of(new TicklerLinkDTO(1, 5, "HL7", 999L)));
        stubPaginatedResults(1, List.of(dto));

        executeAction(action);

        JsonNode links = parseResponse().get("data").get(0).get("links");
        assertThat(links.size()).isEqualTo(1);
        assertThat(links.get(0).get("tableName").asText()).isEqualTo("HL7");
        assertThat(links.get(0).get("tableId").asLong()).isEqualTo(999L);
    }

    // -- Paging Parameters --

    @Test
    @DisplayName("Should pass start and length to TicklerManager for pagination")
    void shouldPassPagingParams_whenProvided() throws Exception {
        allowPrivilege("_tickler", "r");

        addRequestParameter("start", "25");
        addRequestParameter("length", "50");

        executeAction(action);

        verify(mockTicklerManager).getTicklerDTOs(
                any(LoggedInInfo.class), any(CustomFilter.class), eq(25), eq(50));
    }

    @Test
    @DisplayName("Should default to start=0 and length=50")
    void shouldUseDefaults_whenPagingParamsMissing() throws Exception {
        allowPrivilege("_tickler", "r");

        executeAction(action);

        verify(mockTicklerManager).getTicklerDTOs(
                any(LoggedInInfo.class), any(CustomFilter.class), eq(0), eq(50));
    }

    @Test
    @DisplayName("Should clamp negative start to zero")
    void shouldClampStart_whenNegative() throws Exception {
        allowPrivilege("_tickler", "r");

        addRequestParameter("start", "-10");

        executeAction(action);

        verify(mockTicklerManager).getTicklerDTOs(
                any(LoggedInInfo.class), any(CustomFilter.class), eq(0), anyInt());
    }

    @Test
    @DisplayName("Should cap length at 500")
    void shouldCapLength_whenExceedsMax() throws Exception {
        allowPrivilege("_tickler", "r");

        addRequestParameter("length", "1000");

        executeAction(action);

        verify(mockTicklerManager).getTicklerDTOs(
                any(LoggedInInfo.class), any(CustomFilter.class), anyInt(), eq(500));
    }

    @Test
    @DisplayName("Should pass zero limit when length is -1 (show all)")
    void shouldShowAll_whenLengthIsNegative() throws Exception {
        allowPrivilege("_tickler", "r");

        when(mockTicklerManager.getNumTicklers(any(LoggedInInfo.class), any(CustomFilter.class)))
                .thenReturn(150);
        addRequestParameter("length", "-1");

        executeAction(action);

        verify(mockTicklerManager).getTicklerDTOs(
                any(LoggedInInfo.class), any(CustomFilter.class), eq(0), eq(0));
    }

    @Test
    @DisplayName("Should default draw to 1 when not provided")
    void shouldDefaultDraw_whenMissing() throws Exception {
        allowPrivilege("_tickler", "r");

        executeAction(action);

        JsonNode json = parseResponse();
        assertThat(json.get("draw").asInt()).isEqualTo(1);
    }

    // -- Filter Parameters --

    @Test
    @DisplayName("Should pass ticklerview status to filter")
    void shouldPassStatus_whenTicklerviewProvided() throws Exception {
        allowPrivilege("_tickler", "r");

        addRequestParameter("ticklerview", "C");

        executeAction(action);

        ArgumentCaptor<CustomFilter> captor = ArgumentCaptor.forClass(CustomFilter.class);
        verify(mockTicklerManager).getNumTicklers(any(LoggedInInfo.class), captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("C");
    }

    @Test
    @DisplayName("Should default ticklerview to Active")
    void shouldDefaultToActive_whenTicklerviewMissing() throws Exception {
        allowPrivilege("_tickler", "r");

        executeAction(action);

        ArgumentCaptor<CustomFilter> captor = ArgumentCaptor.forClass(CustomFilter.class);
        verify(mockTicklerManager).getNumTicklers(any(LoggedInInfo.class), captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("A");
    }

    @ParameterizedTest
    @DisplayName("Should pass sort direction to filter")
    @CsvSource({"asc, asc", "desc, desc", "invalid, desc", "'', desc"})
    void shouldPassSortDirection_whenOrderDirProvided(String input, String expected) throws Exception {
        allowPrivilege("_tickler", "r");

        if (input != null && !input.isEmpty()) {
            addRequestParameter("order[0][dir]", input);
        }

        executeAction(action);

        ArgumentCaptor<CustomFilter> captor = ArgumentCaptor.forClass(CustomFilter.class);
        verify(mockTicklerManager).getNumTicklers(any(LoggedInInfo.class), captor.capture());
        assertThat(captor.getValue().getSort_order()).isEqualTo(expected);
    }

    @Test
    @DisplayName("Should clear provider filters when demographic_no is set")
    void shouldClearProviderFilters_whenDemographicSpecified() throws Exception {
        allowPrivilege("_tickler", "r");

        addRequestParameter("demographic_no", "1001");
        addRequestParameter("providerview", "999998");
        addRequestParameter("assignedTo", "999999");
        addRequestParameter("mrpview", "999997");

        executeAction(action);

        ArgumentCaptor<CustomFilter> captor = ArgumentCaptor.forClass(CustomFilter.class);
        verify(mockTicklerManager).getNumTicklers(any(LoggedInInfo.class), captor.capture());
        CustomFilter filter = captor.getValue();
        assertThat(filter.getDemographicNo()).isEqualTo("1001");
        assertThat(filter.getMrp()).isNull();
    }

    @Test
    @DisplayName("Should handle non-numeric parameter values gracefully")
    void shouldHandleGracefully_whenParamsAreNonNumeric() throws Exception {
        allowPrivilege("_tickler", "r");

        addRequestParameter("draw", "abc");
        addRequestParameter("start", "xyz");
        addRequestParameter("length", "!!!");

        executeAction(action);

        JsonNode json = parseResponse();
        // Should fall back to defaults: draw=1, start=0, length=50
        assertThat(json.get("draw").asInt()).isEqualTo(1);
        verify(mockTicklerManager).getTicklerDTOs(
                any(LoggedInInfo.class), any(CustomFilter.class), eq(0), eq(50));
    }

    // -- Invalid Date Handling --

    @Test
    @DisplayName("Should return 400 JSON error when date format is invalid")
    void shouldReturn400_whenDateFormatInvalid() throws Exception {
        allowPrivilege("_tickler", "r");

        addRequestParameter("xml_vdate", "not-a-date");

        executeAction(action);

        assertThat(getMockResponse().getStatus()).isEqualTo(400);
        JsonNode json = parseResponse();
        assertThat(json.get("error").asText()).contains("date");
    }

    // -- Warning Flag --

    @Test
    @DisplayName("Should set warning=false when service date is in the future")
    void shouldNotWarn_whenServiceDateIsFuture() throws Exception {
        allowPrivilege("_tickler", "r");

        TicklerListDTO dto = createTestTickler(1, "future tickler");
        // Service date far in the future — no warn period can trigger
        dto.setServiceDate(new Date(System.currentTimeMillis() + 365L * 24 * 60 * 60 * 1000));
        stubPaginatedResults(1, List.of(dto));

        executeAction(action);

        JsonNode row = parseResponse().get("data").get(0);
        assertThat(row.get("warning").asBoolean()).isFalse();
    }

    // -- Null/Empty Dates --

    @Test
    @DisplayName("Should handle null serviceDate and createDate gracefully")
    void shouldReturnEmptyStrings_whenDatesAreNull() throws Exception {
        allowPrivilege("_tickler", "r");

        TicklerListDTO dto = createTestTickler(1, "no dates");
        dto.setServiceDate(null);
        dto.setCreateDate(null);
        stubPaginatedResults(1, List.of(dto));

        executeAction(action);

        JsonNode row = parseResponse().get("data").get(0);
        assertThat(row.get("serviceDate").asText()).isEmpty();
        assertThat(row.get("createDate").asText()).isEmpty();
    }

    @Test
    @DisplayName("Should handle null comment updateDate")
    void shouldReturnEmptyCreateDate_whenCommentUpdateDateIsNull() throws Exception {
        allowPrivilege("_tickler", "r");

        TicklerListDTO dto = createTestTickler(1, "test");
        TicklerCommentDTO comment = new TicklerCommentDTO(1, 1, "comment", null, "Last", "First");
        dto.setComments(List.of(comment));
        stubPaginatedResults(1, List.of(dto));

        executeAction(action);

        JsonNode commentNode = parseResponse().get("comments").get("1").get(0);
        assertThat(commentNode.get("createDate").asText()).isEmpty();
    }

    // -- Helpers --

    private TicklerListDTO createTestTickler(int id, String message) {
        return new TicklerListDTO(
                id, message, new Date(), new Date(),
                Tickler.STATUS.A, Tickler.PRIORITY.Normal,
                1001, "Smith", "John",
                "Doctor", "Jane",
                "Nurse", "Bob");
    }

    private void stubPaginatedResults(int total, List<TicklerListDTO> ticklers) {
        when(mockTicklerManager.getNumTicklers(any(LoggedInInfo.class), any(CustomFilter.class)))
                .thenReturn(total);
        when(mockTicklerManager.getTicklerDTOs(any(LoggedInInfo.class), any(CustomFilter.class), anyInt(), anyInt()))
                .thenReturn(ticklers);
        when(mockTicklerManager.getTicklerDTOs(any(LoggedInInfo.class), any(CustomFilter.class)))
                .thenReturn(ticklers);
    }

    private JsonNode parseResponse() throws Exception {
        return mapper.readTree(getMockResponse().getContentAsString());
    }

    private void injectField(String fieldName, Object value) throws Exception {
        java.lang.reflect.Field field = TicklerList2Action.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(action, value);
    }
}
