<%--
    Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.

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
  Purpose: "Request payment through the patient portal" section of the Ontario third-party
           payments page (billing through the portal, Phase 0).

  Included by billingON3rdPayments.jsp with the request parameter portalPaymentBillingNo. It
  renders nothing unless the patient portal is configured and the user holds the portal payment
  object and _billing read for this invoice's patient: the patient is looked up here from the
  invoice, never taken from the browser. Sending and withdrawing additionally need write.

  The amount is never entered: the server reads the invoice's balance owing and GST when the
  request is sent. The script drives POST demographic/portalPaymentRequest
  (method=create|list|withdraw) with the CSRF-TOKEN header from the csrf-token.jspf include (the
  fragment has no non-GET form of its own). Every value is written with textContent.

  @since 2026-11-27
--%>
<%@ page import="io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao" %>
<%@ page import="io.github.carlos_emr.carlos.commn.model.BillingONCHeader1" %>
<%@ page import="io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings" %>
<%@ page import="io.github.carlos_emr.carlos.integration.patientportal.PortalStaffContextResolver" %>
<%@ page import="io.github.carlos_emr.carlos.managers.SecurityInfoManager" %>
<%@ page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="java.time.LocalDate" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    if (!PatientPortalSettings.isConfigured()) { return; }
    int portalPaymentBilling;
    try { portalPaymentBilling = Integer.parseInt(request.getParameter("portalPaymentBillingNo")); }
    catch (NumberFormatException invalid) { return; }
    LoggedInInfo portalPaymentSession = LoggedInInfo.getLoggedInInfoFromSession(request);
    if (portalPaymentBilling <= 0 || portalPaymentSession == null) { return; }
    BillingONCHeader1 portalPaymentInvoice = SpringUtils.getBean(BillingONCHeader1Dao.class).find(portalPaymentBilling);
    if (portalPaymentInvoice == null || portalPaymentInvoice.getDemographicNo() == null) { return; }
    int portalPaymentPatient = portalPaymentInvoice.getDemographicNo();
    SecurityInfoManager portalPaymentSecurity = SpringUtils.getBean(SecurityInfoManager.class);
    String portalPaymentScope = String.valueOf(portalPaymentPatient);
    if (!portalPaymentSecurity.hasPrivilege(portalPaymentSession, "_demographic", SecurityInfoManager.READ, portalPaymentScope)
            || !portalPaymentSecurity.isAllowedAccessToPatientRecord(portalPaymentSession, portalPaymentPatient)
            || !portalPaymentSecurity.hasPrivilege(portalPaymentSession, PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST,
                    SecurityInfoManager.READ, portalPaymentScope)
            || !portalPaymentSecurity.hasPrivilege(portalPaymentSession, "_billing", SecurityInfoManager.READ, portalPaymentScope)) {
        return;
    }
    boolean portalPaymentMayWrite = portalPaymentSecurity.hasPrivilege(portalPaymentSession,
            PortalStaffContextResolver.OBJECT_PAYMENT_REQUEST, SecurityInfoManager.WRITE, portalPaymentScope)
            && portalPaymentSecurity.hasPrivilege(portalPaymentSession, "_billing", SecurityInfoManager.WRITE, portalPaymentScope);
    String portalPaymentDefaultDue = LocalDate.now().plusDays(30).toString();
%>
<%@ include file="/WEB-INF/jspf/csrf-token.jspf" %>
<section id="portalPaymentRequest" class="portal-payment-request" style="margin:8px 0;padding:6px;border:1px solid #999;background:#f4f4ff;"
         data-endpoint="${carlos:forHtmlAttribute(pageContext.request.contextPath)}/demographic/portalPaymentRequest"
         data-patient="<%= portalPaymentPatient %>"
         data-billing="<%= portalPaymentBilling %>"
         data-may-write="<%= portalPaymentMayWrite %>">
    <h3 style="margin:0 0 4px 0;font-family:Helvetica,Arial,sans-serif;">REQUEST PAYMENT THROUGH THE PATIENT PORTAL</h3>
    <p style="margin:0 0 6px 0;">The patient is asked to pay the balance owing now. The amount is read from this invoice when you send it,
        and the patient reads it in the portal under Messages with the clinic's payment instructions. The portal takes no payment: record
        the payment above, and the request closes when the balance reaches zero.</p>
    <% if (portalPaymentMayWrite) { %>
    <div data-role="create">
        <label>For
            <select data-role="category">
                <option value="uninsured_service">An uninsured service</option>
                <option value="form_or_note">A form or note</option>
                <option value="missed_appointment">A missed appointment</option>
                <option value="other">Other</option>
            </select>
        </label>
        <label>Due
            <input type="date" data-role="dueOn" value="<carlos:encode value='<%= portalPaymentDefaultDue %>' context='htmlAttribute'/>">
        </label>
        <button type="button" data-role="send">Send payment request</button>
    </div>
    <% } %>
    <p data-role="status" role="status" aria-live="polite" style="min-height:1.2em;margin:4px 0;"></p>
    <table width="100%" border="0" data-role="table" hidden>
        <thead>
        <tr><th align="left">Status</th><th align="left">For</th><th align="left">Amount</th><th align="left">Due</th><th align="left">Sent</th><th></th></tr>
        </thead>
        <tbody data-role="rows"></tbody>
    </table>
