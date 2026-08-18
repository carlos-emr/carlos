<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%
    String demographic$ = request.getParameter("demographic_no");
%>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<security:oscarSec roleName="<%=roleName$%>"
                   objectName='<%="_demographic$"+demographic$%>' rights="o"
                   reverse="<%=false%>">
    You have no rights to access the data!
    <% authed = false; %>
    <% response.sendRedirect(request.getContextPath() + "/noRights.html"); %>
</security:oscarSec>

<%
    if (!authed) {
        return;
    }
%>
<%@ page
        import="java.util.*, java.sql.*, java.net.*,java.text.DecimalFormat, io.github.carlos_emr.*, io.github.carlos_emr.carlos.demographic.data.ProvinceNames, io.github.carlos_emr.carlos.waitinglist.WaitingList" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.DemographicCust" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicCustDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DemographicDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.WaitingListDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.WaitingListName" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderPreference" %>
<%@page import="io.github.carlos_emr.carlos.utility.SessionConstants" %>
<%@ page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.Misc" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%
    ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);
    DemographicCustDao demographicCustDao = (DemographicCustDao) SpringUtils.getBean(DemographicCustDao.class);
    DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
    WaitingListDao waitingListDao = SpringUtils.getBean(WaitingListDao.class);
    WaitingListNameDao waitingListNameDao = SpringUtils.getBean(WaitingListNameDao.class);
%>

<jsp:useBean id="providerBean" class="java.util.Properties"
             scope="session"/>
<% java.util.Properties oscarVariables = CarlosProperties.getInstance(); %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<%
    String curProvider_no = (String) session.getAttribute("user");
    String demographic_no = request.getParameter("demographic_no");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
    String deepcolor = "#CCCCFF", weakcolor = "#EEEEFF";
    String str = null;
    int nStrShowLen = 20;
    String prov = (oscarVariables.getProperty("billregion", "")).trim().toUpperCase();

    CarlosProperties oscarProps = CarlosProperties.getInstance();

    ProvinceNames pNames = ProvinceNames.getInstance();

%>


