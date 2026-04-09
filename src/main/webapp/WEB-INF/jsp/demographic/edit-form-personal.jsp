<%-- edit-form-personal.jsp: Edit form personal info, address, HIN (extracted from demographiceditdemographic.jsp lines 2610-3855) --%>
<%@ page import="java.util.*" %>
<%@ page import="java.net.*" %>
<%@ page import="java.text.DecimalFormat" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.apache.commons.text.StringEscapeUtils" %>
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
    String annotation_display = (String) request.getAttribute("annotation_display");
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
    // Build the single DOB display value; treat "0000" year or "00" month/day as blank
    boolean hasDob = birthYear != null && !"0000".equals(birthYear)
            && birthMonth != null && !"00".equals(birthMonth)
            && birthDate != null && !"00".equals(birthDate);
    String dobDisplay = hasDob ? (birthYear + "-" + birthMonth + "-" + birthDate) : "";
    DemographicCust demographicCust = (DemographicCust) request.getAttribute("demographicCust");
    
    // providerBean is session-scoped, populated during login
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<jsp:useBean id="apptMainBean" class="io.github.carlos_emr.AppointmentMainBean" scope="session"/>

<%-- === Original content begins here === --%>
                                                <table width="100%" bgcolor="#EEEEFF" border=0 id="editDemographic"
                                                       style="display: none;">
                                                    <tr>
                                                        <td align="right"
                                                            title='<%=demographic.getDemographicNo()%>'>
                                                            <b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formLastName"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text"
                                                                                name="last_name" <%=getDisabled("last_name")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(demographic.getLastName())%>"
                                                                                onBlur="upCaseCtrl(this)"></td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formFirstName"/>:
                                                        </b></td>
                                                        <td align="left"><input type="text"
                                                                                name="first_name" <%=getDisabled("first_name")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(demographic.getFirstName())%>"
                                                                                onBlur="upCaseCtrl(this)"></td>
                                                    </tr>
                                                    <tr>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMiddleNames"/>:
                                                        </b></td>
                                                        <td align="left"><input type="text"
                                                                                name="middleNames" <%=getDisabled("middleNames")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(demographic.getMiddleNames())%>"
                                                                                onBlur="upCaseCtrl(this)"></td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoTitle"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                String title = demographic.getTitle();
                                                                if (title == null) {
                                                                    title = "";
                                                                }
                                                            %>
                                                            <select name="title" <%=getDisabled("title")%>>
                                                                <option value="" <%=title.equals("") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgNotSet"/></option>
                                                                <option value="DR" <%=title.equalsIgnoreCase("DR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.msgDr"/></option>
                                                                <option value="MS" <%=title.equalsIgnoreCase("MS") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMs"/></option>
                                                                <option value="MISS" <%=title.equalsIgnoreCase("MISS") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMiss"/></option>
                                                                <option value="MRS" <%=title.equalsIgnoreCase("MRS") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMrs"/></option>
                                                                <option value="MR" <%=title.equalsIgnoreCase("MR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMr"/></option>
                                                                <option value="MSSR" <%=title.equalsIgnoreCase("MSSR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMssr"/></option>
                                                                <option value="PROF" <%=title.equalsIgnoreCase("PROF") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgProf"/></option>
                                                                <option value="REEVE" <%=title.equalsIgnoreCase("REEVE") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgReeve"/></option>
                                                                <option value="REV" <%=title.equalsIgnoreCase("REV") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgRev"/></option>
                                                                <option value="RT_HON" <%=title.equalsIgnoreCase("RT_HON") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgRtHon"/></option>
                                                                <option value="SEN" <%=title.equalsIgnoreCase("SEN") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSen"/></option>
                                                                <option value="SGT" <%=title.equalsIgnoreCase("SGT") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSgt"/></option>
                                                                <option value="SR" <%=title.equalsIgnoreCase("SR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSr"/></option>

                                                                <option value="MADAM" <%=title.equalsIgnoreCase("MADAM") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMadam"/></option>
                                                                <option value="MME" <%=title.equalsIgnoreCase("MME") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMme"/></option>
                                                                <option value="MLLE" <%=title.equalsIgnoreCase("MLLE") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMlle"/></option>
                                                                <option value="MAJOR" <%=title.equalsIgnoreCase("MAJOR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMajor"/></option>
                                                                <option value="MAYOR" <%=title.equalsIgnoreCase("MAYOR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgMayor"/></option>

                                                                <option value="BRO" <%=title.equalsIgnoreCase("BRO") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgBro"/></option>
                                                                <option value="CAPT" <%=title.equalsIgnoreCase("CAPT") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCapt"/></option>
                                                                <option value="CHIEF" <%=title.equalsIgnoreCase("CHIEF") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgChief"/></option>
                                                                <option value="CST" <%=title.equalsIgnoreCase("CST") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCst"/></option>
                                                                <option value="CORP" <%=title.equalsIgnoreCase("CORP") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCorp"/></option>
                                                                <option value="FR" <%=title.equalsIgnoreCase("FR") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgFr"/></option>
                                                                <option value="HON" <%=title.equalsIgnoreCase("HON") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgHon"/></option>
                                                                <option value="LT" <%=title.equalsIgnoreCase("LT") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgLt"/></option>

                                                            </select>
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNameUsed"/>:
                                                        </b></td>
                                                        <td align="left"><input type="text"
                                                                                name="nameUsed" <%=getDisabled("nameUsed")%>
                                                                                size="30"
                                                                                value="<%= Encode.forHtmlAttribute(demographic.getAlias()) %>"
                                                                                onBlur="upCaseCtrl(this)"></td>
                                                        <td style="text-align: right;">
                                                            <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPronouns"/></strong>
                                                        </td>
                                                        <td style="text-align: left;">
                                                            <input type="text" id="patientPronouns" name="pronouns"
                                                                   value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demographic.getPronoun()))%>"/>
                                                        </td>
                                                    </tr>
                                                    <tr>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoLanguage"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                String lang = io.github.carlos_emr.carlos.util.StringUtils.noNull(demographic.getOfficialLanguage()); %>
                                                            <select name="official_lang" <%=getDisabled("official_lang")%>>
                                                                <option value="English" <%=lang.equals("English") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgEnglish"/></option>
                                                                <option value="French" <%=lang.equals("French") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgFrench"/></option>
                                                                <option value="Other" <%=lang.equals("Other") ? "selected" : ""%> >
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optOther"/></option>
                                                            </select>
                                                        </td>
                                                        <td align="right">
                                                            <b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSpoken"/>: </b>
                                                        </td>
                                                        <td>
                                                            <%
                                                                String spokenLang = io.github.carlos_emr.carlos.util.StringUtils.noNull(demographic.getSpokenLanguage()); %>
                                                            <select name="spoken_lang"
                                                                    style="width: 200px;" <%=getDisabled("spoken_lang")%>>
                                                                <%for (String splang : Util.spokenLangProperties.getLangSorted()) { %>
                                                                <option value="<%=splang %>" <%=spokenLang.equals(splang) ? "selected" : "" %>><%=splang %>
                                                                </option>
                                                                <%} %>
                                                            </select>
                                                        </td>

                                                    </tr>

                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAddr"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text"
                                                                                name="address" <%=getDisabled("address")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getAddress()))%>">
                                                        </td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formCity"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text" name="city"
                                                                                size="30" <%=getDisabled("city")%>
                                                                                value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getCity()))%>">
                                                        </td>
                                                    </tr>

                                                    <tr valign="top">
                                                        <td align="right">
                                                            <b><% if (oscarProps.getProperty("demographicLabelProvince") == null) { %>
                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formProcvince"/> <% } else {
                                                                    out.print(oscarProps.getProperty("demographicLabelProvince"));
                                                                } %> : </b></td>
                                                        <td align="left">
                                                            <% String province = demographic.getProvince(); %>
                                                            <%
                                                                if ("true".equals(CarlosProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                                            %>
                                                            <select name="province" id="province"></select>
                                                            <br/>
                                                            Filter by Country: <select name="country"
                                                                                       id="country"></select>

                                                            <% } else { %>
                                                            <select name="province"
                                                                    style="width: 200px" <%=getDisabled("province")%>>
                                                                <option value="OT"
                                                                        <%=(province == null || province.equals("OT") || province.equals("") || province.length() > 2) ? " selected" : ""%>>
                                                                    Other
                                                                </option>
                                                                <% if (pNames.isDefined()) {
                                                                    for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                                                        String pr2 = (String) li.next(); %>
                                                                <option value="<%=pr2%>"
                                                                        <%=pr2.equals(province) ? " selected" : ""%>><%=li.next()%>
                                                                </option>
                                                                <% }//for %>
                                                                <% } else { %>
                                                                <option value="AB" <%="AB".equals(province) ? " selected" : ""%>>
                                                                    AB-Alberta
                                                                </option>
                                                                <option value="BC" <%="BC".equals(province) ? " selected" : ""%>>
                                                                    BC-British Columbia
                                                                </option>
                                                                <option value="MB" <%="MB".equals(province) ? " selected" : ""%>>
                                                                    MB-Manitoba
                                                                </option>
                                                                <option value="NB" <%="NB".equals(province) ? " selected" : ""%>>
                                                                    NB-New Brunswick
                                                                </option>
                                                                <option value="NL" <%="NL".equals(province) ? " selected" : ""%>>
                                                                    NL-Newfoundland Labrador
                                                                </option>
                                                                <option value="NT" <%="NT".equals(province) ? " selected" : ""%>>
                                                                    NT-Northwest Territory
                                                                </option>
                                                                <option value="NS" <%="NS".equals(province) ? " selected" : ""%>>
                                                                    NS-Nova Scotia
                                                                </option>
                                                                <option value="NU" <%="NU".equals(province) ? " selected" : ""%>>
                                                                    NU-Nunavut
                                                                </option>
                                                                <option value="ON" <%="ON".equals(province) ? " selected" : ""%>>
                                                                    ON-Ontario
                                                                </option>
                                                                <option value="PE" <%="PE".equals(province) ? " selected" : ""%>>
                                                                    PE-Prince Edward Island
                                                                </option>
                                                                <option value="QC" <%="QC".equals(province) ? " selected" : ""%>>
                                                                    QC-Quebec
                                                                </option>
                                                                <option value="SK" <%="SK".equals(province) ? " selected" : ""%>>
                                                                    SK-Saskatchewan
                                                                </option>
                                                                <option value="YT" <%="YT".equals(province) ? " selected" : ""%>>
                                                                    YT-Yukon
                                                                </option>
                                                                <option value="US" <%="US".equals(province) ? " selected" : ""%>>
                                                                    US resident
                                                                </option>
                                                                <option value="US-AK" <%="US-AK".equals(province) ? " selected" : ""%>>
                                                                    US-AK-Alaska
                                                                </option>
                                                                <option value="US-AL" <%="US-AL".equals(province) ? " selected" : ""%>>
                                                                    US-AL-Alabama
                                                                </option>
                                                                <option value="US-AR" <%="US-AR".equals(province) ? " selected" : ""%>>
                                                                    US-AR-Arkansas
                                                                </option>
                                                                <option value="US-AZ" <%="US-AZ".equals(province) ? " selected" : ""%>>
                                                                    US-AZ-Arizona
                                                                </option>
                                                                <option value="US-CA" <%="US-CA".equals(province) ? " selected" : ""%>>
                                                                    US-CA-California
                                                                </option>
                                                                <option value="US-CO" <%="US-CO".equals(province) ? " selected" : ""%>>
                                                                    US-CO-Colorado
                                                                </option>
                                                                <option value="US-CT" <%="US-CT".equals(province) ? " selected" : ""%>>
                                                                    US-CT-Connecticut
                                                                </option>
                                                                <option value="US-CZ" <%="US-CZ".equals(province) ? " selected" : ""%>>
                                                                    US-CZ-Canal Zone
                                                                </option>
                                                                <option value="US-DC" <%="US-DC".equals(province) ? " selected" : ""%>>
                                                                    US-DC-District Of Columbia
                                                                </option>
                                                                <option value="US-DE" <%="US-DE".equals(province) ? " selected" : ""%>>
                                                                    US-DE-Delaware
                                                                </option>
                                                                <option value="US-FL" <%="US-FL".equals(province) ? " selected" : ""%>>
                                                                    US-FL-Florida
                                                                </option>
                                                                <option value="US-GA" <%="US-GA".equals(province) ? " selected" : ""%>>
                                                                    US-GA-Georgia
                                                                </option>
                                                                <option value="US-GU" <%="US-GU".equals(province) ? " selected" : ""%>>
                                                                    US-GU-Guam
                                                                </option>
                                                                <option value="US-HI" <%="US-HI".equals(province) ? " selected" : ""%>>
                                                                    US-HI-Hawaii
                                                                </option>
                                                                <option value="US-IA" <%="US-IA".equals(province) ? " selected" : ""%>>
                                                                    US-IA-Iowa
                                                                </option>
                                                                <option value="US-ID" <%="US-ID".equals(province) ? " selected" : ""%>>
                                                                    US-ID-Idaho
                                                                </option>
                                                                <option value="US-IL" <%="US-IL".equals(province) ? " selected" : ""%>>
                                                                    US-IL-Illinois
                                                                </option>
                                                                <option value="US-IN" <%="US-IN".equals(province) ? " selected" : ""%>>
                                                                    US-IN-Indiana
                                                                </option>
                                                                <option value="US-KS" <%="US-KS".equals(province) ? " selected" : ""%>>
                                                                    US-KS-Kansas
                                                                </option>
                                                                <option value="US-KY" <%="US-KY".equals(province) ? " selected" : ""%>>
                                                                    US-KY-Kentucky
                                                                </option>
                                                                <option value="US-LA" <%="US-LA".equals(province) ? " selected" : ""%>>
                                                                    US-LA-Louisiana
                                                                </option>
                                                                <option value="US-MA" <%="US-MA".equals(province) ? " selected" : ""%>>
                                                                    US-MA-Massachusetts
                                                                </option>
                                                                <option value="US-MD" <%="US-MD".equals(province) ? " selected" : ""%>>
                                                                    US-MD-Maryland
                                                                </option>
                                                                <option value="US-ME" <%="US-ME".equals(province) ? " selected" : ""%>>
                                                                    US-ME-Maine
                                                                </option>
                                                                <option value="US-MI" <%="US-MI".equals(province) ? " selected" : ""%>>
                                                                    US-MI-Michigan
                                                                </option>
                                                                <option value="US-MN" <%="US-MN".equals(province) ? " selected" : ""%>>
                                                                    US-MN-Minnesota
                                                                </option>
                                                                <option value="US-MO" <%="US-MO".equals(province) ? " selected" : ""%>>
                                                                    US-MO-Missouri
                                                                </option>
                                                                <option value="US-MS" <%="US-MS".equals(province) ? " selected" : ""%>>
                                                                    US-MS-Mississippi
                                                                </option>
                                                                <option value="US-MT" <%="US-MT".equals(province) ? " selected" : ""%>>
                                                                    US-MT-Montana
                                                                </option>
                                                                <option value="US-NC" <%="US-NC".equals(province) ? " selected" : ""%>>
                                                                    US-NC-North Carolina
                                                                </option>
                                                                <option value="US-ND" <%="US-ND".equals(province) ? " selected" : ""%>>
                                                                    US-ND-North Dakota
                                                                </option>
                                                                <option value="US-NE" <%="US-NE".equals(province) ? " selected" : ""%>>
                                                                    US-NE-Nebraska
                                                                </option>
                                                                <option value="US-NH" <%="US-NH".equals(province) ? " selected" : ""%>>
                                                                    US-NH-New Hampshire
                                                                </option>
                                                                <option value="US-NJ" <%="US-NJ".equals(province) ? " selected" : ""%>>
                                                                    US-NJ-New Jersey
                                                                </option>
                                                                <option value="US-NM" <%="US-NM".equals(province) ? " selected" : ""%>>
                                                                    US-NM-New Mexico
                                                                </option>
                                                                <option value="US-NU" <%="US-NU".equals(province) ? " selected" : ""%>>
                                                                    US-NU-Nunavut
                                                                </option>
                                                                <option value="US-NV" <%="US-NV".equals(province) ? " selected" : ""%>>
                                                                    US-NV-Nevada
                                                                </option>
                                                                <option value="US-NY" <%="US-NY".equals(province) ? " selected" : ""%>>
                                                                    US-NY-New York
                                                                </option>
                                                                <option value="US-OH" <%="US-OH".equals(province) ? " selected" : ""%>>
                                                                    US-OH-Ohio
                                                                </option>
                                                                <option value="US-OK" <%="US-OK".equals(province) ? " selected" : ""%>>
                                                                    US-OK-Oklahoma
                                                                </option>
                                                                <option value="US-OR" <%="US-OR".equals(province) ? " selected" : ""%>>
                                                                    US-OR-Oregon
                                                                </option>
                                                                <option value="US-PA" <%="US-PA".equals(province) ? " selected" : ""%>>
                                                                    US-PA-Pennsylvania
                                                                </option>
                                                                <option value="US-PR" <%="US-PR".equals(province) ? " selected" : ""%>>
                                                                    US-PR-Puerto Rico
                                                                </option>
                                                                <option value="US-RI" <%="US-RI".equals(province) ? " selected" : ""%>>
                                                                    US-RI-Rhode Island
                                                                </option>
                                                                <option value="US-SC" <%="US-SC".equals(province) ? " selected" : ""%>>
                                                                    US-SC-South Carolina
                                                                </option>
                                                                <option value="US-SD" <%="US-SD".equals(province) ? " selected" : ""%>>
                                                                    US-SD-South Dakota
                                                                </option>
                                                                <option value="US-TN" <%="US-TN".equals(province) ? " selected" : ""%>>
                                                                    US-TN-Tennessee
                                                                </option>
                                                                <option value="US-TX" <%="US-TX".equals(province) ? " selected" : ""%>>
                                                                    US-TX-Texas
                                                                </option>
                                                                <option value="US-UT" <%="US-UT".equals(province) ? " selected" : ""%>>
                                                                    US-UT-Utah
                                                                </option>
                                                                <option value="US-VA" <%="US-VA".equals(province) ? " selected" : ""%>>
                                                                    US-VA-Virginia
                                                                </option>
                                                                <option value="US-VI" <%="US-VI".equals(province) ? " selected" : ""%>>
                                                                    US-VI-Virgin Islands
                                                                </option>
                                                                <option value="US-VT" <%="US-VT".equals(province) ? " selected" : ""%>>
                                                                    US-VT-Vermont
                                                                </option>
                                                                <option value="US-WA" <%="US-WA".equals(province) ? " selected" : ""%>>
                                                                    US-WA-Washington
                                                                </option>
                                                                <option value="US-WI" <%="US-WI".equals(province) ? " selected" : ""%>>
                                                                    US-WI-Wisconsin
                                                                </option>
                                                                <option value="US-WV" <%="US-WV".equals(province) ? " selected" : ""%>>
                                                                    US-WV-West Virginia
                                                                </option>
                                                                <option value="US-WY" <%="US-WY".equals(province) ? " selected" : ""%>>
                                                                    US-WY-Wyoming
                                                                </option>
                                                                <% } %>
                                                            </select>

                                                            <% } %>
                                                        </td>
                                                        <td align="right">
                                                            <b><% if (oscarProps.getProperty("demographicLabelPostal") == null) { %>
                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPostal"/> <% } else {
                                                                    out.print(oscarProps.getProperty("demographicLabelPostal"));
                                                                } %> : </b></td>
                                                        <td align="left"><input type="text" name="postal"
                                                                                size="30" <%=getDisabled("postal")%>
                                                                                value="<%=StringUtils.trimToEmpty(demographic.getPostal())%>"
                                                                                onBlur="upCaseCtrl(this)"
                                                                                onChange="isPostalCode()"></td>
                                                    </tr>


                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialAddr"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text"
                                                                                name="residentialAddress" <%=getDisabled("residentialAddress")%>
                                                                                size="30"
                                                                                value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getResidentialAddress()))%>">
                                                        </td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialCity"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text" name="residentialCity"
                                                                                size="30" <%=getDisabled("residentialCity")%>
                                                                                value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demographic.getResidentialCity()))%>">
                                                        </td>
                                                    </tr>

                                                    <tr valign="top">
                                                        <td align="right"><b>
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialProvince"/>
                                                            : </b></td>
                                                        <td align="left">

                                                            <%
                                                                String residentialProvince = demographic.getResidentialProvince(); %>
                                                            <%
                                                                if ("true".equals(CarlosProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                                            %>
                                                            <select name="residentialProvince"
                                                                    id="residentialProvince"></select>
                                                            <br/>
                                                            Filter by Country: <select name="residentialCountry"
                                                                                       id="residentialCountry"></select>

                                                            <% } else { %>

                                                            <select name="residentialProvince"
                                                                    style="width: 200px" <%=getDisabled("residentialProvince")%>>
                                                                <option value="OT"
                                                                        <%=(residentialProvince == null || residentialProvince.equals("OT") || residentialProvince.equals("") || residentialProvince.length() > 2) ? " selected" : ""%>>
                                                                    Other
                                                                </option>
                                                                <% if (pNames.isDefined()) {
                                                                    for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                                                        String pr2 = (String) li.next(); %>
                                                                <option value="<%=pr2%>"
                                                                        <%=pr2.equals(residentialProvince) ? " selected" : ""%>><%=li.next()%>
                                                                </option>
                                                                <% }//for %>
                                                                <% } else { %>
                                                                <option value="AB" <%="AB".equals(residentialProvince) ? " selected" : ""%>>
                                                                    AB-Alberta
                                                                </option>
                                                                <option value="BC" <%="BC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    BC-British Columbia
                                                                </option>
                                                                <option value="MB" <%="MB".equals(residentialProvince) ? " selected" : ""%>>
                                                                    MB-Manitoba
                                                                </option>
                                                                <option value="NB" <%="NB".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NB-New Brunswick
                                                                </option>
                                                                <option value="NL" <%="NL".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NL-Newfoundland Labrador
                                                                </option>
                                                                <option value="NT" <%="NT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NT-Northwest Territory
                                                                </option>
                                                                <option value="NS" <%="NS".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NS-Nova Scotia
                                                                </option>
                                                                <option value="NU" <%="NU".equals(residentialProvince) ? " selected" : ""%>>
                                                                    NU-Nunavut
                                                                </option>
                                                                <option value="ON" <%="ON".equals(residentialProvince) ? " selected" : ""%>>
                                                                    ON-Ontario
                                                                </option>
                                                                <option value="PE" <%="PE".equals(residentialProvince) ? " selected" : ""%>>
                                                                    PE-Prince Edward Island
                                                                </option>
                                                                <option value="QC" <%="QC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    QC-Quebec
                                                                </option>
                                                                <option value="SK" <%="SK".equals(residentialProvince) ? " selected" : ""%>>
                                                                    SK-Saskatchewan
                                                                </option>
                                                                <option value="YT" <%="YT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    YT-Yukon
                                                                </option>
                                                                <option value="US" <%="US".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US resident
                                                                </option>
                                                                <option value="US-AK" <%="US-AK".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-AK-Alaska
                                                                </option>
                                                                <option value="US-AL" <%="US-AL".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-AL-Alabama
                                                                </option>
                                                                <option value="US-AR" <%="US-AR".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-AR-Arkansas
                                                                </option>
                                                                <option value="US-AZ" <%="US-AZ".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-AZ-Arizona
                                                                </option>
                                                                <option value="US-CA" <%="US-CA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-CA-California
                                                                </option>
                                                                <option value="US-CO" <%="US-CO".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-CO-Colorado
                                                                </option>
                                                                <option value="US-CT" <%="US-CT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-CT-Connecticut
                                                                </option>
                                                                <option value="US-CZ" <%="US-CZ".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-CZ-Canal Zone
                                                                </option>
                                                                <option value="US-DC" <%="US-DC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-DC-District Of Columbia
                                                                </option>
                                                                <option value="US-DE" <%="US-DE".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-DE-Delaware
                                                                </option>
                                                                <option value="US-FL" <%="US-FL".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-FL-Florida
                                                                </option>
                                                                <option value="US-GA" <%="US-GA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-GA-Georgia
                                                                </option>
                                                                <option value="US-GU" <%="US-GU".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-GU-Guam
                                                                </option>
                                                                <option value="US-HI" <%="US-HI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-HI-Hawaii
                                                                </option>
                                                                <option value="US-IA" <%="US-IA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-IA-Iowa
                                                                </option>
                                                                <option value="US-ID" <%="US-ID".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-ID-Idaho
                                                                </option>
                                                                <option value="US-IL" <%="US-IL".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-IL-Illinois
                                                                </option>
                                                                <option value="US-IN" <%="US-IN".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-IN-Indiana
                                                                </option>
                                                                <option value="US-KS" <%="US-KS".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-KS-Kansas
                                                                </option>
                                                                <option value="US-KY" <%="US-KY".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-KY-Kentucky
                                                                </option>
                                                                <option value="US-LA" <%="US-LA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-LA-Louisiana
                                                                </option>
                                                                <option value="US-MA" <%="US-MA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MA-Massachusetts
                                                                </option>
                                                                <option value="US-MD" <%="US-MD".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MD-Maryland
                                                                </option>
                                                                <option value="US-ME" <%="US-ME".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-ME-Maine
                                                                </option>
                                                                <option value="US-MI" <%="US-MI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MI-Michigan
                                                                </option>
                                                                <option value="US-MN" <%="US-MN".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MN-Minnesota
                                                                </option>
                                                                <option value="US-MO" <%="US-MO".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MO-Missouri
                                                                </option>
                                                                <option value="US-MS" <%="US-MS".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MS-Mississippi
                                                                </option>
                                                                <option value="US-MT" <%="US-MT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-MT-Montana
                                                                </option>
                                                                <option value="US-NC" <%="US-NC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NC-North Carolina
                                                                </option>
                                                                <option value="US-ND" <%="US-ND".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-ND-North Dakota
                                                                </option>
                                                                <option value="US-NE" <%="US-NE".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NE-Nebraska
                                                                </option>
                                                                <option value="US-NH" <%="US-NH".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NH-New Hampshire
                                                                </option>
                                                                <option value="US-NJ" <%="US-NJ".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NJ-New Jersey
                                                                </option>
                                                                <option value="US-NM" <%="US-NM".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NM-New Mexico
                                                                </option>
                                                                <option value="US-NU" <%="US-NU".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NU-Nunavut
                                                                </option>
                                                                <option value="US-NV" <%="US-NV".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NV-Nevada
                                                                </option>
                                                                <option value="US-NY" <%="US-NY".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-NY-New York
                                                                </option>
                                                                <option value="US-OH" <%="US-OH".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-OH-Ohio
                                                                </option>
                                                                <option value="US-OK" <%="US-OK".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-OK-Oklahoma
                                                                </option>
                                                                <option value="US-OR" <%="US-OR".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-OR-Oregon
                                                                </option>
                                                                <option value="US-PA" <%="US-PA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-PA-Pennsylvania
                                                                </option>
                                                                <option value="US-PR" <%="US-PR".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-PR-Puerto Rico
                                                                </option>
                                                                <option value="US-RI" <%="US-RI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-RI-Rhode Island
                                                                </option>
                                                                <option value="US-SC" <%="US-SC".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-SC-South Carolina
                                                                </option>
                                                                <option value="US-SD" <%="US-SD".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-SD-South Dakota
                                                                </option>
                                                                <option value="US-TN" <%="US-TN".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-TN-Tennessee
                                                                </option>
                                                                <option value="US-TX" <%="US-TX".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-TX-Texas
                                                                </option>
                                                                <option value="US-UT" <%="US-UT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-UT-Utah
                                                                </option>
                                                                <option value="US-VA" <%="US-VA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-VA-Virginia
                                                                </option>
                                                                <option value="US-VI" <%="US-VI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-VI-Virgin Islands
                                                                </option>
                                                                <option value="US-VT" <%="US-VT".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-VT-Vermont
                                                                </option>
                                                                <option value="US-WA" <%="US-WA".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-WA-Washington
                                                                </option>
                                                                <option value="US-WI" <%="US-WI".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-WI-Wisconsin
                                                                </option>
                                                                <option value="US-WV" <%="US-WV".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-WV-West Virginia
                                                                </option>
                                                                <option value="US-WY" <%="US-WY".equals(residentialProvince) ? " selected" : ""%>>
                                                                    US-WY-Wyoming
                                                                </option>
                                                                <% } %>
                                                            </select>
                                                            <% } %>
                                                        </td>
                                                        <td align="right"><b>
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialPostal"/>
                                                            : </b></td>
                                                        <td align="left"><input type="text" name="residentialPostal"
                                                                                size="30" <%=getDisabled("residentialPostal")%>
                                                                                value="<%=StringUtils.trimToEmpty(demographic.getResidentialPostal())%>"
                                                                                onBlur="upCaseCtrl(this)"
                                                                                onChange="isPostalCode2()"></td>
                                                    </tr>


                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneH"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <input type="text" name="phone"
                                                                   onblur="formatPhoneNum();" <%=getDisabled("phone")%>
                                                                   style="display: inline; width: auto;"
                                                                   value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demographic.getPhone()))%>">
                                                            <label for="hPhoneExt"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgExt"/>:</label>
                                                            <input type="text" style="width:50% !important;"
                                                                   name="hPhoneExt"
                                                                   id="hPhoneExt" <%=getDisabled("hPhoneExt")%>
                                                                   value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get("hPhoneExt")))%>"
                                                                   size="4"/>
                                                            <input type="hidden" name="hPhoneExtOrig"
                                                                   value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get("hPhoneExt")))%>"/>
                                                        </td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneW"/>:</b>
                                                        </td>
                                                        <td align="left"><input type="text"
                                                                                name="phone2" <%=getDisabled("phone2")%>
                                                                                onblur="formatPhoneNum();"
                                                                                style="display: inline; width: auto;"
                                                                                value="<%=StringUtils.trimToEmpty(demographic.getPhone2())%>">
                                                            <label for="wPhoneExt"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgExt"/>:</label>
                                                            <input type="text" style="width:50% !important;"
                                                                   name="wPhoneExt"
                                                                   id="wPhoneExt" <%=getDisabled("wPhoneExt")%>
                                                                   value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get("wPhoneExt")))%>"
                                                                   size="4"/> <input type="hidden"
                                                                                     name="wPhoneExtOrig"
                                                                                     value="<%=StringUtils.trimToEmpty(StringUtils.trimToEmpty(demoExt.get("wPhoneExt")))%>"/>
                                                        </td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneC"/>: </b>
                                                        </td>
                                                        <td align="left">
                                                            <input type="text" name="demo_cell"
                                                                   onblur="formatPhoneNum();"
                                                                   style="display: inline; width: auto;" <%=getDisabled("demo_cell")%>
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("demo_cell"))%>">
                                                            <input type="hidden" name="demo_cellOrig"
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("demo_cell"))%>"/>
                                                        </td>
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPhoneComment"/>: </b>
                                                        </td>
                                                        <td align="left" colspan="3">
                                                            <input type="hidden" name="phoneCommentOrig"
                                                                   value="<%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demoExt.get("phoneComment")))%>"/>
                                                            <textarea rows="2" cols="30"
                                                                      name="phoneComment"><%=StringEscapeUtils.escapeHtml4(StringUtils.trimToEmpty(demoExt.get("phoneComment")))%></textarea>
                                                        </td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNewsLetter"/>:
                                                        </b></td>
                                                        <td align="left">
                                                            <%
                                                                String newsletter = io.github.carlos_emr.carlos.util.StringUtils.noNull(demographic.getNewsletter()).trim();
                                                                if (newsletter == null || newsletter.equals("")) {
                                                                    newsletter = "Unknown";
                                                                }
                                                            %> <select name="newsletter" <%=getDisabled("newsletter")%>>
                                                            <option value="Unknown" <%if (newsletter.equals("Unknown")) {%>
                                                                    selected <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optUnknown"/></option>
                                                            <option value="No" <%if (newsletter.equals("No")) {%>
                                                                    selected
                                                                    <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optNo"/></option>
                                                            <option value="Paper" <%if (newsletter.equals("Paper")) {%>
                                                                    selected <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optPaper"/></option>
                                                            <option value="Electronic"
                                                                    <%if (newsletter.equals("Electronic")) {%>
                                                                    selected <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optElectronic"/></option>
                                                        </select></td>

                                                        <oscar:oscarPropertiesCheck value="true" defaultVal="false"
                                                                                    property="FIRST_NATIONS_MODULE">
                                                            <td align="right"><b>First Nation: </b></td>
                                                            <td align="left">

                                                                <select name="aboriginal" <%=getDisabled("aboriginal")%>>
                                                                    <option value="" ${ pageScope.demoExtended["aboriginal"] eq '' ? 'selected' : '' } >
                                                                        Unknown
                                                                    </option>
                                                                    <option value="No" ${ pageScope.demoExtended["aboriginal"] eq 'No' ? 'selected' : '' } >
                                                                        No
                                                                    </option>
                                                                    <option value="Yes" ${ pageScope.demoExtended["aboriginal"] eq 'Yes' ? 'selected' : '' } >
                                                                        Yes
                                                                    </option>

                                                                </select>
                                                                <input type="hidden" name="aboriginalOrig"
                                                                       value="${ pageScope.demoExtended["aboriginal"] }"/>
                                                            </td>
                                                        </oscar:oscarPropertiesCheck>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEmail"/>: </b>
                                                        </td>
                                                        <td align="left"><input type="text" name="email"
                                                                                size="30" <%=getDisabled("email")%>
                                                                                value="<%=demographic.getEmail() !=null ? Encode.forHtmlContent(demographic.getEmail()) : ""%>">
                                                        </td>
                                                        <td style="text-align: right;">
                                                            <strong><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formGender"/></strong>
                                                        </td>
                                                        <td style="text-align: left;">
                                                            <input type="text" id="patientGender" name="gender"
                                                                   value="<%=Encode.forHtmlAttribute(StringUtils.trimToEmpty(demographic.getGender()))%>"/>
                                                        </td>
                                                    </tr>
                                                        <%--							<tr valign="top">--%>
                                                        <%--								<td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.consentToUseEmailForCare"/></b></td>--%>
                                                        <%--								<td align="left" nowrap>--%>
                                                        <%--									 <label for="consentToUseEmailForCareY"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgYes"/></label> --%>
                                                        <%--            								<input type="radio" value="yes" id="consentToUseEmailForCareY" name="consentToUseEmailForCare" <% if (demographic.getConsentToUseEmailForCare() != null && demographic.getConsentToUseEmailForCare()){ out.write("checked"); }%> />--%>
                                                        <%--          							 <label for="consentToUseEmailForCareN"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgNo"/></label>--%>
                                                        <%--            								<input type="radio" value="no" id="consentToUseEmailForCareN" name="consentToUseEmailForCare"  <% if (demographic.getConsentToUseEmailForCare() != null && !demographic.getConsentToUseEmailForCare()){ out.write("checked");}%> />--%>
                                                        <%--									 <label for="consentToUseEmailForCareE"><fmt:setBundle basename="oscarResources"/><fmt:message key="WriteScript.msgUnset"/></label>--%>
                                                        <%--            								<input type="radio" value="unset" id="consentToUseEmailForCareE" name="consentToUseEmailForCare"  <% if (demographic.getConsentToUseEmailForCare() == null){ out.write("checked"); } %> />--%>
                                                        <%--								</td>--%>
                                                        <%--							</tr>--%>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDOB"/>
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDOBDetais"/>:</b>
                                                        </td>
                                                        <td align="left" nowrap>
                                                            <%-- Single yyyy-mm-dd text input with calendar picker; hidden fields carry the parts the server expects. --%>
                                                            <input type="text" id="dob"
                                                                   name="dob"
                                                                   placeholder="yyyy-mm-dd"
                                                                   autocomplete="off"
                                                                   value="<%=Encode.forHtmlAttribute(dobDisplay)%>"
                                                                   <%=getDisabled("year_of_birth")%>>
                                                            <img src="<%= request.getContextPath() %>/images/cal.gif" id="dob_cal" alt="calendar">
                                                            <%-- Hidden part-fields consumed by the server --%>
                                                            <input type="hidden" name="year_of_birth"  id="year_of_birth"  value="<%=birthYear%>">
                                                            <input type="hidden" name="month_of_birth" id="month_of_birth" value="<%=birthMonth%>">
                                                            <input type="hidden" name="date_of_birth"  id="date_of_birth"  value="<%=birthDate%>">

                                                            <label for="age"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoAge"/>:</label>
                                                            <input type="text" name="age" id="age"
                                                                   value="<%=Encode.forHtmlAttribute(demographic.getAgeAsOf(new Date()))%>"
                                                                   readonly>

                                                        </td>
                                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formSex"/>:</b>
                                                        </td>
                                                        <td><select name="sex" id="sex">
                                                            <option value=""></option>
                                                            <% for (Gender gn : Gender.values()) {
                                                                String gnI18nKey;
                                                                switch (gn.name()) {
                                                                    case "M":  gnI18nKey = "global.gender.male";        break;
                                                                    case "F":  gnI18nKey = "global.gender.female";      break;
                                                                    case "X":  gnI18nKey = "global.gender.intersex";    break;
                                                                    case "O":  gnI18nKey = "global.gender.other";       break;
                                                                    default:   gnI18nKey = "global.gender.undisclosed"; break;
                                                                }
                                                            %>
                                                            <option value="<%= Encode.forHtmlAttribute(gn.name()) %>" <%=(StringUtils.equalsIgnoreCase(demographic.getSex(), gn.name()) ? " selected=\"selected\" " : "") %>><%= Encode.forHtml(oscarResources.getString(gnI18nKey)) %>
                                                            </option>
                                                            <% } %>
                                                        </select>
                                                        </td>
                                                    </tr>

                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHin"/>: </b>
                                                        </td>
                                                        <td align="left" nowrap><input type="text" name="hin"
                                                                                       id="hinBox" <%=getDisabled("hin")%>
                                                                                       value="<%=StringUtils.trimToEmpty(demographic.getHin())%>"
                                                                                       size="17">
                                                            <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formVer"/><input
                                                                    type="text" name="ver"
                                                                    style="width:20% !important;" <%=getDisabled("ver")%>
                                                                    value="<%=StringUtils.trimToEmpty(demographic.getVer())%>"
                                                                    onBlur="upCaseCtrl(this)" id="verBox">
                                                            <%if ("online".equals(oscarProps.getProperty("hcv.type", "simple"))) { %>
                                                            <input type="button" value="Validate"
                                                                   onClick="validateHC()"/>
                                                            <% } %>
                                                        </td>
                                                        <td align="right">
                                                            <b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEFFDate"/>:</b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                // sdf already defined in preamble
                                                                String effDate = null;
                                                                if (demographic.getEffDate() != null) {
                                                                    effDate = StringUtils.trimToNull(sdf.format(demographic.getEffDate()));
                                                                }
                                                                // Put 0 on the left on dates (decF already in preamble)
                                                                String effDateYear = "";
                                                                String effDateMonth = "";
                                                                String effDateDay = "";
                                                                if (effDate != null) {
                                                                    // Year
                                                                    decF.applyPattern("0000");
                                                                    effDateYear = decF.format(MyDateFormat.getYearFromStandardDate(effDate));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    effDateMonth = decF.format(MyDateFormat.getMonthFromStandardDate(effDate));
                                                                    effDateDay = decF.format(MyDateFormat.getDayFromStandardDate(effDate));
                                                                }
                                                            %> <input type="text" placeholder="yyyy"
                                                                      name="eff_date_year" <%=getDisabled("eff_date_year")%>
                                                                      size="4" maxlength="4" value="<%= effDateYear%>">
                                                            <input
                                                                    type="text" placeholder="mm" name="eff_date_month"
                                                                    size="2"
                                                                    maxlength="2" <%=getDisabled("eff_date_month")%>
                                                                    value="<%= effDateMonth%>"> <input type="text"
                                                                                                       placeholder="dd"
                                                                                                       name="eff_date_date"
                                                                                                       size="2"
                                                                                                       maxlength="2" <%=getDisabled("eff_date_date")%>
                                                                                                       value="<%= effDateDay%>">
                                                        </td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCType"/>:</b>
                                                        </td>
                                                        <td align="left">

                                                            <%
                                                                String hctype = demographic.getHcType() == null ? "" : demographic.getHcType(); %>
                                                            <select name="hc_type"
                                                                    style="width: 200px" <%=getDisabled("hc_type")%>>
                                                                <option value="OT"
                                                                        <%=(hctype.equals("OT") || hctype.equals("") || hctype.length() > 2) ? " selected" : ""%>>
                                                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.optOther"/></option>
                                                                <% if (pNames.isDefined()) {
                                                                    for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                                                        province = (String) li.next(); %>
                                                                <option value="<%=province%>"
                                                                        <%=province.equals(hctype) ? " selected" : ""%>><%=li.next()%>
                                                                </option>
                                                                <% } %>
                                                                <% } else { %>
                                                                <option value="AB" <%=hctype.equals("AB") ? " selected" : ""%>>
                                                                    AB-Alberta
                                                                </option>
                                                                <option value="BC" <%=hctype.equals("BC") ? " selected" : ""%>>
                                                                    BC-British Columbia
                                                                </option>
                                                                <option value="MB" <%=hctype.equals("MB") ? " selected" : ""%>>
                                                                    MB-Manitoba
                                                                </option>
                                                                <option value="NB" <%=hctype.equals("NB") ? " selected" : ""%>>
                                                                    NB-New Brunswick
                                                                </option>
                                                                <option value="NL" <%=hctype.equals("NL") ? " selected" : ""%>>
                                                                    NL-Newfoundland & Labrador
                                                                </option>
                                                                <option value="NT" <%=hctype.equals("NT") ? " selected" : ""%>>
                                                                    NT-Northwest Territory
                                                                </option>
                                                                <option value="NS" <%=hctype.equals("NS") ? " selected" : ""%>>
                                                                    NS-Nova Scotia
                                                                </option>
                                                                <option value="NU" <%=hctype.equals("NU") ? " selected" : ""%>>
                                                                    NU-Nunavut
                                                                </option>
                                                                <option value="ON" <%=hctype.equals("ON") ? " selected" : ""%>>
                                                                    ON-Ontario
                                                                </option>
                                                                <option value="PE" <%=hctype.equals("PE") ? " selected" : ""%>>
                                                                    PE-Prince Edward Island
                                                                </option>
                                                                <option value="QC" <%=hctype.equals("QC") ? " selected" : ""%>>
                                                                    QC-Quebec
                                                                </option>
                                                                <option value="SK" <%=hctype.equals("SK") ? " selected" : ""%>>
                                                                    SK-Saskatchewan
                                                                </option>
                                                                <option value="YT" <%=hctype.equals("YT") ? " selected" : ""%>>
                                                                    YT-Yukon
                                                                </option>
                                                                <option value="US" <%=hctype.equals("US") ? " selected" : ""%>>
                                                                    US resident
                                                                </option>
                                                                <option value="US-AK" <%=hctype.equals("US-AK") ? " selected" : ""%>>
                                                                    US-AK-Alaska
                                                                </option>
                                                                <option value="US-AL" <%=hctype.equals("US-AL") ? " selected" : ""%>>
                                                                    US-AL-Alabama
                                                                </option>
                                                                <option value="US-AR" <%=hctype.equals("US-AR") ? " selected" : ""%>>
                                                                    US-AR-Arkansas
                                                                </option>
                                                                <option value="US-AZ" <%=hctype.equals("US-AZ") ? " selected" : ""%>>
                                                                    US-AZ-Arizona
                                                                </option>
                                                                <option value="US-CA" <%=hctype.equals("US-CA") ? " selected" : ""%>>
                                                                    US-CA-California
                                                                </option>
                                                                <option value="US-CO" <%=hctype.equals("US-CO") ? " selected" : ""%>>
                                                                    US-CO-Colorado
                                                                </option>
                                                                <option value="US-CT" <%=hctype.equals("US-CT") ? " selected" : ""%>>
                                                                    US-CT-Connecticut
                                                                </option>
                                                                <option value="US-CZ" <%=hctype.equals("US-CZ") ? " selected" : ""%>>
                                                                    US-CZ-Canal Zone
                                                                </option>
                                                                <option value="US-DC" <%=hctype.equals("US-DC") ? " selected" : ""%>>
                                                                    US-DC-District Of Columbia
                                                                </option>
                                                                <option value="US-DE" <%=hctype.equals("US-DE") ? " selected" : ""%>>
                                                                    US-DE-Delaware
                                                                </option>
                                                                <option value="US-FL" <%=hctype.equals("US-FL") ? " selected" : ""%>>
                                                                    US-FL-Florida
                                                                </option>
                                                                <option value="US-GA" <%=hctype.equals("US-GA") ? " selected" : ""%>>
                                                                    US-GA-Georgia
                                                                </option>
                                                                <option value="US-GU" <%=hctype.equals("US-GU") ? " selected" : ""%>>
                                                                    US-GU-Guam
                                                                </option>
                                                                <option value="US-HI" <%=hctype.equals("US-HI") ? " selected" : ""%>>
                                                                    US-HI-Hawaii
                                                                </option>
                                                                <option value="US-IA" <%=hctype.equals("US-IA") ? " selected" : ""%>>
                                                                    US-IA-Iowa
                                                                </option>
                                                                <option value="US-ID" <%=hctype.equals("US-ID") ? " selected" : ""%>>
                                                                    US-ID-Idaho
                                                                </option>
                                                                <option value="US-IL" <%=hctype.equals("US-IL") ? " selected" : ""%>>
                                                                    US-IL-Illinois
                                                                </option>
                                                                <option value="US-IN" <%=hctype.equals("US-IN") ? " selected" : ""%>>
                                                                    US-IN-Indiana
                                                                </option>
                                                                <option value="US-KS" <%=hctype.equals("US-KS") ? " selected" : ""%>>
                                                                    US-KS-Kansas
                                                                </option>
                                                                <option value="US-KY" <%=hctype.equals("US-KY") ? " selected" : ""%>>
                                                                    US-KY-Kentucky
                                                                </option>
                                                                <option value="US-LA" <%=hctype.equals("US-LA") ? " selected" : ""%>>
                                                                    US-LA-Louisiana
                                                                </option>
                                                                <option value="US-MA" <%=hctype.equals("US-MA") ? " selected" : ""%>>
                                                                    US-MA-Massachusetts
                                                                </option>
                                                                <option value="US-MD" <%=hctype.equals("US-MD") ? " selected" : ""%>>
                                                                    US-MD-Maryland
                                                                </option>
                                                                <option value="US-ME" <%=hctype.equals("US-ME") ? " selected" : ""%>>
                                                                    US-ME-Maine
                                                                </option>
                                                                <option value="US-MI" <%=hctype.equals("US-MI") ? " selected" : ""%>>
                                                                    US-MI-Michigan
                                                                </option>
                                                                <option value="US-MN" <%=hctype.equals("US-MN") ? " selected" : ""%>>
                                                                    US-MN-Minnesota
                                                                </option>
                                                                <option value="US-MO" <%=hctype.equals("US-MO") ? " selected" : ""%>>
                                                                    US-MO-Missouri
                                                                </option>
                                                                <option value="US-MS" <%=hctype.equals("US-MS") ? " selected" : ""%>>
                                                                    US-MS-Mississippi
                                                                </option>
                                                                <option value="US-MT" <%=hctype.equals("US-MT") ? " selected" : ""%>>
                                                                    US-MT-Montana
                                                                </option>
                                                                <option value="US-NC" <%=hctype.equals("US-NC") ? " selected" : ""%>>
                                                                    US-NC-North Carolina
                                                                </option>
                                                                <option value="US-ND" <%=hctype.equals("US-ND") ? " selected" : ""%>>
                                                                    US-ND-North Dakota
                                                                </option>
                                                                <option value="US-NE" <%=hctype.equals("US-NE") ? " selected" : ""%>>
                                                                    US-NE-Nebraska
                                                                </option>
                                                                <option value="US-NH" <%=hctype.equals("US-NH") ? " selected" : ""%>>
                                                                    US-NH-New Hampshire
                                                                </option>
                                                                <option value="US-NJ" <%=hctype.equals("US-NJ") ? " selected" : ""%>>
                                                                    US-NJ-New Jersey
                                                                </option>
                                                                <option value="US-NM" <%=hctype.equals("US-NM") ? " selected" : ""%>>
                                                                    US-NM-New Mexico
                                                                </option>
                                                                <option value="US-NU" <%=hctype.equals("US-NU") ? " selected" : ""%>>
                                                                    US-NU-Nunavut
                                                                </option>
                                                                <option value="US-NV" <%=hctype.equals("US-NV") ? " selected" : ""%>>
                                                                    US-NV-Nevada
                                                                </option>
                                                                <option value="US-NY" <%=hctype.equals("US-NY") ? " selected" : ""%>>
                                                                    US-NY-New York
                                                                </option>
                                                                <option value="US-OH" <%=hctype.equals("US-OH") ? " selected" : ""%>>
                                                                    US-OH-Ohio
                                                                </option>
                                                                <option value="US-OK" <%=hctype.equals("US-OK") ? " selected" : ""%>>
                                                                    US-OK-Oklahoma
                                                                </option>
                                                                <option value="US-OR" <%=hctype.equals("US-OR") ? " selected" : ""%>>
                                                                    US-OR-Oregon
                                                                </option>
                                                                <option value="US-PA" <%=hctype.equals("US-PA") ? " selected" : ""%>>
                                                                    US-PA-Pennsylvania
                                                                </option>
                                                                <option value="US-PR" <%=hctype.equals("US-PR") ? " selected" : ""%>>
                                                                    US-PR-Puerto Rico
                                                                </option>
                                                                <option value="US-RI" <%=hctype.equals("US-RI") ? " selected" : ""%>>
                                                                    US-RI-Rhode Island
                                                                </option>
                                                                <option value="US-SC" <%=hctype.equals("US-SC") ? " selected" : ""%>>
                                                                    US-SC-South Carolina
                                                                </option>
                                                                <option value="US-SD" <%=hctype.equals("US-SD") ? " selected" : ""%>>
                                                                    US-SD-South Dakota
                                                                </option>
                                                                <option value="US-TN" <%=hctype.equals("US-TN") ? " selected" : ""%>>
                                                                    US-TN-Tennessee
                                                                </option>
                                                                <option value="US-TX" <%=hctype.equals("US-TX") ? " selected" : ""%>>
                                                                    US-TX-Texas
                                                                </option>
                                                                <option value="US-UT" <%=hctype.equals("US-UT") ? " selected" : ""%>>
                                                                    US-UT-Utah
                                                                </option>
                                                                <option value="US-VA" <%=hctype.equals("US-VA") ? " selected" : ""%>>
                                                                    US-VA-Virginia
                                                                </option>
                                                                <option value="US-VI" <%=hctype.equals("US-VI") ? " selected" : ""%>>
                                                                    US-VI-Virgin Islands
                                                                </option>
                                                                <option value="US-VT" <%=hctype.equals("US-VT") ? " selected" : ""%>>
                                                                    US-VT-Vermont
                                                                </option>
                                                                <option value="US-WA" <%=hctype.equals("US-WA") ? " selected" : ""%>>
                                                                    US-WA-Washington
                                                                </option>
                                                                <option value="US-WI" <%=hctype.equals("US-WI") ? " selected" : ""%>>
                                                                    US-WI-Wisconsin
                                                                </option>
                                                                <option value="US-WV" <%=hctype.equals("US-WV") ? " selected" : ""%>>
                                                                    US-WV-West Virginia
                                                                </option>
                                                                <option value="US-WY" <%=hctype.equals("US-WY") ? " selected" : ""%>>
                                                                    US-WY-Wyoming
                                                                </option>
                                                                <% } %>
                                                            </select>
                                                        </td>

                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCRenewDate"/>:</b>
                                                        </td>
                                                        <td align="left">
                                                            <%
                                                                // Put 0 on the left on dates
                                                                // Year
                                                                decF.applyPattern("0000");

                                                                GregorianCalendar hcRenewalCal = new GregorianCalendar();
                                                                String renewDateYear = "";
                                                                String renewDateMonth = "";
                                                                String renewDateDay = "";
                                                                if (demographic.getHcRenewDate() != null) {
                                                                    hcRenewalCal.setTime(demographic.getHcRenewDate());
                                                                    renewDateYear = decF.format(hcRenewalCal.get(GregorianCalendar.YEAR));
                                                                    // Month and Day
                                                                    decF.applyPattern("00");
                                                                    renewDateMonth = decF.format(hcRenewalCal.get(GregorianCalendar.MONTH) + 1);
                                                                    renewDateDay = decF.format(hcRenewalCal.get(GregorianCalendar.DAY_OF_MONTH));
                                                                }

                                                            %>
                                                            <input type="text" placeholder="yyyy"
                                                                   name="hc_renew_date_year" size="4" maxlength="4"
                                                                   value="<%=renewDateYear%>" <%=getDisabled("hc_renew_date_year")%>>
                                                            <input type="text" placeholder="mm"
                                                                   name="hc_renew_date_month" size="2" maxlength="2"
                                                                   value="<%=renewDateMonth%>" <%=getDisabled("hc_renew_date_month")%>>
                                                            <input type="text" placeholder="dd"
                                                                   name="hc_renew_date_date" size="2" maxlength="2"
                                                                   value="<%=renewDateDay%>" <%=getDisabled("hc_renew_date_date")%>>
                                                        </td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCountryOfOrigin"/>: </b>
                                                        </td>
                                                        <td align="left"><select id="countryOfOrigin"
                                                                                 name="countryOfOrigin"
                                                                                 style="width: 200px;" <%=getDisabled("countryOfOrigin")%>>
                                                            <option value="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgNotSet"/></option>
                                                            <%for (CountryCode cc : countryList) { %>
                                                            <option value="<%=cc.getCountryId()%>"
                                                                    <% if (io.github.carlos_emr.carlos.util.StringUtils.noNull(demographic.getCountryOfOrigin()).equals(cc.getCountryId())) {
                                                                        out.print("SELECTED");
                                                                    }%>><%=cc.getCountryName() %>
                                                            </option>
                                                            <%}%>
                                                        </select></td>
                                                        <td><!-- padding --></td>
                                                        <td><!-- padding --></td>
                                                    </tr>
                                                    <tr valign="top">
                                                        <td align="right"><b>SIN:</b></td>
                                                        <td align="left"><input type="text" name="sin"
                                                                                size="30" <%=getDisabled("sin")%>
                                                                                value="<%=(demographic.getSin()==null||demographic.getSin().equals("null"))?"":demographic.getSin()%>">
                                                        </td>
                                                        <td align="right" nowrap><b> <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.cytolNum"/>:</b>
                                                        </td>
                                                        <td><input type="text"
                                                                   name="cytolNum" <%=getDisabled("cytolNum")%>
                                                                   style="display: inline; width: auto;"
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("cytolNum"))%>">
                                                            <input type="hidden" name="cytolNumOrig"
                                                                   value="<%=StringUtils.trimToEmpty(demoExt.get("cytolNum"))%>"/>
                                                        </td>
                                                    </tr>
                                                        <%-- TOGGLE FIRST NATIONS MODULE --%>
                                                    <oscar:oscarPropertiesCheck value="true" defaultVal="false"
                                                                                property="FIRST_NATIONS_MODULE">
                                                        <tr>
                                                            <td colspan="8">
                                                                <jsp:include page="/demographic/manageFirstNationsModule.jsp">
                                                                    <jsp:param name="demo"
                                                                               value="<%= Encode.forHtmlAttribute(demographic_no) %>"/>
                                                                </jsp:include>
                                                            </td>
                                                        </tr>
                                                    </oscar:oscarPropertiesCheck>
                                                        <%-- END TOGGLE FIRST NATIONS MODULE --%>
