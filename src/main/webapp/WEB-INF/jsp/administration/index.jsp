<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
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

    CARLOS EMR Project
    https://github.com/carlos-emr/carlos
--%>
<%--
  Page role: Renders `index.jsp` for the administration navigation area.
  Keep request setup in the paired action and use CARLOS encoding helpers
  for dynamic output rendered by the page.
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
<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<%@ taglib uri="/WEB-INF/caisi-tag.tld" prefix="caisi" %>
<%@ taglib uri="/WEB-INF/oscar-tag.tld" prefix="oscar" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>

<%
    if (session.getAttribute("userrole") == null) response.sendRedirect(request.getContextPath() + "/logoutPage");

    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    String userfirstname = (String) session.getAttribute("userfirstname");
    String userlastname = (String) session.getAttribute("userlastname");
    boolean showScheduleNav = "1".equals(request.getParameter("scheduleNav"));
%>

<!doctype html>
<html lang="${pageContext.request.locale.language}">

<head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
    <title><fmt:message key="admin.admin.page.title"/></title>
    <link href="<%=request.getContextPath() %>/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.css" rel="stylesheet" type="text/css">
    <link href="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.11/css/dataTables.bootstrap5.min.css" rel="stylesheet" type="text/css">
    <link rel="stylesheet" href="<%=request.getContextPath() %>/css/fontawesome-all.min.css">
    <% if (showScheduleNav) { %>
    <link rel="stylesheet" href="<%=request.getContextPath()%>/css/topnav.css">
    <% } %>


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
<% if (showScheduleNav) { %>
    <jsp:include page="/WEB-INF/jsp/provider/mainMenu.jsp"/>
<% } %>
<div class="container-fluid">
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
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/UnLock"><i
                                class="fa-solid fa-user fa-4x"></i>
                            <h5><fmt:message key="admin.admin.unlockAcct"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin,_admin.provider"
                                   rights="r" reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/ViewProviderAddARecordHtm"><i
                                class="fa-solid fa-user fa-4x"></i>
                            <h5><fmt:message key="admin.admin.btnAddProvider"/></h5></a>
                    </div>

                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/ViewSecurityAddARecord"><i
                                class="fa-solid fa-user fa-4x"></i>
                            <h5><fmt:message key="admin.admin.btnAddLogin"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.eform" rights="r"
                                   reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href="${ctx}/eform/efmformmanager${param.scheduleNav eq '1' ? '?scheduleNav=1' : ''}" class="contentLink defaultForms"><i
                                class="fa-solid fa-file fa-4x"></i>
                            <h5><fmt:message key="eform.showmyform.msgManageEFrm"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.schedule" rights="r"
                                   reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href="javascript:void(0);" class="xlink" rel="${ctx}/schedule/TemplateSetting"
                           title="<fmt:message key="admin.admin.scheduleSettingTitle"/>"><i
                                class="fa-solid fa-calendar fa-4x"></i>
                            <h5><fmt:message key="admin.admin.scheduleSetting"/></h5></a>
                    </div>

                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href="javascript:void(0);" class="xlink" rel="${ctx}/admin/ViewAdminDisplayMyGroup"><i
                                class="fa-solid fa-calendar fa-4x"></i>
                            <h5><fmt:message key="admin.admin.btnSearchGroupNoRecords"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.encounter" rights="r"
                                   reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/ProviderTemplate"><i
                                class="fa-solid fa-suitcase-medical fa-4x"></i>
                            <h5><fmt:message key="admin.admin.btnInsertTemplate"/></h5></a>
                    </div>
                </security:oscarSec>

                <security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.userAdmin" rights="r"
                                   reverse="<%=false%>">
                    <div class="card card-body bg-body-tertiary quick-links">
                        <a href='javascript:void(0);' class="xlink" rel="${ctx}/admin/ProviderPrivilege"><i
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
<script type="text/javascript" src="<%=request.getContextPath() %>/library/bootstrap/5.3.8/js/bootstrap.bundle.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/library/flatpickr/flatpickr.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/library/jquery/jquery.validate-1.21.0.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.11/js/jquery.dataTables.min.js"></script>
<script type="text/javascript" src="<%=request.getContextPath() %>/library/DataTables/DataTables-1.13.11/js/dataTables.bootstrap5.min.js"></script>


