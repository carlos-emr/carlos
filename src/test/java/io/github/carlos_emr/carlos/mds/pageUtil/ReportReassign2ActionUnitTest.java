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
package io.github.carlos_emr.carlos.mds.pageUtil;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PatientLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingFavoritesDao;
import io.github.carlos_emr.carlos.commn.dao.QueueDocumentLinkDao;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingFavorite;
import io.github.carlos_emr.carlos.lab.ca.on.CommonLabResultData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Lab Forward ({@code ReportReassign2Action}, issue #4558): the action behind
 * {@code oscarMDS/ReportReassign} and the four lab pages' {@code .../Forward} routes.
 *
 * <p>Every caller posts the one JSON contract the Inbox dialog uses: {@code flaggedLabs}
 * {@code {"files":["id:TYPE"]}}, {@code selectedProviders} {@code {"providers":[...]}} and,
 * when the picker showed the favourites list, {@code selectedFavorites}
 * {@code {"favorites":[...]}}. These tests pin what the action routes, what it refuses before
 * any write, that a missing favourites list leaves the stored favourites alone, and where a
 * page post is sent back to (its own lab display, or the patient lab list).
 */
@Tag("unit")
@Tag("lab")
@DisplayName("ReportReassign2Action (lab Forward)")
class ReportReassign2ActionUnitTest extends CarlosUnitTestBase {

    private static final String CONTEXT = "/carlos";
    private static final String SENDER = "999998";

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final ProviderLabRoutingFavoritesDao favorites = mock(ProviderLabRoutingFavoritesDao.class);

    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<CommonLabResultData> routing;

    @BeforeEach
    void fixture() {
        // CommonLabResultData's static initialiser looks these up; the static mock needs it initialised.
        registerMock(SecurityInfoManager.class, security);
        registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(ProviderLabRoutingDao.class, mock(ProviderLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        routing = mockStatic(CommonLabResultData.class);
        routing.when(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class))).thenReturn(true);
        when(info.getLoggedInProviderNo()).thenReturn(SENDER);
        when(security.hasPrivilege(info, "_lab", "w", null)).thenReturn(true);
        request.setMethod("POST");
        request.setContextPath(CONTEXT);
        route("/lab/CA/ON/Forward");
    }

    @AfterEach
    void release() {
        if (routing != null) routing.close();
        if (sessions != null) sessions.close();
        if (servlet != null) servlet.close();
    }

    private void route(String path) {
        request.setRequestURI(CONTEXT + path);
        request.setServletPath(path);
    }

    private void post(String files, String providers, String favoritesJson) {
        if (files != null) request.setParameter("flaggedLabs", files);
        if (providers != null) request.setParameter("selectedProviders", providers);
        if (favoritesJson != null) request.setParameter("selectedFavorites", favoritesJson);
    }

    private ReportReassign2Action action() {
        return new ReportReassign2Action(security, favorites);
    }

    private JsonNode json() throws Exception {
        return new ObjectMapper().readTree(response.getContentAsString());
    }

    @SuppressWarnings("unchecked")
    private ArrayList<String[]> routedLabs() {
        ArgumentCaptor<ArrayList<String[]>> labs = ArgumentCaptor.forClass(ArrayList.class);
        ArgumentCaptor<String[]> providers = ArgumentCaptor.forClass(String[].class);
        routing.verify(() -> CommonLabResultData.updateLabRouting(labs.capture(), providers.capture()));
        return labs.getValue();
    }

    private String[] routedProviders() {
        ArgumentCaptor<String[]> providers = ArgumentCaptor.forClass(String[].class);
        routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), providers.capture()));
        return providers.getValue();
    }

    private static ProviderLabRoutingFavorite favorite(int id, String to) {
        ProviderLabRoutingFavorite favorite = new ProviderLabRoutingFavorite();
        favorite.setId(id);
        favorite.setProvider_no(SENDER);
        favorite.setRoute_to_provider_no(to);
        return favorite;
    }

    @Nested
    @DisplayName("a lab page's Forward post")
    class PagePost {

        @Test
        @DisplayName("routes the lab to the chosen provider and returns to the CML display")
        void shouldRouteAndRedirectToCmlDisplay_whenCmlPagePostsSelection() throws Exception {
            post("{\"files\":[\"1234:CML\"]}", "{\"providers\":[\"100001\"]}", "{\"favorites\":[]}");

            String result = action().execute();

            assertThat(result).isEqualTo("none");
            assertThat(routedLabs()).hasSize(1);
            assertThat(routedLabs().get(0)).containsExactly("1234", "CML");
            assertThat(routedProviders()).containsExactly("100001");
            assertThat(response.getRedirectedUrl())
                    .isEqualTo(CONTEXT + "/lab/CA/ON/ViewCMLDisplay?segmentID=1234&providerNo=" + SENDER);
        }

        @ParameterizedTest(name = "{0} returns to {2}")
        @CsvSource({
                "/lab/CA/ON/Forward,   CML, /lab/CA/ON/ViewCMLDisplay",
                "/oscarMDS/Forward,    MDS, /oscarMDS/ViewSegmentDisplay",
                "/lab/CA/BC/Forward,   BCP, /lab/CA/BC/ViewLabDisplay",
                "/lab/CA/ALL/Forward,  HL7, /lab/CA/ALL/ViewLabDisplay"
        })
        @DisplayName("each lab page's route sends the page back to its own display")
        void shouldRedirectToOwnDisplay_forEachLabPageRoute(String route, String type, String display) throws Exception {
            route(route);
            request.setParameter("searchProviderNo", "100002");
            post("{\"files\":[\"77:" + type + "\"]}", "{\"providers\":[\"100001\"]}", null);

            action().execute();

            // A route the legacy code built as "<request path>&providerNo=..." (no '?') is not a route at all.
            assertThat(response.getRedirectedUrl())
                    .isEqualTo(CONTEXT + display + "?segmentID=77&providerNo=" + SENDER + "&searchProviderNo=100002");
        }

        @Test
        @DisplayName("the patient lab list's post returns to that list with its filters")
        void shouldRedirectToPatientLabList_whenReportReassignIsPostedByTheList() throws Exception {
            route("/oscarMDS/ReportReassign");
            request.setParameter("searchProviderNo", "100002");
            request.setParameter("status", "N");
            request.setParameter("lname", "FAKE O'Neil & Co");
            request.setParameter("fname", "Pat");
            request.setParameter("hnum", "0000");
            post("{\"files\":[\"5:MDS\",\"6:CML\"]}", "{\"providers\":[\"100001\",\"100003\"]}", null);

            action().execute();

            assertThat(routedLabs()).extracting(lab -> lab[0] + ":" + lab[1]).containsExactly("5:MDS", "6:CML");
            assertThat(routedProviders()).containsExactly("100001", "100003");
            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT + "/lab/ViewDemographicLab?providerNo=" + SENDER
                    + "&searchProviderNo=100002&status=N&lname=FAKE%20O%27Neil%20%26%20Co&fname=Pat&hnum=0000");
        }

        @Test
        @DisplayName("an unknown request path never becomes a redirect target")
        void shouldFallBackToPatientLabList_whenRequestPathIsNotAForwardRoute() throws Exception {
            request.setRequestURI(CONTEXT + "//evil.example/lab/CA/ON/Forward");
            post("{\"files\":[\"1234:CML\"]}", "{\"providers\":[\"100001\"]}", null);

            action().execute();

            assertThat(response.getRedirectedUrl()).isEqualTo(CONTEXT + "/lab/ViewDemographicLab?providerNo=" + SENDER);
        }
    }

    @Nested
    @DisplayName("the Inbox's AJAX post")
    class AjaxPost {

        @BeforeEach
        void ajax() {
            route("/oscarMDS/ReportReassign");
            request.setParameter("ajax", "yes");
        }

        @Test
        @DisplayName("answers JSON naming the routed labs, never the providers")
        void shouldAnswerRoutedFiles_whenSelectionIsRouted() throws Exception {
            post("{\"files\":[\"42:DOC\",\"43:HL7\"]}", "{\"providers\":[\"100001\"]}", "{\"favorites\":[\"100001\"]}");
            when(favorites.findFavorites(SENDER)).thenReturn(List.of());

            String result = action().execute();

            assertThat(result).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.getContentType()).startsWith("application/json");
            assertThat(json().path("success").asBoolean()).isTrue();
            assertThat(json().path("files")).hasSize(2);
            assertThat(json().path("files").get(0).asText()).isEqualTo("42:DOC");
            assertThat(json().path("files").get(1).asText()).isEqualTo("43:HL7");
            assertThat(response.getRedirectedUrl()).isNull();
        }

        @Test
        @DisplayName("a favourites save that fails still answers the routed labs as a success")
        void shouldAnswerSuccess_whenOnlyFavoritesSaveFails() throws Exception {
            when(favorites.findFavorites(SENDER)).thenThrow(new IllegalStateException("favourites table unavailable"));
            post("{\"files\":[\"42:HL7\"]}", "{\"providers\":[\"100001\"]}", "{\"favorites\":[\"100001\"]}");

            action().execute();

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(json().path("success").asBoolean()).isTrue();
            assertThat(json().path("files").get(0).asText()).isEqualTo("42:HL7");
        }

        @Test
        @DisplayName("a routing failure is not reported as success")
        void shouldAnswerFailure_whenRoutingFails() throws Exception {
            routing.when(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class))).thenReturn(false);
            post("{\"files\":[\"42:HL7\"]}", "{\"providers\":[\"100001\"]}", null);

            action().execute();

            assertThat(response.getStatus()).isEqualTo(500);
            assertThat(json().path("success").asBoolean()).isFalse();
        }

        @Test
        @DisplayName("an invalid selection is refused as JSON with 400")
        void shouldAnswerBadRequest_whenSelectionIsInvalid() throws Exception {
            post("1234", "100001", null);

            action().execute();

            assertThat(response.getStatus()).isEqualTo(400);
            assertThat(json().path("success").asBoolean()).isFalse();
            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)), never());
        }
    }

    @Nested
    @DisplayName("an invalid selection")
    class InvalidSelection {

        @Test
        @DisplayName("bare id lists (the pre-#4558 page form) route nothing and change nothing")
        void shouldRefuseWithoutWrites_whenPagePostsBareIdLists() throws Exception {
            post("1234", "100001", null);
            request.setParameter("favorites", "100001");

            String result = action().execute();

            assertThat(result).isEqualTo("none");
            assertThat(response.getStatus()).isEqualTo(400);
            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)), never());
            verifyNoInteractions(favorites);
        }

        @Test
        @DisplayName("a provider list that does not parse is never replaced by the favourites list")
        void shouldNotRouteToFavorites_whenProviderListDoesNotParse() throws Exception {
            // Before #4558 one shared array carried favourites, then providers, then labs: a provider
            // list that failed to parse left the favourites in it, and the lab went to them.
            post("{\"files\":[\"1234:CML\"]}", "100001", "{\"favorites\":[\"100009\"]}");

            action().execute();

            assertThat(response.getStatus()).isEqualTo(400);
            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)), never());
            verifyNoInteractions(favorites);
        }

        @Test
        @DisplayName("a lab list that does not parse is never replaced by the provider list")
        void shouldNotRouteProvidersAsLabs_whenLabListDoesNotParse() throws Exception {
            post("1234", "{\"providers\":[\"100001\"]}", null);

            action().execute();

            assertThat(response.getStatus()).isEqualTo(400);
            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)), never());
        }

        @ParameterizedTest(name = "flaggedLabs={0}")
        @ValueSource(strings = {
                "{\"files\":[]}",
                "{\"files\":[\"1234\"]}",
                "{\"files\":[\"abc:CML\"]}",
                "{\"files\":[\"0:CML\"]}",
                "{\"files\":[\"-4:CML\"]}",
                "{\"files\":[\"99999999999:CML\"]}",
                "{\"files\":[\"12:Spire\"]}",
                "{\"files\":[\"12:CML:extra\"]}",
                "{\"files\":[12]}",
                "{\"files\":\"12:CML\"}",
                "[\"12:CML\"]"
        })
        @DisplayName("a lab entry that is not a positive id and a forwardable type is refused")
        void shouldRefuse_whenLabEntryIsMalformed(String files) throws Exception {
            post(files, "{\"providers\":[\"100001\"]}", null);

            action().execute();

            assertThat(response.getStatus()).isEqualTo(400);
            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)), never());
        }

        @ParameterizedTest(name = "selectedProviders={0}")
        @ValueSource(strings = {
                "{\"providers\":[]}",
                "{\"providers\":[\"\"]}",
                "{\"providers\":[\"1000011\"]}",
                "{\"providers\":[\"10 01\"]}",
                "{\"providers\":[\"<b>\"]}",
                "{\"providers\":[100001]}",
                "{\"other\":[\"100001\"]}"
        })
        @DisplayName("a provider entry that is not a provider number is refused")
        void shouldRefuse_whenProviderEntryIsMalformed(String providers) throws Exception {
            post("{\"files\":[\"12:CML\"]}", providers, null);

            action().execute();

            assertThat(response.getStatus()).isEqualTo(400);
            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)), never());
        }

        @Test
        @DisplayName("a favourites list that does not parse refuses the whole request")
        void shouldRefuseWithoutRouting_whenFavoritesListDoesNotParse() throws Exception {
            post("{\"files\":[\"12:CML\"]}", "{\"providers\":[\"100001\"]}", "100001,100002");

            action().execute();

            assertThat(response.getStatus()).isEqualTo(400);
            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)), never());
            verifyNoInteractions(favorites);
        }

        @Test
        @DisplayName("a repeated selection parameter is ambiguous and refused")
        void shouldRefuse_whenSelectionParameterIsRepeated() throws Exception {
            request.setParameter("flaggedLabs", "{\"files\":[\"12:CML\"]}", "{\"files\":[\"13:CML\"]}");
            request.setParameter("selectedProviders", "{\"providers\":[\"100001\"]}");

            action().execute();

            assertThat(response.getStatus()).isEqualTo(400);
            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)), never());
        }
    }

    @Nested
    @DisplayName("forwarding favourites")
    class Favorites {

        @Test
        @DisplayName("a post without a favourites list leaves the stored favourites alone")
        void shouldLeaveFavoritesUnchanged_whenNoFavoritesListIsPosted() throws Exception {
            post("{\"files\":[\"12:CML\"]}", "{\"providers\":[\"100001\"]}", null);

            action().execute();

            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)));
            verifyNoInteractions(favorites);
        }

        @Test
        @DisplayName("a posted list adds the new favourites and removes the dropped ones by id")
        void shouldSyncFavorites_toThePostedList() throws Exception {
            when(favorites.findFavorites(SENDER)).thenReturn(List.of(favorite(11, "100001"), favorite(12, "100002")));
            post("{\"files\":[\"12:CML\"]}", "{\"providers\":[\"100001\"]}", "{\"favorites\":[\"100002\",\"100003\"]}");

            action().execute();

            verify(favorites).remove(11);
            verify(favorites, never()).remove(12);
            ArgumentCaptor<ProviderLabRoutingFavorite> added = ArgumentCaptor.forClass(ProviderLabRoutingFavorite.class);
            verify(favorites).persist(added.capture());
            assertThat(added.getValue().getProvider_no()).isEqualTo(SENDER);
            assertThat(added.getValue().getRoute_to_provider_no()).isEqualTo("100003");
        }

        @Test
        @DisplayName("a favourites save that fails does not report the forward that happened as failed")
        void shouldStillRedirect_whenFavoritesSaveFailsAfterRouting() throws Exception {
            when(favorites.findFavorites(SENDER)).thenThrow(new IllegalStateException("favourites table unavailable"));
            post("{\"files\":[\"12:CML\"]}", "{\"providers\":[\"100001\"]}", "{\"favorites\":[\"100002\"]}");

            action().execute();

            routing.verify(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class)));
            assertThat(response.getRedirectedUrl())
                    .isEqualTo(CONTEXT + "/lab/CA/ON/ViewCMLDisplay?segmentID=12&providerNo=" + SENDER);
        }

        @Test
        @DisplayName("a forward whose routing fails leaves the stored favourites alone")
        void shouldNotTouchFavorites_whenRoutingFails() throws Exception {
            routing.when(() -> CommonLabResultData.updateLabRouting(any(ArrayList.class), any(String[].class))).thenReturn(false);
            post("{\"files\":[\"12:CML\"]}", "{\"providers\":[\"100001\"]}", "{\"favorites\":[]}");

            action().execute();

            assertThat(response.getStatus()).isEqualTo(500);
            verifyNoInteractions(favorites);
        }

        @Test
        @DisplayName("an empty posted list removes every favourite (the user cleared the list)")
        void shouldRemoveEveryFavorite_whenPostedListIsEmpty() throws Exception {
            when(favorites.findFavorites(SENDER)).thenReturn(List.of(favorite(11, "100001"), favorite(12, "100002")));
            post("{\"files\":[\"12:CML\"]}", "{\"providers\":[\"100001\"]}", "{\"favorites\":[]}");

            action().execute();

            verify(favorites).remove(11);
            verify(favorites).remove(12);
            verify(favorites, never()).persist(any());
        }
    }

    @Nested
    @DisplayName("request guards")
    class Guards {

        @Test
        @DisplayName("a provider without lab write rights is refused before anything is read")
        void shouldThrowSecurityException_whenLabWriteIsMissing() throws Exception {
            when(security.hasPrivilege(info, "_lab", "w", null)).thenReturn(false);
            post("{\"files\":[\"12:CML\"]}", "{\"providers\":[\"100001\"]}", null);

            assertThatThrownBy(() -> action().execute())
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_lab)");
            routing.verifyNoInteractions();
            verifyNoInteractions(favorites);
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
        @DisplayName("only POST forwards")
        void shouldRejectBeforeRouting_forNonPostMethods(String method) throws Exception {
            request.setMethod(method);
            post("{\"files\":[\"12:CML\"]}", "{\"providers\":[\"100001\"]}", null);

            action().execute();

            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            routing.verifyNoInteractions();
            verifyNoInteractions(favorites);
        }
    }
}
