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
<security:oscarSec roleName="<%=roleName$%>" objectName="_demographic" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_demographic");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%

    //int demographic_no = Integer.parseInt(request.getParameter("demographic_no"));
    String demographic_no = request.getParameter("demographic_no");
    String creatorDemo = request.getParameter("demo");

    if (creatorDemo == null) {
        creatorDemo = request.getParameter("remarks");
    }
    if (creatorDemo == null) {
        creatorDemo = (String) request.getAttribute("demo");
    }

%>

<%@page import="io.github.carlos_emr.carlos.demographic.data.*,java.util.*" %>
<%@page import="io.github.carlos_emr.CarlosProperties" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.CtlRelationshipsDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.CtlRelationships" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicData" %>
<%@ page import="io.github.carlos_emr.carlos.demographic.data.DemographicRelationship" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Demographic" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    CtlRelationshipsDao ctlRelationshipsDao = SpringUtils.getBean(CtlRelationshipsDao.class);
%>
<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Strict//EN" "http://www.w3.org/TR/xhtml1/DTD/xhtml1-strict.dtd">
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:message key="demographic.demographiceditdemographic.msgAddRelation"/></title>
        <!--I18n-->
        <link rel="stylesheet" type="text/css"
              href="<%= request.getContextPath() %>/share/css/OscarStandardLayout.css"/>


        <script type="text/javascript">

            //if (document.all || document.layers)  window.resizeTo(790,580);
            function newWindow(file, window) {
                msgWindow = open(file, window, 'scrollbars=yes,width=760,height=520,screenX=0,screenY=0,top=0,left=10');
                if (msgWindow.opener == null) msgWindow.opener = self;
            }

        </script>


        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
    </head>

    <body class="BodyStyle">

    <table class="MainTable" id="scrollNumber1">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:message key="demographic.demographiceditdemographic.msgAddRelation"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><oscar:nameage demographicNo="<%=creatorDemo%>"/></td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a
                                href="javascript:popupStart(300,400,'<%=request.getContextPath()%>/encounter/ViewAbout')"><fmt:message key="global.about"/></a> | <a
                                href="javascript:popupStart(300,400,'<%=request.getContextPath()%>/encounter/ViewLicense')"><fmt:message key="global.license"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top">&nbsp;
                <%if (IsPropertiesOn.isCaisiEnable()) { %>

                <a href="<%=request.getContextPath()%>/PMmodule/ClientManager?id=<carlos:encode value='<%= creatorDemo %>' context="uriComponent"/>"><fmt:message key="demographic.addAlternateContact.backToPmm"/></a>

                <%} %>

            </td>
            <td valign="top" class="MainTableRightColumn">

                <form id="ADDAPPT" method="post"
                      action="<%= request.getContextPath() %>/demographic/DemographicSearch">
                    <div><fmt:message key="demographic.addAlternateContact.name"/> <input type="text" name="keyword" size="25" value=""/>

                        <input type="submit" name="Submit" value="<fmt:message key='Search'/>"/> <input
                                type="hidden" name="orderby" value="last_name"/>

                        <%
                            String searchMode = request.getParameter("search_mode");
                            if (searchMode == null || searchMode.isEmpty()) {
                                searchMode = CarlosProperties.getInstance().getProperty("default_search_mode", "search_name");
                            }
                        %>
                        <input
                                type="hidden" name="search_mode" value="<carlos:encode value='<%= searchMode %>' context="htmlAttribute"/>"/> <input
                                type="hidden" name="originalpage"
                                value="<%= request.getContextPath() %>/demographic/AddRelation"/> <input
                                type="hidden" name="limit1" value="0"/> <input type="hidden"
                                                                               name="limit2" value="5"/> <input
                                type="hidden" name="displaymode"
                                value="Search "/> <input type="hidden" name="appointment_date"
                                                         value="2002-10-01"/> <input type="hidden" name="status"
                                                                                     value="t"/>
                        <input type="hidden" name="start_time" value="10:45"
                               onchange="checkTimeTypeIn(this)"/> <input type="hidden" name="type"
                                                                         value=""/> <input type="hidden" name="duration"
                                                                                           value="15"/> <input
                                type="hidden" name="end_time" value="10:59"
                                onchange="checkTimeTypeIn(this)"/> <input type="hidden"
                                                                          name="demographic_no" value=""/> <input
                                type="hidden"
                                name="location" tabindex="4" value=""/> <input type="hidden"
                                                                               name="resources" tabindex="5" value=""/>
                        <input type="hidden"
                               name="user_id" value="oscardoc, doctor"/> <input type="hidden"
                                                                                name="dboperation"
                                                                                value="search_demorecord"/> <input
                                type="hidden"
                                name="createdatetime" value="2002-10-1 17:53:50"/> <input
                                type="hidden" name="provider_no" value="115"/> <input type="hidden"
                                                                                      name="creator"
                                                                                      value="oscardoc, doctor"/> <input
                                type="hidden"
                                name="remarks" value="<carlos:encode value='<%= creatorDemo %>' context="htmlAttribute"/>"/></div>
                </form>

                <%
                    String demoNo = request.getParameter("demographic_no");
                    String name = request.getParameter("name");
                    String origDemo = request.getParameter("remarks");
                    if (demoNo != null) {
                %> <form action="${pageContext.request.contextPath}/demographic/AddRelation" method="post">
                <input type="hidden" name="origDemo" value="<carlos:encode value='<%= origDemo %>' context="htmlAttribute"/>"/>
                <input type="hidden" name="linkingDemo" value="<carlos:encode value='<%= demoNo %>' context="htmlAttribute"/>"/>


                <div class="prevention">
			<fieldset><legend><fmt:message key="demographic.addAlternateContact.relation"/></legend>
				<label for="name"><fmt:message key="demographic.addAlternateContact.name"/>:</label>
					<span id="name"><carlos:encode value='<%= name %>' context="html"/></>
                            <br/>

			<label for="relation"><fmt:message key="demographic.addAlternateContact.relationship"/>:</label>
				<select name="relation" id="relation">

                                <%
                                    List<CtlRelationships> results = ctlRelationshipsDao.findAllActive();
                                    for (CtlRelationships t : results) {
                                %>
						<option value="<%=t.getValue() %>"><carlos:encode value='<%= t.getLabel() %>' context="html"/></option>
                                <%
                                    }
                                %>
			</select>
				<input type="checkbox" name="sdm" id="sdm" value="yes">
				<label for="sdm"><fmt:message key="demographic.addAlternateContact.substituteDecisionMaker"/></label>
				<input type="checkbox" name="emergContact" id="emergContact" value="yes" />
				<label for="emergContact"><fmt:message key="demographic.addAlternateContact.emergencyContact"/></label> <br />

                            <label for="notes"><fmt:message key="demographic.addAlternateContact.notes"/>:</label><br>
			<textarea cols="20" rows="3" name="notes" id="notes"></textarea>
				<input type="submit" value="<fmt:message key='demographic.addAlternateContact.addRelationship'/>" />
			</fieldset>
                </div>
            </form> <%}%>

                <div class="tablelisting">
                    <table>
                        <% DemographicRelationship demoRelation = new DemographicRelationship();
                  ArrayList<Map<String, String>> list = demoRelation.getDemographicRelationships(creatorDemo);
                  if (! list.isEmpty()){
                        %>
                        <tr>
                            <th>Name</th>
                            <th>Relation</th>
                            <th>SDM</th>
                            <th>Notes</th>
                            <th>&nbsp;</th>
                        </tr>

                        <% }
                            for (int i = 0; i < list.size(); i++) {
                     Map<String, String> h = list.get(i);
                     String relatedDemo = h.get("demographic_no");
                                DemographicData dd = new DemographicData();
                                Demographic demographic = dd.getDemographic(LoggedInInfo.getLoggedInInfoFromSession(request), relatedDemo); %>
                        <tr>
				<td><carlos:encode value='<%= demographic.getLastName() +", "+demographic.getFirstName() %>' context="html"/></td>
				<td><carlos:encode value='<%= h.get("relation") %>' context="html"/></td>
				<td><carlos:encode value='<%= returnYesIf1(h.get("sub_decision_maker")) %>' context="html"/></td>
				<td><carlos:encode value='<%= h.get("notes") %>' context="html"/></td>
				<td>
					<form method="post" action="<%=request.getContextPath()%>/demographic/DeleteRelation" style="display:inline;">
						<input type="hidden" name="id" value="<%=h.get("id")%>"/>
						<input type="hidden" name="origDemo" value="<carlos:encode value='<%= creatorDemo %>' context="htmlAttribute"/>"/>
						<a href="javascript:void(0);" onclick="if(confirm('<fmt:message key='demographic.addAlternateContact.deleteConfirm'/>')){this.closest('form').submit();}"><fmt:message key='global.btnDelete'/></a>
					</form>
				</td>
                        </tr>
                        <%}%>
                    </table>
                </div>


                <oscar:oscarPropertiesCheck property="TORONTO_RFQ" value="yes">
                    <br/>
                    <form action="<%=request.getContextPath() %>/demographic/AddRelation">
                        <input type="hidden" name="origDemo" value="<carlos:encode value='<%= creatorDemo %>' context="htmlAttribute"/>"/>
                        <input type="submit" name="pmmClient" value="<fmt:message key='demographic.addAlternateContact.finished'/>"/>
                    </form>
                </oscar:oscarPropertiesCheck></td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>
            <td class="MainTableBottomRowRightColumn" valign="top">&nbsp;</td>
        </tr>
    </table>
    </body>
</html>
<%!
    String returnYesIf1(Object o) {
        String ret = "";
        if (o instanceof String) {
            String s = (String) o;
            if ("1".equals(s)) {
                ret = "yes";
            }
        }
        return ret;
    }

%>
