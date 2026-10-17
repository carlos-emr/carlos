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

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Administration ▸ Satellite-sites Admin (issue #4700): adding, opening and saving a site. The form's
 * {@code site.*} fields bind to {@link SitesManage2Action#getSite()}; the tests set that site the way the
 * parameters interceptor would.
 *
 * <p>Isolated because two tests set {@code multioffice.formalize.provider.id} on the shared
 * {@link CarlosProperties} singleton.</p>
 */
@Isolated
@Tag("unit")
@Tag("web")
@DisplayName("SitesManage2Action")
class SitesManage2ActionUnitTest extends CarlosUnitTestBase {

    private static final String FORMALIZE = "multioffice.formalize.provider.id";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SiteDao siteDao;
    private SecurityInfoManager security;
    private SitesManage2Action action;
    private Object formalizeBefore;

    @BeforeEach
    void prepare() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        LoggedInInfo user = org.mockito.Mockito.mock(LoggedInInfo.class);
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", user);
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
        siteDao = createAndRegisterMock(SiteDao.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), eq("_admin"), eq("w"), any())).thenReturn(true);
        // Hashtable read: getProperty(String) would log a missing-key warning.
        formalizeBefore = CarlosProperties.getInstance().get(FORMALIZE);
        CarlosProperties.getInstance().setProperty(FORMALIZE, "false");
        action = new SitesManage2Action();
    }

    @AfterEach
    void release() {
        if (formalizeBefore == null) {
            CarlosProperties.getInstance().remove(FORMALIZE);
        } else {
            CarlosProperties.getInstance().put(FORMALIZE, formalizeBefore);
        }
        ActionContext.clear();
    }

    private static Site site(Integer id, String name, String shortName, String bgColor) {
        Site site = new Site();
        site.setSiteId(id);
        site.setName(name);
        site.setShortName(shortName);
        site.setBgColor(bgColor);
        return site;
    }

    private static Site stored() {
        Site stored = site(7, "FAKE North", "FNORTH", "#C0FFEE");
        stored.setPhone("555-0100");
        stored.setStatus((byte) 1);
        stored.setProvince("BC");
        stored.setSiteLogoId(12);
        stored.setSiteUrl("https://fake-site.example");
        stored.setProviderIdFrom(700);
        stored.setProviderIdTo(799);
        return stored;
    }

    @Test
    @DisplayName("should store a new site when its name, short name and colour are posted")
    void shouldStoreNewSite_whenRequiredFieldsPosted() throws Exception {
        request.setParameter("method", "save");
        Site posted = site(null, "FAKE East", "FEAST", "#ABCDEF");
        action.setSite(posted);
        when(siteDao.getAllSites()).thenReturn(java.util.List.of(posted));

        assertThat(action.execute()).isEqualTo("list");

        verify(siteDao).save(posted);
        verify(siteDao, never()).getById(any());
        assertThat(request.getAttribute("sites")).isEqualTo(java.util.List.of(posted));
    }

    @Test
    @DisplayName("should return to the form naming each missing field, and store nothing")
    void shouldReturnToForm_whenRequiredFieldsMissing() throws Exception {
        request.setParameter("method", "save");
        action.setSite(site(7, " ", "", null));

        assertThat(action.execute()).isEqualTo("details");

        assertThat(action.getMissingFields()).containsExactly("admin.sitesAdminDetail.label.siteName",
                "admin.sitesAdminDetail.label.shortName", "admin.sitesAdminDetail.label.themeColor");
        assertThat(action.getSite().getSiteId()).isEqualTo(7);
        verify(siteDao, never()).save(any());
    }

    @Test
    @DisplayName("should return to an empty form when a save posts no site fields")
    void shouldReturnToForm_whenNoSiteFieldsPosted() throws Exception {
        request.setParameter("method", "save");

        assertThat(action.execute()).isEqualTo("details");

        assertThat(action.getSite()).isNotNull();
        assertThat(action.getMissingFields()).hasSize(3);
        verify(siteDao, never()).save(any());
    }

    @Test
    @DisplayName("should update the stored site and keep the logo, URL and provider-id range the form does not carry")
    void shouldKeepStoredFields_whenUpdatingSite() throws Exception {
        request.setParameter("method", "save");
        when(siteDao.getById(7)).thenReturn(stored());
        Site posted = site(7, "FAKE North renamed", "FNORTH", "#C0FFEE");
        posted.setPhone("555-0199");
        action.setSite(posted);

        assertThat(action.execute()).isEqualTo("list");

        ArgumentCaptor<Site> saved = ArgumentCaptor.forClass(Site.class);
        verify(siteDao).save(saved.capture());
        assertThat(saved.getValue().getSiteId()).isEqualTo(7);
        assertThat(saved.getValue().getName()).isEqualTo("FAKE North renamed");
        assertThat(saved.getValue().getPhone()).isEqualTo("555-0199");
        assertThat(saved.getValue().getSiteLogoId()).isEqualTo(12);
        assertThat(saved.getValue().getSiteUrl()).isEqualTo("https://fake-site.example");
        assertThat(saved.getValue().getProviderIdFrom()).isEqualTo(700);
        assertThat(saved.getValue().getProviderIdTo()).isEqualTo(799);
    }

    @Test
    @DisplayName("should take the provider-id range from the form while provider-id formalizing shows it")
    void shouldTakePostedRange_whenFormalizingShowsIt() throws Exception {
        CarlosProperties.getInstance().setProperty(FORMALIZE, "true");
        request.setParameter("method", "save");
        when(siteDao.getById(7)).thenReturn(stored());
        Site posted = site(7, "FAKE North", "FNORTH", "#C0FFEE");
        posted.setProviderIdFrom(800);
        posted.setProviderIdTo(899);
        action.setSite(posted);

        assertThat(action.execute()).isEqualTo("list");

        ArgumentCaptor<Site> saved = ArgumentCaptor.forClass(Site.class);
        verify(siteDao).save(saved.capture());
        assertThat(saved.getValue().getProviderIdFrom()).isEqualTo(800);
        assertThat(saved.getValue().getProviderIdTo()).isEqualTo(899);
        assertThat(saved.getValue().getSiteLogoId()).isEqualTo(12);
    }

    @Test
    @DisplayName("should answer 404 and store nothing when the posted site id is unknown")
    void shouldRefuseSave_whenSiteIdUnknown() throws Exception {
        request.setParameter("method", "save");
        action.setSite(site(99, "FAKE West", "FWEST", "#123456"));

        assertThat(action.execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(404);
        verify(siteDao, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT"})
    @DisplayName("should refuse a save that is not a POST, before touching the sites")
    void shouldRefuseSave_whenNotPost(String verb) throws Exception {
        request.setMethod(verb);
        request.setParameter("method", "save");
        action.setSite(site(null, "FAKE East", "FEAST", "#ABCDEF"));

        assertThat(action.execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(siteDao);
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "update"})
    @DisplayName("should open the site in the form with its stored values")
    void shouldOpenStoredSite_whenEditRequested(String method) throws Exception {
        request.setMethod("edit".equals(method) ? "GET" : "POST");
        request.setParameter("method", method);
        request.setParameter("siteId", "7");
        Site stored = stored();
        when(siteDao.getById(7)).thenReturn(stored);

        assertThat(action.execute()).isEqualTo("details");

        assertThat(action.getSite()).isSameAs(stored);
        verify(siteDao, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "7 OR 1=1"})
    @DisplayName("should answer 400 when the site id to open is not a number")
    void shouldRefuseOpen_whenSiteIdInvalid(String siteId) throws Exception {
        request.setMethod("GET");
        request.setParameter("method", "edit");
        request.setParameter("siteId", siteId);

        assertThat(action.execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(siteDao);
    }

    @Test
    @DisplayName("should answer 404 when the site to open does not exist")
    void shouldRefuseOpen_whenSiteUnknown() throws Exception {
        request.setMethod("GET");
        request.setParameter("method", "edit");
        request.setParameter("siteId", "99");

        assertThat(action.execute()).isEqualTo("none");

        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(action.getSite()).isNull();
    }

    @Test
    @DisplayName("should open an empty form for Add New Site")
    void shouldOpenEmptyForm_whenAddRequested() throws Exception {
        request.setParameter("method", "add");

        assertThat(action.execute()).isEqualTo("details");

        assertThat(action.getSite()).isNotNull();
        assertThat(action.getSite().getSiteId()).isNull();
        verifyNoInteractions(siteDao);
    }

    @Test
    @DisplayName("should refuse every method without administration write rights")
    void shouldThrowSecurityException_whenAdminWriteMissing() {
        when(security.hasPrivilege(any(), eq("_admin"), eq("w"), any())).thenReturn(false);
        request.setParameter("method", "save");
        action.setSite(site(null, "FAKE East", "FEAST", "#ABCDEF"));

        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_admin)");
        verifyNoInteractions(siteDao);
    }
}