<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="demographic.demographiceditdemographic.title"/></title>
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/encounter/encounterStyles.css">

    </head>
    <%
        //----------------------------REFERRAL DOCTOR------------------------------
        String rdohip = "", rd = "", fd = "", family_doc = "";

        String resident = "", nurse = "", alert = "", notes = "", midwife = "";


        DemographicCust demographicCust = demographicCustDao.find(Integer.parseInt(demographic_no));
        if (demographicCust != null) {
            resident = demographicCust.getResident();
            nurse = demographicCust.getNurse();
            alert = demographicCust.getAlert();
            midwife = demographicCust.getMidwife();
            notes = SxmlMisc.getXmlContent(demographicCust.getNotes(), "unotes");
            notes = notes == null ? "" : notes;
        }

        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        String dateString = curYear + "-" + curMonth + "-" + curDay;
        int age = 0, dob_year = 0, dob_month = 0, dob_date = 0;

        int param = Integer.parseInt(demographic_no);

        Demographic d = demographicDao.getDemographicById(param);

        if (d == null) {
            out.println("failed!!!");
        } else {

            //----------------------------REFERRAL DOCTOR------------------------------
            fd = d.getFamilyDoctor();
            if (fd == null) {
                rd = "";
                rdohip = "";
                family_doc = "";
            } else {
                rd = SxmlMisc.getXmlContent(d.getFamilyDoctor(), "rd");
                rd = rd != null ? rd : "";
                rdohip = SxmlMisc.getXmlContent(d.getFamilyDoctor(), "rdohip");
                rdohip = rdohip != null ? rdohip : "";
                family_doc = SxmlMisc.getXmlContent(d.getFamilyDoctor(), "family_doc");
                family_doc = family_doc != null ? family_doc : "";
            }
            //----------------------------REFERRAL DOCTOR --------------end-----------

            dob_year = Integer.parseInt(d.getYearOfBirth());
            dob_month = Integer.parseInt(d.getMonthOfBirth());
            dob_date = Integer.parseInt(d.getDateOfBirth());
            if (dob_year != 0) age = MyDateFormat.getAge(dob_year, dob_month, dob_date);
            WaitingList wL = WaitingList.getInstance();
    %>


    <body
            topmargin="0" leftmargin="0" rightmargin="0">
    <form>
        <table width="100%" class="MainTableLeftColumn">
            <tr>
                <td class="RowTop" colspan="3" align="center" bgcolor="#EEEEFF">
                    <b>Record</b> (<carlos:encode value='<%=d.getDemographicNo()%>' context="html"/>)
                    <carlos:encode value='<%=d.getLastName()%>' context="html"/>,
                    <carlos:encode value='<%=d.getFirstName()%>' context="html"/>
                    <carlos:encode value='<%=d.getSex()%>' context="html"/>
                    <%=age%> years
                </td>
            </tr>
            <tr>
                <td align="left"
                    title="<carlos:encode value='<%=d.getDemographicNo()%>' context="htmlAttribute"/>"><b><fmt:message key="demographic.demographiceditdemographic.formLastName"/>: </b><carlos:encode value='<%=d.getLastName()%>' context="html"/>
                </td>
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formFirstName"/>: </b></td>
                <td align="left"><carlos:encode value='<%=d.getFirstName()%>' context="html"/>
                </td>
            </tr>


            <tr valign="top">
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formAddr"/>: </b> <carlos:encode value='<%=d.getAddress()%>' context="html"/>
                </td>
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formCity"/>: </b></td>
                <td align="left"><carlos:encode value='<%=d.getCity()%>' context="html"/>
                </td>
            </tr>

            <tr valign="top">
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formProcvince"/>: </b><carlos:encode value='<%=d.getProvince()%>' context="html"/>
                </td>
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formPostal"/>: </b></td>
                <td align="left"><carlos:encode value='<%=d.getPostal()%>' context="html"/>
                </td>
            </tr>
            <tr valign="top">
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formPhoneH"/>: </b><carlos:encode value='<%=d.getPhone()%>' context="html"/>
                </td>
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formPhoneW"/>:</b></td>
                <td align="left"><carlos:encode value='<%=d.getPhone2()%>' context="html"/>
                </td>
            </tr>
            <tr valign="top">
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formEmail"/>: </b><carlos:encode value='<%=d.getEmail()%>' context="html"/>
                </td>
            </tr>
            <tr valign="top">
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formDOB"/></b><fmt:message key="demographic.demographiceditdemographic.formDOBDetais"/><b>:
                </b> <carlos:encode value='<%=d.getYearOfBirth()%>' context="html"/>/ <carlos:encode value='<%=d.getMonthOfBirth()%>' context="html"/>/
                    <carlos:encode value='<%=d.getDateOfBirth()%>' context="html"/> <b>Age: </b> <%=age%>
                </td>
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.formSex"/>:</b></td>
                <td align="left"><carlos:encode value='<%=d.getSex()%>' context="html"/>
                </td>
            </tr>
            <tr valign="top">
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formHin"/>: </b><carlos:encode value='<%=d.getHin()%>' context="html"/>
                    <b><fmt:message key="demographic.demographiceditdemographic.formVer"/></b> <carlos:encode value='<%=d.getVer()%>' context="html"/>
                </td>
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formEFFDate"/>:</b></td>
                <td align="left">
                    <%
                        // Put 0 on the left on dates
                        DecimalFormat decF = new DecimalFormat();
                        // Year
                        decF.applyPattern("0000");
                        String effDateYear = decF.format(MyDateFormat.getYearFromStandardDate(d.getFormattedEffDate()));
                        // Month and Day
                        decF.applyPattern("00");
                        String effDateMonth = decF.format(MyDateFormat.getMonthFromStandardDate(d.getFormattedEffDate()));
                        String effDateDay = decF.format(MyDateFormat.getDayFromStandardDate(d.getFormattedEffDate()));
                    %> <carlos:encode value='<%= effDateYear%>' context="html"/>/ <carlos:encode value='<%= effDateMonth%>' context="html"/>/ <carlos:encode value='<%= effDateDay%>' context="html"/>
                </td>
            </tr>
            <tr valign="top">
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formHCType"/>:</b> <%
                    String hctype = d.getHcType() == null ? "" : d.getHcType(); %>
                    <carlos:encode value='<%=hctype%>' context="html"/>
                </td>
                <td></td>
                <td></td>
            </tr>
            <tr valign="top">
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.formDoctor"/>: </b> <%
                    List<Provider> providers = providerDao.getActiveProviders();
                    for (Provider p : providers) {
                        if (p.getProviderNo().equals(d.getProviderNo())) {%>
                    <carlos:encode value='<%=Misc.getShortStr((p.getLastName() + "," + p.getFirstName()), "", nStrShowLen)%>' context="html"/>
                    <% }
                    }
                    %>
                </td>
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.formNurse"/>: </b></td>
                <td align="left">
                    <%
                        for (Provider p : providers) {
                            if (p.getProviderNo().equals(resident)) {%>
                    <carlos:encode value='<%=Misc.getShortStr((p.getLastName() + "," + p.getFirstName()), "", nStrShowLen)%>' context="html"/>
                    <% }
                    }%>
                </td>
            </tr>
            <tr valign="top">
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.formMidwife"/>: </b> <%
                    for (Provider p : providers) {
                        if (p.getProviderNo().equals(midwife)) {%>
                    <carlos:encode value='<%=Misc.getShortStr((p.getLastName() + "," + p.getFirstName()), "", nStrShowLen)%>' context="html"/>
                    <% }
                    }%>
                </td>
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formResident"/>:</b></td>
                <td align="left">
                    <%
                        for (Provider p : providers) {
                            if (p.getProviderNo().equals(nurse)) {%>
                    <carlos:encode value='<%=Misc.getShortStr((p.getLastName() + "," + p.getFirstName()), "", nStrShowLen)%>' context="html"/>
                    <% }
                    }%>
                </td>
            </tr>

            <tr valign="top">
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.formRefDoc"/>: </b> <% if (oscarProps.getProperty("isMRefDocSelectList", "").equals("true")) {
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

                %> <select name="r_doctor" onChange="changeRefDoc()"
                           style="width: 200px">
                    <option value=""></option>
                    <% for (int k = 0; k < vecRef.size(); k++) {
                        prop = (Properties) vecRef.get(k);
                    %>
                    <option
                            value="<carlos:encode value='<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>' context="htmlAttribute"/>"
                            <%=prop.getProperty("referral_no").equals(rdohip) ? "selected" : ""%>>
                        <carlos:encode value='<%=Misc.getShortStr((prop.getProperty("last_name") + "," + prop.getProperty("first_name")), "", nStrShowLen)%>' context="html"/>
                    </option>
                    <% } %>
                </select>
                    <script language="Javascript">
                        <!--
                        function changeRefDoc() {
                            //alert(document.updatedelete.r_doctor.value);
                            var refName = document.updatedelete.r_doctor.options[document.updatedelete.r_doctor.selectedIndex].value;
                            var refNo = "";
                            <% for(int k=0; k<vecRef.size(); k++) {
                                    prop= (Properties) vecRef.get(k);
                            %>
                            if (refName == "<carlos:encode value='<%=prop.getProperty("last_name")+","+prop.getProperty("first_name")%>' context="javaScriptBlock"/>") {
                                refNo = '<carlos:encode value='<%=prop.getProperty("referral_no", "")%>' context="javaScriptBlock"/>';
                            }
                            <% } %>
                            document.updatedelete.r_doctor_ohip.value = refNo;
                        }

                        //-->
                    </script>
                    <% } else {%> <carlos:encode value='<%=rd%>' context="html"/> <% } %>
                </td>
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.formRefDocNo"/>: </b></td>
                <td align="left"><carlos:encode value='<%=rdohip%>' context="html"/>
                </td>
            </tr>

            <tr valign="top">
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.formRosterStatus"/>: </b> <%
                    String rosterStatus = d.getRosterStatus();
                    if (rosterStatus == null) {
                        rosterStatus = "";
                    }
                %> <carlos:encode value='<%=rosterStatus%>' context="html"/>
                </td>
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.DateJoined"/>: </b></td>
                <td align="left">
                    <%
                        // Format year
                        decF.applyPattern("0000");
                        String hcRenewYear = decF.format(MyDateFormat.getYearFromStandardDate(d.getFormattedRenewDate()));
                        decF.applyPattern("00");
                        String hcRenewMonth = decF.format(MyDateFormat.getMonthFromStandardDate(d.getFormattedRenewDate()));
                        String hcRenewDay = decF.format(MyDateFormat.getDayFromStandardDate(d.getFormattedRenewDate()));
                    %> <carlos:encode value='<%= hcRenewYear %>' context="html"/> <carlos:encode value='<%= hcRenewMonth %>' context="html"/> <carlos:encode value='<%= hcRenewDay %>' context="html"/>
                </td>
            </tr>
            <tr valign="top">
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formPatientStatus"/>:</b> <%
                    String pacStatus = d.getPatientStatus(); %>
                    <%
                        boolean nextStatus = true;

                        for (String pt : demographicDao.search_ptstatus()) {
                            if (pacStatus.equals(pt)) { %>
                    <carlos:encode value='<%=pt%>' context="html"/> <% nextStatus = false;
                    }
                    }

                    %> <% if (nextStatus) {

                    %> <carlos:encode value='<%=pacStatus%>' context="html"/> <% } %>
                </td>
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formChartNo"/>:</b></td>
                <td align="left"><carlos:encode value='<%=d.getChartNo()%>' context="html"/>
                </td>
            </tr>

            <%if (wL.getFound()) {%>
            <tr valign="top">
                <td align="left" nowrap><b>Waiting List: </b> <%
                    String listID = "", wlnote = "";
                    for (io.github.carlos_emr.carlos.commn.model.WaitingList w : waitingListDao.search_wlstatus(Integer.parseInt(demographic_no))) {
                        listID = String.valueOf(w.getListId());
                        wlnote = w.getNote();
                    }

                    for (WaitingListName wln : waitingListNameDao.findCurrentByGroup(((ProviderPreference) session.getAttribute(SessionConstants.LOGGED_IN_PROVIDER_PREFERENCE)).getMyGroupNo())) {
                        if (wln.getId().toString().equals(listID)) {
                %><carlos:encode value='<%=wln.getName()%>' context="html"/> <%
                        }
                    }
                %>
                </td>
                <td align="left" nowrap><b>Waiting List Note: </b></td>
                <td align="left"><carlos:encode value='<%=wlnote%>' context="html"/>
                </td>
            </tr>
            <%}%>
            <tr valign="top">
                <td align="left" nowrap><b><fmt:message key="demographic.demographiceditdemographic.formDateJoined1"/>: </b> <%
                    // Format year
                    decF.applyPattern("0000");
                    String dateJoinedYear = decF.format(MyDateFormat.getYearFromStandardDate(d.getFormattedDateJoined()));
                    decF.applyPattern("00");
                    String dateJoinedMonth = decF.format(MyDateFormat.getMonthFromStandardDate(d.getFormattedDateJoined()));
                    String dateJoinedDay = decF.format(MyDateFormat.getDayFromStandardDate(d.getFormattedDateJoined()));
                %> <carlos:encode value='<%= dateJoinedYear %>' context="html"/> <carlos:encode value='<%= dateJoinedMonth %>' context="html"/> <carlos:encode value='<%= dateJoinedDay %>' context="html"/>
                </td>
                <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formEndDate"/>: </b></td>
                <td align="left">
                    <%
                        // Format year
                        decF.applyPattern("0000");
                        String endYear = decF.format(MyDateFormat.getYearFromStandardDate(d.getFormattedEndDate()));
                        decF.applyPattern("00");
                        String endMonth = decF.format(MyDateFormat.getMonthFromStandardDate(d.getFormattedEndDate()));
                        String endDay = decF.format(MyDateFormat.getDayFromStandardDate(d.getFormattedEndDate()));
                    %> <carlos:encode value='<%= endYear %>' context="html"/> <carlos:encode value='<%= endMonth %>' context="html"/> <carlos:encode value='<%= endDay %>' context="html"/>
                </td>
            <tr valign="top">
                <td nowrap colspan="3">
                    <table width="100%" bgcolor="#EEEEFF">
                        <tr>
                            <td width="7%" align="left"><font color="#FF0000"><b><fmt:message key="demographic.demographiceditdemographic.formAlert"/>: </b></font></td>
                            <td><carlos:encode value='<%=alert%>' context="html"/>
                            </td>
                        </tr>
                        <tr>
                            <td align="left"><b><fmt:message key="demographic.demographiceditdemographic.formNotes"/>: </b></td>
                            <td><carlos:encode value='<%=notes%>' context="html"/>
                            </td>
                        </tr>
                    </table>
                </td>
            </tr>
        </table>
    </form>


    <%
        }

    %>

    </body>
</html>
