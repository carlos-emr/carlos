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
package io.github.carlos_emr.carlos.app.contract;

import io.github.carlos_emr.carlos.admin.web.SecurityAddSecurity2Action;
import io.github.carlos_emr.carlos.admin.web.SecurityDelete2Action;
import io.github.carlos_emr.carlos.admin.web.SecurityUpdate2Action;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.eform.actions.DelEForm2Action;
import io.github.carlos_emr.carlos.login.UploadLoginText2Action;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Aggregated contract test: every {@code *2Action} class registered as a
 * mutator MUST reject GET/HEAD before any mutation side-effect fires.
 *
 * <p><b>Why this test exists.</b> CARLOS is mid-migration to gate
 * state-changing JSPs behind Struts2 {@code *2Action} classes. Each gate
 * enforces its own HTTP-method restrictions. Before the migration,
 * {@code HttpMethodGuardFilter} caught GET-on-mutator attempts by filename
 * and the filter's unit tests pinned that behavior. Post-migration the real
 * enforcement lives in {@code execute()} — this test is the aggregated
 * contract that proves, across <em>every</em> migrated mutator:
 *
 * <ol>
 *   <li>GET returns {@link HttpServletResponse#SC_METHOD_NOT_ALLOWED} (or
 *       throws {@link SecurityException}), and</li>
 *   <li>no mutation dependency (DAO, manager, event publisher, file writer)
 *       is invoked on the reject path — verified via Mockito
 *       {@link Mockito#verifyNoInteractions(Object...)} on every bean
 *       obtained during construction/{@code execute()}.</li>
 * </ol>
 *
 * <p><b>Adding a new mutator 2Action.</b> The {@link #discoveryCandidatesMustBeRegistered()}
 * test scans {@code src/main/java} for any {@code *2Action.java} in an audited
 * slice, or explicitly registered legacy class, containing both
 * {@code SC_METHOD_NOT_ALLOWED} and a POST method check
 * and fails the build if the class is not listed here. New mutators must be
 * registered in one of:
 *
 * <ul>
 *   <li>{@link #unconditionalMutators()} — actions that reject GET on every
 *       request path, regardless of parameters. The parameterized test drives
 *       these directly.</li>
 *   <li>{@link #CONDITIONAL_MUTATORS} — actions whose GET-rejection depends on
 *       request parameters (mutation-intent checks like
 *       {@code submit=Save}, presence of {@code statement}, etc.). These must
 *       have their own focused {@code @Tag("unit")} test covering the
 *       mutation-intent GET-rejection path — see e.g.
 *       {@code WLMutation2ActionsTest}, {@code ViewAppointmentSelfPost2ActionTest},
 *       {@code ViewDecision2ActionTest}.</li>
 *   <li>{@link #NON_MUTATOR_GATES} — read-scope gates (e.g.
 *       {@code ViewAppointment2Action}) that happen to include a
 *       {@code SC_METHOD_NOT_ALLOWED} branch for truly unsupported methods
 *       but permit GET as part of their normal operation.</li>
 * </ul>
 *
 * @since 2026-04-22
 */
@Tag("unit")
@Tag("security")
@Tag("contract")
@DisplayName("Mutator 2Action GET/HEAD rejection contract")
class MutatorActionGetRejectionContractTest {

    /**
     * Unconditional POST-only mutator 2Actions. Each entry is
     * {@code {fully-qualified-class-name, securityObject, privilegeLevel}}.
     * The parameterized test drives GET against every entry.
     */
    static Stream<Arguments> unconditionalMutators() {
        return Stream.of(
            // --- login ---
            // Logout2Action is in io.github.carlos_emr.carlos.login, which is not yet in
            // IN_SCOPE_PACKAGE_PREFIXES, so the discovery scan won't auto-find it.
            // Registered explicitly here because it is an unconditional mutator:
            // session.invalidate() and cookie deletion fire on every POST regardless of params.
            // No hasPrivilege() is called (see Logout2Action.execute() for why), so the
            // privilege-tuple fields below are left as empty strings — the contract assertion
            // skips the privilege check when hasPrivilege is never invoked.
            Arguments.of("io.github.carlos_emr.carlos.login.Logout2Action", "", ""),
            Arguments.of("io.github.carlos_emr.carlos.login.UploadLoginText2Action",
                    "_admin", "w"),
            // --- appointment ---
            Arguments.of("io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentAddRecord2Action",
                    "_appointment", "w"),
            Arguments.of("io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentCutRecord2Action",
                    "_appointment", "d"),
            Arguments.of("io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentDeleteRecord2Action",
                    "_appointment", "d"),
            Arguments.of("io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentUpdateRecord2Action",
                    "_appointment", "w"),
            // --- billing ---
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSaveBilling2Action",
                    "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingUpdateBilling2Action",
                    "_billing", "w"),
            // --- admin ---
            Arguments.of("io.github.carlos_emr.carlos.admin.web.ClinicNbrManage2Action",
                    "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.admin.web.SecurityAddSecurity2Action",
                    "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.admin.web.SecurityDelete2Action",
                    "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.admin.web.SecurityUpdate2Action",
                    "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action",
                    "_admin.eform", "w"),
            Arguments.of("io.github.carlos_emr.carlos.eform.actions.AddEForm2Action",
                    "_eform", "w"),
            // --- clinical measurements / flowsheets ---
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctMeasurements2Action",
                    "_measurement", "w"),
            Arguments.of("io.github.carlos_emr.carlos.commn.web.FlowSheetCustom2Action",
                    "_flowsheet", "w"),
            // --- report ---
            Arguments.of("io.github.carlos_emr.carlos.report.pageUtil.DbManageProvider2Action",
                    "_admin.reporting", "w"),
            Arguments.of("io.github.carlos_emr.carlos.report.pageUtil.DbReportAgeSex2Action",
                    "_report", "r"),
            // --- signature ---
            Arguments.of("io.github.carlos_emr.carlos.signature.action.SaveSignatureUpload2Action",
                    "_con", "w"),
            // --- messenger ---
            Arguments.of("io.github.carlos_emr.carlos.messenger.pageUtil.MsgTransferPostItems2Action",
                    "_msg", "w"),
            Arguments.of("io.github.carlos_emr.carlos.messenger.pageUtil.MsgAttachPDF2Action",
                    "_msg", "w"),
            Arguments.of("io.github.carlos_emr.carlos.messenger.pageUtil.MsgAdjustAttachments2Action",
                    "_msg", "w"),
            // --- tickler ---
            Arguments.of("io.github.carlos_emr.carlos.tickler.pageUtil.DbTicklerAdd2Action",
                    "_tickler", "w"),
            Arguments.of("io.github.carlos_emr.carlos.tickler.pageUtil.DbTicklerMain2Action",
                    "_tickler", "u"),
            Arguments.of("io.github.carlos_emr.carlos.tickler.pageUtil.DbTicklerDemoMain2Action",
                    "_tickler", "u"),
            // --- schedule ---
            Arguments.of("io.github.carlos_emr.carlos.schedule.web.ScheduleDateSave2Action",
                    "_appointment", "w"),
            // --- waitinglist ---
            Arguments.of("io.github.carlos_emr.carlos.waitinglist.pageUtil.WLAdd2WaitingList2Action",
                    "_demographic", "w"),
            Arguments.of("io.github.carlos_emr.carlos.waitinglist.pageUtil.WLRemoveFromWaitingList2Action",
                    "_demographic", "w"),
            // --- eform ---
            Arguments.of("io.github.carlos_emr.carlos.eform.actions.DelEForm2Action",
                    "_admin.eform", "w")
        );
    }

    /**
     * Conditional mutators — reject GET only when specific mutation-intent
     * parameters are present. Each of these MUST have a dedicated focused
     * test covering its GET-rejection path; this list only exists so the
     * {@link #discoveryCandidatesMustBeRegistered()} scan does not flag them.
     *
     * <p>If you add to this list, also add the corresponding focused test.
     */
    private static final Set<String> CONDITIONAL_MUTATORS = Set.of(
        // Appointment: rejects GET when targeting specific mutation URIs
        // (appointmentaddrecordprint, groupappt param).
        "io.github.carlos_emr.carlos.appointment.gate.ViewAppointmentSelfPost2Action",
        // Decision: rejects GET when submit param starts with "save".
        "io.github.carlos_emr.carlos.decision.gate.ViewDecision2Action",
        // HRM: rejects GET when statement param is present.
        "io.github.carlos_emr.carlos.hospitalReportManager.HRMStatementModify2Action",
        // Login gate: GET renders the selector, but selectedFacilityId is mutation intent.
        "io.github.carlos_emr.carlos.login.gate.SelectFacility2Action",
        // Ontario billing: dual-purpose pages reject GET only when mutation-intent params exist.
        "io.github.carlos_emr.carlos.billings.ca.on.web.BatchBill2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.BillingDocumentErrorReportUpload2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.MoveMohFiles2Action",
        // BC Teleplan: default page view permits GET; method dispatches are POST-only.
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ManageTeleplan2Action",
        // Messenger admin: rejects GET on form-save method invocations.
        "io.github.carlos_emr.carlos.messenger.config.pageUtil.MsgMessengerAdmin2Action",
        // Provider document descriptions: read methods permit GET; write methods are POST-only.
        "io.github.carlos_emr.carlos.provider.web.DocumentDescriptionTemplate2Action",
        // Document manager: read methods permit GET; addIncomingDocument is POST-only.
        "io.github.carlos_emr.carlos.documentManager.actions.ManageDocument2Action",
        // Admin API clients: list methods permit GET; add/delete are POST-only.
        "io.github.carlos_emr.carlos.admin.web.ClientManage2Action",
        // Schedule: all below reject GET on Save/Delete/mutation-intent params.
        "io.github.carlos_emr.carlos.schedule.web.ScheduleCreateDate2Action",
        "io.github.carlos_emr.carlos.schedule.web.ScheduleEditTemplate2Action",
        "io.github.carlos_emr.carlos.schedule.web.ScheduleHolidaySetting2Action",
        "io.github.carlos_emr.carlos.schedule.web.ScheduleTemplateApplying2Action",
        "io.github.carlos_emr.carlos.schedule.web.ScheduleTemplateCodeSetting2Action",
        // Waitinglist: reject GET on Save/Delete submit values.
        "io.github.carlos_emr.carlos.waitinglist.pageUtil.WLEditWaitingListName2Action",
        "io.github.carlos_emr.carlos.waitinglist.pageUtil.WLSetupDisplayWaitingList2Action"
    );

    /**
     * Actions whose source contains both {@code SC_METHOD_NOT_ALLOWED} and a
     * {@code "POST".equalsIgnoreCase(...)} check but which are NOT mutators —
     * they are read-scope gates that permit GET (and HEAD, usually POST) and
     * only return 405 for truly unsupported methods like DELETE/PUT. Also
     * includes any {@code *2Action} whose only 405 branch guards an idempotent
     * probe (e.g. session heartbeat) rather than a mutation.
     *
     * <p>Keep this list minimal; any new entry means the class has both a
     * read and a mutation path and should normally be classified as a
     * {@link #CONDITIONAL_MUTATORS} entry instead.
     */
    private static final Set<String> NON_MUTATOR_GATES = Set.of(
        // Read-scope gates — permit GET, only 405 truly unsupported methods.
        "io.github.carlos_emr.carlos.appointment.gate.ViewAppointment2Action",
        "io.github.carlos_emr.carlos.appointment.gate.ViewAppointmentWrite2Action",
        "io.github.carlos_emr.carlos.report.gate.ViewReport2Action"
    );

    /**
     * Package prefixes for fully audited slices currently in scope for this
     * aggregated contract test, per the acceptance criteria of the originating
     * issue.
     *
     * <p>Classes outside these prefixes are filtered out by the discovery scan
     * unless they are listed in {@link #IN_SCOPE_EXPLICIT_CLASSES}. When a new
     * slice migration lands, add its package prefix here only after the slice's
     * existing guarded actions have been audited and classified. For legacy-heavy
     * slices with a single migrated mutator, add the specific class to
     * {@link #IN_SCOPE_EXPLICIT_CLASSES} instead.
     *
     * <p>Candidate classes elsewhere in the codebase (admin, billings,
     * dxresearch, prescript, prevention, providers, lab, etc.)
     * typically have their own per-class unit tests for the POST-only
     * contract; they are expected to be folded into this aggregated test in
     * follow-up waves.
     */
    private static final List<String> IN_SCOPE_PACKAGE_PREFIXES = List.of(
        "io.github.carlos_emr.carlos.appointment.",
        "io.github.carlos_emr.carlos.decision.",
        "io.github.carlos_emr.carlos.documentManager.",
        "io.github.carlos_emr.carlos.hospitalReportManager.",
        "io.github.carlos_emr.carlos.messenger.",
        "io.github.carlos_emr.carlos.report.",
        "io.github.carlos_emr.carlos.schedule.",
        "io.github.carlos_emr.carlos.signature.",
        "io.github.carlos_emr.carlos.tickler.",
        "io.github.carlos_emr.carlos.waitinglist."
    );

    /**
     * Individual migrated actions from legacy-heavy slices that are covered by
     * this contract before the entire slice is ready to move under
     * {@link #IN_SCOPE_PACKAGE_PREFIXES}. Keep this list short and deliberate:
     * adding a class here means it is registered in one of the contract
     * manifests above and participates in discovery drift checks.
     */
    private static final Set<String> IN_SCOPE_EXPLICIT_CLASSES = Set.of(
        "io.github.carlos_emr.carlos.admin.web.ClientManage2Action",
        "io.github.carlos_emr.carlos.admin.web.ClinicNbrManage2Action",
        "io.github.carlos_emr.carlos.admin.web.SecurityAddSecurity2Action",
        "io.github.carlos_emr.carlos.admin.web.SecurityDelete2Action",
        "io.github.carlos_emr.carlos.admin.web.SecurityUpdate2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSaveBilling2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingUpdateBilling2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ManageTeleplan2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.BatchBill2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.BillingDocumentErrorReportUpload2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.MoveMohFiles2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.ScheduleOfBenefitsUpload2Action",
        "io.github.carlos_emr.carlos.commn.web.FlowSheetCustom2Action",
        "io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctMeasurements2Action",
        "io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action",
        "io.github.carlos_emr.carlos.login.UploadLoginText2Action",
        "io.github.carlos_emr.carlos.login.gate.SelectFacility2Action",
        "io.github.carlos_emr.carlos.provider.web.DocumentDescriptionTemplate2Action",
        // eform slice: only DelEForm2Action is registered; broader slice audit tracked in issue #2828.
        "io.github.carlos_emr.carlos.eform.actions.DelEForm2Action"
    );

    @ParameterizedTest(name = "{0} rejects GET and HEAD without side-effects")
    @MethodSource("unconditionalMutators")
    @DisplayName("should reject GET/HEAD with 405 (or SecurityException) and fire no mutation dependency")
    void shouldRejectGetAndHead_withoutAnyMutationSideEffect(
            String className, String privilegeObject, String privilegeLevel) throws Exception {
        // Drive both GET and HEAD against every registered unconditional
        // mutator. All known gates guard with "POST".equalsIgnoreCase(method),
        // so both non-POST verbs take the same reject path — but the issue's
        // acceptance criteria calls out both verbs explicitly.
        assertRejectsUnsafeMethod(className, privilegeObject, privilegeLevel, "GET");
        assertRejectsUnsafeMethod(className, privilegeObject, privilegeLevel, "HEAD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"add", "delete"})
    @DisplayName("ClientManage2Action should reject GET for mutation dispatches")
    void shouldRejectGet_forClientManageMutationDispatch(String method) throws Exception {
        assertRejectsUnsafeMethod(
                "io.github.carlos_emr.carlos.admin.web.ClientManage2Action",
                "_admin",
                "w",
                "GET",
                Map.of("method", method));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("ManageDocument2Action should reject unsafe methods for addIncomingDocument")
    void shouldRejectUnsafeMethod_forManageDocumentAddIncomingDocumentDispatch(String httpMethod) throws Exception {
        assertRejectsUnsafeMethod(
                "io.github.carlos_emr.carlos.documentManager.actions.ManageDocument2Action",
                "_edoc",
                "w",
                httpMethod,
                Map.of("method", "addIncomingDocument"));
    }

    private static void assertRejectsUnsafeMethod(
            String className, String privilegeObject, String privilegeLevel, String httpMethod)
            throws Exception {
        assertRejectsUnsafeMethod(className, privilegeObject, privilegeLevel, httpMethod, Collections.emptyMap());
    }

    private static void assertRejectsUnsafeMethod(
            String className, String privilegeObject, String privilegeLevel, String httpMethod,
            Map<String, String> requestParams)
            throws Exception {

        Class<?> actionClass = Class.forName(className);

        // Mock SecurityInfoManager granting the declared privilege — we want the
        // GET rejection to land regardless of whether the class checks privilege
        // before or after the method check.
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        // Grant every privilege so auth never stands in the way of the
        // method-check path. Some actions check multiple privileges (e.g.
        // DbReportAgeSex2Action accepts either _report r or _admin.reporting r).
        // Use nullable(String.class), not isNull(), so this broad grant covers
        // both null and non-null demographic/context targets.
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), any(String.class), any(String.class), nullable(String.class)))
            .thenReturn(true);
        when(securityInfoManager.hasPrivilege(
                any(LoggedInInfo.class), any(String.class), any(String.class), anyInt()))
            .thenReturn(true);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod(httpMethod);
        request.setContextPath("/carlos");
        requestParams.forEach(request::addParameter);
        MockHttpServletResponse response = new MockHttpServletResponse();

        Map<Class<?>, Object> autoMocks = new HashMap<>();
        CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
        when(methodSecurity.hasAdminWrite()).thenReturn(true);
        when(methodSecurity.hasPrivilege(any(String.class), any(String.class))).thenReturn(true);

        try (MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class);
             MockedStatic<LoggedInInfo> loggedInInfo = mockStatic(LoggedInInfo.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LogAction> logAction = mockStatic(LogAction.class)) {

            servletCtx.when(ServletActionContext::getRequest).thenReturn(request);
            servletCtx.when(ServletActionContext::getResponse).thenReturn(response);

            LoggedInInfo sessionInfo = mock(LoggedInInfo.class);
            loggedInInfo.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(sessionInfo);

            // Auto-mock every bean SpringUtils.getBean is asked for. Cache so
            // the same Class always returns the same mock (actions typically
            // resolve their beans once at construction time, but some call
            // getBean inline inside execute()).
            springUtils.when(() -> SpringUtils.getBean(any(Class.class)))
                    .thenAnswer(inv -> {
                        Class<?> beanType = inv.getArgument(0);
                        if (beanType.equals(SecurityInfoManager.class)) {
                            return securityInfoManager;
                        }
                        if (beanType.equals(CarlosMethodSecurity.class)) {
                            return methodSecurity;
                        }
                        return autoMocks.computeIfAbsent(beanType, Mockito::mock);
                    });

            Object action = instantiateAction(actionClass, autoMocks);

            Throwable caught = null;
            Object result = null;
            try {
                result = actionClass.getMethod("execute").invoke(action);
            } catch (InvocationTargetException ite) {
                caught = ite.getTargetException();
            }

            if (caught != null) {
                // SecurityException is an acceptable reject mode for actions
                // that check privilege before HTTP method. The test session
                // granted every privilege, so a SecurityException here only
                // happens if the action explicitly throws on the reject path.
                assertThat(caught)
                    .as("%s to mutator %s must be rejected; if thrown, must be SecurityException",
                        httpMethod, className)
                    .isInstanceOf(SecurityException.class);
            } else {
                assertThat(result)
                    .as("%s to mutator %s must return NONE after sending 405", httpMethod, className)
                    .isEqualTo(ActionSupport.NONE);
                assertThat(response.getStatus())
                    .as("%s to mutator %s must send 405", httpMethod, className)
                    .isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }

            // No mutation dependency — DAOs, managers, event publishers —
            // should have been invoked on the reject path. SecurityInfoManager
            // is excluded above (it is an auth gate, not a mutation dep) and is
            // not put in the autoMocks map to begin with.
            for (Map.Entry<Class<?>, Object> entry : autoMocks.entrySet()) {
                verifyNoInteractions(entry.getValue());
            }
            assertDeclaredPrivilegeWasCheckedIfAuthWasReached(
                    securityInfoManager, className, privilegeObject, privilegeLevel, httpMethod);
        }
    }

    private static Object instantiateAction(Class<?> actionClass, Map<Class<?>, Object> autoMocks)
            throws Exception {
        if (actionClass.equals(DelEForm2Action.class)) {
            return new DelEForm2Action(mock(SecurityInfoManager.class));
        }
        if (actionClass.equals(SecurityDelete2Action.class)) {
            CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
            when(methodSecurity.hasAdminWrite()).thenReturn(true);
            SecurityDao securityDao = (SecurityDao) autoMocks.computeIfAbsent(SecurityDao.class, Mockito::mock);
            return new SecurityDelete2Action(securityDao, methodSecurity);
        }
        if (actionClass.equals(SecurityAddSecurity2Action.class)) {
            return new SecurityAddSecurity2Action(methodSecurity);
        }
        if (actionClass.equals(SecurityUpdate2Action.class)) {
            return new SecurityUpdate2Action(methodSecurity);
        }
        if (actionClass.equals(UploadLoginText2Action.class)) {
            return new UploadLoginText2Action(securityInfoManager);
        }
        return actionClass.getDeclaredConstructor().newInstance();
    }

    private static void assertDeclaredPrivilegeWasCheckedIfAuthWasReached(
            SecurityInfoManager securityInfoManager, String className, String privilegeObject,
            String privilegeLevel, String httpMethod) {
        boolean anyPrivilegeCheck = Mockito.mockingDetails(securityInfoManager).getInvocations().stream()
                .anyMatch(inv -> "hasPrivilege".equals(inv.getMethod().getName()));
        if (!anyPrivilegeCheck) {
            return;
        }

        boolean declaredPrivilegeWasChecked = Mockito.mockingDetails(securityInfoManager).getInvocations().stream()
                .filter(inv -> "hasPrivilege".equals(inv.getMethod().getName()))
                .anyMatch(inv -> {
                    Object[] args = inv.getArguments();
                    return args.length >= 3
                            && privilegeObject.equals(args[1])
                            && privilegeLevel.equals(args[2]);
                });

        assertThat(declaredPrivilegeWasChecked)
            .as("%s to mutator %s reached authorization, but no hasPrivilege call used "
              + "the manifest privilege tuple %s/%s", httpMethod, className, privilegeObject, privilegeLevel)
            .isTrue();
    }

    /**
     * Walks {@code src/main/java} and fails if any {@code *2Action.java}
     * containing both a {@code SC_METHOD_NOT_ALLOWED} reference and a
     * literal POST method comparison is not registered in one of
     * {@link #unconditionalMutators()}, {@link #CONDITIONAL_MUTATORS}, or
     * {@link #NON_MUTATOR_GATES}.
     *
     * <p>When this test fails, add the new class to the appropriate list and
     * (for conditional mutators) add a focused test that drives a GET with
     * mutation intent and asserts 405 / SecurityException + no side-effects.
     */
    @Test
    @DisplayName("discovery: every *2Action with a SC_METHOD_NOT_ALLOWED + POST check must be registered")
    void discoveryCandidatesMustBeRegistered() throws IOException {
        Path sourceRoot = Paths.get("src", "main", "java");
        // The test must run from the repo root (default for surefire). If the
        // source root is missing, the scan cannot run — fail loudly rather
        // than silently passing.
        assertThat(sourceRoot)
            .as("Expected src/main/java relative to the test working directory; "
              + "this test must run from the project root (default surefire CWD)")
            .exists();

        Set<String> registered = new HashSet<>();
        unconditionalMutators().forEach(a -> registered.add((String) a.get()[0]));
        registered.addAll(CONDITIONAL_MUTATORS);
        registered.addAll(NON_MUTATOR_GATES);

        List<String> candidates = scanCandidates(sourceRoot);
        Set<String> unregistered = new TreeSet<>();
        for (String candidate : candidates) {
            if (!registered.contains(candidate)) {
                unregistered.add(candidate);
            }
        }

        assertThat(unregistered)
            .as("New *2Action classes in the in-scope slices (%s) contain SC_METHOD_NOT_ALLOWED + POST "
              + "checks but are not registered in MutatorActionGetRejectionContractTest. "
              + "Register each class in ONE of:\n"
              + "  - unconditionalMutators() — always rejects GET regardless of params\n"
              + "  - CONDITIONAL_MUTATORS     — rejects GET only for specific mutation-intent params "
              + "(must have dedicated focused test)\n"
              + "  - NON_MUTATOR_GATES        — read-scope gate that permits GET and only 405s on "
              + "truly unsupported methods\n"
              + "See the class-level JavaDoc for guidance.", IN_SCOPE_PACKAGE_PREFIXES)
            .isEmpty();
    }

    private static List<String> scanCandidates(Path sourceRoot) throws IOException {
        List<String> out = new ArrayList<>();
        List<Path> actionSources;
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            actionSources = paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("2Action.java"))
                    .toList();
        }
        for (Path actionSource : actionSources) {
            String className = fileToClassName(sourceRoot, actionSource);
            if (!isInScope(className)) {
                continue;
            }
            String source;
            try {
                source = Files.readString(actionSource);
            } catch (IOException e) {
                throw new IOException("Unable to read in-scope 2Action source during mutator discovery: "
                        + actionSource, e);
            }
            if (source.contains("SC_METHOD_NOT_ALLOWED")
                    && (source.contains("\"POST\".equals(")
                        || source.contains("\"POST\".equalsIgnoreCase(")
                        || source.contains(".equalsIgnoreCase(\"POST\")"))) {
                out.add(className);
            }
        }
        Collections.sort(out);
        return out;
    }

    private static boolean isInScope(String fullyQualifiedClassName) {
        if (IN_SCOPE_EXPLICIT_CLASSES.contains(fullyQualifiedClassName)) {
            return true;
        }
        for (String prefix : IN_SCOPE_PACKAGE_PREFIXES) {
            if (fullyQualifiedClassName.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String fileToClassName(Path sourceRoot, Path file) {
        Path relative = sourceRoot.relativize(file);
        String s = relative.toString().replace(File.separatorChar, '.');
        return s.substring(0, s.length() - ".java".length());
    }
}
