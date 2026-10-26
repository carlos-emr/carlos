/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.util.concurrent.atomic.AtomicReference;
import java.util.Map;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.demographic.data.DemographicMerged;
import java.util.HashMap;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.apache.struts2.ActionSupport;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.commn.OtherIdManager;
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.event.EventService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.waitinglist.WaitingList;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("appointment")
class AppointmentTextBoundsUnitTest extends CarlosUnitTestBase {
 private final SecurityInfoManager security=mock(SecurityInfoManager.class);
 private final OscarAppointmentDao appointments=mock(OscarAppointmentDao.class);
 private final AppointmentArchiveDao archives=mock(AppointmentArchiveDao.class);
 private final EventService events=mock(EventService.class);
 private final LoggedInInfo info=mock(LoggedInInfo.class);
 private final AtomicReference<Appointment> written=new AtomicReference<>();
 private MockHttpServletRequest request;
 private MockHttpServletResponse response;
 private MockedStatic<ServletActionContext> servlet;
 private MockedStatic<LoggedInInfo> sessions;
 private MockedStatic<OtherIdManager> otherIds;
 private MockedStatic<WaitingList> waitingLists;
 private final Appointment existing=new Appointment();
 private String lastResult;
 static class Transactions extends AbstractPlatformTransactionManager {
  protected Object doGetTransaction(){return new Object();}
  protected void doBegin(Object tx,TransactionDefinition d){}
  protected void doCommit(DefaultTransactionStatus s){}
  protected void doRollback(DefaultTransactionStatus s){}
 }
 @BeforeEach void setup(){
  registerMock(SecurityInfoManager.class,security);registerMock(OscarAppointmentDao.class,appointments);
  registerMock(AppointmentArchiveDao.class,archives);registerMock(WaitingListDao.class,mock(WaitingListDao.class));
  registerMock(EventService.class,events);registerMock(PlatformTransactionManager.class,new Transactions());
  registerMock(OtherIdDAO.class,mock(OtherIdDAO.class));
  servlet=mockStatic(ServletActionContext.class);servlet.when(ServletActionContext::getRequest).thenAnswer(x->request);servlet.when(ServletActionContext::getResponse).thenAnswer(x->response);
  sessions=mockStatic(LoggedInInfo.class);sessions.when(()->LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class))).thenReturn(info);
  otherIds=mockStatic(OtherIdManager.class);waitingLists=mockStatic(WaitingList.class);WaitingList waiting=mock(WaitingList.class);when(waiting.getFound()).thenReturn(false);waitingLists.when(WaitingList::getInstance).thenReturn(waiting);
  when(info.getLoggedInProviderNo()).thenReturn("999998");when(security.hasPrivilege(eq(info),eq("_appointment"),eq("w"),isNull())).thenReturn(true);
  doAnswer(x->{Appointment value=x.getArgument(0);value.setId(61);written.set(value);return null;}).when(appointments).persist(any(Appointment.class));
  doAnswer(x->{written.set(x.getArgument(0));return x.getArgument(0);}).when(appointments).merge(any(Appointment.class));
  existing.setId(61);existing.setProviderNo("999998");existing.setName("FAKE-PATIENT,Check");existing.setReason("prior");existing.setNotes("prior notes");existing.setResources("prior resources");existing.setStatus("t");
  when(appointments.findForUpdate(61)).thenReturn(existing);
  request=new MockHttpServletRequest();request.setSession(new MockHttpSession());request.getSession().setAttribute("user","999998");request.setMethod("POST");
  for(String[] p:new String[][]{{"provider_no","999998"},{"appointment_no","61"},{"appointment_date","2026-10-21"},{"start_time","09:00"},{"end_time","09:14"},{"keyword","FAKE-PATIENT,Check"},{"reason","reason"},{"notes","notes"},{"resources","resources"},{"demographic_no",""},{"status","t"}})request.setParameter(p[0],p[1]);
  request.setParameter(AppointmentEditVersion.PARAMETER, AppointmentEditVersion.of(existing, null));
  response=new MockHttpServletResponse();
 }
 @AfterEach void close(){for(MockedStatic<?> m:new MockedStatic<?>[]{waitingLists,otherIds,sessions,servlet})if(m!=null)m.close();}
 private void refused(boolean update,String field,String value)throws Exception{
  request.setParameter(field,value);lastResult=update?new AppointmentUpdateRecord2Action().execute():new AppointmentAddRecord2Action().execute();
  assertThat(response.getStatus()).as("explicit native length refusal for %s",field).isEqualTo(400);
  verify(appointments,never()).persist(any());verify(appointments,never()).merge(any());verifyNoInteractions(archives,events);otherIds.verify(() -> OtherIdManager.saveIdAppointment(anyString(), anyString(), any()), never());
  assertThat(request.getParameter(field)).isEqualTo(value);assertThat(request.getAttribute("success")).isNull();
 }
 @Test void addRefusesTwoHundredFiftySixCharacterFreeTextNameBeforePersistence()throws Exception{refused(false,"keyword","N".repeat(256));}
 @Test void addRefusesTwoHundredFiftySixCharacterResourcesBeforePersistence()throws Exception{refused(false,"resources","R".repeat(256));}
 @Test void updateRefusesTwoHundredFiftySixCharacterNameBeforeArchiving()throws Exception{refused(true,"keyword","N".repeat(256));}
 @Test void updateRefusesEightyOneCharacterReasonBeforeArchiving()throws Exception{refused(true,"reason","R".repeat(81));}
 @Test void addPreservesLastCharacterOfFullLogicalReasonWithPostedCrLf()throws Exception{
  String logical="R".repeat(78)+"\nZ";request.setParameter("reason",logical.replace("\n","\r\n"));new AppointmentAddRecord2Action().execute();
  assertThat(written.get().getReason()).isEqualTo(logical).endsWith("Z");
 }
 @Test void updatePreservesLastCharacterOfFullLogicalNotesWithPostedCrLf()throws Exception{
  String logical="N".repeat(253)+"\nZ";request.setParameter("notes",logical.replace("\n","\r\n"));new AppointmentUpdateRecord2Action().execute();
  assertThat(written.get().getNotes()).isEqualTo(logical).endsWith("Z");
 }
 @Test void addAcceptsTwoHundredFiftyFiveFullUnicodeCodePointsWithoutSplittingSurrogates()throws Exception{
  String name="😀".repeat(255);request.setParameter("keyword",name);new AppointmentAddRecord2Action().execute();assertThat(written.get().getName()).isEqualTo(name);
 }
 static Stream<Arguments> fields(){
  return Stream.of(false,true).flatMap(update->Stream.of(new Object[]{"keyword",255},new Object[]{"reason",80},new Object[]{"notes",255},new Object[]{"resources",255})
   .map(field->Arguments.of(update,field[0],field[1])));
 }
 private String execute(boolean update)throws Exception{return update?new AppointmentUpdateRecord2Action().execute():new AppointmentAddRecord2Action().execute();}
 private String unicodeValue(int codepoints){String prefix="O'Bé中文😀&amp;\"\\%41+;";return prefix+"x".repeat(codepoints-prefix.codePointCount(0,prefix.length())-1)+"Z";}
 private String stored(String field){return switch(field){case "keyword"->written.get().getName();case "reason"->written.get().getReason();case "notes"->written.get().getNotes();default->written.get().getResources();};}
 @ParameterizedTest @MethodSource("fields")
 void acceptsEveryExactUtf8CodePointBoundary(boolean update,String field,int maximum)throws Exception{
  String value=unicodeValue(maximum);request.setParameter(field,value);assertThat(execute(update)).isEqualTo(ActionSupport.SUCCESS);
  assertThat(stored(field)).isEqualTo(value);assertThat(response.getStatus()).isEqualTo(200);
 }
 @ParameterizedTest @MethodSource("fields")
 void refusesEveryOneCodePointOverflowWithoutArchiveOrPersistence(boolean update,String field,int maximum)throws Exception{
  refused(update,field,unicodeValue(maximum+1));assertThat(lastResult).isEqualTo(ActionSupport.INPUT);
  assertThat(request.getAttribute("appointmentTextRetry")).isEqualTo(true);
  assertThat(request.getAttribute("appointmentTextMaximum")).isEqualTo(maximum);
 }
 @ParameterizedTest @MethodSource("fields")
 void refusesMalformedSurrogatesRatherThanSilentlyReplacingText(boolean update,String field,int maximum)throws Exception{
  refused(update,field,"A"+Character.toString((char)0xD800)+"Z");
  assertThat(request.getAttribute("appointmentTextMessageKey")).isEqualTo("Appointment.textInvalidUnicode");
 }
 @Test void preservesLegacyUnchangedOverCapacityFieldsAndRawCrLfBytes()throws Exception{
  existing.setName("L".repeat(300));ReflectionTestUtils.setField(existing,"reason","R".repeat(88)+"\r\nZ");ReflectionTestUtils.setField(existing,"notes","N".repeat(300)+"\r\nZ");existing.setResources("S".repeat(280));
  request.setParameter("keyword",existing.getName());request.setParameter("reason",existing.getReason().replace("\r\n","\n"));request.setParameter("notes",existing.getNotes());request.setParameter("resources",existing.getResources());
  request.setParameter(AppointmentEditVersion.PARAMETER, AppointmentEditVersion.of(existing, null));
  String reason=existing.getReason(),notes=existing.getNotes();assertThat(execute(true)).isEqualTo(ActionSupport.SUCCESS);
  assertThat(written.get().getName()).isEqualTo("L".repeat(300));assertThat(written.get().getReason()).isEqualTo(reason);assertThat(written.get().getNotes()).isEqualTo(notes);assertThat(written.get().getResources()).isEqualTo("S".repeat(280));
  var order=inOrder(archives,appointments);order.verify(archives).archiveAppointment(existing);order.verify(appointments).merge(existing);
 }
 @Test void preservesUnchangedStoredCrLfWithoutReencodingTheRecord()throws Exception{
  // JPA uses field access; model setters now normalize new writes, so seed the stored legacy bytes directly.
  ReflectionTestUtils.setField(existing,"reason","first\r\nlast");ReflectionTestUtils.setField(existing,"notes","line\rnext");request.setParameter(AppointmentEditVersion.PARAMETER, AppointmentEditVersion.of(existing, null));request.setParameter("reason","first\r\nlast");request.setParameter("notes","line\r\nnext");
  execute(true);assertThat(written.get().getReason()).isEqualTo("first\r\nlast");assertThat(written.get().getNotes()).isEqualTo("line\rnext");
 }
 @Test void normalizesNewLogicalLineEndingsOnBothNativePathsWithoutDroppingTheLastCharacter()throws Exception{
  String logical="R".repeat(78)+"\nZ";request.setParameter("reason",logical.replace("\n","\r"));request.setParameter("notes","N".repeat(253)+"\r\nZ");
  execute(true);assertThat(written.get().getReason()).isEqualTo(logical);assertThat(written.get().getNotes()).isEqualTo("N".repeat(253)+"\nZ");
 }
 @Test void addsFullLogicalNotesWithoutCountingTheHttpCrLfAsTwoCharacters()throws Exception{
  request.setParameter("notes","N".repeat(253)+"\r\nZ");execute(false);assertThat(written.get().getNotes()).isEqualTo("N".repeat(253)+"\nZ");
 }
 @Test void retainsMissingAndEmptyTextSemantics()throws Exception{
  for(String field:new String[]{"keyword","reason","notes","resources"})request.removeParameter(field);
  execute(true);assertThat(written.get().getName()).isNull();assertThat(written.get().getReason()).isNull();assertThat(written.get().getNotes()).isNull();assertThat(written.get().getResources()).isNull();
 }
 @Test void retainsExplicitEmptyStrings()throws Exception{
  for(String field:new String[]{"keyword","reason","notes","resources"})request.setParameter(field,"");execute(false);
  assertThat(written.get().getName()).isEmpty();assertThat(written.get().getReason()).isEmpty();assertThat(written.get().getNotes()).isEmpty();assertThat(written.get().getResources()).isEmpty();
 }
 @Test void statusOnlyCancellationPreservesAllExistingTextWithoutValidatingUnsubmittedEdits()throws Exception{
  request.setParameter("buttoncancel","Cancel Appt");request.setParameter("keyword","N".repeat(80));request.setParameter("notes","N".repeat(400));
  execute(true);assertThat(existing.getStatus()).isEqualTo("C");assertThat(existing.getName()).isEqualTo("FAKE-PATIENT,Check");assertThat(existing.getNotes()).isEqualTo("prior notes");verify(archives).archiveAppointment(existing);
 }
 @Test void statusOnlyNoShowPreservesLegacyText()throws Exception{
  request.setParameter("buttoncancel","No Show");request.setParameter("resources","R".repeat(300));execute(true);
  assertThat(existing.getStatus()).isEqualTo("N");assertThat(existing.getResources()).isEqualTo("prior resources");
 }
 @Test void refusesUnauthorizedOversizedAndMalformedInputBeforeAnyAppointmentLookupOrWrite(){
  when(security.hasPrivilege(eq(info),eq("_appointment"),eq("w"),isNull())).thenReturn(false);request.setParameter("appointment_no","broken");request.setParameter("keyword","N".repeat(80));
  assertThatThrownBy(()->execute(true)).isInstanceOf(SecurityException.class);assertThatThrownBy(()->execute(false)).isInstanceOf(SecurityException.class);verifyNoInteractions(appointments,archives,events);
 }
 @Test void readMethodsRemainRefusedWithoutParsingOrWriting()throws Exception{
  request.setMethod("GET");request.setParameter("appointment_no","broken");execute(false);assertThat(response.getStatus()).isEqualTo(405);response=new MockHttpServletResponse();execute(true);assertThat(response.getStatus()).isEqualTo(405);verifyNoInteractions(appointments,archives,events);
 }
 @Test void nativeRefusalRetainsEverySubmittedValueAndLeavesTheSameAddTokenRetryable()throws Exception{
  String token=FormSubmissionGuard.issue(request.getSession(),FormSubmissionGuard.Scope.APPOINTMENT_ADD,null);request.setParameter(FormSubmissionGuard.PARAMETER,token);
  request.setParameter("resources","R".repeat(256));request.setParameter("location","North's & East");request.setParameter("type","TYPE");request.setParameter("reasonCode","-1");request.setParameter("duration","15");request.setParameter("printReceipt","1");request.setParameter("appt_mc_number","mc-1");request.setParameter("urgency","critical");request.setParameter("bFirstDisp","true");
  Map<String,String[]> before=new HashMap<>();request.getParameterMap().forEach((key,value)->before.put(key,value.clone()));
  assertThat(execute(false)).isEqualTo(ActionSupport.INPUT);assertThat(response.getStatus()).isEqualTo(400);assertThat(request.getParameterMap()).containsExactlyInAnyOrderEntriesOf(before);verify(appointments,never()).persist(any());
  request.setParameter("resources","fixed");response=new MockHttpServletResponse();assertThat(execute(false)).isEqualTo(ActionSupport.SUCCESS);verify(appointments,times(1)).persist(any());
 }
 @Test void invalidRedisplayIdentityFallsBackToPlainExplicit400BeforeAnyJspCanParseIt()throws Exception{
  request.setParameter("demographic_no","2147483648");refused(true,"resources","R".repeat(256));assertThat(lastResult).isEqualTo(ActionSupport.NONE);assertThat(response.getContentAsString()).contains("maximum length").doesNotContain("2147483648");
 }
 @Test void largestSignedIntegerRedisplayIdentityIsAccepted()throws Exception{
  request.setParameter("demographic_no","2147483647");refused(true,"resources","R".repeat(256));assertThat(lastResult).isEqualTo(ActionSupport.INPUT);
 }
 @Test void nativeFreeTextPatientZeroRetainsItsRetryForm()throws Exception{
  request.setParameter("demographic_no","0");refused(true,"keyword","N".repeat(256));assertThat(lastResult).isEqualTo(ActionSupport.INPUT);
 }
 @Test void malformedDateDoesNotTurnALengthRefusalIntoAJspServerError()throws Exception{
  request.setParameter("appointment_date","2026-02-30");refused(true,"resources","R".repeat(256));assertThat(lastResult).isEqualTo(ActionSupport.NONE);assertThat(response.getContentAsString()).contains("maximum length");
 }

 @Test void acceptsTheFullBaselineLinkedPatientNameWithoutTruncatingOrBlockingTheBooking()throws Exception{
  request.setParameter("demographic_no","41");request.setParameter("keyword","short search label");Demographic patient=new Demographic();patient.setLastName("L".repeat(30));patient.setFirstName("F".repeat(30));
  try(var merged=mockConstruction(DemographicMerged.class,(value,context)->when(value.getHead("41")).thenReturn("41"));
      var data=mockConstruction(DemographicData.class,(value,context)->when(value.getDemographic(info,"41")).thenReturn(patient))){
   assertThat(execute(false)).isEqualTo(ActionSupport.SUCCESS);assertThat(response.getStatus()).isEqualTo(200);assertThat(written.get().getName()).isEqualTo(patient.getLastName()+","+patient.getFirstName());assertThat(written.get().getName().length()).isEqualTo(61);
  }
 }
 @Test void preservesAnAcceptedResolvedPatientNameEvenWhenTheSearchDisplayIsLonger()throws Exception{
  request.setParameter("demographic_no","41");request.setParameter("keyword","display text "+"N".repeat(300));Demographic patient=new Demographic();patient.setLastName("L".repeat(126));patient.setFirstName("F".repeat(128));
  try(var merged=mockConstruction(DemographicMerged.class,(value,context)->when(value.getHead("41")).thenReturn("41"));
      var data=mockConstruction(DemographicData.class,(value,context)->when(value.getDemographic(info,"41")).thenReturn(patient))){
   assertThat(execute(false)).isEqualTo(ActionSupport.SUCCESS);assertThat(written.get().getName()).isEqualTo(patient.getLastName()+","+patient.getFirstName());assertThat(written.get().getName().codePointCount(0,written.get().getName().length())).isEqualTo(255);
  }
 }

 @Test void nativeAddKeepsFreeTextPatientZeroWithoutInventingALinkedPatientName()throws Exception{
  request.setParameter("demographic_no","0");request.setParameter("keyword","Free text");
  try(var merged=mockConstruction(DemographicMerged.class,(value,context)->when(value.getHead("0")).thenReturn(null));
      var data=mockConstruction(DemographicData.class,(value,context)->when(value.getDemographic(info,"0")).thenReturn(null))){
   assertThat(execute(false)).isEqualTo(ActionSupport.SUCCESS);assertThat(written.get().getName()).isEqualTo("Free text");assertThat(written.get().getDemographicNo()).isEqualTo(0);
  }
 }

 @Test void invalidDurationCannotTurnALengthRefusalIntoANativeJspParseError()throws Exception{
  for(String duration:new String[]{"bad","2147483648","-2147483649"}){request.setParameter("duration",duration);response=new MockHttpServletResponse();refused(true,"resources","R".repeat(256));assertThat(lastResult).isEqualTo(ActionSupport.NONE);assertThat(response.getContentAsString()).contains("maximum length");}
 }
 @Test void nativeDefaultAndSignedDurationsRetainTheRetryForm()throws Exception{
  for(String duration:new String[]{""," ","null","15","-15","0"}){request.setParameter("duration",duration);response=new MockHttpServletResponse();refused(true,"resources","R".repeat(256));assertThat(lastResult).isEqualTo(ActionSupport.INPUT);}
 }

 @Test void refusesAnOverCapacityResolvedPatientNameInsteadOfSavingItsShortSearchLabel()throws Exception{
  request.setParameter("demographic_no","41");request.setParameter("keyword","short label");Demographic patient=new Demographic();patient.setLastName("L".repeat(126));patient.setFirstName("F".repeat(129));
  try(var merged=mockConstruction(DemographicMerged.class,(value,context)->when(value.getHead("41")).thenReturn("41"));
      var data=mockConstruction(DemographicData.class,(value,context)->when(value.getDemographic(info,"41")).thenReturn(patient))){
   assertThat(execute(false)).isEqualTo(ActionSupport.INPUT);assertThat(response.getStatus()).isEqualTo(400);verify(appointments,never()).persist(any());verifyNoInteractions(events);
  }
 }

}
