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
<%@ page import="ca.openosp.openo.utility.LoggedInInfo" %>
<%@ page import="ca.openosp.openo.commn.model.UserProperty" %>
<%@ page import="ca.openosp.openo.commn.dao.UserPropertyDAO" %>
<%@ page import="ca.openosp.OscarProperties" %>
<%@ page import="ca.openosp.openo.utility.SpringUtils" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.*" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="http://java.sun.com/jsp/jstl/fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%@ taglib prefix="csrf" uri="http://www.owasp.org/index.php/Category:OWASP_CSRFGuard_Project/Owasp.CsrfGuard.tld" %>

<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");

    UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);

    Properties oscarVariables = OscarProperties.getInstance();

    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String curUser_no = (String) session.getAttribute("user");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
    String prov = (oscarVariables.getProperty("billregion", "")).trim().toUpperCase();

    String resourcebaseurl = oscarVariables.getProperty("resource_base_url");

    UserProperty rbu = userPropertyDao.getProp("resource_baseurl");
    if (rbu != null) {
        resourcebaseurl = rbu.getValue();
    }

    String resourcehelpHtml = "";
    UserProperty rbuHtml = userPropertyDao.getProp("resource_helpHtml");
    if (rbuHtml != null) {
        resourcehelpHtml = rbuHtml.getValue();
    }

    GregorianCalendar cal = new GregorianCalendar();
    int curYear = cal.get(Calendar.YEAR);
    int curMonth = (cal.get(Calendar.MONTH) + 1);
    int curDay = cal.get(Calendar.DAY_OF_MONTH);
%>

<!doctype html>
<html lang="en">

