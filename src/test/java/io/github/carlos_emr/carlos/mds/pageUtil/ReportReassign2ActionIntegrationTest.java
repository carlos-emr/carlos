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

import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingFavoritesDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingFavorite;
import io.github.carlos_emr.carlos.commn.model.ProviderLabRoutingModel;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Lab Forward against the real routing code and H2 (issue #4558): {@code ReportReassign2Action}
 * with the real {@code CommonLabResultData.updateLabRouting}, {@code ProviderLabRouting} and
 * {@code ProviderLabRoutingFavoritesDao}. Only the servlet context, the session lookup and the
 * privilege decision are stubbed.
 *
 * <p>It pins what the unit test cannot: that a lab page's post in the fixed contract really writes
 * the recipient's {@code providerLabRouting} row (status N) while the sender's stays as it was, that
 * the request the pages sent before the fix writes nothing, and that the sender's stored
 * favourites follow the posted list and survive a post that carries none.
 */
@Tag("integration")
@Tag("lab")
@DisplayName("ReportReassign2Action (lab Forward) against H2")
class ReportReassign2ActionIntegrationTest extends CarlosTestBase {

    private static final int MDS_LAB = 9845581;
    private static final int DOCUMENT = 9845582;
    private static final String SENDER = "984551";
    private static final String RECIPIENT = "984552";
    private static final String FAVORITE = "984553";

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager em;
    @Autowired
    private ProviderLabRoutingDao routes;
    @Autowired
    private ProviderLabRoutingFavoritesDao favoritesDao;

    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final MockHttpServletResponse response = new MockHttpServletResponse();
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;

    @BeforeEach
    void setUpForward() {
        em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
        for (String providerNo : new String[]{SENDER, RECIPIENT, FAVORITE}) {
            Provider provider = new Provider(providerNo, "Synthetic", "doctor", "F", "", "Forward" + providerNo);
            provider.setStatus("1");
            em.persist(provider);
        }
        em.persist(new ProviderLabRoutingModel(SENDER, MDS_LAB, "N", "", new Date(), "MDS"));
        em.persist(new ProviderLabRoutingModel(SENDER, DOCUMENT, "N", "", new Date(), "DOC"));
        em.flush();

        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        when(info.getLoggedInProviderNo()).thenReturn(SENDER);
        when(security.hasPrivilege(info, "_lab", "w", null)).thenReturn(true);
        request.setMethod("POST");
        request.setContextPath("/carlos");
    }

    @AfterEach
    void releaseStatics() {
        if (sessions != null) sessions.close();
        if (servlet != null) servlet.close();
    }

    private String forward(String route, String files, String providers, String favorites) {
        request.setRequestURI("/carlos" + route);
        if (files != null) request.setParameter("flaggedLabs", files);
        if (providers != null) request.setParameter("selectedProviders", providers);
        if (favorites != null) request.setParameter("selectedFavorites", favorites);
        String result = new ReportReassign2Action(security, favoritesDao).execute();
        em.flush();
        em.clear();
        return result;
    }

    private String statusOf(int lab, String type, String providerNo) {
        return routes.findAllLabRoutingByIdandType(lab, type).stream()
                .filter(row -> providerNo.equals(row.getProviderNo()))
                .map(ProviderLabRoutingModel::getStatus)
                .reduce((a, b) -> a + "," + b).orElse(null);
    }

    private void favorite(String to) {
        ProviderLabRoutingFavorite favorite = new ProviderLabRoutingFavorite();
        favorite.setProvider_no(SENDER);
        favorite.setRoute_to_provider_no(to);
        favoritesDao.persist(favorite);
    }

    @Test
    @DisplayName("an MDS page's post routes the lab to the recipient as New and leaves the sender's row")
    void shouldRouteMdsLabToRecipient_whenMdsPagePostsSelection() {
        String result = forward("/oscarMDS/Forward", "{\"files\":[\"" + MDS_LAB + ":MDS\"]}",
                "{\"providers\":[\"" + RECIPIENT + "\"]}", "{\"favorites\":[]}");

        assertThat(result).isEqualTo("none");
        assertThat(statusOf(MDS_LAB, "MDS", RECIPIENT)).isEqualTo("N");
        assertThat(statusOf(MDS_LAB, "MDS", SENDER)).isEqualTo("N");
        assertThat(response.getRedirectedUrl())
                .isEqualTo("/carlos/oscarMDS/ViewSegmentDisplay?segmentID=" + MDS_LAB + "&providerNo=" + SENDER);
    }

    @Test
    @DisplayName("the pre-fix page form (bare id lists) writes no routing row and keeps every favourite")
    void shouldWriteNothing_whenPagePostsBareIdLists() {
        favorite(FAVORITE);
        em.flush();

        forward("/oscarMDS/Forward", String.valueOf(MDS_LAB), RECIPIENT, null);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(statusOf(MDS_LAB, "MDS", RECIPIENT)).isNull();
        // Before #4558 this post deleted every favourite, because it carried no selectedFavorites.
        assertThat(favoritesDao.findFavorites(SENDER)).extracting(ProviderLabRoutingFavorite::getRoute_to_provider_no)
                .containsExactly(FAVORITE);
    }

    @Test
    @DisplayName("the Inbox's AJAX post of a document routes it and makes the stored favourites match the list")
    void shouldRouteDocumentAndSyncFavorites_whenInboxPostsFavoritesList() {
        favorite(FAVORITE);
        em.flush();
        request.setParameter("ajax", "yes");

        forward("/oscarMDS/ReportReassign", "{\"files\":[\"" + DOCUMENT + ":DOC\"]}",
                "{\"providers\":[\"" + RECIPIENT + "\"]}", "{\"favorites\":[\"" + RECIPIENT + "\"]}");

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(statusOf(DOCUMENT, "DOC", RECIPIENT)).isEqualTo("N");
        assertThat(favoritesDao.findFavorites(SENDER)).extracting(ProviderLabRoutingFavorite::getRoute_to_provider_no)
                .containsExactly(RECIPIENT);
    }

    @Test
    @DisplayName("a post without a favourites list routes and leaves the stored favourites as they were")
    void shouldKeepFavorites_whenPostCarriesNoFavoritesList() {
        favorite(FAVORITE);
        em.flush();

        forward("/oscarMDS/Forward", "{\"files\":[\"" + MDS_LAB + ":MDS\"]}", "{\"providers\":[\"" + RECIPIENT + "\"]}", null);

        assertThat(statusOf(MDS_LAB, "MDS", RECIPIENT)).isEqualTo("N");
        assertThat(favoritesDao.findFavorites(SENDER)).extracting(ProviderLabRoutingFavorite::getRoute_to_provider_no)
                .containsExactly(FAVORITE);
    }
}
