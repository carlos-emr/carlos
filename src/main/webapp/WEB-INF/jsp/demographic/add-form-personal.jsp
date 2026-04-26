<%-- add-form-personal.jsp: Name, address, phone, demographics, HIN (from demographicaddarecordhtm.jsp lines 767-1877) --%>
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
<%@ page import="io.github.carlos_emr.carlos.demographic.pageUtil.DemographicEditHelper" %>
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
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
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
    java.util.Properties oscarVariables = oscarProps;
    java.util.Locale vLocale = request.getLocale();
    String searchMode = request.getParameter("search_mode");
    String keyWord = request.getParameter("keyword");
%>
<jsp:useBean id="providerBean" class="java.util.Properties" scope="session"/>
<jsp:useBean id="apptMainBean" class="io.github.carlos_emr.AppointmentMainBean" scope="session"/>

<%-- === Original content === --%>
                      onsubmit="return aSubmit()" autocomplete="off">
                    <input type="hidden" name="fromAppt" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("fromAppt")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="originalPage" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("originalPage")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="bFirstDisp" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("bFirstDisp")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="provider_no" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("provider_no")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="start_time" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("start_time")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="end_time" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("end_time")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="duration" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("duration")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="year" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("year")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="month" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("month")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="day" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("day")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="appointment_date" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("appointment_date")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="notes" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("notes")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="reason" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("reason")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="location" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("location")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="resources" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("resources")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="type" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("type")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="style" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("style")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="billing" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("billing")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="status" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("status")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="createdatetime" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("createdatetime")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="creator" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("creator")) %>' context="htmlAttribute"/>">
                    <input type="hidden" name="remarks" value="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("remarks")) %>' context="htmlAttribute"/>">


                    <div id="addDemographicTbl">

                        <%if (CarlosProperties.getInstance().getProperty("workflow_enhance") != null && CarlosProperties.getInstance().getProperty("workflow_enhance").equals("true")) { %>
                        <div class="row mb-2">
                            <div class="col-12">
                                <input type="hidden" name="dboperation" value="add_record">
                                <input type="hidden" name="displaymode" value="Add Record">
                                <input type="submit" name="submit"
                                       class="btn btn-primary"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnAddRecord"/>">
                                <input type="button" name="Button"
                                       class="btn btn-secondary"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnSwipeCard"/>"
                                       onclick="window.open('zadddemographicswipe.htm','', 'scrollbars=yes,resizable=yes,width=600,height=300')"
                                       ;>
                                <input type="button" name="Button"
                                       class="btn btn-secondary"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnCancel"/>"
                                       onclick=self.close();>
                            </div>
                        </div>
                        <%
                            }

                            String lastNameVal = "";
                            String firstNameVal = "";
                            String chartNoVal = "";

                            if (searchMode != null) {
                                if ("search_name".equals(searchMode)) {
                                    int commaIdx = keyWord.indexOf(",");
                                    if (commaIdx == -1)
                                        lastNameVal = keyWord.trim();
                                    else if (commaIdx == (keyWord.length() - 1))
                                        lastNameVal = keyWord.substring(0, keyWord.length() - 1).trim();
                                    else {
                                        lastNameVal = keyWord.substring(0, commaIdx).trim();
                                        firstNameVal = keyWord.substring(commaIdx + 1).trim();
                                    }
                                } else if ("search_chart_no".equals(searchMode)) {
                                    chartNoVal = keyWord;
                                }
                            }
                        %>

                        <div class="row mb-2 align-items-center" id="rowWithLastName">
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formLastName"/><span style="color:red;">:</span></label>
                            </div>
                            <div class="col-sm-4" id="lastName">
                                <input type="text" name="last_name" id="last_name" onBlur="upCaseCtrl(this)"
                                       class="form-control"
                                       value="<carlos:encode value='<%= lastNameVal %>' context="htmlAttribute"/>">
                            </div>
                            <div class="col-sm-2 text-end" id="firstNameLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formFirstName"/><span style="color:red;">:</span></label>
                            </div>
                            <div class="col-sm-4" id="firstName">
                                <input type="text" name="first_name" id="first_name" onBlur="upCaseCtrl(this)"
                                       class="form-control"
                                       value="<carlos:encode value='<%= firstNameVal %>' context="htmlAttribute"/>">
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formMiddleNames"/>:</label>
                            </div>
                            <div class="col-sm-4" id="middleName">
                                <input type="text" name="middleNames" id="middleNames" onBlur="upCaseCtrl(this)"
                                       class="form-control" value="">
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formNameUsed"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <input type="text" name="nameUsed" class="form-control" value="" onBlur="upCaseCtrl(this)"/>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="languageLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.msgDemoLanguage"/>:</label>
                            </div>
                            <div class="col-sm-4" id="languageCell">
                                <select id="official_lang" name="official_lang" class="form-select">
                                    <option value="English" <%= vLocale.getLanguage().equals("en") ? " selected" : "" %>>
                                        <fmt:message key="demographic.demographiceaddrecordhtm.msgEnglish"/></option>
                                    <option value="French"  <%= vLocale.getLanguage().equals("fr") ? " selected" : "" %>>
                                        <fmt:message key="demographic.demographiceaddrecordhtm.msgFrench"/></option>
                                </select>
                            </div>
                            <div class="col-sm-2 text-end" id="titleLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.msgDemoTitle"/>:</label>
                            </div>
                            <div class="col-sm-4" id="titleCell">
                                <select id="title" name="title" onchange="checkTitleSex(value);" class="form-select">
                                    <option value=""><fmt:message key="demographic.demographicaddrecordhtm.msgNotSet"/></option>
                                    <option value="DR"><fmt:message key="demographic.demographicaddrecordhtm.msgDr"/></option>
                                    <option value="MS"><fmt:message key="demographic.demographicaddrecordhtm.msgMs"/></option>
                                    <option value="MISS"><fmt:message key="demographic.demographicaddrecordhtm.msgMiss"/></option>
                                    <option value="MRS"><fmt:message key="demographic.demographicaddrecordhtm.msgMrs"/></option>
                                    <option value="MR"><fmt:message key="demographic.demographicaddrecordhtm.msgMr"/></option>
                                    <option value="MSSR"><fmt:message key="demographic.demographicaddrecordhtm.msgMssr"/></option>
                                    <option value="PROF"><fmt:message key="demographic.demographicaddrecordhtm.msgProf"/></option>
                                    <option value="REEVE"><fmt:message key="demographic.demographicaddrecordhtm.msgReeve"/></option>
                                    <option value="REV"><fmt:message key="demographic.demographicaddrecordhtm.msgRev"/></option>
                                    <option value="RT_HON"><fmt:message key="demographic.demographicaddrecordhtm.msgRtHon"/></option>
                                    <option value="SEN"><fmt:message key="demographic.demographicaddrecordhtm.msgSen"/></option>
                                    <option value="SGT"><fmt:message key="demographic.demographicaddrecordhtm.msgSgt"/></option>
                                    <option value="SR"><fmt:message key="demographic.demographicaddrecordhtm.msgSr"/></option>
                                    <option value="MADAM"><fmt:message key="demographic.demographicaddrecordhtm.msgMadam"/></option>
                                    <option value="MME"><fmt:message key="demographic.demographicaddrecordhtm.msgMme"/></option>
                                    <option value="MLLE"><fmt:message key="demographic.demographicaddrecordhtm.msgMlle"/></option>
                                    <option value="MAJOR"><fmt:message key="demographic.demographicaddrecordhtm.msgMajor"/></option>
                                    <option value="MAYOR"><fmt:message key="demographic.demographicaddrecordhtm.msgMayor"/></option>
                                    <option value="BRO"><fmt:message key="demographic.demographicaddrecordhtm.msgBro"/></option>
                                    <option value="CAPT"><fmt:message key="demographic.demographicaddrecordhtm.msgCapt"/></option>
                                    <option value="Chief"><fmt:message key="demographic.demographicaddrecordhtm.msgChief"/></option>
                                    <option value="Cst"><fmt:message key="demographic.demographicaddrecordhtm.msgCst"/></option>
                                    <option value="Corp"><fmt:message key="demographic.demographicaddrecordhtm.msgCorp"/></option>
                                    <option value="FR"><fmt:message key="demographic.demographicaddrecordhtm.msgFr"/></option>
                                    <option value="HON"><fmt:message key="demographic.demographicaddrecordhtm.msgHon"/></option>
                                    <option value="LT"><fmt:message key="demographic.demographicaddrecordhtm.msgLt"/></option>
                                </select>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="spokenLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.msgSpoken"/>:</label>
                            </div>
                            <div class="col-sm-4" id="spokenCell">
                                <select name="spoken_lang" class="form-select">
                                    <%for (String sp_lang : Util.spokenLangProperties.getLangSorted()) { %>
                                    <option value="<%=sp_lang %>"><%=sp_lang %></option>
                                    <%} %>
                                </select>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="addrLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formAddress"/>:</label>
                            </div>
                            <div class="col-sm-4" id="addressCell">
                                <input id="address" type="text" name="address" class="form-control"/>
                            </div>
                            <div class="col-sm-2 text-end" id="cityLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formCity"/>:</label>
                            </div>
                            <div class="col-sm-4" id="cityCell">
                                <input type="text" id="city" name="city" class="form-control" value="<%=defaultCity %>"/>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="provLbl">
                                <label class="fw-bold col-form-label py-0">
                                    <% if (oscarProps.getProperty("demographicLabelProvince") == null) { %>
                                    <fmt:message key="demographic.demographicaddrecordhtm.formprovince"/>
                                    <% } else {
                                        out.print(oscarProps.getProperty("demographicLabelProvince"));
                                    } %>:
                                </label>
                            </div>
                            <div class="col-sm-4" id="provCell">
                                <%
                                    if ("true".equals(CarlosProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                %>
                                <select name="province" id="province" class="form-select"></select>
                                <div class="mt-1">
                                    <label class="me-1"><fmt:message key="demographic.addFormPersonal.labelFilterByCountry"/></label>
                                    <select name="country" id="country" class="form-select d-inline-block w-auto"></select>
                                </div>
                                <% } else { %>
                                <select id="province" name="province" class="form-select">
                                    <option value="OT" <%=defaultProvince.equals("") || defaultProvince.equals("OT") ? " selected" : ""%>>Other</option>
                                    <%-- <option value="">None Selected</option> --%>
                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>" <%=province.equals(defaultProvince) ? " selected" : ""%>><%=li.next()%></option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB" <%=defaultProvince.equals("AB") ? " selected" : ""%>>AB-Alberta</option>
                                    <option value="BC" <%=defaultProvince.equals("BC") ? " selected" : ""%>>BC-British Columbia</option>
                                    <option value="MB" <%=defaultProvince.equals("MB") ? " selected" : ""%>>MB-Manitoba</option>
                                    <option value="NB" <%=defaultProvince.equals("NB") ? " selected" : ""%>>NB-New Brunswick</option>
                                    <option value="NL" <%=defaultProvince.equals("NL") ? " selected" : ""%>>NL-Newfoundland &amp; Labrador</option>
                                    <option value="NT" <%=defaultProvince.equals("NT") ? " selected" : ""%>>NT-Northwest Territory</option>
                                    <option value="NS" <%=defaultProvince.equals("NS") ? " selected" : ""%>>NS-Nova Scotia</option>
                                    <option value="NU" <%=defaultProvince.equals("NU") ? " selected" : ""%>>NU-Nunavut</option>
                                    <option value="ON" <%=defaultProvince.equals("ON") ? " selected" : ""%>>ON-Ontario</option>
                                    <option value="PE" <%=defaultProvince.equals("PE") ? " selected" : ""%>>PE-Prince Edward Island</option>
                                    <option value="QC" <%=defaultProvince.equals("QC") ? " selected" : ""%>>QC-Quebec</option>
                                    <option value="SK" <%=defaultProvince.equals("SK") ? " selected" : ""%>>SK-Saskatchewan</option>
                                    <option value="YT" <%=defaultProvince.equals("YT") ? " selected" : ""%>>YT-Yukon</option>
                                    <option value="US" <%=defaultProvince.equals("US") ? " selected" : ""%>>US resident</option>
                                    <option value="US-AK" <%=defaultProvince.equals("US-AK") ? " selected" : ""%>>US-AK-Alaska</option>
                                    <option value="US-AL" <%=defaultProvince.equals("US-AL") ? " selected" : ""%>>US-AL-Alabama</option>
                                    <option value="US-AR" <%=defaultProvince.equals("US-AR") ? " selected" : ""%>>US-AR-Arkansas</option>
                                    <option value="US-AZ" <%=defaultProvince.equals("US-AZ") ? " selected" : ""%>>US-AZ-Arizona</option>
                                    <option value="US-CA" <%=defaultProvince.equals("US-CA") ? " selected" : ""%>>US-CA-California</option>
                                    <option value="US-CO" <%=defaultProvince.equals("US-CO") ? " selected" : ""%>>US-CO-Colorado</option>
                                    <option value="US-CT" <%=defaultProvince.equals("US-CT") ? " selected" : ""%>>US-CT-Connecticut</option>
                                    <option value="US-CZ" <%=defaultProvince.equals("US-CZ") ? " selected" : ""%>>US-CZ-Canal Zone</option>
                                    <option value="US-DC" <%=defaultProvince.equals("US-DC") ? " selected" : ""%>>US-DC-District Of Columbia</option>
                                    <option value="US-DE" <%=defaultProvince.equals("US-DE") ? " selected" : ""%>>US-DE-Delaware</option>
                                    <option value="US-FL" <%=defaultProvince.equals("US-FL") ? " selected" : ""%>>US-FL-Florida</option>
                                    <option value="US-GA" <%=defaultProvince.equals("US-GA") ? " selected" : ""%>>US-GA-Georgia</option>
                                    <option value="US-GU" <%=defaultProvince.equals("US-GU") ? " selected" : ""%>>US-GU-Guam</option>
                                    <option value="US-HI" <%=defaultProvince.equals("US-HI") ? " selected" : ""%>>US-HI-Hawaii</option>
                                    <option value="US-IA" <%=defaultProvince.equals("US-IA") ? " selected" : ""%>>US-IA-Iowa</option>
                                    <option value="US-ID" <%=defaultProvince.equals("US-ID") ? " selected" : ""%>>US-ID-Idaho</option>
                                    <option value="US-IL" <%=defaultProvince.equals("US-IL") ? " selected" : ""%>>US-IL-Illinois</option>
                                    <option value="US-IN" <%=defaultProvince.equals("US-IN") ? " selected" : ""%>>US-IN-Indiana</option>
                                    <option value="US-KS" <%=defaultProvince.equals("US-KS") ? " selected" : ""%>>US-KS-Kansas</option>
                                    <option value="US-KY" <%=defaultProvince.equals("US-KY") ? " selected" : ""%>>US-KY-Kentucky</option>
                                    <option value="US-LA" <%=defaultProvince.equals("US-LA") ? " selected" : ""%>>US-LA-Louisiana</option>
                                    <option value="US-MA" <%=defaultProvince.equals("US-MA") ? " selected" : ""%>>US-MA-Massachusetts</option>
                                    <option value="US-MD" <%=defaultProvince.equals("US-MD") ? " selected" : ""%>>US-MD-Maryland</option>
                                    <option value="US-ME" <%=defaultProvince.equals("US-ME") ? " selected" : ""%>>US-ME-Maine</option>
                                    <option value="US-MI" <%=defaultProvince.equals("US-MI") ? " selected" : ""%>>US-MI-Michigan</option>
                                    <option value="US-MN" <%=defaultProvince.equals("US-MN") ? " selected" : ""%>>US-MN-Minnesota</option>
                                    <option value="US-MO" <%=defaultProvince.equals("US-MO") ? " selected" : ""%>>US-MO-Missouri</option>
                                    <option value="US-MS" <%=defaultProvince.equals("US-MS") ? " selected" : ""%>>US-MS-Mississippi</option>
                                    <option value="US-MT" <%=defaultProvince.equals("US-MT") ? " selected" : ""%>>US-MT-Montana</option>
                                    <option value="US-NC" <%=defaultProvince.equals("US-NC") ? " selected" : ""%>>US-NC-North Carolina</option>
                                    <option value="US-ND" <%=defaultProvince.equals("US-ND") ? " selected" : ""%>>US-ND-North Dakota</option>
                                    <option value="US-NE" <%=defaultProvince.equals("US-NE") ? " selected" : ""%>>US-NE-Nebraska</option>
                                    <option value="US-NH" <%=defaultProvince.equals("US-NH") ? " selected" : ""%>>US-NH-New Hampshire</option>
                                    <option value="US-NJ" <%=defaultProvince.equals("US-NJ") ? " selected" : ""%>>US-NJ-New Jersey</option>
                                    <option value="US-NM" <%=defaultProvince.equals("US-NM") ? " selected" : ""%>>US-NM-New Mexico</option>
                                    <option value="US-NU" <%=defaultProvince.equals("US-NU") ? " selected" : ""%>>US-NU-Nunavut</option>
                                    <option value="US-NV" <%=defaultProvince.equals("US-NV") ? " selected" : ""%>>US-NV-Nevada</option>
                                    <option value="US-NY" <%=defaultProvince.equals("US-NY") ? " selected" : ""%>>US-NY-New York</option>
                                    <option value="US-OH" <%=defaultProvince.equals("US-OH") ? " selected" : ""%>>US-OH-Ohio</option>
                                    <option value="US-OK" <%=defaultProvince.equals("US-OK") ? " selected" : ""%>>US-OK-Oklahoma</option>
                                    <option value="US-OR" <%=defaultProvince.equals("US-OR") ? " selected" : ""%>>US-OR-Oregon</option>
                                    <option value="US-PA" <%=defaultProvince.equals("US-PA") ? " selected" : ""%>>US-PA-Pennsylvania</option>
                                    <option value="US-PR" <%=defaultProvince.equals("US-PR") ? " selected" : ""%>>US-PR-Puerto Rico</option>
                                    <option value="US-RI" <%=defaultProvince.equals("US-RI") ? " selected" : ""%>>US-RI-Rhode Island</option>
                                    <option value="US-SC" <%=defaultProvince.equals("US-SC") ? " selected" : ""%>>US-SC-South Carolina</option>
                                    <option value="US-SD" <%=defaultProvince.equals("US-SD") ? " selected" : ""%>>US-SD-South Dakota</option>
                                    <option value="US-TN" <%=defaultProvince.equals("US-TN") ? " selected" : ""%>>US-TN-Tennessee</option>
                                    <option value="US-TX" <%=defaultProvince.equals("US-TX") ? " selected" : ""%>>US-TX-Texas</option>
                                    <option value="US-UT" <%=defaultProvince.equals("US-UT") ? " selected" : ""%>>US-UT-Utah</option>
                                    <option value="US-VA" <%=defaultProvince.equals("US-VA") ? " selected" : ""%>>US-VA-Virginia</option>
                                    <option value="US-VI" <%=defaultProvince.equals("US-VI") ? " selected" : ""%>>US-VI-Virgin Islands</option>
                                    <option value="US-VT" <%=defaultProvince.equals("US-VT") ? " selected" : ""%>>US-VT-Vermont</option>
                                    <option value="US-WA" <%=defaultProvince.equals("US-WA") ? " selected" : ""%>>US-WA-Washington</option>
                                    <option value="US-WI" <%=defaultProvince.equals("US-WI") ? " selected" : ""%>>US-WI-Wisconsin</option>
                                    <option value="US-WV" <%=defaultProvince.equals("US-WV") ? " selected" : ""%>>US-WV-West Virginia</option>
                                    <option value="US-WY" <%=defaultProvince.equals("US-WY") ? " selected" : ""%>>US-WY-Wyoming</option>
                                    <% } %>
                                </select>
                                <% } %>
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0">
                                    <% if (oscarProps.getProperty("demographicLabelPostal") == null) { %>
                                    <fmt:message key="demographic.demographicaddrecordhtm.formPostal"/>
                                    <% if ("false".equals(CarlosProperties.getInstance().getProperty("skip_postal_code_validation", "false"))) { %>
                                    <span style="color:red">*</span>
                                    <% } %>
                                    <% } else {
                                        out.print(oscarProps.getProperty("demographicLabelPostal"));
                                    } %>:
                                </label>
                            </div>
                            <div class="col-sm-4">
                                <input type="text" id="postal" name="postal" class="form-control" onBlur="upCaseCtrl(this)">
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formResidentialAddress"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <input id="residentialAddress" type="text" name="residentialAddress" class="form-control"/>
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formResidentialCity"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <input type="text" id="residentialCity" name="residentialCity" class="form-control" value=""/>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0">
                                    <fmt:message key="demographic.demographicaddrecordhtm.formResidentialProvince"/>:
                                </label>
                            </div>
                            <div class="col-sm-4">
                                <%
                                    if ("true".equals(CarlosProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                %>
                                <select name="residentialProvince" id="residentialProvince" class="form-select"></select>
                                <div class="mt-1">
                                    <label class="me-1"><fmt:message key="demographic.addFormPersonal.labelFilterByCountry"/></label>
                                    <select name="residentialCountry" id="residentialCountry" class="form-select d-inline-block w-auto"></select>
                                </div>
                                <% } else { %>
                                <select id="residentialProvince" name="residentialProvince" class="form-select">
                                    <option value="OT" <%=defaultProvince.equals("") || defaultProvince.equals("OT") ? " selected" : ""%>>Other</option>
                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>" <%=province.equals(defaultProvince) ? " selected" : ""%>><%=li.next()%></option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB" <%=defaultProvince.equals("AB") ? " selected" : ""%>>AB-Alberta</option>
                                    <option value="BC" <%=defaultProvince.equals("BC") ? " selected" : ""%>>BC-British Columbia</option>
                                    <option value="MB" <%=defaultProvince.equals("MB") ? " selected" : ""%>>MB-Manitoba</option>
                                    <option value="NB" <%=defaultProvince.equals("NB") ? " selected" : ""%>>NB-New Brunswick</option>
                                    <option value="NL" <%=defaultProvince.equals("NL") ? " selected" : ""%>>NL-Newfoundland &amp; Labrador</option>
                                    <option value="NT" <%=defaultProvince.equals("NT") ? " selected" : ""%>>NT-Northwest Territory</option>
                                    <option value="NS" <%=defaultProvince.equals("NS") ? " selected" : ""%>>NS-Nova Scotia</option>
                                    <option value="NU" <%=defaultProvince.equals("NU") ? " selected" : ""%>>NU-Nunavut</option>
                                    <option value="ON" <%=defaultProvince.equals("ON") ? " selected" : ""%>>ON-Ontario</option>
                                    <option value="PE" <%=defaultProvince.equals("PE") ? " selected" : ""%>>PE-Prince Edward Island</option>
                                    <option value="QC" <%=defaultProvince.equals("QC") ? " selected" : ""%>>QC-Quebec</option>
                                    <option value="SK" <%=defaultProvince.equals("SK") ? " selected" : ""%>>SK-Saskatchewan</option>
                                    <option value="YT" <%=defaultProvince.equals("YT") ? " selected" : ""%>>YT-Yukon</option>
                                    <option value="US" <%=defaultProvince.equals("US") ? " selected" : ""%>>US resident</option>
                                    <option value="US-AK" <%=defaultProvince.equals("US-AK") ? " selected" : ""%>>US-AK-Alaska</option>
                                    <option value="US-AL" <%=defaultProvince.equals("US-AL") ? " selected" : ""%>>US-AL-Alabama</option>
                                    <option value="US-AR" <%=defaultProvince.equals("US-AR") ? " selected" : ""%>>US-AR-Arkansas</option>
                                    <option value="US-AZ" <%=defaultProvince.equals("US-AZ") ? " selected" : ""%>>US-AZ-Arizona</option>
                                    <option value="US-CA" <%=defaultProvince.equals("US-CA") ? " selected" : ""%>>US-CA-California</option>
                                    <option value="US-CO" <%=defaultProvince.equals("US-CO") ? " selected" : ""%>>US-CO-Colorado</option>
                                    <option value="US-CT" <%=defaultProvince.equals("US-CT") ? " selected" : ""%>>US-CT-Connecticut</option>
                                    <option value="US-CZ" <%=defaultProvince.equals("US-CZ") ? " selected" : ""%>>US-CZ-Canal Zone</option>
                                    <option value="US-DC" <%=defaultProvince.equals("US-DC") ? " selected" : ""%>>US-DC-District Of Columbia</option>
                                    <option value="US-DE" <%=defaultProvince.equals("US-DE") ? " selected" : ""%>>US-DE-Delaware</option>
                                    <option value="US-FL" <%=defaultProvince.equals("US-FL") ? " selected" : ""%>>US-FL-Florida</option>
                                    <option value="US-GA" <%=defaultProvince.equals("US-GA") ? " selected" : ""%>>US-GA-Georgia</option>
                                    <option value="US-GU" <%=defaultProvince.equals("US-GU") ? " selected" : ""%>>US-GU-Guam</option>
                                    <option value="US-HI" <%=defaultProvince.equals("US-HI") ? " selected" : ""%>>US-HI-Hawaii</option>
                                    <option value="US-IA" <%=defaultProvince.equals("US-IA") ? " selected" : ""%>>US-IA-Iowa</option>
                                    <option value="US-ID" <%=defaultProvince.equals("US-ID") ? " selected" : ""%>>US-ID-Idaho</option>
                                    <option value="US-IL" <%=defaultProvince.equals("US-IL") ? " selected" : ""%>>US-IL-Illinois</option>
                                    <option value="US-IN" <%=defaultProvince.equals("US-IN") ? " selected" : ""%>>US-IN-Indiana</option>
                                    <option value="US-KS" <%=defaultProvince.equals("US-KS") ? " selected" : ""%>>US-KS-Kansas</option>
                                    <option value="US-KY" <%=defaultProvince.equals("US-KY") ? " selected" : ""%>>US-KY-Kentucky</option>
                                    <option value="US-LA" <%=defaultProvince.equals("US-LA") ? " selected" : ""%>>US-LA-Louisiana</option>
                                    <option value="US-MA" <%=defaultProvince.equals("US-MA") ? " selected" : ""%>>US-MA-Massachusetts</option>
                                    <option value="US-MD" <%=defaultProvince.equals("US-MD") ? " selected" : ""%>>US-MD-Maryland</option>
                                    <option value="US-ME" <%=defaultProvince.equals("US-ME") ? " selected" : ""%>>US-ME-Maine</option>
                                    <option value="US-MI" <%=defaultProvince.equals("US-MI") ? " selected" : ""%>>US-MI-Michigan</option>
                                    <option value="US-MN" <%=defaultProvince.equals("US-MN") ? " selected" : ""%>>US-MN-Minnesota</option>
                                    <option value="US-MO" <%=defaultProvince.equals("US-MO") ? " selected" : ""%>>US-MO-Missouri</option>
                                    <option value="US-MS" <%=defaultProvince.equals("US-MS") ? " selected" : ""%>>US-MS-Mississippi</option>
                                    <option value="US-MT" <%=defaultProvince.equals("US-MT") ? " selected" : ""%>>US-MT-Montana</option>
                                    <option value="US-NC" <%=defaultProvince.equals("US-NC") ? " selected" : ""%>>US-NC-North Carolina</option>
                                    <option value="US-ND" <%=defaultProvince.equals("US-ND") ? " selected" : ""%>>US-ND-North Dakota</option>
                                    <option value="US-NE" <%=defaultProvince.equals("US-NE") ? " selected" : ""%>>US-NE-Nebraska</option>
                                    <option value="US-NH" <%=defaultProvince.equals("US-NH") ? " selected" : ""%>>US-NH-New Hampshire</option>
                                    <option value="US-NJ" <%=defaultProvince.equals("US-NJ") ? " selected" : ""%>>US-NJ-New Jersey</option>
                                    <option value="US-NM" <%=defaultProvince.equals("US-NM") ? " selected" : ""%>>US-NM-New Mexico</option>
                                    <option value="US-NU" <%=defaultProvince.equals("US-NU") ? " selected" : ""%>>US-NU-Nunavut</option>
                                    <option value="US-NV" <%=defaultProvince.equals("US-NV") ? " selected" : ""%>>US-NV-Nevada</option>
                                    <option value="US-NY" <%=defaultProvince.equals("US-NY") ? " selected" : ""%>>US-NY-New York</option>
                                    <option value="US-OH" <%=defaultProvince.equals("US-OH") ? " selected" : ""%>>US-OH-Ohio</option>
                                    <option value="US-OK" <%=defaultProvince.equals("US-OK") ? " selected" : ""%>>US-OK-Oklahoma</option>
                                    <option value="US-OR" <%=defaultProvince.equals("US-OR") ? " selected" : ""%>>US-OR-Oregon</option>
                                    <option value="US-PA" <%=defaultProvince.equals("US-PA") ? " selected" : ""%>>US-PA-Pennsylvania</option>
                                    <option value="US-PR" <%=defaultProvince.equals("US-PR") ? " selected" : ""%>>US-PR-Puerto Rico</option>
                                    <option value="US-RI" <%=defaultProvince.equals("US-RI") ? " selected" : ""%>>US-RI-Rhode Island</option>
                                    <option value="US-SC" <%=defaultProvince.equals("US-SC") ? " selected" : ""%>>US-SC-South Carolina</option>
                                    <option value="US-SD" <%=defaultProvince.equals("US-SD") ? " selected" : ""%>>US-SD-South Dakota</option>
                                    <option value="US-TN" <%=defaultProvince.equals("US-TN") ? " selected" : ""%>>US-TN-Tennessee</option>
                                    <option value="US-TX" <%=defaultProvince.equals("US-TX") ? " selected" : ""%>>US-TX-Texas</option>
                                    <option value="US-UT" <%=defaultProvince.equals("US-UT") ? " selected" : ""%>>US-UT-Utah</option>
                                    <option value="US-VA" <%=defaultProvince.equals("US-VA") ? " selected" : ""%>>US-VA-Virginia</option>
                                    <option value="US-VI" <%=defaultProvince.equals("US-VI") ? " selected" : ""%>>US-VI-Virgin Islands</option>
                                    <option value="US-VT" <%=defaultProvince.equals("US-VT") ? " selected" : ""%>>US-VT-Vermont</option>
                                    <option value="US-WA" <%=defaultProvince.equals("US-WA") ? " selected" : ""%>>US-WA-Washington</option>
                                    <option value="US-WI" <%=defaultProvince.equals("US-WI") ? " selected" : ""%>>US-WI-Wisconsin</option>
                                    <option value="US-WV" <%=defaultProvince.equals("US-WV") ? " selected" : ""%>>US-WV-West Virginia</option>
                                    <option value="US-WY" <%=defaultProvince.equals("US-WY") ? " selected" : ""%>>US-WY-Wyoming</option>
                                    <% } %>
                                </select>
                                <% } %>
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formResidentialPostal"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <input type="text" id="residentialPostal" name="residentialPostal" class="form-control" onBlur="upCaseCtrl(this)">
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="phoneLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPhoneHome"/>:</label>
                            </div>
                            <div class="col-sm-4" id="phoneCell">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" id="phone" name="phone" class="form-control"
                                           onBlur="formatPhoneNum()"
                                           value="<%=props.getProperty("phoneprefix", "905-")%>">
                                    <span class="text-nowrap"><fmt:message key="demographic.demographicaddrecordhtm.Ext"/>:</span>
                                    <input type="text" id="hPhoneExt" name="hPhoneExt" value="" class="form-control" style="width:4em"/>
                                </div>
                            </div>
                            <div class="col-sm-2 text-end" id="phoneWorkLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPhoneWork"/>:</label>
                            </div>
                            <div class="col-sm-4" id="phoneWorkCell">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" name="phone2" class="form-control" onBlur="formatPhoneNum()" value="">
                                    <span class="text-nowrap"><fmt:message key="demographic.demographicaddrecordhtm.Ext"/>:</span>
                                    <input type="text" name="wPhoneExt" value="" class="form-control" style="width:4em"/>
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="phoneCellLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPhoneCell"/>:</label>
                            </div>
                            <div class="col-sm-4" id="phoneCellCell">
                                <input type="text" name="demo_cell" class="form-control" onBlur="formatPhoneNum()">
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPhoneComment"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <textarea rows="2" class="form-control" name="phoneComment"></textarea>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="newsletterLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter"/>:</label>
                            </div>
                            <div class="col-sm-4" id="newsletterCell">
                                <select name="newsletter" class="form-select">
                                    <option value="Unknown" selected><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optUnknown"/></option>
                                    <option value="No"><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optNo"/></option>
                                    <option value="Paper"><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optPaper"/></option>
                                    <option value="Electronic"><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optElectronic"/></option>
                                </select>
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographiceditdemographic.aboriginal"/>:</label>
                            </div>
                            <div class="col-sm-4">
                                <select name="aboriginal" class="form-select">
                                    <option value=""><fmt:message key="demographic.addFormPersonal.optAboriginalUnknown"/></option>
                                    <option value="No"><fmt:message key="demographic.addFormPersonal.optAboriginalNo"/></option>
                                    <option value="Yes"><fmt:message key="demographic.addFormPersonal.optAboriginalYes"/></option>
                                </select>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="emailLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formEMail"/>:</label>
                            </div>
                            <div class="col-sm-4" id="emailCell">
                                <input type="text" id="email" name="email" class="form-control" value="">
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="dobLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formDOB"/>:<span style="color:red;"></span></label>
                            </div>
                            <div class="col-sm-4" id="dobTbl">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" name="year_of_birth" placeholder="yyyy" id="year_of_birth"
                                           maxlength="4" class="form-control" style="width:5em">
                                    <select name="month_of_birth" id="month_of_birth" class="form-select w-auto">
                                        <option value="01">01</option>
                                        <option value="02">02</option>
                                        <option value="03">03</option>
                                        <option value="04">04</option>
                                        <option value="05">05</option>
                                        <option selected value="06">06</option>
                                        <option value="07">07</option>
                                        <option value="08">08</option>
                                        <option value="09">09</option>
                                        <option value="10">10</option>
                                        <option value="11">11</option>
                                        <option value="12">12</option>
                                    </select>
                                    <select name="date_of_birth" id="date_of_birth" class="form-select w-auto">
                                        <option value="01">01</option>
                                        <option value="02">02</option>
                                        <option value="03">03</option>
                                        <option value="04">04</option>
                                        <option value="05">05</option>
                                        <option value="06">06</option>
                                        <option value="07">07</option>
                                        <option value="08">08</option>
                                        <option value="09">09</option>
                                        <option value="10">10</option>
                                        <option value="11">11</option>
                                        <option value="12">12</option>
                                        <option value="13">13</option>
                                        <option value="14">14</option>
                                        <option selected value="15">15</option>
                                        <option value="16">16</option>
                                        <option value="17">17</option>
                                        <option value="18">18</option>
                                        <option value="19">19</option>
                                        <option value="20">20</option>
                                        <option value="21">21</option>
                                        <option value="22">22</option>
                                        <option value="23">23</option>
                                        <option value="24">24</option>
                                        <option value="25">25</option>
                                        <option value="26">26</option>
                                        <option value="27">27</option>
                                        <option value="28">28</option>
                                        <option value="29">29</option>
                                        <option value="30">30</option>
                                        <option value="31">31</option>
                                    </select>
                                </div>
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formPronouns"/></label>
                            </div>
                            <div class="col-sm-4">
                                <input type="text" id="patientPronouns" name="pronouns" class="form-control"/>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="genderLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formSex"/>:<font color="red"></font></label>
                            </div>
                            <% // Determine if curUser has selected a default sex in preferences
                                UserProperty sexProp = userPropertyDAO.getProp(curUser_no, UserProperty.DEFAULT_SEX);
                                String sex = "";
                                if (sexProp != null) {
                                    sex = sexProp.getValue();
                                } else {
                                    // Access defaultsex system property
                                    sex = props.getProperty("defaultsex", "");
                                }
                            %>
                            <div class="col-sm-4" id="gender">
                                <select name="sex" id="sex" class="form-select">
                                    <option value=""></option>
                                    <% for (Gender gn : Gender.values()) {
                                        String genderDisplayText = DemographicEditHelper.getGenderDisplayText(request.getLocale(), gn.name());
                                    %>
                                    <option value="<%=gn.name()%>" <%=((sex.toUpperCase().equals(gn.name())) ? "selected=\"selected\"" : "") %>><%=genderDisplayText%></option>
                                    <% } %>
                                </select>
                            </div>
                            <div class="col-sm-2 text-end">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formGender"/></label>
                            </div>
                            <div class="col-sm-4">
                                <input type="text" id="patientGender" name="gender" class="form-control"/>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="hinLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formHIN"/>:</label>
                            </div>
                            <div class="col-sm-4" id="hinVer">
                                <div class="d-flex gap-1 align-items-center">
                                    <input type="text" name="hin" id="hin" class="form-control" onfocus="autoFillHin()">
                                    <span class="text-nowrap"><fmt:message key="demographic.demographicaddrecordhtm.formVer"/>:</span>
                                    <input type="text" id="ver" name="ver" value="" class="form-control" style="width:4em" onBlur="upCaseCtrl(this)">
                                </div>
                            </div>
                            <div class="col-sm-2 text-end" id="effDateLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formEFFDate"/>:</label>
                            </div>
                            <div class="col-sm-4" id="effDate">
                                <div class="d-flex gap-1">
                                    <input type="text" placeholder="yyyy" id="eff_date_year" name="eff_date_year" class="form-control" maxlength="4" style="width:5em">
                                    <input type="text" placeholder="mm" id="eff_date_month" name="eff_date_month" class="form-control" maxlength="2" style="width:3.5em">
                                    <input type="text" placeholder="dd" id="eff_date_date" name="eff_date_date" class="form-control" maxlength="2" style="width:3.5em">
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="hcTypeLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.formHCType"/>:</label>
                            </div>
                            <div class="col-sm-4" id="hcType">
                                <select name="hc_type" id="hc_type" class="form-select">
                                    <option value="OT" <%=HCType.equals("") || HCType.equals("OT") ? " selected" : ""%>>Other</option>
                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>"<%=province.equals(HCType) ? " selected" : ""%>><%=li.next()%></option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB"<%=HCType.equals("AB") ? " selected" : ""%>>AB-Alberta</option>
                                    <option value="BC"<%=HCType.equals("BC") ? " selected" : ""%>>BC-British Columbia</option>
                                    <option value="MB"<%=HCType.equals("MB") ? " selected" : ""%>>MB-Manitoba</option>
                                    <option value="NB"<%=HCType.equals("NB") ? " selected" : ""%>>NB-New Brunswick</option>
                                    <option value="NL"<%=HCType.equals("NL") ? " selected" : ""%>>NL-Newfoundland &amp; Labrador</option>
                                    <option value="NT"<%=HCType.equals("NT") ? " selected" : ""%>>NT-Northwest Territory</option>
                                    <option value="NS"<%=HCType.equals("NS") ? " selected" : ""%>>NS-Nova Scotia</option>
                                    <option value="NU"<%=HCType.equals("NU") ? " selected" : ""%>>NU-Nunavut</option>
                                    <option value="ON"<%=HCType.equals("ON") ? " selected" : ""%>>ON-Ontario</option>
                                    <option value="PE"<%=HCType.equals("PE") ? " selected" : ""%>>PE-Prince Edward Island</option>
                                    <option value="QC"<%=HCType.equals("QC") ? " selected" : ""%>>QC-Quebec</option>
                                    <option value="SK"<%=HCType.equals("SK") ? " selected" : ""%>>SK-Saskatchewan</option>
                                    <option value="YT"<%=HCType.equals("YT") ? " selected" : ""%>>YT-Yukon</option>
                                    <option value="US"<%=HCType.equals("US") ? " selected" : ""%>>US resident</option>
                                    <option value="US-AK" <%=HCType.equals("US-AK") ? " selected" : ""%>>US-AK-Alaska</option>
                                    <option value="US-AL" <%=HCType.equals("US-AL") ? " selected" : ""%>>US-AL-Alabama</option>
                                    <option value="US-AR" <%=HCType.equals("US-AR") ? " selected" : ""%>>US-AR-Arkansas</option>
                                    <option value="US-AZ" <%=HCType.equals("US-AZ") ? " selected" : ""%>>US-AZ-Arizona</option>
                                    <option value="US-CA" <%=HCType.equals("US-CA") ? " selected" : ""%>>US-CA-California</option>
                                    <option value="US-CO" <%=HCType.equals("US-CO") ? " selected" : ""%>>US-CO-Colorado</option>
                                    <option value="US-CT" <%=HCType.equals("US-CT") ? " selected" : ""%>>US-CT-Connecticut</option>
                                    <option value="US-CZ" <%=HCType.equals("US-CZ") ? " selected" : ""%>>US-CZ-Canal Zone</option>
                                    <option value="US-DC" <%=HCType.equals("US-DC") ? " selected" : ""%>>US-DC-District Of Columbia</option>
                                    <option value="US-DE" <%=HCType.equals("US-DE") ? " selected" : ""%>>US-DE-Delaware</option>
                                    <option value="US-FL" <%=HCType.equals("US-FL") ? " selected" : ""%>>US-FL-Florida</option>
                                    <option value="US-GA" <%=HCType.equals("US-GA") ? " selected" : ""%>>US-GA-Georgia</option>
                                    <option value="US-GU" <%=HCType.equals("US-GU") ? " selected" : ""%>>US-GU-Guam</option>
                                    <option value="US-HI" <%=HCType.equals("US-HI") ? " selected" : ""%>>US-HI-Hawaii</option>
                                    <option value="US-IA" <%=HCType.equals("US-IA") ? " selected" : ""%>>US-IA-Iowa</option>
                                    <option value="US-ID" <%=HCType.equals("US-ID") ? " selected" : ""%>>US-ID-Idaho</option>
                                    <option value="US-IL" <%=HCType.equals("US-IL") ? " selected" : ""%>>US-IL-Illinois</option>
                                    <option value="US-IN" <%=HCType.equals("US-IN") ? " selected" : ""%>>US-IN-Indiana</option>
                                    <option value="US-KS" <%=HCType.equals("US-KS") ? " selected" : ""%>>US-KS-Kansas</option>
                                    <option value="US-KY" <%=HCType.equals("US-KY") ? " selected" : ""%>>US-KY-Kentucky</option>
                                    <option value="US-LA" <%=HCType.equals("US-LA") ? " selected" : ""%>>US-LA-Louisiana</option>
                                    <option value="US-MA" <%=HCType.equals("US-MA") ? " selected" : ""%>>US-MA-Massachusetts</option>
                                    <option value="US-MD" <%=HCType.equals("US-MD") ? " selected" : ""%>>US-MD-Maryland</option>
                                    <option value="US-ME" <%=HCType.equals("US-ME") ? " selected" : ""%>>US-ME-Maine</option>
                                    <option value="US-MI" <%=HCType.equals("US-MI") ? " selected" : ""%>>US-MI-Michigan</option>
                                    <option value="US-MN" <%=HCType.equals("US-MN") ? " selected" : ""%>>US-MN-Minnesota</option>
                                    <option value="US-MO" <%=HCType.equals("US-MO") ? " selected" : ""%>>US-MO-Missouri</option>
                                    <option value="US-MS" <%=HCType.equals("US-MS") ? " selected" : ""%>>US-MS-Mississippi</option>
                                    <option value="US-MT" <%=HCType.equals("US-MT") ? " selected" : ""%>>US-MT-Montana</option>
                                    <option value="US-NC" <%=HCType.equals("US-NC") ? " selected" : ""%>>US-NC-North Carolina</option>
                                    <option value="US-ND" <%=HCType.equals("US-ND") ? " selected" : ""%>>US-ND-North Dakota</option>
                                    <option value="US-NE" <%=HCType.equals("US-NE") ? " selected" : ""%>>US-NE-Nebraska</option>
                                    <option value="US-NH" <%=HCType.equals("US-NH") ? " selected" : ""%>>US-NH-New Hampshire</option>
                                    <option value="US-NJ" <%=HCType.equals("US-NJ") ? " selected" : ""%>>US-NJ-New Jersey</option>
                                    <option value="US-NM" <%=HCType.equals("US-NM") ? " selected" : ""%>>US-NM-New Mexico</option>
                                    <option value="US-NU" <%=HCType.equals("US-NU") ? " selected" : ""%>>US-NU-Nunavut</option>
                                    <option value="US-NV" <%=HCType.equals("US-NV") ? " selected" : ""%>>US-NV-Nevada</option>
                                    <option value="US-NY" <%=HCType.equals("US-NY") ? " selected" : ""%>>US-NY-New York</option>
                                    <option value="US-OH" <%=HCType.equals("US-OH") ? " selected" : ""%>>US-OH-Ohio</option>
                                    <option value="US-OK" <%=HCType.equals("US-OK") ? " selected" : ""%>>US-OK-Oklahoma</option>
                                    <option value="US-OR" <%=HCType.equals("US-OR") ? " selected" : ""%>>US-OR-Oregon</option>
                                    <option value="US-PA" <%=HCType.equals("US-PA") ? " selected" : ""%>>US-PA-Pennsylvania</option>
                                    <option value="US-PR" <%=HCType.equals("US-PR") ? " selected" : ""%>>US-PR-Puerto Rico</option>
                                    <option value="US-RI" <%=HCType.equals("US-RI") ? " selected" : ""%>>US-RI-Rhode Island</option>
                                    <option value="US-SC" <%=HCType.equals("US-SC") ? " selected" : ""%>>US-SC-South Carolina</option>
                                    <option value="US-SD" <%=HCType.equals("US-SD") ? " selected" : ""%>>US-SD-South Dakota</option>
                                    <option value="US-TN" <%=HCType.equals("US-TN") ? " selected" : ""%>>US-TN-Tennessee</option>
                                    <option value="US-TX" <%=HCType.equals("US-TX") ? " selected" : ""%>>US-TX-Texas</option>
                                    <option value="US-UT" <%=HCType.equals("US-UT") ? " selected" : ""%>>US-UT-Utah</option>
                                    <option value="US-VA" <%=HCType.equals("US-VA") ? " selected" : ""%>>US-VA-Virginia</option>
                                    <option value="US-VI" <%=HCType.equals("US-VI") ? " selected" : ""%>>US-VI-Virgin Islands</option>
                                    <option value="US-VT" <%=HCType.equals("US-VT") ? " selected" : ""%>>US-VT-Vermont</option>
                                    <option value="US-WA" <%=HCType.equals("US-WA") ? " selected" : ""%>>US-WA-Washington</option>
                                    <option value="US-WI" <%=HCType.equals("US-WI") ? " selected" : ""%>>US-WI-Wisconsin</option>
                                    <option value="US-WV" <%=HCType.equals("US-WV") ? " selected" : ""%>>US-WV-West Virginia</option>
                                    <option value="US-WY" <%=HCType.equals("US-WY") ? " selected" : ""%>>US-WY-Wyoming</option>
                                    <% } %>
                                </select>
                            </div>
                            <div class="col-sm-2 text-end" id="renewDateLbl">
                                <label class="fw-bold col-form-label py-0">*<fmt:message key="demographic.demographiceditdemographic.formHCRenewDate"/>:</label>
                            </div>
                            <div class="col-sm-4" id="renewDate">
                                <div class="d-flex gap-1">
                                    <input type="text" placeholder="yyyy" id="hc_renew_date_year" name="hc_renew_date_year" class="form-control" maxlength="4" value="" style="width:5em">
                                    <input type="text" placeholder="mm" id="hc_renew_date_month" name="hc_renew_date_month" class="form-control" maxlength="2" value="" style="width:3.5em">
                                    <input type="text" placeholder="dd" id="hc_renew_date_date" name="hc_renew_date_date" class="form-control" maxlength="2" value="" style="width:3.5em">
                                </div>
                            </div>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <div class="col-sm-2 text-end" id="countryLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.msgCountryOfOrigin"/>:</label>
                            </div>
                            <div class="col-sm-4" id="countryCell">
                                <select id="countryOfOrigin" name="countryOfOrigin" class="form-select">
                                    <option value="-1"><fmt:message key="demographic.demographicaddrecordhtm.msgNotSet"/></option>
                                    <%for (CountryCode cc : countryList) { %>
                                    <option value="<%=cc.getCountryId()%>"><%=cc.getCountryName() %></option>
                                    <%}%>
                                </select>
                            </div>
                            <oscar:oscarPropertiesCheck property="privateConsentEnabled" value="true">
                                <%
                                    String[] privateConsentPrograms = CarlosProperties.getInstance().getProperty("privateConsentPrograms", "").split(",");
                                    ProgramProvider pp2 = programManager2.getCurrentProgramInDomain(loggedInInfo, loggedInInfo.getLoggedInProviderNo());
                                    boolean showConsentsThisTime = false;
                                    if (pp2 != null) {
                                        for (int x = 0; x < privateConsentPrograms.length; x++) {
                                            if (privateConsentPrograms[x].equals(pp2.getProgramId().toString())) {
                                                showConsentsThisTime = true;
                                            }
                                        }
                                    }

                                    if (showConsentsThisTime) {
                                %>
                                <div class="col-sm-4">
                                    <div class="form-check">
                                        <input type="radio" name="usSigned" value="signed" class="form-check-input" id="usConsentSigned">
                                        <label class="form-check-label" for="usConsentSigned"><fmt:message key="demographic.addFormPersonal.optUsConsentSigned"/></label>
                                    </div>
                                    <div class="form-check">
                                        <input type="radio" name="usSigned" value="unsigned" class="form-check-input" id="usConsentUnsigned">
                                        <label class="form-check-label" for="usConsentUnsigned"><fmt:message key="demographic.addFormPersonal.optUsConsentNotSigned"/></label>
                                    </div>
                                </div>
                                <% } %>
                            </oscar:oscarPropertiesCheck>
                            <oscar:oscarPropertiesCheck property="privateConsentEnabled" value="false">
                                <%-- placeholder --%>
                            </oscar:oscarPropertiesCheck>
                        </div>

                        <div class="row mb-2 align-items-center">
                            <%-- TOGGLE FIRST NATIONS MODULE --%>
                            <oscar:oscarPropertiesCheck value="true" defaultVal="false" property="FIRST_NATIONS_MODULE">
                                <jsp:include page="/WEB-INF/jsp/demographic/manageFirstNationsModule.jsp" flush="true">
                                    <jsp:param name="demo" value="0"/>
                                </jsp:include>
                            </oscar:oscarPropertiesCheck>
                            <%-- END TOGGLE FIRST NATIONS MODULE --%>
                            <div class="col-sm-2 text-end" id="sinNoLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.msgSIN"/>:</label>
                            </div>
                            <div class="col-sm-4" id="sinNoCell">
                                <input type="text" name="sin" class="form-control">
                            </div>
                            <div class="col-sm-2 text-end" id="cytologyLbl">
                                <label class="fw-bold col-form-label py-0"><fmt:message key="demographic.demographicaddrecordhtm.cytolNum"/>:</label>
                            </div>
                            <div class="col-sm-4" id="cytologyCell">
                                <input type="text" name="cytolNum" class="form-control">
                            </div>
                        </div>

                        <%-- Cross-file row: label cells are in add-form-clinical.jsp --%>
                        <div class="row mb-2 align-items-center">
