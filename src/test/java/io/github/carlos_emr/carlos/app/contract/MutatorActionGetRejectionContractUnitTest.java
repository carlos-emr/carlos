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

import io.github.carlos_emr.carlos.admin.web.SaveProviderLinkingRules2Action;
import io.github.carlos_emr.carlos.admin.web.SecurityAddSecurity2Action;
import io.github.carlos_emr.carlos.admin.web.SecurityDelete2Action;
import io.github.carlos_emr.carlos.admin.web.SecurityUpdate2Action;
import io.github.carlos_emr.carlos.app.HttpMethodGuardFilter;
import io.github.carlos_emr.carlos.commn.dao.EReferAttachmentDao;
import io.github.carlos_emr.carlos.commn.dao.SecurityDao;
import io.github.carlos_emr.carlos.commn.service.MyGroupMembershipService;
import io.github.carlos_emr.carlos.documentManager.AttachmentOwnershipService;
import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.eform.actions.DelEForm2Action;
import io.github.carlos_emr.carlos.eform.actions.RestoreEForm2Action;
import io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.ERefer2Action;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementGroup2Action;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDefineNewMeasurementGroup2Action;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.service.MeasurementGroupCreationService;
import io.github.carlos_emr.carlos.lab.service.ProviderLinkingRulesService;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.providers.pageUtil.DeleteMyGroupMembers2Action;
import io.github.carlos_emr.carlos.security.CarlosMethodSecurity;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
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
 * slice, or explicitly registered legacy class, containing a POST method check
 * plus either {@code SC_METHOD_NOT_ALLOWED} or a call to the shared
 * {@code methodNotAllowed(...)} helper (as the patient portal actions use)
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
 *       {@code WLMutation2ActionsUnitTest}, {@code ViewAppointmentSelfPost2ActionUnitTest},
 *       {@code ViewDecision2ActionUnitTest}.</li>
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
class MutatorActionGetRejectionContractUnitTest {

