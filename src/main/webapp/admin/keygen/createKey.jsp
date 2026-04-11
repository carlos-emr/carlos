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

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta" prefix="e" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ page import="java.util.*,java.io.*,io.github.carlos_emr.carlos.lab.ca.all.util.KeyPairGen" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + ","
            + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>
<fmt:setBundle basename="oscarResources"/>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<%
    String name = request.getParameter("name");
    String type = request.getParameter("type");
    if (type != null && type.equals("OTHER"))
        type = request.getParameter("otherType");

    java.util.ResourceBundle createKeyResources =
        java.util.ResourceBundle.getBundle("oscarResources", request.getLocale());

    String message = null;
    String failDisplay = "none";

    String error = "false";

    if (name != null && !name.matches("^[a-zA-Z0-9._-]+$")) {
        error = "true";
        message = createKeyResources.getString("admin.createKey.msgErrInvalidChars");
        name = null;
    }

    if (name != null) {
        if (name.equals("oscar")) {
            if (KeyPairGen.checkName(name)) {
                message = createKeyResources.getString("admin.createKey.msgErrOscarKeyExists");
                error = "true";
            } else if (KeyPairGen.createKeys(name, null).equals("success")) {
                message = createKeyResources.getString("admin.createKey.msgCarlosKeyCreated");
            } else {
                message = createKeyResources.getString("admin.createKey.msgErrOscarKeyFailed");
                error = "true";
            }
        } else {
            if (KeyPairGen.checkName(name)) {
                message = java.text.MessageFormat.format(
                    createKeyResources.getString("admin.createKey.msgErrServiceKeyExists"), name);
                error = "true";
            } else {
                String clientKey = KeyPairGen.createKeys(name, type);
                String oscarKey = KeyPairGen.getPublic();

                if (clientKey == null) {
                    message = createKeyResources.getString("admin.createKey.msgErrKeyPairFailed");
                    error = "true";
                } else if (oscarKey == null) {
                    message = createKeyResources.getString("admin.createKey.msgErrPublicKeyFailed");
                    error = "true";
                } else {

                    String keyPairOut = "-------- Service Name --------\n" + name + "\n------------------------------\n" +
                            "----- Client Private Key -----\n" + clientKey + "\n------------------------------\n" +
                            "------ Oscar Public Key ------\n" + oscarKey + "\n------------------------------";
                    response.setContentType("text/plain");
                    response.setHeader("X-Content-Type-Options", "nosniff");
                    response.setContentLength(keyPairOut.length());
                    response.setHeader("Content-Disposition", "attachment; filename=keyPair.key");
                    ServletOutputStream output = null;

                    try {
                        output = response.getOutputStream();
                        output.print(keyPairOut);
                        output.flush();
                    } catch (IOException e) {
                        message = createKeyResources.getString("admin.createKey.msgErrSaveKeyPair");
                        error = "true";
                    } finally {
                        if (output != null) {
                            try {
                                output.close();
                            } catch (IOException e) {
                                message = createKeyResources.getString("admin.createKey.msgErrCloseStream");
                                error = "true";
                            }
                        }
                    }
                }
            }
        }
    }

    if (message != null) {
        failDisplay = "block";
    }
%>

<html lang="${pageContext.request.locale.language}">
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <title><fmt:message key="admin.createKey.title"/></title>
    <link rel="stylesheet" type="text/css" href="<%=request.getContextPath()%>/share/css/OscarStandardLayout.css">
    <fmt:message key="admin.createKey.msgEnterServiceName" var="msgEnterServiceName"/>
    <fmt:message key="admin.createKey.msgSpecifyOtherType" var="msgSpecifyOtherType"/>
    <script type="text/javascript">
        var i18n_enterServiceName = '${e:forJavaScript(msgEnterServiceName)}';
        var i18n_specifyOtherType = '${e:forJavaScript(msgSpecifyOtherType)}';

        function selectOther() {
            if (document.getElementById('selection').value == "OTHER")
                document.getElementById('OTHER').style.visibility = "visible";
            else
                document.getElementById('OTHER').style.visibility = "hidden";
        }

        function checkInput() {

            if (document.getElementById('name').value == "") {
                alert(i18n_enterServiceName);
                return false;
            } else if (document.getElementById('selection').value == "OTHER" && document.getElementById('otherType').value == "") {
                alert(i18n_specifyOtherType);
                return false;
            }
            document.getElementById('success').style.display = "block";
            document.getElementById('fail').style.display = "none";
            return true;
        }
    </script>
</head>
<body>
<form method='POST' action=''>
    <table align="center" class="MainTable">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn" width="175"><fmt:message key="admin.createKey.sectionKeyPairCreation"/>
            </td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td>
                            <div id="success" style="display: none;"><fmt:message key="admin.createKey.msgKeyPairCreatedSuccess"/>
                            </div>
                            <div id="fail" style="display:<%= failDisplay %>;">
                                <%
                                    if (message != null) {
                                        if (error.equals("false")) {
                                            out.print(Encode.forHtml(message));
                                        } else {
                                %><font color="red"><%= Encode.forHtml(message) %>
                            </font>
                                <%
                                        }
                                    }
                                    // reset the message after it has been used
                                    message = null;
                                %>
                            </div>
                        </td>
                        <td>&nbsp;</td>
                        <td style="text-align: right"><a
                                href="javascript:popupStart(300,400,'About.jsp')"><fmt:message key="global.about"/></a> | <a
                                href="javascript:popupStart(300,400,'License.jsp')"><fmt:message key="global.license"/></a></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td><fmt:message key="admin.createKey.btnCreateKeyPair" var="btnCreateKeyPairLabel"/>
                <input type="submit" value="${btnCreateKeyPairLabel}"
                       onclick="return checkInput()"></td>
            <td>
                <table>
                    <tr>
                        <td><fmt:message key="admin.createKey.labelServiceName"/></td>
                        <td><input type="text" id="name" name="name"></td>
                    </tr>
                    <tr>
                        <td><fmt:message key="admin.createKey.labelLabType"/></td>
                        <td><select name="type" id="selection" onClick="selectOther()">
                            <option value="ALPHA">ALPHA</option>
                            <option value="CML">CML</option>
                            <option value="EPSILON">EPSILON/MHL</option>

                            <option value="PATHL7"
                                    <oscar:oscarPropertiesCheck property="PATHNET_LABS" value="yes">
                                        selected
                                    </oscar:oscarPropertiesCheck>>EXCELLERIS
                            </option>

                            <option value="GDML">GDML</option>
                            <option value="HHSEMR">HHSEMR</option>
                            <option value="HRMXML">HRM XML</option>
                            <option value="IHA">IHA</option>
                            <option value="IHAPOI">IHAPOI</option>
                            <option value="MDS">MDS/Lifelabs</option>
                            <!-- <option value="HL7">HL7</option> -->
                            <option value="SIOUX">SIOUX</option>
                            <option value="Spire">Spire</option>
                            <option value="PDFDOC">PDFDOC</option>
                            <option value="BIOTEST">BioTest</option>
                            <option value="CLS"><fmt:message key="admin.createKey.optCalgaryLabService"/></option>
                            <option value="TRUENORTH">TRUENORTH</option>
                            <option value="OTHER"><fmt:message key="admin.createKey.optOther"/></option>
                        </select></td>
                    </tr>
                    <tr id="OTHER" style="visibility: hidden;">
                        <td><fmt:message key="admin.createKey.labelSpecifyOtherType"/></td>
                        <td><input type="text" id="otherType" name="otherType"></td>
                    </tr>
                </table>
            </td>
        </tr>
    </table>
</form>
</body>
</html>
