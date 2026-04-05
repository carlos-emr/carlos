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
 * CARLOS EMR Administration Dashboard
 *
 * <p><strong>Purpose:</strong> Main administration interface providing a dashboard with
 * quick-access cards for common administrative tasks and a collapsible Bootstrap 5.3
 * accordion left navigation covering all administrative modules.</p>
 *
 * <p><strong>Features:</strong></p>
 * <ul>
 *   <li>Quick-access cards for frequently used admin functions (unlock accounts,
 *       add provider, manage eforms, schedule settings, assign rights)</li>
 *   <li>Bootstrap 5.3 accordion left navigation with 16 grouped sections</li>
 *   <li>Dynamic content pane that loads sub-pages without a full page reload</li>
 *   <li>Role-based security filtering via the oscarSec tag on each card and nav item</li>
 *   <li>Configurable help panel and about dialog links</li>
 *   <li>Province-specific billing module visibility based on {@code billregion} property</li>
 * </ul>
 *
 * <p><strong>Security:</strong> Requires an authenticated session; redirects to logout if
 * {@code userrole} is absent. Individual cards and nav items are gated by
 * {@code _admin.*} security objects via the oscarSec tag.</p>
 *
 * <p><strong>Parameters:</strong></p>
 * <ul>
 *   <li>{@code show} - optional: left nav section to expand on load</li>
 *   <li>{@code load} - optional: URL to load into the dynamic content pane on page load</li>
 * </ul>
 *
 * @since 2026-03-21 (Bootstrap 5.3 modernization, CARLOS EMR). Original administration dashboard
 *        was introduced in legacy OSCAR EMR prior to the CARLOS fork; refer to git history
 *        for the earliest introduction date.
 */
--%>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.UserProperty" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO" %>
<%@ page import="io.github.carlos_emr.CarlosProperties" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="java.net.URLEncoder" %>
<%@ page import="java.util.*" %>
<%@ page import="org.owasp.encoder.Encode" %>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>

<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logout.jsp");

    UserPropertyDAO userPropertyDao = SpringUtils.getBean(UserPropertyDAO.class);

    Properties oscarVariables = CarlosProperties.getInstance();

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

<fmt:setBundle basename="oscarResources"/>
<!doctype html>
<html lang="en">

