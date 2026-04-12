<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>
<%@ page import="java.util.*" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils, io.github.carlos_emr.carlos.commn.model.Document" %>
<%@ page import="java.nio.charset.StandardCharsets" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.DocumentDao" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="r" reverse="<%=true%>">
    <%
        authed = false;
        response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_lab");
    %>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<html>
<head>
    <title>PDF Sorter</title>

    <link rel="stylesheet"
          href="${ pageContext.servletContext.contextPath }/library/jquery/jquery-ui.theme-1.14.2.min.css"
          type="text/css"/>
    <link rel="stylesheet" href="${ pageContext.servletContext.contextPath }/share/css/sorter.css" type="text/css"/>

    <script type="text/javascript"
            src="${ pageContext.servletContext.contextPath }/library/jquery/jquery-3.7.1.min.js"></script>
            <script src="${ pageContext.servletContext.contextPath }/library/jquery/jquery-compat.js"></script>
    <script type="text/javascript"
            src="${ pageContext.servletContext.contextPath }/library/jquery/jquery-ui-1.14.2.min.js"></script>

    <script type="text/javascript"
            src="${ pageContext.servletContext.contextPath }/share/javascript/jquery/jquery.rotate.1-1.js"></script>
    <script>
        var ctx = "${ pageContext.servletContext.contextPath }";
    </script>
    <script type="text/javascript"
            src="${ pageContext.servletContext.contextPath }/share/javascript/sorter.js"></script>

</head>
<body>


<div id="mastercontainer">

    <div id="buildercontainer">
        <h2>Document 2</h2>
        <div id="builderContainerWindow">
            <ul id="builder"></ul>
        </div>
        <div id="pickertoolscontainer">
            <ul id="pickertools">
                <li id="tool_add"><img
                        src="${ pageContext.servletContext.contextPath }/images/icons/103.png"><span>Add</span></li>
                <li id="tool_remove"><img src="${ pageContext.servletContext.contextPath }/images/icons/101.png"><span>Remove</span>
                </li>
                <li id="tool_rotate"><img src="${ pageContext.servletContext.contextPath }/images/icons/114.png"><span>Rotate</span>
                </li>
                <li id="tool_savecontinue"><img
                        src="${ pageContext.servletContext.contextPath }/images/icons/172.png"><span>Save &amp; Continue</span>
                </li>
                <li id="tool_done"><img src="${ pageContext.servletContext.contextPath }/images/icons/071.png"><span>Done</span>
                </li>
            </ul>
        </div>
    </div>

    <div id="pickercontainer">
        <h2>Document 1</h2>
        <div id="pickercontainerWindow">
            <ul id="picker">
                <%
                    String documentId = request.getParameter("document");
                    String queueID = request.getParameter("queueID");
                    String demoName = request.getParameter("demoName");
                    DocumentDao docdao = SpringUtils.getBean(DocumentDao.class);
                    Document thisDocument = docdao.getDocument(documentId);

                    for (int i = 1; i <= thisDocument.getNumberofpages(); i++) {
                %>
                <li>
                    <img class="page"
                         src='<%=Encode.forHtmlAttribute(request.getContextPath() + "/documentManager/ManageDocument.do?method=viewDocPage&doc_no=" + java.net.URLEncoder.encode(documentId != null ? documentId : "", StandardCharsets.UTF_8) + "&curPage=" + i)%>'/>
                </li>
                <%
                    }
                %>
            </ul>
        </div>
    </div>
</div>

<input type="hidden" id="document_no" value="<%= Encode.forHtmlAttribute(documentId != null ? documentId : "") %>"/>
<input type="hidden" id="queueID" value="${ e:forHtmlAttribute(param.queueID) }"/>
<input type="hidden" id="demoName" value="<%= Encode.forHtmlAttribute(demoName != null ? demoName : "") %>"/>
</body>
</html>