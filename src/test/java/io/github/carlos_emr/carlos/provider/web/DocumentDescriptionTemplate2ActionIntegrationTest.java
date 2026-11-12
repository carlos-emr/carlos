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
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.DocumentDescriptionTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.DocumentDescriptionTemplate;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;

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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The document description template endpoint against the real DAOs and database (issue #5057):
 * a provider without administration rights keeps a list of their own, an update keeps the stored
 * owner through the real merge, and a refused write leaves the stored row as it was.
 *
 * <p>Only the privilege lookup, the session and the asynchronous audit log are stubbed; every
 * read-back clears the persistence context so it reads the database, not the entity just written.</p>
 */
@DisplayName("DocumentDescriptionTemplate2Action Integration Tests")
@Tag("integration")
@Tag("document")
@Tag("security")
class DocumentDescriptionTemplate2ActionIntegrationTest extends CarlosTestBase {

    private static final String SELF = "910001";
    private static final String OTHER = "910002";
    private static final String DOC_TYPE = "itest-5057";

    @Autowired
    private DocumentDescriptionTemplateDao templateDao;

    @Autowired
    private UserPropertyDAO userPropertyDAO;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private SecurityInfoManager security;
    private LoggedInInfo user;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private MockedStatic<LogAction> logAction;

    @BeforeEach
    void setUp() {
        security = mock(SecurityInfoManager.class);
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn(SELF);
        servletContext = mockStatic(ServletActionContext.class);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(user);
        logAction = mockStatic(LogAction.class);
    }

    @AfterEach
    void tearDown() {
        logAction.close();
        loggedInInfoStatic.close();
        servletContext.close();
    }

    /** Grants a right the way SecurityInfoManagerImpl reads it: write includes read. */
    private void grant(String object, String right) {
        when(security.hasPrivilege(eq(user), eq(object), eq(right), isNull())).thenReturn(true);
        if ("w".equals(right)) {
            when(security.hasPrivilege(eq(user), eq(object), eq("r"), isNull())).thenReturn(true);
        }
    }

    /** The seeded doctor role: preferences and Incoming Docs, no administration objects. */
    private void signInAsDoctor() {
        grant("_pref", "w");
        grant("_edoc", "r");
    }

    private void post(String method, String... params) throws Exception {
        request = new MockHttpServletRequest("POST", "/carlos/DocumentDescriptionTemplate");
        response = new MockHttpServletResponse();
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
        request.addParameter("method", method);
        for (int i = 0; i < params.length; i += 2) {
            request.addParameter(params[i], params[i + 1]);
        }
        new DocumentDescriptionTemplate2Action(security, templateDao, userPropertyDAO).execute();
        entityManager.flush();
        entityManager.clear();
    }

    private DocumentDescriptionTemplate saved(String owner, String shortcut, String description) {
        DocumentDescriptionTemplate template = new DocumentDescriptionTemplate();
        template.setProviderNo(owner);
        template.setDocType(DOC_TYPE);
        template.setDescriptionShortcut(shortcut);
        template.setDescription(description);
        templateDao.persist(template);
        entityManager.flush();
        entityManager.clear();
        return template;
    }

    @Test
    @DisplayName("should store a doctor's own entry under their provider number, off the clinic-wide list")
    void shouldStoreOwnEntry_forDoctorWithoutAdministration() throws Exception {
        signInAsDoctor();

        post("addDocumentDescription", "providerNo", SELF, "doctype", DOC_TYPE,
                "description", "O'Neil \"A&B+\"", "shortcut", "own");

        List<DocumentDescriptionTemplate> own = templateDao.findByDocTypeAndProviderNo(DOC_TYPE, SELF);
        assertThat(own).hasSize(1);
        assertThat(own.get(0).getDescription()).isEqualTo("O'Neil \"A&B+\"");
        assertThat(own.get(0).getDescriptionShortcut()).isEqualTo("own");
        assertThat(templateDao.findByDocTypeAndProviderNo(DOC_TYPE, null)).isEmpty();
    }

    @Test
    @DisplayName("should list only the doctor's own entries, not the clinic's or another provider's")
    void shouldListOnlyOwnEntries_forDoctor() throws Exception {
        signInAsDoctor();
        saved(SELF, "mine", "my entry");
        saved(OTHER, "theirs", "their entry");
        saved(null, "clinic", "clinic entry");

        post("getDocumentDescriptionFromDocType", "doctype", DOC_TYPE,
                "useDocumentDescriptionTemplateType", UserProperty.USER, "providerNo", SELF);

        assertThat(response.getContentAsString())
                .contains("my entry")
                .doesNotContain("their entry")
                .doesNotContain("clinic entry");
    }

    @Test
    @DisplayName("should change a doctor's own entry through the real merge and keep its owner")
    void shouldUpdateOwnEntry_keepingOwnerInDatabase() throws Exception {
        signInAsDoctor();
        DocumentDescriptionTemplate own = saved(SELF, "own", "before");
        Date before = new Date(System.currentTimeMillis() - 60_000);
        entityManager.createQuery("update DocumentDescriptionTemplate t set t.lastUpdated = :d where t.id = :id")
                .setParameter("d", before).setParameter("id", own.getId()).executeUpdate();

        post("updateDocumentDescription", "id", String.valueOf(own.getId()), "providerNo", SELF,
                "doctype", DOC_TYPE, "description", "after", "shortcut", "own2");

        DocumentDescriptionTemplate stored = templateDao.find((Object) own.getId());
        assertThat(stored.getProviderNo()).isEqualTo(SELF);
        assertThat(stored.getDescription()).isEqualTo("after");
        assertThat(stored.getDescriptionShortcut()).isEqualTo("own2");
        assertThat(stored.getlastUpdated()).isAfter(before);
    }

    @Test
    @DisplayName("should leave a clinic-wide entry unchanged when an administrator posts it as their own")
    void shouldLeaveClinicEntryUnchanged_whenUpdateNamesAnotherOwner() {
        grant("_admin", "r");
        grant("_admin", "w");
        grant("_pref", "w");
        DocumentDescriptionTemplate clinic = saved(null, "clinic", "clinic entry");
        String clinicId = String.valueOf(clinic.getId());

        assertThatThrownBy(() -> post("updateDocumentDescription", "id", clinicId,
                "providerNo", SELF, "doctype", DOC_TYPE, "description", "taken", "shortcut", "taken"))
                .isInstanceOf(SecurityException.class);
        entityManager.clear();

        DocumentDescriptionTemplate stored = templateDao.find((Object) clinic.getId());
        assertThat(stored.getProviderNo()).isNull();
        assertThat(stored.getDescription()).isEqualTo("clinic entry");
    }

    @Test
    @DisplayName("should delete a doctor's own entry and refuse the clinic-wide one beside it")
    void shouldDeleteOwnEntry_andRefuseClinicEntry() throws Exception {
        signInAsDoctor();
        DocumentDescriptionTemplate own = saved(SELF, "own", "my entry");
        DocumentDescriptionTemplate clinic = saved(null, "clinic", "clinic entry");

        String clinicId = String.valueOf(clinic.getId());

        post("deleteDocumentDescription", "id", String.valueOf(own.getId()));
        assertThatThrownBy(() -> post("deleteDocumentDescription", "id", clinicId))
                .isInstanceOf(SecurityException.class);
        entityManager.clear();

        assertThat(templateDao.find((Object) own.getId())).isNull();
        assertThat(templateDao.find((Object) clinic.getId())).isNotNull();
    }

    @Test
    @DisplayName("should store the doctor's choice of their own list as their preference")
    void shouldStorePreference_forDoctor() throws Exception {
        signInAsDoctor();

        post("saveDocumentDescriptionTemplatePreference", "defaultShortcut", UserProperty.USER);

        UserProperty preference = userPropertyDAO.getProp(SELF, UserProperty.DOCUMENT_DESCRIPTION_TEMPLATE);
        assertThat(preference).isNotNull();
        assertThat(preference.getValue()).isEqualTo(UserProperty.USER);
    }
}