    /**
     * Unconditional POST-only mutator 2Actions. Each entry is
     * {@code {fully-qualified-class-name, securityObject, privilegeLevel}}.
     * The parameterized test drives GET against every entry.
     */
    static Stream<Arguments> unconditionalMutators() {
        return Stream.of(
            Arguments.of("io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetConfig2Action", "_prevention", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmInitConfigDeleteImmuSet2Action", "_demographic", "w"),
            Arguments.of("io.github.carlos_emr.carlos.messenger.config.pageUtil.MsgMessengerCreateGroup2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.lab.pageUtil.FileLabsOnBehalf2Action", "_lab", "w"),
            Arguments.of("io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentAddRecord2Action", "_appointment", "w"),
            Arguments.of("io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentCutRecord2Action", "_appointment", "d"),
            Arguments.of("io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentDeleteRecord2Action", "_appointment", "d"),
            Arguments.of("io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentUpdateRecord2Action", "_appointment", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSaveBilling2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingUpdateBilling2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ReceivePayment2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.WCBAction22Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingEditCode2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingDeleteNoAppt2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingDeleteWithoutNo2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.quickbilling.QuickBillingBCSave2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SaveBillingPreferences2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SaveAssoc2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.DeleteServiceCodeAssoc2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.AddReferralDoc2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingAddCode2Action", "_billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.DeletePrivateCode2Action", "_admin.billing", "w"),
            Arguments.of("io.github.carlos_emr.carlos.eform.actions.UnRemEForm2Action", "_eform", "w"),
            Arguments.of("io.github.carlos_emr.carlos.admin.web.ClinicNbrManage2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.admin.web.SecurityAddSecurity2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.admin.web.SecurityDelete2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.admin.web.SecurityUpdate2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.admin.web.SaveProviderLinkingRules2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action", "_admin.eform", "w"),
            Arguments.of("io.github.carlos_emr.carlos.form.pageUtil.FrmSelect2Action", "_form", "w"),
            Arguments.of("io.github.carlos_emr.carlos.eform.actions.AddEForm2Action", "_eform", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequest2Action", "_con", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormFax2Action", "_con", "r"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.ERefer2Action", "_con", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.pageUtil.EctSaveEncounter2Action", "_demographic", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctMeasurements2Action", "_measurement", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.HealthTrackerUpdate2Action", "_measurement", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDeleteData2Action", "_measurement", "d"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDefineNewMeasurementGroup2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementGroup2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctEditMeasurementGroup2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctEditMeasurementStyle2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctMeasurementGroupDecisionSupport2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementType2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasuringInstruction2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDeleteMeasurementTypes2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementStyleSheet2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctDeleteMeasurementStyleSheet2Action", "_admin.measurements", "w"),
            Arguments.of("io.github.carlos_emr.carlos.commn.web.FlowSheetCustom2Action", "_flowsheet", "w"),
            Arguments.of("io.github.carlos_emr.carlos.commn.web.FlowSheetDrug2Action", "_demographic", "w"),
            Arguments.of("io.github.carlos_emr.carlos.report.pageUtil.DbManageProvider2Action", "_admin.reporting", "w"),
            Arguments.of("io.github.carlos_emr.carlos.report.pageUtil.GeneratePatientLetters2Action", "_report", "r"),
            Arguments.of("io.github.carlos_emr.carlos.report.pageUtil.ManagePatientLetters2Action", "_report", "r"),
            Arguments.of("io.github.carlos_emr.carlos.report.pageUtil.DeletePatientLetters2Action", "_report", "w"),
            Arguments.of("io.github.carlos_emr.carlos.report.pageUtil.DbReportAgeSex2Action", "_report", "r"),
            Arguments.of("io.github.carlos_emr.carlos.report.pageUtil.RptByExamplesFavorite2Action", "_admin", "r"),
            Arguments.of("io.github.carlos_emr.carlos.report.reportByTemplate.actions.UploadTemplates2Action", "_admin", "r"),
            Arguments.of("io.github.carlos_emr.carlos.signature.action.SaveSignatureUpload2Action", "_con", "w"),
            Arguments.of("io.github.carlos_emr.carlos.email.action.EmailSend2Action", "_email", "w"),
            Arguments.of("io.github.carlos_emr.carlos.messenger.pageUtil.MsgTransferPostItems2Action", "_msg", "w"),
            Arguments.of("io.github.carlos_emr.carlos.messenger.pageUtil.MsgAttachPDF2Action", "_msg", "w"),
            Arguments.of("io.github.carlos_emr.carlos.messenger.pageUtil.MsgAdjustAttachments2Action", "_msg", "w"),
            Arguments.of("io.github.carlos_emr.carlos.tickler.pageUtil.DbTicklerAdd2Action", "_tickler", "w"),
            Arguments.of("io.github.carlos_emr.carlos.tickler.pageUtil.EditTickler2Action", "_tickler", "u"),
            Arguments.of("io.github.carlos_emr.carlos.tickler.pageUtil.DbTicklerMain2Action", "_tickler", "u"),
            Arguments.of("io.github.carlos_emr.carlos.tickler.pageUtil.DbTicklerDemoMain2Action", "_tickler", "u"),
            Arguments.of("io.github.carlos_emr.carlos.form.pageUtil.FrmFormRHPrevention2Action", "_form", "w"),
            Arguments.of("io.github.carlos_emr.carlos.form.pageUtil.FrmFormAddRHWorkFlow2Action", "_form", "w"),
            Arguments.of("io.github.carlos_emr.carlos.schedule.web.ScheduleDateSave2Action", "_appointment", "w"),
            Arguments.of("io.github.carlos_emr.carlos.documentManager.actions.SaveAnnotatedDocument2Action", "_edoc", "w"),
            Arguments.of("io.github.carlos_emr.carlos.hospitalReportManager.HRMModifyDocument2Action", "_hrm", "w"),
            Arguments.of("io.github.carlos_emr.carlos.mds.pageUtil.PatientMatch2Action", "_lab", "w"),
            Arguments.of("io.github.carlos_emr.carlos.mds.pageUtil.SendMostResponProv2Action", "_lab", "w"),
            Arguments.of("io.github.carlos_emr.carlos.providers.pageUtil.DeleteMyGroupMembers2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.waitinglist.pageUtil.WLAdd2WaitingList2Action", "_demographic", "w"),
            Arguments.of("io.github.carlos_emr.carlos.waitinglist.pageUtil.WLRemoveFromWaitingList2Action", "_demographic", "w"),
            Arguments.of("io.github.carlos_emr.carlos.eform.EFormAttachDocs2Action", "_eform", "u"),
            Arguments.of("io.github.carlos_emr.carlos.eform.actions.DelEForm2Action", "_admin.eform", "w"),
            Arguments.of("io.github.carlos_emr.carlos.eform.actions.SaveEFormAsEDoc2Action", "_eform", "u"),
            Arguments.of("io.github.carlos_emr.carlos.eform.actions.RestoreEForm2Action", "_eform", "w"),
            Arguments.of("io.github.carlos_emr.carlos.eform.actions.DelImage2Action", "_eform", "w"),
            Arguments.of("io.github.carlos_emr.carlos.decision.gate.SaveAntenatalRiskConfig2Action", "_form", "w"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxClearPending2Action", "_rx", "w"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteRx2Action", "_rx", "u"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteAllergy2Action", "_allergy", "u"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxUseFavorite2Action", "_rx", "w"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxAddFavorite2Action", "_rx", "w"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxUpdateFavorite2Action", "_rx", "u"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteFavorite2Action", "_rx", "u"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.web.RxHideCpp2Action", "_rx", "u"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.web.RxReorder2Action", "_rx", "u"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxChooseDrug2Action", "_rx", "w"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxAddAllergy2Action", "_allergy", "w"),
            Arguments.of("io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteToEncounter2Action", "_rx", "w"),
            Arguments.of("io.github.carlos_emr.carlos.prevention.web.UpdateVaccineCatalogue2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.email.action.SaveMyEmailFooter2Action", "_email", "w"),
            Arguments.of("io.github.carlos_emr.carlos.email.admin.SaveClinicEmailLogo2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.email.admin.SaveClinicEmailFooter2Action", "_admin", "w"),
            Arguments.of("io.github.carlos_emr.carlos.messenger.pageUtil.MsgCreateMessage2Action", "_msg", "w"),
            Arguments.of("io.github.carlos_emr.carlos.integration.patientportal.web.PortalInvite2Action", "_portal.invite", "w"),
            Arguments.of("io.github.carlos_emr.carlos.integration.patientportal.web.PortalBookingPrompt2Action", "_portal.booking_prompt", "w"),
            Arguments.of("io.github.carlos_emr.carlos.integration.patientportal.web.PortalAccount2Action", "_portal.account", "w"),
            Arguments.of("io.github.carlos_emr.carlos.login.Logout2Action", "", "")
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
        // Portal email recovery: GET only renders the stored state, whatever parameters it
        // carries; only POST runs a recovery operation. Covered by PortalEmailDelivery2ActionUnitTest.
        "io.github.carlos_emr.carlos.integration.patientportal.web.PortalEmailDelivery2Action",
        // Demographic report favourites and patient sets (#4225) are POST-only;
        // report display/run/load remain GET-compatible. Focused RptDemographicReport2ActionUnitTest.
        "io.github.carlos_emr.carlos.report.pageUtil.RptDemographicReport2Action",
        // UploadLoginText2ActionUnitTest covers read-only GET and mutation-intent rejection.
        "io.github.carlos_emr.carlos.login.UploadLoginText2Action",
        // Legacy encounter forms: every save must be POST; only submit=graph/printAll/printAllJasperReport read
        // and permit GET. Focused GET/HEAD cases in Frm2ActionMethodGuardUnitTest.
        "io.github.carlos_emr.carlos.form.Frm2Action",
        // Measurement data: action=saveMeasurement/saveValues must be POST; its reads permit GET.
        // Focused GET/HEAD cases in MeasurementData2ActionMethodGuardUnitTest.
        "io.github.carlos_emr.carlos.measurements.web.MeasurementData2Action",
        // PHCP provider settings: role mutations require POST; focused Alpha20RequestAuthorizationUnitTest.
        "io.github.carlos_emr.carlos.report.gate.ReportProviderRoles2Action",
        // Provider preferences (setProviderStaleDate / setTicklerPreferences): the view dispatches permit
        // GET; every other methodMap dispatch writes or removes a preference and must be a POST. Covered
        // for every such dispatch by shouldRejectGetAndHead_forEveryMethodMapWriteDispatch and by
        // ProviderProperty2ActionMethodGuardUnitTest.
        "io.github.carlos_emr.carlos.provider.web.ProviderProperty2Action",
        // Pregnancy lifecycle (#4557): list/new/edit are read-only; create/complete require POST.
        // Covered by Pregnancy2ActionUnitTest for both GET and HEAD.
        "io.github.carlos_emr.carlos.commn.web.Pregnancy2Action",
        // Inbox queue admin (#4428): the view methods permit GET; method=addNewQueue inserts a queue
        // and its security object and must be a POST with _edoc write. Covered by
        // DmsInboxManage2ActionUnitTest (shouldReturn405_whenAddNewQueueIsNotPost).
        "io.github.carlos_emr.carlos.documentManager.actions.DmsInboxManage2Action",
        // Report queries remain readable; saving favourites/patient sets requires POST.
        // Covered by ReadMethodWriteGuardUnitTest, including both persistence branches.
        // PHCP role settings remain readable; role changes require an administrative POST.
        // Covered by ViewBilledVisitProvider2ActionUnitTest and the installed daysheet workflow.
        "io.github.carlos_emr.carlos.report.gate.ViewBilledVisitProvider2Action",
        // Incoming PDF navigation permits GET; pdfAction mutations require POST and write access.
        // Focused method/privilege tests: ViewIncomingDocuments2ActionUnitTest.
        "io.github.carlos_emr.carlos.documentManager.gate.ViewIncomingDocuments2Action",
        // Report by Template editor (#4133): the bare page permits GET; action=add|edit|delete
        // must be a POST. Covered by ManageTemplates2ActionUnitTest.
        "io.github.carlos_emr.carlos.report.reportByTemplate.actions.ManageTemplates2Action",
        // OBEC report: the bare page permits GET; Create Report (a Begin Date) writes the OBEC file and must be
        // a POST. Covered by Obec2ActionMethodGuardUnitTest.
        "io.github.carlos_emr.carlos.report.pageUtil.Obec2Action",
        // Message view (#4133): viewing permits GET; linkMsgDemo=true writes a msgDemoMap row and
        // must be a POST. Covered by MsgViewMessage2ActionUnitTest.
        "io.github.carlos_emr.carlos.messenger.pageUtil.MsgViewMessage2Action",
        // A patient's message list permits GET; a selected messageNo array unlinks messages
        // and requires POST. Covered by MsgDisplayDemographicMessages2ActionUnitTest.
        "io.github.carlos_emr.carlos.messenger.pageUtil.MsgDisplayDemographicMessages2Action",
        "io.github.carlos_emr.carlos.admin.web.EchartDisplaySettings2Action",
        // Lab display settings (#3977): the view permits GET; dboperation=Save must be a POST.
        // Covered by LabDisplaySettings2ActionUnitTest.
        "io.github.carlos_emr.carlos.admin.web.LabDisplaySettings2Action",
        // Schedule groups (#4515): GET opens Add a Group; the group list's Delete (submit present)
        // removes memberships and must be a POST. Covered by AdminGroupActionsTest.
        "io.github.carlos_emr.carlos.admin.web.AdminNewGroup2Action",
        // BC supplementary billing: view permits GET; edit/delete require POST.
        // Covered by SupServiceCodeAssoc2ActionUnitTest.
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SupServiceCodeAssoc2Action",
        // BC invoice list / adjust bill (#4343): GET opens Bill Status or a bill's adjust page;
        // billCheck or billingmasterNo is save intent and needs POST.
        // Covered by BillingReProcessBill2ActionUnitTest.
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingReProcessBill2Action",
        // BC WCB claim correction: GET with billing_no opens the correction page; id, billingNo
        // or submit is save intent and needs POST. Covered by TeleplanCorrectionActionWCB2ActionUnitTest.
        "io.github.carlos_emr.carlos.billings.ca.bc.administration.TeleplanCorrectionActionWCB2Action",
        // Rx: only method=updateDB mutates (it rebuilds the DrugRef database) and rejects
        // GET; the read-only status methods stay reachable by GET. Covered in detail by
        // RxUpdateDrugref2ActionUnitTest.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxUpdateDrugref2Action",
        // Appointment: rejects GET when targeting specific mutation URIs
        // (appointmentaddrecordprint, groupappt param).
        "io.github.carlos_emr.carlos.appointment.gate.ViewAppointmentSelfPost2Action",
        // Decision: rejects GET when submit param starts with "save".
        "io.github.carlos_emr.carlos.decision.gate.ViewDecision2Action",
        // Chart entry (#4673): opening a chart permits GET; PEAttach=yes copies a note into the
        // chart (the PMM attach popup) and must be a POST. Covered by
        // IncomingEncounterRequestIsolationUnitTest (shouldRefuseToCopyANote_whenTheAttachIsNotPosted).
        "io.github.carlos_emr.carlos.encounter.pageUtil.EctIncomingEncounter2Action",
        // Login gate: GET renders the selector, but selectedFacilityId is mutation intent.
        "io.github.carlos_emr.carlos.login.gate.SelectFacility2Action",
        // Login text upload: admin/uploadEntryText is dual-purpose. A GET/HEAD renders
        // uploadEntryText.jsp (both the admin menu and the administration left-nav open it that
        // way); only a POST -- or a GET/HEAD carrying an acceptable-use-agreement parameter -- is
        // gated (see UploadLoginText2ActionUnitTest for the focused GET-rejection coverage).
        // Ontario billing: dual-purpose pages reject GET only when mutation-intent params exist.
        "io.github.carlos_emr.carlos.billings.ca.on.web.BatchBill2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.BillingDocumentErrorReportUpload2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.MoveMohFiles2Action",
        // BC Teleplan: default page view permits GET; method dispatches are POST-only.
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ManageTeleplan2Action",
        // Generate Teleplan File (#4439 D3): opening the page (menu, year archive) permits GET; providers is the
        // Create Report form's write and needs POST. Also in HttpMethodGuardFilter.MUTATION_INTENT_PARAMS_BY_ROUTE.
        // Covered by GenerateTeleplanFile2ActionUnitTest.
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.GenerateTeleplanFile2Action",
        // BC MSP Quick Billing (#4772): opening the page permits GET; add (data) and remove accept
        // only POST, as the page sends them. Covered by QuickBillingBCSessionFormUnitTest.
        "io.github.carlos_emr.carlos.billings.ca.bc.quickbilling.QuickBillingBC2Action",
        // Messenger admin: rejects GET on form-save method invocations.
        "io.github.carlos_emr.carlos.messenger.config.pageUtil.MsgMessengerAdmin2Action",
        // Provider phone editor: EditPhoneNum -> providerPhone.jsp is dual-purpose. A GET/HEAD renders
        // the editor (view); only a POST — or a GET/HEAD carrying the faxNumber mutation param — is
        // gated (see ProEditPhoneNum2ActionUnitTest for the focused GET-rejection coverage).
        "io.github.carlos_emr.carlos.providers.pageUtil.ProEditPhoneNum2Action",
        // Provider document descriptions: read methods permit GET; write methods are POST-only.
        "io.github.carlos_emr.carlos.provider.web.DocumentDescriptionTemplate2Action",
        // Document manager: read methods permit GET; addIncomingDocument is POST-only.
        "io.github.carlos_emr.carlos.documentManager.actions.ManageDocument2Action",
        // Admin API clients: list methods permit GET; add/delete are POST-only.
        "io.github.carlos_emr.carlos.admin.web.ClientManage2Action",
        // Provider roles: GET renders the provider/role roster; only the add/delete,
        // role-update and primary-role-update intents are gated (see
        // ProviderRole2ActionUnitTest for the focused GET-rejection coverage).
        "io.github.carlos_emr.carlos.admin.web.ProviderRole2Action",
        // Schedule: all below reject GET on Save/Delete/mutation-intent params.
        "io.github.carlos_emr.carlos.schedule.web.ScheduleCreateDate2Action",
        "io.github.carlos_emr.carlos.schedule.web.ScheduleEditTemplate2Action",
        "io.github.carlos_emr.carlos.schedule.web.ScheduleHolidaySetting2Action",
        "io.github.carlos_emr.carlos.schedule.web.ScheduleTemplateApplying2Action",
        "io.github.carlos_emr.carlos.schedule.web.ScheduleTemplateCodeSetting2Action",
        // Waitinglist: reject GET on Save/Delete submit values.
        "io.github.carlos_emr.carlos.waitinglist.pageUtil.WLEditWaitingListName2Action",
        "io.github.carlos_emr.carlos.waitinglist.pageUtil.WLSetupDisplayWaitingList2Action",
        // Prescription: reprint/reprint2 (audit writes), staging (represcribe, represcribe2, saveReRxDrugIdToStash,
        // repcbAllLongTerm, represcribeMultiple) and saveDigitalSignature are POST-only;
        // viewPrescribing only renders the workspace and permits GET (#3908). Covered by
        // RxPatientWriteAuthorizationUnitTest.shouldRejectStagingOrPharmacyWrite_whenMethodIsNotPost
        // and shouldRejectUnsafeMethod_forPrescriptionReprintDispatch below.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxRePrescribe2Action",
        // SMS: the SMS settings page renders on a bare GET (_admin.sms r); method=configure and
        // method=sendSystemTest are POST-only (see ConfigureSms2ActionUnitTest). Issue #3836.
        "io.github.carlos_emr.carlos.sms.admin.ConfigureSms2Action",
        // Pharmacies: search/getPharmacyInfo/... permit GET; delete, unlink, setPreferred, add,
        // save and the legacy pharmacyAction form are POST-only (#3908). Covered by
        // RxPatientWriteAuthorizationUnitTest.shouldRejectStagingOrPharmacyWrite_whenMethodIsNotPost.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxManagePharmacy2Action",
        // Drug-form editor gate: GET renders it for a readable patient; action=update changes the
        // drug and rejects GET/HEAD (#3908). Covered by ViewUpdateForm2ActionUnitTest.
        "io.github.carlos_emr.carlos.prescript.gate.ViewUpdateForm2Action",
        // Allergy display: the page and allergyData stay GET-compatible; method=reorder rewrites
        // allergy positions and rejects non-POST with 405 (#3908). Covered by
        // RxShowAllergy2ActionTest.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxShowAllergy2Action",
        // Prescription stash: deletePrescribe and the legacy action=delete remove a staged card and
        // are POST-only; setStashIndex / action=edit cursor moves stay verb-open. Issue #3871.
        // Covered by RxStash2ActionUnitTest.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxStash2Action",
        // Prescription write: updateSaveAllDrugs, the action=update* stash rewrite/save and
        // updateReRxDrug are POST-only; the read dispatches (listPreviousInstructions,
        // getInstructionsAutocomplete, ...) stay verb-open. Covered by
        // RxWriteScript2ActionWriteIsolationUnitTest.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteScript2Action",
        // Drug reasons: the popup view (GET, no method) stays verb-open; method=addDrugReason /
        // archiveReason write the chart and reject GET/HEAD. Covered by
        // RxPatientWriteAuthorizationUnitTest.shouldRejectReasonWrite_whenMethodIsNotPost.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxReason2Action",
        // Favourite sharing/copying writes require POST; the selection page permits GET.
        // Targeted guard from PR #2478, covered by CopyFavorites2ActionUnitTest.
        "io.github.carlos_emr.carlos.prescript.web.CopyFavorites2Action",
        // Fax: queue/cancel (including the no-method fall-through to cancel) mutate, and prepareFax
        // stages a PDF and a session claim, so all three reject GET/HEAD; only getPreview and
        // getPageCount stay verb-open (see Fax2ActionMethodGateUnitTest).
        // Satellite-sites Admin (#4700): the list, Add New Site and method=edit permit GET; method=save stores
        // a site and must be a POST. Covered by SitesManage2ActionUnitTest (shouldRefuseSave_whenNotPost).
        "io.github.carlos_emr.carlos.commn.web.SitesManage2Action",
        // Fax: queue/cancel (including the no-method fall-through to cancel) mutate and reject
        // GET/HEAD; getPreview/getPageCount/prepareFax stay verb-open (see Fax2ActionMethodGateUnitTest).
        "io.github.carlos_emr.carlos.fax.action.Fax2Action",
        // Fax admin queue: CancelFax/ResendFax/SetCompleted mutate and reject GET/HEAD before
        // dispatch; viewFax/fetchFaxStatus stay verb-open (see ManageFaxes2ActionUnitTest).
        "io.github.carlos_emr.carlos.fax.admin.ManageFaxes2Action",
        // Fax admin config: configure/restartFaxScheduler mutate and testConnection forwards
        // submitted credentials to the provider; all three are POST-only (405 + Allow: POST for
        // GET/HEAD and PUT/PATCH/DELETE alike); getFaxSchedularStatus/getPendingIncomingFaxes
        // stay verb-open (see ConfigureFax2ActionUnitTest).
        "io.github.carlos_emr.carlos.fax.admin.ConfigureFax2Action",
        // SMS: the patient SMS history list renders on a bare GET; only method=showMessage (an audited
        // read of a message's full text) is POST-only (see ViewSmsHistory2ActionUnitTest). Issue #3839.
        "io.github.carlos_emr.carlos.sms.web.ViewSmsHistory2Action",
        // Security/MFA: execute() renders a view on a bare GET; only the method=resetMfa dispatch
        // (a privileged reset of another account's MFA) is POST-only (see MfaActions2ActionUnitTest).
        "io.github.carlos_emr.carlos.security.MfaActions2Action",
        // Demographic: AddRelation popup renders on a bare GET; only linkingDemo+relation mutation
        // intent is POST-only (see AddDemographicRelationship2ActionUnitTest). Issue #3352.
        "io.github.carlos_emr.carlos.demographic.pageUtil.AddDemographicRelationship2Action",
        // Appointment types: a bare GET (and oper=edit) renders the list/edit form; only
        // oper=save and oper=del are POST-only (see AppointmentType2ActionUnitTest).
        "io.github.carlos_emr.carlos.appt.web.AppointmentType2Action",
        // Facility admin: list/edit/add render on a GET (method=add only builds a transient
        // Facility for the form); only method=delete and method=save are POST-only
        // (see FacilityManager2ActionUnitTest).
        "io.github.carlos_emr.carlos.facility.FacilityManager2Action",
        // Chart notes: opening, reading, printing and restoring the chart permit GET; the method
        // values that change a note, an issue, a draft or a note lock are POST-only
        // (see CaseManagementEntry2ActionPostOnlyUnitTest).
        "io.github.carlos_emr.carlos.casemgmt.web.CaseManagementEntry2Action",
        // Customize Measurements ▸ Edit Measurement Group: forward=type|style|dsHTML only open an
        // editor; forward=delete removes the group and is POST-only (issue #3335). Covered by
        // EctSelectMeasurementGroup2ActionUnitTest.
        "io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctSelectMeasurementGroup2Action",
        // Customize Measurements ▸ Add / Remove-Remap Measurement Mapping: a GET without write
        // parameters renders the page (and its Search); identifier+loinc_code (a mapping) or id
        // (a removal/remap) is POST-only (issue #3335). Covered by EctMeasurementMapWrite2ActionsUnitTest.
        "io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctAddMeasurementMap2Action",
        "io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil.EctRemoveMeasurementMap2Action",
        // BPMH form: method=save and method=print store the form and must be POST; opening it permits GET.
        // Focused GET/HEAD cases in BpmhFormRetrieve2ActionUnitTest.
        "io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.web.BpmhFormRetrieve2Action",
        // Write parameters other than `method` (#3394). Each also appears in
        // HttpMethodGuardFilter.MUTATION_INTENT_PARAMS_BY_ROUTE, which
        // shouldRejectGetAndHead_forEveryFilterListedWriteParameter drives against the action.
        // Provider Privilege: opening the page permits GET; submit (Add/Delete) or buttonUpdate rewrites
        // privilege rows and must be a POST. Covered by ProviderPrivilegeRoleWriteGuardUnitTest.
        "io.github.carlos_emr.carlos.admin.web.ProviderPrivilege2Action",
        // Provider Role: opening and filtering permit GET; submit (a localized Add/Delete), buttonUpdate
        // or buttonSetPrimaryRole changes a role, must be a POST and needs write. Covered by
        // ProviderPrivilegeRoleWriteGuardUnitTest.
        // Dx registry report: the reports permit GET; method=editDesc rewrites a shared code description
        // and must be a POST. Covered by DxresearchReport2ActionTest (EditDescMethodGuard).
        "io.github.carlos_emr.carlos.commn.web.DxresearchReport2Action",
        // Message actions: reply, replyAll and forward open the compose page by GET; delete deletes the
        // message and must be a POST. Covered by MsgHandleMessages2ActionDeleteGuardUnitTest.
        "io.github.carlos_emr.carlos.messenger.pageUtil.MsgHandleMessages2Action"
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
        "io.github.carlos_emr.carlos.integration.patientportal.web.PortalPanel2Action",
        // The patient portal staff page: GET only, it renders the page and changes nothing.
        "io.github.carlos_emr.carlos.integration.patientportal.web.PortalManage2Action",
        "io.github.carlos_emr.carlos.encounter.pageUtil.FormExists2Action",
        // Read-scope gates — permit GET, only 405 truly unsupported methods.
        "io.github.carlos_emr.carlos.appointment.gate.ViewAppointment2Action",
        "io.github.carlos_emr.carlos.appointment.gate.ViewAppointmentWrite2Action",
        // Opens the annotation viewer. Requires _edoc write because reaching it is the
        // first step of authoring a new document, but it only renders a page; the
        // mutation lives in SaveAnnotatedDocument2Action.
        "io.github.carlos_emr.carlos.documentManager.actions.AnnotateDocument2Action",
        // Returns word bounding boxes for snap-to-text highlighting. Read-only.
        "io.github.carlos_emr.carlos.documentManager.actions.DocumentTextBoxes2Action",
        "io.github.carlos_emr.carlos.report.gate.ViewReport2Action",
        // Serves the clinic's email footer logo picture (issue #3981): GET and HEAD only, read-only.
        "io.github.carlos_emr.carlos.email.action.ClinicEmailLogo2Action",
        // CDS 4 report results: the shared report gate plus a functional centre in use. Read-only.
        "io.github.carlos_emr.carlos.report.gate.ViewCds4ReportResults2Action",
        // ECHART HISTORY: the shared report gate plus the patient's record restrictions. Read-only.
        "io.github.carlos_emr.carlos.report.gate.ViewReportEChartHistory2Action"
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
        "io.github.carlos_emr.carlos.integration.patientportal.web.",
        "io.github.carlos_emr.carlos.appointment.",
        "io.github.carlos_emr.carlos.decision.",
        // Measurements (issue #3335): every *2Action in the slice was audited; the writes are
        // registered above and the Setup*/display actions are read-only.
        "io.github.carlos_emr.carlos.encounter.oscarMeasurements.",
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
        "io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmCreateImmunizationSetConfig2Action",
        "io.github.carlos_emr.carlos.encounter.immunization.config.pageUtil.EctImmInitConfigDeleteImmuSet2Action",
        // The form package is not in IN_SCOPE_PACKAGE_PREFIXES; its legacy form dispatcher registers here.
        "io.github.carlos_emr.carlos.form.Frm2Action",
        // measurements.web is not in IN_SCOPE_PACKAGE_PREFIXES; its data dispatcher registers here.
        "io.github.carlos_emr.carlos.measurements.web.MeasurementData2Action",
        "io.github.carlos_emr.carlos.lab.pageUtil.FileLabsOnBehalf2Action",
        // The provider.web package is not in IN_SCOPE_PACKAGE_PREFIXES; its methodMap dispatcher is
        // registered explicitly (conditional mutator, see METHOD_MAP_DISPATCHERS).
        "io.github.carlos_emr.carlos.provider.web.ProviderProperty2Action",
        "io.github.carlos_emr.carlos.admin.web.EchartDisplaySettings2Action",
        "io.github.carlos_emr.carlos.admin.web.LabDisplaySettings2Action",
        "io.github.carlos_emr.carlos.admin.web.AdminNewGroup2Action",
        // appt slice: AppointmentType2Action is the only migrated mutator; the appt package is
        // not in IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly (conditional mutator).
        "io.github.carlos_emr.carlos.appt.web.AppointmentType2Action",
        "io.github.carlos_emr.carlos.admin.web.ClientManage2Action",
        "io.github.carlos_emr.carlos.admin.web.ClinicNbrManage2Action",
        "io.github.carlos_emr.carlos.admin.web.ProviderRole2Action",
        "io.github.carlos_emr.carlos.admin.web.SecurityAddSecurity2Action",
        "io.github.carlos_emr.carlos.admin.web.SecurityDelete2Action",
        "io.github.carlos_emr.carlos.admin.web.SecurityUpdate2Action",
        "io.github.carlos_emr.carlos.admin.web.SaveProviderLinkingRules2Action",
        "io.github.carlos_emr.carlos.mds.pageUtil.PatientMatch2Action",
        // BC billing administration is not in IN_SCOPE_PACKAGE_PREFIXES; the WCB correction registers explicitly.
        "io.github.carlos_emr.carlos.billings.ca.bc.administration.TeleplanCorrectionActionWCB2Action",
        "io.github.carlos_emr.carlos.mds.pageUtil.SendMostResponProv2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingReProcessBill2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingSaveBilling2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingUpdateBilling2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingEditCode2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingDeleteNoAppt2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingDeleteWithoutNo2Action",
        "io.github.carlos_emr.carlos.eform.actions.UnRemEForm2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ManageTeleplan2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.ReceivePayment2Action",
        // BC billing (#4439 D3, D12): the Teleplan file generation and the POST-only saves registered above.
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.GenerateTeleplanFile2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.quickbilling.QuickBillingBCSave2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SaveBillingPreferences2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.SaveAssoc2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.DeleteServiceCodeAssoc2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.AddReferralDoc2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.BillingAddCode2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.DeletePrivateCode2Action",
        "io.github.carlos_emr.carlos.billings.ca.bc.quickbilling.QuickBillingBC2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.BatchBill2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.BillingDocumentErrorReportUpload2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.MoveMohFiles2Action",
        "io.github.carlos_emr.carlos.billings.ca.on.web.ScheduleOfBenefitsUpload2Action",
        "io.github.carlos_emr.carlos.commn.web.FlowSheetCustom2Action",
        // email slice: only EmailSend2Action is registered (issue #3111); the broader email
        // production-readiness audit that surfaced it is tracked via PR #3096.
        "io.github.carlos_emr.carlos.email.action.EmailSend2Action",
        // The clinic email footer and its logo (issue #3981): their save actions and the read-only picture.
        "io.github.carlos_emr.carlos.email.admin.SaveClinicEmailLogo2Action",
        "io.github.carlos_emr.carlos.email.admin.SaveClinicEmailFooter2Action",
        "io.github.carlos_emr.carlos.email.action.ClinicEmailLogo2Action",
        "io.github.carlos_emr.carlos.commn.web.FlowSheetDrug2Action",
        "io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequest2Action",
        // (EctMeasurements2Action, HealthTrackerUpdate2Action and EctDeleteData2Action are covered by
        // the encounter.oscarMeasurements. entry in IN_SCOPE_PACKAGE_PREFIXES.)
        "io.github.carlos_emr.carlos.form.pageUtil.FrmSelect2Action",
        "io.github.carlos_emr.carlos.form.pageUtil.FrmXmlUpload2Action",
        "io.github.carlos_emr.carlos.login.UploadLoginText2Action",
        "io.github.carlos_emr.carlos.login.gate.SelectFacility2Action",
        "io.github.carlos_emr.carlos.provider.web.DocumentDescriptionTemplate2Action",
        // eform slice: only these are registered; broader slice audit tracked in issue #2828.
        "io.github.carlos_emr.carlos.eform.actions.DelEForm2Action",
        "io.github.carlos_emr.carlos.eform.actions.SaveEFormAsEDoc2Action",
        // sms slice: the sms package is not in IN_SCOPE_PACKAGE_PREFIXES, so its admin settings
        // action registers explicitly (conditional mutator, #3836).
        "io.github.carlos_emr.carlos.sms.admin.ConfigureSms2Action",
        // Fax slice: the fax package is not in IN_SCOPE_PACKAGE_PREFIXES, so its gated
        // mutators register explicitly (clinician queue/cancel plus the two admin actions).
        "io.github.carlos_emr.carlos.fax.action.Fax2Action",
        "io.github.carlos_emr.carlos.fax.admin.ManageFaxes2Action",
        "io.github.carlos_emr.carlos.fax.admin.ConfigureFax2Action",
        // sms slice: ViewSmsHistory2Action is the first SMS web action; the sms package is not in
        // IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly (conditional mutator, #3839).
        "io.github.carlos_emr.carlos.sms.web.ViewSmsHistory2Action",
        // providers slice: ProEditPhoneNum2Action persists the provider's rxPhone; the providers
        // package is not in IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly here.
        "io.github.carlos_emr.carlos.providers.pageUtil.ProEditPhoneNum2Action",
        // providers slice: DeleteMyGroupMembers2Action deletes group memberships and is POST-only
        // (unconditional mutator, #4515).
        "io.github.carlos_emr.carlos.providers.pageUtil.DeleteMyGroupMembers2Action",
        // encounter slice: EctConsultationFormFax2Action queues PHI faxes; the encounter package is
        // not in IN_SCOPE_PACKAGE_PREFIXES, so this single migrated mutator registers explicitly.
        "io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormFax2Action",
        // encounter slice: ERefer2Action queues attachments for Ocean eReferral (issue #3867).
        "io.github.carlos_emr.carlos.encounter.oceanEReferal.pageUtil.ERefer2Action",
        // encounter slice: EctSaveEncounter2Action writes the legacy editor's eChart revision (#4673).
        "io.github.carlos_emr.carlos.encounter.pageUtil.EctSaveEncounter2Action",
        // encounter slice: EctIncomingEncounter2Action copies a note on PEAttach=yes (#4673;
        // conditional mutator).
        "io.github.carlos_emr.carlos.encounter.pageUtil.EctIncomingEncounter2Action",
        // security slice: MfaActions2Action's resetMfa is a POST-only privileged mutation; the security
        // package is not in IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly (conditional mutator).
        "io.github.carlos_emr.carlos.security.MfaActions2Action",
        // demographic slice: AddDemographicRelationship2Action is the only migrated mutator gated so
        // far; the demographic package is not in IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly.
        "io.github.carlos_emr.carlos.demographic.pageUtil.AddDemographicRelationship2Action",
        // facility slice: FacilityManager2Action is the first gated mutator there; the facility
        // package is not in IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly (conditional
        // mutator). The sibling PMmodule/FacilityManager action is not gated on POST yet.
        "io.github.carlos_emr.carlos.facility.FacilityManager2Action",
        // prevention slice: UpdateVaccineCatalogue2Action (National Vaccine Catalogue install) is
        // the only gated mutator; the prevention package is not in IN_SCOPE_PACKAGE_PREFIXES.
        "io.github.carlos_emr.carlos.prevention.web.UpdateVaccineCatalogue2Action",
        // email slice: EmailSend2Action is the only gated mutator so far; the email package is
        // not in IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly.
        // prescript slice: RxStash2Action's stash removal is POST-only; the prescript package is not
        // in IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly (conditional mutator). Issue #3871.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxStash2Action",
        // prescript slice: RxWriteScript2Action's save and update dispatches are POST-only
        // (conditional mutator, #3908).
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteScript2Action",
        // prescript slice: RxClearPending2Action clears the named patient's stash and is POST-only
        // (unconditional mutator, #3908).
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxClearPending2Action",
        // prescript slice: RxReason2Action's add/archive drug-reason writes are POST-only; the popup
        // view stays GET (conditional mutator, #3908).
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxReason2Action",
        // commn.web slice: the package is not in IN_SCOPE_PACKAGE_PREFIXES, so SitesManage2Action registers
        // explicitly (conditional mutator, #4700).
        "io.github.carlos_emr.carlos.commn.web.SitesManage2Action",
        // prescript slice (#3908): re-prescribe staging, drug deletion/discontinue/clears and
        // pharmacy links are POST-only.
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxRePrescribe2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteRx2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxManagePharmacy2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteAllergy2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxUseFavorite2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxAddFavorite2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxUpdateFavorite2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxDeleteFavorite2Action",
        "io.github.carlos_emr.carlos.prescript.gate.ViewUpdateForm2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxShowAllergy2Action",
        "io.github.carlos_emr.carlos.prescript.web.RxHideCpp2Action",
        "io.github.carlos_emr.carlos.prescript.web.RxReorder2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxChooseDrug2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxAddAllergy2Action",
        "io.github.carlos_emr.carlos.prescript.pageUtil.RxWriteToEncounter2Action",
        "io.github.carlos_emr.carlos.prescript.web.CopyFavorites2Action",
        // casemgmt slice: CaseManagementEntry2Action's save methods are POST-only; the casemgmt
        // package is not in IN_SCOPE_PACKAGE_PREFIXES, so it registers explicitly (conditional mutator).
        "io.github.carlos_emr.carlos.casemgmt.web.CaseManagementEntry2Action",
        // The form package is not in IN_SCOPE_PACKAGE_PREFIXES; the BPMH form's dispatcher registers here.
        "io.github.carlos_emr.carlos.form.pharmaForms.formBPMH.web.BpmhFormRetrieve2Action",
        // admin.web and commn.web are not in IN_SCOPE_PACKAGE_PREFIXES; these gate writes named by
        // parameters other than `method` (#3394; conditional mutators).
        "io.github.carlos_emr.carlos.admin.web.ProviderPrivilege2Action",
        "io.github.carlos_emr.carlos.commn.web.DxresearchReport2Action"
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

    @ParameterizedTest(name = "{0} rejects {1}")
    @CsvSource({
        "GET, addIncomingDocument",
        "HEAD, addIncomingDocument",
        "GET, documentUpdate",
        "HEAD, documentUpdate",
        "GET, documentUpdateAjax",
        "HEAD, documentUpdateAjax",
        "GET, removeLinkFromDocument",
        "HEAD, removeLinkFromDocument"
    })
    @DisplayName("ManageDocument2Action mutation dispatches should reject unsafe methods")
    void shouldRejectUnsafeMethod_forManageDocumentMutationDispatch(
            String httpMethod, String actionMethod) throws Exception {
        assertRejectsUnsafeMethod(
                "io.github.carlos_emr.carlos.documentManager.actions.ManageDocument2Action",
                "_edoc",
                "w",
                httpMethod,
                Map.of("method", actionMethod));
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("EditTickler2Action should reject unsafe methods for the edit dispatch")
    void shouldRejectUnsafeMethod_forTicklerEditDispatch(String httpMethod) throws Exception {
        assertRejectsUnsafeMethod(
                "io.github.carlos_emr.carlos.tickler.pageUtil.EditTickler2Action",
                "_tickler", "u", httpMethod, Map.of("method", "editTickler"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"update", "copy"})
    @DisplayName("CopyFavorites2Action should reject GET and HEAD for either write dispatch")
    void shouldRejectUnsafeMethod_forCopyFavoritesMutationDispatch(String dispatch) throws Exception {
        for (String httpMethod : List.of("GET", "HEAD")) {
            assertRejectsUnsafeMethod(
                    "io.github.carlos_emr.carlos.prescript.web.CopyFavorites2Action",
                    "_rx", "w", httpMethod, Map.of("dispatch", dispatch));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"reprint", "reprint2"})
    @DisplayName("Prescription reprint audit writes should reject GET and HEAD")
    void shouldRejectUnsafeMethod_forPrescriptionReprintDispatch(String method) throws Exception {
        for (String httpMethod : List.of("GET", "HEAD")) {
            assertRejectsUnsafeMethod(
                    "io.github.carlos_emr.carlos.prescript.pageUtil.RxRePrescribe2Action",
                    "_rx", "w", httpMethod,
                    Map.of("method", method, "scriptNo", "12", "drugList", "12", "demographicNo", "1"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD"})
    @DisplayName("DxresearchReport2Action should reject unsafe methods for the editDesc write")
    void shouldRejectUnsafeMethod_forDxresearchReportEditDesc(String httpMethod) throws Exception {
        assertRejectsUnsafeMethod(
                "io.github.carlos_emr.carlos.commn.web.DxresearchReport2Action",
                "_report", "r", httpMethod,
                Map.of("method", "editDesc", "editingCodeType", "icd9", "editingCodeCode", "250",
                        "editingCodeDesc", "FAKE description"));
    }

    /**
     * The privilege each action behind {@link HttpMethodGuardFilter#MUTATION_INTENT_PARAMS_BY_ROUTE}
     * checks before its method guard, as {@code {securityObject, level}}. A route added to that table
     * needs an entry here, or {@link #filterListedWriteParameters()} fails.
     */
    private static final Map<String, String[]> FILTER_LISTED_ROUTE_PRIVILEGES = Map.of(
        "io.github.carlos_emr.carlos.admin.web.ProviderPrivilege2Action", new String[]{"_admin", "w"},
        "io.github.carlos_emr.carlos.admin.web.ProviderRole2Action", new String[]{"_admin", "r"},
        "io.github.carlos_emr.carlos.messenger.pageUtil.MsgHandleMessages2Action", new String[]{"_msg", "r"},
        "io.github.carlos_emr.carlos.form.pageUtil.FrmSelect2Action", new String[]{"_form", "w"},
        "io.github.carlos_emr.carlos.billings.ca.bc.pageUtil.GenerateTeleplanFile2Action", new String[]{"_billing", "w"});

    /**
     * One case per route and write parameter in {@link HttpMethodGuardFilter#MUTATION_INTENT_PARAMS_BY_ROUTE},
     * with the action class the Struts configuration maps that route to. The filter is only the
     * backstop: the action must refuse the GET itself, so a route listed there without its own guard
     * fails here (issue #3394).
     */
    static Stream<Arguments> filterListedWriteParameters() throws Exception {
        Map<String, String> classByRoute = strutsActionClassesByRoute();
        List<Arguments> out = new ArrayList<>();
        for (Map.Entry<String, Set<String>> route
                : new java.util.TreeMap<>(HttpMethodGuardFilter.MUTATION_INTENT_PARAMS_BY_ROUTE).entrySet()) {
            String className = classByRoute.get(route.getKey());
            assertThat(className)
                .as("HttpMethodGuardFilter lists route %s, but no Struts action is mapped there", route.getKey())
                .isNotNull();
            assertThat(FILTER_LISTED_ROUTE_PRIVILEGES)
                .as("add %s (route %s) to FILTER_LISTED_ROUTE_PRIVILEGES", className, route.getKey())
                .containsKey(className);
            for (String param : new TreeSet<>(route.getValue())) {
                out.add(Arguments.of(route.getKey(), className, param));
            }
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} with {2} rejects GET and HEAD in {1}")
    @MethodSource("filterListedWriteParameters")
    @DisplayName("every filter-listed write parameter should be refused by the action itself for GET and HEAD")
    void shouldRejectGetAndHead_forEveryFilterListedWriteParameter(
            String route, String className, String param) throws Exception {
        String[] privilege = FILTER_LISTED_ROUTE_PRIVILEGES.get(className);
        for (String httpMethod : List.of("GET", "HEAD")) {
            // A non-English label proves the guard keys on the parameter's presence, not its value.
            assertRejectsUnsafeMethod(className, privilege[0], privilege[1], httpMethod,
                    Map.of(param, "Ajouter", "messageNo", "1"));
        }
    }

    /** Every Struts action in the {@code struts*.xml} modules, as {@code "/" + name} to its class. */
    private static Map<String, String> strutsActionClassesByRoute() throws Exception {
        Path configDir = Paths.get("src", "main", "webapp", "WEB-INF", "classes");
        Map<String, String> classByRoute = new HashMap<>();
        List<Path> configs;
        try (Stream<Path> paths = Files.list(configDir)) {
            configs = paths.filter(p -> p.getFileName().toString().matches("struts(?:-[a-z]+)?\\.xml"))
                    .sorted().toList();
        }
        DocumentBuilder db = newHardenedDocumentBuilder();
        for (Path config : configs) {
            try (InputStream in = Files.newInputStream(config)) {
                NodeList actions = db.parse(in).getElementsByTagName("action");
                for (int i = 0; i < actions.getLength(); i++) {
                    Element action = (Element) actions.item(i);
                    classByRoute.put("/" + action.getAttribute("name"), action.getAttribute("class"));
                }
            }
        }
        return classByRoute;
    }

    private static DocumentBuilder newHardenedDocumentBuilder() throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        dbf.setValidating(false);
        dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
        dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        dbf.setXIncludeAware(false);
        dbf.setExpandEntityReferences(false);
        DocumentBuilder db = dbf.newDocumentBuilder();
        // The Struts modules declare a DOCTYPE; it is allowed but never resolved.
        db.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return db;
    }

    /**
     * Actions that dispatch through a {@code methodMap} of named handlers, filled by a {@code @PostConstruct}
     * {@code init()}. Every entry whose name does not start with {@code view} writes or removes something, so
     * each must refuse GET and HEAD. {@link #methodMapWriteDispatches()} reads the entries from the action
     * itself, so a new unguarded write fails here with no manifest edit, and
     * {@link #methodMapDispatchersMustBeRegistered()} keeps this list complete.
     */
    private static final Set<String> METHOD_MAP_DISPATCHERS = Set.of(
        "io.github.carlos_emr.carlos.provider.web.ProviderProperty2Action");

    @SuppressWarnings("unchecked")
    static Stream<Arguments> methodMapWriteDispatches() throws Exception {
        List<Arguments> out = new ArrayList<>();
        for (String className : new TreeSet<>(METHOD_MAP_DISPATCHERS)) {
            Class<?> actionClass = Class.forName(className);
            Map<String, ?> methodMap;
            try (MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
                 MockedStatic<ServletActionContext> servletCtx = mockStatic(ServletActionContext.class)) {
                springUtils.when(() -> SpringUtils.getBean(any(Class.class)))
                        .thenAnswer(inv -> Mockito.mock((Class<?>) inv.getArgument(0)));
                servletCtx.when(ServletActionContext::getRequest).thenReturn(new MockHttpServletRequest());
                servletCtx.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
                Object action = actionClass.getDeclaredConstructor().newInstance();
                actionClass.getMethod("init").invoke(action);
                java.lang.reflect.Field field = actionClass.getDeclaredField("methodMap");
                field.setAccessible(true);
                methodMap = (Map<String, ?>) field.get(action);
            }
            new TreeSet<>(methodMap.keySet()).stream()
                    .filter(name -> !name.startsWith("view"))
                    .forEach(name -> out.add(Arguments.of(className, name)));
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} method={1} rejects GET and HEAD")
    @MethodSource("methodMapWriteDispatches")
    @DisplayName("every methodMap write dispatch should reject GET and HEAD without side-effects")
    void shouldRejectGetAndHead_forEveryMethodMapWriteDispatch(String className, String dispatch) throws Exception {
        for (String httpMethod : List.of("GET", "HEAD")) {
            // The privilege tuple is only consulted if authorization runs before the method guard.
            assertRejectsUnsafeMethod(className, "_lab", "w", httpMethod, Map.of("method", dispatch));
        }
    }

    @Test
    @DisplayName("discovery: every *2Action dispatching through a methodMap must be in METHOD_MAP_DISPATCHERS")
    void methodMapDispatchersMustBeRegistered() throws IOException {
        Path sourceRoot = Paths.get("src", "main", "java");
        assertThat(sourceRoot)
            .as("Expected src/main/java relative to the test working directory")
            .exists();
        Set<String> found = new TreeSet<>();
        try (Stream<Path> paths = Files.walk(sourceRoot)) {
            for (Path source : paths.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith("2Action.java")).toList()) {
                if (Files.readString(source).contains("methodMap.put(")) {
                    found.add(fileToClassName(sourceRoot, source));
                }
            }
        }
        assertThat(found)
            .as("*2Action classes that dispatch through a methodMap; register each in METHOD_MAP_DISPATCHERS "
              + "so its write dispatches are driven with GET and HEAD")
            .isEqualTo(new TreeSet<>(METHOD_MAP_DISPATCHERS));
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
            when(sessionInfo.getLoggedInProviderNo()).thenReturn("999998");
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
        if (actionClass.equals(RestoreEForm2Action.class)) {
            return new RestoreEForm2Action(mock(SecurityInfoManager.class));
        }
        if (actionClass.equals(EctDefineNewMeasurementGroup2Action.class)) {
            return new EctDefineNewMeasurementGroup2Action(mock(SecurityInfoManager.class),
                    (MeasurementGroupCreationService) autoMocks.computeIfAbsent(
                            MeasurementGroupCreationService.class, Mockito::mock));
        }
        if (actionClass.equals(EctAddMeasurementGroup2Action.class)) {
            return new EctAddMeasurementGroup2Action(mock(SecurityInfoManager.class),
                    (MeasurementGroupCreationService) autoMocks.computeIfAbsent(
                            MeasurementGroupCreationService.class, Mockito::mock));
        }
        if (actionClass.equals(ERefer2Action.class)) {
            return new ERefer2Action(mock(SecurityInfoManager.class),
                    (DocumentAttachmentManager) autoMocks.computeIfAbsent(DocumentAttachmentManager.class, Mockito::mock),
                    (EReferAttachmentDao) autoMocks.computeIfAbsent(EReferAttachmentDao.class, Mockito::mock),
                    (AttachmentOwnershipService) autoMocks.computeIfAbsent(AttachmentOwnershipService.class, Mockito::mock));
        }
        if (actionClass.equals(SecurityDelete2Action.class)) {
            CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
            when(methodSecurity.hasAdminWrite()).thenReturn(true);
            SecurityDao securityDao = (SecurityDao) autoMocks.computeIfAbsent(SecurityDao.class, Mockito::mock);
            return new SecurityDelete2Action(securityDao, methodSecurity);
        }
        if (actionClass.equals(SecurityAddSecurity2Action.class)) {
            CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
            when(methodSecurity.hasAdminWrite()).thenReturn(true);
            return new SecurityAddSecurity2Action(methodSecurity);
        }
        if (actionClass.equals(SaveProviderLinkingRules2Action.class)) {
            ProviderLinkingRulesService rules = (ProviderLinkingRulesService)
                    autoMocks.computeIfAbsent(ProviderLinkingRulesService.class, Mockito::mock);
            return new SaveProviderLinkingRules2Action(mock(SecurityInfoManager.class), rules);
        }
        if (actionClass.equals(DeleteMyGroupMembers2Action.class)) {
            MyGroupMembershipService memberships = (MyGroupMembershipService)
                    autoMocks.computeIfAbsent(MyGroupMembershipService.class, Mockito::mock);
            return new DeleteMyGroupMembers2Action(mock(SecurityInfoManager.class), memberships);
        }
        if (actionClass.equals(SecurityUpdate2Action.class)) {
            CarlosMethodSecurity methodSecurity = mock(CarlosMethodSecurity.class);
            when(methodSecurity.hasAdminWrite()).thenReturn(true);
            return new SecurityUpdate2Action(methodSecurity);
        }
        if (METHOD_MAP_DISPATCHERS.contains(actionClass.getName())) {
            // Spring runs the @PostConstruct init() that fills methodMap; without it every dispatch falls back to view.
            Object action = actionClass.getDeclaredConstructor().newInstance();
            actionClass.getMethod("init").invoke(action);
            return action;
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
     * containing a literal POST method comparison plus either a
     * {@code SC_METHOD_NOT_ALLOWED} reference or a {@code methodNotAllowed(...)}
     * helper call is not registered in one of
     * {@link #unconditionalMutators()}, {@link #CONDITIONAL_MUTATORS}, or
     * {@link #NON_MUTATOR_GATES}.
     *
     * <p>When this test fails, add the new class to the appropriate list and
     * (for conditional mutators) add a focused test that drives a GET with
     * mutation intent and asserts 405 / SecurityException + no side-effects.
     */
    @Test
    @DisplayName("discovery: every *2Action with a POST check and a 405 answer must be registered")
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
            .as("New *2Action classes in the in-scope slices (%s) contain a POST check plus "
              + "SC_METHOD_NOT_ALLOWED or a methodNotAllowed(...) call, but are not registered in "
              + "MutatorActionGetRejectionContractUnitTest. "
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
            if ((source.contains("SC_METHOD_NOT_ALLOWED") || source.contains("methodNotAllowed("))
                    && (source.contains("\"POST\".equals(")
                        || source.contains("\"POST\".equalsIgnoreCase(")
                        || source.contains(".equalsIgnoreCase(\"POST\")"))) {
                out.add(className);
            }
        }
        Collections.sort(out);
        return out;
    }

    @Test
    void shouldDiscoverNewPortalMutator_usingSharedMethodGuard(
            @org.junit.jupiter.api.io.TempDir Path sourceRoot) throws Exception {
        Path source = sourceRoot.resolve(
                "io/github/carlos_emr/carlos/integration/patientportal/web/FuturePortal2Action.java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, """
                class FuturePortal2Action {
                    void execute() {
                        if (!"POST".equals(request.getMethod())) return methodNotAllowed(response);
                    }
                }
                """);
        assertThat(scanCandidates(sourceRoot)).contains(
                "io.github.carlos_emr.carlos.integration.patientportal.web.FuturePortal2Action");
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
