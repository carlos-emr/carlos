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
<security:oscarSec roleName="<%=roleName$%>" objectName="_admin,_admin.misc" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_admin&type=_admin.misc");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<fmt:setBundle basename="oscarResources"/>


<c:set var="ctx" value="${pageContext.request.contextPath}"/>

<!DOCTYPE html>
<html>
    <head>
    <link rel="icon" href="${pageContext.request.contextPath}/images/favicon.ico"/>
        <base href="<%= request.getScheme() + "://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath() + "/" %>">
        <title><fmt:message key="admin.admin.UpdateDrugref"/></title>
        <link href="${carlos:forHtmlAttribute(ctx)}/library/bootstrap/5.3.8/css/bootstrap.min.css" rel="stylesheet" type="text/css">
        <script src="${carlos:forHtmlAttribute(ctx)}/share/javascript/csrfTokenFetch.js"></script>

        <script>
            // Administration > Update Drugref.
            //
            // Three server calls, all to /rx/updateDrugrefDB:
            //   method=verify   -> {lastUpdate, drugDatabase, version}   (lastUpdate is "updating" mid-run)
            //   method=status   -> {state, step, message, startedAt, finishedAt, lastUpdate}
            //                      state: IDLE | RUNNING | SUCCEEDED | FAILED, or UNAVAILABLE when
            //                      DrugRef cannot answer (down, or a build without getUpdateStatus)
            //   method=updateDB -> {result: "running" | "updating" | null}
            //
            // The page used to stop at "Update has started" and never look again. A run that
            // died left "updating" on screen for good, and a call that failed outright showed
            // nothing at all. Now it polls status until the run ends and says how it ended.
            var POLL_MS = 15000;
            var pollTimer = null;

            function getCsrfToken() {
                var el = document.querySelector('input[name="CSRF-TOKEN"]');
                if (!el) {
                    console.warn('CSRF-TOKEN hidden input not found. POST requests will be rejected.');
                    return '';
                }
                return el.value;
            }

            function callDrugref(method) {
                const url = "${carlos:forJavaScript(ctx)}" + "/rx/updateDrugrefDB";
                const formData = new URLSearchParams();
                formData.append('method', method);
                formData.append('CSRF-TOKEN', getCsrfToken());
                return fetch(url, {
                    method: 'POST',
                    credentials: 'same-origin',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                        'CSRF-TOKEN': getCsrfToken(),
                        'X-Requested-With': 'XMLHttpRequest'
                    },
                    body: formData.toString()
                }).then(function (response) {
                    if (!response.ok) {
                        throw new Error('HTTP ' + response.status + ' from ' + method);
                    }
                    return response.json();
                });
            }

            function show(id, visible) {
                document.getElementById(id).style.display = visible ? 'block' : 'none';
            }

            function setResult(text, kind) {
                var el = document.getElementById('updateResult');
                el.textContent = text;
                el.className = kind ? 'alert alert-' + kind + ' mt-3' : '';
                el.style.display = text ? 'block' : 'none';
            }

            function renderVerify(json) {
                if (!json || json.lastUpdate == null) {
                    document.getElementById('dbInfo').textContent = 'Drugref database has not been updated, please update.';
                    show('dbInfo', true);
                    show('statusDisplay', false);
                    show('updateButton', true);
                } else if (json.lastUpdate === 'updating') {
                    document.getElementById('dbInfo').textContent = 'Drugref database is updating';
                    show('dbInfo', true);
                    show('statusDisplay', false);
                    show('updateButton', false);
                } else {
                    document.getElementById('dbDateTime').textContent = json.lastUpdate;
                    document.getElementById('drugDatabaseVersion').textContent = json.version;
                    document.getElementById('drugDatabase').textContent = json.drugDatabase;
                    show('dbInfo', false);
                    show('statusDisplay', true);
                    show('updateButton', true);
                }
            }

            // Resolves with the verify payload (null when DrugRef could not be reached),
            // which the startup path needs to spot a run already going on an older build.
            function getUpdateTime() {
                return callDrugref('verify')
                    .then(function (json) {
                        renderVerify(json);
                        return json;
                    })
                    .catch(function () {
                        document.getElementById('dbInfo').textContent = 'Drugref database is unavailable. Contact support.';
                        show('dbInfo', true);
                        show('statusDisplay', false);
                        show('updateButton', true);
                        return null;
                    });
            }

            // Renders the status struct; returns true while a run is in progress.
            function renderStatus(status) {
                var running = false;
                if (!status || status.state === 'UNAVAILABLE' || status.state === 'IDLE') {
                    return false;
                }
                if (status.state === 'RUNNING') {
                    running = true;
                    setResult('Update in progress' + (status.startedAt ? ' since ' + status.startedAt : '')
                        + (status.step ? ': ' + status.step : '') + '. Drug lookups are limited until it finishes.', 'info');
                    show('updateButton', false);
                } else if (status.state === 'FAILED') {
                    setResult('The last update' + (status.finishedAt ? ' (' + status.finishedAt + ')' : '')
                        + ' FAILED and the previous drug data was kept: ' + (status.message || 'no details'), 'danger');
                    show('updateButton', true);
                } else if (status.state === 'SUCCEEDED') {
                    setResult('Update completed' + (status.finishedAt ? ' at ' + status.finishedAt : '')
                        + (status.message ? ': ' + status.message : '') + '.', 'success');
                    show('updateButton', true);
                }
                return running;
            }

            function pollStatus() {
                callDrugref('status')
                    .then(function (status) {
                        var running = renderStatus(status);
                        if (!running && status && status.state === 'UNAVAILABLE') {
                            // Older DrugRef without getUpdateStatus: fall back to the verify
                            // probe, which still answers "updating" until the run ends.
                            return callDrugref('verify').then(function (v) {
                                renderVerify(v);
                                if (v && v.lastUpdate === 'updating') {
                                    pollTimer = setTimeout(pollStatus, POLL_MS);
                                } else if (!v || v.lastUpdate == null) {
                                    // DrugRef stopped answering mid-run. A null lastUpdate is
                                    // "cannot tell", NOT "finished": reporting success here
                                    // would tell the operator the rebuild completed when it
                                    // may have died with the tables half-built. Keep looking.
                                    setResult('DrugRef stopped responding, so the outcome of this update is '
                                        + 'unknown. Still checking. If this persists, check the DrugRef service '
                                        + '(journalctl -u carlos-emr) before prescribing.', 'danger');
                                    pollTimer = setTimeout(pollStatus, POLL_MS);
                                } else {
                                    setResult('Update finished at ' + v.lastUpdate + '.', 'success');
                                }
                            });
                        }
                        if (running) {
                            pollTimer = setTimeout(pollStatus, POLL_MS);
                        } else {
                            // Run ended: refresh the date/version panel to show the outcome.
                            getUpdateTime();
                        }
                    })
                    .catch(function (error) {
                        console.error('Error reading update status:', error);
                        pollTimer = setTimeout(pollStatus, POLL_MS);
                    });
            }

            function updateDB() {
                setResult('Starting the update...', 'info');
                callDrugref('updateDB')
                    .then(function (json) {
                        if (json.result === 'running') {
                            setResult("Update has started. It rebuilds the drug database from Health Canada's "
                                + "extract and usually takes 15 to 60 minutes; drug lookups are limited until it "
                                + "finishes. This page follows its progress.", 'info');
                            show('updateButton', false);
                            pollStatus();
                        } else if (json.result === 'updating') {
                            setResult('An update is already running.', 'info');
                            pollStatus();
                        } else {
                            setResult('The update could not be started: DrugRef did not answer. Check that the '
                                + 'DrugRef service is running (drug lookups also need it).', 'danger');
                        }
                    })
                    .catch(function (error) {
                        console.error('Error updating database:', error);
                        setResult('The update could not be started: ' + error.message, 'danger');
                    });
            }

            document.addEventListener("DOMContentLoaded", function () {
                fetchCsrfToken("${carlos:forJavaScript(ctx)}")
                    .then(function () {
                        return getUpdateTime();
                    })
                    .then(function (verifyJson) {
                        // Report a run already in progress (started from another session) or
                        // the outcome of the last one, and keep following a running one.
                        return callDrugref('status').then(function (status) {
                            if (renderStatus(status)) {
                                pollTimer = setTimeout(pollStatus, POLL_MS);
                                return;
                            }
                            if (status && status.state === 'UNAVAILABLE'
                                    && verifyJson && verifyJson.lastUpdate === 'updating') {
                                // An older DrugRef with a run already going: status cannot see
                                // it, so follow it through the verify probe. Without this the
                                // page sat on "database is updating" for good, because polling
                                // was only ever armed off a RUNNING status.
                                setResult('An update is already running. This page is following it.', 'info');
                                show('updateButton', false);
                                pollTimer = setTimeout(pollStatus, POLL_MS);
                            }
                        }).catch(function (error) {
                            console.warn('Update status not available:', error);
                        });
                    })
                    .catch(function (err) {
                        console.warn('Skipping getUpdateTime — CSRF token not available:', err);
                        document.getElementById('dbInfo').textContent =
                            'Could not load CSRF token. Refresh the page or contact support.';
                    });
            });
        </script>
      <style>
        #updateButton {
          padding-top: 19px;
        }
        #statusDisplay label {
          font-weight: bold;
          display: inline-block !important;
        }
      </style>
    </head>
    <body class="mainbody">
    <input type="hidden" name="CSRF-TOKEN" value="">
    <h3><fmt:message key="admin.admin.UpdateDrugref"/></h3>
    <div class="card card-body bg-body-tertiary">
        <div id="dbInfo"></div>
        <div id="statusDisplay" style="display:none;">
          <div>
            <label for="drugDatabase"><fmt:message key="admin.admin.DrugRef.database"/>&colon; </label>
            <span id="drugDatabase"></span></div>
          <div>
            <label for="drugDatabaseVersion"><fmt:message key="admin.admin.DrugRef.databaseVersion"/>&colon; </label>
            <span id="drugDatabaseVersion"></span></div>
          <div>
            <label for="dbDateTime"><fmt:message key="admin.admin.DrugRef.updateDate"/>&colon; </label>
            <span id="dbDateTime"></span></div>
        </div>
        <div id="updateButton">
          <a id="updatedb" onclick="updateDB();" href="javascript:void(0);"
              class="btn btn-primary"><fmt:message key="admin.admin.UpdateDrugref"/></a>
        </div>
        <div id="updateResult" role="status" style="display:none;"></div>
    </div>
    </body>

</html>