<%--

    Copyright (c) 2008-2012 Indivica Inc.

    This software is made available under the terms of the
    GNU General Public License, Version 2, 1991 (GPLv2).
    License details are available via "indivica.ca/gplv2"
    and "gnu.org/licenses/gpl-2.0.html".

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<!DOCTYPE HTML>
<%@ page
        import="java.util.*,ca.openosp.OscarProperties ,ca.openosp.openo.report.reportByTemplate.*,ca.openosp.openo.hospitalReportManager.*,ca.openosp.openo.utility.SpringUtils, ca.openosp.openo.commn.dao.UserPropertyDAO, ca.openosp.openo.commn.model.UserProperty" %>
<%

    OscarProperties props = OscarProperties.getInstance();

    UserPropertyDAO userPropertyDao = (UserPropertyDAO) SpringUtils.getBean(UserPropertyDAO.class);

    String userName = "";
    String location = "";
    String interval = "30";
    String privateKey = "";
    String decryptionKey = "";

    try {
        userName = userPropertyDao.getProp("hrm_username").getValue();
    } catch (Exception e) {
        userName = "";
    }

    try {
        location = userPropertyDao.getProp("hrm_location").getValue();
    } catch (Exception e) {
        location = "";
    }

    try {
        interval = userPropertyDao.getProp("hrm_interval").getValue();
    } catch (Exception e) {
        interval = "30";
    }

    try {
        privateKey = userPropertyDao.getProp("hrm_privateKey").getValue();
    } catch (Exception e) {
        privateKey = "";
    }

    try {
        decryptionKey = userPropertyDao.getProp("hrm_decryptionKey").getValue();
    } catch (Exception e) {
        decryptionKey = "";
    }
%>

<%@ page import="java.util.*,ca.openosp.openo.report.reportByTemplate.*" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>


<html>
    <head>
        <title>HRM Preferences</title>
        <link href="${pageContext.request.contextPath}/css/bootstrap.css" rel="stylesheet" type="text/css">
        <link href="${pageContext.request.contextPath}/css/bootstrap-responsive.css" rel="stylesheet" type="text/css">
        <script src="${pageContext.request.contextPath}/share/javascript/Oscar.js"></script>
        <script src="${pageContext.request.contextPath}/js/global.js"></script>

        <script>
            function popupPage(vheight, vwidth, varpage) { //open a new popup window
                var page = "" + varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";//360,680
                var popup = window.open(page, "groupno", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                    popup.focus();
                }
            }

            function updateLink(filePath, keytype) {
                if (keytype == "PRIVATEKEY") {
                    <%=privateKey%>
                    = filePath;
                } else if (keytype == "DECRYPTIONKEY") {
                    <%=decryptionKey%>
                    = filePath;
                }
            }
        </script>
    </head>
    <body>
    <h4>HRM Preferences</h4>
    <form action="<%=request.getContextPath()%>/hospitalReportManager/HRMPreferences.do" method="post">
        <fieldset>
            <div class="control-group">
                <label class="control-label">User Name:</label>
                <div class="controls">
                    <input readonly="readonly" type="text" name="userName" value="<%=userName%>"/>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label">SFTP Download Folder:</label>
                <div class="controls">
                    <input type="text" name="location" value="<%=location%>"/>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label">Private Key:</label>
                <div class="controls">
                    <a href="<%= request.getContextPath() %>/<%=privateKey%>" id="pkeyLink">View Private Key</a>
                    <input type="button" class="btn" name="privateKey" value="Upload Private Key"
                           onClick='popupPage(600,900,"<%=request.getContextPath() %>/hospitalReportManager/hrmKeyUploader.jsp");return false;'/>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label">Decryption Key:</label>
                <div class="controls">
                    <a href="<%= request.getContextPath() %>/<%=decryptionKey%>" id="dkeyLink">View Decryption Key</a>
                    <input type="button" class="btn" name="decryptionKey" value="Upload Decryption Key"
                           onClick='popupPage(600,900,"<%=request.getContextPath() %>/hospitalReportManager/hrmKeyUploader.jsp");return false;'/>
                </div>
            </div>
            <div class="control-group">
                <label class="control-label">Auto Polling Interval:</label>
                <div class="controls">
                    <input type="text" name="interval" value="<%=interval %>"/>
                </div>
            </div>
            <div class="control-group">
                <input type="submit" class="btn btn-primary" value="Submit"/>
            </div>
            </table>
    </form>
</html>