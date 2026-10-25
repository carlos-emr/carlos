/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.service.ClientImageManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.CasemgmtNoteLockDao;
import io.github.carlos_emr.carlos.commn.model.CasemgmtNoteLock;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.test.base.CarlosWebTestBase;
import org.apache.struts2.ActionContext;
import org.apache.struts2.ActionProxy;
import org.apache.struts2.ActionProxyFactory;
import org.apache.struts2.dispatcher.Dispatcher;
import org.apache.struts2.dispatcher.HttpParameters;
import org.apache.struts2.dispatcher.SessionMap;
import org.apache.struts2.inject.Container;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.WebApplicationContext;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Runs real full saves through Struts parameter, conversion and workflow interceptors.
 * Repeated native duration inputs must reach the atomic request parser, rather than
 * return an unmapped input result before the action can preserve the editor draft.
 *
 * @since 2026-10-25
 */
@Tag("integration")
@Tag("casemgmt")
class CaseManagementDurationStrutsBindingIntegrationTest extends CarlosWebTestBase {
    private static final List<String> FIELDS = List.of("hourOfEncounterTime", "minuteOfEncounterTime",
            "hourOfEncTransportationTime", "minuteOfEncTransportationTime");
    private static final String DRAFT = "Typed FAKE note\n  Exact Unicode café & spacing  ";
    private CaseManagementManager manager;
    private CasemgmtNoteLockDao locks;
    private CaseManagementNote note;
    private CaseManagementEntryFormBean form;
    private CasemgmtNoteLock lock;

