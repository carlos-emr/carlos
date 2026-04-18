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
<%@page import="io.github.carlos_emr.carlos.util.*" %>
<%@page import="org.springframework.beans.BeanUtils" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="org.springframework.web.context.WebApplicationContext" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ page import="java.util.*, java.sql.*, java.net.*, io.github.carlos_emr.*, io.github.carlos_emr.carlos.db.*" errorPage="/WEB-INF/jsp/error/errorpage.jsp" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>

<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Site" %>

<%@page import="io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Appointment" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.AppointmentArchive" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.AppointmentStatusDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.AppointmentStatus" %>
<%@page import="io.github.carlos_emr.carlos.managers.LookupListManager" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.LookupList" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.LookupListItem" %>

<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>
<%@ page import="io.github.carlos_emr.carlos.managers.AppointmentManager" %>
<%@ page import="io.github.carlos_emr.carlos.managers.DemographicManager" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>


<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/special_tag.tld" prefix="special" %>

<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>


<%!
    private List<Site> sites = new java.util.ArrayList<Site>();
    private HashMap<String, String[]> siteBgColor = new HashMap<String, String[]>();
%>

<%
    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    OscarAppointmentDao appointmentDao = (OscarAppointmentDao) SpringUtils.getBean(OscarAppointmentDao.class);
    ProviderDataDao providerDao = SpringUtils.getBean(ProviderDataDao.class);
    AppointmentStatusDao appointmentStatusDao = SpringUtils.getBean(AppointmentStatusDao.class);
    LookupListManager lookupListManager = SpringUtils.getBean(LookupListManager.class);
    LookupList reasonCodes = lookupListManager.findLookupListByName(loggedInInfo, "reasonCode");
    Map<Integer, LookupListItem> reasonCodesMap = new HashMap<Integer, LookupListItem>();
    for (LookupListItem lli : reasonCodes.getItems()) {
        reasonCodesMap.put(lli.getId(), lli);
    }


    if (IsPropertiesOn.isMultisitesEnable()) {
        SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application)
                .getBean(SiteDao.class);
        sites = siteDao.getAllActiveSites();
        //get all sites bgColors
        for (Site st : sites) {
            siteBgColor.put(st.getName(), new String[]{st.getBgColor(), st.getShortName()});
        }
    }

    String curProvider_no = (String) session.getAttribute("user");
    String demographic_no = request.getParameter("demographic_no");
    String strLimit1 = "0";
    String strLimit2 = "50";
    if (request.getParameter("limit1") != null)
        strLimit1 = request.getParameter("limit1");
    if (request.getParameter("limit2") != null)
        strLimit2 = request.getParameter("limit2");

    DemographicManager demographicManager = SpringUtils.getBean(DemographicManager.class);
    Demographic patientDemo = (demographic_no != null && !demographic_no.isEmpty()) ? demographicManager.getDemographic(loggedInInfo, demographic_no) : null;
    String demolastname = patientDemo != null ? patientDemo.getLastName() : "";
    String demofirstname = patientDemo != null ? patientDemo.getFirstName() : "";
    String deepColor = "#CCCCFF", weakColor = "#EEEEFF";
    String showDeleted = request.getParameter("deleted");
    String orderby = "";
    if (request.getParameter("orderby") != null)
        orderby = request.getParameter("orderby");

    Map<String, ProviderData> providerMap = new HashMap<String, ProviderData>();
%>