<head>
    <title><fmt:message key="admin.admin.page.title"/></title>
    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.3/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.4/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">


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

        .selected-heading {
            background-color: #e6e6e6;
        }

        #side a {
            color: #333;
            text-decoration: none;
            outline: 0;
        }

        #side a:hover {
            color: #0088cc;
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
            display: inline-flex;
            flex-direction: column;
            align-items: center;
            justify-content: center;
            width: 160px;
            min-height: 100px;
            margin: 10px;
            text-align: center;
            vertical-align: top;
            overflow: visible;
            word-wrap: break-word;
            overflow-wrap: break-word;
        }

        .quick-links a {
            text-decoration: none;
            color: #333;
            word-wrap: break-word;
            overflow-wrap: break-word;
            width: 100%;
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

        .fa-solid.fa-trash:hover {
            color: #bd362f !important;
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
            /*this is so the link locatons don't display*/
            a:link:after, a:visited:after {
                content: "";
            }
        }
    </style>

    <script type="text/javascript" src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
    <script src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>

    <oscar:customInterface section="main"/> <!--needs to be in header-->
</head>

<body>
<div class="container-fluid">
    <div class="d-print-none d-flex justify-content-end align-items-center gap-3">
        <span class="d-flex align-items-center gap-1">
            <i class="fa-solid fa-circle-question"></i>
            <%if (resourcehelpHtml.isEmpty()) { %>
            <a href="#" ONCLICK="popupPage(600,750,'<%=Encode.forJavaScriptAttribute(resourcebaseurl)%>');return false;" title=""
               onmouseover="window.status='';return true"><fmt:message key="global.help"/></a>
            <%} else {%>
            <div id="help-link">
                <a href="javascript:void(0)"
                   onclick="document.getElementById('helpHtml').style.display='block';document.getElementById('helpHtml').style.right='0px';"><fmt:message key="global.help"/></a>

                <div id="helpHtml">
                    <div class="help-title"><fmt:message key="global.help"/></div>

                    <div class="help-body">

                        <%=resourcehelpHtml%>
                    </div>
                    <a href="javascript:void(0)" class="help-close"
                       onclick="document.getElementById('helpHtml').style.right='-280px';document.getElementById('helpHtml').style.display='none'"><fmt:message key="global.close"/></a>
                </div>

            </div>
            <%}%>
        </span>
        <span class="d-flex align-items-center gap-1">
            <i class="fa-solid fa-circle-info"></i>
            <a href="javascript:void(0)"
               onClick="window.open('<%=request.getContextPath()%>/encounter/About.jsp','About CARLOS EMR','scrollbars=1,resizable=1,width=800,height=600,left=0,top=0')"><fmt:message key="global.about"/></a>
        </span>
    </div>

    <div class="row">


        <%@ include file="leftNav.jspf" %>


        <div class="col-md-9 dynamic-content" id="dynamic-content">

            <!-- ****DYNAMIC CONTENT**** -->
            <%
                String showMenu = request.getParameter("show");
                String loadPage = request.getParameter("load");

                if (showMenu == null && loadPage == null) {
            %>
            <div class="row">
                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.unlockAccount" rights="r">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/unLock.jsp"><i
                                class="fa-solid fa-user fa-4x"></i>
                            <h5><fmt:message key="admin.admin.unlockAcct"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin,_admin.provider"
                                   rights="r" reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/provideraddarecordhtm.jsp"><i
                                class="fa-solid fa-user fa-4x"></i>
                            <h5><fmt:message key="admin.admin.btnAddProvider"/></h5></a>
                    </div>

                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/securityaddarecord.jsp"><i
                                class="fa-solid fa-user fa-4x"></i>
                            <h5><fmt:message key="admin.admin.btnAddLogin"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.eform" rights="r"
                                   reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href="${ctx}/eform/efmformmanager.jsp" class="contentLink defaultForms"><i
                                class="fa-solid fa-file fa-4x"></i>
                            <h5><fmt:message key="eform.showmyform.msgManageEFrm"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.schedule" rights="r"
                                   reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href="javascript:void(0);" class="xlink" rel="${ctx}/schedule/TemplateSetting.do"
                           title="<fmt:message key="admin.admin.scheduleSettingTitle"/>"><i
                                class="fa-solid fa-calendar fa-4x"></i>
                            <h5><fmt:message key="admin.admin.scheduleSetting"/></h5></a>
                    </div>

                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href="javascript:void(0);" class="xlink" rel="${ctx}/admin/admindisplaymygroup.jsp"><i
                                class="fa-solid fa-calendar fa-4x"></i>
                            <h5><fmt:message key="admin.admin.btnSearchGroupNoRecords"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.encounter" rights="r"
                                   reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/providertemplate.jsp"><i
                                class="fa-solid fa-suitcase-medical fa-4x"></i>
                            <h5><fmt:message key="admin.admin.btnInsertTemplate"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r"
                                   reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/providerPrivilege.jsp"><i
                                class="fa-solid fa-wrench fa-4x"></i>
                            <h5><fmt:message key="admin.admin.assignRightsObject"/></h5></a>
                    </div>
                </security:oscarSec>
            </div>

            <%}%>

            <!-- ****DYNAMIC CONTENT END**** -->

        </div>

    </div>
</div>

<!-- jQuery loaded above -->
<script type="text/javascript" src="<%=request.getContextPath() %>/library/bootstrap/5.3.3/js/bootstrap.bundle.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery.validate.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.4/js/jquery.dataTables.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.4/js/dataTables.bootstrap5.min.js"></script>


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
                    document.querySelectorAll('#dynamic-content .dropdown-toggle').forEach(function(el) { new bootstrap.Dropdown(el); });

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
            // post data (CSRFGuard 4.5 auto-injects CSRF token into XHR headers)
            $.ajax({
                url: thisForm.attr('action'),
                type: thisForm.attr('method'),
                data: data,
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
                $(element).closest('.mb-3').removeClass('success').addClass('error');
            },
            success: function (element) {
                element.closest('.mb-3').removeClass('error').addClass('success');
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
        document.querySelectorAll('[data-bs-toggle="tooltip"]').forEach(function(el) { new bootstrap.Tooltip(el); });
    });

    function popupPage(vheight, vwidth, varpage) {
        var page = "" + varpage;
        windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=50,screenY=50,top=0,left=0";
        var popup = window.open(page, "<fmt:message key="provider.appointmentProviderAdminDay.apptProvider"/>", windowprops);
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
