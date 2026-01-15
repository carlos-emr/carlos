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

--%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ page import="java.util.*,ca.openosp.openo.providers.data.*" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="ca.openosp.openo.commn.model.LookupListItem" %>
<%@ page import="ca.openosp.openo.commn.model.LookupList" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.managers.LookupListManager" %>
<%@ page import="ca.openosp.openo.commn.model.ClinicNbr" %>
<%@ page import="ca.openosp.openo.commn.dao.ClinicNbrDao" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@ page import="ca.openosp.openo.commn.model.Site" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderDataDao" %>
<%@ page import="ca.openosp.openo.commn.model.ProviderData" %>
<%@ page import="ca.openosp.openo.commn.Gender" %>
<%@ page import="ca.openosp.openo.providers.data.ProviderBillCenter" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>
<%
    String curProvider_no = (String) session.getAttribute("user");
    //display the main providers page
    //includeing the providers name and a month calendar

    ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);
    List<ProviderData> list = providerDataDao.findAll();
    List<Integer> providerList = new ArrayList<>();
    for (ProviderData h : list) {
        try {
            String pn = h.getId();
            providerList.add(Integer.valueOf(pn));
        } catch (
                Exception e) {/*empty*/} /*No need to do anything. Just want to avoid a NumberFormatException from providers numbers with alphanumeric Characters*/
    }

    String suggestProviderNo = "";
    for (Integer i = 1; i < 1000000; i++) {
        if (!providerList.contains(i)) {
            suggestProviderNo = i.toString();
            break;
        }
    }
%>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean isSiteAccessPrivacy = false;
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r" reverse="false">
<%
    isSiteAccessPrivacy = true;