<html>

    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
        <script src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>
        <script>
            jQuery.noConflict();
        </script>
        <c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
        <script>
            var ctx = '<%=request.getContextPath()%>';
        </script>

        <oscar:customInterface section="appthistory"/>

        <title><fmt:message key="demographic.demographicappthistory.title"/></title>
        <link rel="stylesheet" type="text/css" href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css">
        <script type="text/javascript">

            function popupPageNew(vheight, vwidth, varpage) {
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes";
                var popup = window.open(page, "demographicprofile", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function selectAllCheckboxes() {
                jQuery("input[name='sel']").each(function () {
                    jQuery(this).attr('checked', true);
                });
            }

            function deselectAllCheckboxes() {
                jQuery("input[name='sel']").each(function () {
                    jQuery(this).attr('checked', false);
                });
            }

            function toggleShowDeleted(value) {
                if (value) {
                    //show deleted
                    //appt_history_w_deleted
                    <c:set var="__enc_1"><carlos:encode value='<%= demographic_no %>' context="uriComponent"/></c:set>
                    <c:set var="__enc_2"><carlos:encode value='<%= orderby %>' context="uriComponent"/></c:set>
                    location.href = '<%=request.getContextPath()%>/demographic/DemographicApptHistory?demographic_no=<carlos:encode value='${__enc_1}' context="javaScript"/>&orderby=<carlos:encode value='${__enc_2}' context="javaScript"/>&dboperation=appt_history_w_deleted&limit1=<carlos:encode value='<%= strLimit1 %>' context="javaScript"/>&limit2=<carlos:encode value='<%= strLimit2 %>' context="javaScript"/>&deleted=true';
                } else {
                    //don't show deleted
                    <c:set var="__enc_3"><carlos:encode value='<%= demographic_no %>' context="uriComponent"/></c:set>
                    <c:set var="__enc_4"><carlos:encode value='<%= orderby %>' context="uriComponent"/></c:set>
                    location.hr                    
ef = '<%=request.getContextPath()%>/demographic/DemographicApptHistory?demographic_no=<carlos:encode value='${__enc_3}' context="javaScript"/>&orderby=<carlos:encode value='${__enc_4}' context="javaScript"/>&dboperation=appt_history&limit1=<carlos:encode value='<%= strLimit1 %>' context="javaScript"/>&limit2=<carlos:encode value='<%= strLimit2 %>' context="javaScript"/>';
                }
            }

            jQuery(document).ready(function () {
                <%if(showDeleted != null && showDeleted.equals("true")) { %>
                jQuery("#showDeleted").attr('checked', true);
                <% } else {%>
                jQuery("#showDeleted").attr('checked', false);
                <%} %>
            });


            function filterByProvider(s) {
                var providerNo = s.options[s.selectedIndex].value;
                jQuery("#apptHistoryTbl tbody tr").not(":first").each(function () {
                    if (!providerNo == '' && jQuery(this).attr('provider_no') != providerNo) {
                        jQuery(this).hide();
                    } else {
                        jQuery(this).show();
                    }
                });
            }
        </script>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>

    <body class="BodyStyle" demographic.demographicappthistory.msgTitle=vlink="#0000FF">

    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="demographic.demographicappthistory.msgHistory"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><fmt:message key="demographic.demographicappthistory.msgResults"/>: <carlos:encode value='<%= demolastname %>' context="html"/>
                            ,<carlos:encode value='<%= demofirstname %>' context="html"/>(<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no")) %>' context="html"/>)
                        </td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a
                                href="javascript:popupStart(300,400,'About.jsp')">
                            <fmt:message key="global.about"/></a> | <a
                                href="javascript:popupStart(300,400,'License.jsp')">
                            <fmt:message key="global.license"/></a>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top"><a
                    href="<%=request.getContextPath()%>/demographic/DemographicEdit?demographic_no=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no")) %>' context="uriComponent"/>&apptProvider=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull((String) session.getAttribute("user")) %>' context="uriComponent"/>"
                    onMouseOver="self.status=document.referrer;return true">
                <fmt:message key="global.btnBack"/></a>
                <br/>
                <input type="checkbox" name="showDeleted" id="showDeleted" onChange="toggleShowDeleted(this.checked);"/><fmt:message key="demographic.demographicappthistory.msgShowDeleted"/>
                <br/>
            </td>
            <td class="MainTableRightColumn">
                <table width="95%" border="0" bgcolor="#ffffff" id="apptHistoryTbl">
                    <tr bgcolor="<%=deepColor%>">
                        <TH width="10%"><b><fmt:message key="demographic.demographicappthistory.msgApptDate"/></b></TH>
                        <TH width="10%"><b><fmt:message key="demographic.demographicappthistory.msgFrom"/></b></TH>
                        <TH width="10%"><b><fmt:message key="demographic.demographicappthistory.msgTo"/></b></TH>
                        <TH width="10%"><b><fmt:message key="demographic.demographicappthistory.msgStatus"/></b></TH>
                        <TH width="10%"><b><fmt:message key="demographic.demographicappthistory.msgType"/></b></TH>
                        <TH width="15%"><b><fmt:message key="demographic.demographicappthistory.msgReason"/></b></TH>
                        <TH width="15%"><b><fmt:message key="demographic.demographicappthistory.msgProvider"/></b></TH>
                        <TH><b><fmt:message key="demographic.demographicappthistory.msgComments"/></b></TH>

                        <% if (IsPropertiesOn.isMultisitesEnable()) { %>
                        <TH width="5%">Location</TH>
                        <% } %>
                    </tr>
                    <%
                        int iRSOffSet = 0;
                        int iPageSize = 10;
                        int iRow = 0;
                        if (request.getParameter("limit1") != null) {
                            try { iRSOffSet = Integer.parseInt(request.getParameter("limit1")); }
                            catch (NumberFormatException ignored) { /* keep default */ }
                        }
                        if (request.getParameter("limit2") != null) {
                            try { iPageSize = Integer.parseInt(request.getParameter("limit2")); }
                            catch (NumberFormatException ignored) { /* keep default */ }
                        }
                        List<Object> appointmentList;
                        AppointmentManager appointmentManager = SpringUtils.getBean(AppointmentManager.class);

                        if (!"true".equals(showDeleted)) {
                            appointmentList = new java.util.ArrayList<Object>();
                            appointmentList.addAll(appointmentManager.getAppointmentHistoryWithoutDeleted(loggedInInfo, new Integer(demographic_no), iRSOffSet, iPageSize));
                        } else {
                            appointmentList = appointmentManager.getAppointmentHistoryWithDeleted(loggedInInfo, new Integer(demographic_no), iRSOffSet, iPageSize);
                        }
                        boolean bodd = false;
                        int nItems = 0;


                        if (appointmentList == null) {
                            out.println("failed!!!");
                        } else {

                            for (Object obj : appointmentList) {
                                boolean deleted = false;

                                Appointment appointment = null;
                                AppointmentArchive appointmentArchive = null;
                                if (obj instanceof Appointment) {
                                    appointment = (Appointment) obj;
                                }
                                if (obj instanceof AppointmentArchive) {
                                    appointmentArchive = (AppointmentArchive) obj;
                                    appointment = new Appointment();

                                    BeanUtils.copyProperties(appointmentArchive, appointment);
                                    appointment.setId(appointmentArchive.getAppointmentNo());

                                    deleted = true;
                                }
                                iRow++;

                                if (iRow > iPageSize) break;
                                bodd = bodd ? false : true; //for the color of rows
                                nItems++; //to calculate if it is the end of records

                                ProviderData provider = providerDao.findByProviderNo(appointment.getProviderNo());
                                AppointmentStatus as = appointmentStatusDao.findByStatus(appointment.getStatus());

                                if (provider != null) {
                                    providerMap.put(provider.getId(), provider);
                                }

                                String reasonCodeName = null;
                                if (appointment.getReasonCode() != null) {
                                    LookupListItem lookupListItem = reasonCodesMap.get(appointment.getReasonCode());
                                    if (lookupListItem != null) {
                                        reasonCodeName = lookupListItem.getLabel();
                                    }
                                    if (reasonCodeName != null) {
                                        reasonCodeName = reasonCodeName.trim();
                                    }
                                }

                    %>
                    <tr <%=(deleted) ? "style='text-decoration: line-through' " : "" %>
                            bgcolor="<%=bodd?weakColor:"white"%>" appt_no="<carlos:encode value='<%= appointment.getId().toString() %>' context="htmlAttribute"/>"
                            demographic_no="<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(demographic_no) %>' context="htmlAttribute"/>" provider_no="<e:forHtmlAttribute value='<%= provider!=null?provider.getId():"" %>' />">
                        <c:set var="__enc_5"><carlos:encode value='<%= demographic_no %>' context="uriComponent"/></c:set>
                        <c:set var="__enc_6"><carlos:encode value='<%= appointment.getId().toString() %>' context="uriComponent"/></c:set>
                        <td align=                                              
"center"><a href=#
                                              onClick="popupPageNew(360,680, '<%= request.getContextPath() %>/appointment/appointmentcontrol?demographic_no=<carlos:encode value='${__enc_5}' context="javaScriptAttribute"/>&appointment_no=<carlos:encode value='${__enc_6}' context="javaScriptAttribute"/>&displaymode=edit&dboperation=search');return false;"><e:forHtmlContent value='<%= appointment.getAppointmentDate() != null ? appointment.getAppointmentDate().toString() : "" %>' />
                        </a></td>
                        <td align="center"><e:forHtmlContent value='<%= appointment.getStartTime() != null ? appointment.getStartTime().toString() : "" %>' />
                        </td>
                        <td align="center"><e:forHtmlContent value='<%= appointment.getEndTime() != null ? appointment.getEndTime().toString() : "" %>' />
                        </td>
                        <td align="center">
                            <%if (as != null && as.getDescription() != null) {%>
                            <carlos:encode value='<%= as.getDescription() %>' context="html"/>
                            <% } %>
                        </td>
                        <td><carlos:encode value='<%= appointment.getType() %>' context="html"/>
                        </td>
                        <td><%=(reasonCodeName != null && !reasonCodeName.isEmpty()) ? Encode.forHtml(reasonCodeName) : ""%><%=(appointment.getReason() != null && !appointment.getReason().isEmpty()) ? ((reasonCodeName != null && !reasonCodeName.isEmpty()) ? " - " : "") + Encode.forHtml(appointment.getReason()) : ""%>
                        </td>
                        <% if (provider != null) {%>
                        <td><e:forHtmlContent value='<%= (provider.getLastName() == null ? "N/A" : provider.getLastName()) + "," + (provider.getFirstName() == null ? "N/A" : provider.getFirstName()) %>' />
                        </td>
                        <%} else { %>
                        <td>N/A</td>
                        <%}%>


                        <%
                            String remarks = appointment.getRemarks();
                            if (remarks == null)
                                remarks = "";

                            String comments = "";
                            boolean newline = false;

                            if (appointment.getStatus() != null) {
                                if (appointment.getStatus().startsWith("N")) {
                                    comments = "No Show";
                                } else if (appointment.getStatus().startsWith("C")) {
                                    comments = "Cancelled";
                                }
                            }

                            if (!remarks.isEmpty() && !comments.isEmpty()) {
                                newline = true;
                            }
                        %>
                        <td>&nbsp;<carlos:encode value='<%= remarks %>' context="html"/><% if (newline) {%><br/>&nbsp;<%}%><carlos:encode value='<%= comments %>' context="html"/>
                        </td>
                        <%
                            if (IsPropertiesOn.isMultisitesEnable()) {
                                String[] sbc = siteBgColor.get(appointment.getLocation());
                                String siteColor = sbc != null && sbc.length > 0 ? sbc[0] : "";
                                String siteLabel = sbc != null && sbc.length > 1 ? sbc[1] : io.github.carlos_emr.carlos.util.StringUtils.noNull(appointment.getLocation());
                        %>
                        <td style='background-color:<carlos:encode value='<%= siteColor %>' context="cssString"/>'><carlos:encode value='<%= siteLabel %>' context="html"/>
                        </td>
                        <%
                            }
                        %>
                    </tr>
                    <%
                            }
                        }
                    %>

                </table>
                <br>
                <%
                    int nPrevPage = 0, nNextPage = 0;
                    nNextPage = Integer.parseInt(strLimit2) + Integer.parseInt(strLimit1);
                    nPrevPage = Integer.parseInt(strLimit1) - Integer.parseInt(strLimit2);
                    if (nPrevPage >= 0) {
                %>
                <a href="DemographicApptHistory?demographic_no=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no")) %>' context="uriComponent"/>&dboperation=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("dboperation")) %>' context="uriComponent"/>&orderby=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("orderby")) %>' context="uriComponent"/>&limit1=<%=nPrevPage%>&limit2=<carlos:encode value='<%= strLimit2 %>' context="uriComponent"/>">
                    <fmt:message key="demographic.demographicappthistory.btnPrevPage"/></a>
                <%
                    }

                    if (nItems >= Integer.parseInt(strLimit2)) {
                %>
                <a href="DemographicApptHistory?demographic_no=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("demographic_no")) %>' context="uriComponent"/>&dboperation=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("dboperation")) %>' context="uriComponent"/>&orderby=<carlos:encode value='<%= io.github.carlos_emr.carlos.util.StringUtils.noNull(request.getParameter("orderby")) %>' context="uriComponent"/>&limit1=<%=nNextPage%>&limit2=<carlos:encode value='<%= strLimit2 %>' context="uriComponent"/>">
                    <fmt:message key="demographic.demographicappthistory.btnNextPage"/></a>
                <%
                    }
                %>
                <p>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn"></td>
            <td class="MainTableBottomRowRightColumn">
                Filter results on this page by provider:
                <select onChange="filterByProvider(this)">
                    <option value="">ALL</option>
                    <%
                        for (ProviderData prov : providerMap.values()) {
                    %>
                    <option value="<carlos:encode value='<%= prov.getId() %>' context="htmlAttribute"/>"><carlos:encode value='<%= prov.getLastName() + ", " + prov.getFirstName() %>' context="html"/>
                    </option>
                    <%
                        }
                    %>
                </select>
            </td>
        </tr>
    </table>
    </body>
</html>