    @BeforeEach
    void prepareFullSave() throws Exception {
        manager = mock(CaseManagementManager.class);
        locks = mock(CasemgmtNoteLockDao.class);
        // These are resolved by field initializers before Struts can inject the action.
        // Register all collaborators first; the full save itself uses only manager/locks.
        replaceSpringUtilsBean(CaseManagementNoteDAO.class, mock(CaseManagementNoteDAO.class));
        replaceSpringUtilsBean(CaseManagementIssueDAO.class, mock(CaseManagementIssueDAO.class));
        replaceSpringUtilsBean(CaseManagementNoteExtDAO.class, mock(CaseManagementNoteExtDAO.class));
        replaceSpringUtilsBean(IssueDAO.class, mock(IssueDAO.class));
        replaceSpringUtilsBean(CasemgmtNoteLockDao.class, locks);
        replaceSpringUtilsBean(TicklerManager.class, mock(TicklerManager.class));
        replaceSpringUtilsBean(CaseManagementManager.class, manager);
        replaceSpringUtilsBean(ClientImageManager.class, mock(ClientImageManager.class));
        replaceSpringUtilsBean(ProviderManager.class, mock(ProviderManager.class));
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        Provider provider = new Provider();
        provider.setProviderNo("999998");
        when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(provider);
        when(mockSecurityInfoManager.isAllowedAccessToPatientRecord(mockLoggedInInfo, 101)).thenReturn(true);
        mockSession.setAttribute("userrole", "doctor");
        mockRequest.setMethod("POST");
        add("method", "save");
        add("demographicNo", "101");
        add("noteId", "501");
        add("caseNote_note", DRAFT);
        add("caseNote.encounter_type", "telephone");
        add("observation_date", "14-Oct-2026 10:00");
        add("chain", "list");
        add("ajaxview", "ajaxView");
        ChartProgramContext.remember(mockRequest, "7");
        add(ChartProgramContext.PARAMETER, ChartProgramContext.token(mockRequest));

        note = new CaseManagementNote();
        note.setId(501L);
        note.setDemographic_no("101");
        note.setProgram_no("7");
        note.setNote("Original FAKE note");
        note.setHistory("Original history");
        note.setHourOfEncounterTime(5);
        note.setMinuteOfEncounterTime(6);
        note.setHourOfEncTransportationTime(7);
        note.setMinuteOfEncTransportationTime(8);
        form = new CaseManagementEntryFormBean();
        form.setCaseNote(note);
        form.setIssueCheckList(new ArrayList<>());
        mockSession.setAttribute(ChartProgramContext.formKey(mockRequest, "101"), form);
        lock = new CasemgmtNoteLock();
        lock.setId(10L);
        lock.setNoteId(501L);
        lock.setDemographicNo(101);
        lock.setProviderNo("999998");
        lock.setSessionId(mockSession.getId());
        mockSession.setAttribute("casemgmtNoteLock101", lock);
        when(locks.find(10L)).thenReturn(lock);
        when(manager.saveCaseManagementNote(any(), any(), anyList(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any())).thenAnswer(call -> {
            CaseManagementNote revision = call.<CaseManagementNote>getArgument(1).copyForRevision();
            revision.setId(600L);
            return revision;
        });
    }

    static Stream<Arguments> acceptedDurations() {
        return FIELDS.stream().flatMap(field -> Stream.of(
                Arguments.of(field, new String[]{""}, null),
                Arguments.of(field, new String[]{"", ""}, null),
                Arguments.of(field, new String[]{"0"}, 0),
                Arguments.of(field, new String[]{"1", "1"}, 1),
                Arguments.of(field, new String[]{"01", "1"}, 1)));
    }

    @ParameterizedTest
    @MethodSource("acceptedDurations")
    void shouldSaveThroughTheInterceptorStack_whenDurationValuesAgree(String field, String[] values, Integer expected) throws Exception {
        add(field, values);

        BoundSave result = executeFullSave();

        assertThat(result.code()).isEqualTo("list");
        assertThat(result.action().hasFieldErrors()).isFalse();
        assertThat(result.action().hasActionErrors()).isFalse();
        assertThat(result.conversionErrors()).isEmpty();
        assertThat(mockResponse.getStatus()).isEqualTo(200);
        assertThat(form.getCaseNote().getId()).isEqualTo(600L);
        assertThat(duration(form.getCaseNote(), field)).isEqualTo(expected);
        assertThat(form.getCaseNote().getNote()).isEqualTo(DRAFT.trim());
        verify(manager).saveCaseManagementNote(any(), any(), anyList(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        assertActualResultExists(result.code());
    }

    @Test
    void shouldClearAllDurationsThroughTheInterceptorStack_whenNativeSignatureAndEditorBothSubmitEmptyInputs() throws Exception {
        FIELDS.forEach(field -> add(field, "", ""));

        BoundSave result = executeFullSave();

        assertThat(result.code()).isEqualTo("list");
        assertThat(result.action().hasFieldErrors()).isFalse();
        assertThat(result.conversionErrors()).isEmpty();
        assertThat(mockResponse.getStatus()).isEqualTo(200);
        FIELDS.forEach(field -> assertThat(duration(form.getCaseNote(), field)).isNull());
        assertThat(form.getCaseNote().getId()).isEqualTo(600L);
        verify(locks).merge(lock);
        assertActualResultExists(result.code());
    }

    static Stream<Arguments> refusedDurations() {
        return FIELDS.stream().flatMap(field -> Stream.of(
                Arguments.of(field, new String[]{"bad"}),
                Arguments.of(field, new String[]{"1", "bad"}),
                Arguments.of(field, new String[]{"bad", "1"}),
                Arguments.of(field, new String[]{"1", "2"}),
                Arguments.of(field, new String[]{"", "1"}),
                Arguments.of(field, new String[]{"1", ""})));
    }

    @ParameterizedTest
    @MethodSource("refusedDurations")
    void shouldRefuseInTheActionAndKeepTheExactDraft_whenAnyDurationOccurrenceIsInvalid(String field, String[] values) throws Exception {
        add(field, values);

        BoundSave result = executeFullSave();

        assertThat(result.code()).isEqualTo("windowCloseError");
        assertThat(result.action().hasFieldErrors()).isFalse();
        assertThat(result.action().hasActionErrors()).isFalse();
        assertThat(result.conversionErrors()).isEmpty();
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(mockRequest.getAttribute("noteInputRejected")).isEqualTo(Boolean.TRUE);
        assertThat(mockRequest.getAttribute("unsavedNoteText")).isEqualTo(DRAFT);
        assertThat(form.getCaseNote()).isSameAs(note);
        assertThat(note.getNote()).isEqualTo("Original FAKE note");
        assertThat(note.getHistory()).isEqualTo("Original history");
        assertThat(FIELDS.stream().map(name -> duration(note, name)).toList()).containsExactly(5, 6, 7, 8);
        assertThat(lock.getNoteId()).isEqualTo(501L);
        verify(manager, never()).saveCaseManagementNote(any(), any(), anyList(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        verify(locks, never()).merge(any());
        assertActualResultExists(result.code());
    }

    static Stream<Arguments> equivalentPatientParameters() {
        return Stream.of(
                Arguments.of(new String[]{"101", "101"}, null),
                Arguments.of(new String[]{"00101", "101"}, null),
                Arguments.of(new String[]{"101"}, new String[]{"000101"}),
                Arguments.of(new String[]{"00101", "101"}, new String[]{"101", "000101"}),
                Arguments.of(null, new String[]{"00101", "101"}));
    }

    @ParameterizedTest
    @MethodSource("equivalentPatientParameters")
    void shouldRestoreTheCanonicalEditorThroughStruts_whenEquivalentPatientParametersAreRepeated(
            String[] camelCase, String[] legacy) throws Exception {
        setPatientParameters(camelCase, legacy);
        var selectedIssues = form.getIssueCheckList();

        BoundSave result = executeFullSave();

        assertThat(result.code()).isEqualTo("list");
        assertThat(result.action().getDemographicNo()).isEqualTo("101");
        assertThat(result.action().getIssueCheckList()).isSameAs(selectedIssues);
        assertThat(result.action().hasFieldErrors()).isFalse();
        assertThat(result.conversionErrors()).isEmpty();
        assertThat(mockResponse.getStatus()).isEqualTo(200);
        assertThat(form.getCaseNote().getId()).isEqualTo(600L);
        assertThat(form.getCaseNote().getDemographic_no()).isEqualTo("101");
        assertThat(form.getCaseNote().getNote()).isEqualTo(DRAFT.trim());
        verify(manager).saveCaseManagementNote(any(), any(), anyList(), any(), any(), anyBoolean(),
                any(), any(), any(), any(), any(), any(), any());
        assertActualResultExists(result.code());
    }

    static Stream<Arguments> conflictingPatientParameters() {
        return Stream.of(
                Arguments.of(new String[]{"101", "202"}, null),
                Arguments.of(new String[]{"101"}, new String[]{"202"}),
                Arguments.of(null, new String[]{"101", "202"}),
                Arguments.of(new String[]{"101", "bad"}, null));
    }

    @ParameterizedTest
    @MethodSource("conflictingPatientParameters")
    void shouldRefuseBeforeRestoringOrSavingThroughStruts_whenPatientParametersConflict(
            String[] camelCase, String[] legacy) throws Exception {
        setPatientParameters(camelCase, legacy);

        BoundSave result = executeFullSave();

        assertThat(result.code()).isEqualTo("none");
        assertThat(result.action().hasFieldErrors()).isFalse();
        assertThat(result.conversionErrors()).isEmpty();
        assertThat(mockResponse.getStatus()).isEqualTo(400);
        assertThat(mockRequest.getParameter("caseNote_note")).isEqualTo(DRAFT);
        assertThat(form.getCaseNote()).isSameAs(note);
        assertThat(note.getNote()).isEqualTo("Original FAKE note");
        assertThat(note.getHistory()).isEqualTo("Original history");
        assertThat(FIELDS.stream().map(name -> duration(note, name)).toList()).containsExactly(5, 6, 7, 8);
        assertThat(lock.getNoteId()).isEqualTo(501L);
        verifyNoInteractions(manager);
        verify(locks, never()).merge(any());
    }

    private void setPatientParameters(String[] camelCase, String[] legacy) {
        requestParameters.remove("demographicNo");
        mockRequest.removeParameter("demographicNo");
        if (camelCase != null) add("demographicNo", camelCase);
        if (legacy != null) add("demographic_no", legacy);
    }

    private void add(String field, String... values) {
        requestParameters.put(field, values);
        mockRequest.setParameter(field, values);
    }

    private BoundSave executeFullSave() throws Exception {
        mockRequest.getServletContext().setAttribute(
                WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, applicationContext);
        Dispatcher dispatcher = new Dispatcher(mockRequest.getServletContext(), Map.of(
                "config", "struts-default.xml,struts-plugin.xml,struts-chart-duration-binding-test.xml"));
        dispatcher.init();
        try {
            Container container = dispatcher.getContainer();
            ActionContext bindingContext = ActionContext.of(new HashMap<>(ActionContext.getContext().getContextMap()))
                    .withContainer(container).withServletContext(mockRequest.getServletContext())
                    .withServletRequest(mockRequest).withServletResponse(mockResponse)
                    .withSession(new SessionMap(mockRequest)).withParameters(HttpParameters.create(requestParameters).build());
            bindingContext.bind();
            ActionProxy proxy = container.getInstance(ActionProxyFactory.class).createActionProxy(
                    "/", "chart-duration-binding-test", null, bindingContext.getContextMap(), false, true);
            CaseManagementEntry2Action action = (CaseManagementEntry2Action) proxy.getAction();
            action.caseManagementMgr = manager;
            ReflectionTestUtils.setField(action, "securityInfoManager", mockSecurityInfoManager);
            ReflectionTestUtils.setField(action, "casemgmtNoteLockDao", locks);
            String code = proxy.execute();
            Map<String, ?> conversion = bindingContext.getConversionErrors();
            return new BoundSave(action, code, conversion == null ? Map.of() : new HashMap<>(conversion));
        } finally {
            dispatcher.cleanup();
            Dispatcher.clearInstance();
            setUpActionContext();
        }
    }

    private static Integer duration(CaseManagementNote value, String field) {
        return switch (field) {
            case "hourOfEncounterTime" -> value.getHourOfEncounterTime();
            case "minuteOfEncounterTime" -> value.getMinuteOfEncounterTime();
            case "hourOfEncTransportationTime" -> value.getHourOfEncTransportationTime();
            case "minuteOfEncTransportationTime" -> value.getMinuteOfEncTransportationTime();
            default -> throw new IllegalArgumentException(field);
        };
    }

    private static void assertActualResultExists(String code) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var document = factory.newDocumentBuilder().parse(
                Path.of("src/main/webapp/WEB-INF/classes/struts-clinical.xml").toFile());
        var actions = document.getElementsByTagName("action");
        List<String> results = new ArrayList<>();
        for (int i = 0; i < actions.getLength(); i++) {
            var action = (org.w3c.dom.Element) actions.item(i);
            if (!"CaseManagementEntry".equals(action.getAttribute("name"))) continue;
            var mappings = action.getElementsByTagName("result");
            for (int j = 0; j < mappings.getLength(); j++) {
                results.add(((org.w3c.dom.Element) mappings.item(j)).getAttribute("name"));
            }
        }
        assertThat(results).contains(code);
    }

    private record BoundSave(CaseManagementEntry2Action action, String code, Map<String, ?> conversionErrors) { }
}
