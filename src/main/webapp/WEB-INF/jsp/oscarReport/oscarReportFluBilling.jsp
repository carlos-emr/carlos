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
    CARLOS EMR — Flu Billing Report

    Purpose:
        Influenza recall worklist. Lists every patient aged 65 or over who is
        eligible for an Ontario G590A/G591A influenza claim, alongside the date
        of their latest such claim in the selected year. A blank Billing Date
        marks a patient who still needs a flu shot — the rows this report
        exists to surface.

    Features:
        - Seven patient columns: name, date of birth, age, enrolment (roster)
          status, patient status, phone, and billing date.
        - Year selector spanning the selected year plus or minus two, clamped
          to the range the validation accepts, so it never offers a year that
          would fall back to the current one.
        - Provider filter defaulting to All Providers.
        - Renders as a fragment loaded into #dynamic-content by the
          administration left navigation, and standalone in the popup opened
          from admin/admin.jsp.

    Request parameters:
        numMonth - four-digit report year, from 1900 to two years ahead of the
                   current year. Anything else falls back to the current year.
        proNo    - provider number to filter on. "-1", blank, or a provider not
                   in the active list all mean all providers, so the dropdown
                   label always matches the filter that ran.

    Security:
        RptFluBilling2Action is the authority and requires read rights on
        _report, so that is the effective requirement. The <security:oscarSec>
        gate below is defence in depth for direct dispatches and additionally
        accepts _admin.reporting; a user holding only _admin.reporting is
        already rejected by the action and never reaches it.

    @since 2026-08-06
--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report,_admin.reporting" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_report&type=_admin.reporting");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@ page import="java.util.*,io.github.carlos_emr.carlos.report.data.*" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@ page import="io.github.carlos_emr.carlos.report.data.RptFluReportData" %>
<%@ include file="/taglibs.jsp" %>
<fmt:setBundle basename="oscarResources"/>
<c:set var="ctx" value="${pageContext.request.contextPath}" scope="request"/>

<%
    // numMonth is attacker-controllable, so anything that is not a plausible
    // four-digit year falls back to the current year. Passing it straight to
    // Integer.parseInt used to 500 on non-numeric input, and a value near
    // Integer.MAX_VALUE overflowed the year-select loop below into an
    // effectively unbounded render that pinned a request thread. The lower bound
    // keeps values such as 0025 out, which parse but render every billing date
    // blank -- and a blank cell here means "this patient still needs a flu shot".
    GregorianCalendar cal = new GregorianCalendar();
    int thisYear = cal.get(Calendar.YEAR);
    String requestedYear = request.getParameter("numMonth");
    int curYear = thisYear;
    if (requestedYear != null && requestedYear.matches("\\d{4}")) {
        int parsedYear = Integer.parseInt(requestedYear);
        if (parsedYear >= MIN_REPORT_YEAR && parsedYear <= thisYear + MAX_FUTURE_REPORT_YEARS) {
            curYear = parsedYear;
        }
    }
    String years = String.valueOf(curYear);

    // Trimmed here so a padded value can still match a real provider below; the
    // selectability guard after the provider list is loaded is what turns every
    // other unusable value -- absent, blank, unknown, deactivated -- into "-1".
    String pros = request.getParameter("proNo");
    pros = (pros == null) ? "-1" : pros.trim();

    RptFluReportData fluData = new RptFluReportData();
    List<Provider> providers = fluData.providerList();

    // Only a provider the dropdown can actually show is a filter we can label
    // honestly. A proNo that is unknown or since-deactivated would otherwise
    // filter the table to nothing while no <option> carried "selected", so the
    // browser fell back to displaying "All Providers" over an empty worklist --
    // the same misleading state as an absent proNo, reached from a bookmarked
    // or shared URL.
    if (!"-1".equals(pros)) {
        boolean selectableProvider = false;
        for (Provider candidate : providers) {
            if (pros.equals(candidate.getProviderNo())) {
                selectableProvider = true;
                break;
            }
        }
        if (!selectableProvider) {
            pros = "-1";
        }
    }

    fluData.fluReportGenerate(pros, years);
