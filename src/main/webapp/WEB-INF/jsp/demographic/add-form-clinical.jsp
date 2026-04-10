<%-- add-form-clinical.jsp: Care team, roster, consent, programs, submit (from demographicaddarecordhtm.jsp lines 1878-2490) --%>
<%@ page import="java.util.*" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Date" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="org.owasp.encoder.Encode" %>
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
<%@ page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.waitinglist.WaitingList" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
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
                            <td id="demoDoctorLbl" align="right">
                                <b><% if (oscarProps.getProperty("demographicLabelDoctor") != null) {
                                    out.print(oscarProps.getProperty("demographicLabelDoctor", ""));
                                } else { %>
                                    <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formDoctor"/> <% } %>
                                    : </b></td>
                            <td id="demoDoctorCell" align="left">
                                <select name="staff">
                                    <option value=""></option>
                                    <%
                                        for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
                                            String docProviderNo = p.getProviderNo();
                                    %>
                                    <option id="doc<%=docProviderNo%>"
                                            value="<%=docProviderNo%>"><%=Encode.forHtmlContent(p.getFormattedName())%>
                                    </option>
                                    <%
                                        }
                                    %>
                                    <option value=""></option>
                                </select></td>
                            <td id="nurseLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNurse"/>: </b></td>
                            <td id="nurseCell"><select name="cust1">
                                <option value=""></option>
                                <%
                                    for (Provider p : providerDao.getActiveProvidersByRole("nurse")) {
                                %>
                                <option value="<%=p.getProviderNo()%>"><%=Encode.forHtmlContent(p.getFormattedName())%>
                                </option>
                                <%
                                    }

                                %>
                            </select></td>
                        </tr>
                        <tr valign="top">
                            <td id="midwifeLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formMidwife"/>: </b></td>
                            <td id="midwifeCell"><select name="cust4">
                                <option value=""></option>
                                <%
                                    for (Provider p : providerDao.getActiveProvidersByRole("midwife")) {
                                %>
                                <option value="<%=p.getProviderNo()%>">
                                    <%=Encode.forHtmlContent(p.getFormattedName())%>
                                </option>
                                <%
                                    }

                                %>
                            </select></td>
                            <td id="residentLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formResident"/>: </b></td>
                            <td id="residentCell" align="left"><select name="cust2">
                                <option value=""></option>
                                <%
                                    for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
                                %>
                                <option value="<%=p.getProviderNo()%>">
                                    <%=Encode.forHtmlContent(p.getFormattedName())%>
                                </option>
                                <%
                                    }

                                %>
                            </select></td>
                        </tr>
                        <tr id="rowWithReferralDoc" valign="top">
                            <td id="referralDocLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formReferalDoctor"/>:</b></td>
                            <td id="referralDocCell" align="left">
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
                                %> <select name="r_doctor"
                                           onChange="changeRefDoc()">
                                <option value=""></option>
                                <% for (int k = 0; k < vecRef.size(); k++) {
                                    prop = (Properties) vecRef.get(k);
                                %>
                                <option
                                        value="<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>">
                                    <%=Misc.getShortStr((prop.getProperty("last_name") + "," + prop.getProperty("first_name")), "", nStrShowLen)%>
                                </option>
                                <% } %>
                            </select>
                                <script language="Javascript">

                                    function changeRefDoc() {
//alert(document.forms[1].r_doctor.value);
                                        var refName = document.forms[1].r_doctor.options[document.forms[1].r_doctor.selectedIndex].value;
                                        var refNo = "";
                                        <% for(int k=0; k<vecRef.size(); k++) {
  		prop= (Properties) vecRef.get(k);
  	%>
                                        if (refName.indexOf("<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>") >= 0) {
                                            refNo = '<%=prop.getProperty("referral_no", "")%>';
                                        }
                                        <% } %>
                                        document.forms[1].r_doctor_ohip.value = refNo;
                                    }

                                </script>
                                <% } else {%> <input type="text" name="r_doctor" maxlength="40"
                                                     value=""> <% } %>
                            </td>
                            <td id="referralDocNoLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formReferalDoctorN"/>:</b></td>
                            <td id="referralDocNoCell" align="left"><input type="text"
                                                                           name="r_doctor_ohip"
                                                                           maxlength="6"> <% if ("ON".equals(prov)) { %>
                                <a
                                        href="javascript:referralScriptAttach2('r_doctor_ohip','r_doctor')"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographiceditdemographic.btnSearch"/>
                                    #</a> <% } %>
                            </td>
                        </tr>
                        <tr valign="top">
                            <td align="right" id="rosterStatusLbl" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPCNRosterStatus"/>: </b></td>
                            <td id="rosterStatus" align="left">
                                <!--input type="text" name="roster_status" onBlur="upCaseCtrl(this)"-->
                                <select id="roster_status" name="roster_status" style="width: 160px">
                                    <option value=""></option>
                                    <option value="RO"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.RO-rostered"/></option>
                                    <option value="FS"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.FS-feeforservice"/></option>

                                    <%
                                        for (String status : demographicDao.getRosterStatuses()) {%>
                                    <option value="<%=status%>"><%=status%>
                                    </option>
                                    <% } // end while %>
                                </select> <input type="button" onClick="newStatus1();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.AddNewRosterStatus"/> "/></td>
                            <td id="rosterDateLbl" align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPCNDateJoined"/>: </b></td>
                            <td class="rosterDateCell" align="left"><input type="text" name="roster_date_year"
                                                                           size="4" maxlength="4"> <input type="text"
                                                                                                          name="roster_date_month"
                                                                                                          size="2"
                                                                                                          maxlength="2">
                                <input
                                        type="text" name="roster_date_date" size="2" maxlength="2">
                            </td>
                        </tr>
                        <tr valign="top">
                            <td align="right" id="rosterEnrolledToLbl"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formRosterEnrolledTo"/>: </b></td>
                            <td id="rosterEnrolledTo" align="left">
                                <select id="roster_enrolled_to" name="roster_enrolled_to">
                                    <option value=""></option>
                                            <%
						for (Provider p : providerDao.getActiveProvidersByRole("doctor")) {
								String docProviderNo = p.getProviderNo();
					%>
                                    <option id="<%=docProviderNo%>" value="<%=docProviderNo%>"><%=p.getFormattedName()%>
                                    </option>
                                            <%
						}
					%>
                                    <option value=""></option>
                            </td>
                            <td id="chartNoLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formChartNo"/>:</b></td>
                            <td id="chartNo" align="left"><input type="text" id="chart_no" name="chart_no"
                                                                 value="<%=Encode.forHtmlAttribute(chartNoVal)%>">
                            </td>

                        </tr>
                        <tr valign="top">
                            <td id="ptStatusLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPatientStatus"/>:</b></td>
                            <td id="ptStatusCell" align="left">
                                <select id="patient_status" name="patient_status" style="width: 160px">
                                    <option value="AC"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.AC-Active"/></option>
                                    <option value="IN"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.IN-InActive"/></option>
                                    <option value="DE"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.DE-Deceased"/></option>
                                    <option value="MO"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.MO-Moved"/></option>
                                    <option value="FI"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.FI-Fired"/></option>
                                    <%
                                        for (String status : demographicDao.search_ptstatus()) { %>
                                    <option value="<%=status%>"><%=status%>
                                    </option>
                                    <% } // end while %>
                                </select> <input type="button" onClick="newStatus();" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.AddNewPatient"/> ">

                            </td>
                            <td align="right" nowrap>
                                <b>Patient Status Date:</b>
                            </td>
                            <td align="left">
                                <input type="text" placeholder="yyyy-mm-dd"
                                       name="patient_status_date" id="patient_status_date"
                                       value="<%=today %>" size="12"> <img
                                    src="<%= request.getContextPath() %>/images/cal.gif" id="patient_status_date_cal">
                            </td>
                        </tr>


                        <tr valign="top">
                            <td id="joinDateLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formDateJoined"/></b><b>:
                            </b></td>
                            <td id="joinDateCell" align="left"><input type="text" name="date_joined_year"
                                                                      placeholder="yyyy"
                                                                      size="4" maxlength="4" value="<%=curYear%>">
                                <input
                                        type="text" placeholder="mm" name="date_joined_month" size="2" maxlength="2"
                                        value="<%=curMonth%>"> <input type="text" placeholder="dd"
                                                                      name="date_joined_date" size="2" maxlength="2"
                                                                      value="<%=curDay%>">
                            </td>
                            <td id="endDateLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formEndDate"/></b><b>: </b></td>
                            <td id="endDateCell" align="left"><input type="text" placeholder="yyyy" name="end_date_year"
                                                                     size="4" maxlength="4"> <input type="text"
                                                                                                    placeholder="mm"
                                                                                                    name="end_date_month"
                                                                                                    size="2"
                                                                                                    maxlength="2">
                                <input
                                        type="text" placeholder="dd" name="end_date_date" size="2" maxlength="2"></td>
                        </tr>

                        <tr valign="top">
                            <td id="phuLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formPHU"/>:</b></td>
                            <td id="phuLblCell" align="left">
                                <select id="PHU" name="PHU">
                                    <option value="">Select Below</option>
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
                                    <option value="<%=llItem.getValue()%>" <%=selected%>><%=llItem.getLabel()%>
                                    </option>
                                    <%
                                            }
                                        }
                                    } else {
                                    %>
                                    <option value="">None Available</option>
                                    <%
                                        }

                                    %>
                                </select>
                            </td>
                            <td><!-- placeholder --></td>
                            <td><!-- placeholder --></td>
                        </tr>


                        <% //"Has Primary Care Physician" & "Employment Status" fields
                            final String hasPrimary = "Has Primary Care Physician";
                            final String empStatus = "Employment Status";
                            boolean hasHasPrimary = oscarProps.isPropertyActive("showPrimaryCarePhysicianCheck");
                            boolean hasEmpStatus = oscarProps.isPropertyActive("showEmploymentStatus");
                            String hasPrimaryCarePhysician = "N/A";
                            String employmentStatus = "N/A";

                            if (hasHasPrimary || hasEmpStatus) {
                        %>
                        <tr valign="top">
                            <% if (hasHasPrimary) {
                            %>
                            <td style="text-align: right;"><b><%=hasPrimary.replace(" ", "&nbsp;")%>:</b></td>
                            <td>
                                <select name="<%=hasPrimary.replace(" ", "")%>">
                                    <option value="N/A" <%="N/A".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                        N/A
                                    </option>
                                    <option value="Yes" <%="Yes".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>
                                        Yes
                                    </option>
                                    <option value="No" <%="No".equals(hasPrimaryCarePhysician) ? "selected" : ""%>>No
                                    </option>
                                </select>
                            </td>
                            <% }
                                if (hasEmpStatus) {
                            %>
                            <td style="text-align: right;"><b><%=empStatus.replace(" ", "&nbsp;")%>:</b></td>
                            <td>
                                <select name="<%=empStatus.replace(" ", "")%>">
                                    <option value="N/A" <%="N/A".equals(employmentStatus) ? "selected" : ""%>>N/A
                                    </option>
                                    <option value="FULL TIME" <%="FULL TIME".equals(employmentStatus) ? "selected" : ""%>>
                                        FULL TIME
                                    </option>
                                    <option value="ODSP" <%="ODSP".equals(employmentStatus) ? "selected" : ""%>>ODSP
                                    </option>
                                    <option value="OW" <%="OW".equals(employmentStatus) ? "selected" : ""%>>OW</option>
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

//customized key
                            if (oscarVariables.getProperty("demographicExt") != null) {
                                boolean bExtForm = oscarVariables.getProperty("demographicExtForm") != null ? true : false;
                                String[] propDemoExtForm = bExtForm ? (oscarVariables.getProperty("demographicExtForm", "").split("\\|")) : null;
                                String[] propDemoExt = oscarVariables.getProperty("demographicExt", "").split("\\|");
                                for (int k = 0; k < propDemoExt.length; k = k + 2) {
                        %>
                        <tr valign="top">
                            <td style="text-align: right;"><b><%=propDemoExt[k] %>
                            </b><b>: </b></td>
                            <td style="text-align: left;">
                                <% if (bExtForm) {
                                    out.println(propDemoExtForm[k]);
                                } else { %>
                                <input type="text" name="<%=propDemoExt[k].replace(' ', '_') %>" value="">
                                <% } %>
                            </td>
                            <td style="text-align: right;"><%=(k + 1) < propDemoExt.length ? ("<b>" + propDemoExt[k + 1] + ": </b>") : "&nbsp;" %>
                            </td>
                            <td style="text-align: left;">
                                <% if (bExtForm && (k + 1) < propDemoExt.length) {
                                    out.println(propDemoExtForm[k + 1]);
                                } else { %> <%=(k + 1) < propDemoExt.length ? "<input type=\"text\" name=\"" + propDemoExt[k + 1].replace(' ', '_') + "\"  value=''>" : "&nbsp;" %>
                                <% } %>
                            </td>
                        </tr>
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
                        <tr>
                            <td colspan="4">
                                <jsp:include page="<%=fieldJSP%>"/>

                                <%}%>
                            </td>
                        </tr>


                        <%
                            String wLReadonly = "";
                            WaitingList wL = WaitingList.getInstance();
                            if (!wL.getFound()) {
                                wLReadonly = "readonly";
                            }
                        %>
                        <tr>
                            <td id="waitListTbl" colspan="4">
                                <table border="1" width="100%">
                                    <tr valign="top">
                                        <td align="right"><b> <fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecordhtm.msgWaitList"/>: </b></td>
                                        <td align="left"><select id="name_list_id" name="list_id">
                                            <% if (wLReadonly.equals("")) { %>
                                            <option value="0">--Select Waiting List--</option>
                                            <%} else { %>
                                            <option value="0"><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecordhtm.optCreateWaitList"/>
                                            </option>
                                            <%} %>
                                            <%
                                                for (WaitingListName wln : waitingListNameDao.findCurrentByGroup(((ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE)).getMyGroupNo())) {

                                            %>
                                            <option value="<%=wln.getId()%>"><%=wln.getName()%>
                                            </option>
                                            <%
                                                }
                                            %>
                                        </select></td>
                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecordhtm.msgWaitListNote"/>: </b></td>
                                        <td align="left"><input type="text" id="waiting_list_note"
                                                                name="waiting_list_note"
                                                <%=wLReadonly%>></td>
                                    </tr>

                                    <tr>

                                        <td align="right" nowrap><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddarecordhtm.msgDateOfReq"/>:</b></td>
                                        <td align="left"><input type="text" placeholder="yyyy-mm-dd"
                                                                name="waiting_list_referral_date"
                                                                id="waiting_list_referral_date"
                                                                value="" size="12" <%=wLReadonly%>> <img
                                                src="<%= request.getContextPath() %>/images/cal.gif" id="referral_date_cal">
                                        </td>
                                        <td><!-- placeholder --></td>
                                        <td><!-- placeholder --></td>
                                    </tr>
                                </table>
                            </td>
                        </tr>


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
                            <!-- consents -->
                            <tr valign="top">

                                <td colspan="4">
                                    <input type="checkbox" name="privacyConsent" value="yes"><b>Privacy Consent (verbal)
                                    Obtained</b>
                                    <br/>
                                    <input type="checkbox" name="informedConsent" value="yes"><b>Informed Consent
                                    (verbal) Obtained</b>
                                    <br/>
                                </td>

                            </tr>

                            <% } %>

                            <oscar:oscarPropertiesCheck property="USE_NEW_PATIENT_CONSENT_MODULE" value="true">

                                <c:forEach items="${ consentTypes }" var="consentType" varStatus="count">
                                    <c:set var="patientConsent" value=""/>
                                    <c:forEach items="${ patientConsents }" var="consent">
                                        <c:if test="${ consent.consentType.id eq consentType.id }">
                                            <c:set var="patientConsent" value="${ consent }"/>
                                        </c:if>
                                    </c:forEach>
                                    <tr class="privacyConsentRow" id="${ count.index }">
                                        <td class="alignRight" style="width:16%;vertical-align:top;">
                                            <div style="font-weight:bold;white-space:nowrap;">
                                                <c:out value="${ consentType.name }"/>
                                            </div>
                                        </td>

                                        <td colspan="2" style="padding-left:10px;vertical-align:top;">
                                            <c:out value="${ consentType.description }"/>
                                        </td>

                                        <td id="consentStatusDate" style="width:31%;vertical-align:top;">
                                            <input type="radio"
                                                   name="${ consentType.type }"
                                                   id="optin_${ consentType.type }"
                                                   value="0"
                                            />
                                            <label for="optin_${ consentType.type }">Opt-In</label>
                                            <input type="radio"
                                                   name="${ consentType.type }"
                                                   id="optout_${ consentType.type }"
                                                   value="1"
                                            />
                                            <label for="optout_${ consentType.type }">Opt-Out</label>
                                            <input type="button"
                                                   name="clearRadio_${consentType.type}_btn"
                                                   onclick="consentClearBtn('${consentType.type}')" value="Clear"/>

                                        </td>

                                    </tr>
                                </c:forEach>

                            </oscar:oscarPropertiesCheck>

                        </oscar:oscarPropertiesCheck>

                        <tr valign="top">
                            <td colspan="4">
                                <table>
                                    <tr bgcolor="#CCCCFF" class="category_table_heading">
                                        <th colspan="2" class="alignLeft">Program Admissions</th>
                                    </tr>
                                    <tr>
                                        <td>Residential Status:</td>
                                        <td>Service Programs</td>
                                    </tr>
                                    <tr>
                                        <td>
                                            <select id="rsid" name="rps">
                                                <%
                                                    String _pvid = loggedInInfo.getLoggedInProviderNo();
                                                    Program[] bedP = new Program[0];
                                                    Program oscarProgram = programDao.getProgramByName("OSCAR");
                                                    
                                                    // Always use OSCAR program as default if it exists
                                                    if (oscarProgram != null) {
                                                %>
                                                <option value="<%=oscarProgram.getId()%>" selected="selected"><%=oscarProgram.getName()%></option>
                                                <%
                                                    }
                                                    
                                                    for (Program _p : bedP) {
                                                        // Skip OSCAR program since we already added it
                                                        if (oscarProgram != null && _p.getId().equals(oscarProgram.getId())) {
                                                            continue;
                                                        }
                                                %>
                                                <option value="<%=_p.getId()%>"><%=_p.getName()%></option>
                                                <%
                                                    }
                                                    
                                                    // If no OSCAR program and no bed programs, still need a value
                                                    if (oscarProgram == null && bedP.length == 0) {
                                                %>
                                                <option value="">No programs available</option>
                                                <%
                                                    }
                                                %>
                                            </select>
                                        </td>
                                        <td>
                                            <ul>
                                                <%
                                                    List<Program> servP = pm.getServicePrograms();
                                                    for (Program _p : servP) {
                                                %>
                                                <li>
                                                    <input type="checkbox" name="sp"
                                                           value="<%=_p.getId()%>"/><%=_p.getName()%>
                                                </li>
                                                <%}%>
                                            </ul>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>

                        <tr>
                            <td colspan="4">
                                <table width="100%" bgcolor="#EEEEFF">
                                    <tr>
                                        <td id="alertLbl" width="10%" align="right"><font
                                                color="#FF0000"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formAlert"/>: </b></font></td>
                                        <td id="alertCell"><textarea id="cust3" name="cust3" style="width: 100%"
                                                                     rows="2"></textarea>
                                        </td>
                                    </tr>
                                    <tr>
                                        <td id="notesLbl" align="right"><b><fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.formNotes"/> : </b></td>
                                        <td id="notesCell"><textarea id="content" name="content" style="width: 100%"
                                                                     rows="2"></textarea>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td colspan="4">
                                <div>
                                    <%
                                        //    Integer fid = ((Facility)session.getAttribute("currentFacility")).getRegistrationIntake();
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
                                            src="<%= request.getContextPath() %>/eform/efmshowform_data.jsp?fid=<%=fid%>"
                                            onload="this.height=0;var fdh=(this.Document?this.Document.body.scrollHeight:this.contentDocument.body.offsetHeight);this.height=(fdh>800?fdh:800)"
                                            width="100%"></iframe>
                                    <%}%>
                                </div>
                            </td>
                        </tr>
                        <tr bgcolor="#CCCCFF">
                            <td colspan="4">
                                <input type="hidden" name="dboperation"
                                       value="add_record"> <input type="hidden" name="displaymode" value="Add Record">
                                <input type="submit" id="btnAddRecord" name="btnAddRecord"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnAddRecord"/>"/>
                                <input type="button" id="btnSwipeCard" name="Button"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnSwipeCard"/>"
                                       onclick="window.open('zadddemographicswipe.htm','', 'scrollbars=yes,resizable=yes,width=600,height=300')"
                                       ;>

                                <input type="button" name="closeButton"
                                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="demographic.demographicaddrecordhtm.btnCancel"/>"
                                       onclick="self.close();">

                            </td>
                        </tr>
                    </table>
                </form>
