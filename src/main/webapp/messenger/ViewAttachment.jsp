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
 * Hierarchical Attachment Viewer
 *
 * This JSP page provides a hierarchical tree-view interface for viewing and managing
 * message attachments in the OpenO EMR messenger system. It displays attachment data
 * in an expandable/collapsible tree structure and allows users to save or modify
 * attachments before sending messages.
 *
 * Main Features:
 * - Interactive tree-view display of attachment data with expand/collapse functionality
 * - XML-based attachment data parsing and rendering
 * - Support for special AR (Antenatal Record) forms with custom navigation links
 * - Form-based attachment management with save functionality
 * - Cross-browser compatibility with IE and Mozilla/Firefox support
 *
 * Security Requirements:
 * - Requires "_msg" object read permissions via security taglib
 * - User session validation and role-based access control
 * - Validates msgSessionBean presence and validity
 *
 * Request Attributes:
 * - Attachment: XML string containing structured attachment data
 * - attId: Attachment ID for form processing and AR form links
 *
 * Session Dependencies:
 * - msgSessionBean: Required for attachment management context
 * - Must be valid session bean or redirects to index.jsp
 *
 * JavaScript Functions:
 * - showTbl(): Toggles visibility of tree nodes and updates expand/collapse icons
 * - expandAll(): Expands all collapsed tree nodes
 * - collapseAll(): Collapses all expanded tree nodes
 * - chkClick(): Prevents event bubbling for checkbox clicks
 * - popupViewAttach(): Opens attachment viewing popup windows
 *
 * XML Structure Support:
 * - Document root with nested tables, items, and content elements
 * - Supports removable and non-removable items
 * - Field-based content display with name/value pairs
 *
 * AR Form Integration:
 * - Special handling for AR Form attachments
 * - Direct links to AR1, AR2 Pg1, and AR2 Pg2 forms
 * - Integrated with encounter form system
 *
 * @since 2003
 */
--%>

<%@ page
        import="ca.openosp.openo.messenger.docxfer.send.*,ca.openosp.openo.messenger.docxfer.util.*, ca.openosp.openo.util.*" %>
<%@ page import="java.util.*, org.w3c.dom.*" %>
<%@ page import="ca.openosp.openo.messenger.docxfer.util.MsgCommxml" %>
<%@ page import="ca.openosp.openo.util.UtilXML" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>


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


<c:if test="${empty sessionScope.msgSessionBean}">
    <% response.sendRedirect(request.getContextPath() + "/messenger/index.jsp"); %>
</c:if>
<c:choose>
    <c:when test="${not empty sessionScope.msgSessionBean}">
        <c:set var="bean" value="${sessionScope.msgSessionBean}" scope="page"/>
        <c:if test="${bean.valid == false}">
            <% response.sendRedirect(request.getContextPath() + "/messenger/index.jsp"); %>
        </c:if>
    </c:when>
</c:choose>

