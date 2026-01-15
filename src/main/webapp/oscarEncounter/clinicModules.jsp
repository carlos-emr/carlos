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
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.nio.charset.StandardCharsets" %>
<%@page import="java.net.URLEncoder" %>
<%@ page import="ca.openosp.openo.encounter.pageUtil.EctSessionBean" %>
<%@ page import="ca.openosp.openo.util.UtilDateUtilities" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/rewrite-tag.tld" prefix="rewrite" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%
    long startTime = System.currentTimeMillis();
    EctSessionBean bean = null;
    if ((bean = (EctSessionBean) request.getSession().getAttribute("EctSessionBean")) == null) {
        response.sendError(response.SC_FORBIDDEN);
        //response.sendRedirect("error.jsp");
        return;
    }
    UtilDateUtilities dateConvert = new UtilDateUtilities();
    String pAge = Integer.toString(dateConvert.calcAge(bean.yearOfBirth, bean.monthOfBirth, bean.dateOfBirth));
    String winName = "";
%>

<h3>&nbsp;<fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.clinicalModules"/></h3>
<ul id="ModuleList">

    <li><oscar:oscarPropertiesCheck property="PREVENTION" value="yes">
        <%
            winName = "Prevention" + bean.demographicNo;
        %>
        <a class="links" onmouseover="this.className='linkhover'"
           onmouseout="this.className='links'"
           href="javascript:popupPage(700,960,'<%=winName%>','<%= request.getContextPath() %>/oscarPrevention/index.jsp?demographic_no=<%=bean.demographicNo%>')">
            <oscar:preventionWarnings demographicNo="<%=bean.demographicNo%>">prevention</oscar:preventionWarnings></a>
    </oscar:oscarPropertiesCheck></li>
    <%-- <li>
                        <%
                           winName = "Disease" + bean.demographicNo;
                        %>
                        <a class="links" onmouseover="this.className='linkhover'"  onmouseout="this.className='links'" href=# onClick="popupPage(580,900,'<%=winName%>','<%=request.getContextPath()%>/oscarResearch/dxresearch/setupDxResearch.do?demographicNo=<%=bean.demographicNo%>&providerNo=<%=bean.providerNo%>&quickList=');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.disease"/></a>
                    </li>
                    <li>
                        <%
                           winName = "AddTickler" + bean.demographicNo;
                        %>
                        <a class="links" onmouseover="this.className='linkhover'"  onmouseout="this.className='links'" href=# onClick="popupPage(580,800,'<%=winName%>','<%=request.getContextPath()%>/appointment/appointmentcontrol.jsp?keyword=<%=URLEncoder.encode(bean.patientLastName+","+bean.patientFirstName)%>&displaymode=<%=URLEncoder.encode("Search ", StandardCharsets.UTF_8)%>&search_mode=search_name&originalpage=<%=URLEncoder.encode(request.getContextPath(), StandardCharsets.UTF_8) + "/tickler/ticklerAdd.jsp")%>&orderby=last_name&appointment_date=2000-01-01&limit1=0&limit2=5&status=t&start_time=10:45&end_time=10:59&duration=15&dboperation=search_demorecord&type=&demographic_no=<%=bean.demographicNo%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.addTickler"/></a>                        
                        <%
                           winName = "ViewTickler" + bean.demographicNo;
                        %>                        
                        <a class="links" onmouseover="this.className='linkhover'"  onmouseout="this.className='links'" href="#" onClick="popupPage(700,1000,'<%=winName%>','<%=request.getContextPath()%>/tickler/ticklerDemoMain.jsp?demoview=<%=bean.demographicNo%>');return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.viewTickler"/></a><br>
                        
                    </li> --%>
    <li>
        <%
            winName = "Calculator" + bean.demographicNo;
        %> <a class="links"
              onmouseover="this.className='linkhover'"
              onmouseout="this.className='links'"
              href="javascript: function myFunction() {return false; }"
              onClick="popupPage(150,200,'<%=winName%>','calculators.jsp?sex=<%=bean.patientSex%>&age=<%=pAge%>'); return false;"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarEncounter.Index.calculators"/></a></li>

</ul>
<input type="hidden" id="modCount" value="7">
