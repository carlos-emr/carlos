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

    // Pre-fill values passed from the HL7 lab result (e.g. via PatientSearch.jsp)
    String prefillLastName      = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_last_name"));
    String prefillFirstName     = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_first_name"));
    String prefillAddress       = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_address"));
    String prefillCity          = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_city"));
    String prefillProvince      = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_province"));
    String prefillPostal        = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_postal"));
    String prefillPhone         = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_phone"));
    String prefillSex           = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_sex")).toUpperCase();
    String prefillYearOfBirth   = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_year_of_birth"));
    String prefillMonthOfBirth  = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_month_of_birth"));
    String prefillDateOfBirth   = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_date_of_birth"));
    String prefillHin           = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_hin"));
    String prefillVer           = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_ver"));
    String prefillHcType        = io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("prefill_hc_type"));
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


                    <table id="addDemographicTbl" bgcolor="#EEEEFF">


                        <%if (CarlosProperties.getInstance().getProperty("workflow_enhance") != null && CarlosProperties.getInstance().getProperty("workflow_enhance").equals("true")) { %>
                        <tr bgcolor="#CCCCFF">
                            <td colspan="4">
                                <input type="hidden" name="dboperation"
                                       value="add_record">
                                <input type="hidden" name="displaymode" value="Add Record">
                                <input type="submit" name="submit"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnAddRecord"/>">
                                <input type="button" name="Button"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnSwipeCard"/>"
                                       onclick="window.open('zadddemographicswipe.htm','', 'scrollbars=yes,resizable=yes,width=600,height=300')"
                                       ;>
                                <input type="button" name="Button"
                                       value="<fmt:message key="demographic.demographicaddrecordhtm.btnCancel"/>"
                                       onclick=self.close();>
                            </td>
                        </tr>
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
                            // Prefill values from HL7 lab take precedence over search keyword
                            if (!prefillLastName.isEmpty())  lastNameVal  = prefillLastName;
                            if (!prefillFirstName.isEmpty()) firstNameVal = prefillFirstName;
                            // Province for address select (separate from hc_type)
                            String selectedProvince = prefillProvince.isEmpty() ? defaultProvince : prefillProvince;
                            // HC type for health card select
                            String selectedHcType = prefillHcType.isEmpty() ? HCType : prefillHcType;
                        %>

                        <tr id="rowWithLastName">
                            <td align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formLastName"/><span
                                    style="color:red;">:</span> </b></td>
                            <td id="lastName" align="left">
                                <input type="text" name="last_name" id="last_name" onBlur="upCaseCtrl(this)"
                                       value="<carlos:encode value='<%= lastNameVal %>' context="htmlAttribute"/>">

                            </td>
                            <td align="right" id="firstNameLbl"><b><fmt:message key="demographic.demographicaddrecordhtm.formFirstName"/><span
                                    style="color:red;">:</span> </b></td>
                            <td id="firstName" align="left">
                                <input type="text" name="first_name" id="first_name" onBlur="upCaseCtrl(this)"
                                       value="<carlos:encode value='<%= firstNameVal %>' context="htmlAttribute"/>">
                            </td>
                        </tr>
                        <tr>
                            <td align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formMiddleNames"/>: </b></td>
                            <td id="middleName" align="left">
                                <input type="text" name="middleNames" id="middleNames" onBlur="upCaseCtrl(this)"
                                       value="">

                            </td>
                            <td align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formNameUsed"/>:
                            </b></td>
                            <td align="left">
                                <input type="text" name="nameUsed" size="30" value="" onBlur="upCaseCtrl(this)"/>
                            </td>
                        </tr>
                        <tr>
                            <td id="languageLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.msgDemoLanguage"/><font
                                    color="red">:</font></b></td>
                            <td id="languageCell" align="left">
                                <select id="official_lang" name="official_lang">
                                    <option value="English" <%= vLocale.getLanguage().equals("en") ? " selected" : "" %>>
                                        <fmt:message key="demographic.demographiceaddrecordhtm.msgEnglish"/></option>
                                    <option value="French"  <%= vLocale.getLanguage().equals("fr") ? " selected" : "" %>>
                                        <fmt:message key="demographic.demographiceaddrecordhtm.msgFrench"/></option>
                                </select>
                            </td>
                            <td id="titleLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.msgDemoTitle"/><font
                                    color="red">:</font></b></td>
                            <td id="titleCell" align="left">
                                <select id="title" name="title" onchange="checkTitleSex(value);">
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
                            </td>
                        </tr>
                        <tr>
                            <td id="spokenLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.msgSpoken"/>:</b></td>
                            <td id="spokenCell"><select name="spoken_lang">
                                <%for (String sp_lang : Util.spokenLangProperties.getLangSorted()) { %>
                                <option value="<%=sp_lang %>"><%=sp_lang %>
                                </option>
                                <%} %>
                            </select>
                            </td>
                            <td><!-- placeholder --></td>
                            <td><!-- placeholder --></td>
                        </tr>

                        <tr valign="top">
                            <td id="addrLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formAddress"/>: </b></td>
                            <td id="addressCell" align="left"><input id="address" type="text" name="address" size=40
                                       value="<carlos:encode value='<%= prefillAddress %>' context="htmlAttribute"/>"/>

                            </td>
                            <td id="cityLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formCity"/>: </b></td>
                            <td id="cityCell" align="left"><input type="text" id="city" name="city"
                                                                  value="<carlos:encode value='<%= prefillCity.isEmpty() ? defaultCity : prefillCity %>' context="htmlAttribute"/>"/></td>
                        </tr>

                        <tr valign="top">
                            <td id="provLbl" align="right"><b>
                                <% if (oscarProps.getProperty("demographicLabelProvince") == null) { %>
                                <fmt:message key="demographic.demographicaddrecordhtm.formprovince"/>
                                <% } else {
                                    out.print(oscarProps.getProperty("demographicLabelProvince"));
                                } %> : </b></td>
                            <td id="provCell" align="left">
                                <%
                                    if ("true".equals(CarlosProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                %>
                                <select name="province" id="province"></select>
                                <br/>
                                Filter by Country: <select name="country" id="country"></select>

                                <% } else { %>
                                <select id="province" name="province">
                                    <option value="OT"
                                            <%=selectedProvince.equals("") || selectedProvince.equals("OT") ? " selected" : ""%>>
                                        Other
                                    </option>
                                        <%-- <option value="">None Selected</option> --%>
                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>"
                                            <%=province.equals(selectedProvince) ? " selected" : ""%>><%=li.next()%>
                                    </option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB" <%=selectedProvince.equals("AB") ? " selected" : ""%>>AB-Alberta
                                    </option>
                                    <option value="BC" <%=selectedProvince.equals("BC") ? " selected" : ""%>>BC-British
                                        Columbia
                                    </option>
                                    <option value="MB" <%=selectedProvince.equals("MB") ? " selected" : ""%>>
                                        MB-Manitoba
                                    </option>
                                    <option value="NB" <%=selectedProvince.equals("NB") ? " selected" : ""%>>NB-New
                                        Brunswick
                                    </option>
                                    <option value="NL" <%=selectedProvince.equals("NL") ? " selected" : ""%>>
                                        NL-Newfoundland & Labrador
                                    </option>
                                    <option value="NT" <%=selectedProvince.equals("NT") ? " selected" : ""%>>NT-Northwest
                                        Territory
                                    </option>
                                    <option value="NS" <%=selectedProvince.equals("NS") ? " selected" : ""%>>NS-Nova
                                        Scotia
                                    </option>
                                    <option value="NU" <%=selectedProvince.equals("NU") ? " selected" : ""%>>NU-Nunavut
                                    </option>
                                    <option value="ON" <%=selectedProvince.equals("ON") ? " selected" : ""%>>ON-Ontario
                                    </option>
                                    <option value="PE" <%=selectedProvince.equals("PE") ? " selected" : ""%>>PE-Prince
                                        Edward Island
                                    </option>
                                    <option value="QC" <%=selectedProvince.equals("QC") ? " selected" : ""%>>QC-Quebec
                                    </option>
                                    <option value="SK" <%=selectedProvince.equals("SK") ? " selected" : ""%>>
                                        SK-Saskatchewan
                                    </option>
                                    <option value="YT" <%=selectedProvince.equals("YT") ? " selected" : ""%>>YT-Yukon
                                    </option>
                                    <option value="US" <%=selectedProvince.equals("US") ? " selected" : ""%>>US
                                        resident
                                    </option>
                                    <option value="US-AK" <%=selectedProvince.equals("US-AK") ? " selected" : ""%>>
                                        US-AK-Alaska
                                    </option>
                                    <option value="US-AL" <%=selectedProvince.equals("US-AL") ? " selected" : ""%>>
                                        US-AL-Alabama
                                    </option>
                                    <option value="US-AR" <%=selectedProvince.equals("US-AR") ? " selected" : ""%>>
                                        US-AR-Arkansas
                                    </option>
                                    <option value="US-AZ" <%=selectedProvince.equals("US-AZ") ? " selected" : ""%>>
                                        US-AZ-Arizona
                                    </option>
                                    <option value="US-CA" <%=selectedProvince.equals("US-CA") ? " selected" : ""%>>
                                        US-CA-California
                                    </option>
                                    <option value="US-CO" <%=selectedProvince.equals("US-CO") ? " selected" : ""%>>
                                        US-CO-Colorado
                                    </option>
                                    <option value="US-CT" <%=selectedProvince.equals("US-CT") ? " selected" : ""%>>
                                        US-CT-Connecticut
                                    </option>
                                    <option value="US-CZ" <%=selectedProvince.equals("US-CZ") ? " selected" : ""%>>
                                        US-CZ-Canal Zone
                                    </option>
                                    <option value="US-DC" <%=selectedProvince.equals("US-DC") ? " selected" : ""%>>
                                        US-DC-District Of Columbia
                                    </option>
                                    <option value="US-DE" <%=selectedProvince.equals("US-DE") ? " selected" : ""%>>
                                        US-DE-Delaware
                                    </option>
                                    <option value="US-FL" <%=selectedProvince.equals("US-FL") ? " selected" : ""%>>
                                        US-FL-Florida
                                    </option>
                                    <option value="US-GA" <%=selectedProvince.equals("US-GA") ? " selected" : ""%>>
                                        US-GA-Georgia
                                    </option>
                                    <option value="US-GU" <%=selectedProvince.equals("US-GU") ? " selected" : ""%>>
                                        US-GU-Guam
                                    </option>
                                    <option value="US-HI" <%=selectedProvince.equals("US-HI") ? " selected" : ""%>>
                                        US-HI-Hawaii
                                    </option>
                                    <option value="US-IA" <%=selectedProvince.equals("US-IA") ? " selected" : ""%>>
                                        US-IA-Iowa
                                    </option>
                                    <option value="US-ID" <%=selectedProvince.equals("US-ID") ? " selected" : ""%>>
                                        US-ID-Idaho
                                    </option>
                                    <option value="US-IL" <%=selectedProvince.equals("US-IL") ? " selected" : ""%>>
                                        US-IL-Illinois
                                    </option>
                                    <option value="US-IN" <%=selectedProvince.equals("US-IN") ? " selected" : ""%>>
                                        US-IN-Indiana
                                    </option>
                                    <option value="US-KS" <%=selectedProvince.equals("US-KS") ? " selected" : ""%>>
                                        US-KS-Kansas
                                    </option>
                                    <option value="US-KY" <%=selectedProvince.equals("US-KY") ? " selected" : ""%>>
                                        US-KY-Kentucky
                                    </option>
                                    <option value="US-LA" <%=selectedProvince.equals("US-LA") ? " selected" : ""%>>
                                        US-LA-Louisiana
                                    </option>
                                    <option value="US-MA" <%=selectedProvince.equals("US-MA") ? " selected" : ""%>>
                                        US-MA-Massachusetts
                                    </option>
                                    <option value="US-MD" <%=selectedProvince.equals("US-MD") ? " selected" : ""%>>
                                        US-MD-Maryland
                                    </option>
                                    <option value="US-ME" <%=selectedProvince.equals("US-ME") ? " selected" : ""%>>
                                        US-ME-Maine
                                    </option>
                                    <option value="US-MI" <%=selectedProvince.equals("US-MI") ? " selected" : ""%>>
                                        US-MI-Michigan
                                    </option>
                                    <option value="US-MN" <%=selectedProvince.equals("US-MN") ? " selected" : ""%>>
                                        US-MN-Minnesota
                                    </option>
                                    <option value="US-MO" <%=selectedProvince.equals("US-MO") ? " selected" : ""%>>
                                        US-MO-Missouri
                                    </option>
                                    <option value="US-MS" <%=selectedProvince.equals("US-MS") ? " selected" : ""%>>
                                        US-MS-Mississippi
                                    </option>
                                    <option value="US-MT" <%=selectedProvince.equals("US-MT") ? " selected" : ""%>>
                                        US-MT-Montana
                                    </option>
                                    <option value="US-NC" <%=selectedProvince.equals("US-NC") ? " selected" : ""%>>
                                        US-NC-North Carolina
                                    </option>
                                    <option value="US-ND" <%=selectedProvince.equals("US-ND") ? " selected" : ""%>>
                                        US-ND-North Dakota
                                    </option>
                                    <option value="US-NE" <%=selectedProvince.equals("US-NE") ? " selected" : ""%>>
                                        US-NE-Nebraska
                                    </option>
                                    <option value="US-NH" <%=selectedProvince.equals("US-NH") ? " selected" : ""%>>
                                        US-NH-New Hampshire
                                    </option>
                                    <option value="US-NJ" <%=selectedProvince.equals("US-NJ") ? " selected" : ""%>>
                                        US-NJ-New Jersey
                                    </option>
                                    <option value="US-NM" <%=selectedProvince.equals("US-NM") ? " selected" : ""%>>
                                        US-NM-New Mexico
                                    </option>
                                    <option value="US-NU" <%=selectedProvince.equals("US-NU") ? " selected" : ""%>>
                                        US-NU-Nunavut
                                    </option>
                                    <option value="US-NV" <%=selectedProvince.equals("US-NV") ? " selected" : ""%>>
                                        US-NV-Nevada
                                    </option>
                                    <option value="US-NY" <%=selectedProvince.equals("US-NY") ? " selected" : ""%>>
                                        US-NY-New York
                                    </option>
                                    <option value="US-OH" <%=selectedProvince.equals("US-OH") ? " selected" : ""%>>
                                        US-OH-Ohio
                                    </option>
                                    <option value="US-OK" <%=selectedProvince.equals("US-OK") ? " selected" : ""%>>
                                        US-OK-Oklahoma
                                    </option>
                                    <option value="US-OR" <%=selectedProvince.equals("US-OR") ? " selected" : ""%>>
                                        US-OR-Oregon
                                    </option>
                                    <option value="US-PA" <%=selectedProvince.equals("US-PA") ? " selected" : ""%>>
                                        US-PA-Pennsylvania
                                    </option>
                                    <option value="US-PR" <%=selectedProvince.equals("US-PR") ? " selected" : ""%>>
                                        US-PR-Puerto Rico
                                    </option>
                                    <option value="US-RI" <%=selectedProvince.equals("US-RI") ? " selected" : ""%>>
                                        US-RI-Rhode Island
                                    </option>
                                    <option value="US-SC" <%=selectedProvince.equals("US-SC") ? " selected" : ""%>>
                                        US-SC-South Carolina
                                    </option>
                                    <option value="US-SD" <%=selectedProvince.equals("US-SD") ? " selected" : ""%>>
                                        US-SD-South Dakota
                                    </option>
                                    <option value="US-TN" <%=selectedProvince.equals("US-TN") ? " selected" : ""%>>
                                        US-TN-Tennessee
                                    </option>
                                    <option value="US-TX" <%=selectedProvince.equals("US-TX") ? " selected" : ""%>>
                                        US-TX-Texas
                                    </option>
                                    <option value="US-UT" <%=selectedProvince.equals("US-UT") ? " selected" : ""%>>
                                        US-UT-Utah
                                    </option>
                                    <option value="US-VA" <%=selectedProvince.equals("US-VA") ? " selected" : ""%>>
                                        US-VA-Virginia
                                    </option>
                                    <option value="US-VI" <%=selectedProvince.equals("US-VI") ? " selected" : ""%>>
                                        US-VI-Virgin Islands
                                    </option>
                                    <option value="US-VT" <%=selectedProvince.equals("US-VT") ? " selected" : ""%>>
                                        US-VT-Vermont
                                    </option>
                                    <option value="US-WA" <%=selectedProvince.equals("US-WA") ? " selected" : ""%>>
                                        US-WA-Washington
                                    </option>
                                    <option value="US-WI" <%=selectedProvince.equals("US-WI") ? " selected" : ""%>>
                                        US-WI-Wisconsin
                                    </option>
                                    <option value="US-WV" <%=selectedProvince.equals("US-WV") ? " selected" : ""%>>
                                        US-WV-West Virginia
                                    </option>
                                    <option value="US-WY" <%=selectedProvince.equals("US-WY") ? " selected" : ""%>>
                                        US-WY-Wyoming
                                    </option>
                                    <% } %>
                                </select>
                                <% } %>
                            </td>
                            <td class="postalLbl" align="right">
                                <b><% if (oscarProps.getProperty("demographicLabelPostal") == null) { %>
                                    <fmt:message key="demographic.demographicaddrecordhtm.formPostal"/>
                                    <% if ("false".equals(CarlosProperties.getInstance().getProperty("skip_postal_code_validation", "false"))) { %>
                                    <span style="color:red">*</span>
                                    <% } %>

                                    <% } else {
                                        out.print(oscarProps.getProperty("demographicLabelPostal"));
                                    } %> : </b></td>
                            <td class="postalCell" align="left"><input type="text" id="postal" name="postal"
                                                                       onBlur="upCaseCtrl(this)"
                                                                       value="<carlos:encode value='<%= prefillPostal %>' context="htmlAttribute"/>"></td>
                        </tr>


                        <tr valign="top">
                            <td class="addrLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formResidentialAddress"/>: </b></td>
                            <td class="addressCell" align="left"><input id="residentialAddress" type="text"
                                                                        name="residentialAddress" size=40/>

                            </td>
                            <td class="cityLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formResidentialCity"/>: </b></td>
                            <td class="cityCell" align="left"><input type="text" id="residentialCity"
                                                                     name="residentialCity"
                                                                     value=""/></td>
                        </tr>

                        <tr valign="top">
                            <td class="provLbl" align="right"><b>
                                <fmt:message key="demographic.demographicaddrecordhtm.formResidentialProvince"/> : </b>
                            </td>
                            <td class="provCell" align="left">
                                <%
                                    if ("true".equals(CarlosProperties.getInstance().getProperty("iso3166.2.enabled", "false"))) {
                                %>
                                <select name="residentialProvince" id="residentialProvince"></select>
                                <br/>
                                Filter by Country: <select name="residentialCountry" id="residentialCountry"></select>

                                <% } else { %>
                                <select id="residentialProvince" name="residentialProvince">
                                    <option value="OT"
                                            <%=selectedProvince.equals("") || selectedProvince.equals("OT") ? " selected" : ""%>>
                                        Other
                                    </option>

                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>"
                                            <%=province.equals(defaultProvince) ? " selected" : ""%>><%=li.next()%>
                                    </option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB" <%=selectedProvince.equals("AB") ? " selected" : ""%>>AB-Alberta
                                    </option>
                                    <option value="BC" <%=selectedProvince.equals("BC") ? " selected" : ""%>>BC-British
                                        Columbia
                                    </option>
                                    <option value="MB" <%=selectedProvince.equals("MB") ? " selected" : ""%>>
                                        MB-Manitoba
                                    </option>
                                    <option value="NB" <%=selectedProvince.equals("NB") ? " selected" : ""%>>NB-New
                                        Brunswick
                                    </option>
                                    <option value="NL" <%=selectedProvince.equals("NL") ? " selected" : ""%>>
                                        NL-Newfoundland & Labrador
                                    </option>
                                    <option value="NT" <%=selectedProvince.equals("NT") ? " selected" : ""%>>NT-Northwest
                                        Territory
                                    </option>
                                    <option value="NS" <%=selectedProvince.equals("NS") ? " selected" : ""%>>NS-Nova
                                        Scotia
                                    </option>
                                    <option value="NU" <%=selectedProvince.equals("NU") ? " selected" : ""%>>NU-Nunavut
                                    </option>
                                    <option value="ON" <%=selectedProvince.equals("ON") ? " selected" : ""%>>ON-Ontario
                                    </option>
                                    <option value="PE" <%=selectedProvince.equals("PE") ? " selected" : ""%>>PE-Prince
                                        Edward Island
                                    </option>
                                    <option value="QC" <%=selectedProvince.equals("QC") ? " selected" : ""%>>QC-Quebec
                                    </option>
                                    <option value="SK" <%=selectedProvince.equals("SK") ? " selected" : ""%>>
                                        SK-Saskatchewan
                                    </option>
                                    <option value="YT" <%=selectedProvince.equals("YT") ? " selected" : ""%>>YT-Yukon
                                    </option>
                                    <option value="US" <%=selectedProvince.equals("US") ? " selected" : ""%>>US
                                        resident
                                    </option>
                                    <option value="US-AK" <%=selectedProvince.equals("US-AK") ? " selected" : ""%>>
                                        US-AK-Alaska
                                    </option>
                                    <option value="US-AL" <%=selectedProvince.equals("US-AL") ? " selected" : ""%>>
                                        US-AL-Alabama
                                    </option>
                                    <option value="US-AR" <%=selectedProvince.equals("US-AR") ? " selected" : ""%>>
                                        US-AR-Arkansas
                                    </option>
                                    <option value="US-AZ" <%=selectedProvince.equals("US-AZ") ? " selected" : ""%>>
                                        US-AZ-Arizona
                                    </option>
                                    <option value="US-CA" <%=selectedProvince.equals("US-CA") ? " selected" : ""%>>
                                        US-CA-California
                                    </option>
                                    <option value="US-CO" <%=selectedProvince.equals("US-CO") ? " selected" : ""%>>
                                        US-CO-Colorado
                                    </option>
                                    <option value="US-CT" <%=selectedProvince.equals("US-CT") ? " selected" : ""%>>
                                        US-CT-Connecticut
                                    </option>
                                    <option value="US-CZ" <%=selectedProvince.equals("US-CZ") ? " selected" : ""%>>
                                        US-CZ-Canal Zone
                                    </option>
                                    <option value="US-DC" <%=selectedProvince.equals("US-DC") ? " selected" : ""%>>
                                        US-DC-District Of Columbia
                                    </option>
                                    <option value="US-DE" <%=selectedProvince.equals("US-DE") ? " selected" : ""%>>
                                        US-DE-Delaware
                                    </option>
                                    <option value="US-FL" <%=selectedProvince.equals("US-FL") ? " selected" : ""%>>
                                        US-FL-Florida
                                    </option>
                                    <option value="US-GA" <%=selectedProvince.equals("US-GA") ? " selected" : ""%>>
                                        US-GA-Georgia
                                    </option>
                                    <option value="US-GU" <%=selectedProvince.equals("US-GU") ? " selected" : ""%>>
                                        US-GU-Guam
                                    </option>
                                    <option value="US-HI" <%=selectedProvince.equals("US-HI") ? " selected" : ""%>>
                                        US-HI-Hawaii
                                    </option>
                                    <option value="US-IA" <%=selectedProvince.equals("US-IA") ? " selected" : ""%>>
                                        US-IA-Iowa
                                    </option>
                                    <option value="US-ID" <%=selectedProvince.equals("US-ID") ? " selected" : ""%>>
                                        US-ID-Idaho
                                    </option>
                                    <option value="US-IL" <%=selectedProvince.equals("US-IL") ? " selected" : ""%>>
                                        US-IL-Illinois
                                    </option>
                                    <option value="US-IN" <%=selectedProvince.equals("US-IN") ? " selected" : ""%>>
                                        US-IN-Indiana
                                    </option>
                                    <option value="US-KS" <%=selectedProvince.equals("US-KS") ? " selected" : ""%>>
                                        US-KS-Kansas
                                    </option>
                                    <option value="US-KY" <%=selectedProvince.equals("US-KY") ? " selected" : ""%>>
                                        US-KY-Kentucky
                                    </option>
                                    <option value="US-LA" <%=selectedProvince.equals("US-LA") ? " selected" : ""%>>
                                        US-LA-Louisiana
                                    </option>
                                    <option value="US-MA" <%=selectedProvince.equals("US-MA") ? " selected" : ""%>>
                                        US-MA-Massachusetts
                                    </option>
                                    <option value="US-MD" <%=selectedProvince.equals("US-MD") ? " selected" : ""%>>
                                        US-MD-Maryland
                                    </option>
                                    <option value="US-ME" <%=selectedProvince.equals("US-ME") ? " selected" : ""%>>
                                        US-ME-Maine
                                    </option>
                                    <option value="US-MI" <%=selectedProvince.equals("US-MI") ? " selected" : ""%>>
                                        US-MI-Michigan
                                    </option>
                                    <option value="US-MN" <%=selectedProvince.equals("US-MN") ? " selected" : ""%>>
                                        US-MN-Minnesota
                                    </option>
                                    <option value="US-MO" <%=selectedProvince.equals("US-MO") ? " selected" : ""%>>
                                        US-MO-Missouri
                                    </option>
                                    <option value="US-MS" <%=selectedProvince.equals("US-MS") ? " selected" : ""%>>
                                        US-MS-Mississippi
                                    </option>
                                    <option value="US-MT" <%=selectedProvince.equals("US-MT") ? " selected" : ""%>>
                                        US-MT-Montana
                                    </option>
                                    <option value="US-NC" <%=selectedProvince.equals("US-NC") ? " selected" : ""%>>
                                        US-NC-North Carolina
                                    </option>
                                    <option value="US-ND" <%=selectedProvince.equals("US-ND") ? " selected" : ""%>>
                                        US-ND-North Dakota
                                    </option>
                                    <option value="US-NE" <%=selectedProvince.equals("US-NE") ? " selected" : ""%>>
                                        US-NE-Nebraska
                                    </option>
                                    <option value="US-NH" <%=selectedProvince.equals("US-NH") ? " selected" : ""%>>
                                        US-NH-New Hampshire
                                    </option>
                                    <option value="US-NJ" <%=selectedProvince.equals("US-NJ") ? " selected" : ""%>>
                                        US-NJ-New Jersey
                                    </option>
                                    <option value="US-NM" <%=selectedProvince.equals("US-NM") ? " selected" : ""%>>
                                        US-NM-New Mexico
                                    </option>
                                    <option value="US-NU" <%=selectedProvince.equals("US-NU") ? " selected" : ""%>>
                                        US-NU-Nunavut
                                    </option>
                                    <option value="US-NV" <%=selectedProvince.equals("US-NV") ? " selected" : ""%>>
                                        US-NV-Nevada
                                    </option>
                                    <option value="US-NY" <%=selectedProvince.equals("US-NY") ? " selected" : ""%>>
                                        US-NY-New York
                                    </option>
                                    <option value="US-OH" <%=selectedProvince.equals("US-OH") ? " selected" : ""%>>
                                        US-OH-Ohio
                                    </option>
                                    <option value="US-OK" <%=selectedProvince.equals("US-OK") ? " selected" : ""%>>
                                        US-OK-Oklahoma
                                    </option>
                                    <option value="US-OR" <%=selectedProvince.equals("US-OR") ? " selected" : ""%>>
                                        US-OR-Oregon
                                    </option>
                                    <option value="US-PA" <%=selectedProvince.equals("US-PA") ? " selected" : ""%>>
                                        US-PA-Pennsylvania
                                    </option>
                                    <option value="US-PR" <%=selectedProvince.equals("US-PR") ? " selected" : ""%>>
                                        US-PR-Puerto Rico
                                    </option>
                                    <option value="US-RI" <%=selectedProvince.equals("US-RI") ? " selected" : ""%>>
                                        US-RI-Rhode Island
                                    </option>
                                    <option value="US-SC" <%=selectedProvince.equals("US-SC") ? " selected" : ""%>>
                                        US-SC-South Carolina
                                    </option>
                                    <option value="US-SD" <%=selectedProvince.equals("US-SD") ? " selected" : ""%>>
                                        US-SD-South Dakota
                                    </option>
                                    <option value="US-TN" <%=selectedProvince.equals("US-TN") ? " selected" : ""%>>
                                        US-TN-Tennessee
                                    </option>
                                    <option value="US-TX" <%=selectedProvince.equals("US-TX") ? " selected" : ""%>>
                                        US-TX-Texas
                                    </option>
                                    <option value="US-UT" <%=selectedProvince.equals("US-UT") ? " selected" : ""%>>
                                        US-UT-Utah
                                    </option>
                                    <option value="US-VA" <%=selectedProvince.equals("US-VA") ? " selected" : ""%>>
                                        US-VA-Virginia
                                    </option>
                                    <option value="US-VI" <%=selectedProvince.equals("US-VI") ? " selected" : ""%>>
                                        US-VI-Virgin Islands
                                    </option>
                                    <option value="US-VT" <%=selectedProvince.equals("US-VT") ? " selected" : ""%>>
                                        US-VT-Vermont
                                    </option>
                                    <option value="US-WA" <%=selectedProvince.equals("US-WA") ? " selected" : ""%>>
                                        US-WA-Washington
                                    </option>
                                    <option value="US-WI" <%=selectedProvince.equals("US-WI") ? " selected" : ""%>>
                                        US-WI-Wisconsin
                                    </option>
                                    <option value="US-WV" <%=selectedProvince.equals("US-WV") ? " selected" : ""%>>
                                        US-WV-West Virginia
                                    </option>
                                    <option value="US-WY" <%=selectedProvince.equals("US-WY") ? " selected" : ""%>>
                                        US-WY-Wyoming
                                    </option>
                                    <% } %>
                                </select>
                                <% } %>
                            </td>
                            <td id="postalLbl" align="right"><b>
                                <fmt:message key="demographic.demographicaddrecordhtm.formResidentialPostal"/>
                                : </b></td>
                            <td id="postalCell" align="left"><input type="text" id="residentialPostal"
                                                                    name="residentialPostal"
                                                                    onBlur="upCaseCtrl(this)"></td>
                        </tr>

                        <tr valign="top">
                            <td id="phoneLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formPhoneHome"/>: </b></td>
                            <td id="phoneCell" align="left"><input type="text" id="phone" name="phone"
                                                                   onBlur="formatPhoneNum()"
                                                                   value="<carlos:encode value='<%= prefillPhone.isEmpty() ? props.getProperty("phoneprefix", "905-") : prefillPhone %>' context="htmlAttribute"/>">
                                <fmt:message key="demographic.demographicaddrecordhtm.Ext"/>:<input
                                        type="text" id="hPhoneExt" name="hPhoneExt" value="" size="4"/></td>
                            <td id="phoneWorkLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formPhoneWork"/>:</b></td>
                            <td id="phoneWorkCell" align="left"><input type="text" name="phone2"
                                                                       onBlur="formatPhoneNum()" value=""> <fmt:message key="demographic.demographicaddrecordhtm.Ext"/>:<input type="text"
                                                                                           name="wPhoneExt" value=""
                                                                                           style="display: inline"
                                                                                           size="4"/></td>
                        </tr>
                        <tr valign="top">
                            <td id="phoneCellLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formPhoneCell"/>: </b></td>
                            <td id="phoneCellCell" align="left"><input type="text" name="demo_cell"
                                                                       onBlur="formatPhoneNum()"></td>
                            <td align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formPhoneComment"/>: </b></td>
                            <td align="left" colspan="3">
                                <textarea rows="2" cols="30" name="phoneComment"></textarea>
                            </td>
                        </tr>
                        <tr valign="top">
                            <td id="newsletterLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter"/>: </b></td>
                            <td id="newsletterCell" align="left"><select name="newsletter">
                                <option value="Unknown" selected><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optUnknown"/></option>
                                <option value="No"><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optNo"/></option>
                                <option value="Paper"><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optPaper"/></option>
                                <option value="Electronic"><fmt:message key="demographic.demographicaddrecordhtm.formNewsLetter.optElectronic"/></option>
                            </select></td>
                            <td align="right"><b><fmt:message key="demographic.demographiceditdemographic.aboriginal"/>: </b>
                            </td>
                            <td align="left">
                                <select name="aboriginal">
                                    <option value="">Unknown</option>
                                    <option value="No">No</option>
                                    <option value="Yes">Yes</option>
                                </select>
                        </tr>
                        <tr valign="top">
                            <td id="emailLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formEMail"/>: </b></td>
                            <td id="emailCell" align="left"><input type="text" id="email" name="email" value="">
                            </td>
                        </tr>
                        <tr valign="top">
                            <td id="dobLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formDOB"/><span
                                    style="color:red;">:</span></b></td>
                            <td id="dobTbl" align="left">
                                <table>
                                    <tr>
                                        <td><input type="text" name="year_of_birth" placeholder="yyyy"
                                                   id="year_of_birth"
                                                   maxlength="4"
                                                   value="<carlos:encode value='<%= prefillYearOfBirth %>' context="htmlAttribute"/>"></td>

                                        <td>
                                            <select name="month_of_birth" id="month_of_birth">
                                                <% for (String m : new String[]{"01","02","03","04","05","06","07","08","09","10","11","12"}) { %>
                                                <option value="<%=m%>" <%=(prefillMonthOfBirth.isEmpty() ? "06" : prefillMonthOfBirth).equals(m) ? "selected" : ""%>><%=m%>
                                                <% } %>
                                            </select></td>

                                        <td>
                                            <select name="date_of_birth" id="date_of_birth">
                                                <% for (int d = 1; d <= 31; d++) {
                                                    String dStr = String.format("%02d", d); %>
                                                <option value="<%=dStr%>" <%=(prefillDateOfBirth.isEmpty() ? "15" : prefillDateOfBirth).equals(dStr) ? "selected" : ""%>><%=dStr%>
                                                <% } %>
                                            </select></td>
                                    </tr>
                                </table>
                            </td>

                            <td style="text-align: right;">
                                <strong><fmt:message key="demographic.demographicaddrecordhtm.formPronouns"/></strong>
                            </td>
                            <td style="text-align: left;">
                                <input type="text" id="patientPronouns" name="pronouns"/>
                            </td>
                        </tr>
                        <tr>
                            <td align="right" id="genderLbl"><b><fmt:message key="demographic.demographicaddrecordhtm.formSex"/><font
                                    color="red">:</font></b></td>

                            <% // Determine if curUser has selected a default sex in preferences
                                UserProperty sexProp = userPropertyDAO.getProp(curUser_no, UserProperty.DEFAULT_SEX);
                                String sex = "";
                                if (sexProp != null) {
                                    sex = sexProp.getValue();
                                } else {
                                    // Access defaultsex system property
                                    sex = props.getProperty("defaultsex", "");
                                }
                                // Prefill sex overrides the user/system default
                                if (!prefillSex.isEmpty()) sex = prefillSex;
                            %>
                            <td id="gender" align="left">

                                <select name="sex" id="sex">
                                    <option value=""></option>
                                    <% for (Gender gn : Gender.values()) {
                                        String genderDisplayText = DemographicEditHelper.getGenderDisplayText(request.getLocale(), gn.name());
                                    %>
                                    <option value="<%=gn.name()%>" <%=((sex.toUpperCase().equals(gn.name())) ? "selected=\"selected\"" : "") %>><%=genderDisplayText%>
                                    </option>
                                    <% } %>
                                </select>

                            </td>

                            <td style="text-align: right;">
                                <strong><fmt:message key="demographic.demographicaddrecordhtm.formGender"/></strong>
                            </td>
                            <td style="text-align: left;">
                                <input type="text" id="patientGender" name="gender"/>
                            </td>
                        </tr>


                        <tr valign="top">
                            <td align="right" id="hinLbl"><b><fmt:message key="demographic.demographicaddrecordhtm.formHIN"/>: </b></td>
                            <td align="left" id="hinVer">
                                <input type="text" name="hin" id="hin" onfocus="autoFillHin()"
                                       value="<carlos:encode value='<%= prefillHin %>' context="htmlAttribute"/>">
                                <fmt:message key="demographic.demographicaddrecordhtm.formVer"/>:
                                <input type="text" id="ver" name="ver" onBlur="upCaseCtrl(this)"
                                       value="<carlos:encode value='<%= prefillVer %>' context="htmlAttribute"/>">
                            </td>
                            <td id="effDateLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formEFFDate"/>: </b></td>
                            <td id="effDate" align="left">
                                <input type="text" placeholder="yyyy" id="eff_date_year" name="eff_date_year"
                                       maxlength="4">
                                <input type="text" placeholder="mm" id="eff_date_month" name="eff_date_month"
                                       maxlength="2">
                                <input type="text" placeholder="dd" id="eff_date_date" name="eff_date_date"
                                       maxlength="2">
                            </td>
                        </tr>
                        <tr>
                            <td id="hcTypeLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.formHCType"/>: </b></td>
                            <td id="hcType">

                                <select name="hc_type" id="hc_type">
                                    <option value="OT"
                                            <%=selectedHcType.equals("") || selectedHcType.equals("OT") ? " selected" : ""%>>Other
                                    </option>
                                    <% if (pNames.isDefined()) {
                                        for (ListIterator li = pNames.listIterator(); li.hasNext(); ) {
                                            String province = (String) li.next(); %>
                                    <option value="<%=province%>"<%=province.equals(selectedHcType) ? " selected" : ""%>><%=li.next()%>
                                    </option>
                                    <% } %>
                                    <% } else { %>
                                    <option value="AB"<%=selectedHcType.equals("AB") ? " selected" : ""%>>AB-Alberta</option>
                                    <option value="BC"<%=selectedHcType.equals("BC") ? " selected" : ""%>>BC-British Columbia
                                    </option>
                                    <option value="MB"<%=selectedHcType.equals("MB") ? " selected" : ""%>>MB-Manitoba</option>
                                    <option value="NB"<%=selectedHcType.equals("NB") ? " selected" : ""%>>NB-New Brunswick
                                    </option>
                                    <option value="NL"<%=selectedHcType.equals("NL") ? " selected" : ""%>>NL-Newfoundland &
                                        Labrador
                                    </option>
                                    <option value="NT"<%=selectedHcType.equals("NT") ? " selected" : ""%>>NT-Northwest
                                        Territory
                                    </option>
                                    <option value="NS"<%=selectedHcType.equals("NS") ? " selected" : ""%>>NS-Nova Scotia
                                    </option>
                                    <option value="NU"<%=selectedHcType.equals("NU") ? " selected" : ""%>>NU-Nunavut</option>
                                    <option value="ON"<%=selectedHcType.equals("ON") ? " selected" : ""%>>ON-Ontario</option>
                                    <option value="PE"<%=selectedHcType.equals("PE") ? " selected" : ""%>>PE-Prince Edward
                                        Island
                                    </option>
                                    <option value="QC"<%=selectedHcType.equals("QC") ? " selected" : ""%>>QC-Quebec</option>
                                    <option value="SK"<%=selectedHcType.equals("SK") ? " selected" : ""%>>SK-Saskatchewan
                                    </option>
                                    <option value="YT"<%=selectedHcType.equals("YT") ? " selected" : ""%>>YT-Yukon</option>
                                    <option value="US"<%=selectedHcType.equals("US") ? " selected" : ""%>>US resident</option>
                                    <option value="US-AK" <%=selectedHcType.equals("US-AK") ? " selected" : ""%>>US-AK-Alaska
                                    </option>
                                    <option value="US-AL" <%=selectedHcType.equals("US-AL") ? " selected" : ""%>>US-AL-Alabama
                                    </option>
                                    <option value="US-AR" <%=selectedHcType.equals("US-AR") ? " selected" : ""%>>
                                        US-AR-Arkansas
                                    </option>
                                    <option value="US-AZ" <%=selectedHcType.equals("US-AZ") ? " selected" : ""%>>US-AZ-Arizona
                                    </option>
                                    <option value="US-CA" <%=selectedHcType.equals("US-CA") ? " selected" : ""%>>
                                        US-CA-California
                                    </option>
                                    <option value="US-CO" <%=selectedHcType.equals("US-CO") ? " selected" : ""%>>
                                        US-CO-Colorado
                                    </option>
                                    <option value="US-CT" <%=selectedHcType.equals("US-CT") ? " selected" : ""%>>
                                        US-CT-Connecticut
                                    </option>
                                    <option value="US-CZ" <%=selectedHcType.equals("US-CZ") ? " selected" : ""%>>US-CZ-Canal
                                        Zone
                                    </option>
                                    <option value="US-DC" <%=selectedHcType.equals("US-DC") ? " selected" : ""%>>US-DC-District
                                        Of Columbia
                                    </option>
                                    <option value="US-DE" <%=selectedHcType.equals("US-DE") ? " selected" : ""%>>
                                        US-DE-Delaware
                                    </option>
                                    <option value="US-FL" <%=selectedHcType.equals("US-FL") ? " selected" : ""%>>US-FL-Florida
                                    </option>
                                    <option value="US-GA" <%=selectedHcType.equals("US-GA") ? " selected" : ""%>>US-GA-Georgia
                                    </option>
                                    <option value="US-GU" <%=selectedHcType.equals("US-GU") ? " selected" : ""%>>US-GU-Guam
                                    </option>
                                    <option value="US-HI" <%=selectedHcType.equals("US-HI") ? " selected" : ""%>>US-HI-Hawaii
                                    </option>
                                    <option value="US-IA" <%=selectedHcType.equals("US-IA") ? " selected" : ""%>>US-IA-Iowa
                                    </option>
                                    <option value="US-ID" <%=selectedHcType.equals("US-ID") ? " selected" : ""%>>US-ID-Idaho
                                    </option>
                                    <option value="US-IL" <%=selectedHcType.equals("US-IL") ? " selected" : ""%>>
                                        US-IL-Illinois
                                    </option>
                                    <option value="US-IN" <%=selectedHcType.equals("US-IN") ? " selected" : ""%>>US-IN-Indiana
                                    </option>
                                    <option value="US-KS" <%=selectedHcType.equals("US-KS") ? " selected" : ""%>>US-KS-Kansas
                                    </option>
                                    <option value="US-KY" <%=selectedHcType.equals("US-KY") ? " selected" : ""%>>
                                        US-KY-Kentucky
                                    </option>
                                    <option value="US-LA" <%=selectedHcType.equals("US-LA") ? " selected" : ""%>>
                                        US-LA-Louisiana
                                    </option>
                                    <option value="US-MA" <%=selectedHcType.equals("US-MA") ? " selected" : ""%>>
                                        US-MA-Massachusetts
                                    </option>
                                    <option value="US-MD" <%=selectedHcType.equals("US-MD") ? " selected" : ""%>>
                                        US-MD-Maryland
                                    </option>
                                    <option value="US-ME" <%=selectedHcType.equals("US-ME") ? " selected" : ""%>>US-ME-Maine
                                    </option>
                                    <option value="US-MI" <%=selectedHcType.equals("US-MI") ? " selected" : ""%>>
                                        US-MI-Michigan
                                    </option>
                                    <option value="US-MN" <%=selectedHcType.equals("US-MN") ? " selected" : ""%>>
                                        US-MN-Minnesota
                                    </option>
                                    <option value="US-MO" <%=selectedHcType.equals("US-MO") ? " selected" : ""%>>
                                        US-MO-Missouri
                                    </option>
                                    <option value="US-MS" <%=selectedHcType.equals("US-MS") ? " selected" : ""%>>
                                        US-MS-Mississippi
                                    </option>
                                    <option value="US-MT" <%=selectedHcType.equals("US-MT") ? " selected" : ""%>>US-MT-Montana
                                    </option>
                                    <option value="US-NC" <%=selectedHcType.equals("US-NC") ? " selected" : ""%>>US-NC-North
                                        Carolina
                                    </option>
                                    <option value="US-ND" <%=selectedHcType.equals("US-ND") ? " selected" : ""%>>US-ND-North
                                        Dakota
                                    </option>
                                    <option value="US-NE" <%=selectedHcType.equals("US-NE") ? " selected" : ""%>>
                                        US-NE-Nebraska
                                    </option>
                                    <option value="US-NH" <%=selectedHcType.equals("US-NH") ? " selected" : ""%>>US-NH-New
                                        Hampshire
                                    </option>
                                    <option value="US-NJ" <%=selectedHcType.equals("US-NJ") ? " selected" : ""%>>US-NJ-New
                                        Jersey
                                    </option>
                                    <option value="US-NM" <%=selectedHcType.equals("US-NM") ? " selected" : ""%>>US-NM-New
                                        Mexico
                                    </option>
                                    <option value="US-NU" <%=selectedHcType.equals("US-NU") ? " selected" : ""%>>US-NU-Nunavut
                                    </option>
                                    <option value="US-NV" <%=selectedHcType.equals("US-NV") ? " selected" : ""%>>US-NV-Nevada
                                    </option>
                                    <option value="US-NY" <%=selectedHcType.equals("US-NY") ? " selected" : ""%>>US-NY-New
                                        York
                                    </option>
                                    <option value="US-OH" <%=selectedHcType.equals("US-OH") ? " selected" : ""%>>US-OH-Ohio
                                    </option>
                                    <option value="US-OK" <%=selectedHcType.equals("US-OK") ? " selected" : ""%>>
                                        US-OK-Oklahoma
                                    </option>
                                    <option value="US-OR" <%=selectedHcType.equals("US-OR") ? " selected" : ""%>>US-OR-Oregon
                                    </option>
                                    <option value="US-PA" <%=selectedHcType.equals("US-PA") ? " selected" : ""%>>
                                        US-PA-Pennsylvania
                                    </option>
                                    <option value="US-PR" <%=selectedHcType.equals("US-PR") ? " selected" : ""%>>US-PR-Puerto
                                        Rico
                                    </option>
                                    <option value="US-RI" <%=selectedHcType.equals("US-RI") ? " selected" : ""%>>US-RI-Rhode
                                        Island
                                    </option>
                                    <option value="US-SC" <%=selectedHcType.equals("US-SC") ? " selected" : ""%>>US-SC-South
                                        Carolina
                                    </option>
                                    <option value="US-SD" <%=selectedHcType.equals("US-SD") ? " selected" : ""%>>US-SD-South
                                        Dakota
                                    </option>
                                    <option value="US-TN" <%=selectedHcType.equals("US-TN") ? " selected" : ""%>>
                                        US-TN-Tennessee
                                    </option>
                                    <option value="US-TX" <%=selectedHcType.equals("US-TX") ? " selected" : ""%>>US-TX-Texas
                                    </option>
                                    <option value="US-UT" <%=selectedHcType.equals("US-UT") ? " selected" : ""%>>US-UT-Utah
                                    </option>
                                    <option value="US-VA" <%=selectedHcType.equals("US-VA") ? " selected" : ""%>>
                                        US-VA-Virginia
                                    </option>
                                    <option value="US-VI" <%=selectedHcType.equals("US-VI") ? " selected" : ""%>>US-VI-Virgin
                                        Islands
                                    </option>
                                    <option value="US-VT" <%=selectedHcType.equals("US-VT") ? " selected" : ""%>>US-VT-Vermont
                                    </option>
                                    <option value="US-WA" <%=selectedHcType.equals("US-WA") ? " selected" : ""%>>
                                        US-WA-Washington
                                    </option>
                                    <option value="US-WI" <%=selectedHcType.equals("US-WI") ? " selected" : ""%>>
                                        US-WI-Wisconsin
                                    </option>
                                    <option value="US-WV" <%=selectedHcType.equals("US-WV") ? " selected" : ""%>>US-WV-West
                                        Virginia
                                    </option>
                                    <option value="US-WY" <%=selectedHcType.equals("US-WY") ? " selected" : ""%>>US-WY-Wyoming
                                    </option>
                                    <% } %>
                                </select>

                            </td>
                            <td id="renewDateLbl" align="right"><b>*<fmt:message key="demographic.demographiceditdemographic.formHCRenewDate"/>:</b></td>
                            <td id="renewDate" align="left"><input type="text" placeholder="yyyy"
                                                                   id="hc_renew_date_year" name="hc_renew_date_year"
                                                                   size="4" maxlength="4" value="">
                                <input type="text" placeholder="mm" id="hc_renew_date_month" name="hc_renew_date_month"
                                       size="2" maxlength="2" value="">
                                <input type="text" placeholder="dd" id="hc_renew_date_date" name="hc_renew_date_date"
                                       size="2" maxlength="2" value="">
                            </td>
                        </tr>
                        <tr>
                            <td id="countryLbl" align="right">
                                <b><fmt:message key="demographic.demographicaddrecordhtm.msgCountryOfOrigin"/>:</b>
                            </td>
                            <td id="countryCell">
                                <select id="countryOfOrigin" name="countryOfOrigin">
                                    <option value="-1"><fmt:message key="demographic.demographicaddrecordhtm.msgNotSet"/></option>
                                    <%for (CountryCode cc : countryList) { %>
                                    <option value="<%=cc.getCountryId()%>"><%=cc.getCountryName() %>
                                    </option>
                                    <%}%>
                                </select>
                            </td>
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
                                <td colspan="2">

                                    <input type="radio" name="usSigned" value="signed">U.S. Resident Consent Form Signed

                                    <input type="radio" name="usSigned" value="unsigned">U.S. Resident Consent Form NOT
                                    Signed

                                </td>
                                <% } %>
                            </oscar:oscarPropertiesCheck>
                            <oscar:oscarPropertiesCheck property="privateConsentEnabled" value="false">
                                <td><!-- placeholder --></td>
                                <td><!-- placeholder --></td>
                            </oscar:oscarPropertiesCheck>
                        </tr>


                        <tr valign="top">
                                <%-- TOGGLE FIRST NATIONS MODULE --%>
                            <oscar:oscarPropertiesCheck value="true" defaultVal="false" property="FIRST_NATIONS_MODULE">
                                <jsp:include page="/WEB-INF/jsp/demographic/manageFirstNationsModule.jsp" flush="true">
                                    <jsp:param name="demo" value="0"/>
                                </jsp:include>
                            </oscar:oscarPropertiesCheck>
                                <%-- END TOGGLE FIRST NATIONS MODULE --%>
                            <td id="sinNoLbl" align="right"><b><fmt:message key="demographic.demographicaddrecordhtm.msgSIN"/>:</b></td>
                            <td id="sinNoCell" align="left">
                                <input type="text" name="sin">
                            </td>


                            <td id="cytologyLbl" align="right"><b> <fmt:message key="demographic.demographicaddrecordhtm.cytolNum"/>:</b></td>
                            <td id="cytologyCell" align="left">
                                <input type="text" name="cytolNum">

                            </td>
                        </tr>
                        <tr valign="top">
