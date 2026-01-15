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

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = session.getAttribute("userrole") + "," + session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<link rel="stylesheet" type="text/css"
      href="<%= request.getContextPath() %>/oscarEncounter/encounterStyles.css">
<html>
    <script language="JavaScript" type="text/JavaScript">
        <!--
        function reloadPage(init) {  //reloads the window if Nav4 resized
            if (init == true) with (navigator) {
                if ((appName == "Netscape") && (parseInt(appVersion) == 4)) {
                    document.pgW = innerWidth;
                    document.pgH = innerHeight;
                    onresize = reloadPage;
                }
            }
            else if (innerWidth != document.pgW || innerHeight != document.pgH) location.reload();
        }

        reloadPage(true);

        function findObj(n, d) { //v4.01
            var p, i, x;
            if (!d) d = document;
            if ((p = n.indexOf("?")) > 0 && parent.frames.length) {
                d = parent.frames[n.substring(p + 1)].document;
                n = n.substring(0, p);
            }
            if (!(x = d[n]) && d.all) x = d.all[n];
            for (i = 0; !x && i < d.forms.length; i++) x = d.forms[i][n];
            for (i = 0; !x && d.layers && i < d.layers.length; i++) x = findObj(n, d.layers[i].document);
            if (!x && d.getElementById) x = d.getElementById(n);
            return x;
        }

        function showHideLayers() { //v6.0
            var i, p, v, obj, args = showHideLayers.arguments;
            for (i = 0; i < (args.length - 2); i += 3) if ((obj = findObj(args[i])) != null) {
                v = args[i + 2];
                if (obj.style) {
                    obj = obj.style;
                    v = (v == 'show') ? 'visible' : (v == 'hide') ? 'hidden' : v;
                }
                obj.visibility = v;
            }
        }

        //-->
    </script>
    <head>
        <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
        <title><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/></title>

        <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>
        <style type="text/css" media="print">
            .header {
                display: none;
            }

            .header INPUT {
                display: none;
            }

            .header A {
                display: none;
            }
        </style>
        <script type="text/javascript">
            var remote = null;

            function rs(n, u, w, h, x) {
                args = "width=" + w + ",height=" + h + ",resizable=yes,scrollbars=yes,status=0,top=60,left=30";
                remote = window.open(u, n, args);
                // if (remote != null) {
                //    if (remote.opener == null)
                //        remote.opener = self;
                // }
                // if (x == 1) { return remote; }
            }

            function popupOscarFluConfig(vheight, vwidth, varpage) { //open a new popup window
                var page = varpage;
                windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "OscarFluConfig", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }


            // Sets the text area to the currently select favourite query, or to an empty string
            function write2TextArea() {
                const form = document.forms[0];
                const select = form.selectedRecentSearch;
                const selectedValue = select.options[select.selectedIndex].value;
                if (selectedValue === 'Recent Search') {
                    form.sql.value = '';
                } else {
                    form.sql.value = selectedValue;
                }
            }
        </script>

    </head>

    <body vlink="#0000FF" class="BodyStyle">
    <div id="Layer1"
         style="position: absolute; left: 5px; top: 200px; width: 800px; height: 600px; z-index: 1; visibility: hidden;">
        <table width="100%" border="1" cellpadding="0" cellspacing="0"
               bgcolor="#D6D5C5">
            <tr>
                <td><font size="2" face="Tahoma">
                    <c:if test="${not empty resultText}">
                        <pre><c:out value="${resultText}" escapeXml="false"/></pre>
                    </c:if>
                </font></td>
            </tr>
        </table>
    </div>
    <table class="MainTable" id="scrollNumber1" name="encounterTable">
        <form action="${pageContext.request.contextPath}/oscarReport/RptByExample.do" method="post">
        <tr class="MainTableTopRow">
            <td class="MainTableTopRowLeftColumn"><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.CDMReport.msgReport"/></td>
            <td class="MainTableTopRowRightColumn">
                <table class="TopStatusBar">
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgQueryByExamples"/></td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableLeftColumn" valign="top">
                <table>
                    <tr>
                        <td nowrap><a href="#"
                                      onClick="popupPage(600, 1000, 'RptViewAllQueryByExamples.do')">View
                            Query History</a></td>
                    </tr>
                    <tr>
                        <td><a href="#"
                               onClick="popupPage(600, 1000, 'RptByExamplesAllFavorites.do')">Edit
                            My Favorite</a></td>
                    </tr>
                </table>
            </td>
            <td class="MainTableRightColumn">
                <table>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgEnterAQuery"/></td>
                    </tr>
                    <tr>
                        <td><textarea name="sql" cols="80" rows="4"></textarea></td>
                    </tr>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgOr"/></td>
                    </tr>
                    <tr>
                        <td><fmt:setBundle basename="oscarResources"/><fmt:message key="oscarReport.RptByExample.MsgSelectFromMyFavorites"/></td>
                    </tr>
                    <tr>
                        <td><select name="selectedRecentSearch"
                                         style="width:660">
                            <option value="My favorites" disabled="true"/>
                            <c:forEach var="favorite" items="${favorites}">
                                <option value="${favorite.query}">
                                        ${favorite.query}
                                </option>
                            </c:forEach>
                        </select> <input type="button" value="Load Query"
                                              onClick="write2TextArea(); return false;"></td>
                    </tr>
                    <tr>
                        <td><input type="button" value="Query" onclick="submit();"/>
                        </td>
                    </tr>
                    <tr></tr>
                    <tr>
                        <td>
                            <c:if test="${not empty results}">
                                <c:out value="${results}" escapeXml="false"/>
                            </c:if>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="MainTableBottomRowLeftColumn">&nbsp;</td>

            <td class="MainTableBottomRowRightColumn">&nbsp;</td>
        </tr>
    </table>

    </form>
    </body>
</html>