<link rel="stylesheet" type="text/css" href="encounterStyles.css">
<html>
<head>
    <script type="text/javascript" src="<%= request.getContextPath() %>/js/global.js"></script>
    <link rel="stylesheet" type="text/css" media="all" href="<%= request.getContextPath() %>/share/css/extractedFromPages.css"/>

    <script language="javascript">
        // Cross-browser compatibility fix for IE vs Mozilla/Firefox event handling
        var browserName = navigator.appName;
        if (browserName == "Netscape") {
            if (document.implementation) {
                // Detects W3C DOM browsers (IE is not a W3C DOM Browser)
                if (Event.prototype && Event.prototype.__defineGetter__) {
                    // Detects Mozilla Based Browsers - add srcElement property for compatibility
                    Event.prototype.__defineGetter__("srcElement", function () {
                            var src = this.target;
                            if (src && src.nodeType == Node.TEXT_NODE)
                                src = src.parentNode;
                            return src;
                        }
                    );
                }
            }
        }

        /**
         * Toggles visibility of tree table nodes and updates expand/collapse icons
         * @param {string} tblName - ID of table to toggle (usually 'tblNode')
         * @param {Event} event - Click event from tree node
         */
        function showTbl(tblName, event) {
            var i;
            var span;

            // Find the span element that contains the clickable tree node
            if (event.srcElement.tagName == 'SPAN') {
                span = event.srcElement;
            } else {
                if (event.srcElement.parentNode.tagName == 'SPAN') {
                    span = event.srcElement.parentNode;
                } else {
                    if (event.srcElement.tagName == 'IMG') {
                        span = event.srcElement.parentNode.getElementsByTagName('SPAN').item(0);
                    }
                }
            }

            if (span != 'undefined') {
                // Toggle the expand/collapse icon
                var imgs = span.getElementsByTagName('IMG');
                if (imgs.length > 0) {
                    var img = imgs.item(0);
                    var s = img.src;
                    if (s.search('plus.gif') > -1) {
                        img.src = s.replace('plus.gif', 'minus.gif');
                    } else {
                        img.src = s.replace('minus.gif', 'plus.gif');
                    }
                }

                // Find and toggle the associated table visibility
                var nods = span.parentNode.childNodes;
                for (i = 0; i < nods.length; i++) {
                    var nod = nods.item(i);
                    if (nod.id == tblName) {
                        if (nod.style.display == "none") {
                            nod.style.display = "";
                        } else {
                            nod.style.display = "none";
                        }
                    }
                }
            }
        }

        /**
         * Expands all collapsed tree nodes by clicking plus icons
         */
        function expandAll() {
            var i;
            var root = document.all('tblRoot');
            var col = root.getElementsByTagName('IMG');

            // Click all plus icons to expand collapsed nodes
            for (i = 0; i < col.length; i++) {
                var nod = col.item(i);
                if (nod.src.search('plus.gif') > -1) {
                    nod.click();
                }
            }
        }

        /**
         * Collapses all expanded tree nodes by clicking minus icons
         */
        function collapseAll() {
            var i;
            var root = document.all('tblRoot');
            var col = root.getElementsByTagName('IMG');

            // Click all minus icons to collapse expanded nodes
            for (i = 0; i < col.length; i++) {
                var nod = col.item(i);
                if (nod.src.search('minus.gif') > -1) {
                    nod.click();
                }
            }
        }

        /**
         * Opens attachment viewing popup window
         * @param {number} vheight - Window height
         * @param {number} vwidth - Window width  
         * @param {string} varpage - Page URL to open
         */
        function popupViewAttach(vheight, vwidth, varpage) {
            var page = varpage;
            windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
            var popup = window.open(varpage, "oscarMVA", windowprops);
            if (popup != null) {
                if (popup.opener == null) {
                    popup.opener = self;
                }
            }
        }

        /**
         * Prevents event bubbling when checkboxes are clicked
         */
        function chkClick() {
            event.cancelBubble = true;
        }
    </script>
    <%
        // Parse attachment XML data for tree display
        Document xmlDoc = null;
        String attch = (String) request.getAttribute("Attachment");
        xmlDoc = MsgCommxml.parseXML(attch);

        Element root = xmlDoc.getDocumentElement();
    %>
    <%!
        String spanStartRoot = "<span class=\"treeNode\" onclick=\"javascript:showTbl('tblRoot',event);\">"
                + "<img class=\"treeNode\" src=\"img/minus.gif\" border=\"0\" />";

        String spanStart = "<span class=\"treeNode\" onclick=\"javascript:showTbl('tblNode',event);\">"
                + "<img class=\"treeNode\" src=\"img/plus.gif\" border=\"0\" />";
        String spanEnd = "</span>";

        String tblStartRoot = "<table class=\"treeTable\" id=\"tblRoot\" cellspacing=0 cellpadding=3>";
        String tblStart = "<table class=\"treeTable\" id=\"tblNode\" style=\"display:none\" cellspacing=0 cellpadding=3>";
        String tblStartContent = "<table class=\"content\" id=\"tblNode\" style=\"display:none\" cellspacing=0 cellpadding=3>";

        String tblRowStart = "<tr><td>";
        String tblRowEnd = "</td></tr>";

        String tblEnd = "</table>";

        void DrawDoc(Element root, JspWriter out)
                throws javax.servlet.jsp.JspException, java.io.IOException {
            out.print(spanStartRoot + "Document Transfer" + spanEnd);
            out.print(tblStartRoot);

            NodeList lst = root.getChildNodes();
            for (int i = 0; i < lst.getLength(); i++) {
                out.print(tblRowStart);
                DrawTable((Element) lst.item(i), out);
                out.print(tblRowEnd);
            }

            out.print(tblEnd);
        }

        void DrawTable(Element tbl, JspWriter out)
                throws javax.servlet.jsp.JspException, java.io.IOException {
            out.print(spanStart + tbl.getAttribute("name") + spanEnd);
            out.print(tblStart);

            NodeList lst = tbl.getChildNodes();
            for (int i = 0; i < lst.getLength(); i++) {
                out.print(tblRowStart);
                DrawItem((Element) lst.item(i), out);
                out.print(tblRowEnd);
            }
            out.print(tblEnd);
        }

        void DrawItem(Element item, JspWriter out)
                throws javax.servlet.jsp.JspException, java.io.IOException {
            out.print(spanStart);
            if (!item.getAttribute("removable").equalsIgnoreCase("false")) {
                // String sName = "item" + item.getAttribute("itemId");
                // out.print("<input type=checkbox name='" + sName + "' onclick='javascript:chkClick();'/>");
            }
            out.print(item.getAttribute("name") + ": " + item.getAttribute("value") + spanEnd);
            out.print(tblStartContent);

            NodeList lst = item.getChildNodes();
            for (int i = 0; i < lst.getLength(); i++) {
                if (lst.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    if (((Element) lst.item(i)).getTagName().equals("content")) {
                        DrawContent((Element) lst.item(i), out);
                    }
                }
            }
            out.print(tblEnd);
        }

        void DrawContent(Element content, JspWriter out)
                throws javax.servlet.jsp.JspException, java.io.IOException {
            NodeList lst = content.getChildNodes();
            for (int i = 0; i < lst.getLength(); i++) {
                if (lst.item(i).getNodeType() == Node.ELEMENT_NODE) {
                    Element fld = (Element) lst.item(i);
                    if (fld.getTagName().equals("fld")) {
                        out.print("<tr><td style='font-weight:bold'>");
                        out.print(fld.getAttribute("name") + ": ");
                        out.print("</td><td>");
                        out.print(fld.getAttribute("value"));
                        out.print("</td></tr>");
                    }
                }
            }
        }
    %>
    <title>Document Transfer</title>
