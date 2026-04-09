<%-- edit-form-clinical.jsp: Roster, consent, programs, notes, toolbar (extracted from demographiceditdemographic.jsp lines 3856-5018) --%>
<%@ page import="java.util.*" %>
<%@ page import="java.net.*" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.AppointmentMainBean" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.carlos.commn.Gender" %>
<%@ page import="io.github.carlos_emr.carlos.commn.ISO36612" %>
<%@ page import="io.github.carlos_emr.carlos.commn.OtherIdManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ContactDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.CountryCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicArchiveDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicContactDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicCustDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicExtArchiveDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DemographicExtDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateCodeDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ScheduleTemplateDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.WaitingListDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Admission" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicArchive" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicExtArchive" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.CountryCode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicContact" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.DemographicCust" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupList" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupListItem" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ScheduleTemplateCode" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.WaitingListName" %>
<%@ page import="io.github.carlos_emr.carlos.commn.web.Contact2Action" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicMerged" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicRelationship" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.ProvinceNames" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.DemographicEditHelper" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.Util" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogAction" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogConst" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.PatientConsentManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.ProgramManager2" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.waitinglist.WaitingList" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.model.CaseManagementNoteLink" %>
<%@ page import="io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.AdmissionManager" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.ProgramManager" %>
<%@ page import="io.github.carlos_emr.carlos.util.ConversionUtils" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>
<c:set var="ctx" value="${ pageContext.request.contextPath }"/>
<%-- Retrieve all variables from request attributes (set by DemographicEdit2Action) --%>
<%
    String demographic_no = (String) request.getAttribute("demographic_no");
    Demographic demographic = (Demographic) request.getAttribute("demographic");
    Map<String, String> demoExt = (Map<String, String>) request.getAttribute("demoExt");
    List<DemographicArchive> archives = (List<DemographicArchive>) request.getAttribute("archives");
    List<DemographicExtArchive> extArchives = (List<DemographicExtArchive>) request.getAttribute("extArchives");
    Admission communityAdmission = (Admission) request.getAttribute("communityAdmission");
    List<Admission> serviceAdmissions = (List<Admission>) request.getAttribute("serviceAdmissions");
    if (serviceAdmissions == null) serviceAdmissions = new ArrayList<Admission>();
    List<Provider> providers = (List<Provider>) request.getAttribute("providers");
    List<Provider> doctors = (List<Provider>) request.getAttribute("doctors");
    List<Provider> nurses = (List<Provider>) request.getAttribute("nurses");
    List<Provider> midwifes = (List<Provider>) request.getAttribute("midwifes");
    List<CountryCode> countryList = (List<CountryCode>) request.getAttribute("countryList");
    boolean hasImportExtra = Boolean.TRUE.equals(request.getAttribute("hasImportExtra"));
    String usSigned = (String) request.getAttribute("usSigned");
    String privacyConsent = (String) request.getAttribute("privacyConsent");
    String informedConsent = (String) request.getAttribute("informedConsent");
    boolean privateConsentEnabled = Boolean.TRUE.equals(request.getAttribute("privateConsentEnabled"));
    ProvinceNames pNames = (ProvinceNames) request.getAttribute("pNames");
    String dateString = (String) request.getAttribute("dateString");
    String noteReason = (String) request.getAttribute("noteReason");
    String currentProgram = (String) request.getAttribute("currentProgram");
    boolean isMobileOptimized = Boolean.TRUE.equals(request.getAttribute("isMobileOptimized"));
    String prov = (String) request.getAttribute("prov");
    String curProvider_no = (String) request.getAttribute("curProvider_no");
    String userfirstname = (String) request.getAttribute("userfirstname");
    String userlastname = (String) request.getAttribute("userlastname");
    String apptProvider = (String) request.getAttribute("apptProvider");
    String appointment = (String) request.getAttribute("appointment");
    CarlosProperties oscarProps = (CarlosProperties) request.getAttribute("oscarProps");

    // DAOs/Managers from request attributes
    ScheduleTemplateCodeDao scheduleTemplateCodeDao = (ScheduleTemplateCodeDao) request.getAttribute("scheduleTemplateCodeDao");
    WaitingListDao waitingListDao = (WaitingListDao) request.getAttribute("waitingListDao");
    WaitingListNameDao waitingListNameDao = (WaitingListNameDao) request.getAttribute("waitingListNameDao");
    UserPropertyDAO pref = (UserPropertyDAO) request.getAttribute("userPropertyDAO");
    DemographicDao demographicDao = (DemographicDao) request.getAttribute("demographicDao");
    DemographicExtDao demographicExtDao = (DemographicExtDao) request.getAttribute("demographicExtDao");
    DemographicArchiveDao demographicArchiveDao = (DemographicArchiveDao) request.getAttribute("demographicArchiveDao");
    DemographicExtArchiveDao demographicExtArchiveDao = (DemographicExtArchiveDao) request.getAttribute("demographicExtArchiveDao");
    DemographicManager demographicManager = (DemographicManager) request.getAttribute("demographicManager");
    ProgramManager pm = (ProgramManager) request.getAttribute("programManager");
    ProgramDao programDao = (ProgramDao) request.getAttribute("programDao");
    AdmissionManager admissionManager = (AdmissionManager) request.getAttribute("admissionManager");
    CountryCodeDao ccDAO = (CountryCodeDao) request.getAttribute("ccDAO");
    DemographicCustDao demographicCustDao = (DemographicCustDao) request.getAttribute("demographicCustDao");
    LookupListManager lookupListManager = (LookupListManager) request.getAttribute("lookupListManager");
    ProfessionalSpecialistDao professionalSpecialistDao = SpringUtils.getBean(ProfessionalSpecialistDao.class);
    ProviderDao providerDao = (ProviderDao) request.getAttribute("providerDao");
    ProgramManager2 programManager2 = (ProgramManager2) request.getAttribute("programManager2");
    boolean showConsentsThisTime = false;
    // Cross-fragment variables (computed locally since they depend on request context)
    Admission bedAdmission = null;
    
    String rootContextPath = request.getContextPath();
    String demoPath = rootContextPath + "/demographic/";
    String printEnvelope, printLbl, printAddressLbl, printChartLbl, printSexHealthLbl, printHtmlLbl, printLabLbl;
    if (oscarProps != null && "true".equals(oscarProps.getProperty("new_label_print"))) {
        printEnvelope = demoPath + "printEnvelope.jsp?demos=";
        printLbl = demoPath + "printDemoLabel.jsp?demographic_no=";
        printAddressLbl = demoPath + "printAddressLabel.jsp?demographic_no=";
        printChartLbl = demoPath + "printDemoChartLabel.jsp?demographic_no=";
        printSexHealthLbl = demoPath + "printDemoChartLabel.jsp?labelName=SexualHealthClinicLabel&demographic_no=";
        printHtmlLbl = demoPath + "demographiclabelprintsetting.jsp?demographic_no=";
        printLabLbl = demoPath + "printClientLabLabel.jsp?demographic_no=";
    } else {
        printEnvelope = rootContextPath + "/report/GenerateEnvelopes.do?demos=";
        printLbl = demoPath + "printDemoLabelAction.do?demographic_no=";
        printAddressLbl = demoPath + "printDemoAddressLabelAction.do?demographic_no=";
        printChartLbl = demoPath + "printDemoChartLabelAction.do?demographic_no=";
        printSexHealthLbl = demoPath + "printDemoChartLabelAction.do?labelName=SexualHealthClinicLabel&demographic_no=";
        printHtmlLbl = demoPath + "demographiclabelprintsetting.jsp?demographic_no=";
        printLabLbl = demoPath + "printClientLabLabelAction.do?demographic_no=";
    }

    String wLReadonly = "";
    if (oscarProps != null && "true".equals(oscarProps.getProperty("DEMOGRAPHIC_WAITING_LIST"))) {
        wLReadonly = "readonly";
    }
    
    String warningLevel = demoExt != null ? demoExt.get("rxInteractionWarningLevel") : null;
    if (warningLevel == null) warningLevel = "0";

    String demographicExt = oscarProps != null ? oscarProps.getProperty("demographicExt") : null;
    String[] propDemoExt = new String[0];
    DecimalFormat decF = new DecimalFormat();
    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
    LookupList phuLookupList = (LookupList) request.getAttribute("phuLookupList");
    LookupList firstNationCommunities = (LookupList) request.getAttribute("firstNationCommunities");
    
    // View section variables also used in edit sections
    String hasPrimaryCarePhysician = "N/A";
    String employmentStatus = "N/A";
    final String hasPrimary = "Has Primary Care Physician";
    final String empStatus = "Employment Status";
    boolean hasDemoExt = (demographicExt != null && !demographicExt.trim().isEmpty());
    boolean hasHasPrimary = (oscarProps != null && oscarProps.isPropertyActive("showPrimaryCarePhysicianCheck"));
    boolean hasEmpStatus = (oscarProps != null && oscarProps.isPropertyActive("showEmploymentStatusOnAddEdit"));
    if (hasDemoExt && propDemoExt != null) {
        for (String item : propDemoExt) {
            if (hasPrimary.equals(item)) {
                hasPrimaryCarePhysician = org.apache.commons.lang3.StringUtils.defaultString(demoExt.get(hasPrimary.replace(" ", "_")), "N/A");
            }
            if (empStatus.equals(item)) {
                employmentStatus = org.apache.commons.lang3.StringUtils.defaultString(demoExt.get(empStatus.replace(" ", "_")), "N/A");
            }
        }
    }
    if (demographicExt != null && !demographicExt.isEmpty()) {
        propDemoExt = demographicExt.split("\\|");
    }

    
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    java.util.ResourceBundle oscarResources = ResourceBundle.getBundle("oscarResources", request.getLocale());
    
    int nStrShowLen = 20;
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";
%>
<%!
    // Helper methods available in each fragment (delegating to DemographicEditHelper)
    public String getDisabled(String fieldName) {
        return DemographicEditHelper.getDisabled(fieldName);
    }
    public String isProgramSelected(Admission admission, Integer programId) {
        return DemographicEditHelper.isProgramSelected(admission, programId);
    }
    public String isProgramSelected(List<Admission> admissions, Integer programId) {
        return DemographicEditHelper.isProgramSelected(admissions, programId);
    }
    public int timeStrToMins(String timeStr) {
        return DemographicEditHelper.timeStrToMins(timeStr);
    }
