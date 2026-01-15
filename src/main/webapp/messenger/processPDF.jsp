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

<%--
/**
 * PDF Generation from HTML Content
 *
 * This JSP page handles the conversion of HTML content to PDF format for message
 * attachments. It loads content from a specified URI into an iframe, extracts the
 * HTML, and processes it for PDF generation with automatic form submission.
 *
 * Main Features:
 * - Automatic HTML content extraction from iframe after page load
 * - JavaScript-driven workflow with timed content capture
 * - Form submission to ProcessDoc2PDF action for final PDF generation
 * - Patient context preservation throughout processing
 *
 * Security Requirements:
 * - Requires "_msg" object read permissions via security taglib
 * - User session validation and role-based access control
 *
 * Request Parameters:
 * - demographic_no: Required patient demographic number
 * - uri: Source URI to load content from
 * - pdfTitle: Title for the generated PDF attachment
 *
 * JavaScript Functions:
 * - SetBottomURL(): Updates iframe source to load content
 * - GetBottomSRC(): Extracts HTML content from loaded iframe
 *
 * Processing Flow:
 * 1. Load specified URI in hidden iframe
 * 2. Wait 5 seconds for content to load
 * 3. Extract HTML content from iframe
 * 4. Auto-submit form to PDF generation action
 * 5. Close window and focus parent
 *
 * @since 2003
 */
--%>

<%@ page
        import="ca.openosp.openo.messenger.docxfer.send.*,ca.openosp.openo.messenger.docxfer.util.*, ca.openosp.openo.encounter.data.*, ca.openosp.openo.encounter.pageUtil.EctSessionBean " %>
<%@  page
        import=" java.util.*, org.w3c.dom.*, java.sql.*, ca.openosp.*, java.text.*, java.lang.*,java.net.*"
        errorPage="/errorpage.jsp" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_msg" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_msg");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="ca.openosp.openo.util.*" %>


<%
    // Extract processing parameters for PDF generation
    String demographic_no = request.getParameter("demographic_no");
    String uri = request.getParameter("uri");
    String pdfTitle = request.getParameter("pdfTitle");
%>


<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title>Generate Preview Page</title>
</head>
<script type="text/javascript">
    /**
     * Sets the URL for the bottom iframe to load content from
     * @param {string} url - URL to load in the iframe
     */
    function SetBottomURL(url) {
        f = parent.attFrame;

        if (url != "") {
            loc = url;
        } else {
            loc = document.forms[0].url.value;
        }
        f.location = loc;
    }

    /**
     * Extracts HTML content from the loaded iframe
     */
    function GetBottomSRC() {
        f = parent.attFrame;
        document.forms[0].srcText.value = f.document.body.innerHTML;
    }
</script>
<body>
<%-- <jsp:useBean id="beanInstanceName" scope="session" class="beanPackage.BeanClassName" /> --%>
<%-- <jsp:getProperty name="beanInstanceName"  property="propertyName" /> --%>


<form action="${pageContext.request.contextPath}/messenger/ProcessDoc2PDF.do" method="post">

    Attaching <%=demographic_no%>
    <%=pdfTitle%>

    <textarea name="srcText" rows="5" cols="80"></textarea>
    <input type="hidden" name="isPreview" id="isPreview" value="false"/>
    <input type="submit" name="ok" value="Apply" />
    <input type="hidden" name="pdfTitle" id="pdfTitle" value="<%=pdfTitle%>"/>

</form>

<script>
    // Load content from specified URI and process after delay
    SetBottomURL('<%=uri%>' + "&demographic_no=" + '<%=demographic_no%> ');
    
    // Wait 5 seconds for content to load, then extract and submit
    setTimeout("GetBottomSRC()", 5000);
    setTimeout("document.forms[0].submit()", 5000);
    
    // Close this window and focus parent
    this.close();
    parent.window.focus();
</script>

</body>
</html>
