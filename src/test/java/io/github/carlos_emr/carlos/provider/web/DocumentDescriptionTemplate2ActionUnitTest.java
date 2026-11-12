/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.DocumentDescriptionTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.DocumentDescriptionTemplate;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Security contract tests for the document description template endpoint (issue #5057).
 *
 * <p>The endpoint serves three pages: the clinic-wide list (administration), a provider's own list
 * (Preferences) and the Incoming Docs shortcut buttons. These tests pin who may read and change
 * which entries, that a write is checked against the stored entry rather than the posted owner, and
 * that a refused or malformed request never reaches the DAO.</p>
 *
 * <p>The action keeps its two read methods available to GET, so the aggregated mutator contract
 * cannot drive it without mutation-intent parameters; the POST-only write methods are pinned here.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("DocumentDescriptionTemplate2Action")
class DocumentDescriptionTemplate2ActionUnitTest {

    private static final String SELF = "100101";
    private static final String OTHER = "200202";

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager security;
    private DocumentDescriptionTemplateDao dao;
    private UserPropertyDAO userPropertyDAO;
    private LoggedInInfo user;
    private MockedStatic<ServletActionContext> servletContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private MockedStatic<LogAction> logAction;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/carlos/DocumentDescriptionTemplate");
        response = new MockHttpServletResponse();
        security = mock(SecurityInfoManager.class);
        dao = mock(DocumentDescriptionTemplateDao.class);
        userPropertyDAO = mock(UserPropertyDAO.class);
        user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn(SELF);

        servletContext = mockStatic(ServletActionContext.class);
        servletContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletContext.when(ServletActionContext::getResponse).thenReturn(response);
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

    /** The seeded doctor role: its own preferences and Incoming Docs, no administration objects. */
    private void signInAsDoctor() {
        grant("_pref", "w");
        grant("_edoc", "r");
    }

    private void signInAsAdministrator() {
        grant("_admin", "r");
        grant("_admin", "w");
        grant("_pref", "w");
        grant("_edoc", "r");
    }

    private String run(String method, String... params) throws Exception {
        request.addParameter("method", method);
        for (int i = 0; i < params.length; i += 2) {
            request.addParameter(params[i], params[i + 1]);
        }
        return new DocumentDescriptionTemplate2Action(security, dao, userPropertyDAO).execute();
    }

    private DocumentDescriptionTemplate stored(int id, String owner) {
        DocumentDescriptionTemplate template = new DocumentDescriptionTemplate();
        template.setId(id);
        template.setProviderNo(owner);
        template.setDocType("lab");
        template.setDescriptionShortcut("old");
        template.setDescription("old description");
        // The action looks the id up as an Integer, i.e. through find(Object), not find(int).
        when(dao.find((Object) Integer.valueOf(id))).thenReturn(template);
        return template;
    }

    @Nested
    @DisplayName("entry check")
    class EntryCheck {

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"getDocumentDescriptionFromDocType", "getDocumentDescriptionFromId",
                "addDocumentDescription", "updateDocumentDescription", "deleteDocumentDescription",
                "saveDocumentDescriptionTemplatePreference"})
        @DisplayName("should refuse every method before DAO access for a user with no template right")
        void shouldRefuseEveryMethod_withoutAnyTemplateRight(String method) {
            assertThatThrownBy(() -> run(method, "id", "7", "providerNo", SELF, "doctype", "lab",
                    "description", "d", "shortcut", "s"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageStartingWith("missing required sec object (");
            verifyNoInteractions(dao, userPropertyDAO);
        }

        @ParameterizedTest(name = "{0} {1} is rejected before DAO access")
        @CsvSource({
                "GET,addDocumentDescription",
                "HEAD,addDocumentDescription",
                "GET,updateDocumentDescription",
                "HEAD,updateDocumentDescription",
                "GET,deleteDocumentDescription",
                "HEAD,deleteDocumentDescription",
                "GET,saveDocumentDescriptionTemplatePreference",
                "HEAD,saveDocumentDescriptionTemplatePreference"
        })
        @DisplayName("should reject non-POST mutation methods before DAO access")
        void shouldRejectNonPostMutationMethods_beforeDaoAccess(String httpMethod, String actionMethod) throws Exception {
            signInAsAdministrator();
            request.setMethod(httpMethod);

            assertThat(run(actionMethod, "id", "7", "providerNo", "null")).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(dao, userPropertyDAO);
        }

        @Test
        @DisplayName("should answer 400 for an unknown method")
        void shouldAnswerBadRequest_forUnknownMethod() throws Exception {
            signInAsDoctor();

            assertThat(run("dropEverything")).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(dao, userPropertyDAO);
        }
    }

    @Nested
    @DisplayName("reads")
    class Reads {

        @Test
        @DisplayName("should list a document type's clinic-wide entries for a doctor")
        void shouldListClinicEntries_whenClinicListRequested() throws Exception {
            signInAsDoctor();
            DocumentDescriptionTemplate clinicEntry = stored(3, null);
            when(dao.findByDocTypeAndProviderNo("lab", null)).thenReturn(List.of(clinicEntry));

            assertThat(run("getDocumentDescriptionFromDocType", "doctype", "lab",
                    "useDocumentDescriptionTemplateType", UserProperty.CLINIC, "providerNo", SELF))
                    .isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentType()).startsWith("application/json");
            assertThat(response.getContentAsString()).contains("\"descriptionShortcut\":\"old\"");
        }

        @Test
        @DisplayName("should list the signed-in provider's own entries")
        void shouldListOwnEntries_whenUserListRequested() throws Exception {
            signInAsDoctor();

            run("getDocumentDescriptionFromDocType", "doctype", "lab",
                    "useDocumentDescriptionTemplateType", UserProperty.USER, "providerNo", SELF);

            verify(dao).findByDocTypeAndProviderNo("lab", SELF);
        }

        @Test
        @DisplayName("should list the signed-in provider's own entries when the request names nobody")
        void shouldListOwnEntries_whenUserListNamesNoProvider() throws Exception {
            signInAsDoctor();

            run("getDocumentDescriptionFromDocType", "doctype", "lab",
                    "useDocumentDescriptionTemplateType", UserProperty.USER);

            verify(dao).findByDocTypeAndProviderNo("lab", SELF);
        }

        @Test
        @DisplayName("should refuse another provider's own list, even to an administrator")
        void shouldRefuseList_whenUserListNamesAnotherProvider() {
            signInAsAdministrator();

            assertThatThrownBy(() -> run("getDocumentDescriptionFromDocType", "doctype", "lab",
                    "useDocumentDescriptionTemplateType", UserProperty.USER, "providerNo", OTHER))
                    .isInstanceOf(SecurityException.class);
            verifyNoInteractions(dao);
        }

        @Test
        @DisplayName("should let an Incoming Docs reader without preferences list the clinic-wide entries")
        void shouldListClinicEntries_forIncomingDocsReader() throws Exception {
            grant("_edoc", "r");

            run("getDocumentDescriptionFromDocType", "doctype", "lab",
                    "useDocumentDescriptionTemplateType", UserProperty.CLINIC);

            verify(dao).findByDocTypeAndProviderNo("lab", null);
        }

        @Test
        @DisplayName("should return a clinic-wide entry by id to a doctor")
        void shouldReturnClinicEntry_byIdForDoctor() throws Exception {
            signInAsDoctor();
            stored(7, null);

            run("getDocumentDescriptionFromId", "id", "7");

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
            assertThat(response.getContentAsString()).contains("\"id\":7");
        }

        @Test
        @DisplayName("should refuse another provider's entry by id")
        void shouldRefuseEntry_whenItBelongsToAnotherProvider() {
            signInAsAdministrator();
            stored(7, OTHER);

            assertThatThrownBy(() -> run("getDocumentDescriptionFromId", "id", "7"))
                    .isInstanceOf(SecurityException.class);
            assertThat(response.getContentLength()).isZero();
        }

        @Test
        @DisplayName("should answer 404 for an entry that does not exist")
        void shouldAnswerNotFound_whenEntryMissing() throws Exception {
            signInAsDoctor();

            assertThat(run("getDocumentDescriptionFromId", "id", "7")).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
        }

        @ParameterizedTest(name = "id={0}")
        @ValueSource(strings = {"", "abc", "-1", "1e3", "12345678901"})
        @DisplayName("should answer 400 for a missing or malformed id before DAO access")
        void shouldAnswerBadRequest_whenIdMalformed(String id) throws Exception {
            signInAsDoctor();

            assertThat(run("getDocumentDescriptionFromId", "id", id)).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verifyNoInteractions(dao);
        }
    }

    @Nested
    @DisplayName("add")
    class Add {

        @Test
        @DisplayName("should add a doctor's own entry under their provider number")
        void shouldAddOwnEntry_forDoctor() throws Exception {
            signInAsDoctor();

            run("addDocumentDescription", "providerNo", SELF, "doctype", "lab",
                    "description", "Bloodwork A&B+", "shortcut", "bw");

            ArgumentCaptor<DocumentDescriptionTemplate> saved = ArgumentCaptor.forClass(DocumentDescriptionTemplate.class);
            verify(dao).persist(saved.capture());
            assertThat(saved.getValue().getProviderNo()).isEqualTo(SELF);
            assertThat(saved.getValue().getDescription()).isEqualTo("Bloodwork A&B+");
            assertThat(saved.getValue().getDescriptionShortcut()).isEqualTo("bw");
            assertThat(saved.getValue().getDocType()).isEqualTo("lab");
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_OK);
        }

        @Test
        @DisplayName("should refuse a clinic-wide entry from a doctor without document administration")
        void shouldRefuseClinicEntry_forDoctorWithoutAdministration() {
            signInAsDoctor();

            assertThatThrownBy(() -> run("addDocumentDescription", "providerNo", "null", "doctype", "lab",
                    "description", "d", "shortcut", "s"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_admin | _admin.document)");
            verify(dao, never()).persist(any());
        }

        @ParameterizedTest(name = "{0} write")
        @ValueSource(strings = {"_admin", "_admin.document"})
        @DisplayName("should add a clinic-wide entry for an administration writer")
        void shouldAddClinicEntry_forAdministrationWriter(String object) throws Exception {
            grant(object, "w");

            run("addDocumentDescription", "providerNo", "null", "doctype", "lab",
                    "description", "d", "shortcut", "s");

            ArgumentCaptor<DocumentDescriptionTemplate> saved = ArgumentCaptor.forClass(DocumentDescriptionTemplate.class);
            verify(dao).persist(saved.capture());
            assertThat(saved.getValue().getProviderNo()).isNull();
        }

        @Test
        @DisplayName("should refuse adding an entry to another provider's list, even to an administrator")
        void shouldRefuseAddingEntry_forAnotherProvider() {
            signInAsAdministrator();

            assertThatThrownBy(() -> run("addDocumentDescription", "providerNo", OTHER, "doctype", "lab",
                    "description", "d", "shortcut", "s"))
                    .isInstanceOf(SecurityException.class);
            verify(dao, never()).persist(any());
        }

        @Test
        @DisplayName("should refuse an own entry from a user without preference write")
        void shouldRefuseOwnEntry_withoutPreferenceWrite() {
            grant("_edoc", "r");

            assertThatThrownBy(() -> run("addDocumentDescription", "providerNo", SELF, "doctype", "lab",
                    "description", "d", "shortcut", "s"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_pref)");
            verify(dao, never()).persist(any());
        }

        @ParameterizedTest(name = "doctype=[{0}] description=[{1}] shortcut=[{2}]")
        @CsvSource({
                "lab,'',s",
                "lab,'   ',s",
                "lab,d,''",
                "'',d,s",
                "lab,d,abcdefghijklmnopqrstu",
        })
        @DisplayName("should answer 400 and save nothing for a blank or oversized field")
        void shouldRejectAdd_whenFieldBlankOrTooLong(String docType, String description, String shortcut) throws Exception {
            signInAsDoctor();

            assertThat(run("addDocumentDescription", "providerNo", SELF, "doctype", docType,
                    "description", description, "shortcut", shortcut)).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verify(dao, never()).persist(any());
        }

        @Test
        @DisplayName("should answer 400 for a description longer than its column")
        void shouldRejectAdd_whenDescriptionTooLong() throws Exception {
            signInAsDoctor();

            run("addDocumentDescription", "providerNo", SELF, "doctype", "lab",
                    "description", "x".repeat(256), "shortcut", "s");

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST);
            verify(dao, never()).persist(any());
        }
    }

    @Nested
    @DisplayName("update")
    class Update {

        @Test
        @DisplayName("should change a doctor's own entry and keep its owner")
        void shouldUpdateOwnEntry_keepingItsOwner() throws Exception {
            signInAsDoctor();
            DocumentDescriptionTemplate template = stored(7, SELF);

            run("updateDocumentDescription", "id", "7", "providerNo", SELF, "doctype", "consult",
                    "description", "new description", "shortcut", "new");

            verify(dao).merge(template);
            assertThat(template.getProviderNo()).isEqualTo(SELF);
            assertThat(template.getDescription()).isEqualTo("new description");
            assertThat(template.getDescriptionShortcut()).isEqualTo("new");
            assertThat(template.getDocType()).isEqualTo("consult");
        }

        @Test
        @DisplayName("should refuse a doctor changing a clinic-wide entry, whatever owner is posted")
        void shouldRefuseUpdate_whenEntryIsClinicWideForDoctor() {
            signInAsDoctor();
            DocumentDescriptionTemplate template = stored(7, null);

            assertThatThrownBy(() -> run("updateDocumentDescription", "id", "7", "providerNo", SELF,
                    "doctype", "lab", "description", "d", "shortcut", "s"))
                    .isInstanceOf(SecurityException.class);
            verify(dao, never()).merge(any());
            assertThat(template.getDescription()).isEqualTo("old description");
        }

        @Test
        @DisplayName("should refuse changing another provider's entry, even to an administrator")
        void shouldRefuseUpdate_whenEntryBelongsToAnotherProvider() {
            signInAsAdministrator();
            stored(7, OTHER);

            assertThatThrownBy(() -> run("updateDocumentDescription", "id", "7", "providerNo", OTHER,
                    "doctype", "lab", "description", "d", "shortcut", "s"))
                    .isInstanceOf(SecurityException.class);
            verify(dao, never()).merge(any());
        }

        @Test
        @DisplayName("should refuse moving a clinic-wide entry into the administrator's own list")
        void shouldRefuseUpdate_whenPostedOwnerDiffersFromStored() {
            signInAsAdministrator();
            DocumentDescriptionTemplate template = stored(7, null);

            assertThatThrownBy(() -> run("updateDocumentDescription", "id", "7", "providerNo", SELF,
                    "doctype", "lab", "description", "d", "shortcut", "s"))
                    .isInstanceOf(SecurityException.class)
                    .hasMessageContaining("keeps its owner");
            verify(dao, never()).merge(any());
            assertThat(template.getProviderNo()).isNull();
        }

        @Test
        @DisplayName("should change a clinic-wide entry for an administrator")
        void shouldUpdateClinicEntry_forAdministrator() throws Exception {
            signInAsAdministrator();
            DocumentDescriptionTemplate template = stored(7, null);

            run("updateDocumentDescription", "id", "7", "providerNo", "null", "doctype", "lab",
                    "description", "edited", "shortcut", "s");

            verify(dao).merge(template);
            assertThat(template.getProviderNo()).isNull();
            assertThat(template.getDescription()).isEqualTo("edited");
        }

        @Test
        @DisplayName("should answer 404 when updating an entry that does not exist")
        void shouldAnswerNotFound_whenUpdatingMissingEntry() throws Exception {
            signInAsDoctor();

            run("updateDocumentDescription", "id", "7", "providerNo", SELF, "doctype", "lab",
                    "description", "d", "shortcut", "s");

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            verify(dao, never()).merge(any());
        }
    }

    @Nested
    @DisplayName("delete")
    class Delete {

        @Test
        @DisplayName("should delete a doctor's own entry")
        void shouldDeleteOwnEntry_forDoctor() throws Exception {
            signInAsDoctor();
            stored(7, SELF);

            run("deleteDocumentDescription", "id", "7");

            verify(dao).remove(Integer.valueOf(7));
        }

        @Test
        @DisplayName("should refuse a doctor deleting a clinic-wide entry")
        void shouldRefuseDelete_whenEntryIsClinicWideForDoctor() {
            signInAsDoctor();
            stored(7, null);

            assertThatThrownBy(() -> run("deleteDocumentDescription", "id", "7"))
                    .isInstanceOf(SecurityException.class);
            verify(dao, never()).remove(any(Object.class));
        }

        @Test
        @DisplayName("should refuse deleting another provider's entry, even to an administrator")
        void shouldRefuseDelete_whenEntryBelongsToAnotherProvider() {
            signInAsAdministrator();
            stored(7, OTHER);

            assertThatThrownBy(() -> run("deleteDocumentDescription", "id", "7"))
                    .isInstanceOf(SecurityException.class);
            verify(dao, never()).remove(any(Object.class));
        }

        @Test
        @DisplayName("should delete a clinic-wide entry for an administrator")
        void shouldDeleteClinicEntry_forAdministrator() throws Exception {
            signInAsAdministrator();
            stored(7, null);

            run("deleteDocumentDescription", "id", "7");

            verify(dao).remove(Integer.valueOf(7));
        }

        @Test
        @DisplayName("should answer 404 when deleting an entry that does not exist")
        void shouldAnswerNotFound_whenDeletingMissingEntry() throws Exception {
            signInAsDoctor();

            run("deleteDocumentDescription", "id", "7");

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_NOT_FOUND);
            verify(dao, never()).remove(any(Object.class));
        }
    }

    @Nested
    @DisplayName("template preference")
    class Preference {

        @Test
        @DisplayName("should save the signed-in provider's own preference")
        void shouldSavePreference_forSignedInProvider() throws Exception {
            signInAsDoctor();

            run("saveDocumentDescriptionTemplatePreference", "defaultShortcut", UserProperty.USER,
                    "providerNo", OTHER);

            ArgumentCaptor<UserProperty> saved = ArgumentCaptor.forClass(UserProperty.class);
            verify(userPropertyDAO).saveProp(saved.capture());
            assertThat(saved.getValue().getProviderNo()).isEqualTo(SELF);
            assertThat(saved.getValue().getName()).isEqualTo(UserProperty.DOCUMENT_DESCRIPTION_TEMPLATE);
            assertThat(saved.getValue().getValue()).isEqualTo(UserProperty.USER);
        }

        @Test
        @DisplayName("should refuse the preference to a user without preference write")
        void shouldRefusePreference_withoutPreferenceWrite() {
            grant("_admin", "r");
            grant("_admin", "w");

            assertThatThrownBy(() -> run("saveDocumentDescriptionTemplatePreference",
                    "defaultShortcut", UserProperty.USER))
                    .isInstanceOf(SecurityException.class)
                    .hasMessage("missing required sec object (_pref)");
            verify(userPropertyDAO, never()).saveProp(any(UserProperty.class));
            verify(userPropertyDAO, never()).getProp(anyString(), anyString());
        }
    }
}
