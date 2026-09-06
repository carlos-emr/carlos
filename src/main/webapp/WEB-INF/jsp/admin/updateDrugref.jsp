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
            // The newest history timestamp this page has seen from a verify probe, and a copy
            // of it frozen at the moment a run starts. On a DrugRef too old for getUpdateStatus
            // there is no outcome to read, and the timestamp moving is the only evidence a run
            // succeeded -- the history row is written at the end of a good rebuild and not at
            // all by a failed one. Frozen separately because the poll re-renders verify, which
            // would otherwise overwrite the baseline with the value being compared against.
            var lastVerifiedUpdate = null;
            var lastUpdateBeforeRun = null;
            // Whether a verify probe has answered definitely at all. Distinct from the timestamp
            // above being null, which is ALSO what a healthy install that has never been updated
            // answers -- and there the baseline is perfectly well known ("no history yet"), so a
            // timestamp appearing afterwards is proof the first rebuild worked. Conflating the
            // two reported a successful first-ever update as an unknown outcome.
            var baselineKnown = false;
            var baselineKnownBeforeRun = false;

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

            // Read on _admin / _admin.misc opens this page; only WRITE may start a rebuild.
            // Set by ViewUpdateDrugref2Action from the same predicate RxUpdateDrugref2Action
            // enforces, so the two cannot drift apart.
            var canTriggerUpdate = ${canTriggerUpdate ? 'true' : 'false'};

            function show(id, visible) {
                // One choke point rather than a guard at each of the seventeen call sites: a
                // view-only administrator must never be offered a trigger the action will refuse
                // with an HTML 500, and a future branch that forgets to check would reintroduce
                // exactly that. This is presentation only -- the control is the refusal in
                // RxUpdateDrugref2Action, which does not consult anything the browser sends.
                if (id === 'updateButton' && !canTriggerUpdate) {
                    visible = false;
                }
                var el = document.getElementById(id);
                if (el) {
                    el.style.display = visible ? 'block' : 'none';
                }
            }

            // Keep the strings this page builds ASCII. The JSP declares no page encoding and
            // web.xml sets none, so Jasper reads the source as ISO-8859-1: a UTF-8 em dash in
            // a JS literal reaches the operator as three mojibake characters. Seen live on
            // the packaged install; the bundle strings are unaffected (properties files are
            // decoded separately).
            function setResult(text, kind) {
                var el = document.getElementById('updateResult');
                el.textContent = text;
                el.className = kind ? 'alert alert-' + kind + ' mt-3' : '';
                el.style.display = text ? 'block' : 'none';
            }

            function renderVerify(json) {
                // An all-null payload is RxUpdateDrugref2Action's unavailable fallback, not a
                // database that has never been updated. Telling the operator to press Update
                // when DrugRef cannot be reached invites a second run on top of one that may
                // still be going, so the trigger is hidden until the status is definite.
                if (!json || (json.lastUpdate == null && json.drugDatabase == null && json.version == null)) {
                    document.getElementById('dbInfo').textContent = 'Drugref database is unavailable. Contact support.';
                    show('dbInfo', true);
                    show('statusDisplay', false);
                    show('updateButton', false);
                } else if (json.lastUpdate == null) {
                    // Definite: DrugRef answered, and it has no history. A known baseline.
                    baselineKnown = true;
                    lastVerifiedUpdate = null;
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
                    baselineKnown = true;
                    lastVerifiedUpdate = json.lastUpdate;
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
                        // Hidden, not shown: this is the transport-level twin of the
                        // all-null branch in renderVerify, and it hid the trigger for the
                        // same reason. An unreachable DrugRef says nothing about whether a
                        // run is going, so offering Update here invites a second one on top
                        // of the first.
                        document.getElementById('dbInfo').textContent = 'Drugref database is unavailable. Contact support.';
                        show('dbInfo', true);
                        show('statusDisplay', false);
                        show('updateButton', false);
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

            function schedulePoll() {
                // Exactly one timer chain. Repeat clicks used to each start their own, and
                // overwriting pollTimer orphaned the earlier ones rather than stopping them.
                if (pollTimer) {
                    clearTimeout(pollTimer);
                }
                pollTimer = setTimeout(pollStatus, POLL_MS);
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
                                    schedulePoll();
                                } else if (!v || (v.lastUpdate == null && v.drugDatabase == null
                                        && v.version == null)) {
                                    // DrugRef stopped answering mid-run. An ALL-null payload is
                                    // the action's unavailable fallback; testing lastUpdate
                                    // alone also caught a healthy DrugRef that has simply never
                                    // been updated, and then accused it of being down and
                                    // polled forever with the trigger hidden. "Cannot tell" is
                                    // still not "finished": reporting success here would say
                                    // the rebuild completed when it may have died with the
                                    // tables half-built. Keep looking.
                                    setResult('DrugRef stopped responding, so the outcome of this update is '
                                        + 'unknown. Still checking. If this persists, check the DrugRef service '
                                        + '(journalctl -u carlos-emr) before prescribing.', 'danger');
                                    show('updateButton', false);
                                    schedulePoll();
                                } else if (!baselineKnownBeforeRun) {
                                    // The page never saw a pre-run timestamp (opened mid-run),
                                    // so there is nothing to compare against and neither
                                    // "succeeded" nor "failed" can be claimed. Say that, in
                                    // those words, rather than picking one.
                                    setResult('The update has ended. This DrugRef build cannot report '
                                        + 'whether it succeeded, and this page did not see the previous '
                                        + 'timestamp to compare against. DrugRef reports last updated '
                                        + (v.lastUpdate || 'never')
                                        + ' - confirm drug search before prescribing.', 'warning');
                                    show('updateButton', true);
                                } else if (v.lastUpdate != null && v.lastUpdate !== lastUpdateBeforeRun) {
                                    // The history timestamp MOVED. On a DrugRef too old to
                                    // report an outcome, that is the only positive evidence a
                                    // run finished: the row is written at the end of a
                                    // successful rebuild and not at all by a failed one.
                                    setResult('Update finished at ' + v.lastUpdate + '.', 'success');
                                } else {
                                    // The run ended and the timestamp did NOT move. Reporting
                                    // "Update finished at <date>" here -- as this did -- painted
                                    // a green success built entirely on the "updating" flag
                                    // having cleared, quoting the PRE-RUN date back at the
                                    // operator. A failed run clears that flag too.
                                    setResult('The update has ended, but DrugRef reports no new update'
                                        + (v.lastUpdate ? ' (still ' + v.lastUpdate + ')' : '')
                                        + ', so it most likely FAILED. This DrugRef build is too old to '
                                        + 'report why - check the service log (journalctl -u carlos-emr) '
                                        + 'and confirm drug search before prescribing.', 'danger');
                                    show('updateButton', true);
                                }
                            });
                        }
                        if (running) {
                            schedulePoll();
                        } else {
                            // Not RUNNING. That is usually the end of the run -- but it is also
                            // what a status of IDLE looks like in the moment before a just-started
                            // worker publishes RUNNING. Refresh the panel, and if the verify probe
                            // still says "updating" keep following instead of stopping here, which
                            // used to strand the page on "database is updating" until a reload.
                            return getUpdateTime().then(function (v) {
                                if (v && v.lastUpdate === 'updating') {
                                    show('updateButton', false);
                                    schedulePoll();
                                }
                            });
                        }
                    })
                    .catch(function (error) {
                        console.error('Error reading update status:', error);
                        schedulePoll();
                    });
            }

            function updateDB() {
                setResult('Starting the update...', 'info');
                // Hidden now, not in startUpdate(): the re-probe below is a round trip, and the
                // trigger must not stay clickable across it.
                show('updateButton', false);
                // Re-probe the baseline instead of reusing the one from page load. On a DrugRef
                // too old to report an outcome, the legacy fallback decides success by whether
                // the history timestamp MOVED, so a stale baseline is not a cosmetic problem:
                // if another administrator completed an update while this page sat open, the
                // timestamp has already moved, and THIS run -- however it ends -- would be
                // reported as "Update finished at <their date>". A failed rebuild announced as
                // a success is the one outcome this panel exists to prevent.
                //
                // The probe cannot fail the run: DrugRef refuses to start a second update while
                // one is going, so the only cost of an unreachable probe is an unknown
                // baseline, which the fallback already reports honestly rather than guessing.
                verifyForBaseline()
                    .then(startUpdate)
                    .catch(function (error) {
                        console.error('Could not re-read the baseline before updating:', error);
                        baselineKnown = false;
                        startUpdate();
                    });
            }

            // Re-reads the last-update timestamp and refreshes the panel with it, so the
            // baseline frozen a moment later is what DrugRef reports NOW, not at page load.
            function verifyForBaseline() {
                return callDrugref('verify').then(function (json) {
                    var unavailable = !json
                            || (json.lastUpdate == null && json.drugDatabase == null && json.version == null);
                    if (unavailable || json.lastUpdate === 'updating') {
                        // No usable baseline. Either DrugRef could not be reached, or a run is
                        // already going -- and a timestamp frozen mid-run belongs to whatever
                        // that run does, not to this one. Marking it unknown routes the legacy
                        // fallback to its "cannot tell" message instead of a guess; leaving the
                        // page-load value in place would be the stale baseline this re-probe
                        // exists to eliminate.
                        baselineKnown = false;
                        return;
                    }
                    renderVerify(json);
                });
            }

            function startUpdate() {
                // Frozen immediately before the POST. If the page was opened mid-run, or the
                // re-probe above could not reach DrugRef, this is unknown -- and the legacy
                // fallback says so rather than guessing either way.
                lastUpdateBeforeRun = lastVerifiedUpdate;
                baselineKnownBeforeRun = baselineKnown;
                // Again: the re-probe calls renderVerify(), which shows the trigger when the
                // database looks updatable. That is right for the panel and wrong for the
                // moment a run is starting, so the last word belongs here.
                show('updateButton', false);
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
                            show('updateButton', false);
                            pollStatus();
                        } else {
                            setResult('The update could not be started: DrugRef did not answer. Check that the '
                                + 'DrugRef service is running (drug lookups also need it).', 'danger');
                            // Nothing was started, so the trigger comes back: the operator can
                            // start the service or fix the proxy and retry in place. It is
                            // hidden before the POST to stop a double-click across the round
                            // trip, and leaving it hidden here would strand them on a dead page
                            // with a reload as the only way forward. show() still withholds it
                            // from anyone without write rights.
                            show('updateButton', true);
                        }
                    })
                    .catch(function (error) {
                        console.error('Error updating database:', error);
                        setResult('The update could not be started: ' + error.message, 'danger');
                        // Same reasoning as above: the request failed, so no run is in flight
                        // and a retry is exactly what the operator should be able to do.
                        show('updateButton', true);
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
                                schedulePoll();
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
                                schedulePoll();
                            }
                        }).catch(function (error) {
                            console.warn('Update status not available:', error);
                        });
                    })
                    .catch(function (err) {
                        console.warn('Skipping getUpdateTime - CSRF token not available:', err);
                        document.getElementById('dbInfo').textContent =
                            'Could not load CSRF token. Refresh the page or contact support.';
                        // No token means every POST this page makes is rejected, the probe
                        // included, so the state is unknown and the trigger would fail if
                        // pressed.
                        show('updateButton', false);
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
        <div id="updateButton" style="display:none;">
          <a id="updatedb" onclick="updateDB();" href="javascript:void(0);"
              class="btn btn-primary"><fmt:message key="admin.admin.UpdateDrugref"/></a>
        </div>
        <div id="updateResult" role="status" style="display:none;"></div>
    </div>
    </body>

</html>