</head>

<body class="BodyStyle" vlink="#0000FF">
<!--  -->
<table class="MainTable" id="scrollNumber1" name="encounterTable">
    <tr class="MainTableTopRow">
        <td class="MainTableTopRowLeftColumn">oscarComm</td>
        <td class="MainTableTopRowRightColumn">
            <table class="TopStatusBar">
                <tr>
                    <td>Document Transfer</td>
                    <td></td>
                    <td style="text-align: right"><a
                            href="javascript:popupStart(300,400,'About.jsp')">About</a> | <a
                            href="javascript:popupStart(300,400,'License.jsp')">License</a></td>
                </tr>
            </table>
        </td>
    </tr>
    <tr>
        <td class="MainTableLeftColumn">&nbsp;</td>
        <td class="MainTableRightColumn">
            <hr style="color: #A9A9A9;">
            <div style="height: 6px;"></div>

            <form method="POST" action="<%= request.getContextPath() %>/messenger/AdjustAttachments.jsp"><input
                    type=hidden name="xmlDoc"
                    value="<%= MsgCommxml.encode64(MsgCommxml.toXML(root)) %>"/> <input
                    type=hidden name="id" value="<%= request.getAttribute("attId")%>"/>

                <% DrawDoc(root, out); %> <br>
                <div style="font-size: 8pt; margin-top: 15px;"><input
                        type=submit value="Save Attachments"/> <a
                        href="javascript:expandAll();">Expand All</a> &nbsp;|&nbsp; <a
                        href="javascript:collapseAll();">Collapse All</a> <%
                    java.util.Properties prop = UtilXML.getPropText(xmlDoc, "table", "sqlFrom", "name"); //
                    if (prop.getProperty("formAR") != null && prop.getProperty("formAR").equals("AR Form")) {
                %> &nbsp;|&nbsp; <a
                        href="<%= request.getContextPath() %>/oscarEncounter/formCommARPg1.jsp?messageid=<%=request.getAttribute("attId")%>">AR1</a>
                    &nbsp;|&nbsp; <a
                            href="<%= request.getContextPath() %>/oscarEncounter/formCommARPg2.jsp?messageid=<%=request.getAttribute("attId")%>">AR2
                        Pg1</a> &nbsp;|&nbsp; <a
                            href="<%= request.getContextPath() %>/oscarEncounter/formCommARPg3.jsp?messageid=<%=request.getAttribute("attId")%>">AR2
                        Pg2</a> <%
                        } %>
                </div>
            </form>
        </td>
    </tr>
    <tr>
        <td class="MainTableBottomRowLeftColumn"></td>
        <td class="MainTableBottomRowRightColumn"></td>
    </tr>
</table>
</body>
</html>
