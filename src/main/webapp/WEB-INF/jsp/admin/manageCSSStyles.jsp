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


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@include file="/WEB-INF/jsp/casemgmt/taglibs.jsp" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>
<html>
<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.admin.manageCodeStyles"/></title>
    <meta charset="UTF-8">
    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet">
    <script type="text/javascript" src="<%= request.getContextPath() %>/share/javascript/picker.js"></script>
    <script type="text/javascript">

        function getEl(id) {
            return document.getElementById(id);
        }

        function enableEdit(elem) {
            if (elem.checked == true) {
                getEl("styleText").readOnly = false;
                getEl("apply-btn").style.display = 'block';
            } else {
                getEl("styleText").readOnly = true;
                getEl("apply-btn").style.display = 'none';
            }

        }

        function addStyle(id, option) {
            var currentStyle = getEl("styleText").value;
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

                getEl("styleText").value = currentStyle;
                getEl("example").style.cssText = currentStyle;
            } else {
                if (option.value != "") {
                    currentStyle += id + ":" + option.value + ";";
                    getEl("styleText").value = currentStyle;
                    getEl("example").style.cssText = currentStyle;
                }
            }


        }

        var color;
        var bgcolor;

        function checkColours() {
            if (color != getEl("color").value) {
                addStyle("color", getEl("color"));
                color = getEl("color").value;
            }

            if (bgcolor != getEl("background-color").value) {
                addStyle("background-color", getEl("background-color"));
                bgcolor = getEl("background-color").value;
            }
        }

        function edit() {
            var style = getEl("style").options[getEl("style").selectedIndex].value;
            var styles = style.split(";");
            var item;
            var components;
            var value;
            var pos;
            var tmp;

            getEl("font-size").selectedIndex = 0;
            getEl("font-style").selectedIndex = 0;
            getEl("font-variant").selectedIndex = 0;
            getEl("font-weight").selectedIndex = 0;
            getEl("text-decoration").selectedIndex = 0;
            getEl("styleName").value = "";
            getEl("color").value = "";
            getEl("background-color").value = "";
            getEl("styleText").value = "";
            getEl("example").style.cssText = "";

            for (var idx = 0; idx < styles.length - 1; ++idx) {
                components = styles[idx].split(":");
                item = components[0];
                value = components[1];

                if (item == "color" || item == "background-color") {
                    getEl(item).value = value;
                } else {
                    for (var idx2 = 0; idx2 < getEl(item).options.length; ++idx2) {
                        if (getEl(item).options[idx2].value == value) {
                            getEl(item).options[idx2].selected = true;
                            break;
                        }
                    } //end for
                }
            } //end for

            if (style != "-1") {
                getEl("styleText").value = style;
                getEl("editStyle").value = style;
                getEl("example").style.cssText = style;
                getEl("styleName").value = getEl("style").options[getEl("style").selectedIndex].text;
            }
        }

        function checkfields() {
            var msg = "";

            if (getEl("styleText").value.length == 0) {
                msg = "<fmt:message key="admin.manageCodeStyles.noStyleError"/>";
            }

            if (getEl("styleName").value.trim().length == 0) {
                msg += "\r\n<fmt:message key="admin.manageCodeStyles.noStyleNameError"/>";
            }

            if (msg.length > 0) {
                alert(msg);
                return false;
            }

            //if it's a new style save it for addition
            if (getEl("style").selectedIndex == 0) {
                addStyle("color", getEl("color"));
                addStyle("background-color", getEl("background-color"));
                getEl("editStyle").value = getEl("styleText").value;
            }
            getEl("method").value = "save";

            return true;

        }

        function deleteStyle() {

            if (getEl("style").selectedIndex == 0) {
                return false;
            }

            if (confirm("<fmt:message key="admin.manageCodeStyles.confirmDelete"/>")) {
                getEl("editStyle").value = getEl("style").options[getEl("style").selectedIndex].value;
                getEl("method").value = "delete";
                return true;
            }
            return false;
        }

        function applyStyle() {
            getEl("example").style.cssText = getEl("styleText").value;
        }

        function reinit() {
            getEl("style").selectedIndex = 0;
            getEl("font-size").selectedIndex = 0;
            getEl("font-style").selectedIndex = 0;
            getEl("font-variant").selectedIndex = 0;
            getEl("font-weight").selectedIndex = 0;
            getEl("text-decoration").selectedIndex = 0;
            getEl("styleName").value = "";
            getEl("color").value = "";
            getEl("background-color").value = "";
            getEl("styleText").value = "";
            getEl("editStyle").value = "";
            getEl("example").style.cssText = "";
        }

        function init() {
            reinit();
            color = getEl("color").value;
            bgcolor = getEl("background-color").value;
            setInterval("checkColours()", 5000);
        }

    </script>
    <script src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>

</head>
<body>

<h3><fmt:message key="admin.admin.manageCodeStyles"/></h3>