<head>
    <title><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.page.title"/></title>
    <link href="<%=request.getContextPath() %>/css/bootstrap.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/css/datepicker.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/css/DT_bootstrap.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/css/bootstrap-responsive.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/font-awesome.min.css">


    <style>
        body {
            background-color: #fff;
        }

        sup {
            color: #000;
            font-weight: bold;
        }

        #main-wrapper {
            margin-top: 70px;
        }

        div.navbar div.dropdown:hover ul.dropdown-menu {
            display: block;
	margin: 0px;
        }

        .navbar .dropdown-menu {
            margin-top: 0px;
        }

        .navbar .nav > li > a {
            padding: 10px 10px;
        }

        #caret-loggedIn {
            vertical-align: top;
            opacity: 0.3;
            margin-top: 18px;
        }

        .accordion-heading {
            background-color: #fff;
            border-top: 1px solid #e5e5e5;
        }

        .accordion-heading:first-of-type {
            border: none;
        }

        .accordion-heading:hover {
            background-color: #e6e6e6;
        }

        .accordion-heading a:hover {
            text-decoration: none;
        }

        .icon-chevron-right {
            opacity: 0.3;
        }

        .accordion-heading a:hover > .icon-chevron-right {
            opacity: 1;
        }

        .selected-heading {
            background-color: #e6e6e6;
        }

        .accordion-inner ul li a {
            color: #424242;
            padding: 2px 0px;
            margin: auto 5px;
            display: block;
            width: 100%;
        }

        .accordion-inner a:hover {
            text-decoration: none;
            color: #000;
            cursor: pointer;
            cursor: hand;
        }

        .accordion-inner ul li:nth-child(even) {
            background-color: white;
        }

        .accordion-inner ul li:nth-child(odd) {
            background-color: whitesmoke;
            border-top: 1px solid #dddddd;
            border-bottom: 1px solid #dddddd;
        }

        #side a {
            color: #333;
            text-decoration: none;
            outline: 0;
        }

        #side a:hover {
            color: #0088cc;
        }

        .icon-chevron-right {
            float: right;
        }

        #adminNav {
            -webkit-box-shadow: 0 1px 4px rgba(0, 0, 0, 0.065);
            -moz-box-shadow: 0 1px 4px rgba(0, 0, 0, 0.065);
            box-shadow: 0 1px 4px rgba(0, 0, 0, 0.065);
        }


        #adminNav ul {
            padding: 0px;
            margin: 0px;
            list-style-type: none;
        }

        label.valid {
            width: 24px;
            height: 24px;
            background: url(<%=request.getContextPath() %>/images/icons/valid.png) center center no-repeat;
            display: inline-block;
            text-indent: -9999px;
        }

        label.error {
            font-weight: bold;
            color: red;
            padding: 2px 8px;
            margin-top: 2px;
            font-size: 13px;
            display: inline;
        }

        .table tbody tr:hover td, .table tbody tr:hover th {
            background-color: #FFFFAA;
        }

        .quick-links {
            display: inline-block;
            width: 160px;
            height: 100px;
            margin: 10px;
            text-align: center;
            vertical-align: top;
        }

        .quick-links a {
            text-decoration: none;
            color: #333;
        }

        .quick-links a:hover {
            color: #0088cc;
        }

        .used-heading {
            padding-bottom: 0px;
            margin-bottom: 0px;
        }

        /*remove font awesomes 'link' response to icons*/
        i[class*='icon-'] {
            color: #333
        }

        i[class*='icon-']:before {
            display: inline-block;
            text-decoration: none;
            cursor: pointer;
            cursor: hand;
        }

        i[class*='icon-']:hover {
            color: #0088cc;
        }

        .icon-trash:hover {
            color: #bd362f !important;
        }

        .visible-print {
            display: none !important;
        }

        .dynamic-content, .dynamic-iframe-content {
            position: relative;
            overflow: hidden;
        }

        /* Allow tooltips to overflow the container (applied via JS for browser compatibility) */
        .dynamic-content.has-tooltip {
            overflow: visible;
        }

        .dynamic-iframe-content {
            padding-top: 80%;
        }

        iframe#myFrame {
            position: absolute;
            top: 0;
            left: 0;
            bottom: 0;
            right: 0;
            width: 100%;
            height: 100%;
        }

        @media print {
            .visible-print {
                display: inherit !important;
            }

            .hidden-print {
                display: none !important;
            }

            /*this is so the link locatons don't display*/
            a:link:after, a:visited:after {
                content: "";
            }
        }
    </style>

    <script type="text/javascript" src="<%=request.getContextPath()%>/js/jquery-1.9.1.js"></script>

    <oscar:customInterface section="main"/> <!--needs to be in header-->
</head>

