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
package io.github.carlos_emr.carlos.casemgmt.web;

import io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager;
import io.github.carlos_emr.carlos.casemgmt.common.ChartProgramContext;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNote;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.casemgmt.web.formbeans.CaseManagementEntryFormBean;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.GroupNoteDao;
import io.github.carlos_emr.carlos.commn.model.Admission;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathFactory;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Actual action entrypoints plus shipped native routing; no JSP mutation or verb waiver.
 * @since 2026-10-25
 */
@Tag("unit") @Tag("casemgmt")
class GroupNoteSelect2ActionUnitTest extends CarlosUnitTestBase {
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockHttpSession session;
    private MockedStatic<ServletActionContext> servlet;
    private SecurityInfoManager security;
    private CaseManagementManager notes;
    private AdmissionManager admissions;
    private DemographicDao demographics;
    private GroupNoteDao links;
    private LoggedInInfo login;
    private CaseManagementEntryFormBean form;
    private CaseManagementNote note;
    private String key;
    private String token;
    private TestAction action;

    class TestAction extends GroupNoteSelect2Action {
        CaseManagementEntryFormBean saved;
        String savedProgram;
        Runnable duringSave;
        @Override protected void saveGroupNote(LoggedInInfo user, CaseManagementEntryFormBean value, String program) {
            assertThat(user).isSameAs(login);
            saved = value; savedProgram = program;
            if (duringSave != null) duringSave.run();
        }
    }

