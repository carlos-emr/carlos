/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.demographic;

import java.util.Date;
import java.util.List;
import java.util.Set;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.CustomFilter;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.web.BillingONReview2Action;
import io.github.carlos_emr.carlos.demographic.gate.ViewDemographicLabelPrintSetting2Action;
import io.github.carlos_emr.carlos.demographic.gate.ViewEnrollmentHistory2Action;
import io.github.carlos_emr.carlos.demographic.pageUtil.DemographicSearch2Action;
import io.github.carlos_emr.carlos.documentManager.TicklerAttachmentService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.managers.TicklerManager;
import io.github.carlos_emr.carlos.model.security.Secobjprivilege;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.tickler.dto.TicklerCommentDTO;
import io.github.carlos_emr.carlos.tickler.dto.TicklerListDTO;
import io.github.carlos_emr.carlos.tickler.gate.ViewAddTickler2Action;
import io.github.carlos_emr.carlos.tickler.pageUtil.TicklerList2Action;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit") @Tag("security")
class PatientLockReadRoutesUnitTest extends CarlosUnitTestBase {
    enum Route { BILLING, ENROLLMENT, LABEL, TICKLER_FORM }
    private SecurityInfoManager security;
    private DemographicDao demographics;
    private TicklerManager ticklers;
    private TicklerAttachmentService attachments;
    private LoggedInInfo user;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;

    @BeforeEach void setup() {
        security=mock(SecurityInfoManager.class);demographics=mock(DemographicDao.class);
        ticklers=mock(TicklerManager.class);attachments=mock(TicklerAttachmentService.class);
        user=mock(LoggedInInfo.class);when(user.getLoggedInProviderNo()).thenReturn("999998");
        when(security.hasPrivilege(eq(user),anyString(),anyString(),(String)isNull())).thenReturn(true);
        when(security.hasPrivilege(eq(user),anyString(),anyString(),anyString())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(user),anyInt())).thenReturn(true);
        when(security.getSecurityObjects(user)).thenReturn(List.of());
        registerMock(SecurityInfoManager.class,security);registerMock(DemographicDao.class,demographics);
        registerMock(ClinicDAO.class,mock(ClinicDAO.class));registerMock(TicklerManager.class,ticklers);
        registerMock(TicklerAttachmentService.class,attachments);
        request=new MockHttpServletRequest("GET","/patient/read");response=new MockHttpServletResponse();
        request.setParameter("demographicNo","7");request.setParameter("demographic_no","7");
        servlet=mockStatic(ServletActionContext.class);servlet.when(ServletActionContext::getRequest).thenReturn(request);
        servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions=mockStatic(LoggedInInfo.class);sessions.when(()->LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(user);
        sessions.when(()->LoggedInInfo.requireLoggedInInfoFromSession(request)).thenCallRealMethod();
    }
    @AfterEach void closeStatics(){sessions.close();servlet.close();}
    private String execute(Route route)throws Exception{return switch(route){
        case BILLING->new BillingONReview2Action().getDemographic();
        case ENROLLMENT->new ViewEnrollmentHistory2Action().execute();
        case LABEL->new ViewDemographicLabelPrintSetting2Action().execute();
        case TICKLER_FORM->new ViewAddTickler2Action().execute();};}
    private void selected(String patient){request.setParameter("demographicNo",patient);request.setParameter("demographic_no",patient);}
    private void locks(){when(security.isAllowedAccessToPatientRecord(user,7)).thenReturn(false);
        when(security.getSecurityObjects(user)).thenReturn(List.of(new Secobjprivilege("doctor","_eChart$7","|o|",0,"999998")));}

