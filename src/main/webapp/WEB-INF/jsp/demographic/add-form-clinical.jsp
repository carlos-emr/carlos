<%-- add-form-clinical.jsp: Care team, roster, consent, programs, submit (from demographicaddarecordhtm.jsp lines 1878-2490) --%>
<%@ page import="java.util.*" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="io.github.carlos_emr.AppointmentMainBean" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.Misc" %>
<%@ page import="io.github.carlos_emr.carlos.commn.Gender" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.*" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.ProvinceNames" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.Util" %>
<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.PatientConsentManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.ProgramManager2" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider" %>
<%@ page import="io.github.carlos_emr.carlos.PMmodule.service.ProgramManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.waitinglist.WaitingList" %>

<%@ taglib uri="jakarta.tags.functions" prefix="fn" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${ pageContext.request.contextPath }"/>

<%-- Retrieve variables from request attributes (set by DemographicAdd2Action) --%>
<%
    String curUser_no = (String) request.getAttribute("curUser_no");
    CarlosProperties oscarProps = (CarlosProperties) request.getAttribute("oscarProps");
    String prov = (String) request.getAttribute("prov");
    String curYear = (String) request.getAttribute("curYear");
    String curMonth = (String) request.getAttribute("curMonth");
    String curDay = (String) request.getAttribute("curDay");
    String billingCentre = (String) request.getAttribute("billingCentre");
    String defaultCity = (String) request.getAttribute("defaultCity");
    List<CountryCode> countryList = (List<CountryCode>) request.getAttribute("countryList");
    CountryCodeDao ccDAO = (CountryCodeDao) request.getAttribute("ccDAO");
    UserPropertyDAO userPropertyDAO = (UserPropertyDAO) request.getAttribute("userPropertyDAO");
    String HCType = (String) request.getAttribute("HCType");
    String defaultProvince = (String) request.getAttribute("defaultProvince");
    ProvinceNames pNames = (ProvinceNames) request.getAttribute("pNames");
    boolean privateConsentEnabled = Boolean.TRUE.equals(request.getAttribute("privateConsentEnabled"));
    String today = (String) request.getAttribute("today");
    List<Provider> doctors = (List<Provider>) request.getAttribute("doctors");
    List<Provider> nurses = (List<Provider>) request.getAttribute("nurses");
    List<Provider> midwifes = (List<Provider>) request.getAttribute("midwifes");
    ProviderDao providerDao = (ProviderDao) request.getAttribute("providerDao");
    DemographicDao demographicDao = (DemographicDao) request.getAttribute("demographicDao");
    WaitingListNameDao waitingListNameDao = (WaitingListNameDao) request.getAttribute("waitingListNameDao");
    EFormDao eformDao = (EFormDao) request.getAttribute("eformDao");
    ProgramDao programDao = (ProgramDao) request.getAttribute("programDao");
    ProgramManager pm = (ProgramManager) request.getAttribute("programManager");
    ProgramManager2 programManager2 = (ProgramManager2) request.getAttribute("programManager2");
    ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) request.getAttribute("professionalSpecialistDao");

    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    int nStrShowLen = 20;
    CarlosProperties props = oscarProps;
    String chartNoVal = "";
    java.util.Properties oscarVariables = oscarProps;
    java.util.Locale vLocale = request.getLocale();
    String searchMode = request.getParameter("search_mode");
    String keyWord = request.getParameter("keyword");
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<jsp:useBean id="apptMainBean" class="io.github.carlos_emr.AppointmentMainBean" scope="session"/>