%>
</security:oscarSec>
<html>
    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery-1.9.1.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecordhtm.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
        <script LANGUAGE="JavaScript">
            <!--
            function setfocus() {
                document.searchprovider.provider_no.focus();
                document.searchprovider.provider_no.select();
            }

            function onsub() {
                if (document.searchprovider.provider_no.value == "" ||
                    document.searchprovider.last_name.value == "" ||
                    document.searchprovider.first_name.value == "" ||
                    document.searchprovider.provider_type.value == "") {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="global.msgInputKeyword"/>");
                    return false;
                }


                if (document.searchprovider.practitionerNo.value != "") {
                    var val = document.searchprovider.practitionerNoType.options[document.searchprovider.practitionerNoType.selectedIndex].value;
                    if (val == "") {
                        alert("Please choose a College Type");
                        return false;
                    }
                }
                if (!(document.searchprovider.provider_no.value == "-new-" || document.searchprovider.provider_no.value.match(/^[1-9]\d*$/))) {
                    alert("Provider No. must be a number.");
                    return false;
                } else {
                    return true;
                }

            }

            function upCaseCtrl(ctrl) {
                ctrl.value = ctrl.value.toUpperCase();
            }

            jQuery(document).ready(function () {
                    jQuery("#provider_type").change(function () {

                            if (jQuery("#provider_type").val() == "resident") {
                                jQuery(".supervisor").slideDown(600);
                                jQuery("#supervisor").focus();

                            } else {
                                if (jQuery(".supervisor").is(":visible")) {
                                    jQuery(".supervisor").slideUp(600);
                                }
                            }
                        }
                    )

                }
            );


            //-->
        </script>
    </head>

    <body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th align="CENTER"><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecordhtm.description"/></font></th>
            </tr>
        </table>
        <form method="post" action="provideraddarecord.jsp" name="searchprovider" onsubmit="return onsub()">
            <table cellspacing="0" cellpadding="2" width="90%" border="0">
                <tr>
                    <td width="50%" align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formProviderNo"/><font color="red">:</font></td>
                    <td>
                        <%
                            if (OscarProperties.getInstance().isProviderNoAuto()) {
                        %> <input
                            type="text" name="provider_no" maxlength="6" readonly="readonly"
                            value="-new-"> <%
                    } else {%>
                        <input type="text" name="provider_no" maxlength="6"/>
                        <input type="button" value='<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecordhtm.suggest"/>'
                               onclick="provider_no.value='<%=suggestProviderNo%>'"
                        <%}%>
                    </td>
                </tr>
                <tr>
                    <td>
                        <div align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formLastName"/><font color="red">:</font></div>
                    </td>
                    <td><input type="text" name="last_name" maxlength="30"></td>
                </tr>
                <tr>
                    <td>
                        <div align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formFirstName"/><font color="red">:</font></div>
                    </td>
                    <td><input type="text" name="first_name" maxlength="30">
                    </td>
                </tr>

                <%
                    if (IsPropertiesOn.isMultisitesEnable()) {
                %>
                <tr>
                    <td>
                        <div align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.sitesAssigned"/><font color="red">:</font>
                        </div>
                    </td>
                    <td>
                        <%
                            SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                            List<Site> sites = (isSiteAccessPrivacy ? siteDao.getActiveSitesByProviderNo(curProvider_no) : siteDao.getAllActiveSites());
                            for (int i = 0; i < sites.size(); i++) {
                        %>
                        <input type="checkbox" name="sites"
                               value="<%=sites.get(i).getSiteId()%>"><%=sites.get(i).getName()%><br/>
                        <%
                            }
                        %>
                    </td>
                </tr>
                <%
                    }
                %>

                <tr>
                    <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType"/><font
                            color="red">:</font></td>
                    <td><!--input type="text" name="provider_type" --> <%

                    %> <select id="provider_type" name="provider_type">
                        <option value="receptionist"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionReceptionist"/></option>
                        <option value="doctor" selected="selected"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionDoctor"/></option>
                        <option value="nurse"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionNurse"/></option>
                        <option value="resident"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionResident"/></option>
                        <option value="midwife"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionMidwife"/></option>
                        <option value="admin"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionAdmin"/></option>
                    </select>
                    </td>
                </tr>
                <%

                    List<ProviderData> providerL = providerDataDao.findAllBilling("1");
                %>
                <tr class="supervisor" style="display:none">
                    <td align="right">
                        Assigned Supervisor
                    </td>
                    <td>
                        <select id="supervisor" name="supervisor">
                            <option value="">Please Assign Supervisor</option>
                                    <%
                    for( ProviderData p : providerL ) {
                    %>
                            <option value="<%=p.getId()%>"><%=p.getLastName() + ", " + p.getFirstName()%>
                            </option>

                                    <%
                    }
                    %>
                    </td>
                </tr>
                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSpecialty"/>:
                        </td>
                        <td><input type="text" name="specialty"
                                   onBlur="upCaseCtrl(this)" maxlength="40"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formTeam"/>:
                        </td>
                        <td><input type="text" name="team" maxlength="20"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSex"/>:
                        </td>
                        <td><select name="sex" id="sex">
                            <option value=""></option>
                            <% for (Gender gn : Gender.values()) { %>
                            <option value=<%=gn.name()%>><%=gn.getText()%>
                            </option>
                            <% } %>
                        </select>
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formDOB"/>(<font
                                size="-1"><i><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecordhtm.dateFormat"/></i></font>):
                        </td>
                        <td><input type="text" name="dob" maxlength="11"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formAddress"/>:
                        </td>
                        <td><input type="text" name="address" size="40" maxlength="40">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formHomePhone"/>:
                        </td>
                        <td><input type="text" name="phone" maxlength="20"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formWorkPhone"/>:
                        </td>
                        <td><input type="text" name="workphone" value="" maxlength="20">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formEmail"/>:
                        </td>
                        <td><input type="text" name="email" value=""></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formPager"/>:
                        </td>
                        <td><input type="text" name="xml_p_pager" value=""></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formCell"/>:
                        </td>
                        <td><input type="text" name="xml_p_cell" value=""></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOtherPhone"/>:
                        </td>
                        <td><input type="text" name="xml_p_phone2" value=""></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formFax"/>:
                        </td>
                        <td><input type="text" name="xml_p_fax" value=""></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOhipNo"/>:
                        </td>
                        <td><input type="text" name="ohip_no" maxlength="20"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formRmaNo"/>:
                        </td>
                        <td><input type="text" name="rma_no" maxlength="20"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formBillingNo"/>:
                        </td>
                        <td><input type="text" name="billing_no" maxlength="20">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formHsoNo"/>:
                        </td>
                        <td><input type="text" name="hso_no" maxlength="10"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSpecialtyCode"/>:
                        </td>
                        <td><input type="text" name="xml_p_specialty_code"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formBillingGroupNo"/>:
                        </td>
                        <td><input type="text" name="xml_p_billinggroup_no"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formCPSIDType"/>:
                        </td>
                        <td>
                            <select name="practitionerNoType" id="practitionerNoType">
                                <option value="">Select Below</option>
                                <%
                                    LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
                                    LookupList ll = lookupListManager.findLookupListByName(LoggedInInfo.getLoggedInInfoFromSession(request), "practitionerNoType");
                                    if (ll != null) {
                                        for (LookupListItem llItem : ll.getItems()) {
                                %>
                                <option value="<%=llItem.getValue()%>"><%=llItem.getLabel()%>
                                </option>
                                <%
                                        }
                                    }
                                %>
                            </select>

                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formCPSID"/>:
                        </td>
                        <td><input type="text" name="practitionerNo" id="practitionerNo"></td>
                    </tr>

                    <%
                        if (OscarProperties.getInstance().getBooleanProperty("rma_enabled", "true")) {
                    %>
                    <tr>
                        <td align="right">Default Clinic NBR:</td>
                        <td colspan="3">
                            <select name="xml_p_nbr">
                                <%
                                    ClinicNbrDao clinicNbrDAO = (ClinicNbrDao) SpringUtils.getBean(ClinicNbrDao.class);
                                    List<ClinicNbr> nbrList = clinicNbrDAO.findAll();
                                    Iterator<ClinicNbr> nbrIter = nbrList.iterator();
                                    while (nbrIter.hasNext()) {
                                        ClinicNbr tempNbr = nbrIter.next();
                                        String valueString = tempNbr.getNbrValue() + " | " + tempNbr.getNbrString();
                                %>
                                <option value="<%=tempNbr.getNbrValue()%>"><%=valueString%>
                                </option>
                                <%}%>

                            </select>
                        </td>
                    </tr>
                    <%} %>

                    <tr>
                        <td align="right">Bill Center:</td>
                        <td><select name="billcenter">
                            <option value=""></option>
                            <%
                                ProviderBillCenter billCenter = new ProviderBillCenter();
                                String billCode = "";
                                String codeDesc = "";
                                Enumeration<?> keys = billCenter.getAllBillCenter().propertyNames();
                                String defaultBillCenter = OscarProperties.getInstance().getProperty("default_bill_center", "");

                                for (int i = 0; i < billCenter.getAllBillCenter().size(); i++) {

                                    billCode = (String) keys.nextElement();

                                    String selectedBillCenter = "";
                                    if (billCode.equalsIgnoreCase(defaultBillCenter))
                                        selectedBillCenter = "selected=\"selected\"";

                                    codeDesc = billCenter.getAllBillCenter().getProperty(billCode);
                            %>
                            <option value=<%= billCode %> <%=selectedBillCenter%>><%= codeDesc%>
                            </option>
                            <%
                                }
                            %>
                        </select></td>
                    </tr>

                    <input type="hidden" name="provider_activity" value="">

                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSlpUsername"/>:
                        </td>
                        <td><input type="text" name="xml_p_slpusername"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSlpPassword"/>:
                        </td>
                        <td><input type="text" name="xml_p_slppassword"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formStatus"/>:
                        </td>
                        <td>
                            <input type="radio" id="statusActive" name="status" value="1" checked><label
                                for="statusActive"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formStatusActive"/></label>
                            <input type="radio" id="statusInactive" name="status" value="0"><label for="statusInactive"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formStatusInactive"/></label>
                        </td>
                    </tr>

                </caisi:isModuleLoad>
                <tr>
                    <td colspan="2">
                        <div align="center">

                            <input type="submit" name="submitbtn"
                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecordhtm.btnProviderAddRecord"/>">
                        </div>
                    </td>
                </tr>
            </table>
        </form>

        <p></p>


    </center>
    </body>
</html>
