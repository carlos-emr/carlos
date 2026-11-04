/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.lab.gate;

import java.io.File;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.PatientLabRouting;
import io.github.carlos_emr.carlos.lab.ca.all.Hl7textResultsData;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.LabPDFCreator;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.PrintLabs2Action;
import io.github.carlos_emr.carlos.lab.ca.all.pageUtil.CreateLabLabel2Action;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.lab.service.LabPatientAccess;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.mockito.MockedConstruction;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit") @Tag("lab") @Tag("security")
class LabPatientReadSecurityUnitTest extends CarlosUnitTestBase {
    enum Gate { DISPLAY, AJAX, BC, PATIENT_LIST }
    private SecurityInfoManager security;
    private PatientLabRoutingDao routings;
    private LoggedInInfo login;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> sessions;
    private MockedStatic<Hl7textResultsData> versions;
    private MockedStatic<Factory> factory;
    private MockedStatic<LogAction> audit;

    @BeforeEach void setup() {
        security=mock(SecurityInfoManager.class);routings=mock(PatientLabRoutingDao.class);login=mock(LoggedInInfo.class);
        when(login.getLoggedInProviderNo()).thenReturn("test-provider");
        when(security.hasPrivilege(eq(login),eq("_lab"),eq("r"),isNull())).thenReturn(true);
        when(security.isAllowedAccessToPatientRecord(eq(login),anyInt())).thenReturn(true);
        when(routings.findByLabNoAndLabType(anyInt(),anyString())).thenReturn(List.of());
        registerMock(SecurityInfoManager.class,security);registerMock(PatientLabRoutingDao.class,routings);
        registerMock(MeasurementsDeletedDao.class,mock(MeasurementsDeletedDao.class));registerMock(MeasurementDao.class,mock(MeasurementDao.class));
        registerMock(MeasurementsExtDao.class,mock(MeasurementsExtDao.class));registerMock(MeasurementMapDao.class,mock(MeasurementMapDao.class));
        registerMock(ConsultDocsDao.class,mock(ConsultDocsDao.class));registerMock(ConsultResponseDocDao.class,mock(ConsultResponseDocDao.class));
        registerMock(Hl7TextInfoDao.class,mock(Hl7TextInfoDao.class));registerMock(Hl7TextMessageDao.class,mock(Hl7TextMessageDao.class));
        registerMock(EFormDocsDao.class,mock(EFormDocsDao.class));
        request=new MockHttpServletRequest("GET","/lab/read");response=new MockHttpServletResponse();
        request.setParameter("segmentID","77");request.setParameter("demographicNo","123");
        servlet=mockStatic(ServletActionContext.class);servlet.when(ServletActionContext::getRequest).thenReturn(request);servlet.when(ServletActionContext::getResponse).thenReturn(response);
        sessions=mockStatic(LoggedInInfo.class);sessions.when(()->LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(login);
        versions=mockStatic(Hl7textResultsData.class);versions.when(()->Hl7textResultsData.getMatchingLabs("77")).thenReturn("77");
        factory=mockStatic(Factory.class);audit=logActionMock;
    }
    @AfterEach void closeMocks(){factory.close();versions.close();sessions.close();servlet.close();}
    private String execute(Gate gate)throws Exception{return switch(gate){case DISPLAY->new ViewLabDisplay2Action().execute();case AJAX->new ViewLabDisplayAjax2Action().execute();case BC->new ViewLabBcDisplay2Action().execute();case PATIENT_LIST->new ViewDemographicLab2Action().execute();};}
    private String type(Gate gate){return gate==Gate.BC?"BCP":"HL7";}
    private void owner(int lab,String type,int patient){when(routings.findByLabNoAndLabType(lab,type)).thenReturn(List.of(new PatientLabRouting(lab,type,patient)));}
    private void locked(int patient){when(security.isAllowedAccessToPatientRecord(login,patient)).thenReturn(false);}

    @ParameterizedTest @EnumSource(Gate.class)
    void refusesLockedActualPatientBeforeAnyViewOrContentRead(Gate gate){owner(77,type(gate),123);locked(123);
        assertThatThrownBy(()->execute(gate)).isInstanceOf(SecurityException.class);factory.verifyNoInteractions();audit.verifyNoInteractions();assertThat(response.getContentAsByteArray()).isEmpty();}
    @ParameterizedTest @EnumSource(Gate.class)
    void preservesAnUnlockedPatientView(Gate gate)throws Exception{owner(77,type(gate),123);assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);verify(security).isAllowedAccessToPatientRecord(login,123);}
    @ParameterizedTest @EnumSource(Gate.class)
    void deniesGlobalPrivilegeBeforeRoutingOrPatientParsing(Gate gate){request.setParameter("segmentID","bad");request.setParameter("demographicNo","bad");when(security.hasPrivilege(login,"_lab","r",null)).thenReturn(false);
        assertThatThrownBy(()->execute(gate)).isInstanceOf(SecurityException.class);verifyNoInteractions(routings);verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());versions.verifyNoInteractions();}
    @ParameterizedTest @EnumSource(value=Gate.class,names={"DISPLAY","AJAX","BC"})
    void ignoresForgedOpenPatientAndProviderUrlWhenTheLabOwnerIsLocked(Gate gate){owner(77,type(gate),123);locked(123);request.setParameter("demographicNo","456");request.setParameter("demographicId","456");request.setParameter("providerNo","someone-else");
        assertThatThrownBy(()->execute(gate)).isInstanceOf(SecurityException.class);verify(security,never()).isAllowedAccessToPatientRecord(login,456);}
    @ParameterizedTest @EnumSource(value=Gate.class,names={"DISPLAY","AJAX","BC"})
    void preservesUnassignedLabsBehindTheirGlobalPrivilege(Gate gate)throws Exception{when(routings.findByLabNoAndLabType(77,type(gate))).thenReturn(List.of(new PatientLabRouting(77,type(gate),0),new PatientLabRouting(77,type(gate),null)));
        assertThat(execute(gate)).isEqualTo(ActionSupport.SUCCESS);verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());}
    @ParameterizedTest @ValueSource(strings={"GET","HEAD"})
    void refusesEveryOwnerWhenTheSameLabHasMultiplePatientRoutings(String method){request.setMethod(method);when(routings.findByLabNoAndLabType(77,"HL7")).thenReturn(List.of(new PatientLabRouting(77,"HL7",456),new PatientLabRouting(77,"HL7",123)));locked(123);
        assertThatThrownBy(()->execute(Gate.DISPLAY)).isInstanceOf(SecurityException.class);verify(security).isAllowedAccessToPatientRecord(login,456);verify(security).isAllowedAccessToPatientRecord(login,123);}
    @Test void sourceTypedRoutingCannotBeReplacedByANumericIdCollision()throws Exception{owner(77,"HL7",123);owner(77,"BCP",456);locked(123);assertThat(execute(Gate.BC)).isEqualTo(ActionSupport.SUCCESS);verify(routings).findByLabNoAndLabType(77,"BCP");verify(routings,never()).findByLabNoAndLabType(77,"HL7");}
    @ParameterizedTest @ValueSource(strings={"true","false"})
    void refusesLockedServerSelectedVersionIncludingShowLatestAndComparison(String showLatest){owner(77,"HL7",456);owner(88,"HL7",123);locked(123);request.setParameter("showLatest",showLatest);versions.when(()->Hl7textResultsData.getMatchingLabs("77")).thenReturn("77,88");
        assertThatThrownBy(()->execute(Gate.DISPLAY)).isInstanceOf(SecurityException.class);factory.verifyNoInteractions();}
    @Test void refusesLockedExplicitAllVersionsEvenWithOpenRequestedAndForgedDemographic(){owner(77,"HL7",456);owner(88,"HL7",123);locked(123);request.setParameter("all","true");request.setParameter("multiID","77,88");request.setParameter("demographicId","456");
        assertThatThrownBy(()->execute(Gate.DISPLAY)).isInstanceOf(SecurityException.class);versions.verifyNoInteractions();factory.verifyNoInteractions();}
    @Test void acceptsUnlockedVersionsAndDeduplicatesChecksWithoutDroppingTheSelectedLab()throws Exception{owner(77,"HL7",123);owner(88,"HL7",456);request.setParameter("all","true");request.setParameter("multiID","88,77,88");assertThat(execute(Gate.DISPLAY)).isEqualTo(ActionSupport.SUCCESS);verify(routings,times(1)).findByLabNoAndLabType(77,"HL7");verify(routings,times(1)).findByLabNoAndLabType(88,"HL7");}
    @ParameterizedTest @ValueSource(strings={"0","-1","2147483648","1x","","999999999999999999999"})
    void refusesInvalidLabIdentityWithoutDaoOrPdfReads(String identity)throws Exception{request.setParameter("segmentID",identity);assertThat(execute(Gate.DISPLAY)).isEqualTo(ActionSupport.NONE);assertThat(response.getStatus()).isEqualTo(400);verifyNoInteractions(routings);versions.verifyNoInteractions();factory.verifyNoInteractions();}
    @Test void acceptsFullSignedIntegerLabIdentity()throws Exception{request.setParameter("segmentID","2147483647");request.setParameter("all","true");request.setParameter("multiID","2147483647");assertThat(execute(Gate.DISPLAY)).isEqualTo(ActionSupport.SUCCESS);verify(routings).findByLabNoAndLabType(Integer.MAX_VALUE,"HL7");}
    @ParameterizedTest @ValueSource(strings={"77,","77,bad","77,2147483648"})
    void malformedVersionCannotReachTheJsp(String ids)throws Exception{request.setParameter("all","true");request.setParameter("multiID",ids);assertThat(execute(Gate.DISPLAY)).isEqualTo(ActionSupport.NONE);assertThat(response.getStatus()).isEqualTo(400);factory.verifyNoInteractions();}
    @Test void globalPatientLabListStillOpensWithoutAFabricatedPatient()throws Exception{request.removeParameter("demographicNo");assertThat(execute(Gate.PATIENT_LIST)).isEqualTo(ActionSupport.SUCCESS);verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());}
    @Test void filtersGlobalListByActualTypedOwnersWhilePreservingOrderAndUnassignedRows(){
        LabResultData open=result("77","HL7"),locked=result("88","HL7"),unassigned=result("99","HL7"),otherSource=result("88","BCP");owner(77,"HL7",456);owner(88,"HL7",123);owner(88,"BCP",456);locked(123);
        List<LabResultData> original=List.of(open,locked,unassigned,otherSource);assertThat(LabPatientAccess.visibleResults(original,security,routings,login)).containsExactly(open,unassigned,otherSource);assertThat(original).containsExactly(open,locked,unassigned,otherSource);}
    private LabResultData result(String id,String source){LabResultData value=mock(LabResultData.class);value.segmentID=id;value.labType=source;return value;}
    @ParameterizedTest @ValueSource(strings={"GET","HEAD"})
    void printRefusesTheActualLockedPatientBeforeAuditHeadersFactoryOrTemporaryFiles(String method){request.setMethod(method);owner(77,"HL7",123);locked(123);request.setParameter("demographicNo","456");request.setParameter("providerNo","foreign");
        try(MockedConstruction<LabPDFCreator> pdf=mockConstruction(LabPDFCreator.class)){
            assertThatThrownBy(()->new PrintLabs2Action().execute()).isInstanceOf(SecurityException.class);assertThat(pdf.constructed()).isEmpty();factory.verifyNoInteractions();audit.verifyNoInteractions();assertThat(response.getContentType()).isNull();assertThat(response.getHeader("Content-Disposition")).isNull();assertThat(response.getContentAsByteArray()).isEmpty();}}
    @ParameterizedTest @ValueSource(ints={0,123})
    void printPreservesNativePdfRenderingForAnUnlockedOrUnassignedLab(int patient)throws Exception{owner(77,"HL7",patient);MessageHandler handler=mock(MessageHandler.class);when(handler.getPatientName()).thenReturn("Allowed Patient");when(handler.getHeaders()).thenReturn(new ArrayList<>(List.of("LAB")));factory.when(()->Factory.getHandler("77")).thenReturn(handler);
        try(MockedConstruction<LabPDFCreator> pdf=mockConstruction(LabPDFCreator.class,(mock,context)->{
            OutputStream output=(OutputStream)context.arguments().get(1);doAnswer(call->{output.write("%PDF-allowed-lab".getBytes(StandardCharsets.UTF_8));return null;}).when(mock).printPdf();doAnswer(call->{Files.copy(((File)call.getArgument(0)).toPath(),(OutputStream)call.getArgument(1));return null;}).when(mock).addEmbeddedDocuments(any(File.class),any(OutputStream.class));})){
            assertThat(new PrintLabs2Action().execute()).isNull();assertThat(response.getContentType()).isEqualTo("application/pdf");assertThat(response.getContentAsString()).isEqualTo("%PDF-allowed-lab");assertThat(pdf.constructed()).hasSize(1);if(patient>0)verify(security).isAllowedAccessToPatientRecord(login,patient);else verify(security,never()).isAllowedAccessToPatientRecord(any(),anyInt());}}
    @Test void unresolvedOwnershipLookupCannotSilentlyTreatKnownLabAsUnassigned(){when(routings.findByLabNoAndLabType(77,"HL7")).thenReturn(null);assertThatThrownBy(()->execute(Gate.DISPLAY)).isInstanceOf(SecurityException.class);factory.verifyNoInteractions();}
    @Test void forwardedJspSelectionPreflightRefusesEveryLockedVersionBeforeAnyParser(){
        owner(77,"HL7",456);owner(88,"HL7",123);locked(123);
        assertThatThrownBy(()->LabPatientAccess.requireLabs(security,routings,login,new String[]{"77","88"},"HL7")).isInstanceOf(SecurityException.class);
        verify(security).isAllowedAccessToPatientRecord(login,456);verify(security).isAllowedAccessToPatientRecord(login,123);factory.verifyNoInteractions();
    }
    @Test void forwardedJspSelectionKeepsUnassignedAndDeduplicatesItsCompleteSet(){
        LabPatientAccess.requireLabs(security,routings,login,new String[]{"77","77","88"},"HL7");
        verify(routings,times(1)).findByLabNoAndLabType(77,"HL7");verify(routings,times(1)).findByLabNoAndLabType(88,"HL7");
    }
    private CreateLabLabel2Action labelAction(String id){CreateLabLabel2Action action=new CreateLabLabel2Action();action.setLab_no(id);action.setLabel("new label");return action;}
    @ParameterizedTest @ValueSource(strings={"POST","HEAD"})
    void directLabelMutationRefusesLockedActualOwnerBeforeItsDaoWriteAndViewForward(String method){
        request.setMethod(method);owner(77,"HL7",123);locked(123);request.setParameter("demographicNo","456");request.setParameter("providerNo","foreign");
        when(security.hasPrivilege(login,"_lab","w",null)).thenReturn(true);Hl7TextInfoDao labels=mock(Hl7TextInfoDao.class);registerMock(Hl7TextInfoDao.class,labels);
        assertThatThrownBy(()->labelAction("77").execute()).isInstanceOf(SecurityException.class);verifyNoInteractions(labels);factory.verifyNoInteractions();audit.verifyNoInteractions();
    }
    @ParameterizedTest @ValueSource(ints={0,123})
    void directLabelMutationPreservesTheAuthorizedNativeWriteAndForward(int patient){
        request.setMethod("POST");owner(77,"HL7",patient);when(security.hasPrivilege(login,"_lab","w",null)).thenReturn(true);Hl7TextInfoDao labels=mock(Hl7TextInfoDao.class);registerMock(Hl7TextInfoDao.class,labels);
        assertThat(labelAction("77").execute()).isEqualTo("complete");verify(labels).createUpdateLabelByLabNumber("new label",77);
    }
    @Test void directLabelGlobalDenialPrecedesIdentityParsingAndDaoAccess(){request.setMethod("POST");assertThatThrownBy(()->labelAction("not-a-lab").execute()).isInstanceOf(SecurityException.class);verifyNoInteractions(routings);}
    @Test void directLabelInvalidIdentityIsAnExplicit400WithoutWritingOrForwarding(){request.setMethod("POST");when(security.hasPrivilege(login,"_lab","w",null)).thenReturn(true);Hl7TextInfoDao labels=mock(Hl7TextInfoDao.class);registerMock(Hl7TextInfoDao.class,labels);
        assertThat(labelAction("2147483648").execute()).isEqualTo(ActionSupport.NONE);assertThat(response.getStatus()).isEqualTo(400);verifyNoInteractions(routings,labels);}
    private String jspSource(String relative)throws Exception{
        String configured=System.getenv("LAB_PATIENT_READ_SOURCE_ROOT");
        java.nio.file.Path root=java.nio.file.Path.of(configured==null?System.getProperty("user.dir"):configured);
        return Files.readString(root.resolve("src/main/webapp/WEB-INF/jsp/lab/"+relative));
    }
    @Test void nativeLabListFiltersActualRowsBeforeGroupingAndRendering()throws Exception{
        String jsp=jspSource("DemographicLab.jsp");int populate=jsp.indexOf("ArrayList<LabResultData> labs = comLab.populateLabResultsData");int filter=jsp.indexOf("labs = LabPatientAccess.visibleResults(");int group=jsp.indexOf("labs = getLatestLabVersions(labs);");int render=jsp.indexOf("pageContext.setAttribute(\"labs\"");
        assertThat(populate).isNotNegative();assertThat(filter).isGreaterThan(populate);assertThat(group).isGreaterThan(filter);assertThat(render).isGreaterThan(group);
    }
    @Test void forwardedNativeLabDisplayPreflightsEachSelectedSetBeforeEitherHl7Parser()throws Exception{
        String jsp=jspSource("CA/ALL/labDisplay.jsp");int all=jsp.indexOf("if (showAll) {");int first=jsp.indexOf("LabPatientAccess.requireLabs(",all);int firstParser=jsp.indexOf("handlers.add(Factory.getHandler(",all);
        assertThat(all).isPositive();assertThat(first).isBetween(all,firstParser-1);
        int selected=jsp.indexOf("multiLabId = Hl7textResultsData.getMatchingLabs(segmentID);");int second=jsp.indexOf("LabPatientAccess.requireLabs(",selected);int secondParser=jsp.indexOf("handler = Factory.getHandler(segmentID);",selected);
        assertThat(selected).isPositive();assertThat(second).isBetween(selected,secondParser-1);
    }
}
