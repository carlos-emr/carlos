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
<%@page import="java.util.*,ca.openosp.openo.eform.*" %>
<%@page import="ca.openosp.openo.web.eform.EfmPatientFormList" %>
<%@page import="ca.openosp.openo.utility.SpringUtils" %>
<%@page import="ca.openosp.openo.PMmodule.dao.ProviderDao" %>
<%@ page import="ca.openosp.openo.eform.EFormUtil" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");
    String country = request.getLocale().getCountry();

    ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    String orderByRequest = request.getParameter("orderby");
    String orderBy = "";
    if (orderByRequest == null) orderBy = EFormUtil.DATE;
    else orderBy = orderByRequest;
%>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<html>

    <head>
        <script type="text/javascript" src="<%=request.getContextPath()%>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.frmIndependent"/>s</title>


        <script type="text/javascript">
            function openEform(varpage, windowname) {
                var page = "" + varpage;

                windowprops = "height=700,width=800,location=no,"
                    + "scrollbars=yes,menubars=no,status=yes,toolbars=no,resizable=yes,top=10,left=200";

                var popup = window.open(page, windowname, windowprops);

                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }

            function checkSelectBox() {
                var selectVal = document.forms[0].group_view.value;
                if (selectVal == "default") {
                    return false;
                }
            }
        </script>
    </head>

    <body>

    <%@ include file="efmTopNav.jspf" %>

    <div class="well">
        <h3 style="display:inline"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.frmIndependent"/>s</h3> <i
            class="icon-question-sign"></i>


        <p>View: <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.independent.btnCurrent"/> | <a
                href="<%=request.getContextPath()%>/eform/efmmanageindependentdeleted.jsp"
                class="contentLink"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.independent.btnDeleted"/></a></p>


        <table class="table table-condensed table-striped">
            <thead>
            <tr>
                <th>
                    <a href="<%=request.getContextPath()%>/eform/efmmanageindependent.jsp?orderby=<%=EFormUtil.NAME%>"
                       class="contentLink">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.btnFormName"/>
                    </a>
                </th>
                <th>
                    <a href="<%=request.getContextPath()%>/eform/efmmanageindependent.jsp?orderby=<%=EFormUtil.SUBJECT%>"
                       class="contentLink">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.btnSubject"/>
                    </a>
                </th>
                <th>
                    <a href="<%=request.getContextPath()%>/eform/efmmanageindependent.jsp?orderby=<%=EFormUtil.PROVIDER%>"
                       class="contentLink">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.btnFormProvider"/>
                    </a>
                </th>
                <th>
                    <a href="<%=request.getContextPath()%>/eform/efmmanageindependent.jsp" class="contentLink">
                        <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.formDate"/>
                    </a>
                </th>
                <th>
                    <fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.msgAction"/>
                </th>
            </tr>
            </thead>


            <tbody>
            <%
                ArrayList<HashMap<String, ? extends Object>> eForms;
                eForms = EFormUtil.listPatientIndependentEForms(orderBy, EFormUtil.CURRENT);

                for (int i = 0; i < eForms.size(); i++) {
                    HashMap<String, ? extends Object> curform = eForms.get(i);
            %>
            <tr>
                <td><a href="#"
                       ONCLICK="openEform('<%= request.getContextPath() %>/eform/efmshowform_data.jsp?fdid=<%=curform.get("fdid")%>', '<%="FormP" + i%>'); return false;"
                       TITLE="<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.msgViewFrm"/>"
                       onmouseover="window.status='<fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.msgViewFrm"/>'; return true"><%=curform.get("formName")%>
                </a></td>
                <td><%=curform.get("formSubject")%>
                </td>
                <td align='center'><%=providerDao.getProviderNameLastFirst((String) curform.get("providerNo"))%>
                </td>
                <td align='center'><%=curform.get("formDate")%>
                </td>
                <td align='center'><a
                        href="<%= request.getContextPath() %>/eform/removeEForm.do?callpage=independent&fdid=<%=curform.get("fdid")%>"
                        onClick="javascript: return confirm('Are you sure you want to delete this eform?');"
                        class="contentLink"><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.uploadimages.btnDelete"/></a></td>
            </tr>
            <%
                }
                if (eForms.size() <= 0) {
            %>
            <tr>
                <td align='center' colspan='5'><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.msgNoData"/></td>
            </tr>
            <%
                }
            %>

            </tbody>
        </table>

        <%@ include file="efmFooter.jspf" %>
    </div>

    </body>
</html>