<body>
<div class="container-fluid">
    <div class="row-fluid hidden-print" style="text-align:right">
        <i class=" icon-question-sign"></i>
        <%if (resourcehelpHtml == "") { %>
        <a href="#" ONCLICK="popupPage(600,750,'<%=resourcebaseurl%>');return false;" title=""
           onmouseover="window.status='';return true">Help</a>
        <%} else {%>
        <div id="help-link">
            <a href="javascript:void(0)"
               onclick="document.getElementById('helpHtml').style.display='block';document.getElementById('helpHtml').style.right='0px';"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.help"/></a>

            <div id="helpHtml">
                <div class="help-title">Help</div>

                <div class="help-body">

                    <%=resourcehelpHtml%>
                </div>
                <a href="javascript:void(0)" class="help-close"
                   onclick="document.getElementById('helpHtml').style.right='-280px';document.getElementById('helpHtml').style.display='none'">(X)</a>
            </div>

        </div>
        <%}%>

        <i class=" icon-info-sign" style="margin-left:10px;"></i> <a href="javascript:void(0)"
                                                                     onClick="window.open('<%=request.getContextPath()%>/oscarEncounter/About.jsp','About OSCAR','scrollbars=1,resizable=1,width=800,height=600,left=0,top=0')"><fmt:setBundle basename="oscarResources"/><fmt:message key="global.about"/></a></div>

    <div class="row-fluid">


        <%@ include file="leftNav.jspf" %>


        <div class="span9 dynamic-content" id="dynamic-content">

            <!-- ****DYNAMIC CONTENT**** -->
            <%
                String showMenu = request.getParameter("show");
                String loadPage = request.getParameter("load");

                if (showMenu == null && loadPage == null) {
            %>
            <div class="row-fluid">
                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.unlockAccount" rights="r">
                    <div class="well quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/unLock.jsp"><i
                                class="icon-user icon-4x"></i>
                            <h5><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.unlockAcct"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin,_admin.provider"
                                   rights="r" reverse="<%=false%>">
                    <div class="well quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/provideraddarecordhtm.jsp"><i
                                class="icon-user icon-4x"></i>
                            <h5><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnAddProvider"/></h5></a>
                    </div>

                    <div class="well quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/securityaddarecord.jsp"><i
                                class="icon-user icon-4x"></i>
                            <h5><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnAddLogin"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.eform" rights="r"
                                   reverse="<%=false%>">
                    <div class="well quick-links">
                        <a href="${ctx}/eform/efmformmanager.jsp" class="contentLink defaultForms"><i
                                class="icon-file icon-4x"></i>
                            <h5><fmt:setBundle basename="oscarResources"/><fmt:message key="eform.showmyform.msgManageEFrm"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.schedule" rights="r"
                                   reverse="<%=false%>">
                    <div class="well quick-links">
                        <a href="javascript:void(0);" class="xlink" rel="${ctx}/schedule/scheduletemplatesetting.jsp"
                           title="<fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.scheduleSettingTitle"/>"><i
                                class="icon-calendar icon-4x"></i>
                            <h5><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.scheduleSetting"/></h5></a>
                    </div>

                    <div class="well quick-links">
                        <a href="javascript:void(0);" class="xlink" rel="${ctx}/admin/admindisplaymygroup.jsp"><i
                                class="icon-calendar icon-4x"></i>
                            <h5><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnSearchGroupNoRecords"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.encounter" rights="r"
                                   reverse="<%=false%>">
                    <div class="well quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/providertemplate.jsp"><i
                                class="icon-medkit icon-4x"></i>
                            <h5><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.btnInsertTemplate"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r"
                                   reverse="<%=false%>">
                    <div class="well quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/providerPrivilege.jsp"><i
                                class="icon-wrench icon-4x"></i>
                            <h5><fmt:setBundle basename="oscarResources"/><fmt:message key="admin.admin.assignRightsObject"/></h5></a>
                    </div>
                </security:oscarSec>
            </div>

            <%}%>

            <!-- ****DYNAMIC CONTENT END**** -->

        </div>

    </div>
</div>

<!-- jquery-1.9.1.js - in nonPatientContextHeader.jspf -->
<script type="text/javascript" src="<%=request.getContextPath() %>/js/bootstrap.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/js/bootstrap-datepicker.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery.validate.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/js/jquery.dataTables.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/js/DT_bootstrap.js"></script>