<script type="text/javascript">
    $(document).ready(function () {
        $("a.contentLink").click(function (e) {
            var href = $(this).attr("href");
            e.preventDefault();
            // Only AJAX-load a real URL. Several controls reachable from this
            // shell borrow .contentLink purely for styling and do their work in
            // their own onclick (leftNav's caisi entries are
            // href="javascript:void(0);", and one carries no href at all).
            // Loading that literal value cannot succeed: jQuery completes with
            // status 0 / statusText "error" and the handler below paints
            // "Sorry but there was an error: 0 error" over the page — over the
            // iframe the element's own handler just installed — while the
            // control itself worked fine. A missing href reaches .load() as
            // undefined and throws instead. Same defect, and same fix, as
            // eform/efmFooter.jspf.
            if (!href || href === "#" || /^\s*javascript:/i.test(href)) {
                return;
            }
            // AJAX-loaded content flows in the shell's own scroll; drop any
            // height the previous framed section left on the container.
            resetFrameSizing();
            $("#dynamic-content").removeClass("dynamic-iframe-content");
            $("#dynamic-content").load(href,
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
            // A multipart form must go out as FormData: $(form).serialize()
            // silently DROPS file inputs, so hijacking a multipart form with a
            // serialized body posted it without its file — the eForm import
            // panel, for one, always arrived fileless inside this shell while
            // the same form worked opened standalone. (This is also why the
            // eForm editor's save changes encoding depending on how it was
            // reached; the WAF exclusions cover both shapes.)
            let isMultipart = (thisForm.attr('enctype') || '').toLowerCase() === 'multipart/form-data';
            let ajaxOptions = {
                url: thisForm.attr('action'),
                type: thisForm.attr('method'),
                success: function (returnData) {
                    // insert returned html
                    $('#' + divId).html(returnData)
                }
            };
            if (isMultipart) {
                ajaxOptions.data = new FormData(this);
                ajaxOptions.processData = false;
                ajaxOptions.contentType = false;
            } else {
                // gather the form data (CSRFGuard 4.5 auto-injects CSRF token into XHR headers)
                ajaxOptions.data = $(this).serialize();
            }
            $.ajax(ajaxOptions);

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

    // The admin shell hosts most section pages inside #myFrame (see the .xlink
    // handler in leftNav.jspf). Two things have to happen when the framed page
    // changes: the frame has to be tall enough for the new document, and the
    // SHELL has to scroll back to the top so the reader is looking at the top of
    // it. This function is the hook a framed page calls to ask for both, passing
    // its own content height; scrollFramedContentIntoView() below is the same
    // behaviour driven from the shell for the many legacy pages that never call
    // in.
    //
    // It was commented out during the Bootstrap 5 rework, which left the shell
    // parked at whatever scroll offset the reader had used to reach a button
    // near the bottom of the frame. A multi-step wizard then looks broken: the
    // schedule week-setting "Next" posts, saves, and loads the next step, but the
    // reader is still looking at the middle of it and reports that "nothing
    // happens". The pages that DID call in got a hard
    // "parent.parent.resizeIframe is not a function" instead. Keep it defined.
    function resizeIframe(newHgt) {
        var frame = document.getElementById('myFrame');
        if (!frame) {
            // AJAX-loaded (non-framed) content also reaches this via a nested
            // page; there is nothing to size, but the scroll is still wanted.
            scrollShellToTop();
            return;
        }
        growFrameTo(parseInt(newHgt, 10));
        scrollShellToTop();
    }

    // Breathing room added on top of a framed document's own height, so the
    // grown frame does not sit flush against its content and re-introduce a
    // nested scrollbar from sub-pixel rounding.
    var FRAME_HEIGHT_MARGIN = 75;

    // Grow the frame (and the aspect-ratio box it lives in) to fit a framed
    // document `contentHeight` px tall. Only ever grows, and only when the
    // content genuinely does not fit.
    //
    // The margin is added ONLY when growth is needed, which is what keeps this
    // from ratcheting. A document shorter than its frame reports a scrollHeight
    // equal to the frame's own height — the viewport is its lower bound — so
    // adding the margin first and comparing afterwards would grow the frame by
    // FRAME_HEIGHT_MARGIN on EVERY in-frame navigation, accumulating blank
    // space without limit across a multi-step flow. Comparing the bare content
    // height first makes the fitting case a no-op, and one growth step is
    // enough: the next measurement equals the new frame height and stops.
    function growFrameTo(contentHeight) {
        var frame = document.getElementById('myFrame');
        var container = document.getElementById('dynamic-content');
        if (!frame || !isFinite(contentHeight) || contentHeight <= 0) {
            return;
        }
        if (contentHeight <= frame.getBoundingClientRect().height) {
            return;
        }
        var height = contentHeight + FRAME_HEIGHT_MARGIN;
        if (container) {
            // The .dynamic-iframe-content box is sized by `padding-top: 80%`, an
            // aspect-ratio hack with no relation to the content. Swap it for a
            // real height once the real height is known.
            container.style.paddingTop = '0';
            container.style.height = height + 'px';
        }
        frame.style.height = height + 'px';
    }

    // Undo anything growFrameTo() applied, so the next section starts from the
    // CSS box again instead of inheriting the previous page's height.
    function resetFrameSizing() {
        var container = document.getElementById('dynamic-content');
        if (container) {
            container.style.paddingTop = '';
            container.style.height = '';
        }
    }

    // .stop(true) first: the .xlink handler scrolls on click and this runs again
    // when the frame finishes loading, so without it the two animations queue and
    // the shell keeps animating after it has already arrived. Clearing the queue
    // also means a reader who scrolls during the animation is not fought by a
    // stale one that is still ticking.
    function scrollShellToTop() {
        $("html, body").stop(true).animate({ scrollTop: 0 }, "slow");
    }

    // Called by the .xlink handler on every document the frame loads. Reads the
    // framed document's own height (same-origin — every section route is served
    // by this application) so a page taller than the aspect box is not clipped
    // behind a nested scrollbar, then puts the shell back at the top so the
    // reader sees the new page from its beginning. A page that also calls
    // resizeIframe() itself just asks for the same thing twice, which is
    // harmless: growFrameTo() is a no-op once the content fits.
    function scrollFramedContentIntoView(frame) {
        try {
            var doc = frame && frame.contentDocument;
            if (doc && doc.documentElement) {
                growFrameTo(doc.documentElement.scrollHeight);
                observeFramedContentHeight(frame, doc);
            }
        } catch (e) {
            // A cross-origin document cannot be measured; the CSS box still applies.
        }
        scrollShellToTop();
    }

    // Keep following the framed document's height after load.
    //
    // Measuring once at load is not enough for the pages this shell-side path
    // exists to cover: a section that renders a table from its own AJAX call,
    // expands an accordion, or loads images without declared dimensions is
    // taller a moment later, and would sit clipped behind the aspect box with a
    // nested scrollbar — the defect this is meant to remove. The legacy pages
    // escape that by calling resizeIframe() again themselves; a page that never
    // calls in has no second chance without this.
    //
    // It only grows the frame — it deliberately does NOT scroll. A reader who
    // opens a collapsed panel half way down a section has not asked to be sent
    // back to the top.
    function observeFramedContentHeight(frame, doc) {
        if (typeof ResizeObserver === 'undefined') {
            return;
        }
        // One observer per frame: each load replaces the document, and a stale
        // observer would keep measuring the previous one.
        if (frame.carlosContentObserver) {
            frame.carlosContentObserver.disconnect();
        }
        var observer = new ResizeObserver(function () {
            try {
                var current = frame.contentDocument;
                if (current && current.documentElement) {
                    growFrameTo(current.documentElement.scrollHeight);
                }
            } catch (e) {
                // Document went away or turned cross-origin mid-observation.
            }
        });
        // Observe BOTH roots. ResizeObserver reports an element's own box, not
        // the document's scrollHeight, so which of the two actually changes
        // depends on the framed page's CSS. Measured in Chromium: with the
        // default auto heights either one fires; with `html { height: 100% }`
        // and an auto body only <body> fires; with `body { height: 100% }`
        // neither does — such a page still has to call resizeIframe() itself.
        // Observing both costs one extra registration and covers a case a
        // single root misses.
        observer.observe(doc.documentElement);
        if (doc.body) {
            observer.observe(doc.body);
        }
        frame.carlosContentObserver = observer;
    }

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