<%-- === Original content === --%>
<%-- Completes the cross-file row opened at the end of add-form-personal.jsp --%>
                            <div class="col-sm-2 text-end" id="demoDoctorLbl">
                                <label class="fw-bold col-form-label py-0">
                                    <% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                        out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                                    } else { %>
                                    <fmt:message key="demographic.demographicaddrecordhtm.formDoctor"/>
                                    <% } %>:
                                </label>
                            </div>
                            <div class="col-sm-4" id="demoDoctorCell">
                                <select name="staff" class="form-select">
                                    <option value=""></option>
                                    <%
                                        for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
                                            String docProviderNo = p.getProviderNo();
                                    %>
                                    <option id="doc<%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(docProviderNo) %>" value="<%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(docProviderNo) %>"><carlos:encode value='<%= p.getFormattedName() %>' context="html"/></option>                                    <%
                                        }
                                    %>

                                </select>
                            </div>
                            <div class="col-sm-2 text-end" id="nurseLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formNurse"/>:</label>
                            </div>
                            <div class="col-sm-4" id="nurseCell">
                                <select name="cust1" class="form-select">
                                    <option value=""></option>
                                    <%
                                        for (Provider p : providerDao.getActiveProvidersByRole("nurse")) {
                                    %>
                                    <option value="<%=p.getProviderNo()%>"><carlos:encode value='<%= p.getFormattedName() %>' context="html"/></option>
                                    <%
                                        }
                                    %>
                                </select>
                            </div>
                        </div><%-- end cross-file row --%>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="midwifeLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formMidwife"/>:</label>
                            </div>
                            <div class="col-sm-4" id="midwifeCell">
                                <select name="cust4" class="form-select">
                                    <option value=""></option>
                                    <%
                                        for (Provider p : providerDao.getActiveProvidersByRole("midwife")) {
                                    %>
                                    <option value="<%=p.getProviderNo()%>"><carlos:encode value='<%= p.getFormattedName() %>' context="html"/></option>
                                    <%
                                        }
                                    %>
                                </select>
                            </div>
                            <div class="col-sm-2 text-end" id="residentLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formResident"/>:</label>
                            </div>
                            <div class="col-sm-4" id="residentCell">
                                <select name="cust2" class="form-select">
                                    <option value=""></option>
                                    <%
                                        for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
                                    %>
                                    <option value="<%=p.getProviderNo()%>"><carlos:encode value='<%= p.getFormattedName() %>' context="html"/></option>
                                    <%
                                        }
                                    %>
                                </select>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center" id="rowWithReferralDoc">
                            <div class="col-sm-2 text-end" id="referralDocLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formReferalDoctor"/>:</label>
                            </div>
                            <div class="col-sm-4" id="referralDocCell">
                                <% if ("true".equals(oscarProps.getProperty("isMRefDocSelectList", ""))) {
                                    // drop down list
                                    Properties prop = null;
                                    Vector vecRef = new Vector();

                                    List<ProfessionalSpecialist> specialists = professionalSpecialistDao.findAll();
                                    for (ProfessionalSpecialist specialist : specialists) {
                                        if (specialist != null && specialist.getReferralNo() != null && !specialist.getReferralNo().equals("")) {
                                            prop = new Properties();
                                            prop.setProperty("referral_no", specialist.getReferralNo());
                                            prop.setProperty("last_name", specialist.getLastName());
                                            prop.setProperty("first_name", specialist.getFirstName());
                                            vecRef.add(prop);
                                        }
                                    }
                                %>
                                <select name="r_doctor" onChange="changeRefDoc()" class="form-select">
                                    <option value=""></option>
                                    <% for (int k = 0; k < vecRef.size(); k++) {
                                        prop = (Properties) vecRef.get(k);
                                    %>
                                    <option
                                            value="<%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(prop.getProperty("last_name") + "," + prop.getProperty("first_name")) %>"
                                            data-referral-no="<%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlAttribute(prop.getProperty("referral_no", "")) %>">
                                      <%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtml(Misc.getShortStr((prop.getProperty("last_name") + "," + prop.getProperty("first_name")), "", nStrShowLen)) %>
                                    </option>
                                    <% } %>
                                </select>
                                <script>
                                    function changeRefDoc() {
                                        var option = document.forms[1].r_doctor.options[document.forms[1].r_doctor.selectedIndex];
                                        document.forms[1].r_doctor_ohip.value = option ? (option.getAttribute("data-referral-no") || "") : "";
                                    }
                                </script>
                                <% } else { %>
                                <input type="text" name="r_doctor" maxlength="40" class="form-control" value="">
                                <% } %>
                            </div>
                            <div class="col-sm-2 text-end" id="referralDocNoLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formReferalDoctorN"/>:</label>
                            </div>
                            <div class="col-sm-4" id="referralDocNoCell">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" name="r_doctor_ohip" maxlength="6" class="form-control">
                                    <% if ("ON".equals(prov)) { %>
                                    <a href="javascript:referralScriptAttach2('r_doctor_ohip','r_doctor')"><fmt:message key="demographic.demographiceditdemographic.btnSearch"/> #</a>
                                    <% } %>
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="rosterStatusLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPCNRosterStatus"/>:</label>
                            </div>
                            <div class="col-sm-4" id="rosterStatus">
                                <div class="d-flex gap-1 align-items-center">
                                    <select id="roster_status" name="roster_status" class="form-select">
                                        <option value=""></option>
                                        <option value="RO"><fmt:message key="demographic.demographicaddrecordhtm.RO-rostered"/></option>
                                        <option value="FS"><fmt:message key="demographic.demographicaddrecordhtm.FS-feeforservice"/></option>
                                        <%
                                            for (String status : demographicDao.getRosterStatuses()) {%>
                                        <option value="<%= SafeEncode.forHtmlAttribute(status) %>"><%= SafeEncode.forHtml(status) %></option>
                                        <% } // end while %>
                                    </select>
                                    <input type="button" onClick="newStatus1();" class="btn btn-outline-secondary btn-sm"
                                           value="<fmt:message key="demographic.demographicaddrecordhtm.AddNewRosterStatus"/>"/>
                                </div>
                            </div>
                            <div class="col-sm-2 text-end" id="rosterDateLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPCNDateJoined"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" placeholder="<fmt:message key="yyyy-mm-dd"/>"
                                           name="roster_date" id="roster_date"
                                           class="form-control"
                                           value="<%=today %>" size="12"
                                           onchange="parseDateField('roster_date');">
                                    <img src="<%= request.getContextPath() %>/images/cal.gif" id="roster_date_cal">
                                    <input type="hidden" name="roster_date_year">
                                    <input type="hidden" name="roster_date_month">
                                    <input type="hidden" name="roster_date_date">
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="rosterEnrolledToLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formRosterEnrolledTo"/>:</label>
                            </div>
                            <div class="col-sm-4" id="rosterEnrolledTo">
                                <select id="roster_enrolled_to" name="roster_enrolled_to" class="form-select">
                                    <option value=""></option>
                                    <%
                                        for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
                                            String docProviderNo = p.getProviderNo();
                                    %>
                                    <option id="<%= SafeEncode.forHtmlAttribute(docProviderNo) %>" value="<%= SafeEncode.forHtmlAttribute(docProviderNo) %>"><%= SafeEncode.forHtml(p.getFormattedName()) %></option>
                                    <%
                                        }
                                    %>
                                    <option value=""></option>
                                </select>
                            </div>
                            <div class="col-sm-2 text-end" id="chartNoLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formChartNo"/>:</label>
                            </div>
                            <div class="col-sm-4" id="chartNo">
                                <input type="text" id="chart_no" name="chart_no" class="form-control"
                                       value="<carlos:encode value='<%= chartNoVal %>' context="htmlAttribute"/>">
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="ptStatusLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPatientStatus"/>:</label>
                            </div>
                            <div class="col-sm-4" id="ptStatusCell">
                                <div class="d-flex gap-1 align-items-center">
                                    <select id="patient_status" name="patient_status" class="form-select">
                                        <option value="AC"><fmt:message key="demographic.demographicaddrecordhtm.AC-Active"/></option>
                                        <option value="IN"><fmt:message key="demographic.demographicaddrecordhtm.IN-InActive"/></option>
                                        <option value="DE"><fmt:message key="demographic.demographicaddrecordhtm.DE-Deceased"/></option>
                                        <option value="MO"><fmt:message key="demographic.demographicaddrecordhtm.MO-Moved"/></option>
                                        <option value="FI"><fmt:message key="demographic.demographicaddrecordhtm.FI-Fired"/></option>
                                        <%
                                            for (String status : demographicDao.search_ptstatus()) { %>
                                        <option value="<%= SafeEncode.forHtmlAttribute(status) %>"><%= SafeEncode.forHtml(status) %></option>
                                        <% } // end while %>
                                    </select>
                                    <input type="button" onClick="newStatus();" class="btn btn-outline-secondary btn-sm"
                                           value="<fmt:message key="demographic.demographicaddrecordhtm.AddNewPatient"/>">
                                </div>
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.addFormClinical.labelPatientStatusDate"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" placeholder="<fmt:message key="yyyy-mm-dd"/>"
                                           name="patient_status_date" id="patient_status_date"
                                           class="form-control"
                                           value="<%=today %>" size="12">
                                    <img src="<%= request.getContextPath() %>/images/cal.gif" id="patient_status_date_cal">
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="joinDateLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formDateJoined"/>:</label>
                            </div>
                            <div class="col-sm-4" id="joinDateCell">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" placeholder="<fmt:message key="yyyy-mm-dd"/>"
                                           name="date_joined" id="date_joined"
                                           class="form-control"
                                           value="<%=today %>" size="12"
                                           onchange="parseDateField('date_joined');">
                                    <img src="<%= request.getContextPath() %>/images/cal.gif" id="date_joined_cal">
                                    <input type="hidden" name="date_joined_year">
                                    <input type="hidden" name="date_joined_month">
                                    <input type="hidden" name="date_joined_date">
                                </div>
                            </div>
                            <div class="col-sm-2 text-end" id="endDateLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formEndDate"/>:</label>
                            </div>
                            <div class="col-sm-4" id="endDateCell">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" placeholder="<fmt:message key="yyyy-mm-dd"/>"
                                           name="end_date" id="end_date"
                                           class="form-control"
                                           value="<%=today %>" size="12"
                                           onchange="parseDateField('end_date');">
                                    <img src="<%= request.getContextPath() %>/images/cal.gif" id="end_date_cal">
                                    <input type="hidden" name="end_date_year">
                                    <input type="hidden" name="end_date_month">
                                    <input type="hidden" name="end_date_date">
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="phuLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPHU"/>:</label>
                            </div>
                            <div class="col-sm-4" id="phuLblCell">
                                <select id="PHU" name="PHU" class="form-select">
                                    <option value=""><fmt:message key="demographic.demographicaddrecordhtm.optSelectBelow"/></option>
                                    <%
                                        String defaultPhu = CarlosProperties.getInstance().getProperty("default_phu");

                                        LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
                                        LookupList ll = lookupListManager.findLookupListByName(LoggedInInfo.getLoggedInInfoFromSession(request), "phu");
                                        if (ll != null) {
                                            for (LookupListItem llItem : ll.getItems()) {
                                                if (llItem.isActive()) {
                                                    String selected = "";
                                                    if (llItem.getValue().equals(defaultPhu)) {
                                                        selected = " selected=\"selected\" ";
                                                    }
                                    %>
                                    <option value="<%=llItem.getValue()%>" <%=selected%>><%=llItem.getLabel()%></option>
                                    <%
                                            }
                                        }
                                    } else {
                                    %>
                                    <option value=""><fmt:message key="demographic.demographicaddrecordhtm.optNoneAvailable"/></option>
                                    <%
                                        }
                                    %>
                                </select>
                            </div>
                        </div>

                        <% //"Has Primary Care Physician" & "Employment Status" fields
                            final String hasPrimary = "Has Primary Care Physician";
                            final String empStatus = "Employment Status";
                            boolean hasHasPrimary = oscarProps.isPropertyActive("showPrimaryCarePhysicianCheck");
                            boolean hasEmpStatus = oscarProps.isPropertyActive("showEmploymentStatus");
                            String hasPrimaryCarePhysician = "N/A";
                            String employmentStatus = "N/A";

                            if (hasHasPrimary || hasEmpStatus) {
                        %>
                        <div class="row mb-2 align-items-center">
                            <% if (hasHasPrimary) { %>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.addFormClinical.labelHasPrimaryCarePhy"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <select name="<%=hasPrimary.replace(" ", "")%>" class="form-select">
                                    <option value="N/A" <%="N/A".equals(hasPrimaryCarePhysician) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optNA"/></option>
                                    <option value="Yes" <%="Yes".equals(hasPrimaryCarePhysician) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optYes"/></option>
                                    <option value="No" <%="No".equals(hasPrimaryCarePhysician) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optNo"/></option>
                                </select>
                            </div>
                            <% }
                                if (hasEmpStatus) {
                            %>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.addFormClinical.labelEmploymentStatus"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <select name="<%=empStatus.replace(" ", "")%>" class="form-select">
                                    <option value="N/A" <%="N/A".equals(employmentStatus) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optNA"/></option>
                                    <option value="FULL TIME" <%="FULL TIME".equals(employmentStatus) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optFullTime"/></option>
                                    <option value="ODSP" <%="ODSP".equals(employmentStatus) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optODSP"/></option>
                                    <option value="OW" <%="OW".equals(employmentStatus) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optOW"/></option>
                                    <option value="PART TIME" <%="PART TIME".equals(employmentStatus) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optPartTime"/></option>
                                    <option value="UNEMPLOYED" <%="UNEMPLOYED".equals(employmentStatus) ? "selected" : ""%>><fmt:message key="demographic.addFormClinical.optUnemployed"/></option>
                                </select>
                            </div>
                        </div>
                        <% }
                        }

                        //customized key
                            if (oscarVariables.getProperty("demographicExt") != null) {
                                boolean bExtForm = oscarVariables.getProperty("demographicExtForm") != null ? true : false;
                                String[] propDemoExtForm = bExtForm ? (oscarVariables.getProperty("demographicExtForm", "").split("\\|")) : null;
                                String[] propDemoExt = oscarVariables.getProperty("demographicExt", "").split("\\|");
                                for (int k = 0; k < propDemoExt.length; k = k + 2) {
                        %>
                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end"><label class="fw-bold col-form-label py-0"><%= SafeEncode.forHtml(propDemoExt[k]) %>:</label></div>
                            <div class="col-sm-4">
                                <% if (bExtForm) {
                                    out.println(propDemoExtForm[k]);
                                } else { %>
                                <input type="text" name="<%=propDemoExt[k].replace(' ', '_') %>" class="form-control" value="">
                                <% } %>
                            </div>
                            <div class="col-sm-2 text-end"><%=(k + 1) < propDemoExt.length ? ("<label class=\"fw-bold col-form-label py-0\">" + propDemoExt[k + 1] + ":</label>") : "&nbsp;" %></div>
                            <div class="col-sm-4">
                                <% if (bExtForm && (k + 1) < propDemoExt.length) {
                                    out.println(propDemoExtForm[k + 1]);
                                } else { %> <%=(k + 1) < propDemoExt.length ? "<input type=\"text\" name=\"" + propDemoExt[k + 1].replace(' ', '_') + "\" class=\"form-control\" value=''>" : "&nbsp;" %>
                                <% } %>
                            </div>
                        </div>
                        <% }
                        }
                            if (oscarVariables.getProperty("demographicExtJScript") != null) {
                                out.println(oscarVariables.getProperty("demographicExtJScript"));
                            }
                        %>

                        <%
                            if (oscarProps.getProperty("EXTRA_DEMO_FIELDS") != null) {
                                String fieldJSP = oscarProps.getProperty("EXTRA_DEMO_FIELDS");
                                fieldJSP += ".jsp";
                        %>
                        <div class="row mb-2">
                            <div class="col-12">
                                <jsp:include page="<%=fieldJSP%>"/>
                                <%}%>
                            </div>
                        </div>

                        <%
                            String wLReadonly = "";
                            WaitingList wL = WaitingList.getInstance();
                            if (!wL.getFound()) {
                                wLReadonly = "readonly";
                            }
                        %>
                        <div class="row mb-2">
                            <div class="col-12" id="waitListTbl">
                                <div class="border p-2">
                                    <div class="row mb-2 align-items-center">
                                        <div class="col-sm-2 text-end">
                                            <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddarecordhtm.msgWaitList"/>:</label>
                                        </div>
                                        <div class="col-sm-4">
                                            <select id="name_list_id" name="list_id" class="form-select">
                                                <% if (wLReadonly.equals("")) { %>
                                                <option value="0"><fmt:message key="demographic.addFormClinical.optSelectWaitList"/></option>
                                                <%} else { %>
                                                <option value="0"><fmt:message key="demographic.demographicaddarecordhtm.optCreateWaitList"/></option>
                                                <%} %>
                                                <%
                                                    for (WaitingListName wln : waitingListNameDao.findCurrentByGroup(((ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE)).getMyGroupNo())) {
                                                %>
                                                <option value="<%= SafeEncode.forHtmlAttribute(wln.getId().toString()) %>"><%= SafeEncode.forHtml(wln.getName()) %></option>
                                                <%
                                                    }
                                                %>
                                            </select>
                                        </div>
                                        <div class="col-sm-2 text-end">
                                            <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddarecordhtm.msgWaitListNote"/>:</label>
                                        </div>
                                        <div class="col-sm-4">
                                            <input type="text" id="waiting_list_note" name="waiting_list_note" class="form-control" <%=wLReadonly%>>
                                        </div>
                                    </div>
                                    <div class="row mb-2 align-items-center">
                                        <div class="col-sm-2 text-end">
                                            <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddarecordhtm.msgDateOfReq"/>:</label>
                                        </div>
                                        <div class="col-sm-4">
                                            <div class="d-flex gap-1 align-items-center">
                                                <input type="text" placeholder="<fmt:message key="yyyy-mm-dd"/>"
                                                       name="waiting_list_referral_date"
                                                       id="waiting_list_referral_date"
                                                       class="form-control"
                                                       value="" size="12" <%=wLReadonly%>>
                                                <img src="<%= request.getContextPath() %>/images/cal.gif" id="referral_date_cal">
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <%-- TOGGLE PRIVACY CONSENT MODULE --%>
                        <oscar:oscarPropertiesCheck property="privateConsentEnabled" value="true">
                            <%
                                String[] privateConsentPrograms2 = CarlosProperties.getInstance().getProperty("privateConsentPrograms", "").split(",");
                                ProgramProvider pp3 = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
                                boolean showConsents = false;
                                if (pp3 != null) {
                                    for (int x = 0; x < privateConsentPrograms2.length; x++) {
                                        if (privateConsentPrograms2[x].equals(pp3.getProgramId().toString())) {
                                            showConsents = true;
                                        }
                                    }
                                }

                                if (showConsents) { %>
                            <%-- consents --%>
                            <div class="row mb-2">
                                <div class="col-12">
                                    <div class="form-check">
                                        <input type="checkbox" name="privacyConsent" value="yes" class="form-check-input" id="privacyConsentChk">
                                        <label class="form-check-label fw-bold" for="privacyConsentChk"><fmt:message key="demographic.addFormClinical.optPrivacyConsentObtained"/></label>
                                    </div>
                                    <div class="form-check">
                                        <input type="checkbox" name="informedConsent" value="yes" class="form-check-input" id="informedConsentChk">
                                        <label class="form-check-label fw-bold" for="informedConsentChk"><fmt:message key="demographic.addFormClinical.optInformedConsentObtained"/></label>
                                    </div>
                                </div>
                            </div>
                            <% } %>

                            <oscar:oscarPropertiesCheck property="USE_NEW_PATIENT_CONSENT_MODULE" value="true">
                                <c:forEach items="${ consentTypes }" var="consentType" varStatus="count">
                                    <c:set var="patientConsent" value=""/>
                                    <c:forEach items="${ patientConsents }" var="consent">
                                        <c:if test="${ consent.consentType.id eq consentType.id }">
                                            <c:set var="patientConsent" value="${ consent }"/>
                                        </c:if>
                                    </c:forEach>
                                     <c:set var="rawConsentTypeKey" value="${ consentType.type }"/>
                                     <c:set var="safeConsentTypeKey" value="${ fn:replace(rawConsentTypeKey, ' ', '_') }"/>
                                    <div class="row mb-2 align-items-start privacyConsentRow" id="privacyConsentRow_${count.index}">
                                        <div class="col-sm-2 text-end">
                                            <span class="fw-bold">${carlos:forHtml(consentType.name)}</span>
                                        </div>
                                        <div class="col-sm-6">
                                            ${carlos:forHtml(consentType.description)}
                                        </div>
                                        <div class="col-sm-4" id="consentStatusDate">
                                            <div class="form-check form-check-inline">
                                                 <input type="radio" name="${ safeConsentTypeKey }" id="optin_${safeConsentTypeKey}" value="0" class="form-check-input"/>
                                                 <label class="form-check-label" for="optin_${ safeConsentTypeKey }"><fmt:message key="demographic.demographicaddrecordhtm.optIn"/></label>
                                            </div>
                                            <div class="form-check form-check-inline">
                                                 <input type="radio" name="${ safeConsentTypeKey }" id="optout_${safeConsentTypeKey}" value="1" class="form-check-input"/>
                                                 <label class="form-check-label" for="optout_${ safeConsentTypeKey }"><fmt:message key="demographic.demographicaddrecordhtm.optOut"/></label>
                                            </div>
                                            <input type="button" class="btn btn-outline-secondary btn-sm"
                                                    name="clearRadio_${safeConsentTypeKey}_btn"
                                                    onclick="consentClearBtn('${safeConsentTypeKey}')" value="<fmt:message key='demographic.demographicaddrecordhtm.clear'/>"/>
                                        </div>
                                    </div>
                                </c:forEach>
                            </oscar:oscarPropertiesCheck>
                        </oscar:oscarPropertiesCheck>
                        <%
                        // CAISI program has been deprecated, just a stub to leave here
                        String _pvid = loggedInInfo.getLoggedInProviderNo();
                        Program[] bedP = new Program[0];
                        Program oscarProgram = programDao.getProgramByName("OSCAR");
                        String programId = "";
                        if (oscarProgram != null) {
                          programId = String.valueOf(oscarProgram.getId());
                        }
                        %>                
                        <input type="hidden" name="rps" value="<%= SafeEncode.forHtmlAttribute(programId) %>" >
                        <input type="hidden" name="sp" value="<%= SafeEncode.forHtmlAttribute(programId) %>" >


                        <div class="row mb-2">
                            <div class="col-12">
                                <div class="row mb-2 align-items-center">
                                    <div class="col-sm-1 text-end" id="alertLbl">
                                        <label class="fw-bold col-form-label py-0 text-danger"><fmt:message key="demographic.demographicaddrecordhtm.formAlert"/>:</label>
                                    </div>
                                    <div class="col-sm-11" id="alertCell">
                                        <textarea id="cust3" name="cust3" class="form-control" rows="2"></textarea>
                                    </div>
                                </div>
                                <div class="row mb-2 align-items-center">
                                    <div class="col-sm-1 text-end" id="notesLbl">
                                        <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formNotes"/>:</label>
                                    </div>
                                    <div class="col-sm-11" id="notesCell">
                                        <textarea id="content" name="content" class="form-control" rows="2"></textarea>
                                    </div>
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2">
                            <div class="col-12">
                                <div>
                                    <%
                                        Facility facility = loggedInInfo.getCurrentFacility();
                                        Integer fid = null;
                                        if (facility != null) fid = facility.getRegistrationIntake();
                                        if (fid == null || fid < 0) {
                                            List<EForm> eforms = eformDao.getEfromInGroupByGroupName("Registration Intake");
                                            if (eforms != null && eforms.size() == 1) fid = eforms.get(0).getId();
                                        }
                                        if (fid != null && fid >= 0) {
                                    %>
                                    <iframe scrolling="no" id="eform_iframe" name="eform_iframe" frameborder="0"
                                            src="<%= request.getContextPath() %>/eform/efmshowform_data?fid=<%=fid%>"
                                            onload="this.height=0;var fdh=(this.Document?this.Document.body.scrollHeight:this.contentDocument.body.offsetHeight);this.height=(fdh>800?fdh:800)"
                                            width="100%"></iframe>
                                    <%}%>
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2 py-2" style="background-color:#CCCCFF;">
                            <div class="col-12">
                                <input type="hidden" name="dboperation" value="add_record">
                                <input type="hidden" name="displaymode" value="Add Record">
                                <input type="submit" id="btnAddRecord" name="btnAddRecord"
                                       class="btn btn-primary"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnAddRecord"/>"/>
                                <input type="button" id="btnSwipeCard" name="Button"
                                       class="btn btn-secondary"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnSwipeCard"/>"
                                       onclick="window.open('zadddemographicswipe.htm','', 'scrollbars=yes,resizable=yes,width=600,height=300')"
                                       ;>
                                <input type="button" name="closeButton"
                                       class="btn btn-secondary"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnCancel"/>"
                                       onclick="self.close();">
                            </div>
                        </div>

                    </div><%-- /#addDemographicTbl --%>
                </form>