    @ParameterizedTest @EnumSource(Route.class)
    void lockedPatientCannotReachAnyNativePatientReadOrView(Route route)throws Exception{
        locks();assertThatThrownBy(()->execute(route)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(demographics,ticklers,attachments);assertThat(response.getContentAsByteArray()).isEmpty();
        assertThat(request.getAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE)).isNull();
    }
    @ParameterizedTest @EnumSource(Route.class)
    void headCannotBypassTheSamePatientReadGate(Route route)throws Exception{
        request.setMethod("HEAD");locks();assertThatThrownBy(()->execute(route)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(demographics,ticklers,attachments);assertThat(response.getContentAsByteArray()).isEmpty();
    }
    @ParameterizedTest @EnumSource(Route.class)
    void unlockedPatientRetainsNativeBillingJsonOrAuthorizedViewIdentity(Route route)throws Exception{
        selected("000008");Demographic patient=new Demographic();patient.setDemographicNo(8);patient.setLastName("FAKE-OPEN-PATIENT");
        when(demographics.getDemographic("8")).thenReturn(patient);
        String result=execute(route);verify(security).isAllowedAccessToPatientRecord(user,8);
        if(route==Route.BILLING){assertThat(result).isEqualTo(ActionSupport.NONE);verify(demographics).getDemographic("8");
            assertThat(response.getContentAsString()).contains("FAKE-OPEN-PATIENT");assertThat(response.getContentType()).startsWith("application/json");}
        else{assertThat(result).isEqualTo(ActionSupport.SUCCESS);assertThat(request.getAttribute(PatientReadAccess.AUTHORIZED_PATIENT_ATTRIBUTE)).isEqualTo("8");}
    }
    @ParameterizedTest @EnumSource(Route.class)
    void conflictingPatientAliasCannotAuthorizeOnePatientAndRenderAnother(Route route)throws Exception{
        request.setParameter("demographic_no","8");assertThat(execute(route)).isEqualTo(ActionSupport.NONE);assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(demographics);verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());
    }
    @ParameterizedTest @EnumSource(Route.class)
    void globalDenialStillPrecedesInvalidPatientParsing(Route route){
        selected("not-a-patient");when(security.hasPrivilege(eq(user),anyString(),anyString(),(String)isNull())).thenReturn(false);
        assertThatThrownBy(()->execute(route)).isInstanceOf(SecurityException.class);
        verifyNoInteractions(demographics);verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());
    }
    @ParameterizedTest @EnumSource(Route.class)
    void overflowingPatientCannotReachTheDataLookupOrView(Route route)throws Exception{
        selected("2147483648");assertThat(execute(route)).isEqualTo(ActionSupport.NONE);assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(demographics);verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());
    }
    @Test void blankNativeTicklerAddStillOpensWithoutInventingAPatient()throws Exception{
        request.removeParameter("demographicNo");request.removeParameter("demographic_no");
        assertThat(new ViewAddTickler2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());
    }
    @ParameterizedTest @ValueSource(strings={"","0"})
    void neutralNativeTicklerSelectionRetainsTheEmptyForm(String selection)throws Exception{
        selected(selection);assertThat(new ViewAddTickler2Action().execute()).isEqualTo(ActionSupport.SUCCESS);
        verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());verifyNoInteractions(demographics,ticklers);
    }
    @ParameterizedTest @ValueSource(strings={"2147483648","-1","bad"})
    void invalidTicklerListIdentityIs400BeforeAnyDataRead(String selection)throws Exception{
        selected(selection);new TicklerList2Action().execute();assertThat(response.getStatus()).isEqualTo(400);
        verifyNoInteractions(ticklers,attachments);verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());
    }
    @Test void lockedExplicitTicklerListFailsBeforeCountsOrDtoAndCommentLoading()throws Exception{
        locks();assertThat(new TicklerList2Action().execute()).isNull();assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).doesNotContain("FAKE");verifyNoInteractions(ticklers,attachments);
    }
    @Test void allClientsSetsServerExclusionsBeforeCountsAndHidesAnyStaleDeniedDto()throws Exception{
        locks();request.removeParameter("demographic_no");request.setParameter("demographicNo","All Clients");
        TicklerListDTO hidden=new TicklerListDTO();hidden.setId(71);hidden.setDemographicNo(7);hidden.setMessage("FAKE-LOCKED-TICKLER");
        hidden.setComments(List.of(new TicklerCommentDTO(72,71,"FAKE-LOCKED-COMMENT",new Date(),"Hidden","Provider")));
        TicklerListDTO open=new TicklerListDTO();open.setId(81);open.setDemographicNo(8);open.setMessage("FAKE-OPEN-TICKLER");
        open.setDemographicLastName("FAKE-OPEN-NAME");
        TicklerListDTO unassigned=new TicklerListDTO();unassigned.setId(91);unassigned.setMessage("FAKE-UNASSIGNED-TICKLER");
        when(ticklers.getNumTicklers(eq(user),any(CustomFilter.class))).thenAnswer(c->{
            assertThat(((CustomFilter)c.getArgument(1)).getExcludedDemographicNos()).containsExactly(7);return 2;});
        when(ticklers.getTicklerDTOs(eq(user),any(CustomFilter.class),eq(0),eq(25))).thenAnswer(c->{
            assertThat(((CustomFilter)c.getArgument(1)).getExcludedDemographicNos()).containsExactly(7);return List.of(hidden,open,unassigned);});
        new TicklerList2Action().execute();assertThat(response.getStatus()).isEqualTo(200);
        JsonNode result=new ObjectMapper().readTree(response.getContentAsString());
        assertThat(result.get("recordsTotal").asInt()).isEqualTo(2);assertThat(result.get("data").size()).isEqualTo(2);
        assertThat(result.get("data").get(1).get("message").asText()).isEqualTo("FAKE-UNASSIGNED-TICKLER");
        assertThat(result.get("data").get(1).get("demographicNo").isNull()).isTrue();
        assertThat(result.get("data").get(0).get("message").asText()).isEqualTo("FAKE-OPEN-TICKLER");
        assertThat(response.getContentAsString()).doesNotContain("FAKE-LOCKED-TICKLER","FAKE-LOCKED-COMMENT");
        verifyNoInteractions(attachments);
    }
    @ParameterizedTest @ValueSource(strings={"Search","Search "})
    void bothNativeSearchViewsReceiveTheSameCanonicalExclusionsBeforeRendering(String view){
        locks();request.setParameter("displaymode",view);request.setParameter("keyword","");
        assertThat(new DemographicSearch2Action().execute()).isEqualTo(view.endsWith(" ")?"apptResults":ActionSupport.SUCCESS);
        assertThat(PatientReadAccess.deniedPatientsFromRequest(request,security,user,"_demographic","r")).containsExactly(7);
        verify(security,times(1)).getSecurityObjects(user);
    }
    @Test void missingMetadataCannotSilentlyBecomeAnUnfilteredPatientSearch(){
        when(security.getSecurityObjects(user)).thenReturn(null);
        assertThatThrownBy(()->new DemographicSearch2Action().execute()).isInstanceOf(SecurityException.class);
    }
    @Test void missingTicklerSessionRetainsUnauthorizedJsonWithoutAnyDataReads()throws Exception{
        sessions.when(()->LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(null);
        new TicklerList2Action().execute();assertThat(response.getStatus()).isEqualTo(401);verifyNoInteractions(ticklers,attachments,security);
    }
    @Test void forwardedNativeTicklerJspPreflightsBeforeReadingAnyPatient()throws Exception{
        String jsp=java.nio.file.Files.readString(java.nio.file.Path.of(System.getProperty("user.dir"),
                "src/main/webapp/WEB-INF/jsp/tickler/ticklerAdd.jsp"));
        int dropped=jsp.indexOf("if (!patientDroppedForDocument)");
        int guard=jsp.indexOf("PatientReadAccess.authorizedOptionalPatient(");
        int read=jsp.indexOf("demographicDao.getDemographic(requestDemographicNo)");
        assertThat(dropped).isPositive();assertThat(guard).isGreaterThan(dropped);assertThat(read).isGreaterThan(guard);
        assertThat(jsp).contains("Demographic demographic = requestDemographicNo == null ? null : demographicDao.getDemographic(requestDemographicNo)");
    }

}
