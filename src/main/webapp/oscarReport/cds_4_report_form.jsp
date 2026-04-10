<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_report" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_report");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page import="java.util.Calendar" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.model.Program" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.service.ProgramManager" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Provider" %>
<%@page import="io.github.carlos_emr.carlos.managers.ProviderManager2" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.FunctionalCentreDao" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="java.util.List" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.FunctionalCentre" %>
<%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@page import="java.util.GregorianCalendar" %>
<%@page import="java.text.DateFormatSymbols" %>
<%@page import="org.owasp.encoder.Encode" %>

<%@ include file="/taglibs.jsp" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"
       scope="request"/>
<link rel="stylesheet" href="${ctx}/library/flatpickr/flatpickr.min.css" type="text/css"/>
<script src="${ctx}/library/jquery/jquery-3.7.1.min.js"></script>
<script src="${ctx}/library/jquery/jquery-compat.js"></script>
<script src="${ctx}/library/flatpickr/flatpickr.min.js"></script>

<%
    FunctionalCentreDao functionalCentreDao = (FunctionalCentreDao) SpringUtils.getBean(FunctionalCentreDao.class);
    ProviderManager2 providerManager = (ProviderManager2) SpringUtils.getBean(ProviderManager2.class);
    ProgramManager programManager = (ProgramManager) SpringUtils.getBean(ProgramManager.class);

    LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
    List<FunctionalCentre> functionalCentres = functionalCentreDao.findInUseByFacility(loggedInInfo.getCurrentFacility().getId());
%>

<div class="pb-2 mt-4 mb-3 border-bottom">
    <h4>CDS Reports</h4>
</div>

<form class="card card-body bg-body-tertiary" action="cds_4_report_results.jsp"
      id="cdsForm">
    <fieldset>

        <!-- Form Name -->
        <legend>CDS-MH 4.05</legend>

        <div class="mb-3">
            <label class="form-label">Functional Centre</label>
            <div>
                <select id="functionalCentreId" name="functionalCentreId" class="form-select">
                    <%
                        for (FunctionalCentre functionalCentre : functionalCentres) {
                    %>
                    <option value="<%=functionalCentre.getAccountId()%>"><%=functionalCentre.getAccountId() + ", " + functionalCentre.getDescription()%>
                    </option>
                    <%
                        }
                    %>
                </select>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Date Start</label>
            <div>
                <input type="text" name="startDate" id="startDate"/>
                <script type="text/javascript">
                    (function() {
                        var d = new Date();
                        var month = d.getMonth();
                        if (month > 0) {
                            d.setMonth(month - 1);
                        } else {
                            d.setMonth(11);
                            d.setFullYear(d.getFullYear() - 1);
                        }
                        flatpickr('#startDate', {dateFormat: 'Y-m-d', defaultDate: d, allowInput: true});
                    })();
                </script>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Date End (inclusive)</label>
            <div>
                <input type="text" name="endDate" id="endDate"/>
                <script type="text/javascript">
                    flatpickr('#endDate', {dateFormat: 'Y-m-d', defaultDate: new Date(), allowInput: true});
                </script>
            </div>
        </div>
        <div class="mb-3">
            <label class="form-label">Filter By</label>
            <div>
                <select id="filterCriteriaSelection" onchange="showFilterCriteria()">
                    <option value="">None</option>
                    <option value="PROVIDER">Provider</option>
                    <option value="PROGRAM">Program</option>
                </select>
                <script type="text/javascript">
                    function showFilterCriteria() {
                        var selection = document.getElementById('filterCriteriaSelection').value;

                        if (selection == "PROVIDER") {
                            document.getElementById('providerOptions').style.display = '';
                            document.getElementById('programOptions').style.display = 'none';
                        } else if (selection == "PROGRAM") {
                            document.getElementById('providerOptions').style.display = 'none';
                            document.getElementById('programOptions').style.display = '';
                        } else {
                            document.getElementById('providerOptions').style.display = 'none';
                            document.getElementById('programOptions').style.display = 'none';
                        }
                    }

                    document.addEventListener('DOMContentLoaded', function () {
                        showFilterCriteria();
                    });
                </script>
            </div>
        </div>
        <div id="providerOptions" class="mb-3">
            <label class="form-label">Providers to include
                <small>
                    (multi select is allowed)
                </small>
            </label>
            <div>
                <select name="providerIds" class="form-select d-inline-block w-auto" multiple="multiple">
                    <%
                        // null for both active and inactive because the report might be for a providers who's just left in the current reporting period.
                        List<Provider> providers = providerManager.getProviders(loggedInInfo, null);

                        for (Provider provider : providers) {
                            // skip (system,system) user
                            if (provider.getProviderNo().equals(Provider.SYSTEM_PROVIDER_NO)) continue;

                    %>
                    <option value="<%=provider.getProviderNo()%>"><%=Encode.forHtml(provider.getFormattedName())%>
                    </option>
                    <%
                        }
                    %>
                </select>
            </div>
        </div>

        <div id="programOptions" class="mb-3">
            <label class="form-label">Programs to include
                <small>
                    (multi select is allowed)
                </small>
            </label>
            <div>
                <select name="programIds" class="form-select d-inline-block w-auto" multiple="multiple">
                    <%
                        List<Program> programs = programManager.getPrograms(loggedInInfo.getCurrentFacility().getId());

                        for (Program program : programs) {
                    %>
                    <option value="<%=program.getId()%>"><%=Encode.forHtml(program.getName() + " (" + program.getType() + ")")%>
                    </option>
                    <%
                        }
                    %>
                </select>
            </div>
        </div>

        <div class="mb-3">
            <div>
                <button type="submit" class="btn btn-primary">View Report</button>
            </div>
        </div>

    </fieldset>
</form>

<div id="cds-results"></div>
<script type="text/javascript">
    $(document).ready(function () {
        $('#cdsForm').validate({
            rules: {
                functionalCentreId: {
                    required: true
                }
            }
        });
    });

    registerFormSubmit('cdsForm', 'cds-results');
</script>
