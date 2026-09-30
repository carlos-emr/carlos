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
    ViewConsultationRequests.jsp

    Purpose: Displays a paginated, filterable list of consultation requests for a
    provider or team, supporting date range search, team filtering, and completion
    status toggling.

    Features:
    - Bootstrap 5 responsive table with sortable columns
    - Filter by team, date range (referral or appointment date), and completion status
    - Filter by Consultant (type-ahead over specialists referenced by consult requests, served by
      encounter/consultation/searchConsultants) and by Provider (the patient's MRP). Both values
      come from request attributes resolved by EctViewConsultationRequests2Action, never from raw
      parameters: an unknown consultant or an out-of-scope provider is dropped on the server.
      Text typed in the Consultant box but not picked from the list is cleared before every
      submit, so it can never become a filter (issue #3976).
    - Clickable rows (mouse + keyboard) to open consultation detail popup
    - Overdue consultation highlighting based on user preferences
    - Bulk tickler creation for "Nothing Done" consultations older than one week
    - Multisite support with site-specific background colours
    - Native HTML5 date inputs replacing legacy calendar widget
    - OWASP-encoded URLs for all popup interactions

    @since CARLOS EMR 1.0 (modernized 2026 from legacy OSCAR layout)
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.ConsultationRequestDao" %>

<%@ page import="io.github.carlos_emr.carlos.encounter.pageUtil.*,java.text.*,java.util.*" %>
<%@ page import="java.sql.ResultSet" %>
<%@ page
        import="io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO, io.github.carlos_emr.carlos.commn.model.UserProperty, org.springframework.web.context.support.WebApplicationContextUtils" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>

<%@ page import="io.github.carlos_emr.carlos.commn.model.Site" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.SiteDao" %>

<%@ page import="io.github.carlos_emr.carlos.commn.model.ProviderData" %>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.ProviderDataDao" %>

<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctConsultationFormRequestUtil" %>
<%@ page import="io.github.carlos_emr.carlos.encounter.oscarConsultationRequest.pageUtil.EctViewConsultationRequestsUtil" %>
<%@ page import="io.github.carlos_emr.carlos.commn.IsPropertiesOn" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ page import="io.github.carlos_emr.carlos.consultation.dto.ConsultationListFilterDto" %>
<%@ page import="io.github.carlos_emr.carlos.consultation.dto.ConsultationMrpOptionDto" %>

<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>

<%
    String curProvider_no = (String) session.getAttribute("user");
    boolean showScheduleNav = "1".equals(request.getParameter("scheduleNav"));

    boolean isSiteAccessPrivacy = false;
    boolean isTeamAccessPrivacy = false;
    boolean bMultisites = IsPropertiesOn.isMultisitesEnable();
    List<String> mgrSite = new ArrayList<String>();

    ProviderDataDao providerDataDao = SpringUtils.getBean(ProviderDataDao.class);

    String strLimit = request.getParameter("limit");
    String strOffset = request.getParameter("offset");

    Integer limit = ConsultationRequestDao.DEFAULT_CONSULT_REQUEST_RESULTS_LIMIT;
    Integer offset = 0;

    try {
        offset = Integer.parseInt(strOffset);
    } catch (NumberFormatException e) {
        offset = 0;
    }

    try {
        limit = Integer.parseInt(strLimit);
    } catch (NumberFormatException e) {
        limit = 100;
    }
%>
<security:oscarSec objectName="_site_access_privacy" roleName="<%=roleName$%>" rights="r"
                   reverse="false"><%isSiteAccessPrivacy = true; %></security:oscarSec>
<security:oscarSec objectName="_team_access_privacy" roleName="<%=roleName$%>" rights="r"
                   reverse="false"><%isTeamAccessPrivacy = true; %></security:oscarSec>

<%
    List<ProviderData> pdList = null;
    HashMap<String, String> providerMap = new HashMap<String, String>();

    // Site access privacy is a MULTISITE feature: providersite rows only exist
    // when multisite mode is on, so it is applied only then, mirroring the
    // schedule (appointmentprovideradminday.jsp). The Flyway seed grants the
    // admin role _site_access_privacy on every install, and without multisite
    // there are no site assignments to restrict by and mgrSite below stays
    // empty, so applying it would silently drop EVERY consult from the list
    // (seen on the packaged demo install). Team access privacy filters on the
    // plain provider.team column and stays enforced without multisite.
    boolean restrictToSite = bMultisites && isSiteAccessPrivacy;
    boolean restrictToTeam = isTeamAccessPrivacy;
    boolean restrictToSiteOrTeam = restrictToSite || restrictToTeam;

//multisites function
    if (restrictToSiteOrTeam) {

        if (restrictToSite)
            pdList = providerDataDao.findByProviderSite(curProvider_no);

        if (restrictToTeam)
            pdList = providerDataDao.findByProviderTeam(curProvider_no);

        for (ProviderData providerData : pdList) {
            providerMap.put(providerData.getId(), "true");
        }
    }
%>

<%
    //multi-site office , save all bgcolor to Hashmap
    HashMap<String, String> siteBgColor = new HashMap<String, String>();
    HashMap<String, String> siteShortName = new HashMap<String, String>();
    if (bMultisites) {
        SiteDao siteDao = (SiteDao) WebApplicationContextUtils.getWebApplicationContext(application).getBean(SiteDao.class);

        List<Site> sites = siteDao.getAllSites();
        for (Site st : sites) {
            siteBgColor.put(st.getName(), st.getBgColor());
            siteShortName.put(st.getName(), st.getShortName());
        }
        List<Site> providerSites = siteDao.getActiveSitesByProviderNo(curProvider_no);
        for (Site st : providerSites) {
            mgrSite.add(st.getName());
        }
    }
%>

<html>

    <%

        String team = (String) request.getAttribute("teamVar");
        if (team == null) {
            team = new String();
        }

        Boolean includeBool = (Boolean) request.getAttribute("includeCompleted");
        boolean includeCompleted = false;
        if (includeBool != null) {
            includeCompleted = "on".equals(request.getParameter("includeCompleted"));
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");

        // Getting startDate attribute of the consultation request and ensuring that it is of type "Date" before casting
        Object startDateObj = request.getAttribute("startDate");
        Date startDate = null;
        String formattedStartDate = "";
        if (startDateObj instanceof Date) {
            startDate = (Date) startDateObj;
            formattedStartDate = sdf.format(startDateObj);
        }

        // Getting endDate attribute of the consultation request and ensuring that it is of type "Date" before casting
        Object endDateObj = request.getAttribute("endDate");
        Date endDate = null;
        String formattedEndDate = "";
        if (endDateObj instanceof Date) {
            endDate = (Date) endDateObj;
            formattedEndDate = sdf.format(endDateObj);
        }

        // Getting orderby, description, and searchDate attributes of the consultation request
        String orderby = (String) request.getAttribute("orderby");
        String desc = (String) request.getAttribute("desc");
        String searchDate = (String) request.getAttribute("searchDate");

        // Setting defaults to match consultation request in struts 1
        if (searchDate == null) {
            searchDate = "0";
        }

        // Consultant / Provider (MRP) filters, already validated and scope-checked by the action.
        // The attribute names differ from the parameter names on purpose (see
        // ConsultationListFilterResolver): Struts answers a missing attribute from the action's value
        // stack. The instanceof guards keep a wrong-typed value from ever reaching a cast.
        Object consultantIdAttr = request.getAttribute("consultListConsultantId");
        Integer consultantId = consultantIdAttr instanceof Integer ? (Integer) consultantIdAttr : null;
        Object consultantLabelAttr = request.getAttribute("consultListConsultantLabel");
        String consultantLabel = consultantId != null && consultantLabelAttr instanceof String ? (String) consultantLabelAttr : "";
        Object filterProviderAttr = request.getAttribute("consultListFilterProviderNo");
        String filterProviderNo = filterProviderAttr instanceof String ? (String) filterProviderAttr : null;
        List<ConsultationMrpOptionDto> mrpOptions = new ArrayList<ConsultationMrpOptionDto>();
        Object mrpOptionsAttr = request.getAttribute("consultListMrpOptions");
        if (mrpOptionsAttr instanceof List) {
            for (Object option : (List<?>) mrpOptionsAttr) {
                if (option instanceof ConsultationMrpOptionDto) {
                    mrpOptions.add((ConsultationMrpOptionDto) option);
                }
            }
        }

        EctConsultationFormRequestUtil consultUtil;
        consultUtil = new EctConsultationFormRequestUtil();

        // Same gates as the row filters below: outside multisite mode the site
        // restriction is off, so the dropdown lists every team unless team
        // privacy narrows it.
        if (restrictToTeam) {
            consultUtil.estTeamsByTeam(curProvider_no);
        } else if (restrictToSite) {
            consultUtil.estTeamsBySite(curProvider_no);
        } else {
            consultUtil.estTeams();
        }


        List<String> tickerList = new ArrayList<String>();
    %>


    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <%@ include file="/WEB-INF/jsp/includes/global-head.jspf" %>
        <% if (showScheduleNav) { %>
        <link rel="stylesheet" href="<%=request.getContextPath()%>/css/topnav.css">
        <% } %>
        <title>
            <fmt:message key="ectViewConsultationRequests.title"/>
        </title>

        <style>
            .consult-table th a {
                color: var(--carlos-text);
                text-decoration: none;
            }
            .consult-table th a:hover {
                text-decoration: underline;
            }
            .consult-row-overdue {
                color: red;
            }
            .consult-status-1 { background-color: #eeeeFF; }
            .consult-status-2 { background-color: #ccccFF; }
            .consult-status-3 { background-color: #B8B8FF; }
            .consult-status-4 { background-color: #eeeeff; }
            .consult-status-5 { background-color: rgb(212, 212, 254); }
            .urgency-urgent { color: red; font-weight: bold; }
            .filter-bar {
                background: var(--carlos-bg-light);
                border: 1px solid var(--carlos-border);
                border-radius: 4px;
                padding: 10px 15px;
                margin-bottom: 12px;
            }
            .filter-bar .form-check-input:checked {
                background-color: var(--carlos-primary);
                border-color: var(--carlos-primary);
            }
            .consult-table tbody tr {
                cursor: pointer;
            }
            .consultant-search {
                position: relative;
                min-width: 16rem;
            }
            .consultant-suggestions {
                position: absolute;
                z-index: 1000;
                top: 100%;
                left: 0;
                right: 0;
                max-height: 18rem;
                overflow-y: auto;
                margin: 0;
                padding: 0;
                list-style: none;
                background: var(--carlos-bg, #fff);
                border: 1px solid var(--carlos-border);
                border-radius: 0 0 4px 4px;
                box-shadow: 0 4px 8px rgba(0, 0, 0, 0.15);
            }
            .consultant-suggestions li {
                padding: 4px 8px;
                cursor: pointer;
                font-size: 0.875rem;
            }
            .consultant-suggestions li[aria-selected="true"],
            .consultant-suggestions li:hover {
                background: var(--carlos-primary);
                color: #fff;
            }
            .consultant-suggestions li.consultant-no-match {
                cursor: default;
                font-style: italic;
            }
            .consultant-suggestions li.consultant-no-match:hover {
                background: transparent;
                color: inherit;
            }
            .consult-table tbody tr:hover td {
                filter: brightness(0.95);
            }
        </style>

        <script type="text/javascript">
            function BackToOscar() {
                window.close();
            }

            function popupOscarRx(vheight, vwidth, varpage) {
                var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "<fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgConsReq"/>", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            function popupOscarConsultationConfig(vheight, vwidth, varpage) {
                var windowprops = "height=" + vheight + ",width=" + vwidth + ",location=no,scrollbars=yes,menubars=no,toolbars=no,resizable=yes,screenX=0,screenY=0,top=0,left=0";
                var popup = window.open(varpage, "<fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgConsConfig"/>", windowprops);
                if (popup != null) {
                    if (popup.opener == null) {
                        popup.opener = self;
                    }
                }
            }

            // The filter form by id: with scheduleNav the main menu is included first, so
            // document.forms[0] is not guaranteed to be this form.
            function consultFilterForm() {
                return document.getElementById('consultationFilterForm');
            }

            // Every submit path (filter button, sort headers, paging) calls this first. Text typed
            // in the Consultant box that was never picked from the suggestions has no id, so it is
            // cleared here and can never be submitted as a filter.
            function sanitizeConsultationFilters() {
                var search = document.getElementById('consultantSearch');
                var id = document.getElementById('consultantId');
                if (search && id && id.value === '') {
                    search.value = '';
                }
            }

            function setOrder(val) {
                var frm = consultFilterForm();
                if (frm.orderby.value == val) {
                    if (frm.desc.value == '1') {
                        frm.desc.value = '0';
                    } else {
                        frm.desc.value = '1';
                    }
                } else {
                    frm.orderby.value = val;
                    frm.desc.value = '0';
                }
                sanitizeConsultationFilters();
                frm.submit();
            }

            function gotoPage(next) {
                var frm = consultFilterForm();
                frm.limit.value = <%=limit%>;
                if (next) frm.offset.value = <%=offset+limit%>;
                else frm.offset.value = <%=offset-limit%>;
                sanitizeConsultationFilters();
                frm.submit();
            }

            // Consultant type-ahead: min 2 characters, 300 ms debounce, fetch-based suggestion list
            // (no jQuery UI on this page). Suggestions are written with textContent only.
            document.addEventListener('DOMContentLoaded', function () {
                var frm = consultFilterForm();
                var search = document.getElementById('consultantSearch');
                var hiddenId = document.getElementById('consultantId');
                var list = document.getElementById('consultantSuggestions');
                var clearBtn = document.getElementById('consultantClear');
                if (!frm || !search || !hiddenId || !list) {
                    return;
                }
                var MIN_CHARS = 2;
                var DEBOUNCE_MS = 300;
                var endpoint = search.getAttribute('data-search-url');
                var noMatchText = search.getAttribute('data-no-matches') || '';
                var timer = null;
                var requestSeq = 0;
                var activeIndex = -1;

                function options() {
                    return list.querySelectorAll('li[role="option"]');
                }

                function closeList() {
                    list.hidden = true;
                    list.textContent = '';
                    activeIndex = -1;
                    search.setAttribute('aria-expanded', 'false');
                    search.removeAttribute('aria-activedescendant');
                }

                function setActive(index) {
                    var opts = options();
                    if (opts.length === 0) {
                        return;
                    }
                    if (index < 0) index = opts.length - 1;
                    if (index >= opts.length) index = 0;
                    for (var i = 0; i < opts.length; i++) {
                        opts[i].setAttribute('aria-selected', i === index ? 'true' : 'false');
                    }
                    activeIndex = index;
                    search.setAttribute('aria-activedescendant', opts[index].id);
                    opts[index].scrollIntoView({block: 'nearest'});
                }

                function choose(li) {
                    hiddenId.value = li.getAttribute('data-value');
                    search.value = li.textContent;
                    closeList();
                }

                function render(items) {
                    list.textContent = '';
                    activeIndex = -1;
                    if (!Array.isArray(items) || items.length === 0) {
                        var none = document.createElement('li');
                        none.className = 'consultant-no-match';
                        none.textContent = noMatchText;
                        list.appendChild(none);
                    } else {
                        items.forEach(function (item, i) {
                            var li = document.createElement('li');
                            li.id = 'consultantOption' + i;
                            li.setAttribute('role', 'option');
                            li.setAttribute('aria-selected', 'false');
                            li.setAttribute('data-value', String(item.value));
                            li.textContent = item.label;
                            li.addEventListener('mousedown', function (e) {
                                // mousedown, not click: fires before the input's blur closes the list.
                                e.preventDefault();
                                choose(li);
                            });
                            list.appendChild(li);
                        });
                    }
                    list.hidden = false;
                    search.setAttribute('aria-expanded', 'true');
                }

                function lookup(term) {
                    var seq = ++requestSeq;
                    fetch(endpoint + '?keyword=' + encodeURIComponent(term), {
                        credentials: 'same-origin',
                        headers: {'Accept': 'application/json'}
                    }).then(function (r) {
                        if (!r.ok) {
                            throw new Error('HTTP ' + r.status);
                        }
                        return r.json();
                    }).then(function (items) {
                        // Ignore an answer to a keyword the user has since changed.
                        if (seq === requestSeq && document.activeElement === search) {
                            render(items);
                        }
                    }).catch(function () {
                        if (seq === requestSeq) {
                            closeList();
                        }
                    });
                }

                search.addEventListener('input', function () {
                    // Any edit invalidates the previous pick until a suggestion is chosen again.
                    hiddenId.value = '';
                    clearTimeout(timer);
                    // Invalidate any in-flight lookup now, not when the debounced one starts:
                    // otherwise an answer for the previous text could still render (and be
                    // picked) during the debounce window.
                    requestSeq++;
                    closeList();
                    var term = search.value.trim();
                    if (term.length < MIN_CHARS) {
                        return;
                    }
                    timer = setTimeout(function () { lookup(term); }, DEBOUNCE_MS);
                });

                search.addEventListener('keydown', function (e) {
                    var open = !list.hidden && options().length > 0;
                    if (e.key === 'ArrowDown' && open) {
                        e.preventDefault();
                        setActive(activeIndex + 1);
                    } else if (e.key === 'ArrowUp' && open) {
                        e.preventDefault();
                        setActive(activeIndex - 1);
                    } else if (e.key === 'Enter' && open && activeIndex >= 0) {
                        // Enter picks the highlighted consultant instead of submitting the form.
                        e.preventDefault();
                        choose(options()[activeIndex]);
                    } else if (e.key === 'Escape' && !list.hidden) {
                        e.preventDefault();
                        closeList();
                    }
                });

                search.addEventListener('blur', function () {
                    closeList();
                });

                if (clearBtn) {
                    clearBtn.addEventListener('click', function () {
                        hiddenId.value = '';
                        search.value = '';
                        closeList();
                        search.focus();
                    });
                }

                // A filter submit (button or Enter) starts again at page 1; sort and paging call
                // frm.submit() directly, which does not fire this event, and keep their offset.
                frm.addEventListener('submit', function () {
                    sanitizeConsultationFilters();
                    frm.offset.value = '0';
                });
            });
        </script>
    </head>

    <body>
    <% if (showScheduleNav) { %>
        <jsp:include page="/WEB-INF/jsp/provider/mainMenu.jsp"/>
    <% } %>
    <div class="container-fluid p-0">

        <!-- Page Header -->
        <div class="page-header-bar d-flex justify-content-between align-items-center">
            <h4 class="page-header-title">
                <i class="fas fa-clipboard-list me-2"></i>
                <fmt:message key="ectViewConsultationRequests.title"/>
            </h4>
            <div>
                <a href="javascript:popupOscarConsultationConfig(700,960,'<%=request.getContextPath()%>/encounter/oscarConsultationRequest/config/ViewShowAllServices')"
                   class="btn btn-secondary btn-sm">
                    <i class="fas fa-cog me-1"></i>
                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgEditSpecialists"/>
                </a>
                <button type="button" class="btn btn-secondary btn-sm ms-1" onclick="window.close();">
                    <fmt:message key="global.btnBack"/>
                </button>
            </div>
        </div>

        <div class="px-3">

            <!-- Filter Bar -->
            <form id="consultationFilterForm" action="${pageContext.request.contextPath}/encounter/ViewConsultation" method="get">
                <div class="filter-bar">
                    <div class="row g-2 align-items-end">
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold">
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formSelectTeam"/>
                            </label>
                            <select name="sendTo" class="form-select form-select-sm">
                                <option value=""><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formViewAll"/></option>
                                <%
                                    if (team.equals("-1")) { %>
                                <option value="-1" selected><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/></option>
                                <% } else { %>
                                <option value="-1"><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/></option>
                                <% }
                                    for (int i = 0; i < consultUtil.teamVec.size(); i++) {
                                        String te = (String) consultUtil.teamVec.get(i);
                                        if (te.equals(team)) {
                                %>
                                <option value="<carlos:encode value='<%= te %>' context="htmlAttribute"/>" selected><carlos:encode value='<%= te %>' context="html"/></option>
                                <%} else {%>
                                <option value="<carlos:encode value='<%= te %>' context="htmlAttribute"/>"><carlos:encode value='<%= te %>' context="html"/></option>
                                <%
                                        }
                                    }
                                %>
                            </select>
                        </div>
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold" for="consultantSearch">
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgConsultant"/>
                            </label>
                            <div class="consultant-search">
                                <div class="input-group input-group-sm">
                                    <input type="text" id="consultantSearch" class="form-control form-control-sm"
                                           autocomplete="off" role="combobox" aria-autocomplete="list"
                                           aria-expanded="false" aria-controls="consultantSuggestions"
                                           maxlength="100"
                                           placeholder="<fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formConsultantPlaceholder"/>"
                                           data-no-matches="<fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgConsultantNoMatches"/>"
                                           data-search-url="${pageContext.request.contextPath}/encounter/consultation/searchConsultants"
                                           value="<carlos:encode value='<%= consultantLabel %>' context="htmlAttribute"/>"/>
                                    <button type="button" id="consultantClear" class="btn btn-outline-secondary"
                                            title="<fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.btnClearConsultant"/>"
                                            aria-label="<fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.btnClearConsultant"/>">
                                        <i class="fas fa-times" aria-hidden="true"></i>
                                    </button>
                                </div>
                                <ul id="consultantSuggestions" class="consultant-suggestions" role="listbox" hidden></ul>
                            </div>
                            <input type="hidden" name="consultantId" id="consultantId"
                                   value="<carlos:encode value='<%= consultantId != null ? String.valueOf(consultantId) : "" %>' context="htmlAttribute"/>"/>
                        </div>
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold" for="filterProviderNo">
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgProvider"/>
                            </label>
                            <select name="filterProviderNo" id="filterProviderNo" class="form-select form-select-sm">
                                <option value=""><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formAllProviders"/></option>
                                <% for (ConsultationMrpOptionDto mrpOption : mrpOptions) { %>
                                <option value="<carlos:encode value='<%= mrpOption.providerNo() %>' context="htmlAttribute"/>"<%= mrpOption.providerNo().equals(filterProviderNo) ? " selected" : "" %>><carlos:encode value='<%= mrpOption.label() %>' context="html"/></option>
                                <% } %>
                            </select>
                        </div>
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold">
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgStart"/>
                            </label>
                            <input type="date" name="startDate" id="startDate" class="form-control form-control-sm"
                                   value="<carlos:encode value='<%= formattedStartDate %>' context="htmlAttribute"/>" />
                        </div>
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold">
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgEnd"/>
                            </label>
                            <input type="date" name="endDate" id="endDate" class="form-control form-control-sm"
                                   value="<carlos:encode value='<%= formattedEndDate %>' context="htmlAttribute"/>" />
                        </div>
                        <div class="col-auto">
                            <div class="form-check mt-2">
                                <input type="checkbox" name="includeCompleted" id="includeCompleted" class="form-check-input"
                                    <%= includeCompleted ? "checked" : "" %> />
                                <label class="form-check-label small" for="includeCompleted">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgIncludeCompleted"/>
                                </label>
                            </div>
                        </div>
                        <div class="col-auto">
                            <label class="form-label mb-0 small fw-bold">
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgSearchon"/>
                            </label>
                            <div>
                                <div class="form-check form-check-inline">
                                    <input type="radio" name="searchDate" value="0" id="searchDateRef" class="form-check-input"
                                        <%= "0".equals(searchDate) ? "checked" : "" %> />
                                    <label class="form-check-label small" for="searchDateRef"><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgRefDate"/></label>
                                </div>
                                <div class="form-check form-check-inline">
                                    <input type="radio" name="searchDate" value="1" id="searchDateAppt" class="form-check-input"
                                        <%= "1".equals(searchDate) ? "checked" : "" %> />
                                    <label class="form-check-label small" for="searchDateAppt"><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgApptDate"/></label>
                                </div>
                            </div>
                        </div>
                        <div class="col-auto">
                            <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgSearch" var="msgSearchBtn"/>
                            <input type="submit" class="btn btn-primary btn-sm"
                                   value="${msgSearchBtn}"/>
                        </div>
                    </div>
                    <input type="hidden" name="currentTeam" id="currentTeam" value="<carlos:encode value='<%= team != null ? team : "" %>' context="htmlAttribute"/>"/>
                    <input type="hidden" name="orderby" id="orderby" value="<carlos:encode value='<%= orderby != null ? orderby : "" %>' context="htmlAttribute"/>"/>
                    <input type="hidden" name="desc" id="desc" value="<carlos:encode value='<%= desc != null ? desc : "" %>' context="htmlAttribute"/>"/>
                    <input type="hidden" name="offset" id="offset" value="<carlos:encode value='<%= String.valueOf(offset) %>' context="htmlAttribute"/>"/>
                    <input type="hidden" name="limit" id="limit" value="<carlos:encode value='<%= String.valueOf(limit) %>' context="htmlAttribute"/>"/>
                    <% if (showScheduleNav) { %>
                    <%-- Sorting and pagination submit this form; this hidden flag keeps the schedule top bar visible after each submit. --%>
                    <input type="hidden" name="scheduleNav" value="1"/>
                    <% } %>
                </div>
            </form>

            <!-- Team Info Badge -->
            <div class="mb-2">
                <span class="badge bg-secondary">
                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msfConsReqForTeam"/>:
                    <%
                        if (team.equals("-1")) {
                    %>
                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/>
                    <% } else if (team.isEmpty()) { %>
                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formViewAll"/>
                    <% } else { %>
                    <carlos:encode value='<%= team %>' context="html"/>
                    <% } %>
                </span>
                <% if (consultantId != null) { %>
                <span class="badge bg-secondary" id="consultantFilterBadge">
                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgConsultant"/>:
                    <carlos:encode value='<%= consultantLabel %>' context="html"/>
                </span>
                <% } %>
                <% if (filterProviderNo != null) {
                       String filterProviderLabel = filterProviderNo;
                       for (ConsultationMrpOptionDto mrpOption : mrpOptions) {
                           if (mrpOption.providerNo().equals(filterProviderNo)) {
                               filterProviderLabel = mrpOption.label();
                               break;
                           }
                       }
                %>
                <span class="badge bg-secondary" id="providerFilterBadge">
                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgProvider"/>:
                    <carlos:encode value='<%= filterProviderLabel %>' context="html"/>
                </span>
                <% } %>
            </div>

            <!-- Consultation Results Table -->
            <div class="table-responsive">
                <table class="table table-hover table-sm table-bordered consult-table">
                    <thead class="table-light">
                        <tr>
                            <th>
                                <a href="#" onclick="setOrder('1'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgStatus"/>
                                </a>
                            </th>
                            <th>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgency"/>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('2'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgTeam"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('3'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgPatient"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('4'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgProvider"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('5'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgService"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('6'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgConsultant"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('7'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgRefDate"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('8'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgAppointmentDate"/>
                                </a>
                            </th>
                            <th>
                                <a href="#" onclick="setOrder('9'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgFollowUpDate"/>
                                </a>
                            </th>
                            <% if (bMultisites) { %>
                            <th>
                                <a href="#" onclick="setOrder('10'); return false;">
                                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgSiteName"/>
                                </a>
                            </th>
                            <%} %>
                        </tr>
                    </thead>
                    <tbody>
                        <%
                            EctViewConsultationRequestsUtil theRequests;
                            theRequests = new EctViewConsultationRequestsUtil();
                            theRequests.estConsultationVecByTeam(LoggedInInfo.getLoggedInInfoFromSession(request),
                                    new ConsultationListFilterDto(team, includeCompleted, startDate, endDate, orderby, desc,
                                            searchDate, offset, limit, consultantId, filterProviderNo));
                            boolean overdue;
                            UserPropertyDAO pref = (UserPropertyDAO) WebApplicationContextUtils.getWebApplicationContext(pageContext.getServletContext()).getBean(UserPropertyDAO.class);
                            String user = (String) session.getAttribute("user");
                            UserProperty up = pref.getProp(user, UserProperty.CONSULTATION_TIME_PERIOD_WARNING);
                            String timeperiod = null;
                            int countback = 0;

                            if (up != null && up.getValue() != null && !up.getValue().trim().equals("")) {
                                timeperiod = up.getValue();
                            }

                            for (int i = 0; i < theRequests.ids.size(); i++) {
                                //multisites. skip record if not belong to same site/team
                                if (restrictToSiteOrTeam) {
                                    if (providerMap.get(theRequests.providerNo.get(i)) == null) continue;
                                }

                                String id = theRequests.ids.get(i);
                                String status = theRequests.status.get(i);
                                String patient = theRequests.patient.get(i);
                                String provide = theRequests.provider.get(i);
                                String service = theRequests.service.get(i);
                                boolean eReferral = theRequests.eReferral.get(i);
                                String date = theRequests.date.get(i);
                                String demo = theRequests.demographicNo.get(i);
                                String appt = theRequests.apptDate.get(i);
                                String patBook = theRequests.patientWillBook.get(i);
                                String urgency = theRequests.urgency.get(i);
                                String sendTo = theRequests.teams.get(i);
                                if (sendTo == null) sendTo = "-1";
                                String specialist = theRequests.vSpecialist.get(i);
                                String followUpDate = theRequests.followUpDate.get(i);
                                String siteName = "";
                                if (bMultisites) {
                                    siteName = theRequests.siteName.get(i);
                                }

                                //multisites. skip record if not belong to same site
                                // (mgrSite is only populated under multisite; without it
                                // this check would drop every row).
                                if (bMultisites && restrictToSiteOrTeam) {
                                    if (!mgrSite.contains(siteName)) continue;
                                }
                                if (EctViewConsultationRequestsUtil.isTicklerDemographic(demo)
                                        && "1".equals(status) && dateGreaterThan(date, Calendar.WEEK_OF_YEAR, -1)) {
                                    tickerList.add(demo);
                                }
                                overdue = false;

                                if (timeperiod != null) {
                                    try {
                                        countback = Integer.parseInt(timeperiod);
                                    } catch (NumberFormatException e) {
                                        timeperiod = null; // fall through to default logic below
                                    }
                                }
                                if (timeperiod != null) {
                                    countback = countback * -1;

                                    if ((status.equals("1") || status.equals("2") || status.equals("3")) && dateGreaterThan(date, Calendar.MONTH, countback)) {
                                        overdue = true;
                                    }
                                } else {
                                    countback = -7;  //7 days
                                    if ((status.equals("1") || status.equals("3")) && dateGreaterThan(date, Calendar.DAY_OF_YEAR, countback)) {
                                        overdue = true;
                                    }

                                    countback = -30;  //30 days
                                    if (status.equals("2") && dateGreaterThan(date, Calendar.DAY_OF_YEAR, countback)) {
                                        overdue = true;
                                    }
                                }

                                String viewUrl = request.getContextPath() + "/encounter/ViewRequest?requestId=" + SafeEncode.forUriComponent(id);
                        %>
                        <tr class="<%=overdue ? "consult-row-overdue" : ""%>"
                            tabindex="0"
                            role="button"
                            onclick="popupOscarRx(700,960,'<carlos:encode value='<%= viewUrl %>' context="javaScriptAttribute"/>')"
                            onkeypress="if(event.key==='Enter'){popupOscarRx(700,960,'<carlos:encode value='<%= viewUrl %>' context="javaScriptAttribute"/>');}">
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <% if (status.equals("1")) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgND"/>
                                <% } else if (status.equals("2")) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgSR"/>
                                <% } else if (status.equals("3")) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgPR"/>
                                <% } else if (status.equals("4")) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgDONE"/>
                                <% } else if (status.equals("5")) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgBC"/>
                                <%}%>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <% if ("1".equals(urgency)) { %>
                                <span class="urgency-urgent"><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgencyUrgent"/></span>
                                <% } else if ("2".equals(urgency)) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgencyNonUrgent"/>
                                <% } else if ("3".equals(urgency)) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgUrgencyReturn"/>
                                <% } %>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <% if (sendTo.equals("-1")) { %>
                                <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.formTeamNotApplicable"/>
                                <% } else { %>
                                <carlos:encode value='<%= sendTo %>' context="html"/>
                                <% } %>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <carlos:encode value='<%= patient %>' context="html"/>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <carlos:encode value='<%= provide %>' context="html"/>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <carlos:encode value='<%= service %>' context="html"/>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <carlos:encode value='<%= specialist %>' context="html"/>
                                <% if (eReferral) { %>
                                <span class="badge bg-info text-dark ms-1"><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgOceanBadge"/></span>
                                <%} %>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <carlos:encode value='<%= date %>' context="html"/>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <% if (patBook != null && patBook.trim().equals("1")) {%>
                                <span class="fst-italic"><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgPatientWillBook"/></span>
                                <%} else {%>
                                <carlos:encode value='<%= appt %>' context="html"/>
                                <%}%>
                            </td>
                            <td class="consult-status-<carlos:encode value='<%= status %>' context="htmlAttribute"/>">
                                <carlos:encode value='<%= followUpDate %>' context="html"/>
                            </td>
                            <% if (bMultisites) { %>
                            <td style="background-color: <carlos:encode value='<%= siteBgColor.get(siteName)==null || siteBgColor.get(siteName).length()== 0 ? "#FFFFFF" : siteBgColor.get(siteName) %>' context="htmlAttribute"/>">
                                <carlos:encode value='<%= siteShortName.get(siteName) != null ? siteShortName.get(siteName) : "" %>' context="html"/>
                            </td>
                            <%} %>
                        </tr>
                        <%}%>
                    </tbody>
                </table>
            </div>

            <!-- Pagination -->
            <div class="d-flex justify-content-between align-items-center mb-3">
                <div>
                    <%
                        if (offset > 0) {
                    %><button type="button" class="btn btn-secondary btn-sm" onclick="gotoPage(false);">
                        <i class="fas fa-chevron-left me-1"></i><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgPrev"/>
                    </button><%
                        }
                        if (theRequests.ids.size() == limit) {
                    %><button type="button" class="btn btn-secondary btn-sm ms-1" onclick="gotoPage(true);">
                        <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgNext"/><i class="fas fa-chevron-right ms-1"></i>
                    </button><%
                        }
                    %>
                </div>
                <div>
                    <% if (tickerList.size() > 0) {
                        String queryStr = "";
                        for (int i = 0; i < tickerList.size(); i++) {
                            String demo = (String) tickerList.get(i);
                            if (i == 0) {
                                queryStr += "demo=" + SafeEncode.forUriComponent(demo);
                            } else {
                                queryStr += "&demo=" + SafeEncode.forUriComponent(demo);
                            }
                        }%>
                    <fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgAddTicklerConfirm" var="addTicklerConfirmVar"/>
                    <%  String addTicklerConfirmJs = SafeEncode.forJavaScript((String)pageContext.getAttribute("addTicklerConfirmVar"));
                        String addTicklerUrl = request.getContextPath() + "/tickler/ViewAddTickler?" + queryStr
                            + "&message=" + java.net.URLEncoder.encode("Patient has Consultation Letter with a status of 'Nothing Done' for over one week", "UTF-8"); %>
                    <a class="btn btn-link btn-sm" target="_blank"
                       href="<%= SafeEncode.forHtmlAttribute(addTicklerUrl) %>"
                       onclick="return confirm('<%=addTicklerConfirmJs%>');">
                        <i class="fas fa-bell me-1"></i><fmt:message key="encounter.oscarConsultationRequest.ViewConsultationRequests.msgAddTicklerBtn"/>
                    </a>
                    <%}%>
                </div>
            </div>

        </div>
    </div>

    </body>

</html>
<%!

    boolean dateGreaterThan(String dateStr, int unit, int period) {
        DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        Date prevDate = null;
        try {
            prevDate = formatter.parse(dateStr);
        } catch (Exception e) {
            return false;
        }

        Calendar bonusEl = Calendar.getInstance();
        bonusEl.add(unit, period);
        Date bonusStartDate = bonusEl.getTime();

        return bonusStartDate.after(prevDate);
    }

%>
