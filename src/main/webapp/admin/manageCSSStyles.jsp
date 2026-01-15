<!DOCTYPE html>
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
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@include file="/casemgmt/taglibs.jsp" %>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<html>
<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageCodeStyles"/></title>
    <meta charset="UTF-8">
    <link href="<%=request.getContextPath() %>/css/bootstrap.min.css" rel="stylesheet">
    <script src="<c:out value="${ctx}"/>/share/javascript/prototype.js" type="text/javascript"></script>
    <script src="<c:out value="${ctx}"/>/share/javascript/scriptaculous.js" type="text/javascript"></script>
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/picker.js"></script>
    <script type="text/javascript">

        function enableEdit(elem) {
            if (elem.checked == true) {
                $("styleText").readOnly = false;
                $("apply-btn").style.display = 'block';
            } else {
                $("styleText").readOnly = true;
                $("apply-btn").style.display = 'none';
            }

        }

        function addStyle(id, option) {
            var currentStyle = $F("styleText");
            var idx = currentStyle.indexOf(id);
            var idx2;
            var tmp1;
            var tmp2;


            //need to account for color not overwriting background-color
            if (id == "color") {
                tmp1 = currentStyle.charAt(idx - 1);
                if (tmp1 == '-') {
                    idx = currentStyle.indexOf(id, idx + 1);
                }
            }

            if (idx != -1) {
                tmp1 = currentStyle.substring(0, idx);
                idx2 = currentStyle.indexOf(";", idx);
                tmp2 = currentStyle.substring(idx2 + 1);

                if (option.value != "") {
                    currentStyle = tmp1 + id + ":" + option.value + ";" + tmp2;
                } else {
                    currentStyle = tmp1 + tmp2;
                }

                $("styleText").value = currentStyle;
                $("example").style.cssText = currentStyle;
            } else {
                if (option.value != "") {
                    currentStyle += id + ":" + option.value + ";";
                    $("styleText").value = currentStyle;
                    $("example").style.cssText = currentStyle;
                }
            }


        }

        var color;
        var bgcolor;

        function checkColours() {
            if (color != $F("color")) {
                addStyle("color", $("color"));
                color = $F("color");
            }

            if (bgcolor != $F("background-color")) {
                addStyle("background-color", $("background-color"));
                bgcolor = $F("background-color");
            }
        }

        function edit() {
            var style = $("style").options[$("style").selectedIndex].value;
            var styles = style.split(";");
            var item;
            var components;
            var value;
            var pos;
            var tmp;

            $("font-size").selectedIndex = 0;
            $("font-style").selectedIndex = 0;
            $("font-variant").selectedIndex = 0;
            $("font-weight").selectedIndex = 0;
            $("text-decoration").selectedIndex = 0;
            $("styleName").value = "";
            $("color").value = "";
            $("background-color").value = "";
            $("styleText").value = "";
            $("example").style.cssText = "";

            for (var idx = 0; idx < styles.length - 1; ++idx) {
                components = styles[idx].split(":");
                item = components[0];
                value = components[1];

                if (item == "color" || item == "background-color") {
                    $(item).value = value;
                } else {
                    for (var idx2 = 0; idx2 < $(item).options.length; ++idx2) {
                        if ($(item).options[idx2].value == value) {
                            $(item).options[idx2].selected = true;
                            break;
                        }
                    } //end for
                }
            } //end for

            if (style != "-1") {
                $("styleText").value = style;
                $("editStyle").value = style;
                $("example").style.cssText = style;
                $("styleName").value = $("style").options[$("style").selectedIndex].text;
            }
        }

        function checkfields() {
            var msg = "";

            if ($("styleText").value.length == 0) {
                msg = "<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.noStyleError"/>";
            }

            if ($("styleName").value.trim().length == 0) {
                msg += "\r\n<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.noStyleNameError"/>";
            }

            if (msg.length > 0) {
                alert(msg);
                return false;
            }

            //if it's a new style save it for addition
            if ($("style").selectedIndex == 0) {
                addStyle("color", $("color"));
                addStyle("background-color", $("background-color"));
                $("editStyle").value = $("styleText").value;
            }
            $("method").value = "save";

            return true;

        }

        function deleteStyle() {

            if ($("style").selectedIndex == 0) {
                return false;
            }

            if (confirm("<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.confirmDelete"/>")) {
                $("editStyle").value = $("style").options[$("style").selectedIndex].value;
                $("method").value = "delete";
                return true;
            }
            return false;
        }

        function applyStyle() {
            $("example").style.cssText = $("styleText").value;
        }

        function reinit() {
            $("style").selectedIndex = 0;
            $("font-size").selectedIndex = 0;
            $("font-style").selectedIndex = 0;
            $("font-variant").selectedIndex = 0;
            $("font-weight").selectedIndex = 0;
            $("text-decoration").selectedIndex = 0;
            $("styleName").value = "";
            $("color").value = "";
            $("background-color").value = "";
            $("styleText").value = "";
            $("editStyle").value = "";
            $("example").style.cssText = "";
        }

        function init() {
            reinit();
            color = $F("color");
            bgcolor = $F("background-color");
            setInterval("checkColours()", 5000);
        }

    </script>

    <style type="text/css">
        .span4 {
            padding-left: 0px;
            padding-right: 0px;
            margin-left: 0px;
            margin-right: 0px;
        }

        .span10 {
            padding-left: 0px;
            padding-right: 0px;
            margin-left: 0px;
            margin-right: 0px;
        }
    </style>

</head>
<body>

<h3><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.manageCodeStyles"/></h3>

<div class="container-fluid form-inline">

    <%
        String success = (String) request.getAttribute("success");
        if ("true".equalsIgnoreCase(success)) {
    %>
    <div class="alert alert-success">
        <button type="button" class="close" data-dismiss="alert">&times;</button>
        <strong>Success!</strong> <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.sucess"/>
    </div>
    <%
        }
    %>

    <form action="${pageContext.request.contextPath}/admin/manageCSSStyles.do" method="post" accept-charset="UTF-8">
        <input type="hidden" id="method" name="method" value="save"/>

        <div class="row well"><!--select existing styles-->

            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.CurrentStyles"/><br/>

            <select name="selectedStyle" id="style">
                <option value="-1"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                <c:forEach items="${styles}" var="style">
                    <option value="${style.style}">${style.name}</option>
                </c:forEach>
            </select>

            <input class="btn" type="button" onclick="edit();return false;"
                   value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.Edit"/>"/>
            <input type="submit" name="submit" value="Delete" class="btn" onclick="return deleteStyle();"/>


        </div>
        <!--select existing styles-->


        <div class="row">

            <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.StyleName"/><br>
            <input type="text" id="styleName" name="styleName"/>
            <!--<br><br>
<small><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.Instructions"/></small>-->

        </div>

        <div class="row">
            <div class="span4">
                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.FontSize"/><br>
                <select id="font-size" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="xx-small">XX-Small</option>
                    <option value="x-small">X-Small</option>
                    <option value="medium">Medium</option>
                    <option value="large">Large</option>
                    <option value="x-large">X-Large</option>
                    <option value="xx-large">XX-Large</option>
                </select>
                <br>

                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.FontStyle"/><br>
                <select id="font-style" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="italic">Italic</option>
                    <option value="oblique">Obllique</option>
                </select>
                <br>

                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.FontVariant"/><br>
                <select id="font-variant" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="small-caps">Small-Caps</option>
                </select>
                <br>

                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.FontWeight"/><br>
                <select id="font-weight" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="bold">Bold</option>
                    <option value="bolder">Bolder</option>
                    <option value="lighter">Lighter</option>
                </select>
                <br/>

                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.TextDecoration"/><br>
                <select id="text-decoration" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="underline">Underline</option>
                    <option value="overline">Overline</option>
                    <option value="line-through">Line Through</option>
                </select>
                <br/>

                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.TextColour"/><br>
                <a href="javascript:TCP.popup(document.forms[0].elements['color']);"><img width="15" height="13"
                                                                                          border="0"
                                                                                          src="<%= request.getContextPath() %>/images/sel.gif"></a>
                <input id="color" type="text" size="7" onchange="checkColours();"/>
                <br>

                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.BackgroundColour"/><br>
                <a href="javascript:TCP.popup(document.forms[0].elements['background-color'])"><img width="15"
                                                                                                    height="13"
                                                                                                    border="0"
                                                                                                    src="<%= request.getContextPath() %>/images/sel.gif"></a>
                <input id="background-color" type="text" size="7" onchange="checkColours();"/>
                <br>


            </div><!--span4-->


            <div class="span4">
                <input type="hidden" id="editStyle" name="editStyle"/>

                <fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.StyleText"/> <small><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.ManualEnter"/><input type="checkbox"
                                                                     onclick="enableEdit(this);"></small><br/>
                <textarea rows="8" class="span6" readonly="true" id="styleText" name="styleText"></textarea>
                <input class="btn" id="apply-btn" type="button"
                       value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.Apply"/>" onclick="applyStyle();return false;"
                       style="display:none"/>

                <br><br>

                Sample Text:<br>
                <span id="example"><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.Example"/></span>

            </div><!--span6-->
        </div>
        <!-- row -->


        <div class="span10" style="text-align:right;">
            <hr>
            <input class="btn btn-large" type="button" value="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.manageCodeStyles.Clear"/>"
                   onclick="reinit();return false;"/>
            <input type="submit" name="submit" value="Save" class="btn btn-large btn-primary" onclick="return checkfields();" />
        </div>

    </form>
</div>


</body>

</html>