<div class="container-fluid d-flex flex-wrap align-items-center gap-2">

    <%
        String success = (String) request.getAttribute("success");
        if ("true".equalsIgnoreCase(success)) {
    %>
    <div class="alert alert-success">
        <button type="button" class="btn-close" data-bs-dismiss="alert" aria-label="Close"></button>
        <strong><fmt:message key="admin.manageCodeStyles.success"/></strong> <fmt:message key="admin.manageCodeStyles.sucess"/>
    </div>
    <%
        }
    %>

    <form action="${pageContext.request.contextPath}/admin/manageCSSStyles" method="post" accept-charset="UTF-8">
        <input type="hidden" id="method" name="method" value="save"/>

        <div class="row card card-body bg-body-tertiary"><!--select existing styles-->

            <fmt:message key="admin.manageCodeStyles.CurrentStyles"/><br/>

            <select name="selectedStyle" id="style">
                <option value="-1"><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                <c:forEach items="${styles}" var="style">
                    <option value="${carlos:forHtmlAttribute(style.style)}">${carlos:forHtml(style.name)}</option>
                </c:forEach>
            </select>

            <input class="btn btn-secondary" type="button" onclick="edit();return false;"
                   value="<fmt:message key="admin.manageCodeStyles.Edit"/>"/>
            <input type="submit" name="submit" value="<fmt:message key="admin.manageCodeStyles.Delete"/>" class="btn btn-secondary" onclick="return deleteStyle();"/>


        </div>
        <!--select existing styles-->


        <div class="row">

            <fmt:message key="admin.manageCodeStyles.StyleName"/><br>
            <input type="text" id="styleName" name="styleName"/>
            <!--<br><br>
<small><fmt:message key="admin.manageCodeStyles.Instructions"/></small>-->

        </div>

        <div class="row">
            <div class="col-md-4">
                <fmt:message key="admin.manageCodeStyles.FontSize"/><br>
                <select id="font-size" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="xx-small"><fmt:message key="admin.manageCodeStyles.xxSmall"/></option>
                    <option value="x-small"><fmt:message key="admin.manageCodeStyles.xSmall"/></option>
                    <option value="medium"><fmt:message key="admin.manageCodeStyles.medium"/></option>
                    <option value="large"><fmt:message key="admin.manageCodeStyles.large"/></option>
                    <option value="x-large"><fmt:message key="admin.manageCodeStyles.xLarge"/></option>
                    <option value="xx-large"><fmt:message key="admin.manageCodeStyles.xxLarge"/></option>
                </select>
                <br>

                <fmt:message key="admin.manageCodeStyles.FontStyle"/><br>
                <select id="font-style" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="italic"><fmt:message key="admin.manageCodeStyles.italic"/></option>
                    <option value="oblique"><fmt:message key="admin.manageCodeStyles.oblique"/></option>
                </select>
                <br>

                <fmt:message key="admin.manageCodeStyles.FontVariant"/><br>
                <select id="font-variant" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="small-caps"><fmt:message key="admin.manageCodeStyles.smallCaps"/></option>
                </select>
                <br>

                <fmt:message key="admin.manageCodeStyles.FontWeight"/><br>
                <select id="font-weight" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="bold"><fmt:message key="admin.manageCodeStyles.bold"/></option>
                    <option value="bolder"><fmt:message key="admin.manageCodeStyles.bolder"/></option>
                    <option value="lighter"><fmt:message key="admin.manageCodeStyles.lighter"/></option>
                </select>
                <br/>

                <fmt:message key="admin.manageCodeStyles.TextDecoration"/><br>
                <select id="text-decoration" onchange="addStyle(this.id, this.options[this.selectedIndex]);">
                    <option value=""><fmt:message key="admin.manageCodeStyles.NoneSelected"/></option>
                    <option value="underline"><fmt:message key="admin.manageCodeStyles.underline"/></option>
                    <option value="overline"><fmt:message key="admin.manageCodeStyles.overline"/></option>
                    <option value="line-through"><fmt:message key="admin.manageCodeStyles.lineThrough"/></option>
                </select>
                <br/>

                <fmt:message key="admin.manageCodeStyles.TextColour"/><br>
                <a href="javascript:TCP.popup(document.forms[0].elements['color']);"><img width="15" height="13"
                                                                                          border="0"
                                                                                          src="<%= request.getContextPath() %>/images/sel.gif"></a>
                <input id="color" type="text" size="7" onchange="checkColours();"/>
                <br>

                <fmt:message key="admin.manageCodeStyles.BackgroundColour"/><br>
                <a href="javascript:TCP.popup(document.forms[0].elements['background-color'])"><img width="15"
                                                                                                    height="13"
                                                                                                    border="0"
                                                                                                    src="<%= request.getContextPath() %>/images/sel.gif"></a>
                <input id="background-color" type="text" size="7" onchange="checkColours();"/>
                <br>


            </div><!--span4-->


            <div class="col-md-4">
                <input type="hidden" id="editStyle" name="editStyle"/>

                <fmt:message key="admin.manageCodeStyles.StyleText"/> <small><fmt:message key="admin.manageCodeStyles.ManualEnter"/><input type="checkbox"
                                                                     onclick="enableEdit(this);"></small><br/>
                <textarea rows="8" class="form-control" readonly="true" id="styleText" name="styleText"></textarea>
                <input class="btn btn-secondary" id="apply-btn" type="button"
                       value="<fmt:message key="admin.manageCodeStyles.Apply"/>" onclick="applyStyle();return false;"
                       style="display:none"/>

                <br><br>

                <fmt:message key="admin.manageCodeStyles.SampleText"/><br>
                <span id="example"><fmt:message key="admin.manageCodeStyles.Example"/></span>

            </div><!--span6-->
        </div>
        <!-- row -->


        <div class="col-md-10" style="text-align:right;">
            <hr>
            <input class="btn btn-lg" type="button" value="<fmt:message key="admin.manageCodeStyles.Clear"/>"
                   onclick="reinit();return false;"/>
            <input type="submit" name="submit" value="<fmt:message key="admin.manageCodeStyles.Save"/>" class="btn btn-lg btn-primary" onclick="return checkfields();" />
        </div>

    </form>
</div>


</body>

</html>