    @BeforeEach void prepare() {
        security = createAndRegisterMock(SecurityInfoManager.class);
        notes = createAndRegisterMock(CaseManagementManager.class);
        admissions = createAndRegisterMock(AdmissionManager.class);
        demographics = createAndRegisterMock(DemographicDao.class);
        links = createAndRegisterMock(GroupNoteDao.class);
        login = mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("4250");
        Facility facility = new Facility(); facility.setEnableGroupNotes(true);
        when(login.getCurrentFacility()).thenReturn(facility);
        session = new MockHttpSession(); LoggedInInfo.setLoggedInInfoIntoSession(session, login);
        request = new MockHttpServletRequest(); request.setSession(session); request.setMethod("GET");
        request.setParameter("demographicNo", "101"); request.setParameter("noteId", "51");
        request.setParameter("programId", "7"); request.setParameter("num_anonymous", "0");
        ChartProgramContext.remember(request, "7"); token = ChartProgramContext.token(request);
        request.setParameter("chartContext", token);
        response = new MockHttpServletResponse();
        servlet = mockStatic(ServletActionContext.class);
        servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        when(security.hasPrivilege(eq(login), anyString(), anyString(), anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login), anyInt())).thenReturn(true);
        when(notes.isClientInProgramDomain(eq("4250"), anyString())).thenReturn(true);
        note = new CaseManagementNote(); note.setId(51L); note.setDemographic_no("101");
        when(notes.getNote("51")).thenReturn(note);
        when(notes.filterNotes(eq(login), eq("4250"), anyCollection(), eq("7"))).thenReturn(List.of(note));
        form = new CaseManagementEntryFormBean(); form.setNoteId("51"); form.setCaseNote(note);
        key = ChartProgramContext.formKey(request, "101"); session.setAttribute(key, form);
        Admission admission = mock(Admission.class); when(admission.getClientId()).thenReturn(202);
        when(admissions.getCurrentAdmissionsByProgramId("7")).thenReturn(List.of(admission));
        Demographic client = new Demographic(); client.setDemographicNo(202); client.setLastName("Recipient <患者>");
        when(demographics.getDemographic("202")).thenReturn(client);
        when(links.findLinksByNoteId(51)).thenReturn(List.of());
        action = new TestAction();
    }
    @AfterEach void closeServlet() { if (servlet != null) servlet.close(); }

    @Test
    void shouldRenderCapturedProgramDespiteOtherWindowAndForgedProgramHint_whenUsingNativeGroupSelection() throws Exception {
        var other = new MockHttpServletRequest(); other.setSession(session); other.setParameter("demographicNo", "101");
        ChartProgramContext.remember(other, "8");
        request.setParameter("programId", "999");
        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("groupNoteProgram")).isEqualTo("7");
        assertThat(request.getAttribute("groupNoteContext")).isEqualTo(token);
        assertThat(request.getAttribute("groupNoteId")).isEqualTo("51");
        assertThat((List<?>) request.getAttribute("groupNoteMembers")).hasSize(1);
        verify(admissions).getCurrentAdmissionsByProgramId("7");
        assertThat(action.saved).isNull();
    }
    @Test
    void shouldSaveDetachedSelectionThenLeaveRedirectToMappedGet_whenUsingNativeGroupSelection() throws Exception {
        request.setMethod("POST"); request.setParameter("group_client_id", "202");
        assertThat(action.save()).isEqualTo("success");
        assertThat(action.saved).isNotSameAs(form);
        assertThat(action.saved.getNoteId()).isEqualTo("51");
        assertThat(action.saved.getGroupNoteClientIds()).containsExactly("202");
        assertThat(action.savedProgram).isEqualTo("7");
        assertThat(form.isGroupNote()).isTrue();
    }
    @Test
    void shouldPreserveNativeUnsavedMessageWithoutLoadingAdmissionsOrNote_whenUsingNativeGroupSelection() throws Exception {
        request.setParameter("noteId","0"); form.setNoteId(null);
        assertThat(action.execute()).isEqualTo("success");
        assertThat(request.getAttribute("groupNoteUnsaved")).isEqualTo(true);
        verify(notes,never()).getNote(anyString());
        verifyNoInteractions(admissions, demographics, links);
        assertThat(action.saved).isNull();
    }
    @Test
    void shouldAllowEmptySelectionToClearExistingSharing_whenUsingNativeGroupSelection() throws Exception {
        request.setMethod("POST");
        assertThat(action.save()).isEqualTo("success");
        assertThat(action.saved.getGroupNoteClientIds()).isEmpty();
        assertThat(action.saved.getGroupNoteTotalAnonymous()).isZero();
    }
    @Test
    void shouldNotRepublishOverAnotherEditorDuringPersistence_whenUsingNativeGroupSelection() throws Exception {
        request.setMethod("POST"); request.setParameter("group_client_id", "202");
        var replacement = new CaseManagementEntryFormBean(); replacement.setNoteId("52");
        action.duringSave = () -> session.setAttribute(key, replacement);
        assertThat(action.save()).isEqualTo("success");
        assertThat(session.getAttribute(key)).isSameAs(replacement);
        assertThat(replacement.isGroupNote()).isFalse();
        assertThat(form.isGroupNote()).isFalse();
    }
    @ParameterizedTest @ValueSource(strings={"GET","HEAD","PUT","DELETE","PATCH"})
    void shouldRefuseNonPostSaveBeforeAnyRead_whenUsingNativeGroupSelection(String method) throws Exception {
        request.setMethod(method);
        assertThat(action.save()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405); assertThat(response.getHeader("Allow")).isEqualTo("POST");
        verifyNoInteractions(security, notes, admissions, demographics, links);
    }
    @ParameterizedTest @ValueSource(strings={"POST","PUT","DELETE"})
    void shouldKeepPopupReadOnly_whenUsingNativeGroupSelection(String method) throws Exception {
        request.setMethod(method); assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(405); assertThat(response.getHeader("Allow")).isEqualTo("GET, HEAD");
        verifyNoInteractions(security, notes, admissions, demographics, links);
    }
    @Test
    void shouldAllowHeadWithoutSaving_whenUsingNativeGroupSelection() throws Exception {
        request.setMethod("HEAD"); assertThat(action.execute()).isEqualTo("success"); assertThat(action.saved).isNull();
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"","0","-1","abc","2147483901"})
    void shouldRefuseMalformedPatientBeforeAnyLookup_whenUsingNativeGroupSelection(String patient) throws Exception {
        if (patient == null) request.removeParameter("demographicNo"); else request.setParameter("demographicNo", patient);
        assertThat(action.execute()).isEqualTo("none"); assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(security, notes, admissions, demographics, links);
    }
    @Test
    void shouldRefuseForgedOrForeignContextBeforeClinicalLookup_whenUsingNativeGroupSelection() {
        int signature = token.indexOf('.') + 1;
        request.setParameter("chartContext", token.substring(0, signature)
                + (token.charAt(signature) == 'A' ? "B" : "A") + token.substring(signature + 1));
        assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
        verifyNoInteractions(security, notes, admissions, demographics, links);
    }
    @ParameterizedTest @ValueSource(strings={"_eChart","_casemgmt.notes"})
    void shouldRefuseMissingWritePrivilege_whenUsingNativeGroupSelection(String module) throws Exception {
        request.setMethod("POST"); when(security.hasPrivilege(login,module,"w","101")).thenReturn(false);
        assertThat(action.save()).isEqualTo("none"); assertThat(response.getStatus()).isEqualTo(403);
        assertThat(action.saved).isNull(); verifyNoInteractions(notes, admissions, demographics, links);
    }
    @Test
    void shouldRefuseRecordExclusionBeforeClinicalLookup_whenUsingNativeGroupSelection() throws Exception {
        when(security.isAllowedAccessToPatientRecord(login,101)).thenReturn(false);
        assertThat(action.execute()).isEqualTo("none"); assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(notes, admissions, demographics, links);
    }
    @Test
    void shouldRefuseMissingFacility_beforeReadingClinicalRows() throws Exception {
        when(login.getCurrentFacility()).thenReturn(null);
        assertThat(action.execute()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(notes,admissions,demographics,links);
    }
    @Test
    void shouldRefuseSourceOutsideDomain_beforeReadingAdmissions() throws Exception {
        when(notes.isClientInProgramDomain("4250","101")).thenReturn(false);
        assertThat(action.execute()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(admissions,demographics,links);
    }
    @Test
    void shouldAllowSourceReferral_whenTheNativeChartUsesReferralAuthority() throws Exception {
        when(notes.isClientInProgramDomain("4250","101")).thenReturn(false);
        when(notes.isClientReferredInProgramDomain("4250","101")).thenReturn(true);
        assertThat(action.execute()).isEqualTo("success");
    }
    @Test
    void shouldRefuseRecipientOutsideDomain_beforeAnyPersistence() throws Exception {
        when(notes.isClientInProgramDomain("4250","202")).thenReturn(false);
        request.setMethod("POST");request.setParameter("group_client_id","202");
        assertThat(action.save()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(403);assertThat(action.saved).isNull();
    }
    @Test
    void shouldRefuseReadOnlyProgramBeforeAdmissions_whenUsingNativeGroupSelection() throws Exception {
        when(notes.hasAccessRight("note-read-only","access","4250","101","7")).thenReturn(true);
        assertThat(action.execute()).isEqualTo("none"); assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(admissions, demographics, links);
    }
    @Test
    void shouldRefuseStalePopupBeforeLoadingNoteOrChangingForm_whenUsingNativeGroupSelection() throws Exception {
        form.setNoteId("52"); request.setMethod("POST");
        assertThat(action.save()).isEqualTo("none"); assertThat(response.getStatus()).isEqualTo(409);
        assertThat(form.isGroupNote()).isFalse(); verify(notes,never()).getNote(anyString());
        verifyNoInteractions(admissions, demographics, links);
    }
    @Test
    void shouldRefuseForeignOrRoleHiddenNoteBeforeAdmissions_whenUsingNativeGroupSelection() throws Exception {
        note.setDemographic_no("303");
        assertThat(action.execute()).isEqualTo("none"); assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(admissions, demographics, links);
    }
    @Test
    void shouldRefuseRoleHiddenNoteBeforeAdmissions_whenUsingNativeGroupSelection() throws Exception {
        when(notes.filterNotes(eq(login),eq("4250"),anyCollection(),eq("7"))).thenReturn(List.of());
        assertThat(action.execute()).isEqualTo("none"); assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(admissions, demographics, links);
    }
    @ParameterizedTest @ValueSource(strings={"303","101"})
    void shouldRefuseUnenrolledOrOwnerRecipientWithoutMutation_whenUsingNativeGroupSelection(String patient) throws Exception {
        request.setMethod("POST"); request.setParameter("group_client_id",patient);
        assertThat(action.save()).isEqualTo("none"); assertThat(response.getStatus()).isEqualTo(403);
        assertThat(action.saved).isNull(); assertThat(form.isGroupNote()).isFalse();
    }
    @Test
    void shouldOmitExcludedRecipientAndRejectForgedSelection_whenUsingNativeGroupSelection() throws Exception {
        when(security.isAllowedAccessToPatientRecord(login,202)).thenReturn(false);
        assertThat(action.execute()).isEqualTo("success");
        assertThat((List<?>)request.getAttribute("groupNoteMembers")).isEmpty();
        verifyNoInteractions(demographics);
        request.setMethod("POST");request.setParameter("group_client_id","202");
        assertThat(action.save()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(403);
        assertThat(action.saved).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"_eChart","_casemgmt.notes"})
    void shouldRefuseRecipientWithoutModuleWriteBeforeAnyPersistence_whenUsingNativeGroupSelection(String module) throws Exception {
        request.setMethod("POST");request.setParameter("group_client_id","202");
        when(security.hasPrivilege(login,module,"w","202")).thenReturn(false);
        assertThat(action.save()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(403);
        assertThat(action.saved).isNull();assertThat(form.isGroupNote()).isFalse();
    }
    @Test
    void shouldRefuseReadOnlyRecipientInCapturedProgramBeforeAnyPersistence_whenUsingNativeGroupSelection() throws Exception {
        request.setMethod("POST");request.setParameter("group_client_id","202");
        when(notes.hasAccessRight("note-read-only","access","4250","202","7")).thenReturn(true);
        assertThat(action.save()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(403);
        assertThat(action.saved).isNull();assertThat(form.isGroupNote()).isFalse();
    }
    @Test
    void shouldRefuseClearingAnOldRecipientWithoutWriteAuthority_whenUsingNativeGroupSelection() throws Exception {
        var link=new io.github.carlos_emr.carlos.commn.model.GroupNoteLink();link.setNoteId(51);link.setDemographicNo(202);link.setActive(true);
        when(links.findLinksByNoteId(51)).thenReturn(List.of(link));
        when(security.hasPrivilege(login,"_casemgmt.notes","w","202")).thenReturn(false);
        request.setMethod("POST");
        assertThat(action.save()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(403);
        assertThat(action.saved).isNull();assertThat(link.isActive()).isTrue();
    }
    @ParameterizedTest @NullSource @ValueSource(strings={"","-1","1.5","2147483901","1000"})
    void shouldRefuseInvalidAnonymousCountBeforeAnySave_whenUsingNativeGroupSelection(String count) throws Exception {
        request.setMethod("POST"); if(count==null)request.removeParameter("num_anonymous");else request.setParameter("num_anonymous",count);
        assertThat(action.save()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(400);
        assertThat(action.saved).isNull();assertThat(form.isGroupNote()).isFalse();
    }
    @Test
    void shouldRenderTheActual303Location_whenTheOriginatingContextIsCaptured() throws Exception {
        request.setMethod("POST");request.setContextPath("/carlos");request.setParameter("group_client_id","202");
        assertThat(action.save()).isEqualTo("success");
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        var doc=factory.newDocumentBuilder().parse(Path.of("src/main/webapp/WEB-INF/classes/struts-clinical.xml").toFile());
        var xpath=XPathFactory.newInstance().newXPath();
        var result=(org.w3c.dom.Element)xpath.evaluate("//action[@name='casemgmt/SaveGroupNoteSelection']/result[@name='success']",doc,javax.xml.xpath.XPathConstants.NODE);
        var parameters=new java.util.LinkedHashMap<String,String>();var nodes=result.getElementsByTagName("param");
        for(int i=0;i<nodes.getLength();i++){var entry=(org.w3c.dom.Element)nodes.item(i);parameters.put(entry.getAttribute("name"),entry.getTextContent().trim());}
        var previous=org.apache.struts2.ActionContext.getContext();var previousDispatcher=org.apache.struts2.dispatcher.Dispatcher.getInstance();
        var dispatcher=new org.apache.struts2.dispatcher.Dispatcher(request.getServletContext(),java.util.Map.of("config","struts-default.xml","struts.action.extension",""));dispatcher.init();
        try {
            var container=dispatcher.getContainer();container.getInstance(org.apache.struts2.ognl.ThreadAllowlist.class).allowClassHierarchy(action.getClass());
            var stack=container.getInstance(org.apache.struts2.util.ValueStackFactory.class).createValueStack();stack.push(action);
            var context=stack.getActionContext().withContainer(container).withValueStack(stack).withServletRequest(request).withServletResponse(response).bind();
            var config=new org.apache.struts2.config.entities.ResultConfig.Builder("success",org.apache.struts2.result.ServletActionRedirectResult.class.getName()).addParams(parameters).build();
            var actionConfig=new org.apache.struts2.config.entities.ActionConfig.Builder("clinical","casemgmt/SaveGroupNoteSelection",action.getClass().getName()).addResultConfig(config).build();
            var proxy=mock(org.apache.struts2.ActionProxy.class);when(proxy.getConfig()).thenReturn(actionConfig);
            var invocation=mock(org.apache.struts2.ActionInvocation.class);when(invocation.getProxy()).thenReturn(proxy);when(invocation.getInvocationContext()).thenReturn(context);
            when(invocation.getStack()).thenReturn(stack);when(invocation.getResultCode()).thenReturn("success");
            var redirect=new org.apache.struts2.result.ServletActionRedirectResult("/",parameters.get("actionName"),null);container.inject(redirect);
            redirect.setSuppressEmptyParameters(true);redirect.setStatusCode(Integer.parseInt(parameters.get("statusCode")));redirect.execute(invocation);
            assertThat(response.getStatus()).isEqualTo(303);
            var location=java.net.URI.create(response.getHeader("Location"));
            assertThat(location.getPath()).isEqualTo("/carlos/casemgmt/ViewGroupNoteSelectAction");
            var values=java.util.Arrays.stream(location.getRawQuery().split("&")).map(value->value.split("=",2))
                .collect(java.util.stream.Collectors.toMap(value->value[0],value->java.net.URLDecoder.decode(value[1],java.nio.charset.StandardCharsets.UTF_8)));
            assertThat(values).containsEntry("demographicNo","101").containsEntry("programId","7").containsEntry("chartContext",token);
        } finally {
            dispatcher.getContainer().getInstance(org.apache.struts2.ognl.ThreadAllowlist.class).clearAllowlist();dispatcher.cleanup();
            if(previousDispatcher==null)org.apache.struts2.dispatcher.Dispatcher.clearInstance();else org.apache.struts2.dispatcher.Dispatcher.setInstance(previousDispatcher);
            if(previous==null)org.apache.struts2.ActionContext.clear();else previous.bind();
        }
    }
    @Test
    void shouldKeepNativeRoutesMappedAndRedirectPostToGet_whenUsingNativeGroupSelection() throws Exception {
        var factory=DocumentBuilderFactory.newInstance();factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd",false);
        var doc=factory.newDocumentBuilder().parse(Path.of("src/main/webapp/WEB-INF/classes/struts-clinical.xml").toFile());
        var xpath=XPathFactory.newInstance().newXPath();
        String base="//action[@name='casemgmt/SaveGroupNoteSelection']";
        assertThat(xpath.evaluate(base+"/@class",doc)).isEqualTo(GroupNoteSelect2Action.class.getName());
        assertThat(xpath.evaluate(base+"/@method",doc)).isEqualTo("save");
        assertThat(xpath.evaluate(base+"/result/@type",doc)).isEqualTo("redirectAction");
        assertThat(xpath.evaluate(base+"/result/param[@name='statusCode']",doc)).isEqualTo("303");
        assertThat(xpath.evaluate(base+"/result/param[@name='actionName']",doc)).isEqualTo("casemgmt/ViewGroupNoteSelectAction");
        for(String name:List.of("demographicNo","programId","chartContext"))assertThat(xpath.evaluate(base+"/result/param[@name='"+name+"']",doc)).isEqualTo("${"+name+"}");
        assertThat(xpath.evaluate("//action[@name='casemgmt/ViewGroupNoteSelectAction']/@class",doc)).endsWith(".ViewClinical2Action");
        assertThat(Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/groupNoteSelectAction.jsp"))).doesNotContain("saveGroupNote","setGroupNote","SpringUtils");
    }
    @ParameterizedTest @ValueSource(strings={"0","00","000","0000000000","02"})
    void shouldAcceptNativeNonnegativeCount_whenDigitsContainLeadingZeros(String count) throws Exception {
        request.setMethod("POST");request.setParameter("num_anonymous",count);
        assertThat(action.save()).isEqualTo("success");
        assertThat(action.saved.getGroupNoteTotalAnonymous()).isEqualTo(Integer.parseInt(count));
    }
    @Test void shouldAcceptMatchingZeroAliases_whenCountsRepeat() throws Exception {
        request.setMethod("POST");request.setParameter("num_anonymous",new String[]{"0","00","000"});
        assertThat(action.save()).isEqualTo("success");assertThat(action.saved.getGroupNoteTotalAnonymous()).isZero();
    }
    @Test void shouldAcceptMatchingPositiveAliases_whenCountsRepeat() throws Exception {
        request.setMethod("POST");request.setParameter("num_anonymous",new String[]{"02","2"});
        assertThat(action.save()).isEqualTo("success");assertThat(action.saved.getGroupNoteTotalAnonymous()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"-0","+0","0.0","0e0"})
    void shouldKeepNonDecimalCountsRefused_whenZeroCompatibilityIsAllowed(String count) throws Exception {
        request.setMethod("POST");request.setParameter("num_anonymous",count);
        assertThat(action.save()).isEqualTo("none");assertThat(response.getStatus()).isEqualTo(400);assertThat(action.saved).isNull();
    }

}