<script type="text/javascript">
    $(document).ready(function () {
        $("a.contentLink").click(function (e) {
            e.preventDefault();
            $("#dynamic-content").removeClass("dynamic-iframe-content");
            $("#dynamic-content").load($(this).attr("href"),
                function (response, status, xhr) {
                    if (status == "error") {
                        var msg = "Sorry but there was an error: ";
                        $("#dynamic-content").html(msg + xhr.status + " " + xhr.statusText);
                    }

                    // Re-initialize Bootstrap dropdowns for dynamically loaded content
                    $("#dynamic-content .dropdown-toggle").dropdown();

                    // Toggle overflow for pages with CSS tooltips (for browser compatibility)
                    if ($("#dynamic-content .css-tooltip").length > 0) {
                        $("#dynamic-content").addClass("has-tooltip");
                    } else {
                        $("#dynamic-content").removeClass("has-tooltip");
                    }

                    $("html, body").animate({scrollTop: 0}, "slow");
                });
        });

    });

    function registerFormSubmit(formId, divId) {
        let thisForm = $('#' + formId);
        $(thisForm.submit(function () {
            if (thisForm.valid != null && !thisForm.valid()) {
                return false;
            }
            // gather the form data
            let data = $(this).serialize();
            // get csrf token and place it in the header for a ajax request
            let headerToken = null;

            thisForm.serializeArray().forEach(function (field) {
                if (field.name === '<csrf:tokenname/>' && headerToken === null) {
                    headerToken = {'<csrf:tokenname/>': field.value};
                }
            });
            // post data
            $.ajax({
                url: thisForm.attr('action'),
                type: thisForm.attr('method'),
                data: data,
                headers: headerToken,
                success: function (returnData) {
                    // insert returned html
                    $('#' + divId).html(returnData)
                }
            });

            return false; // stops browser from doing default submit process
        }));
    }

    function submitForm(formId, divId) {
        // gather the form data
        var data = $(this).serialize();
        // post data
        $.post($('#' + formId).attr('action'), data, function (returnData) {
            // insert returned html
            $('#' + divId).html(returnData)
        })
    }

    function parseDate(date, format, separator) {
        if (!date) {
            date = '';
        }
        var parts = date.split(separator), formatParts = format.split(separator),
            date1 = new Date(),
            val;
        date1.setHours(0);
        date1.setMinutes(0);
        date1.setSeconds(0);
        date1.setMilliseconds(0);
        if (parts.length === formatParts.length) {
            var year = date1.getFullYear(), day = date1.getDate(), month = date1.getMonth();
            for (var i = 0, cnt = formatParts.length; i < cnt; i++) {
                val = parseInt(parts[i], 10) || 1;
                switch (formatParts[i]) {
                    case 'dd':
                    case 'd':
                        day = val;
                        date1.setDate(val);
                        break;
                    case 'mm':
                    case 'm':
                        month = val - 1;
                        date1.setMonth(val - 1);
                        break;
                    case 'yy':
                        year = 2000 + val;
                        date1.setFullYear(2000 + val);
                        break;
                    case 'yyyy':
                        year = val;
                        date1.setFullYear(val);
                        break;
                    default:
                        if (!val)
                            return null;
                }
            }
            date1 = new Date(year, month, day, 0, 0, 0);
            return date1;
        }
        return null;
    }

    function validDate(value, format, separator) {
        try {
            var d = parseDate(value, format, separator);

            return d != null;
        } catch (e) {
            return false;
        }
    }

    /* function resizeIframe(newHgt)
    {
        $('#myFrame').height((parseInt(newHgt)+75)+'px');
        $("html, body").animate({ scrollTop: 0 }, "slow");
    } */

    $(document).ready(function () {

        // set validation defaults
        jQuery.validator.setDefaults({
            debug: true,
            highlight: function (element) {
                $(element).closest('.control-group').removeClass('success').addClass('error');
            },
            success: function (element) {
                element.closest('.control-group').removeClass('error').addClass('success');
            }
        });


        jQuery.validator.addMethod("oscarDate", function (value, element) {
                return validDate(value, "yyyy-mm-dd", "-");
            },
            "Date format should be yyyy-mm-dd.");

        jQuery.validator.addMethod("oscarMonth", function (value, element) {
                return validDate(value, "mm/yyyy", "/");
            },
            "Date format should be mm/yyyy.");


        // initialiaze toolstips
        $('[rel=tooltip]').tooltip();
    });

    function popupPage(vheight, vwidth, varpage) {
        var page = "" + varpage;
        windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
        var popup = window.open(page, "<fmt:setBundle basename="oscarResources"/><fmt:message key="provider.appointmentProviderAdminDay.apptProvider"/>", windowprops);
        if (popup != null) {
            if (popup.opener == null) {
                popup.opener = self;
            }
            popup.focus();
        }
    }

</script>

</body>
</html>