</section>
<script type="text/javascript">
    (function () {
        var root = document.getElementById('portalPaymentRequest');
        if (!root) { return; }
        var endpoint = root.getAttribute('data-endpoint');
        var patient = root.getAttribute('data-patient');
        var billing = root.getAttribute('data-billing');
        var mayWrite = root.getAttribute('data-may-write') === 'true';
        var statusLine = root.querySelector('[data-role="status"]');
        var table = root.querySelector('[data-role="table"]');
        var rows = root.querySelector('[data-role="rows"]');
        var LABELS = {
            uninsured_service: 'An uninsured service', form_or_note: 'A form or note',
            missed_appointment: 'A missed appointment', other: 'Other'
        };
        var STATES = {
            pending: 'Not confirmed by the portal', open: 'Sent to the patient', paid: 'Paid', withdrawn: 'Withdrawn'
        };
        var REFUSALS = {
            portal_account_inactive: 'This patient does not have an active portal account. Contact the patient directly.',
            nothing_owing: 'This invoice has nothing owing, so there is nothing to request.',
            open_request_differs: 'A different request is already open for this invoice. Withdraw it first.',
            due_date_invalid: 'The due date must be from today to one year ahead.',
            request_paid: 'This payment request has been paid and cannot be changed.',
            invoice_not_found: 'The invoice was not found for this patient.'
        };

        function say(text) { statusLine.textContent = text; }
        function money(cents) {
            var whole = Math.floor(Math.abs(cents) / 100), part = ('0' + (Math.abs(cents) % 100)).slice(-2);
            return '$' + whole + '.' + part;
        }
        function token() {
            var input = document.querySelector('input[name="CSRF-TOKEN"]');
            return input ? input.value : '';
        }
        function post(fields) {
            var body = new URLSearchParams();
            body.set('demographicNo', patient);
            body.set('billingNo', billing);
            Object.keys(fields).forEach(function (key) { body.set(key, fields[key]); });
            return fetch(endpoint, {
                method: 'POST', credentials: 'same-origin',
                headers: {'Content-Type': 'application/x-www-form-urlencoded', 'CSRF-TOKEN': token(), 'X-Requested-With': 'XMLHttpRequest'},
                body: body.toString()
            }).then(function (response) {
                return response.json().then(function (json) { return {status: response.status, json: json}; });
            });
        }
        function cell(row, text) {
            var td = document.createElement('td');
            td.textContent = text;
            row.appendChild(td);
            return td;
        }
        function render(requests) {
            rows.textContent = '';
            table.hidden = requests.length === 0;
            requests.forEach(function (item) {
                var tr = document.createElement('tr');
                cell(tr, STATES[item.status] || item.status);
                cell(tr, LABELS[item.category] || item.category);
                cell(tr, money(item.amountCents));
                cell(tr, item.dueOn);
                cell(tr, (item.createdAt || '').slice(0, 10));
                var action = cell(tr, '');
                if (mayWrite && (item.status === 'open' || item.status === 'pending')) {
                    var button = document.createElement('button');
                    button.type = 'button';
                    button.textContent = 'Withdraw';
                    button.addEventListener('click', function () {
                        button.disabled = true;
                        say('Withdrawing…');
                        post({method: 'withdraw', requestId: item.id}).then(function (result) {
                            say(result.json.ok ? 'Withdrawn.' : (REFUSALS[result.json.reason] || 'The request could not be withdrawn.'));
                            refresh();
                        }).catch(function () { say('The request could not be withdrawn. Try again.'); button.disabled = false; });
                    });
                    action.appendChild(button);
                }
                rows.appendChild(tr);
            });
        }
        function refresh() {
            return post({method: 'list'}).then(function (result) {
                if (result.json.ok) { render(result.json.requests); }
                else { say(REFUSALS[result.json.reason] || 'Payment requests are unavailable right now.'); }
            }).catch(function () { say('Payment requests are unavailable right now.'); });
        }
        var send = root.querySelector('[data-role="send"]');
        if (send) {
            send.addEventListener('click', function () {
                send.disabled = true;
                say('Sending…');
                post({
                    method: 'create',
                    category: root.querySelector('[data-role="category"]').value,
                    dueOn: root.querySelector('[data-role="dueOn"]').value
                }).then(function (result) {
                    if (result.json.ok) {
                        say(result.json.created ? 'Sent. The patient will see it in the portal.' : 'This request was already sent.');
                    } else {
                        say(REFUSALS[result.json.reason] || 'The request could not be sent. Try again, or contact the patient directly.');
                    }
                    send.disabled = false;
                    refresh();
                }).catch(function () { say('The request may not have been sent. Check the list below, then try again.'); send.disabled = false; });
            });
        }
        refresh();
    }());
</script>
            request_unconfirmed: 'The portal has not confirmed this request. Retry sending it before withdrawing it.',
