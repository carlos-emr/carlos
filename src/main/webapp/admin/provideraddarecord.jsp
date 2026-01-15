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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String curProvider_no = (String) session.getAttribute("user");

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
    <%isSiteAccessPrivacy = true; %>
</security:oscarSec>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ page import="java.sql.*, java.util.*, ca.openosp.*" errorPage="/errorpage.jsp" %>
<%@ page import="ca.openosp.openo.log.LogAction,ca.openosp.openo.log.LogConst" %>
<%@ page import="ca.openosp.openo.log.*, ca.openosp.openo.db.*" %>

<%@page import="ca.openosp.openo.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.commn.model.Site" %>

<%@ page import="org.apache.commons.text.StringEscapeUtils,ca.openosp.openo.providers.data.ProviderBillCenter" %>

<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="ca.openosp.openo.commn.model.Provider" %>
<%@ page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@page import="ca.openosp.openo.commn.model.ProviderSite" %>
<%@page import="ca.openosp.openo.commn.model.ProviderSitePK" %>
<%@page import="ca.openosp.openo.commn.dao.ProviderSiteDao" %>
<%@ page import="ca.openosp.openo.db.DBPreparedHandler" %>
<%@ page import="ca.openosp.openo.commn.IsPropertiesOn" %>
<%@ page import="ca.openosp.MyDateFormat" %>
<%@ page import="ca.openosp.SxmlMisc" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%
    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);
    boolean alreadyExists = false;
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecord.title"/></title>
        <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css">
    </head>

    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th><font face="Helvetica" color="#FFFFFF">
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecord.description"/>
                </font></th>
            </tr>
        </table>
        <%
            boolean isOk = false;
            int retry = 0;
            String curUser_no = (String) session.getAttribute("user");

            Provider p = new Provider();
            p.setProviderNo(request.getParameter("provider_no"));
            p.setLastName(request.getParameter("last_name"));
            p.setFirstName(request.getParameter("first_name"));
            p.setProviderType(request.getParameter("provider_type"));
            p.setSpecialty(request.getParameter("specialty"));
            p.setTeam(request.getParameter("team"));
            p.setSex(request.getParameter("sex"));
            p.setDob(MyDateFormat.getSysDate(request.getParameter("dob")));
            p.setAddress(request.getParameter("address"));
            p.setPhone(request.getParameter("phone"));
            p.setWorkPhone(request.getParameter("workphone"));
            p.setEmail(request.getParameter("email"));
            p.setOhipNo(request.getParameter("ohip_no"));
            p.setRmaNo(request.getParameter("rma_no"));
            p.setBillingNo(request.getParameter("billing_no"));
            p.setHsoNo(request.getParameter("hso_no"));
            p.setStatus(request.getParameter("status"));
            p.setComments(SxmlMisc.createXmlDataString(request, "xml_p"));
            p.setProviderActivity(request.getParameter("provider_activity"));
            p.setPractitionerNo(request.getParameter("practitionerNo"));
            p.setPractitionerNoType(request.getParameter("practitionerNoType"));
            p.setLastUpdateUser((String) session.getAttribute("user"));
            p.setLastUpdateDate(new java.util.Date());
            p.setSupervisor(request.getParameter("supervisor"));

//multi-office provide id formalize check, can be turn off on properties multioffice.formalize.providers.id
            boolean isProviderFormalize = true;
            String errMsgProviderFormalize = "admin.provideraddrecord.msgAdditionFailure";
            Integer min_value = 0;
            Integer max_value = 0;

            if (IsPropertiesOn.isProviderFormalizeEnable()) {

                String StrProviderId = request.getParameter("provider_no");
                OscarProperties props = OscarProperties.getInstance();

                String[] provider_sites = {};

                // get providers id ranger
                if (request.getParameter("provider_type").equalsIgnoreCase("doctor")) {
                    //providers is doctor, get providers id range from Property
                    min_value = new Integer(props.getProperty("multioffice.formalize.doctor.minimum.provider.id", ""));
                    max_value = new Integer(props.getProperty("multioffice.formalize.doctor.maximum.provider.id", ""));
                } else {
                    //non-doctor role
                    provider_sites = request.getParameterValues("sites");
                    provider_sites = (provider_sites == null ? new String[]{} : provider_sites);

                    if (provider_sites.length > 1) {
                        //non-doctor can only have one site
                        isProviderFormalize = false;
                        errMsgProviderFormalize = "admin.provideraddrecord.msgFormalizeProviderIdMultiSiteFailure";
                    } else {
                        if (provider_sites.length == 1) {
                            //get providers id range from site
                            String provider_site_id = provider_sites[0];
                            SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);
                            Site provider_site = siteDao.getById(new Integer(provider_site_id));
                            min_value = provider_site.getProviderIdFrom();
                            max_value = provider_site.getProviderIdTo();
                        }
                    }
                }
                if (isProviderFormalize) {
                    try {
                        Integer providerId = Integer.parseInt(StrProviderId);
                        if (request.getParameter("provider_type").equalsIgnoreCase("doctor") || provider_sites.length == 1) {
                            if (!(providerId >= min_value && providerId <= max_value)) {
                                // providerId is not in the range
                                isProviderFormalize = false;
                                errMsgProviderFormalize = "admin.provideraddrecord.msgFormalizeProviderIdFailure";
                            }
                        }
                    } catch (NumberFormatException e) {
                        //providerId is not a number
                        isProviderFormalize = false;
                        errMsgProviderFormalize = "admin.provideraddrecord.msgFormalizeProviderIdFailure";
                    }
                }
            }

            if (!IsPropertiesOn.isProviderFormalizeEnable() || isProviderFormalize) {

                DBPreparedHandler dbObj = new DBPreparedHandler();

                // check if the providers no need to be auto generated
                if (OscarProperties.getInstance().isProviderNoAuto()) {
                    p.setProviderNo(dbObj.getNewProviderNo());
                }

                if (providerDao.providerExists(p.getProviderNo())) {
                    isOk = false;
                    alreadyExists = true;
                } else {
                    providerDao.saveProvider(p);
                    isOk = true;
                }

                if (isOk && IsPropertiesOn.isMultisitesEnable()) {
                    String[] sites = request.getParameterValues("sites");
                    if (sites != null)
                        for (int i = 0; i < sites.length; i++) {
                            ProviderSite ps = new ProviderSite();
                            ps.setId(new ProviderSitePK(p.getProviderNo(), Integer.parseInt(sites[i])));
                            providerSiteDao.persist(ps);
                        }
                }

                if (isOk) {
                    String proId = p.getPractitionerNo();
                    String ip = request.getRemoteAddr();
                    LogAction.addLog(curUser_no, LogConst.ADD, "adminAddUser", proId, ip);

                    ProviderBillCenter billCenter = new ProviderBillCenter();
                    billCenter.addBillCenter(request.getParameter("provider_no"), request.getParameter("billcenter"));

        %>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecord.msgAdditionSuccess"/>
        </h1>
        <%
        } else {
        %>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecord.msgAdditionFailure"/></h1>
        <%
            if (alreadyExists) {
        %><h2><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.provideraddrecord.msgAlreadyExists"/></h2><%
            }

        }
    } else {
        if (!isProviderFormalize) {
    %>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="<%=errMsgProviderFormalize%>"/></h1>
        Provider # range from : <%=min_value %> To : <%=max_value %>
        <%
                }
            }
        %>
    </center>
    </body>
</html>
