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

<%--
/**
 * PDF Attachment File Listing Interface
 *
 * This JSP page provides an interface for viewing and downloading individual PDF files
 * from message attachments. It displays a list of PDF files contained within an
 * attachment and allows users to download specific files by clicking download buttons.
 *
 * Main Features:
 * - Displays list of PDF files extracted from attachment data
 * - Individual download buttons for each PDF file
 * - Integration with PDF attachment processing system
 * - Simple table-based interface for file selection
 *
 * Security Requirements:
 * - Requires "_msg" object read permissions via security taglib
 * - User session validation and role-based access control
 * - Validates msgSessionBean presence and validity
 *
 * Request Attributes:
 * - PDFAttachment: XML/data string containing PDF file information
 *
 * Session Dependencies:
 * - msgSessionBean: Required for attachment context
 * - PDFAttachment: Stored in session for form processing
 *
 * Processing:
 * 1. Extracts PDF file titles from attachment data using Doc2PDF utility
 * 2. Displays files in table format with download buttons
 * 3. Submits selected file ID to ViewPDFFile action for download
 *
 * Form Integration:
 * - Posts to ViewPDFFile action with file_id parameter
 * - Passes attachment data as hidden form field
 *
 * @since 2003
 */
--%>

<%@ page import="io.github.carlos_emr.carlos.messenger.docxfer.send.*" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.messenger.docxfer.util.*" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.util.*" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="java.util.*" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="org.w3c.dom.*" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.util.Doc2PDF" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="/WEB-INF/carlos.tld" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<fmt:setBundle basename="oscarResources"/>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<c:if test="${empty msgSessionBean}">
    <c:redirect url="/index"/>
</c:if>
<c:if test="${not empty msgSessionBean}">
    <c:set var="bean" value="${msgSessionBean}" scope="session"/>
    <c:if test="${bean.valid == 'false'}">
        <c:redirect url="/index"/>
    </c:if>
</c:if>

<!DOCTYPE html>
<html lang="${pageContext.request.locale.language}">
<head>
    <meta charset="UTF-8">
    <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>

    <%
        // Extract and store PDF attachment data for processing
        String pdfAttch = (String) request.getAttribute("PDFAttachment");
        session.setAttribute("PDFAttachment", pdfAttch);
    %>

    <title><fmt:message key="messenger.ViewPDFAttachment.title"/></title>
</head>
<fmt:message key="global.btnClose" var="closeLabel"/>
<body>
<div class="container-fluid px-2 py-2">

    <%-- Alert banner — hidden by default, shown via JS on error --%>
    <div id="jsAlertBanner"
         class="alert alert-danger alert-dismissible"
         style="display:none"
         role="alert">
        <span id="jsAlertText"></span>
        <button type="button"
                class="btn-close"
                onclick="this.closest('.alert').style.display='none'"
                aria-label="${closeLabel}"></button>
    </div>

    <%-- Page header bar --%>
    <div class="page-header-bar d-flex align-items-center justify-content-between py-2 mb-2 border-bottom"
         id="header">
        <div class="d-flex align-items-center gap-2">
            <i class="fa-regular fa-paperclip" aria-hidden="true"></i>
            <span class="fw-semibold"><fmt:message key="messenger.CreateMessage.msgMessenger"/></span>
        </div>
        <div class="d-flex align-items-center gap-3">
            <a href="javascript:popupStart(300,400,'About.jsp')" class="small text-decoration-none">
                <fmt:message key="global.about"/>
            </a>
            <a href="javascript:popupStart(300,400,'License.jsp')" class="small text-decoration-none">
                <fmt:message key="global.license"/>
            </a>
        </div>
    </div>

    <div class="bg-light border rounded p-2">

        <%-- Close button --%>
        <div class="mb-2">
            <button type="button"
                    class="btn btn-outline-secondary btn-sm"
                    onclick="javascript:top.window.close()">
                <i class="fa-regular fa-circle-xmark" aria-hidden="true"></i>
                <fmt:message key="messenger.ViewPDFAttachment.btnCloseAttachment"/>
            </button>
        </div>


        <form action="${pageContext.request.contextPath}/messenger/ViewPDFFile" method="post">
            <table class="table table-sm table-bordered">

                            <% 
                                // Extract PDF file titles from attachment data
                                Vector attVector = Doc2PDF.getXMLTagValue(pdfAttch, "TITLE" ); 
                            %>
                            <% for ( int i = 0 ; i < attVector.size(); i++) { %>
                    <tr>
                        <td><%= SafeEncode.forHtml((String) attVector.get(i)) %>
                        </td>
                        <td>
                          <button type="submit"
                                  class="btn btn-success"
                                  onclick="document.forms[0].file_id.value = <%=i%>"
                                  title="<fmt:message key='messenger.ViewPDFAttachment.btnDownload'/>"
                                  aria-label="<fmt:message key='messenger.ViewPDFAttachment.btnDownload'/>">
                              <i class="fa fa-download" aria-hidden="true"></i>
                              <span class="visually-hidden"><fmt:message key="messenger.ViewPDFAttachment.btnDownload"/></span>
                           </button></td>
                    </tr>
                            <% }  %>
                    </table>
                        <input type="hidden" name="file_id" id="file_id"/>
                        <input type="hidden" name="attachment" id="attachment" value="<%= SafeEncode.forHtmlAttribute(pdfAttch) %>"/>

        </form>
    </div>
</div>
</body>
</html>
