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
<security:oscarSec roleName="<%=roleName$%>" objectName="_billing" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_billing");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@page import="org.springframework.web.context.WebApplicationContext" %>
<%@page import="org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@page import="ca.openosp.openo.demographic.data.*,ca.openosp.openo.utility.SpringUtils" %>
<%@page import="java.text.*, java.util.*, ca.openosp.openo.billing.ca.bc.data.*,ca.openosp.openo.billing.ca.bc.pageUtil.*,ca.openosp.*,ca.openosp.openo.entities.*" %>
<%@ page import="ca.openosp.openo.entities.WCB" %>
<%@ page import="ca.openosp.openo.billings.ca.bc.data.BillingmasterDAO" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%
    String demographicNo = request.getParameter("demographicNo");
    String wcbid = request.getParameter("wcbid");
    String billingcode = request.getParameter("billingcode");
%>

<div>
    <div>
        <label> WCB Forms available to attach.</label> <a
            onclick="popup(700,960,'viewformwcb.do?demographic_no=<%=demographicNo%>&formId=0&provNo=999998&parentAjaxId=forms&hideToBill=true','<%=demographicNo%>NEWWCB'); return false;"
            href="javascript:void(0);">New WCB Form</a> <br>
    </div>
    <table class="table table-striped table-condensed">
        <tr bgcolor="#CCCCFF">
            <td>&nbsp;</td>
            <td>&nbsp;</td>
            <th>Date of Injury</th>
            <th>Created On</th>
            <th>Diagnosis</th>
            <th title="Verify Form Not Needed Errors">Verify FNN</th>
            <th title="Verify Form Needed Errors">Verify FN</th>
        </tr>
        <%
            BillingmasterDAO billingmasterDAO = (BillingmasterDAO) SpringUtils.getBean(BillingmasterDAO.class);
            List<WCB> l = billingmasterDAO.getWCBForms(demographicNo);
            for (WCB wcb : l) {
                request.setAttribute("wcb", (Object) wcb);
        %>
        <tr>
            <td><input type="radio" name="WCBid" value="<%=wcb.getId()%>" <%=checked(wcbid, wcb.getId())%> /></td>
            <td><a href="javascript:void(0);"
                   onclick="checkifSet('<%=wcb.getW_icd9()%>','<%= wcb.getW_feeitem()%>','<%= wcb.getW_extrafeeitem()%>');">Populate</a>
            </td>
            <td align="middle">
                <a onclick="popup(700,960,'viewformwcb.do?demographic_no=<%=demographicNo%>&formId=<%=wcb.getId()%>&provNo=<%=session.getAttribute("user")%>&parentAjaxId=forms&billingcode=<%=billingcode%>&hideToBill=true','<%=demographicNo%>NEWWCB'); return false;"
                   href="javascript:void(0);"><fmt:formatDate pattern="yyyy-MM-dd" value="${wcb.w_doi}"/></a>

            </td>
            <td><fmt:formatDate pattern="yyyy-MM-dd HH:mm" value="${wcb.formCreated}"/></td>
            <td><%=wcb.getW_diagnosis()%>&nbsp;</td>


            <%
                if (wcb.verifyFormNotNeeded() != null && wcb.verifyFormNotNeeded().size() > 0) {
                    List<String> errs = wcb.verifyFormNotNeeded();
            %>
            <td title="header=[To bill WCB without a form the following is needed] body=[<%for (String s : errs) { %><fmt:setBundle basename="oscarResources"/><fmt:message key="<%=s%>"/><%  }%>]">
                    <%}else{%>
            <td>
                <%}%>

                <%=wcb.verifyFormNotNeeded().size()%>
            </td>


            <%
                if (wcb.verifyEverythingOnForm() != null && wcb.verifyEverythingOnForm().size() > 0) {
                    List<String> errs = wcb.verifyEverythingOnForm();
            %>
            <td title="header=[To bill WCB with a form the following is needed] body=[<%for (String s : errs) { %><fmt:setBundle basename="oscarResources"/><fmt:message key="<%=s%>"/><%  }%>]">
                    <%}else{%>
            <td>
                <%}%>
                <%=wcb.verifyEverythingOnForm().size()%>
            </td>


        </tr>
        <%}%>
    </table>
</div>


<%!
    String checked(String s, int i) {
        try {
            int b = Integer.parseInt(s);
            if (b == i) {
                return "checked";
            }
        } catch (Exception e) {
        }
        return "";
    }
%>
