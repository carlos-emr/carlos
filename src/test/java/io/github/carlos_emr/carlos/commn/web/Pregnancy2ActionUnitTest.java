/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.dao.EpisodeDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Episode;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.LockModeType;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.Query;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.ActionProxyFactory;
import org.apache.struts2.dispatcher.Dispatcher;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class Pregnancy2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private EpisodeDao episodes;
    private SecurityInfoManager security;
    private EntityManager em;
    private EntityManagerFactory factory;
    private PlatformTransactionManager transactions;
    private TypedQuery<Episode> current;
    private Query patientLock;
    private Episode stored;
    private Pregnancy2Action action;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void prepare() {
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("demographicNo", "101");
        request.setParameter("code", "72892002");
        request.setParameter("episodeId", "7");
        request.setParameter("startDate", "2026-01-02");
        request.setParameter("endDate", "2026-02-03");
        request.setParameter("notes", "Literal <script> ' & notes");
        LoggedInInfo user = mock(LoggedInInfo.class);
        when(user.getLoggedInProviderNo()).thenReturn("999001");
        request.getSession().setAttribute(LoggedInInfo.class.getName() + ".LOGGED_IN_INFO_KEY", user);
        ActionContext.of().withServletRequest(request).withServletResponse(response).bind();
        episodes = createAndRegisterMock(EpisodeDao.class);
        security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(eq(user), anyString(), anyString(), anyInt())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(user, 101)).thenReturn(true);
        em = mock(EntityManager.class);
        factory = createAndRegisterMock(EntityManagerFactory.class);
        transactions = createAndRegisterMock(PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        TransactionSynchronizationManager.bindResource(factory, new EntityManagerHolder(em));
        patientLock = mock(Query.class, RETURNS_SELF);
        when(em.createNativeQuery("SELECT demographic_no FROM demographic WHERE demographic_no=:patient FOR UPDATE")).thenReturn(patientLock);
        when(patientLock.getResultList()).thenReturn(List.of(101));
        current = mock(TypedQuery.class, RETURNS_SELF);
        when(em.createQuery(anyString(), eq(Episode.class))).thenReturn(current);
        when(current.getResultList()).thenReturn(List.of());
        stored = pregnancy(101);
        when(episodes.find(Integer.valueOf(7))).thenReturn(stored);
        when(em.find(Episode.class, 7, LockModeType.PESSIMISTIC_WRITE)).thenReturn(stored);
        action = new Pregnancy2Action();
    }

    @AfterEach void releaseContext() {
        TransactionSynchronizationManager.unbindResource(factory);
        ActionContext.clear();
    }

    private static Episode pregnancy(int patient) {
        Episode episode = new Episode();
        episode.setId(7);
        episode.setDemographicNo(patient);
        episode.setCode("72892002");
        episode.setCodingSystem("SnomedCore");
        episode.setStatus("Current");
        episode.setStartDateStr("2026-01-02");
        episode.setNotes("Original");
        return episode;
    }

    @ParameterizedTest @CsvSource({"create,GET", "create,HEAD", "complete,GET", "complete,HEAD", "create,PUT", "complete,DELETE"})
    void shouldRejectMutation_whenNotPost(String operation, String verb) throws Exception {
        request.setParameter("method", operation);
        request.setMethod(verb);
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405);
        assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(episodes, em, transactions, security);
    }

    @ParameterizedTest @ValueSource(strings = {"saveFormAjax", "saveMeasurementAjax", "doDelete", "doComplete", "createGBSLabReq", "createGTTLabReq", "getMeds", "getLatestFormIdByPregnancy", "recordPrint", "unknown"})
    void shouldRejectLegacyDispatch_whenOperationOutsideLifecycle(String operation) throws Exception {
        request.setParameter("method", operation);
        action.execute();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(episodes, em, transactions, security);
    }

    @ParameterizedTest @ValueSource(strings = {"list", "new", "edit"})
    void shouldReadWithoutWriting_whenReadOnlyRoleOpensGet(String operation) throws Exception {
        request.setMethod("GET");
        request.setParameter("method", operation);
        when(security.hasPrivilege(any(), eq("_pregnancy"), eq("w"), eq(101))).thenReturn(false);
        assertThat(action.execute()).isIn("list", "form");
        assertThat(request.getAttribute("canWrite")).isEqualTo(false);
        verifyNoInteractions(em, transactions);
        verify(episodes, never()).persist(any());
        verify(episodes, never()).merge(any());
    }

    @ParameterizedTest @ValueSource(strings = {"create", "complete"})
    void shouldDenyWrite_whenReadOnlyRolePosts(String operation) {
        request.setParameter("method", operation);
        when(security.hasPrivilege(any(), eq("_pregnancy"), eq("w"), eq(101))).thenReturn(false);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class).hasMessage("missing required sec object (_pregnancy)");
        verifyNoInteractions(episodes, em, transactions);
    }

    @ParameterizedTest @ValueSource(strings = {"list", "new", "edit", "create", "complete"})
    void shouldDenyEveryOperation_whenIndividualChartRestricted(String operation) {
        request.setParameter("method", operation);
        when(security.isAllowedAccessToPatientRecord(any(), eq(101))).thenReturn(false);
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(episodes, em, transactions);
    }

    @ParameterizedTest @ValueSource(strings = {"_pregnancy", "_eChart"})
    void shouldDenyRead_whenModuleOrChartReadMissing(String object) {
        when(security.hasPrivilege(any(), eq(object), eq("r"), eq(101))).thenReturn(false);
        assertThatThrownBy(action::list).isInstanceOf(SecurityException.class);
        verifyNoInteractions(episodes, em, transactions);
    }

    @ParameterizedTest @ValueSource(strings = {"", "-1", "0", "bad", "2147483648", "https://example.invalid", "//example.invalid"})
    void shouldRejectPatient_whenIdentifierInvalid(String patient) throws Exception {
        request.setParameter("demographicNo", patient);
        action.create();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(action.getDemographicNo()).isNull();
        verifyNoInteractions(episodes, em, transactions);
    }

    @Test void shouldRejectPatient_whenMissingDespiteSessionLastChart() throws Exception {
        request.removeParameter("demographicNo");
        request.getSession().setAttribute("demographicNo", "101");
        action.list();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(episodes);
    }

    @Test void shouldRejectPatient_whenAliasesConflict() throws Exception {
        request.setParameter("demographic_no", "202");
        action.complete();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(em, transactions);
    }

    @ParameterizedTest @ValueSource(strings = {"edit", "complete"})
    void shouldHideEpisode_whenItBelongsToAnotherPatient(String operation) throws Exception {
        stored.setDemographicNo(202);
        request.setParameter("method", operation);
        action.execute();
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(request.getAttribute("episode")).isNull();
        assertThat(stored.getStatus()).isEqualTo("Current");
        assertThat(stored.getNotes()).isEqualTo("Original");
        verify(em, never()).flush();
    }

    @Test void shouldHideEpisode_whenItIsNotPregnancy() throws Exception {
        stored.setCode("other");
        action.complete();
        assertThat(response.getStatus()).isEqualTo(404);
        verify(em, never()).flush();
    }

    @ParameterizedTest @ValueSource(strings = {"", "0001-01-01", "0999-12-31", "2026-02-30", "2026-1-1", "bad", "2999-01-01"})
    void shouldRejectCreate_whenDateInvalid(String date) throws Exception {
        request.setParameter("startDate", date);
        action.create();
        assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(em, transactions);
    }

    @ParameterizedTest @ValueSource(strings = {"", "0001-01-01", "0999-12-31", "2026-02-30", "2026-1-1", "bad", "2999-01-01", "2026-01-01"})
    void shouldPreserveEpisode_whenCompletionDateInvalid(String date) throws Exception {
        request.setParameter("endDate", date);
        action.complete();
        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(stored.getStatus()).isEqualTo("Current");
        assertThat(stored.getEndDate()).isNull();
        verify(em, never()).flush();
    }

    @Test void shouldRejectDuplicate_whenCurrentPregnancyExists() throws Exception {
        when(current.getResultList()).thenReturn(List.of(stored));
        action.create();
        assertThat(response.getStatus()).isEqualTo(409);
        verify(em, never()).persist(any());
    }

    @Test void shouldRejectReplay_whenPregnancyAlreadyComplete() throws Exception {
        stored.setStatus("Complete");
        action.complete();
        assertThat(response.getStatus()).isEqualTo(409);
        assertThat(stored.getNotes()).isEqualTo("Original");
        verify(em, never()).flush();
    }

    @Test void shouldCreateAndRedirect_whenSubmissionValid() throws Exception {
        assertThat(action.create()).isEqualTo("saved");
        verify(em).persist(argThat(value -> value instanceof Episode episode
                && episode.getDemographicNo() == 101 && "Current".equals(episode.getStatus())
                && "Literal <script> ' & notes".equals(episode.getNotes()) && "999001".equals(episode.getLastUpdateUser())));
        var order = inOrder(em);
        order.verify(em).createNativeQuery("SELECT demographic_no FROM demographic WHERE demographic_no=:patient FOR UPDATE");
        verify(patientLock).setParameter("patient", 101);
        verify(em, never()).find(eq(Demographic.class), any(), any(LockModeType.class));
        order.verify(em).createQuery(anyString(), eq(Episode.class));
        order.verify(em).persist(any());
        order.verify(em).flush();
        verify(transactions).commit(any());
        assertThat(action.getDemographicNo()).isEqualTo(101);
        assertThat(response.getRedirectedUrl()).isNull();
    }

    @Test void shouldCompleteAndAudit_whenSubmissionValid() throws Exception {
        assertThat(action.complete()).isEqualTo("saved");
        assertThat(action.getDemographicNo()).isEqualTo(101);
        assertThat(stored.getStatus()).isEqualTo("Complete");
        assertThat(stored.getEndDateStr()).isEqualTo("2026-02-03");
        assertThat(stored.getLastUpdateUser()).isEqualTo("999001");
        verify(em).flush();
        verify(transactions).commit(any());
    }

    @ParameterizedTest @ValueSource(strings = {"create", "complete"})
    void shouldSendRedirect_afterStrutsExecutesSavedResult(String operation) throws Exception {
        request.setContextPath("/carlos");
        request.setRequestURI("/carlos/Pregnancy");
        request.setParameter("method", operation);
        var springContext = new org.springframework.web.context.support.GenericWebApplicationContext();
        springContext.refresh();
        request.getServletContext().setAttribute(
                org.springframework.web.context.WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, springContext);
        ClassLoader previous = Thread.currentThread().getContextClassLoader();
        var loader = new java.net.URLClassLoader(new java.net.URL[] {
                java.nio.file.Path.of("src/main/webapp/WEB-INF/classes").toUri().toURL()}, previous);
        Thread.currentThread().setContextClassLoader(loader);
        Dispatcher dispatcher = new Dispatcher(request.getServletContext(), Map.of(
                "config", "struts-default.xml,struts-plugin.xml,struts.xml"));
        try {
            dispatcher.init();
            var container = dispatcher.getContainer();
            var context = ActionContext.of(new HashMap<String, Object>())
                    .withContainer(container).withServletContext(request.getServletContext())
                    .withServletRequest(request).withServletResponse(response).withSession(new HashMap<>())
                    .withParameters(org.apache.struts2.dispatcher.HttpParameters.create(request.getParameterMap()).build());
            context.bind();
            ActionProxy proxy = container.getInstance(ActionProxyFactory.class).createActionProxy(
                    "/", "Pregnancy", null, context.getContextMap(), true, true);
            assertThat(proxy.execute()).isEqualTo("saved");
            assertThat(response.getStatus()).isEqualTo(302);
            assertThat(response.getRedirectedUrl()).isEqualTo("/carlos/Pregnancy?demographicNo=101&saved=true");
            verify(transactions).commit(any());
        } finally {
            dispatcher.cleanup();
            Dispatcher.clearInstance();
            Thread.currentThread().setContextClassLoader(previous);
            loader.close();
            springContext.close();
        }
    }

    private String createToken(String patient) {
        return FormSubmissionGuard.issue(request.getSession(), FormSubmissionGuard.Scope.PREGNANCY_CREATE, patient);
    }

    @Test void shouldReuseSavedHistory_whenCreateTokenReplayedWithChangedFields() throws Exception {
        request.setParameter(FormSubmissionGuard.PARAMETER, createToken("101"));
        assertThat(action.create()).isEqualTo("saved");
        clearInvocations(em, transactions);
        request.setParameter("code", "invalid");
        assertThat(action.create()).isEqualTo("saved");
        verifyNoInteractions(em, transactions);
    }

    @Test void shouldRefuseCreate_whenTokenBelongsToAnotherPatient() throws Exception {
        request.setParameter(FormSubmissionGuard.PARAMETER, createToken("202"));
        assertThat(action.create()).isEqualTo("submissionRefused");
        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(em, transactions);
    }

    @Test void shouldPermitRetry_whenValidationWroteNothing() throws Exception {
        request.setParameter(FormSubmissionGuard.PARAMETER, createToken("101"));
        request.setParameter("startDate", "bad");
        assertThat(action.create()).isEqualTo("none");
        verifyNoInteractions(em, transactions);
        request.setParameter("startDate", "2026-01-02");
        assertThat(action.create()).isEqualTo("saved");
        verify(em).persist(any());
    }

    @Test void shouldRefuseReplay_whenCommitOutcomeUnknown() throws Exception {
        request.setParameter(FormSubmissionGuard.PARAMETER, createToken("101"));
        doThrow(new IllegalStateException("Commit acknowledgement lost")).when(transactions).commit(any());
        assertThatThrownBy(action::create).isInstanceOf(IllegalStateException.class);
        clearInvocations(em, transactions);
        assertThat(action.create()).isEqualTo("submissionRefused");
        assertThat(response.getStatus()).isEqualTo(409);
        verifyNoInteractions(em, transactions);
    }

    @Test void shouldIssueFreshPatientBoundToken_whenAddFormRenderedAgain() throws Exception {
        assertThat(action.newPregnancy()).isEqualTo("form");
        String first = (String) request.getAttribute("formSubmissionToken");
        action.newPregnancy();
        String second = (String) request.getAttribute("formSubmissionToken");
        assertThat(first).isNotBlank().isNotEqualTo(second);
        request.setParameter(FormSubmissionGuard.PARAMETER, first);
        assertThat(action.create()).isEqualTo("saved");
    }
}
