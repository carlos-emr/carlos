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
<%@ page import="ca.openosp.openo.commn.model.LookupListItem" %>
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.commn.model.LookupList" %>
<%@ page import="ca.openosp.openo.managers.LookupListManager" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
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
<%@ page import="java.util.*, ca.openosp.SxmlMisc, ca.openosp.openo.providers.data.ProviderBillCenter" errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.openo.log.LogAction,ca.openosp.openo.log.LogConst" %>
<%@ page import="ca.openosp.openo.commn.model.ClinicNbr" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.dao.ClinicNbrDao" %>
<%@ page import="ca.openosp.openo.commn.model.ProviderData" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderDataDao" %>
<%@ page import="ca.openosp.openo.commn.dao.SecurityDao" %>
<%@ page import="ca.openosp.openo.commn.model.Security" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.commn.model.ProviderSite" %>
<%@ page import="ca.openosp.openo.commn.dao.ProviderSiteDao" %>
<%@ page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Site" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.commn.Gender" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>
<%@ page import="ca.openosp.MyDateFormat" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/jquery-1.9.1.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providerupdateprovider.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
        <script LANGUAGE="JavaScript">
            <!--
            function setfocus() {
                document.updatearecord.last_name.focus();
                document.updatearecord.last_name.select();
            }

            jQuery(document).ready(function () {
                    jQuery("#provider_type").change(function () {

                            if (jQuery("#provider_type").val() == "resident") {
                                jQuery(".supervisor").slideDown(600);
                                jQuery("#supervisor").focus();

                            } else {
                                if (jQuery(".supervisor").is(":visible")) {
                                    jQuery(".supervisor").slideUp(600);
                                    jQuery("#supervisor").val("");
                                }
                            }
                        }
                    )

                }
            );

            //-->

            function onsub() {
                if (document.updatearecord.provider_no.value == "" ||
                    document.updatearecord.last_name.value == "" ||
                    document.updatearecord.first_name.value == "" ||
                    document.updatearecord.provider_type.value == "") {
                    alert("<fmt:setBundle basename="oscarResources"/><fmt:message key="global.msgInputKeyword"/>");
                    return false;
                }


                if (document.updatearecord.practitionerNo.value != "") {
                    var val = document.updatearecord.practitionerNoType.options[document.updatearecord.practitionerNoType.selectedIndex].value;
                    if (val == "") {
                        alert("Please choose a College Type");
                        return false;
                    }
                }
                if (!(document.updatearecord.provider_no.value == "-new-" || document.updatearecord.provider_no.value.match(/^[1-9]\d*$/))) {
                    alert("Provider No. must be a number.");
                    return false;
                } else {
                    return true;
                }
            }

        </script>
    </head>

    <%
        String curProvider_no = (String) session.getAttribute("user");
        List<Integer> siteIDs = new ArrayList<Integer>();
        boolean isSiteAccessPrivacy = false;
    %>

    <security:oscarSec objectName="_site_access_privacy"
                       roleName="<%=roleName$%>" rights="r" reverse="false">
        <%
            isSiteAccessPrivacy = true;

            ProviderSiteDao providerSiteDao = (ProviderSiteDao) SpringUtils.getBean(ProviderSiteDao.class);

            List<ProviderSite> psList = providerSiteDao.findByProviderNo(curProvider_no);
            for (ProviderSite pSite : psList) {
                siteIDs.add(pSite.getId().getSiteId());
            }

        %>
    </security:oscarSec>

    <body onLoad="setfocus()" topmargin="0" leftmargin="0" rightmargin="0">
    <%
        String keyword = request.getParameter("keyword");
        ProviderData provider = providerDao.findByProviderNo(keyword);

        if (provider == null) {
    %>
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providerupdateprovider.description"/></font></th>
            </tr>
        </table>
        <p><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.notFound">Provider not found</fmt:message></p>
    </center>
    </body>
    </html>
    <%
            return;
        }

        SecurityDao securityDao = (SecurityDao) SpringUtils.getBean(SecurityDao.class);
        List<Security> results = securityDao.findByProviderNo(provider.getId());
        Security security = null;
        if (results.size() > 0) security = results.get(0);

        LogAction.addLog((String) session.getAttribute("user"), LogConst.UPDATE, "adminUpdateUser",
                request.getParameter("keyword"), request.getRemoteAddr());
    %>
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providerupdateprovider.description"/></font></th>
            </tr>
        </table>

        <form method="post" action="providerupdate.jsp" name="updatearecord" onsubmit="return onsub()">

            <table cellspacing="0" cellpadding="2" width="100%" border="0"
                   datasrc='#xml_list'>

                <tr>
                    <td width="50%" align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formProviderNo"/>:
                    </td>
                    <td>
                                <% String provider_no = provider.getId(); %>
                                <%= provider_no %>
                        <input type="hidden" name="provider_no" value="<%= provider_no %>">

                </tr>
                <tr>
                    <td>
                        <div align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formLastName"/>:
                        </div>
                    </td>
                    <td><input type="text" index="3" name="last_name"
                               value="<%= Encode.forHtmlAttribute(provider.getLastName() == null ? "" : provider.getLastName()) %>" maxlength="30"></td>
                </tr>
                <tr>
                    <td>
                        <div align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formFirstName"/>:
                        </div>
                    </td>
                    <td><input type="text" index="4" name="first_name"
                               value="<%= Encode.forHtmlAttribute(provider.getFirstName() == null ? "" : provider.getFirstName()) %>" maxlength="30"></td>
                </tr>


                <% if (IsPropertiesOn.isMultisitesEnable()) { %>
                <tr>
                    <td>
                        <div align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.sitesAssigned"/><font color="red">:</font>
                        </div>
                    </td>
                    <td>
                        <%
                            SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                            List<Site> psites = siteDao.getActiveSitesByProviderNo(provider_no);
                            List<Site> sites = siteDao.getAllActiveSites();
                            for (int i = 0; i < sites.size(); i++) {
                        %>
                        <input type="checkbox" name="sites"
                               value="<%= sites.get(i).getSiteId() %>" <%= psites.contains(sites.get(i))?"checked='checked'":"" %> <%=((!isSiteAccessPrivacy) || siteIDs.contains(sites.get(i).getSiteId()) ? "" : " disabled ") %>>
                        <%= sites.get(i).getName() %><br/>
                        <%
                            }
                        %>
                    </td>
                </tr>
                <% } %>

                <tr>
                    <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType"/>:
                    </td>
                    <td>
                        <select id="provider_type" name="provider_type">
                            <option value="receptionist"
                                    <% if ("receptionist".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionReceptionist"/></option>
                            <option value="doctor"
                                    <% if ("doctor".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionDoctor"/></option>
                            <option value="nurse"
                                    <% if ("nurse".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionNurse"/></option>
                            <option value="resident"
                                    <% if ("resident".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionResident"/></option>
                            <option value="midwife"
                                    <% if ("midwife".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionMidwife"/></option>
                            <option value="admin"
                                    <% if ("admin".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formType.optionAdmin"/></option>
                        </select>
                        <!--input type="text" name="provider_type" value="<%= provider.getProviderType() %>" maxlength="15" -->
                    </td>
                </tr>
                <%

                    List<ProviderData> providerL = providerDao.findAllBilling("1");
                %>
                <tr class="supervisor" <%if (!"resident".equals(provider.getProviderType())) {%> style="display:none"
                <%
                    } else {
                    }
                %>">
                <td align="right">
                    Assigned Supervisor
                </td>
                <td>
                    <select id="supervisor" name="supervisor">
                        <option value="">Please Assign Supervisor</option>
                                <%
                    for( ProviderData p : providerL ) {
                        
                    %>
                        <option value="<%=p.getId()%>"
                                <%if( provider.getSupervisor() != null &&  provider.getSupervisor().equals(p.getId())){%>SELECTED<%}%>><%=p.getLastName() + ", " + p.getFirstName()%>
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
                                   value="<%= Encode.forHtmlAttribute(provider.getSpecialty() == null ? "" : provider.getSpecialty()) %>" maxlength="40"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formTeam"/>:
                        </td>
                        <td><input type="text" name="team"
                                   value="<%= Encode.forHtmlAttribute(provider.getTeam() == null ? "" : provider.getTeam()) %>" maxlength="20"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSex"/>:
                        </td>
                        <td><select name="sex" id="sex">
                            <option value=""></option>
                            <% for (Gender gn : Gender.values()) { %>
                            <option value=<%=gn.name()%> <%=((provider.getSex() != null && provider.getSex().toUpperCase().equals(gn.name())) ? "selected" : "") %>><%=gn.getText()%>
                            </option>
                            <% } %>
                        </select>
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formDOB"/>:
                        </td>
                        <td><input type="text" name="dob"
                                   value="<%= MyDateFormat.getMyStandardDate(provider.getDob()) %>"
                                   maxlength="11"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formAddress"/>:
                        </td>
                        <td><input type="text" name="address"
                                   value="<%= Encode.forHtmlAttribute(provider.getAddress()==null ? "" : provider.getAddress()) %>" size="40"
                                   maxlength="40"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formHomePhone"/>:
                        </td>
                        <td><input type="text" name="phone"
                                   value="<%= Encode.forHtmlAttribute(provider.getPhone()==null ? "" : provider.getPhone()) %>"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formWorkPhone"/>:
                        </td>
                        <td><input type="text" name="workphone"
                                   value="<%= Encode.forHtmlAttribute(provider.getWorkPhone()==null ? "" : provider.getWorkPhone()) %>"
                                   maxlength="50"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formEmail"/>:</td>
                        <td><input type="text" name="email"
                                   value="<%= Encode.forHtmlAttribute(provider.getEmail()==null ? "" : provider.getEmail()) %>"
                                   maxlength="50"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formPager"/>:
                        </td>
                        <td><input type="text" name="xml_p_pager"
                                   value="<%= Encode.forHtmlAttribute(SxmlMisc.getXmlContent(provider.getComments(),"xml_p_pager")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_pager")) %>"
                                   datafld='xml_p_pager'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formCell"/>:
                        </td>
                        <td><input type="text" name="xml_p_cell"
                                   value="<%= Encode.forHtmlAttribute(SxmlMisc.getXmlContent(provider.getComments(),"xml_p_cell")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_cell")) %>"
                                   datafld='xml_p_cell'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOtherPhone"/>:
                        </td>
                        <td><input type="text" name="xml_p_phone2"
                                   value="<%= Encode.forHtmlAttribute(SxmlMisc.getXmlContent(provider.getComments(),"xml_p_phone2")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_phone2")) %>"
                                   datafld='xml_p_phone2'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formFax"/>:
                        </td>
                        <td><input type="text" name="xml_p_fax"
                                   value="<%= Encode.forHtmlAttribute(SxmlMisc.getXmlContent(provider.getComments(),"xml_p_fax")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_fax")) %>"
                                   datafld='xml_p_fax'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOhipNo"/>:
                        </td>
                        <td><input type="text" name="ohip_no"
                                   value="<%= Encode.forHtmlAttribute(provider.getOhipNo()==null ? "" : provider.getOhipNo()) %>" maxlength="20">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formRmaNo"/>:
                        </td>
                        <td><input type="text" name="rma_no"
                                   value="<%= Encode.forHtmlAttribute(provider.getRmaNo()==null ? "" : provider.getRmaNo()) %>" maxlength="20">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formBillingNo"/>:
                        </td>
                        <td><input type="text" name="billing_no"
                                   value="<%= Encode.forHtmlAttribute(provider.getBillingNo()==null ? "" : provider.getBillingNo()) %>"
                                   maxlength="20"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formHsoNo"/>:
                        </td>
                        <td><input type="text" name="hso_no"
                                   value="<%= Encode.forHtmlAttribute(provider.getHsoNo()==null ? "" : provider.getHsoNo()) %>" maxlength="10">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formStatus"/>:
                        </td>
                        <td>
                            <input type="radio" id="statusActive" name="status"
                                   value="1" <%="1".equals(provider.getStatus()) ? "checked" : ""%>><label
                                for="statusActive"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formStatusActive"/></label>
                            <input type="radio" id="statusInactive" name="status"
                                   value="0" <%=!"1".equals(provider.getStatus()) ? "checked" : ""%>><label
                                for="statusInactive"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formStatusInactive"/></label>
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSpecialtyCode"/>:
                        </td>
                        <td><input type="text" name="xml_p_specialty_code"
                                   value="<%= Encode.forHtmlAttribute(SxmlMisc.getXmlContent(provider.getComments(),"xml_p_specialty_code")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_specialty_code")) %>"
                                   datafld='xml_p_specialty_code'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formBillingGroupNo"/>:
                        </td>
                        <td><input type="text" name="xml_p_billinggroup_no"
                                   value="<%= Encode.forHtmlAttribute(SxmlMisc.getXmlContent(provider.getComments(),"xml_p_billinggroup_no")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_billinggroup_no")) %>"
                                   datafld='xml_p_billinggroup_no'></td>
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
                                            String selected = "";
                                            if (llItem.getValue() != null && llItem.getValue().equals(provider.getPractitionerNoType())) {
                                                selected = " selected=\"selected\" ";
                                            }
                                %>

                                <option value="<%=llItem.getValue()%>" <%=selected %>><%=llItem.getLabel()%>
                                </option>
                                <%
                                    }
                                } else {
                                %>

                                <option value="">None Available</option>
                                <%
                                    }

                                %>
                            </select>

                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formCPSID"/>:
                        </td>
                        <td><input type="text" name="practitionerNo"
                                   value="<%= Encode.forHtmlAttribute(provider.getPractitionerNo()==null ? "" : provider.getPractitionerNo()) %>"
                                   maxlength="10"></td>
                    </tr>
                    <%
                        UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
                    %>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialFirstName"/>:</td>
                        <td><input type="text" name="officialFirstName"
                                   value="<%= Encode.forHtmlAttribute(StringUtils.trimToEmpty(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_FIRST_NAME))) %>"
                                   maxlength="255"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialSecondName"/>:</td>
                        <td><input type="text" name="officialSecondName"
                                   value="<%= Encode.forHtmlAttribute(StringUtils.trimToEmpty(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_SECOND_NAME))) %>"
                                   maxlength="255"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialLastName"/>:</td>
                        <td><input type="text" name="officialLastName"
                                   value="<%= Encode.forHtmlAttribute(StringUtils.trimToEmpty(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_LAST_NAME))) %>"
                                   maxlength="255"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialOlisIdentifierType"/>:</td>
                        <td><select name="officialOlisIdtype">
                            <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialOlisIdentifierType.option.notset"/></option>
                            <option value="MDL" <%="MDL".equals(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_OLIS_IDTYPE)) ? "SELECTED" : ""%>>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialOlisIdentifierType.option.mdl"/>
                            </option>
                            <option value="DDSL" <%="DDSL".equals(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_OLIS_IDTYPE)) ? "SELECTED" : ""%>>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialOlisIdentifierType.option.ddsl"/>
                            </option>
                            <option value="NPL" <%="NPL".equals(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_OLIS_IDTYPE)) ? "SELECTED" : ""%>>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialOlisIdentifierType.option.npl"/>
                            </option>
                            <option value="ML" <%="ML".equals(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_OLIS_IDTYPE)) ? "SELECTED" : ""%>>
                                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formOfficialOlisIdentifierType.option.ml"/>
                            </option>
                        </select>
                        </td>
                    </tr>


                    <% if (OscarProperties.getInstance().getBooleanProperty("rma_enabled", "true")) { %>
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
                                <option value="<%=tempNbr.getNbrValue()%>" <%=SxmlMisc.getXmlContent(provider.getComments(), "xml_p_nbr").startsWith(tempNbr.getNbrValue()) ? "selected" : ""%>><%=valueString%>
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
                                String currentBillCode = billCenter.getBillCenter(provider_no);
                                for (int i = 0; i < billCenter.getAllBillCenter().size(); i++) {
                                    billCode = (String) keys.nextElement();
                                    codeDesc = billCenter.getAllBillCenter().getProperty(billCode);
                            %>
                            <option value=<%= billCode %>
                                    <%=currentBillCode.compareTo(billCode) == 0 ? "selected" : ""%>><%= codeDesc%>
                            </option>
                            <%
                                }
                            %>
                        </select></td>
                    </tr>

                    <input type="hidden" name="provider_activity" value="">


                </caisi:isModuleLoad>
                <tr>
                    <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSlpUsername"/>:
                    </td>
                    <td><input type="text" name="xml_p_slpusername"
                               value="<%= Encode.forHtmlAttribute(SxmlMisc.getXmlContent(provider.getComments(),"xml_p_slpusername")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_slpusername")) %>"
                               datafld='xml_p_slpusername'></td>
                </tr>
                <tr>
                    <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provider.formSlpPassword"/>:
                    </td>
                    <td><input type="text" name="xml_p_slppassword"
                               value="<%= Encode.forHtmlAttribute(SxmlMisc.getXmlContent(provider.getComments(),"xml_p_slppassword")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_slppassword")) %>"
                               datafld='xml_p_slppassword'></td>
                </tr>
                <tr>
                    <td align="right"><fmt:setBundle basename="oscarResources"/><fmt:message key="provider.login.title.confidentiality"/>:
                    </td>
                    <td><input type="text" readonly name="signed_confidentiality"
                               value="<%= Encode.forHtmlAttribute(provider.getSignedConfidentiality()==null ? "" : String.valueOf(provider.getSignedConfidentiality())) %>">
                    </td>
                </tr>


                <tr>
                    <td colspan="2">
                        <div align="center"><input type="submit"
                                                   name="subbutton"
                                                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providerupdateprovider.btnSubmit"/>">
                        </div>
                    </td>
                </tr>

            </table>
        </form>

    </center>
    </body>
</html>