%>
<%!
    // Bounds shared by the numMonth validation and the year dropdown, so the
    // selector can never offer a year the validation would reject.
    static final int MIN_REPORT_YEAR = 1900;
    static final int MAX_FUTURE_REPORT_YEARS = 2;

    String selled(String i, String years) {
        String retval = "";
        if (i.equals(years)) {
            retval = "selected";
        }
        return retval;
    }
%>


<div class="pb-2 mt-4 mb-3 border-bottom">
    <h4>
        <fmt:message key="oscarReport.oscarReportFluBilling.title"/>
        <carlos:encode value='<%= years %>' context="html"/>
    </h4>
</div>

<form action="${carlos:forHtmlAttribute(ctx)}/oscarReport/FluBilling" method="get" class="card card-body bg-body-tertiary d-flex flex-wrap align-items-center gap-2" id="fluForm">
    <select name="numMonth" class="form-select form-select-sm d-inline-block w-auto">
        <%
            // Clamped to the same range the validation above accepts, so every
            // offered year is actually selectable. Otherwise picking the highest
            // year exposes options that silently fall back to the current year.
            for (int i = Math.max(MIN_REPORT_YEAR, curYear - 2);
                 i <= Math.min(thisYear + MAX_FUTURE_REPORT_YEARS, curYear + 2); i++) {
        %>
        <option value="<%=i%>" <%=selled(("" + i), years)%>><%=i%>
        </option>
        <%
            }
        %>

    </select> <select name="proNo" class="form-select">
    <option value="-1" <%=selled("-1", pros)%>>
        <fmt:message key="oscarReport.oscarReportFluBilling.msgAllProviders"/>
    </option>
    <%
        for (Provider p : providers) {
    %>
    <option value="<carlos:encode value='<%= p.getProviderNo() %>' context="htmlAttribute"/>" <%=selled(p.getProviderNo(), pros)%>><carlos:encode value='<%= p.getFormattedName() %>' context="html"/>
    </option>
    <%
        }
    %>
</select>
    <button type="submit" class="btn btn-primary">
        <fmt:message key="oscarReport.oscarReportFluBilling.btnUpdate"/>
    </button>
</form>

<table class="table table-bordered table-striped table-sm table-hover">
    <thead>
    <tr>
        <th><fmt:message key="oscarReport.oscarReportFluBilling.msgName"/></th>
        <th><fmt:message key="oscarReport.oscarReportFluBilling.msgDOB"/></th>
        <th><fmt:message key="oscarReport.oscarReportFluBilling.msgAge"/></th>
        <th><fmt:message key="oscarReport.oscarReportFluBilling.msgRoster"/></th>
        <th><fmt:message key="oscarReport.oscarReportFluBilling.msgPatientStatus"/></th>
        <th><fmt:message key="oscarReport.oscarReportFluBilling.msgPhone"/></th>

        <th><fmt:message key="oscarReport.oscarReportFluBilling.msgBillingDate"/></th>
    </tr>
    </thead>
    <tbody>
    <%
        RptFluReportData.DemoFluDataStruct demoData;
        for (int i = 0; i < fluData.demoList.size(); i++) {
            demoData = (RptFluReportData.DemoFluDataStruct) fluData.demoList
                    .get(i);
    %>
    <tr>
        <td><carlos:encode value='<%= demoData.demoName %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= demoData.getDemoDOB() %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= demoData.getDemoAge() %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= demoData.demoRosterStatus %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= demoData.demoPatientStatus %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= demoData.getDemoPhone() %>' context="html"/>
        </td>
        <td><carlos:encode value='<%= demoData.getBillingDate(fluData.years) %>' context="html"/>
        </td>
    </tr>
    <%
        }
    %>
    </tbody>
</table>

<script>
    // registerFormSubmit is defined on the administration/index.jsp parent, which
    // loads this fragment into #dynamic-content via the leftNav contentLink handler.
    // admin/admin.jsp opens the same report standalone in a popup, where the parent
    // (and therefore the hook) does not exist; there the form falls back to its
    // native GET submit against the same action, which is the intended degradation.
    if (typeof registerFormSubmit === 'function') {
        registerFormSubmit('fluForm', 'dynamic-content');
    } else {
        console.warn('registerFormSubmit unavailable; Flu Billing Report filters will full-page submit.');
    }
</script>
