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
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupListItem" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.LookupList" %>
<%@ page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.userAdmin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<%@ page import="java.util.*, io.github.carlos_emr.SxmlMisc, io.github.carlos_emr.carlos.providers.data.ProviderBillCenter" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.log.LogAction,io.github.carlos_emr.carlos.log.LogConst" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ClinicNbr" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SecurityDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Security" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderSite" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@ page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.commn.Gender" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);
%>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%= request.getContextPath() %>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%= request.getContextPath() %>/library/jquery/jquery-compat.js"></script>
        <title><fmt:message key="admin.providerupdateprovider.title"/></title>
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
                    alert("<fmt:message key="global.msgInputKeyword"/>");
                    return false;
                }


                if (document.updatearecord.practitionerNo.value != "") {
                    var val = document.updatearecord.practitionerNoType.options[document.updatearecord.practitionerNoType.selectedIndex].value;
                    if (val == "") {
                        alert("<fmt:message key="admin.providerupdateprovider.msgChooseCollegeType"/>");
                        return false;
                    }
                }
                if (!(document.updatearecord.provider_no.value == "-new-" || document.updatearecord.provider_no.value.match(/^[1-9]\d*$/))) {
                    alert("<fmt:message key="admin.providerupdateprovider.msgProviderNoNumber"/>");
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
                <th><font face="Helvetica" color="#FFFFFF"><fmt:message key="admin.providerupdateprovider.description"/></font></th>
            </tr>
        </table>
        <p><fmt:message key="admin.provider.notFound">Provider not found</fmt:message></p>
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
                <th><font face="Helvetica" color="#FFFFFF"><fmt:message key="admin.providerupdateprovider.description"/></font></th>
            </tr>
        </table>

        <form method="post" action="${pageContext.request.contextPath}/admin/ProviderUpdate" name="updatearecord" onsubmit="return onsub()">

            <table cellspacing="0" cellpadding="2" width="100%" border="0"
                   datasrc='#xml_list'>

                <tr>
                    <td width="50%" align="right"><fmt:message key="admin.provider.formProviderNo"/>:
                    </td>
                    <td>
                                <% String provider_no = provider.getId(); %>
                                <carlos:encode value='<%= provider_no %>' context="html"/>
                        <input type="hidden" name="provider_no" value="<carlos:encode value='<%= provider_no %>' context="htmlAttribute"/>">

                </tr>
                <tr>
                    <td>
                        <div align="right"><fmt:message key="admin.provider.formLastName"/>:
                        </div>
                    </td>
                    <td><input type="text" index="3" name="last_name"
                               value="<carlos:encode value='<%= provider.getLastName() == null ? "" : provider.getLastName() %>' context="htmlAttribute"/>" maxlength="30"></td>
                </tr>
                <tr>
                    <td>
                        <div align="right"><fmt:message key="admin.provider.formFirstName"/>:
                        </div>
                    </td>
                    <td><input type="text" index="4" name="first_name"
                               value="<carlos:encode value='<%= provider.getFirstName() == null ? "" : provider.getFirstName() %>' context="htmlAttribute"/>" maxlength="30"></td>
                </tr>


                <% if (IsPropertiesOn.isMultisitesEnable()) { %>
                <tr>
                    <td>
                        <div align="right"><fmt:message key="admin.provider.sitesAssigned"/><font color="red">:</font>
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
                               value="<carlos:encode value='<%= sites.get(i).getSiteId() == null ? "" : String.valueOf(sites.get(i).getSiteId()) %>' context="htmlAttribute"/>" <%= psites.contains(sites.get(i))?"checked='checked'":"" %> <%=((!isSiteAccessPrivacy) || siteIDs.contains(sites.get(i).getSiteId()) ? "" : " disabled ") %>>
                        <carlos:encode value='<%= sites.get(i).getName() == null ? "" : sites.get(i).getName() %>' context="html"/><br/>
                        <%
                            }
                        %>
                    </td>
                </tr>
                <% } %>

                <tr>
                    <td align="right"><fmt:message key="admin.provider.formType"/>:
                    </td>
                    <td>
                        <select id="provider_type" name="provider_type">
                            <option value="receptionist"
                                    <% if ("receptionist".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:message key="admin.provider.formType.optionReceptionist"/></option>
                            <option value="doctor"
                                    <% if ("doctor".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:message key="admin.provider.formType.optionDoctor"/></option>
                            <option value="nurse"
                                    <% if ("nurse".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:message key="admin.provider.formType.optionNurse"/></option>
                            <option value="resident"
                                    <% if ("resident".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:message key="admin.provider.formType.optionResident"/></option>
                            <option value="midwife"
                                    <% if ("midwife".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:message key="admin.provider.formType.optionMidwife"/></option>
                            <option value="admin"
                                    <% if ("admin".equals(provider.getProviderType())) { %>
                                    SELECTED <%}%>><fmt:message key="admin.provider.formType.optionAdmin"/></option>
                        </select>
                        <%-- Removed: unused commented-out provider_type input (XSS vector via stored providerType) --%>
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
                <td align="right"><fmt:message key="admin.providerupdateprovider.assignedSupervisor"/></td>
                <td>
                    <select id="supervisor" name="supervisor">
                        <option value=""><fmt:message key="admin.providerupdateprovider.pleaseAssignSupervisor"/></option>
                                <%
                    for( ProviderData p : providerL ) {
                        
                    %>
                        <option value="<carlos:encode value='<%= p.getId() == null ? "" : p.getId() %>' context="htmlAttribute"/>"
                                <%if( provider.getSupervisor() != null &&  provider.getSupervisor().equals(p.getId())){%>SELECTED<%}%>><carlos:encode value='<%= (p.getLastName() == null ? "" : p.getLastName()) + ", " + (p.getFirstName() == null ? "" : p.getFirstName()) %>' context="html"/>
                        </option>

                                <%
                    }
                    %>
                </td>
                </tr>
                <caisi:isModuleLoad moduleName="TORONTO_RFQ" reverse="true">
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formSpecialty"/>:
                        </td>
                        <td><input type="text" name="specialty"
                                   value="<carlos:encode value='<%= provider.getSpecialty() == null ? "" : provider.getSpecialty() %>' context="htmlAttribute"/>" maxlength="40"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formTeam"/>:
                        </td>
                        <td><input type="text" name="team"
                                   value="<carlos:encode value='<%= provider.getTeam() == null ? "" : provider.getTeam() %>' context="htmlAttribute"/>" maxlength="20"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formSex"/>:
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
                        <td align="right"><fmt:message key="admin.provider.formDOB"/>:
                        </td>
                        <td><input type="text" name="dob"
                                   value="<%= MyDateFormat.getMyStandardDate(provider.getDob()) %>"
                                   maxlength="11"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formAddress"/>:
                        </td>
                        <td><input type="text" name="address"
                                   value="<carlos:encode value='<%= provider.getAddress()==null ? "" : provider.getAddress() %>' context="htmlAttribute"/>" size="40"
                                   maxlength="40"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formHomePhone"/>:
                        </td>
                        <td><input type="text" name="phone"
                                   value="<carlos:encode value='<%= provider.getPhone()==null ? "" : provider.getPhone() %>' context="htmlAttribute"/>"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formWorkPhone"/>:
                        </td>
                        <td><input type="text" name="workphone"
                                   value="<carlos:encode value='<%= provider.getWorkPhone()==null ? "" : provider.getWorkPhone() %>' context="htmlAttribute"/>"
                                   maxlength="50"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formEmail"/>:</td>
                        <td><input type="text" name="email"
                                   value="<carlos:encode value='<%= provider.getEmail()==null ? "" : provider.getEmail() %>' context="htmlAttribute"/>"
                                   maxlength="50"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formPager"/>:
                        </td>
                        <td><input type="text" name="xml_p_pager"
                                   value="<carlos:encode value='<%= SxmlMisc.getXmlContent(provider.getComments(),"xml_p_pager")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_pager") %>' context="htmlAttribute"/>"
                                   datafld='xml_p_pager'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formCell"/>:
                        </td>
                        <td><input type="text" name="xml_p_cell"
                                   value="<carlos:encode value='<%= SxmlMisc.getXmlContent(provider.getComments(),"xml_p_cell")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_cell") %>' context="htmlAttribute"/>"
                                   datafld='xml_p_cell'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formOtherPhone"/>:
                        </td>
                        <td><input type="text" name="xml_p_phone2"
                                   value="<carlos:encode value='<%= SxmlMisc.getXmlContent(provider.getComments(),"xml_p_phone2")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_phone2") %>' context="htmlAttribute"/>"
                                   datafld='xml_p_phone2'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formFax"/>:
                        </td>
                        <td><input type="text" name="xml_p_fax"
                                   value="<carlos:encode value='<%= SxmlMisc.getXmlContent(provider.getComments(),"xml_p_fax")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_fax") %>' context="htmlAttribute"/>"
                                   datafld='xml_p_fax'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formOhipNo"/>:
                        </td>
                        <td><input type="text" name="ohip_no"
                                   value="<carlos:encode value='<%= provider.getOhipNo()==null ? "" : provider.getOhipNo() %>' context="htmlAttribute"/>" maxlength="20">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formRmaNo"/>:
                        </td>
                        <td><input type="text" name="rma_no"
                                   value="<carlos:encode value='<%= provider.getRmaNo()==null ? "" : provider.getRmaNo() %>' context="htmlAttribute"/>" maxlength="20">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formBillingNo"/>:
                        </td>
                        <td><input type="text" name="billing_no"
                                   value="<carlos:encode value='<%= provider.getBillingNo()==null ? "" : provider.getBillingNo() %>' context="htmlAttribute"/>"
                                   maxlength="20"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formHsoNo"/>:
                        </td>
                        <td><input type="text" name="hso_no"
                                   value="<carlos:encode value='<%= provider.getHsoNo()==null ? "" : provider.getHsoNo() %>' context="htmlAttribute"/>" maxlength="10">
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formStatus"/>:
                        </td>
                        <td>
                            <input type="radio" id="statusActive" name="status"
                                   value="1" <%="1".equals(provider.getStatus()) ? "checked" : ""%>><label
                                for="statusActive"><fmt:message key="admin.provider.formStatusActive"/></label>
                            <input type="radio" id="statusInactive" name="status"
                                   value="0" <%=!"1".equals(provider.getStatus()) ? "checked" : ""%>><label
                                for="statusInactive"><fmt:message key="admin.provider.formStatusInactive"/></label>
                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formSpecialtyCode"/>:
                        </td>
                        <td><input type="text" name="xml_p_specialty_code"
                                   value="<carlos:encode value='<%= SxmlMisc.getXmlContent(provider.getComments(),"xml_p_specialty_code")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_specialty_code") %>' context="htmlAttribute"/>"
                                   datafld='xml_p_specialty_code'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formBillingGroupNo"/>:
                        </td>
                        <td><input type="text" name="xml_p_billinggroup_no"
                                   value="<carlos:encode value='<%= SxmlMisc.getXmlContent(provider.getComments(),"xml_p_billinggroup_no")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_billinggroup_no") %>' context="htmlAttribute"/>"
                                   datafld='xml_p_billinggroup_no'></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formCPSIDType"/>:
                        </td>
                        <td>
                            <select name="practitionerNoType" id="practitionerNoType">
                                <option value=""><fmt:message key="admin.providerupdateprovider.selectBelow"/></option>
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

                                <option value="<carlos:encode value='<%= llItem.getValue() == null ? "" : llItem.getValue() %>' context="htmlAttribute"/>" <%=selected %>><carlos:encode value='<%= llItem.getLabel() == null ? "" : llItem.getLabel() %>' context="html"/>
                                </option>
                                <%
                                    }
                                } else {
                                %>

                                <option value=""><fmt:message key="admin.providerupdateprovider.noneAvailable"/></option>
                                <%
                                    }

                                %>
                            </select>

                        </td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formCPSID"/>:
                        </td>
                        <td><input type="text" name="practitionerNo"
                                   value="<carlos:encode value='<%= provider.getPractitionerNo()==null ? "" : provider.getPractitionerNo() %>' context="htmlAttribute"/>"
                                   maxlength="10"></td>
                    </tr>
                    <%
                        UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);
                    %>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formOfficialFirstName"/>:</td>
                        <td><input type="text" name="officialFirstName"
                                   value="<carlos:encode value='<%= StringUtils.trimToEmpty(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_FIRST_NAME)) %>' context="htmlAttribute"/>"
                                   maxlength="255"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formOfficialSecondName"/>:</td>
                        <td><input type="text" name="officialSecondName"
                                   value="<carlos:encode value='<%= StringUtils.trimToEmpty(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_SECOND_NAME)) %>' context="htmlAttribute"/>"
                                   maxlength="255"></td>
                    </tr>
                    <tr>
                        <td align="right"><fmt:message key="admin.provider.formOfficialLastName"/>:</td>
                        <td><input type="text" name="officialLastName"
                                   value="<carlos:encode value='<%= StringUtils.trimToEmpty(userPropertyDAO.getStringValue(provider_no, UserProperty.OFFICIAL_LAST_NAME)) %>' context="htmlAttribute"/>"
                                   maxlength="255"></td>
                    </tr>
                    <% if (CarlosProperties.getInstance().getBooleanProperty("rma_enabled", "true")) { %>
                    <tr>
                        <td align="right"><fmt:message key="admin.providerupdateprovider.defaultClinicNbr"/></td>
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
                                <option value="<carlos:encode value='<%= tempNbr.getNbrValue() == null ? "" : tempNbr.getNbrValue() %>' context="htmlAttribute"/>" <%=StringUtils.defaultString(SxmlMisc.getXmlContent(provider.getComments(), "xml_p_nbr")).startsWith(tempNbr.getNbrValue() == null ? "" : tempNbr.getNbrValue()) ? "selected" : ""%>><carlos:encode value='<%= valueString %>' context="html"/>
                                </option>
                                <%}%>

                            </select>
                        </td>
                    </tr>
                    <%} %>
                    <tr>
                        <td align="right"><fmt:message key="admin.providerupdateprovider.billCenter"/></td>
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
                            <option value="<carlos:encode value='<%= billCode %>' context="htmlAttribute"/>"
                                    <%=currentBillCode.compareTo(billCode) == 0 ? "selected" : ""%>><carlos:encode value='<%= codeDesc %>' context="html"/>
                            </option>
                            <%
                                }
                            %>
                        </select></td>
                    </tr>

                    <input type="hidden" name="provider_activity" value="">


                </caisi:isModuleLoad>
                <tr>
                    <td align="right"><fmt:message key="admin.provider.formSlpUsername"/>:
                    </td>
                    <td><input type="text" name="xml_p_slpusername"
                               value="<carlos:encode value='<%= SxmlMisc.getXmlContent(provider.getComments(),"xml_p_slpusername")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_slpusername") %>' context="htmlAttribute"/>"
                               datafld='xml_p_slpusername'></td>
                </tr>
                <tr>
                    <td align="right"><fmt:message key="admin.provider.formSlpPassword"/>:
                    </td>
                    <td><input type="text" name="xml_p_slppassword"
                               value="<carlos:encode value='<%= SxmlMisc.getXmlContent(provider.getComments(),"xml_p_slppassword")==null ? "" : SxmlMisc.getXmlContent(provider.getComments(),"xml_p_slppassword") %>' context="htmlAttribute"/>"
                               datafld='xml_p_slppassword'></td>
                </tr>
                <tr>
                    <td align="right"><fmt:message key="provider.login.title.confidentiality"/>:
                    </td>
                    <td><input type="text" readonly name="signed_confidentiality"
                               value="<carlos:encode value='<%= provider.getSignedConfidentiality()==null ? "" : String.valueOf(provider.getSignedConfidentiality()) %>' context="htmlAttribute"/>">
                    </td>
                </tr>


                <tr>
                    <td colspan="2">
                        <div align="center"><input type="submit"
                                                   name="subbutton"
                                                   value="<fmt:message key="admin.providerupdateprovider.btnSubmit"/>">
                        </div>
                    </td>
                </tr>

            </table>
        </form>

    </center>
    </body>
</html>
