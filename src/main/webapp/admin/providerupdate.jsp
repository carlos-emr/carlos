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
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r" reverse="<%=true%>">
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.userAdmin");%>
    <%authed = false; %>
</security:oscarSec>

<%
    if (!authed) {
        return;
    }
    if (!"POST".equalsIgnoreCase(request.getMethod())) {
        response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "POST required");
        return;
    }
%>
<%@ page
        import="java.sql.*, io.github.carlos_emr.carlos.login.*, java.util.*,io.github.carlos_emr.*,io.github.carlos_emr.carlos.db.*,io.github.carlos_emr.carlos.providers.data.ProviderBillCenter"
        errorPage="/errorpage.jsp" %>

<%@page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderArchive" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ProviderArchiveDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="org.springframework.beans.BeanUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderSite" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.ProviderSitePK" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ProviderSiteDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.db.DBPreparedHandler" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="io.github.carlos_emr.SxmlMisc" %>
<%@ page import="io.github.carlos_emr.MyDateFormat" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>
<%
    ProviderDao providerDao = (ProviderDao) SpringUtils.getBean(ProviderDao.class);
    ProviderSiteDao providerSiteDao = SpringUtils.getBean(ProviderSiteDao.class);
%>
<html>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providerupdate.title"/></title>
    </head>
    <link rel="stylesheet" href="<%= request.getContextPath() %>/web.css"/>

    <body bgproperties="fixed" topmargin="0" leftmargin="0" rightmargin="0">
    <center>
        <table border="0" cellspacing="0" cellpadding="0" width="100%">
            <tr bgcolor="#486ebd">
                <th><font face="Helvetica" color="#FFFFFF"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providerupdate.description"/></font></th>
            </tr>
        </table>

        <%
            ProviderBillCenter billCenter = new ProviderBillCenter();
            billCenter.updateBillCenter(request.getParameter("provider_no"), request.getParameter("billcenter"));


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
                ProviderArchiveDao providerArchiveDao = (ProviderArchiveDao) SpringUtils.getBean(ProviderArchiveDao.class);
                Provider provider = providerDao.getProvider(request.getParameter("provider_no"));
                ProviderArchive pa = new ProviderArchive();
                BeanUtils.copyProperties(provider, pa);
                pa.setId(null);
                providerArchiveDao.persist(pa);


                Provider p = providerDao.getProvider(request.getParameter("provider_no"));
                if (p != null) {
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
                    String supervisor = request.getParameter("supervisor");

                    if (supervisor == null || supervisor.equalsIgnoreCase("null") || supervisor.equals("")) {
                        p.setSupervisor(null);
                    } else {
                        p.setSupervisor(supervisor);
                    }

                    providerDao.updateProvider(p);


                    UserPropertyDAO userPropertyDAO = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);

                    String officialFirstName = request.getParameter("officialFirstName");
                    String officialSecondName = request.getParameter("officialSecondName");
                    String officialLastName = request.getParameter("officialLastName");

                    userPropertyDAO.saveProp(provider.getProviderNo(), UserProperty.OFFICIAL_FIRST_NAME, officialFirstName);
                    userPropertyDAO.saveProp(provider.getProviderNo(), UserProperty.OFFICIAL_SECOND_NAME, officialSecondName);
                    userPropertyDAO.saveProp(provider.getProviderNo(), UserProperty.OFFICIAL_LAST_NAME, officialLastName);


                    if (IsPropertiesOn.isMultisitesEnable()) {
                        String[] sites = request.getParameterValues("sites");
                        DBPreparedHandler dbObj = new DBPreparedHandler();
                        String provider_no = request.getParameter("provider_no");
                        List<ProviderSite> pss = providerSiteDao.findByProviderNo(provider_no);
                        for (ProviderSite ps : pss) {
                            providerSiteDao.remove(ps.getId());
                        }
                        if (sites != null) {
                            for (int i = 0; i < sites.length; i++) {
                                ProviderSite ps = new ProviderSite();
                                ps.setId(new ProviderSitePK(provider_no, Integer.parseInt(sites[i])));
                                providerSiteDao.persist(ps);
                            }
                        }
                    }
        %>
        <p>
        <h2><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providerupdate.msgUpdateSuccess"/>
            <a href="providerupdateprovider.jsp?keyword=<%=Encode.forUriComponent(request.getParameter("provider_no"))%>"><%= Encode.forHtml(request.getParameter("provider_no")) %>
            </a>
        </h2>
        <%
        } else {
        %>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.providerupdate.msgUpdateFailure"/><%= request.getParameter("provider_no") %>.</h1>
        <%
            }
        } else {
            if (!isProviderFormalize) {
                //output ProviderFormalize error message
        %>
        <h1><fmt:setBundle basename="oscarResources"/><fmt:message key="<%=errMsgProviderFormalize%>"/></h1>
        Provider # range from : <%=min_value %> To : <%=max_value %>
        <%
                }
            }
        %>
        <p></p>

    </center>
    </body>
</html>