%>
<%
    // Computed intermediate values (set by action)
    String rd = (String) request.getAttribute("rd");
    String rdohip = (String) request.getAttribute("rdohip");
    String family_doc = (String) request.getAttribute("family_doc");
    String resident = (String) request.getAttribute("resident");
    String nurse = (String) request.getAttribute("nurse");
    String alert = (String) request.getAttribute("alert");
    String notes = (String) request.getAttribute("notes");
    String midwife = (String) request.getAttribute("midwife");
    String birthYear = (String) request.getAttribute("birthYear");
    String birthMonth = (String) request.getAttribute("birthMonth");
    String birthDate = (String) request.getAttribute("birthDate");
    DemographicCust demographicCust = (DemographicCust) request.getAttribute("demographicCust");
    
    // providerBean is session-scoped, populated during login
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<jsp:useBean id="apptMainBean" class="io.github.carlos_emr.AppointmentMainBean" scope="session"/>

<%-- === Original content begins here === --%>
                                                        <%-- TOGGLE OFF PATIENT CLINIC STATUS --%>
                                                    <oscar:oscarPropertiesCheck
                                                            property="DEMOGRAPHIC_PATIENT_CLINIC_STATUS" value="true">

                                                        <tr valign="top">
                                                            <td align="right" nowrap><b>
                                                                <% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                                                    out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                                                                } else { %>
                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMRP"/>
                                                                <% } %>: </b></td>
                                                            <td align="left"><select id="mrp"
                                                                                     name="provider_no" <%=getDisabled("provider_no")%>
                                                                                     style="width: 200px">
                                                                <option value=""></option>
                                                                <%
                                                                    for (Provider p : doctors) {

                                                                %>
                                                                <option value="<%=p.getProviderNo()%>"
                                                                        <%=p.getProviderNo().equals(demographic.getProviderNo()) ? "selected" : ""%>>
                                                                    <%=p.getLastName() + "," + p.getFirstName()%>
                                                                </option>
                                                                <% } %>
                                                            </select></td>
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNurse"/>: </b>
                                                            </td>
                                                            <td align="left"><select
                                                                    name="nurse" <%=getDisabled("nurse")%>
                                                                    style="width: 200px">
                                                                <option value=""></option>
                                                                <%


                                                                    for (Provider p : nurses) {
                                                                %>
                                                                <option value="<%=p.getProviderNo()%>"
                                                                        <%=p.getProviderNo().equals(nurse) ? "selected" : ""%>>
                                                                    <%=p.getLastName() + "," + p.getFirstName()%>
                                                                </option>
                                                                <% } %>
                                                            </select></td>
                                                        </tr>
                                                        <tr valign="top">
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMidwife"/>: </b>
                                                            </td>
                                                            <td align="left"><select
                                                                    name="midwife" <%=getDisabled("midwife")%>
                                                                    style="width: 200px">
                                                                <option value=""></option>
                                                                <%
                                                                    for (Provider p : midwifes) {
                                                                %>
                                                                <option value="<%=p.getProviderNo()%>"
                                                                        <%=p.getProviderNo().equals(midwife) ? "selected" : ""%>>
                                                                    <%=p.getLastName() + "," + p.getFirstName()%>
                                                                </option>
                                                                <% } %>
                                                            </select></td>
                                                            <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResident"/>:</b>
                                                            </td>
                                                            <td align="left"><select name="resident"
                                                                                     style="width: 200px" <%=getDisabled("resident")%>>
                                                                <option value=""></option>
                                                                <%
                                                                    for (Provider p : doctors) {
                                                                %>
                                                                <option value="<%=p.getProviderNo()%>"
                                                                        <%=p.getProviderNo().equals(resident) ? "selected" : ""%>>
                                                                    <%=p.getLastName() + "," + p.getFirstName()%>
                                                                </option>
                                                                <% } %>
                                                            </select></td>
                                                        </tr>

                                                        <tr valign="top">
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRefDoc"/>: </b>
                                                            </td>
                                                            <td align="left">
                                                                <% if (oscarProps.getProperty("isMRefDocSelectList", "").equals("true")) {
                                                                    // drop down list
                                                                    Properties prop = null;
                                                                    Vector vecRef = new Vector();
                                                                    List<ProfessionalSpecialist> specialists = professionalSpecialistDao.findAll();
                                                                    for (ProfessionalSpecialist specialist : specialists) {
                                                                        prop = new Properties();
                                                                        if (specialist != null && specialist.getReferralNo() != null && !specialist.getReferralNo().equals("")) {
                                                                            prop.setProperty("referral_no", specialist.getReferralNo());
                                                                            prop.setProperty("last_name", specialist.getLastName());
                                                                            prop.setProperty("first_name", specialist.getFirstName());
                                                                            vecRef.add(prop);
                                                                        }
                                                                    }

                                                                %> <select name="r_doctor" <%=getDisabled("r_doctor")%>
                                                                           onChange="changeRefDoc()"
                                                                           style="width: 200px">
                                                                <option value=""></option>
                                                                <% for (int k = 0; k < vecRef.size(); k++) {
                                                                    prop = (Properties) vecRef.get(k);
                                                                %>
                                                                <option
                                                                        value="<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>"
                                                                        <%=prop.getProperty("referral_no").equals(rdohip) ? "selected" : ""%>>
                                                                    <%=prop.getProperty("last_name") + "," + prop.getProperty("first_name")%>
                                                                </option>
                                                                <% }

                                                                %>
                                                            </select>
                                                                <script type="text/javascript" language="Javascript">
                                                                    //<!--
                                                                    function changeRefDoc() {
//alert(document.updatedelete.r_doctor.value);
                                                                        var refName = document.updatedelete.r_doctor.options[document.updatedelete.r_doctor.selectedIndex].value;
                                                                        var refNo = "";
                                                                        <% for(int k=0; k<vecRef.size(); k++) {
  		prop= (Properties) vecRef.get(k);
  	%>
                                                                        if (refName == "<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>") {
                                                                            refNo = '<%=prop.getProperty("referral_no", "")%>';
                                                                        }
                                                                        <% } %>
                                                                        document.updatedelete.r_doctor_ohip.value = refNo;
                                                                    }

                                                                    //-->
                                                                </script>
                                                                <% } else {%> <input type="text" name="r_doctor"
                                                                                     size="30"
                                                                                     maxlength="40" <%=getDisabled("r_doctor")%>
                                                                                     value="<%=rd%>"> <% } %>
                                                            </td>
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRefDocNo"/>: </b>
                                                            </td>
                                                            <td align="left"><input type="text"
                                                                                    name="r_doctor_ohip" <%=getDisabled("r_doctor_ohip")%>
                                                                                    size="20" maxlength="6"
                                                                                    value="<%=rdohip%>"> <% if ("ON".equals(prov)) { %>
                                                                <a
                                                                        href="javascript:referralScriptAttach2('r_doctor_ohip','r_doctor')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSearch"/>
                                                                    #</a> <% } %>
                                                            </td>
                                                        </tr>

                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END TOGGLE OFF PATIENT CLINIC STATUS --%>

                                                        <%-- TOGGLE OFF PATIENT ROSTERING - NOT USED IN ALL PROVINCES. --%>
                                                    <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_PATIENT_ROSTERING"
                                                                                value="true">

                                                        <tr valign="top">
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRosterStatus"/>:
                                                            </b></td>
                                                            <td align="left">
                                                                <%
                                                                    String rosterStatus = demographic.getRosterStatus();
                                                                    if (rosterStatus == null) {
                                                                        rosterStatus = "";
                                                                    }
                                                                %>
                                                                <input type="hidden" name="initial_rosterstatus"
                                                                       value="<%=rosterStatus%>"/>
                                                                <select id="roster_status" name="roster_status"
                                                                        style="width: 120px;" <%=getDisabled("roster_status")%>
                                                                        onchange="checkRosterStatus2(); updateEnrolledTo();">
                                                                    <option value=""></option>
                                                                    <option value="RO"
                                                                            <%="RO".equals(rosterStatus) ? " selected" : ""%>>
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optRostered"/></option>
                                                                    <!--
									<option value="NR"
										<%=rosterStatus.equals("NR")?" selected":""%>>
									<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optNotRostered"/></option>
									-->
                                                                    <option value="TE"
                                                                            <%=rosterStatus.equals("TE") ? " selected" : ""%>>
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optTerminated"/></option>

                                                                    <option value="FS"
                                                                            <%=rosterStatus.equals("FS") ? " selected" : ""%>>
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optFeeService"/></option>
                                                                    <%
                                                                        for (String status : demographicDao.getRosterStatuses()) {
                                                                    %>
                                                                    <option
                                                                            <%=rosterStatus.equals(status) ? " selected" : ""%>><%=status%>
                                                                    </option>
                                                                    <% }

                                                                        // end while %>
                                                                </select>
                                                                <security:oscarSec roleName="<%=roleName$%>"
                                                                                   objectName="_admin.demographic"
                                                                                   rights="r" reverse="<%=false%>">
                                                                    <input type="button" onClick="newStatus1();"
                                                                           value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnAddNew"/>">
                                                                </security:oscarSec>
                                                            </td>
                                                            <%
                                                                // Put 0 on the left on dates
                                                                // Year
                                                                decF.applyPattern("0000");

                                                                GregorianCalendar dateCal = new GregorianCalendar();
                                                                String rosterDateYear = "";
                                                                String rosterDateMonth = "";
                                                                String rosterDateDay = "";
                                                                if (demographic.getRosterDate() != null) {
                                                                    dateCal.setTime(demographic.getRosterDate());
                                                                    rosterDateYear = decF.format(dateCal.get(GregorianCalendar.YEAR));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    rosterDateMonth = decF.format(dateCal.get(GregorianCalendar.MONTH) + 1);
                                                                    rosterDateDay = decF.format(dateCal.get(GregorianCalendar.DAY_OF_MONTH));
                                                                }
                                                                String rosterTerminationDateYear = "";
                                                                String rosterTerminationDateMonth = "";
                                                                String rosterTerminationDateDay = "";
                                                                String rosterTerminationReason = "";
                                                                if (demographic.getRosterTerminationDate() != null) {
                                                                    dateCal.setTime(demographic.getRosterTerminationDate());
                                                                    rosterTerminationDateYear = decF.format(dateCal.get(GregorianCalendar.YEAR));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    rosterTerminationDateMonth = decF.format(dateCal.get(GregorianCalendar.MONTH) + 1);
                                                                    rosterTerminationDateDay = decF.format(dateCal.get(GregorianCalendar.DAY_OF_MONTH));
                                                                }
                                                                rosterTerminationReason = demographic.getRosterTerminationReason();


                                                            %>

                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.DateJoined"/>: </b>
                                                            </td>
                                                            <td align="left">
                                                                <input type="text" name="roster_date_year" size="4"
                                                                       maxlength="4" value="<%=rosterDateYear%>">
                                                                <input type="text" name="roster_date_month" size="2"
                                                                       maxlength="2" value="<%=rosterDateMonth%>">
                                                                <input type="text" name="roster_date_day" size="2"
                                                                       maxlength="2" value="<%=rosterDateDay%>">
                                                            </td>
                                                        </tr>

                                                        <tr valign="top">
                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterEnrolledTo"/>:
                                                            </b></td>
                                                            <td align="left">
                                                                <!-- select box here -->
                                                                <select id="enrolledTo"
                                                                        name="roster_enrolled_to" <%=getDisabled("roster_enrolled_to")%>
                                                                        style="width: 200px">
                                                                    <option value=""></option>
                                                                    <%
                                                                        for (Provider p : doctors) {

                                                                    %>
                                                                    <option value="<%=p.getProviderNo()%>"
                                                                            <%=p.getProviderNo().equals(demographic.getRosterEnrolledTo()) ? "selected" : ""%>>
                                                                        <%=p.getLastName() + "," + p.getFirstName()%>
                                                                    </option>
                                                                    <% } %>
                                                                </select>


                                                            </td>

                                                            <td align="right" nowrap><b></b></td>
                                                            <td align="left" colspan="3">

                                                            </td>
                                                        </tr>


                                                        <tr valign="top">

                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterTerminationDate"/>: </b>
                                                            </td>
                                                            <td align="left">
                                                                <input type="text" placeholder="yyyy"
                                                                       name="roster_termination_date_year" size="4"
                                                                       maxlength="4"
                                                                       value="<%=rosterTerminationDateYear%>">
                                                                <input type="text" placeholder="mm"
                                                                       name="roster_termination_date_month" size="2"
                                                                       maxlength="2"
                                                                       value="<%=rosterTerminationDateMonth%>">
                                                                <input type="text" placeholder="dd"
                                                                       name="roster_termination_date_day" size="2"
                                                                       maxlength="2"
                                                                       value="<%=rosterTerminationDateDay%>">
                                                            </td>


                                                            <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterTerminationReason"/>: </b>
                                                            </td>
                                                            <td align="left" colspan="3">
                                                                <select name="roster_termination_reason"
                                                                        style="width: 200px;">
                                                                    <option value="">N/A</option>
                                                                    <%for (String code : Util.rosterTermReasonProperties.getTermReasonCodes()) { %>
                                                                    <option value="<%=code %>" <%=code.equals(rosterTerminationReason) ? "selected" : "" %> ><%=Util.rosterTermReasonProperties.getReasonByCode(code) %>
                                                                    </option>
                                                                    <%} %>
                                                                </select>
                                                            </td>
                                                        </tr>

                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END TOGGLE OFF PATIENT ROSTERING --%>


                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPatientStatus"/>:</b>
                                                            <b> </b></td>
                                                        <td align="left">
                                                            <%
                                                                String patientStatus = demographic.getPatientStatus();
                                                                if (patientStatus == null) patientStatus = "";%>
                                                            <input type="hidden" name="initial_patientstatus"
                                                                   value="<%=patientStatus%>">
                                                            <select name="patient_status"
                                                                    style="width: 120px" <%=getDisabled("patient_status")%>
                                                                    onChange="updatePatientStatusDate()">
                                                                <option value="AC"
                                                                        <%="AC".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optActive"/></option>
                                                                <option value="IN"
                                                                        <%="IN".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optInActive"/></option>
                                                                <option value="DE"
                                                                        <%="DE".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optDeceased"/></option>
                                                                <option value="MO"
                                                                        <%="MO".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optMoved"/></option>
                                                                <option value="FI"
                                                                        <%="FI".equals(patientStatus) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optFired"/></option>
                                                                <%
                                                                    for (String status : demographicDao.search_ptstatus()) {
                                                                %>
                                                                <option
                                                                        <%=status.equals(patientStatus) ? " selected" : ""%>><%=status%>
                                                                </option>
                                                                <% }

                                                                    // end while %>
                                                            </select>
                                                            <security:oscarSec roleName="<%=roleName$%>"
                                                                               objectName="_admin.demographic"
                                                                               rights="r" reverse="<%=false%>">
                                                                <input type="button" onClick="newStatus();"
                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnAddNew"/>">
                                                            </security:oscarSec>
                                                        </td>

                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.PatientStatusDate"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                decF.applyPattern("0000");

                                                                GregorianCalendar dateCal = new GregorianCalendar();
                                                                String patientStatusDateYear = "";
                                                                String patientStatusDateMonth = "";
                                                                String patientStatusDateDay = "";
                                                                if (demographic.getPatientStatusDate() != null) {
                                                                    dateCal.setTime(demographic.getPatientStatusDate());
                                                                    patientStatusDateYear = decF.format(dateCal.get(GregorianCalendar.YEAR));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    patientStatusDateMonth = decF.format(dateCal.get(GregorianCalendar.MONTH) + 1);
                                                                    patientStatusDateDay = decF.format(dateCal.get(GregorianCalendar.DAY_OF_MONTH));
                                                                }
                                                            %>
                                                            <input type="text" placeholder="yyyy"
                                                                   name="patientstatus_date_year" size="4" maxlength="4"
                                                                   value="<%=patientStatusDateYear%>">
                                                            <input type="text" placeholder="mm"
                                                                   name="patientstatus_date_month" size="2"
                                                                   maxlength="2" value="<%=patientStatusDateMonth%>">
                                                            <input type="text" placeholder="dd"
                                                                   name="patientstatus_date_day" size="2" maxlength="2"
                                                                   value="<%=patientStatusDateDay%>">
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <%if (!"true".equals(CarlosProperties.getInstance().getProperty("phu.hide", "false"))) { %>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPHU"/>:</b>
                                                        </td>
                                                        <td align="left">
                                                            <select id="PHU" name="PHU">
                                                                <option value="">Select Below</option>
                                                                <%
                                                                    if (phuLookupList != null) {
                                                                        for (LookupListItem llItem : phuLookupList.getItems()) {
                                                                            if (llItem.isActive()) {
                                                                                String selected = "";
                                                                                if (llItem.getValue().equals(StringUtils.trimToEmpty(demoExt.get("PHU")))) {
                                                                                    selected = " selected=\"selected\" ";
                                                                                }
                                                                %>
                                                                <option value="<%=llItem.getValue()%>" <%=selected%>><%=llItem.getLabel()%>
                                                                </option>
                                                                <%
                                                                            }
                                                                        }
                                                                    }

                                                                %>
                                                            </select>
                                                        </td>
                                                        <% } else { %>
                                                        <td align="right"></td>
                                                        <td align="left"><input type="hidden" name="PHU" value=""/></td>
                                                        <% } %>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formChartNo"/>:</b>
                                                        </td>
                                                        <td align="left"><input type="text" name="chart_no"
                                                                                size="30"
                                                                                value="<%=StringUtils.trimToEmpty(demographic.getChartNo())%>" <%=getDisabled("chart_no")%>>
                                                        </td>
                                                    </tr>

                                                    <tr>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="web.record.details.archivedPaperChart"/>: </b></td>
                                                        <td align="left">
                                                            <%
                                                                String paperChartIndicator = StringUtils.trimToEmpty(demoExt.get("paper_chart_archived"));
                                                                String paperChartIndicatorDate = StringUtils.trimToEmpty(demoExt.get("paper_chart_archived_date"));
                                                                String paperChartIndicatorProgram = StringUtils.trimToEmpty(demoExt.get("paper_chart_archived_program"));
                                                            %>
                                                            <select name="paper_chart_archived"
                                                                    id="paper_chart_archived" <%=getDisabled("paper_chart_archived")%>
                                                                    onChange="updatePaperArchive()">
                                                                <option value="" <%="".equals(paperChartIndicator) ? " selected" : ""%>>
                                                                </option>
                                                                <option value="NO" <%="NO".equals(paperChartIndicator) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.no"/>
                                                                </option>
                                                                <option value="YES"    <%="YES".equals(paperChartIndicator) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.yes"/>
                                                                </option>
                                                            </select>

                                                            <input type="text" placeholder="yyyy-mm-dd"
                                                                   name="paper_chart_archived_date"
                                                                   id="paper_chart_archived_date"
                                                                   value="<%=paperChartIndicatorDate%>">
                                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="archive_date_cal">

                                                            <input type="hidden" name="paper_chart_archived_program"
                                                                   id="paper_chart_archived_program"
                                                                   value="<%=paperChartIndicatorProgram%>"/>
                                                        </td>
                                                        <td><!-- padding --></td>
                                                        <td><!-- padding --></td>
                                                    </tr>
                                                        <%--
						THE "PATIENT JOINED DATE" ROW HAS NOT BEEN ADDED TWICE IN ERROR
						IT IS PLACED HERE FOR REPOSITIONING WHEN THE WAITING LIST
						MODULE IS ACTIVE.
						THIS WAY WILL MAKE EVERYONE HAPPY.
					--%>
                                                    <tr valign="top">
                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDateJoined1"/>:
                                                        </b></td>
                                                        <td align="left">
                                                            <%

                                                                String date_joined = demographic.getDateJoined() != null ? sdf.format(demographic.getDateJoined()) : null;
                                                                String dateJoinedYear = "";
                                                                String dateJoinedMonth = "";
                                                                String dateJoinedDay = "";
                                                                if (date_joined != null && date_joined.length() == 10) {
                                                                    // Format year
                                                                    decF.applyPattern("0000");
                                                                    dateJoinedYear = decF.format(MyDateFormat.getYearFromStandardDate(date_joined));
                                                                    decF.applyPattern("00");
                                                                    dateJoinedMonth = decF.format(MyDateFormat.getMonthFromStandardDate(date_joined));
                                                                    dateJoinedDay = decF.format(MyDateFormat.getDayFromStandardDate(date_joined));
                                                                }
                                                            %> <input type="text"
                                                                      name="date_joined_year" size="4" maxlength="4"
                                                                      placeholder="yyyy"
                                                                      value="<%= dateJoinedYear %>"> <input type="text"
                                                                                                            placeholder="mm"
                                                                                                            name="date_joined_month"
                                                                                                            size="2"
                                                                                                            maxlength="2"
                                                                                                            value="<%= dateJoinedMonth %>">
                                                            <input type="text" placeholder="dd"
                                                                   name="date_joined_date" size="2" maxlength="2"
                                                                   value="<%= dateJoinedDay %>"></td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEndDate"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                String endDate = null;
                                                                if (demographic.getEndDate() != null) {
                                                                    endDate = sdf.format(demographic.getEndDate());
                                                                }
                                                                String endYear = "";
                                                                String endMonth = "";
                                                                String endDay = "";

                                                                if (endDate != null) {
                                                                    // Format year
                                                                    decF.applyPattern("0000");
                                                                    endYear = decF.format(MyDateFormat.getYearFromStandardDate(endDate));
                                                                    decF.applyPattern("00");
                                                                    endMonth = decF.format(MyDateFormat.getMonthFromStandardDate(endDate));
                                                                    endDay = decF.format(MyDateFormat.getDayFromStandardDate(endDate));
                                                                }
                                                            %> <input type="text" name="end_date_year"
                                                                      placeholder="yyyy"
                                                                      size="4" maxlength="4" value="<%= endYear %>">
                                                            <input placeholder="mm"
                                                                   type="text" name="end_date_month" size="2"
                                                                   maxlength="2"
                                                                   value="<%= endMonth %>"> <input type="text"
                                                                                                   placeholder="dd"
                                                                                                   name="end_date_date"
                                                                                                   size="2"
                                                                                                   maxlength="2"
                                                                                                   value="<%= endDay %>">
                                                        </td>
                                                    </tr>
                                                        <%-- END MOVE PATIENT JOINED DATE --%>

                                                    <% // customized key + "Has Primary Care Physician" & "Employment Status"
                                                        if (hasHasPrimary || hasEmpStatus) {
                                                    %>
                                                    <tr valign="top">
                                                        <% if (hasHasPrimary) {
                                                        %>
                                                        <td style="text-align: right;">
                                                            <b><%=hasPrimary.replace(" ", "&nbsp;")%>:</b></td>
                                                        <td style="text-align: left;">
                                                            <select name="<%=hasPrimary.replace(" ", "")%>">
                                                                <option value="N/A" <%="N/A".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                                                    N/A
                                                                </option>
                                                                <option value="Yes" <%="Yes".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                                                    Yes
                                                                </option>
                                                                <option value="No" <%="No".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                                                    No
                                                                </option>
                                                            </select>
                                                        </td>
                                                        <% }
                                                            if (hasEmpStatus) {
                                                        %>
                                                        <td style="text-align: right;">
                                                            <b><%=empStatus.replace(" ", "&nbsp;")%>:</b></td>
                                                        <td style="text-align: left;">
                                                            <select name="<%=empStatus.replace(" ", "")%>">
                                                                <option value="N/A" <%="N/A".equals(employmentStatus) ? "selected" : ""%>>
                                                                    N/A
                                                                </option>
                                                                <option value="FULL TIME" <%="FULL TIME".equals(employmentStatus) ? "selected" : ""%>>
                                                                    FULL TIME
                                                                </option>
                                                                <option value="ODSP" <%="ODSP".equals(employmentStatus) ? "selected" : ""%>>
                                                                    ODSP
                                                                </option>
                                                                <option value="OW" <%="OW".equals(employmentStatus) ? "selected" : ""%>>
                                                                    OW
                                                                </option>
                                                                <option value="PART TIME" <%="PART TIME".equals(employmentStatus) ? "selected" : ""%>>
                                                                    PART TIME
                                                                </option>
                                                                <option value="UNEMPLOYED" <%="UNEMPLOYED".equals(employmentStatus) ? "selected" : ""%>>
                                                                    UNEMPLOYED
                                                                </option>
                                                            </select>
                                                        </td>
                                                    </tr>
                                                    <% }
                                                    }
                                                        if (hasDemoExt) {
                                                            boolean bExtForm = oscarProps.getProperty("demographicExtForm") != null ? true : false;
                                                            String[] propDemoExtForm = bExtForm ? (oscarProps.getProperty("demographicExtForm", "").split("\\|")) : null;
                                                            for (int k = 0; k < propDemoExt.length; k = k + 2) {
                                                    %>
                                                    <tr valign="top">
                                                        <td align="right"><b><%=Encode.forHtmlContent(propDemoExt[k])%>
                                                            : </b></td>
                                                        <td align="left">
                                                            <% if (bExtForm) {
                                                                if (propDemoExtForm[k].indexOf("<select") >= 0) {
                                                                    out.println(propDemoExtForm[k].replaceAll("value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_'))) + "\"", "value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_'))) + "\"" + " selected"));
                                                                } else {
                                                                    out.println(propDemoExtForm[k].replaceAll("value=\"\"", "value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_'))) + "\""));
                                                                }
                                                            } else { %>
                                                            <input type="text"
                                                                   name="<%= Encode.forHtmlAttribute(propDemoExt[k].replace(' ', '_')) %>"
                                                                   value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_'))))%>"/>
                                                            <% } %>
                                                            <input type="hidden"
                                                                   name="<%=propDemoExt[k].replace(' ', '_')%>Orig"
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get(propDemoExt[k].replace(' ', '_')))%>"/>
                                                        </td>
                                                        <% if ((k + 1) < propDemoExt.length) { %>
                                                        <td align="right"><b>
                                                            <%out.println(Encode.forHtmlContent(propDemoExt[k + 1]) + ":");%></b>
                                                        </td>
                                                        <td align="left">
                                                            <% if (bExtForm) {
                                                                if (propDemoExtForm[k + 1].indexOf("<select") >= 0) {
                                                                    out.println(propDemoExtForm[k + 1].replaceAll("value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k + 1].replace(' ', '_'))) + "\"", "value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k + 1].replace(' ', '_'))) + "\"" + " selected"));
                                                                } else {
                                                                    out.println(propDemoExtForm[k + 1].replaceAll("value=\"\"", "value=\"" + StringUtils.trimToEmpty(demoExt.get(propDemoExt[k + 1].replace(' ', '_'))) + "\""));
                                                                }
                                                            } else { %> <input type="text"
                                                                               name="<%=Encode.forHtmlAttribute(propDemoExt[k+1].replace(' ', '_'))%>"
                                                                               value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demoExt.get(propDemoExt[k+1].replace(' ', '_'))))%>"/>
                                                            <% } %> <input type="hidden"
                                                                           name="<%=Encode.forHtmlAttribute(propDemoExt[k+1].replace(' ', '_'))%>Orig"
                                                                           value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demoExt.get(propDemoExt[k+1].replace(' ', '_'))))%>"/>
                                                        </td>
                                                        <% } else {%>
                                                        <td>&nbsp;</td>
                                                        <td>&nbsp;</td>
                                                        <% } %>
                                                    </tr>
                                                    <% }
                                                    } %>

                                                        <%-- TOGGLE PATIENT PRIVACY CONSENT --%>
                                                    <oscar:oscarPropertiesCheck property="privateConsentEnabled"
                                                                                value="true">

                                                        <tr valign="top">
                                                            <td colspan="4">
                                                                <table id="privacyConsentTable">
                                                                    <tr id="privacyConsentHeading"
                                                                        class="category_table_heading"
                                                                        style="display:none;">
                                                                        <th class="alignLeft" colspan="4">Privacy
                                                                            Consent
                                                                        </th>
                                                                    </tr>

                                                                    <% if (showConsentsThisTime) { %>
                                                                    <tr>
                                                                        <td width="30%">
                                                                            <input type="hidden" name="usSignedOrig"
                                                                                   value="<%=StringUtils.defaultString(apptMainBean.getString(demoExt.get("usSigned")))%>"/>
                                                                            <input type="hidden"
                                                                                   name="privacyConsentOrig"
                                                                                   value="<%=privacyConsent%>"/>
                                                                            <input type="hidden"
                                                                                   name="informedConsentOrig"
                                                                                   value="<%=informedConsent%>"/>

                                                                            <input type="checkbox" name="privacyConsent"
                                                                                   id="privacyConsent"
                                                                                   value="yes" <%=privacyConsent.equals("yes") ? "checked" : ""%>>
                                                                            <label style="font-weight:bold;"
                                                                                   for="privacyConsent">Privacy Consent
                                                                                (verbal) Obtained</label>
                                                                        </td>
                                                                        <td>
                                                                            &nbsp;
                                                                        </td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td>
                                                                            <input type="checkbox"
                                                                                   name="informedConsent"
                                                                                   id="informedConsent"
                                                                                   value="yes" <%=informedConsent.equals("yes") ? "checked" : ""%>>
                                                                            <label style="font-weight:bold;"
                                                                                   for="informedConsent">Informed
                                                                                Consent (verbal) Obtained</label>
                                                                        </td>
                                                                    </tr>
                                                                    <tr>
                                                                        <td>
                                                                            <div id="usSigned">
                                                                                <input type="radio" name="usSigned"
                                                                                       id="usSignedYes"
                                                                                       value="signed" <%=usSigned.equals("signed") ? "checked" : ""%>>
                                                                                <label style="font-weight:bold;"
                                                                                       for="usSigned">U.S. Resident
                                                                                    Consent Form Signed </label>

                                                                                <input type="radio" name="usSigned"
                                                                                       id="usSignedNo"
                                                                                       value="unsigned" <%=usSigned.equals("unsigned") ? "checked" : ""%>>
                                                                                <label style="font-weight:bold;"
                                                                                       for="usSigned">U.S. Resident
                                                                                    Consent Form NOT Signed</label>
                                                                            </div>
                                                                        </td>
                                                                    </tr>
                                                                    <% } %>

                                                                        <%-- This block of code was designed to eventually manage all of the patient consents. --%>
                                                                    <oscar:oscarPropertiesCheck
                                                                            property="USE_NEW_PATIENT_CONSENT_MODULE"
                                                                            value="true">

                                                                        <c:forEach items="${ consentTypes }"
                                                                                   var="consentType" varStatus="count">
                                                                            <c:set var="patientConsent" value=""/>
                                                                            <c:forEach items="${ patientConsents }"
                                                                                       var="consent">
                                                                                <c:if test="${ consent.consentType.id eq consentType.id }">
                                                                                    <c:set var="patientConsent"
                                                                                           value="${ consent }"/>
                                                                                </c:if>
                                                                            </c:forEach>
                                                                            <tr class="privacyConsentRow"
                                                                                id="${ count.index }">
                                                                                <td class="alignRight"
                                                                                    style="width:16%;vertical-align:top;">
                                                                                    <div style="font-weight:bold;white-space:nowrap;">
                                                                                        <c:out value="${ consentType.name }"/>
                                                                                    </div>

                                                                                    <c:if test="${ not empty patientConsent and not empty patientConsent.optout }">
                                                                                        <c:choose>
                                                                                            <c:when test="${ patientConsent.optout }">
                                                                                                <div id="consentDate_${consentType.type}"
                                                                                                     style="color:red;white-space:nowrap;">
                                                                                                    Opted Out:<c:out
                                                                                                        value="${ patientConsent.optoutDate }"/>
                                                                                                </div>
                                                                                            </c:when>
                                                                                            <c:otherwise>
                                                                                                <div id="consentDate_${consentType.type}"
                                                                                                     style="color:green;white-space:nowrap;">
                                                                                                    Consented:<c:out
                                                                                                        value="${ patientConsent.consentDate }"/>
                                                                                                </div>
                                                                                            </c:otherwise>
                                                                                        </c:choose>
                                                                                    </c:if>
                                                                                </td>

                                                                                <td colspan="2"
                                                                                    style="padding-left:10px;vertical-align:top;">
                                                                                    <c:out value="${ consentType.description }"/>
                                                                                </td>

                                                                                <td id="consentStatusDate"
                                                                                    style="width:31%;vertical-align:top;">
                                                                                    <input type="radio"
                                                                                           name="${ consentType.type }"
                                                                                           id="optin_${ consentType.type }"
                                                                                           value="0"
                                                                                            <c:if test="${ not empty patientConsent and not empty patientConsent.optout and not patientConsent.optout }">
                                                                                                <c:out value="checked"/>
                                                                                            </c:if>
                                                                                    />
                                                                                    <label for="optin_${ consentType.type }">Opt-In</label>
                                                                                    <input type="radio"
                                                                                           name="${ consentType.type }"
                                                                                           id="optout_${ consentType.type }"
                                                                                           value="1"
                                                                                            <c:if test="${ not empty patientConsent and not empty patientConsent.optout and patientConsent.optout }">
                                                                                                <c:out value="checked"/>
                                                                                            </c:if>
                                                                                    />
                                                                                    <label for="optout_${ consentType.type }">Opt-Out</label>
                                                                                    <input type="button"
                                                                                           name="clearRadio_${consentType.type}_btn"
                                                                                           onclick="consentClearBtn('${consentType.type}')"
                                                                                           value="Clear"/>

                                                                                        <%-- Was this consent set by the user? Or by the database?  --%>
                                                                                    <input type="hidden"
                                                                                           name="consentPreset_${consentType.type}"
                                                                                           id="consentPreset_${consentType.type}"
                                                                                           value="${ not empty patientConsent }"/>

                                                                                        <%-- This consent will be labeled for delete when the clear button is clicked. --%>
                                                                                    <input type="hidden"
                                                                                           name="deleteConsent_${consentType.type}"
                                                                                           id="deleteConsent_${consentType.type}"
                                                                                           value="0"/>

                                                                                </td>

                                                                            </tr>
                                                                        </c:forEach>

                                                                    </oscar:oscarPropertiesCheck>

                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </oscar:oscarPropertiesCheck>

                                                        <%-- END TOGGLE OFF PATIENT PRIVACY CONSENT --%>

                                                        <%-- TOGGLE OFF MEDITECH MODULE --%>
                                                    <% if (oscarProps.isPropertyActive("meditech_id")) { %>
                                                    <tr>
                                                        <td align="right"><b>Meditech ID: </b></td>
                                                        <td align="left"><input type="text" name="meditech_id"
                                                                                size="30"
                                                                                value="<%=OtherIdManager.getDemoOtherId(
									demographic_no, "meditech_id")%>">
                                                            <input type="hidden" name="meditech_idOrig"
                                                                   value="<%=OtherIdManager.getDemoOtherId(
									demographic_no, "meditech_id")%>">
                                                        </td>
                                                        <td><!-- padding --></td>
                                                        <td><!-- padding --></td>
                                                    </tr>
                                                    <%
                                                        }
                                                    %>
                                                        <%-- END TOGGLE OFF MEDITECH MODULE --%>

                                                        <%-- TOGGLE OFF EXTRA DEMO FIELDS (NATIVE HEALTH) --%>
                                                    <%
                                                        if (oscarProps.getProperty("EXTRA_DEMO_FIELDS") != null) {
                                                            String fieldJSP = "/demographic/" + oscarProps
                                                                    .getProperty("EXTRA_DEMO_FIELDS");
                                                            fieldJSP += ".jsp";
                                                    %>
                                                    <tr>
                                                        <td colspan="4">
                                                            <jsp:include page="<%=fieldJSP%>">
                                                                <jsp:param name="demo" value="<%= demographic_no %>"/>
                                                            </jsp:include>
                                                        </td>
                                                    </tr>
                                                    <%}%>

                                                        <%-- END TOGGLE OFF EXTRA DEMO FIELDS (NATIVE HEALTH) --%>

                                                        <%-- WAITING LIST MODULE --%>
                                                    <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_WAITING_LIST"
                                                                                value="true">


                                                        <tr valign="top">
                                                            <td colspan="4">
                                                                <table border="0" cellspacing="0" cellpadding="0"
                                                                       width="100%" id="waitingListTable">

                                                                    <tr id="waitingListHeading"
                                                                        class="category_table_heading">
                                                                        <th colspan="4" class="alignLeft">Waiting List
                                                                        </th>
                                                                    </tr>
                                                                    <tr>
                                                                        <td align="right" nowrap><b>
                                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgWaitList"/>:</b>
                                                                        </td>
                                                                        <td align="left">
                                                                            <%

                                                                                List<io.github.carlos_emr.carlos.commn.model.WaitingList> wls = waitingListDao.search_wlstatus(Integer.parseInt(demographic_no));

                                                                                String wlId = "", listID = "", wlnote = "";
                                                                                String wlReferralDate = "";
                                                                                if (wls.size() > 0) {
                                                                                    io.github.carlos_emr.carlos.commn.model.WaitingList wl = wls.get(0);
                                                                                    wlId = wl.getId().toString();
                                                                                    listID = String.valueOf(wl.getListId());
                                                                                    wlnote = wl.getNote();
                                                                                    wlReferralDate = ConversionUtils.toDateString(wl.getOnListSince());
                                                                                    if (wlReferralDate != null && wlReferralDate.length() > 10) {
                                                                                        wlReferralDate = wlReferralDate.substring(0, 11);
                                                                                    }
                                                                                }

                                                                            %> <input type="hidden" name="wlId"
                                                                                      value="<%=wlId%>"> <select
                                                                                name="list_id">
                                                                            <%if ("".equals(wLReadonly)) {%>
                                                                            <option value="0"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optSelectWaitList"/></option>
                                                                            <%} else {%>
                                                                            <option value="0">
                                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optCreateWaitList"/></option>
                                                                            <%} %>
                                                                            <%

                                                                                List<WaitingListName> wlns = waitingListNameDao.findCurrentByGroup(((ProviderPreference) session.getAttribute(io.github.carlos_emr.carlos.utility.SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE)).getMyGroupNo());
                                                                                for (WaitingListName wln : wlns) {
                                                                            %>
                                                                            <option value="<%=wln.getId()%>"
                                                                                    <%=wln.getId().toString().equals(listID) ? " selected" : ""%>>
                                                                                <%=wln.getName()%>
                                                                            </option>
                                                                            <%
                                                                                }

                                                                            %>
                                                                        </select></td>
                                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgWaitListNote"/>: </b>
                                                                        </td>
                                                                        <td align="left"><input type="text"
                                                                                                name="waiting_list_note"
                                                                                                value="<%=wlnote%>"
                                                                                <%=wLReadonly%>></td>
                                                                    </tr>
                                                                    <tr>

                                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDateOfReq"/>: </b>
                                                                        </td>
                                                                        <td align="left"><input type="text"
                                                                                                placeholder="yyyy-mm-dd"
                                                                                                name="waiting_list_referral_date"
                                                                                                id="waiting_list_referral_date"
                                                                                                size="11"
                                                                                                value="<%=wlReferralDate%>" <%=wLReadonly%>><img
                                                                                src="<%= request.getContextPath() %>/images/cal.gif"
                                                                                id="referral_date_cal">
                                                                        </td>
                                                                        <td><!-- padding --></td>
                                                                        <td><!-- padding --></td>
                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>
                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END WAITING LIST MODULE --%>


                                                        <%-- AUTHOR DENNIS WARREN O/A COLCAMEX RESOURCES --%>
                                                    <oscar:oscarPropertiesCheck
                                                            property="DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM"
                                                            value="true">
                                                        <tr>
                                                            <td colspan="4">
                                                                <jsp:include page="/demographic/manageHealthCareTeam.jsp">
                                                                    <jsp:param name="demographicNo"
                                                                               value="<%= Encode.forHtmlAttribute(demographic_no) %>"/>
                                                                </jsp:include>
                                                            </td>
                                                        </tr>
                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END AUTHOR DENNIS WARREN O/A COLCAMEX RESOURCES --%>

                                                        <%-- TOGGLED OFF PROGRAM ADMISSIONS --%>
                                                    <oscar:oscarPropertiesCheck
                                                            property="DEMOGRAPHIC_PROGRAM_ADMISSIONS" value="true">

                                                        <tr>
                                                            <td colspan="4">
                                                                <table style="width:100%;">
                                                                    <tr class="category_table_heading">
                                                                        <th colspan="4" class="alignLeft">Program
                                                                            Admissions
                                                                        </th>
                                                                    </tr>
                                                                    <tr>
                                                                        <td><b>Residential Status:</b></td>
                                                                        <td>
                                                                            <select id="rsid" name="rps">
                                                                                <option value=""></option>
                                                                                <%
                                                                                    String _pvid = loggedInInfo.getLoggedInProviderNo();
                                                                                    ProgramManager tempPm = SpringUtils.getBean(ProgramManager.class);
                                                                                    
                                                                                    // Get providers's programs for permission checking - recreating getActiveProviderPrograms logic
                                                                                    Set<Program> pset = new HashSet<Program>();
                                                                                    for (Program providerProgram : tempPm.getProgramDomain(_pvid)) {
                                                                                        if (providerProgram != null && providerProgram.isActive()) {
                                                                                            pset.add(providerProgram);
                                                                                        }
                                                                                    }
                                                                                    
                                                                                    Program[] bedP = new Program[0];
                                                                                    Program oscarp = programDao.getProgramByName("OSCAR");


                                                                                    for (Program _p : bedP) {
                                                                                %>
                                                                                <option value="<%=_p.getId()%>" <%=isProgramSelected(bedAdmission, _p.getId()) %>><%=_p.getName()%>
                                                                                </option>
                                                                                <%
                                                                                    }

                                                                                %>
                                                                            </select>

                                                                        </td>

                                                                        <td><b>Service Programs</b></td>

                                                                        <td>
                                                                            <ul>
                                                                                <%
                                                                                    ProgramManager programManager = SpringUtils.getBean(ProgramManager.class);
                                                                                    List<Program> servP = programManager.getServicePrograms();

                                                                                    for (Program _p : servP) {
                                                                                        boolean readOnly = false;
                                                                                        if (!pset.contains(_p)) {
                                                                                            readOnly = true;
                                                                                        }
                                                                                        String selected = isProgramSelected(serviceAdmissions, _p.getId());

                                                                                        if (readOnly && selected.length() == 0) {
                                                                                            continue;
                                                                                        }

                                                                                %>
                                                                                <li>
                                                                                    <input type="checkbox" name="sp"
                                                                                           id="sp"
                                                                                           value="<%=_p.getId()%>" <%=selected %> <%=(readOnly) ? " disabled=\"disabled\" " : "" %> />
                                                                                    <label for="sp"><%=_p.getName()%>
                                                                                    </label>
                                                                                </li>
                                                                                <%}%>
                                                                            </ul>
                                                                        </td>

                                                                    </tr>
                                                                </table>
                                                            </td>
                                                        </tr>

                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END TOGGLE OFF PROGRAM ADMISSIONS --%>

                                                    <tr valign="top">
                                                        <td colspan="4">
                                                            <table id="rxinteractionTable" style="width:100%;">
                                                                <tr class="category_table_heading">
                                                                    <th class="alignLeft" colspan="4">
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.rxInteractionWarningLevel"/></th>
                                                                </tr>
                                                                <tr>
                                                                    <td class="alignRight">
                                                                        <strong>Level</strong>
                                                                    </td>
                                                                    <td>
                                                                        <input type="hidden"
                                                                               name="rxInteractionWarningLevelOrig"
                                                                               value="<%=StringUtils.trimToEmpty(demoExt.get("rxInteractionWarningLevel"))%>"/>
                                                                        <select id="rxInteractionWarningLevel"
                                                                                name="rxInteractionWarningLevel">
                                                                            <option value="0" <%=(warningLevel.equals("0") ? "selected=\"selected\"" : "") %>>
                                                                                Not Specified
                                                                            </option>
                                                                            <option value="1" <%=(warningLevel.equals("1") ? "selected=\"selected\"" : "") %>>
                                                                                Low
                                                                            </option>
                                                                            <option value="2" <%=(warningLevel.equals("2") ? "selected=\"selected\"" : "") %>>
                                                                                Medium
                                                                            </option>
                                                                            <option value="3" <%=(warningLevel.equals("3") ? "selected=\"selected\"" : "") %>>
                                                                                High
                                                                            </option>
                                                                            <option value="4" <%=(warningLevel.equals("4") ? "selected=\"selected\"" : "") %>>
                                                                                None
                                                                            </option>
                                                                        </select>
                                                                    </td>

                                                                    <td><!-- padding --></td>
                                                                    <td><!-- padding --></td>
                                                                </tr>
                                                            </table>
                                                        </td>
                                                    </tr>
                                                        <%-- PATIENT NOTES MODULE --%>
                                                    <tr>
                                                        <td nowrap colspan="4">
                                                            <table width="100%"
                                                                   id="demographicPatientNotes">
                                                                <tr id="paitientNotesHeading"
                                                                    class="category_table_heading">
                                                                    <th colspan="4" class="alignLeft">Patient Notes</th>
                                                                </tr>

                                                                <tr>
                                                                    <td width="7%" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAlert"/>: </b>
                                                                    </td>
                                                                    <td><textarea name="alert" style="width: 100%"
                                                                                  rows="8"><%=alert%></textarea></td>

                                                                    <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNotes"/>: </b>
                                                                    </td>
                                                                    <td><textarea name="notes" style="width: 100%"
                                                                                  rows="8"><%=notes%></textarea>
                                                                    </td>
                                                                </tr>
                                                            </table>
                                                        </td>
                                                    </tr>

                                                </table>

                                            </td>
                                        </tr>
                                            <%-- END PATIENT NOTES MODULE --%>
                                            <%-- BOTTOM TOOLBAR  --%>
                                        <tr class="darkPurple">
                                            <td>
                                                <input type="hidden" name="dboperation" value="update_record">
                                                <input type="hidden" name="displaymode" value="Update Record">

                                                <%-- Row 1: Primary actions --%>
                                                <div class="toolbar-row toolbar-primary">
                                                    <div class="toolbar-left">
                                                        <span id="updateButton" style="display: none;">
                                                            <security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w">
                                                                <%
                                                                    boolean showCbiReminder = oscarProps.getBooleanProperty("CBI_REMIND_ON_UPDATE_DEMOGRAPHIC", "true");
                                                                %>
                                                                <input type="submit" class="btn-toolbar-update" <%=(showCbiReminder?"onclick='return showCbiReminder()'":"")%>
                                                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnUpdate"/>">
                                                            </security:oscarSec>
                                                        </span>
                                                        <security:oscarSec roleName="<%=roleName$%>" objectName="_demographicExport" rights="r" reverse="<%=false%>">
                                                            <input type="button" class="btn-toolbar-secondary"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgExport"/>"
                                                                   onclick="window.open('<%= request.getContextPath() %>/demographic/demographicExport.jsp?demographicNo=<%=demographic.getDemographicNo()%>');"/>
                                                        </security:oscarSec>
                                                        <input type="button" class="btn-toolbar-secondary"
                                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnAuditInfo"/>"
                                                               onclick="window.open('<%= Encode.forJavaScriptAttribute(request.getContextPath()) %>/demographic/demographicAudit.jsp?demographic_no=<%= Encode.forJavaScriptAttribute(Encode.forUriComponent(demographic.getDemographicNo().toString())) %>');"/>
                                                    </div>
                                                    <div class="toolbar-right">
                                                        <span id="swipeButtonBottom" style="display: none;">
                                                            <input type="button" name="Button" class="btn-toolbar-secondary"
                                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSwipeCard"/>"
                                                                   onclick="window.open('demographic/zdemographicswipe.jsp','', 'scrollbars=yes,resizable=yes,width=600,height=300, top=360, left=0')">
                                                        </span>
                                                        <div class="dropdown">
                                                            <button class="btn-toolbar-secondary dropdown-toggle" type="button" data-bs-toggle="dropdown" aria-expanded="false">
                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.printAndLabels"/>
                                                            </button>
                                                            <ul class="dropdown-menu dropdown-menu-end">
                                                                <li><a class="dropdown-item" href="#" onclick="popupPage(400,700,'<%=printEnvelope%><%=demographic.getDemographicNo()%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFEnvelope"/></a></li>
                                                                <li><a class="dropdown-item" href="#" onclick="popupPage(400,700,'<%=printLbl%><%=demographic.getDemographicNo()%>&appointment_no=<%=Encode.forUriComponent(appointment != null ? appointment : "")%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFLabel"/></a></li>
                                                                <li><a class="dropdown-item" href="#" onclick="popupPage(400,700,'<%=printAddressLbl%><%=demographic.getDemographicNo()%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFAddressLabel"/></a></li>
                                                                <li><a class="dropdown-item" href="#" onclick="popupPage(400,700,'<%=printChartLbl%><%=demographic.getDemographicNo()%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePDFChartLabel"/></a></li>
                                                                <% if (oscarProps.getProperty("showSexualHealthLabel", "false").equals("true")) { %>
                                                                <li><a class="dropdown-item" href="#" onclick="popupPage(400,700,'<%=printSexHealthLbl%><%=demographic.getDemographicNo()%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnCreatePublicHealthLabel"/></a></li>
                                                                <% } %>
                                                                <li><a class="dropdown-item" href="#" onclick="popupPage(600,800,'<%=printHtmlLbl%><%=demographic.getDemographicNo()%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnPrintLabel"/></a></li>
                                                                <li><a class="dropdown-item" href="#" onclick="popupPage(400,700,'<%=printLabLbl%><%=demographic.getDemographicNo()%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnClientLabLabel"/></a></li>
                                                            </ul>
                                                        </div>
                                                        <input type="button" name="Button" id="cancelButton" class="btn-toolbar-back"
                                                               value="<fmt:setBundle basename="oscarResources"/><fmt:message key="global.btnBack"/>" onclick="self.close();">
                                                    </div>
                                                </div>
                                                </table>
                                                    <%-- END BOTTOM TOOLBAR  --%>

                                            </td>
                                        </tr>
                                    </table>

                                </form>
