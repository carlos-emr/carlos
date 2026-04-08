<%-- edit-view.jsp: Read-only demographic display (extracted from demographiceditdemographic.jsp lines 1513-2609) --%>
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
                                                <div style="display: block;" id="viewDemographics2" class="hide-empties">
                                                    <div class="demographicWrapper">
                                                        <div class="leftSection">
                                                            <div class="demographicSection" id="demographic">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemographic"/></h3>
                                                                <%
                                                                    for (String key : demoExt.keySet()) {
                                                                        if (key.endsWith("_id")) {
                                                                %>
                                                                <input type="hidden" name="<%= key %>"
                                                                       value="<%=Encode.forHtml(StringUtils.trimToEmpty(demoExt.get(key)))%>"/>
                                                                <%
                                                                        }
                                                                    }
                                                                %>
                                                                <ul>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoTitle"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getTitle())%></span>
                                                                    </li>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formLastName"/>:</span>
                                                                        <span class="info"><%=Encode.forHtmlContent(demographic.getLastName())%></span>
                                                                    </li>
                                                                    <li><span class="label">
							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formFirstName"/>:</span>
                                                                        <span class="info"><%=Encode.forHtmlContent(demographic.getFirstName())%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMiddleNames"/>:</span>
                                                                        <span class="info"> <%=Encode.forHtmlContent(demographic.getMiddleNames())%></span>
                                                                    </li>
                                                                    <li>
														<span class="label" style="color:red;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNameUsed"/>:
														</span>
                                                                        <span class="info" style="color:red;">
															<%= Encode.forHtml(demographic.getAlias()) %>
														</span>
                                                                    </li>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPronouns"/>:</span>
                                                                        <span class="info"><%=Encode.forHtmlContent(StringUtils.trimToEmpty(demographic.getPronoun()))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formSex"/>:</span>
                                                                        <span class="info"><%
                                                                            String viewSexCode = demographic.getSex() != null ? demographic.getSex().toUpperCase() : "U";
                                                                            String viewGenderKey;
                                                                            switch (viewSexCode) {
                                                                                case "M":  viewGenderKey = "global.gender.male";        break;
                                                                                case "F":  viewGenderKey = "global.gender.female";      break;
                                                                                case "X":  viewGenderKey = "global.gender.intersex";    break;
                                                                                case "O":  viewGenderKey = "global.gender.other";       break;
                                                                                default:   viewGenderKey = "global.gender.undisclosed"; break;
                                                                            }
                                                                        %><%= Encode.forHtml(oscarResources.getString(viewGenderKey)) %></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formGender"/>:</span>
                                                                        <span class="info"><%=Encode.forHtmlContent(StringUtils.trimToEmpty(demographic.getGender()))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoAge"/>:</span>
                                                                        <span class="info"><%=demographic.getAgeAsOf(new Date())%>&nbsp;(
	                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDOB"/>: <%=birthYear%>-<%=birthMonth%>-<%=birthDate%>)
                                                        </span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgDemoLanguage"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getOfficialLanguage())%></span>
                                                                    </li>
                                                                    <% if (demographic.getCountryOfOrigin() != null && !demographic.getCountryOfOrigin().equals("") && !demographic.getCountryOfOrigin().equals("-1")) {
                                                                        CountryCode countryCode = ccDAO.getCountryCode(demographic.getCountryOfOrigin());
                                                                        if (countryCode != null) {
                                                                    %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgCountryOfOrigin"/>:</span>
                                                                        <span class="info"><%=countryCode.getCountryName() %></span>
                                                                    </li>
                                                                    <% }
                                                                    }
                                                                    %>
                                                                    <% String sp_lang = demographic.getSpokenLanguage();
                                                                        if (sp_lang != null && sp_lang.length() > 0) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSpokenLang"/>:</span>
                                                                        <span class="info"><%=sp_lang%></span>
                                                                    </li>
                                                                    <% } %>

                                                                    <% String sin = demographic.getSin();
                                                                        if (sin != null && sin.length() > 0) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgSIN"/>:</span>
                                                                        <span class="info"><%=sin%></span>
                                                                    </li>
                                                                    <% } %>

                                                                    <oscar:oscarPropertiesCheck value="true"
                                                                                                defaultVal="false"
                                                                                                property="FIRST_NATIONS_MODULE">
                                                                        <li><span class="label">
	                           	First Nation:</span>
                                                                            <span class="info">
	                            	<c:out value='${ pageScope.demoExtended["aboriginal"] }'/>
	                            </span>
                                                                        </li>
                                                                        <li>
                                                                            <span class="label">Status Number:</span>
                                                                            <span class="info"><c:out
                                                                                    value='${ pageScope.demoExtended["statusNum"] }'/></span>
                                                                        </li>
                                                                        <li>
                                                                            <span class="label">First Nation Community:</span>
                                                                            <span class="info"><c:out
                                                                                    value='${ fncommunity }'/></span>
                                                                        </li>
                                                                    </oscar:oscarPropertiesCheck>

                                                                    <% if (oscarProps.getProperty("EXTRA_DEMO_FIELDS") != null) {
                                                                        String fieldJSP = "/demographic/" + oscarProps.getProperty("EXTRA_DEMO_FIELDS");
                                                                        fieldJSP += "View.jsp";
                                                                    %>
                                                                    <jsp:include page="<%=fieldJSP%>">
                                                                        <jsp:param name="demo"
                                                                                   value="<%= Encode.forHtmlAttribute(demographic_no) %>"/>
                                                                    </jsp:include>
                                                                    <%}%>

                                                                </ul>
                                                            </div>

                                                                <%-- TOGGLE NEW CONTACTS UI --%>
                                                            <%if (!oscarProps.isPropertyActive("NEW_CONTACTS_UI")) { %>

                                                            <div class="demographicSection" id="otherContacts">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgOtherContacts"/>
                                                                    <a class="h3-pill" href="javascript: function myFunction() {return false; }"
                                                                            onClick="popup(700,960,'<%= request.getContextPath() %>/demographic/AddAlternateContact.jsp?demo=<%=demographic.getDemographicNo()%>','AddRelation')">
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgAddRelation"/></a>
                                                                </h3>
                                                                <ul>
                                                                    <%
                                                                        DemographicRelationship demoRelation = new DemographicRelationship();
                                                                        List relList = demoRelation.getDemographicRelationshipsWithNamePhone(loggedInInfo, demographic.getDemographicNo().toString(), loggedInInfo.getCurrentFacility().getId());
                                                                        for (int reCounter = 0; reCounter < relList.size(); reCounter++) {
                                                                            HashMap relHash = (HashMap) relList.get(reCounter);
                                                                            String dNo = (String) relHash.get("demographicNo");
                                                                            String workPhone = demographicManager.getDemographicWorkPhoneAndExtension(loggedInInfo, Integer.valueOf(dNo));
                                                                            String cellPhone = demographicExtDao.getValueForDemoKey(Integer.valueOf(dNo), "demo_cell");

                                                                            String formattedWorkPhone = (workPhone != null && workPhone.length() > 0 && !workPhone.equals("null")) ? "  W:" + workPhone : "";
                                                                            String formattedCellPhone = (cellPhone != null && cellPhone.length() > 0 && !cellPhone.equals("null")) ? "  C:" + cellPhone : "";
                                                                            String sdb = relHash.get("subDecisionMaker") == null ? "" : ((Boolean) relHash.get("subDecisionMaker")).booleanValue() ? "<span title=\"SDM\" >/SDM</span>" : "";
                                                                            String ec = relHash.get("emergencyContact") == null ? "" : ((Boolean) relHash.get("emergencyContact")).booleanValue() ? "<span title=\"Emergency Contact\">/EC</span>" : "";
                                                                            String masterLink = "<a target=\"demographic" + dNo + "\" href=\"" + request.getContextPath() + "/demographic/DemographicEdit.do?demographic_no=" + dNo + "\">M</a>";
                                                                            String encounterLink = "<a target=\"encounter" + dNo + "\" href=\"javascript: function myFunction() {return false; }\" onClick=\"popupEChart(710,1024,'" + request.getContextPath() + "/encounter/IncomingEncounter.do?demographicNo=" + dNo + "&providerNo=" + loggedInInfo.getLoggedInProviderNo() + "&appointmentNo=&curProviderNo=&reason=&appointmentDate=&startTime=&status=&userName=" + URLEncoder.encode(userfirstname + " " + userlastname, StandardCharsets.UTF_8) + "&curDate=" + dateString + "');return false;\">E</a>";
                                                                    %>
                                                                    <li><span
                                                                            class="label"><%=relHash.get("relation")%><%=sdb%><%=ec%>:</span>
                                                                        <span class="info"><%=relHash.get("lastName")%>, <%=relHash.get("firstName")%>, H:<%=relHash.get("phone") == null ? "" : relHash.get("phone")%><%=formattedWorkPhone%><%=formattedCellPhone%> <%=masterLink%> <%=encounterLink %></span>
                                                                    </li>
                                                                    <%}%>

                                                                </ul>
                                                            </div>

                                                            <% } else { %>

                                                            <div class="demographicSection" id="otherContacts2">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgOtherContacts"/>:
                                                                    <b><a
                                                                            href="javascript: function myFunction() {return false; }"
                                                                            onClick="popup(700,960,'Contact.do?method=manage&demographic_no=<%=demographic.getDemographicNo()%>','ManageContacts')">
                                                                        <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgManageContacts"/><!--i18n--></a></b>
                                                                </h3>
                                                                <ul>
                                                                    <%
                                                                        ContactDao contactDao = (ContactDao) SpringUtils.getBean(ContactDao.class);
                                                                        DemographicContactDao dContactDao = (DemographicContactDao) SpringUtils.getBean(DemographicContactDao.class);
                                                                        List<DemographicContact> dContacts = dContactDao.findByDemographicNo(demographic.getDemographicNo());
                                                                        dContacts = Contact2Action.fillContactNames(dContacts);
                                                                        for (DemographicContact dContact : dContacts) {
                                                                            String sdm = (dContact.getSdm() != null && dContact.getSdm().equals("true")) ? "<span title=\"SDM\" >/SDM</span>" : "";
                                                                            String ec = (dContact.getEc() != null && dContact.getEc().equals("true")) ? "<span title=\"Emergency Contact\" >/EC</span>" : "";
                                                                            String masterLink = null;
                                                                            if (DemographicContact.CATEGORY_PERSONAL.equals(dContact.getCategory()) && DemographicContact.TYPE_DEMOGRAPHIC == dContact.getType()) {
                                                                                masterLink = "<a target=\"demographic" + dContact.getContactId() + "\" href=\"" + request.getContextPath() + "/demographic/DemographicEdit.do?demographic_no=" + dContact.getContactId() + "\">M</a>";
                                                                            }
                                                                            if (DemographicContact.CATEGORY_PERSONAL.equals(dContact.getCategory()) && DemographicContact.TYPE_CONTACT == dContact.getType()) {
                                                                                masterLink = "<a target=\"_blank\" href=\"" + request.getContextPath() + "/demographic/Contact.do?method=viewContact&contact.id=" + dContact.getContactId() + "\">details</a>";
                                                                            }
                                                                    %>

                                                                    <li><span
                                                                            class="label"><%=dContact.getRole()%>:</span>
                                                                        <span class="info"><%=dContact.getContactName() %><%=sdm%><%=ec%> <%=masterLink != null ? masterLink : "" %></span>
                                                                    </li>

                                                                    <%} %>

                                                                </ul>
                                                            </div>

                                                            <% } %>
                                                            <div class="demographicSection" id="clinicStatus">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgClinicStatus"/>
                                                                    <a class="h3-pill" href="#"
                                                                        onclick="popup(1000, 650, '<%= Encode.forJavaScriptAttribute(request.getContextPath() + "/demographic/EnrollmentHistory.jsp?demographicNo=" + Encode.forUriComponent(demographic_no)) %>', 'enrollmentHistory'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgEnrollmentHistory"/></a>
                                                                </h3>
                                                                <ul>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRosterStatus"/>:</span>
                                                                        <span class="info"><%=demographic.getRosterStatusDisplay()%></span>
                                                                    </li>
                                                                    <%if ("RO".equals(demographic.getRosterStatus()) || "TE".equals(demographic.getRosterStatus())) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.DateJoined"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getRosterDate())%></span>
                                                                    </li>
                                                                    <% } %>
                                                                    <%if ("RO".equals(demographic.getRosterStatus())) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterEnrolledTo"/>:</span>
                                                                        <span class="info">
                                                        <%
                                                            String enrolledTo = "";
                                                            if (demographic.getRosterEnrolledTo() != null) {
                                                                Provider enrolledToProvider = providerDao.getProvider(demographic.getRosterEnrolledTo());
                                                                if (enrolledToProvider != null) {
                                                                    enrolledTo = enrolledToProvider.getFormattedName();
                                                                }
                                                            }
                                                        %>
                                                        <%=enrolledTo %>
                                                        </span>
                                                                    </li>
                                                                    <% } %>
                                                                    <%if ("TE".equals(demographic.getRosterStatus())) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterTerminationDate"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getRosterTerminationDate())%></span>
                                                                    </li>
                                                                    <%if (null != demographic.getRosterTerminationDate()) { %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.RosterTerminationReason"/>:</span>
                                                                        <span class="info"><%=Util.rosterTermReasonProperties.getReasonByCode(demographic.getRosterTerminationReason()) %></span>
                                                                    </li>
                                                                    <%
                                                                            }
                                                                        }
                                                                    %>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPatientStatus"/>:</span>
                                                                        <span class="info">
							<%
                                String PatStat = demographic.getPatientStatus();
                                String Dead = "DE";
                                String Inactive = "IN";

                                if (Dead.equals(PatStat)) {%>
							<b style="color: #FF0000;"><%=demographic.getPatientStatus()%></b>
							<%} else if (Inactive.equals(PatStat)) {%>
							<b style="color: #0000FF;"><%=demographic.getPatientStatus()%></b>
							<%} else {%>
                                                            <%=demographic.getPatientStatus()%>
							<%}%>
                                                        </span>
                                                                    </li>
                                                                    <li><span class="label">
							 	<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.PatientStatusDate"/>:</span>
                                                                        <span class="info">
                                <%
                                    String tmpDate = "";
                                    if (demographic.getPatientStatusDate() != null) {
                                        tmpDate = org.apache.commons.lang3.time.DateFormatUtils.ISO_DATE_FORMAT.format(demographic.getPatientStatusDate());
                                    }
                                %>
                                <%=tmpDate%></span>
                                                                    </li>

                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formChartNo"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getChartNo())%></span>
                                                                    </li>
                                                                    <% if (oscarProps.isPropertyActive("meditech_id")) { %>
                                                                    <li><span class="label">Meditech ID:</span>
                                                                        <span class="info"><%=OtherIdManager.getDemoOtherId(demographic_no, "meditech_id")%></span>
                                                                    </li>
                                                                    <% } %>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.cytolNum"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demoExt.get("cytolNum"))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDateJoined1"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getDateJoined())%></span>
                                                                    </li>
                                                                    <li>
                                                        <span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEndDate"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getEndDate())%></span>
                                                                    </li>

                                                                    <%if (!"true".equals(CarlosProperties.getInstance().getProperty("phu.hide", "false"))) { %>
                                                                    <li><span class="label">
								<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPHU"/>:</span>
                                                                        <span class="info"><c:out
                                                                                value="${phuName}"/></span>
                                                                    </li>
                                                                    <%} %>

                                                                </ul>
                                                            </div>

                                                            <div class="demographicSection" id="alert">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAlert"/></h3>
                                                                <b style="color: brown;"><%=alert%>
                                                                </b>
                                                                &nbsp;
                                                            </div>

                                                            <div class="demographicSection"
                                                                 id="rxInteractionWarningLevelDisplay">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.rxInteractionWarningLevel"/></h3>
                                                                <%
                                                                    // warningLevel already defined in preamble
                                                                    String warningLevelStr = "Not Specified";
                                                                    if (warningLevel.equals("1")) {
                                                                        warningLevelStr = "Low";
                                                                    }
                                                                    if (warningLevel.equals("2")) {
                                                                        warningLevelStr = "Medium";
                                                                    }
                                                                    if (warningLevel.equals("3")) {
                                                                        warningLevelStr = "High";
                                                                    }
                                                                    if (warningLevel.equals("4")) {
                                                                        warningLevelStr = "None";
                                                                    }
                                                                %>
                                                                <span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.rxInteractionWarningLevel"/>:</span>
                                                                <span class="info"><%=warningLevelStr%></span>


                                                            </div>

                                                            <div class="demographicSection" id="paperChartIndicator">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator"/></h3>
                                                                <%
                                                                    String archived = demoExt.get("paper_chart_archived");
                                                                    String archivedStr = "", archivedDate = "", archivedProgram = "";
                                                                    if ("YES".equals(archived)) {
                                                                        archivedStr = "Yes";
                                                                    }
                                                                    if ("NO".equals(archived)) {
                                                                        archivedStr = "No";
                                                                    }
                                                                    if (demoExt.get("paper_chart_archived_date") != null) {
                                                                        archivedDate = demoExt.get("paper_chart_archived_date");
                                                                    }
                                                                    if (demoExt.get("paper_chart_archived_program") != null) {
                                                                        archivedProgram = demoExt.get("paper_chart_archived_program");
                                                                    }
                                                                %>
                                                                <ul>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.archived"/>:</span>
                                                                        <span class="info"><%=archivedStr %></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.dateArchived"/>:</span>
                                                                        <span class="info"><%=archivedDate %></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.paperChartIndicator.programArchived"/>:</span>
                                                                        <span class="info"><%=archivedProgram %></span>
                                                                    </li>
                                                                </ul>
                                                            </div>

                                                                <%-- TOGGLE PRIVACY CONSENTS --%>
                                                            <oscar:oscarPropertiesCheck property="privateConsentEnabled"
                                                                                        value="true">

                                                                <div class="demographicSection" id="consent">
                                                                    <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.consent"/></h3>

                                                                    <ul>

                                                                        <%
                                                                            String[] privateConsentPrograms = oscarProps.getProperty("privateConsentPrograms", "").split(",");
                                                                            ProgramProvider pp = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());

                                                                            if (pp != null) {
                                                                                for (int x = 0; x < privateConsentPrograms.length; x++) {
                                                                                    if (privateConsentPrograms[x].equals(pp.getProgramId().toString())) {
                                                                                        showConsentsThisTime = true;
                                                                                    }
                                                                                }
                                                                            }

                                                                            if (showConsentsThisTime) { %>

                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.privacyConsent"/>:</span>
                                                                            <span class="info"><%=privacyConsent %></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.informedConsent"/>:</span>
                                                                            <span class="info"><%=informedConsent %></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.usConsent"/>:</span>
                                                                            <span class="info"><%=usSigned %></span>
                                                                        </li>


                                                                        <% } %>

                                                                            <%-- ENABLE THE NEW PATIENT CONSENT MODULE --%>
                                                                        <oscar:oscarPropertiesCheck
                                                                                property="USE_NEW_PATIENT_CONSENT_MODULE"
                                                                                value="true">

                                                                            <c:forEach items="${ patientConsents }"
                                                                                       var="patientConsent">
                                                                                <c:if test="${ not empty patientConsent.optout}">
                                                                                    <li>
                                                                                        <c:if test="${ patientConsent.consentType.active }">
                          			<span class="popup label"
                                          onmouseover="nhpup.popup(${ patientConsent.consentType.description },{'width':350} );">
										<c:out value="${ patientConsent.consentType.name }"/>
									</span>

                                                                                            <c:choose>
                                                                                                <c:when test="${ patientConsent.optout }">
                                                                                                    <span class="info"
                                                                                                          style="color:red;"> Opted Out:<c:out
                                                                                                            value="${ patientConsent.optoutDate }"/></span>
                                                                                                </c:when>

                                                                                                <c:otherwise>
                                                                                                    <span class="info"
                                                                                                          style="color:green;">Consented:<c:out
                                                                                                            value="${ patientConsent.consentDate }"/></span>
                                                                                                </c:otherwise>
                                                                                            </c:choose>

                                                                                        </c:if>
                                                                                    </li>
                                                                                </c:if>
                                                                            </c:forEach>
                                                                        </oscar:oscarPropertiesCheck>
                                                                            <%-- END ENABLE NEW PATIENT CONSENT MODULE --%>

                                                                    </ul>
                                                                </div>

                                                            </oscar:oscarPropertiesCheck>
                                                                <%-- END TOGGLE ALL PRIVACY CONSENTS --%>

                                                            <% // All cross-fragment variables (hasPrimaryCarePhysician, employmentStatus,
                                                                // hasPrimary, empStatus, hasDemoExt, hasHasPrimary, hasEmpStatus)
                                                                // already defined in preamble — re-compute values from config here
                                                                if (oscarProps.isPropertyActive("showPrimaryCarePhysicianCheck")) {
                                                                    hasHasPrimary = true;
                                                                    String key = hasPrimary.replace(" ", "");
                                                                    if (demoExt.get(key) != null && !demoExt.get(key).trim().isEmpty()) {
                                                                        hasPrimaryCarePhysician = demoExt.get(key);
                                                                    }
                                                                }
                                                                if (oscarProps.isPropertyActive("showEmploymentStatus")) {
                                                                    hasEmpStatus = true;
                                                                    String key = empStatus.replace(" ", "");
                                                                    if (demoExt.get(key) != null && !demoExt.get(key).trim().isEmpty()) {
                                                                        employmentStatus = demoExt.get(key);
                                                                    }
                                                                }

                                                                if (hasDemoExt || hasHasPrimary || hasEmpStatus) {
                                                            %>
                                                            <div id="special" class="demographicSection">
                                                                <h3>&nbsp;Custom</h3>
                                                                <ul>

                                                                    <%
                                                                        if (propDemoExt != null) {
                                                                            for (String propItem : propDemoExt) {
                                                                                String propValue = StringUtils.trimToEmpty(demoExt.get(propItem.replace(' ', '_')));
                                                                    %>
                                                                    <li>
                                                                        <%= Encode.forHtml(propItem + ": ") %>
                                                                        <strong><%=    Encode.forHtml(propValue) %>
                                                                        </strong>
                                                                    </li>
                                                                    <% }
                                                                    }

                                                                        if (hasHasPrimary) {
                                                                    %>
                                                                    <li>
                                                                        <%=hasPrimary%>:
                                                                        <strong><%=hasPrimaryCarePhysician%>
                                                                        </strong>
                                                                    </li>
                                                                    <% }
                                                                        if (hasEmpStatus) {
                                                                    %>
                                                                    <li>
                                                                        <%=empStatus%>: <strong><%=employmentStatus%>
                                                                    </strong>
                                                                    </li>
                                                                    <% }
                                                                    %>
                                                                </ul>
                                                            </div>
                                                            <%} %>

                                                        </div> <!-- end of left section -->

                                                        <div class="rightSection">
                                                            <div class="demographicSection" id="contactInformation">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgContactInfo"/></h3>
                                                                <ul>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneH"/>(<span
                                                                            class="popup"
                                                                            onmouseover="nhpup.popup(homePhoneHistory);"
                                                                            title="Home phone History">History</span>):</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getPhone())%> <%=StringUtils.trimToEmpty(demoExt.get("hPhoneExt"))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneW"/>(<span
                                                                            class="popup"
                                                                            onmouseover="nhpup.popup(workPhoneHistory);"
                                                                            title="Work phone History">History</span>):</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getPhone2())%> <%=StringUtils.trimToEmpty(demoExt.get("wPhoneExt"))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPhoneC"/>(<span
                                                                            class="popup"
                                                                            onmouseover="nhpup.popup(cellPhoneHistory);"
                                                                            title="cell phone History">History</span>):</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demoExt.get("demo_cell"))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPhoneComment"/>:</span>
                                                                        <span class="info"><%=Encode.forHtml(StringUtils.trimToEmpty(demoExt.get("phoneComment")))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formAddr"/>(<span
                                                                            class="popup"
                                                                            onmouseover="nhpup.popup(addressHistory);"
                                                                            title="Address History">History</span>):</span>
                                                                        <span class="info"><%=Encode.forHtml(StringUtils.trimToEmpty(demographic.getAddress()))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formCity"/>:</span>
                                                                        <span class="info"><%=Encode.forHtml(StringUtils.trimToEmpty(demographic.getCity()))%></span>
                                                                    </li>
                                                                    <li><span class="label">
							<% if (oscarProps.getProperty("demographicLabelProvince") == null) { %>
							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formProcvince"/> <% } else {
                                                                        out.print(oscarProps.getProperty("demographicLabelProvince"));
                                                                    } %>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(ISO36612.getInstance().translateCodeToHumanReadableString(demographic.getProvince()))%></span>
                                                                    </li>
                                                                    <li><span class="label">
							<% if (oscarProps.getProperty("demographicLabelPostal") == null) { %>
							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formPostal"/> <% } else {
                                                                        out.print(oscarProps.getProperty("demographicLabelPostal"));
                                                                    } %>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getPostal())%></span>
                                                                    </li>


                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialAddr"/>:</span>
                                                                        <span class="info"><%=Encode.forHtml(StringUtils.trimToEmpty(demographic.getResidentialAddress()))%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialCity"/>:</span>
                                                                        <span class="info"><%=Encode.forHtml(StringUtils.trimToEmpty(demographic.getResidentialCity()))%></span>
                                                                    </li>
                                                                    <li><span class="label">
														<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialProvince"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(ISO36612.getInstance().translateCodeToHumanReadableString(demographic.getResidentialProvince()))%></span>
                                                                    </li>
                                                                    <li><span class="label">

							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResidentialPostal"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getResidentialPostal())%></span>
                                                                    </li>


                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEmail"/>:</span>
                                                                        <span class="info"><%=demographic.getEmail() != null ? demographic.getEmail() : ""%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNewsLetter"/>:</span>
                                                                        <span class="info"><%=demographic.getNewsletter() != null ? demographic.getNewsletter() : "Unknown"%></span>
                                                                    </li>
                                                                </ul>
                                                            </div>

                                                            <div class="demographicSection" id="healthInsurance">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgHealthIns"/></h3>
                                                                <ul>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHin"/>:</span>
                                                                        <span class="info"><%=StringUtils.trimToEmpty(demographic.getHin())%>
							&nbsp; <%=StringUtils.trimToEmpty(demographic.getVer())%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCType"/>:</span>
                                                                        <span class="info"><%=demographic.getHcType() == null ? "" : demographic.getHcType() %></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formEFFDate"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getEffDate())%></span>
                                                                    </li>
                                                                    <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formHCRenewDate"/>:</span>
                                                                        <span class="info"><%=MyDateFormat.getMyStandardDate(demographic.getHcRenewDate())%></span>
                                                                    </li>
                                                                </ul>
                                                                    <%-- TOGGLE FIRST NATIONS MODULE --%>
                                                                <oscar:oscarPropertiesCheck value="true"
                                                                                            defaultVal="false"
                                                                                            property="FIRST_NATIONS_MODULE">

                                                                    <jsp:include page="/demographic/displayFirstNationsModule.jsp"
                                                                                 flush="false">
                                                                        <jsp:param name="demo"
                                                                                   value="<%= Encode.forHtmlAttribute(demographic_no) %>"/>
                                                                        <jsp:param name="fncommunity"
                                                                                   value="${fncommunity}"/>
                                                                    </jsp:include>

                                                                </oscar:oscarPropertiesCheck>
                                                                    <%-- END TOGGLE FIRST NATIONS MODULE --%>
                                                            </div>

                                                                <%-- TOGGLE WORKFLOW_ENHANCE - SHOWS PATIENTS INTERNAL PROVIDERS AND RELATED SCHEDULE AVAIL --%>

                                                            <oscar:oscarPropertiesCheck value="true"
                                                                                        property="workflow_enhance">
                                                                <div class="demographicSection">
                                                                    <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgInternalProviders"/></h3>
                                                                    <div style="background-color: #EEEEFF;">
                                                                        <ul>
                                                                            <%-- timeStrToMins() is now in DemographicEditHelper and available via preamble --%>
                                                                            <% // ===== quick appointment booking =====
                                                                                // database access object, data objects for looking things up


                                                                                String[] twoLetterDate = {"", "Su", "Mo", "Tu", "We", "Th", "Fr", "Sa"};

                                                                                // build templateMap, which maps template codes to their associated duration
                                                                                Map<String, String> templateMap = new HashMap<String, String>();
                                                                                for (ScheduleTemplateCode stc : scheduleTemplateCodeDao.findTemplateCodes()) {
                                                                                    templateMap.put(String.valueOf(stc.getCode()), stc.getDuration());
                                                                                }


                                                                                // build list of providers associated with this patient
                                                                                Map<String, Map<String, Map<String, String>>> provMap = new HashMap<String, Map<String, Map<String, String>>>();
                                                                                if (demographic != null) {
                                                                                    provMap.put("doctor", new HashMap<String, Map<String, String>>());
                                                                                    provMap.get("doctor").put("prov_no", new HashMap<String, String>());
                                                                                    provMap.get("doctor").get("prov_no").put("no", demographic.getProviderNo());
                                                                                }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(resident, ""))) {
                                                                                    provMap.put("prov1", new HashMap<String, Map<String, String>>());
                                                                                    provMap.get("prov1").put("prov_no", new HashMap<String, String>());
                                                                                    provMap.get("prov1").get("prov_no").put("no", resident);
                                                                                }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(midwife, ""))) {
                                                                                    provMap.put("prov2", new HashMap<String, Map<String, String>>());
                                                                                    provMap.get("prov2").put("prov_no", new HashMap<String, String>());
                                                                                    provMap.get("prov2").get("prov_no").put("no", midwife);
                                                                                }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(nurse, ""))) {
                                                                                    provMap.put("prov3", new HashMap<String, Map<String, String>>());
                                                                                    provMap.get("prov3").put("prov_no", new HashMap<String, String>());
                                                                                    provMap.get("prov3").get("prov_no").put("no", nurse);
                                                                                }

                                                                                // precompute all data for the providers associated with this patient
                                                                                for (String thisProv : provMap.keySet()) {

                                                                                    String thisProvNo = provMap.get(thisProv).get("prov_no").get("no");

                                                                                    // starting tomorrow, look for available appointment slots
                                                                                    Calendar qApptCal = new GregorianCalendar();
                                                                                    qApptCal.add(Calendar.DATE, 1);
                                                                                    int numDays = 0;
                                                                                    int maxLookahead = 90;

                                                                                    while ((numDays < 5) && (maxLookahead > 0)) {
                                                                                        int qApptYear = qApptCal.get(Calendar.YEAR);
                                                                                        int qApptMonth = (qApptCal.get(Calendar.MONTH) + 1);
                                                                                        int qApptDay = qApptCal.get(Calendar.DAY_OF_MONTH);
                                                                                        String qApptWkDay = twoLetterDate[qApptCal.get(Calendar.DAY_OF_WEEK)];
                                                                                        String qCurDate = qApptYear + "-" + qApptMonth + "-" + qApptDay;

                                                                                        // get timecode string template associated with this day, number of minutes each slot represents
                                                                                        ScheduleTemplateDao dao = SpringUtils.getBean(ScheduleTemplateDao.class);
                                                                                        List<Object> timecodeResult = dao.findTimeCodeByProviderNo2(thisProvNo, ConversionUtils.fromDateString(qCurDate));

                                                                                        // if theres a template on this day, continue
                                                                                        if (!timecodeResult.isEmpty()) {

                                                                                            String timecode = StringUtils.trimToEmpty(String.valueOf(timecodeResult.get(0)));

                                                                                            int timecodeInterval = 1440 / timecode.length();

                                                                                            // build schedArr, which has 1s where template slots are
                                                                                            int[] schedArr = new int[timecode.length()];
                                                                                            String schedChar;
                                                                                            for (int i = 0; i < timecode.length(); i++) {
                                                                                                schedChar = "" + timecode.charAt(i);
                                                                                                if (!schedChar.equals("_")) {
                                                                                                    if (templateMap.get("" + timecode.charAt(i)) != null) {
                                                                                                        schedArr[i] = 1;
                                                                                                    }
                                                                                                }
                                                                                            }

                                                                                            // get list of appointments on this day
                                                                                            int start_index, end_index;
                                                                                            OscarAppointmentDao apptDao = SpringUtils.getBean(OscarAppointmentDao.class);
                                                                                            // put 0s in schedArr where appointments are
                                                                                            for (Appointment appt : apptDao.findByProviderAndDayandNotStatuses(thisProvNo, ConversionUtils.fromDateString(qCurDate), new String[]{"N", "C"})) {
                                                                                                start_index = timeStrToMins(StringUtils.trimToEmpty(ConversionUtils.toTimeString(appt.getStartTime()))) / timecodeInterval;
                                                                                                end_index = timeStrToMins(StringUtils.trimToEmpty(ConversionUtils.toTimeString(appt.getEndTime()))) / timecodeInterval;

                                                                                                // very late appts may push us past the time range we care about
                                                                                                // trying to invalidate these times will lead to a ArrayIndexOutOfBoundsException
                                                                                                // fix this so we stay within the bounds of schedArr
                                                                                                if (end_index > (timecode.length() - 1)) {
                                                                                                    end_index = timecode.length() - 1;
                                                                                                }

                                                                                                // protect against the dual case as well
                                                                                                if (start_index < 0) {
                                                                                                    start_index = 0;
                                                                                                }

                                                                                                // handle appts of duration longer than template interval
                                                                                                for (int i = start_index; i <= end_index; i++) {
                                                                                                    schedArr[i] = 0;
                                                                                                }
                                                                                            }

                                                                                            // list slots that can act as start times for appointments of template specified length
                                                                                            boolean enoughRoom;
                                                                                            boolean validDay = false;
                                                                                            int templateDuration, startHour, startMin;
                                                                                            String startTimeStr, endTimeStr, sortDateStr;
                                                                                            String timecodeChar;
                                                                                            for (int i = 0; i < timecode.length(); i++) {
                                                                                                if (schedArr[i] == 1) {
                                                                                                    enoughRoom = true;
                                                                                                    timecodeChar = "" + timecode.charAt(i);
                                                                                                    templateDuration = Integer.parseInt(templateMap.get(timecodeChar));
                                                                                                    for (int n = 0; n < templateDuration / timecodeInterval; n++) {
                                                                                                        if (((i + n) < (schedArr.length - 1)) && (schedArr[i + n] != 1)) {
                                                                                                            enoughRoom = false;
                                                                                                        }
                                                                                                    }
                                                                                                    if (enoughRoom) {
                                                                                                        validDay = true;
                                                                                                        sortDateStr = qApptYear + "-" + String.format("%02d", qApptMonth) + "-" + String.format("%02d", qApptDay);
                                                                                                        if (!provMap.get(thisProv).containsKey(sortDateStr + "," + qApptWkDay + " " + qApptMonth + "-" + qApptDay)) {
                                                                                                            provMap.get(thisProv).put(sortDateStr + "," + qApptWkDay + " " + qApptMonth + "-" + qApptDay, new HashMap<String, String>());
                                                                                                        }
                                                                                                        startHour = i * timecodeInterval / 60;
                                                                                                        startMin = i * timecodeInterval % 60;
                                                                                                        startTimeStr = String.format("%02d", startHour) + ":" + String.format("%02d", startMin);
                                                                                                        endTimeStr = String.format("%02d", startHour) + ":" + String.format("%02d", startMin + timecodeInterval - 1);

                                                                                                        provMap.get(thisProv).get(sortDateStr + "," + qApptWkDay + " " + qApptMonth + "-" + qApptDay).put(startTimeStr + "," + timecodeChar, request.getContextPath() + "/appointment/addappointment.jsp?demographic_no=" + demographic.getDemographicNo() + "&name=" + URLEncoder.encode(demographic.getLastName() + "," + demographic.getFirstName(), "UTF-8") + "&provider_no=" + thisProvNo + "&bFirstDisp=true&year=" + qApptYear + "&month=" + qApptMonth + "&day=" + qApptDay + "&start_time=" + startTimeStr + "&end_time=" + endTimeStr + "&duration=" + templateDuration + "&search=true");
                                                                                                    }
                                                                                                }
                                                                                            }

                                                                                            if (validDay) {
                                                                                                numDays++;
                                                                                            }
                                                                                        }

                                                                                        // look at the next day
                                                                                        qApptCal.add(Calendar.DATE, 1);
                                                                                        maxLookahead--;
                                                                                    }
                                                                                }
                                                                            %>
                                                                            <% if (demographic.getProviderNo() != null) { %>
                                                                            <li>
                                                                                <% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                                                                    out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                                                                                } else { %>
                                                                                <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formDoctor"/>
                                                                                <% } %>:
                                                                                <b><%=providerBean.getProperty(demographic.getProviderNo(), "")%>
                                                                                </b>
                                                                                <% // ===== quick appointment booking for doctor =====
                                                                                    if (provMap.get("doctor") != null) {
                                                                                %><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%
                                                                                boolean firstBar = true;
                                                                                ArrayList<String> sortedDays = new ArrayList(provMap.get("doctor").keySet());
                                                                                Collections.sort(sortedDays);
                                                                                for (String thisDate : sortedDays) {
                                                                                    if (!thisDate.equals("prov_no")) {
                                                                                        if (!firstBar) {%>|<%
                                                                                }
                                                                                ;
                                                                                firstBar = false;
                                                                                String[] thisDateArr = thisDate.split(",");
                                                                                String thisDispDate = thisDateArr[1];
                                                                            %>
                                                                                <a style="text-decoration: none;"
                                                                                   href="#"
                                                                                   onclick="return !showAppt('_doctor_<%=thisDateArr[0]%>', event);"><b><%=thisDispDate%>
                                                                                </b></a>
                                                                                <div id='menu_doctor_<%=thisDateArr[0]%>'
                                                                                     class='menu'
                                                                                     onclick='event.cancelBubble = true;'>
                                                                                    <h3 style='text-align: center; color: black;'>
                                                                                        Available Appts.
                                                                                        (<%=thisDispDate%>)</h3>
                                                                                    <ul>
                                                                                        <%
                                                                                            ArrayList<String> sortedTimes = new ArrayList(provMap.get("doctor").get(thisDate).keySet());
                                                                                            Collections.sort(sortedTimes);
                                                                                            for (String thisTime : sortedTimes) {
                                                                                                String[] thisTimeArr = thisTime.split(",");
                                                                                        %>
                                                                                        <li>[<%=thisTimeArr[1]%>] <a
                                                                                                href="#"
                                                                                                onClick="popupPage(400,780,'<%=provMap.get("doctor").get(thisDate).get(thisTime) %>');return false;"><%= thisTimeArr[0] %>
                                                                                        </a></li>
                                                                                        <%
                                                                                            }
                                                                                        %></ul>
                                                                                </div>
                                                                                <% }
                                                                                }
                                                                                }
                                                                                %>
                                                                            </li>
                                                                            <% }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(resident, ""))) { %>
                                                                            <li>Alt. Provider 1:
                                                                                <b><%=providerBean.getProperty(resident, "")%>
                                                                                </b>
                                                                                <% // ===== quick appointment booking for prov1 =====
                                                                                    if (provMap.get("prov1") != null) {
                                                                                %><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%
                                                                                    boolean firstBar = true;
                                                                                    ArrayList<String> sortedDays = new ArrayList(provMap.get("prov1").keySet());
                                                                                    Collections.sort(sortedDays);
                                                                                    for (String thisDate : sortedDays) {
                                                                                        if (!thisDate.equals("prov_no")) {
                                                                                            if (!firstBar) {%>|<%
                                                                                    }
                                                                                    ;
                                                                                    firstBar = false;
                                                                                    String[] thisDateArr = thisDate.split(",");
                                                                                    String thisDispDate = thisDateArr[1];
                                                                                %>
                                                                                <a style="text-decoration: none;"
                                                                                   href="#"
                                                                                   onclick="return !showAppt('_prov1_<%=thisDateArr[0]%>', event);"><b><%=thisDispDate%>
                                                                                </b></a>
                                                                                <div id='menu_prov1_<%=thisDateArr[0]%>'
                                                                                     class='menu'
                                                                                     onclick='event.cancelBubble = true;'>
                                                                                    <h3 style='text-align: center; color: black;'>
                                                                                        Available Appts.
                                                                                        (<%=thisDispDate%>)</h3>
                                                                                    <ul>
                                                                                        <%
                                                                                            ArrayList<String> sortedTimes = new ArrayList(provMap.get("prov1").get(thisDate).keySet());
                                                                                            Collections.sort(sortedTimes);
                                                                                            for (String thisTime : sortedTimes) {
                                                                                                String[] thisTimeArr = thisTime.split(",");
                                                                                        %>
                                                                                        <li>[<%=thisTimeArr[1]%>] <a
                                                                                                href="#"
                                                                                                onClick="popupPage(400,780,'<%=provMap.get("prov1").get(thisDate).get(thisTime) %>');return false;"><%= thisTimeArr[0] %>
                                                                                        </a></li>
                                                                                        <%
                                                                                            }
                                                                                        %></ul>
                                                                                </div>
                                                                                <%
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                %>
                                                                            </li>
                                                                            <% }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(midwife, ""))) { %>
                                                                            <li>Alt. Provider 2:
                                                                                <b><%=providerBean.getProperty(midwife, "")%>
                                                                                </b>
                                                                                <% // ===== quick appointment booking for prov2 =====
                                                                                    if (provMap.get("prov2") != null) {
                                                                                %><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%
                                                                                    boolean firstBar = true;
                                                                                    ArrayList<String> sortedDays = new ArrayList(provMap.get("prov2").keySet());
                                                                                    Collections.sort(sortedDays);
                                                                                    for (String thisDate : sortedDays) {
                                                                                        if (!thisDate.equals("prov_no")) {
                                                                                            if (!firstBar) {%>|<%
                                                                                    }
                                                                                    ;
                                                                                    firstBar = false;
                                                                                    String[] thisDateArr = thisDate.split(",");
                                                                                    String thisDispDate = thisDateArr[1];
                                                                                %>
                                                                                <a style="text-decoration: none;"
                                                                                   href="#"
                                                                                   onclick="return !showAppt('_prov2_<%=thisDateArr[0]%>', event);"><b><%=thisDispDate%>
                                                                                </b></a>
                                                                                <div id='menu_prov2_<%=thisDateArr[0]%>'
                                                                                     class='menu'
                                                                                     onclick='event.cancelBubble = true;'>
                                                                                    <h3 style='text-align: center; color: black;'>
                                                                                        Available Appts.
                                                                                        (<%=thisDispDate%>)</h3>
                                                                                    <ul>
                                                                                        <%
                                                                                            ArrayList<String> sortedTimes = new ArrayList(provMap.get("prov2").get(thisDate).keySet());
                                                                                            Collections.sort(sortedTimes);
                                                                                            for (String thisTime : sortedTimes) {
                                                                                                String[] thisTimeArr = thisTime.split(",");
                                                                                        %>
                                                                                        <li>[<%=thisTimeArr[1]%>] <a
                                                                                                href="#"
                                                                                                onClick="popupPage(400,780,'<%=provMap.get("prov2").get(thisDate).get(thisTime) %>');return false;"><%= thisTimeArr[0] %>
                                                                                        </a></li>
                                                                                        <%
                                                                                            }
                                                                                        %></ul>
                                                                                </div>
                                                                                <%
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                %>
                                                                            </li>
                                                                            <% }
                                                                                if (StringUtils.isNotEmpty(providerBean.getProperty(nurse, ""))) { %>
                                                                            <li>Alt. Provider 3:
                                                                                <b><%=providerBean.getProperty(nurse, "")%>
                                                                                </b>
                                                                                <% // ===== quick appointment booking for prov3 =====
                                                                                    if (provMap.get("prov3") != null) {
                                                                                %><br/>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;<%
                                                                                    boolean firstBar = true;
                                                                                    ArrayList<String> sortedDays = new ArrayList(provMap.get("prov3").keySet());
                                                                                    Collections.sort(sortedDays);
                                                                                    for (String thisDate : sortedDays) {
                                                                                        if (!thisDate.equals("prov_no")) {
                                                                                            if (!firstBar) {%>|<%
                                                                                    }
                                                                                    ;
                                                                                    firstBar = false;
                                                                                    String[] thisDateArr = thisDate.split(",");
                                                                                    String thisDispDate = thisDateArr[1];
                                                                                %>
                                                                                <a style="text-decoration: none;"
                                                                                   href="#"
                                                                                   onclick="return !showAppt('_prov3_<%=thisDateArr[0]%>', event);"><b><%=thisDispDate%>
                                                                                </b></a>
                                                                                <div id='menu_prov3_<%=thisDateArr[0]%>'
                                                                                     class='menu'
                                                                                     onclick='event.cancelBubble = true;'>
                                                                                    <h3 style='text-align: center; color: black;'>
                                                                                        Available Appts.
                                                                                        (<%=thisDispDate%>)</h3>
                                                                                    <ul>
                                                                                        <%
                                                                                            ArrayList<String> sortedTimes = new ArrayList(provMap.get("prov3").get(thisDate).keySet());
                                                                                            Collections.sort(sortedTimes);
                                                                                            for (String thisTime : sortedTimes) {
                                                                                                String[] thisTimeArr = thisTime.split(",");
                                                                                        %>
                                                                                        <li>[<%=thisTimeArr[1]%>] <a
                                                                                                href="#"
                                                                                                onClick="popupPage(400,780,'<%=provMap.get("prov3").get(thisDate).get(thisTime) %>');return false;"><%= thisTimeArr[0] %>
                                                                                        </a></li>
                                                                                        <%
                                                                                            }
                                                                                        %></ul>
                                                                                </div>
                                                                                <%
                                                                                            }
                                                                                        }
                                                                                    }
                                                                                %>
                                                                            </li>
                                                                            <% } %>
                                                                        </ul>
                                                                    </div>
                                                                </div>

                                                                <%--} --%>
                                                            </oscar:oscarPropertiesCheck>
                                                                <%-- END TOGGLE WORKFLOW_ENHANCE --%>

                                                                <%-- AUTHOR DENNIS WARREN O/A COLCAMEX RESOURCES --%>
                                                            <oscar:oscarPropertiesCheck
                                                                    property="DEMOGRAPHIC_PATIENT_HEALTH_CARE_TEAM"
                                                                    value="true">
                                                                <jsp:include page="/demographic/displayHealthCareTeam.jsp">
                                                                    <jsp:param name="demographicNo"
                                                                               value="<%= Encode.forHtmlAttribute(demographic_no) %>"/>
                                                                </jsp:include>
                                                            </oscar:oscarPropertiesCheck>
                                                                <%-- TOGGLE OFF PATIENT CLINIC STATUS --%>
                                                            <oscar:oscarPropertiesCheck
                                                                    property="DEMOGRAPHIC_PATIENT_CLINIC_STATUS"
                                                                    value="true">

                                                                <div class="demographicSection"
                                                                     id="patientClinicStatus">
                                                                    <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.msgPatientClinicStatus"/></h3>
                                                                    <ul>
                                                                        <li><span class="label">
							<% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                            } else { %>
							<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMRP"/>
                                                    <% } %>:</span><span class="info">
                                                    <%if (demographic != null && demographic.getProviderNo() != null) {%>
                                                           <%=providerBean.getProperty(demographic.getProviderNo(), "")%>
                                                    <%}%>
                                                    </span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNurse"/>:</span><span
                                                                                class="info"><%=providerBean.getProperty(nurse == null ? "" : nurse, "")%></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formMidwife"/>:</span><span
                                                                                class="info"><%=providerBean.getProperty(midwife == null ? "" : midwife, "")%></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formResident"/>:</span>
                                                                            <span class="info"><%=providerBean.getProperty(resident == null ? "" : resident, "")%></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRefDoc"/>:</span><span
                                                                                class="info"><%=rd%></span>
                                                                        </li>
                                                                        <li><span class="label"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formRefDocNo"/>:</span><span
                                                                                class="info"><%=rdohip%></span>
                                                                        </li>
                                                                    </ul>
                                                                </div>

                                                            </oscar:oscarPropertiesCheck>

                                                                <%-- END TOGGLE OFF PATIENT CLINIC STATUS --%>

                                                                <%-- END AUTHOR DENNIS WARREN O/A COLCAMEX RESOURCES --%>


                                                            <div class="demographicSection" id="notes">
                                                                <h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.formNotes"/></h3>

                                                                <%=Encode.forHtml(notes)%>&nbsp;
                                                            </div>

                                                                <%-- TOGGLED OFF PROGRAM ADMISSIONS --%>
                                                            <oscar:oscarPropertiesCheck
                                                                    property="DEMOGRAPHIC_PROGRAM_ADMISSIONS"
                                                                    value="true">
                                                                <div class="demographicSection" id="programs">
                                                                    <h3>Programs</h3>
                                                                    <ul>
                                                                        <%
                                                                            for (Admission adm : serviceAdmissions) {
                                                                        %>
                                                                        <li><span class="label">Service:</span><span
                                                                                class="info"><%=adm.getProgramName()%></span>
                                                                        </li>

                                                                        <%
                                                                            }
                                                                        %>
                                                                    </ul>

                                                                </div>
                                                            </oscar:oscarPropertiesCheck>
                                                                <%-- TOGGLED OFF PROGRAM ADMISSIONS --%>

                                                        </div>

                                                    </div>
                                                </div>
                                                <!--newEnd-->

