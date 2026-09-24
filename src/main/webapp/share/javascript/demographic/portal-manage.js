/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

/*
 * Staff page for one patient's portal access (demographic/portalManage).
 *
 * Reads demographic/portalPanel and acts through demographic/portalInvite and demographic/portalAccount.
 * Every element is built with textContent, never innerHTML: invite and account fields come from the
 * portal, and a refusal the page has no translation for is shown as the server worded it, and none of
 * it may become markup. Delivery outcomes and known refusals arrive as codes and are translated here. POSTs carry the
 * CSRF-TOKEN header from csrf-token.jspf, because fetch() is not wrapped by CSRFGuard's client script.
 *
 * @since 2026-09-22
 */
(function () {
    'use strict';

    /**
     * The page's decisions, free of the DOM so they can be tested in Node
     * (scripts/portal-manage.test.js).
     *
     * @param messages Map of translated page text, keyed as in the JSP's #portal-messages list
     */
    function createLogic(messages) {
        function message(key) {
            return messages.has(key) ? messages.get(key) : null;
        }

        function text(key) {
            var item = message(key);
            return item !== null ? item : key;
        }

        /** Words a delivery attempt: its state, why it stands there, and whether a code was left live. */
        function describe(delivery) {
            var parts = [text('deliveries.state.' + delivery.state)];
            if (delivery.outcome) {
                parts.push(text('deliveries.outcome.' + delivery.outcome));
            }
            if (delivery.revokeFailed) {
                parts.push(text('deliveries.revokeFailed'));
            }
            if (delivery.outcome === 'commit_unconfirmed' && delivery.supersededInviteId) {
                // The portal retires the old code as it activates a replacement, so an unconfirmed
                // replacement may have taken the old code with it.
                parts.push(text('deliveries.replacementMayBeLost'));
            }
            return parts;
        }

        /** A refusal the page knows by its code is shown translated; any other keeps the server's wording. */
        function refusal(body) {
            if (body && body.reason && message('refusal.' + body.reason)) {
                return text('refusal.' + body.reason);
            }
            return body && body.message ? body.message : text('error.generic');
        }

        /**
         * Whether a handled request is good news. An attempt that stopped short still answers 200, and a
         * sent invitation whose chart note failed still needs staff to act.
         */
        function isGoodNews(delivery) {
            return !delivery || (delivery.state === 'sent' && delivery.outcome !== 'chart_note_failed');
        }

        /** Whether to offer withdrawing a stuck earlier attempt: asked once, never on the retry itself. */
        function offersWithdrawal(body, params) {
            return Boolean(body && body.reason === 'stale_attempt_exists' && !params.withdrawStale);
        }

        return {message: message, text: text, describe: describe, refusal: refusal, isGoodNews: isGoodNews,
            offersWithdrawal: offersWithdrawal};
    }

    if (typeof module !== 'undefined' && module.exports) {
        module.exports = {createLogic: createLogic};
        return;
    }

    var root = document.getElementById('portal-manage');
    if (!root) {
        return;
    }
    var context = root.dataset.context || '';
    var demographicNo = root.dataset.demographicNo;
    var can = {
        invite: root.dataset.canInvite === 'true',
        recover: root.dataset.canRecover === 'true',
        revoke: root.dataset.canRevoke === 'true',
        setAccess: root.dataset.canSetAccess === 'true',
        unlock: root.dataset.canUnlock === 'true'
    };
    var statusBox = document.getElementById('portal-status');
    var inviteStatusBox = document.getElementById('portal-invite-status');
    var lastInvites = [];
    var busy = false;
    var loadSequence = 0;

    // Read once into a map: keys include values from the portal (an invitation's status), which must
    // never be spliced into a CSS selector.
    var messages = new Map();
    document.querySelectorAll('#portal-messages [data-key]').forEach(function (item) {
        messages.set(item.dataset.key, item.textContent);
    });
    var logic = createLogic(messages);
    var message = logic.message;
    var text = logic.text;
    var describe = logic.describe;
    var refusal = logic.refusal;

    function element(tag, className, content) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (content !== undefined && content !== null) {
            node.textContent = String(content);
        }
        return node;
    }

    /** A button in the page's style; variant is primary, secondary or danger. */
    function button(label, variant, onClick, small) {
        var node = element('button', 'portal-button portal-button--' + variant
            + (small ? ' portal-button--small' : ''), label);
        node.type = 'button';
        node.addEventListener('click', onClick);
        return node;
    }

    function when(value) {
        if (!value) {
            return '';
        }
        var date = new Date(value);
        return isNaN(date.getTime()) ? String(value) : date.toLocaleString();
    }

    /**
     * Shows a message; given several lines, the first is the headline and the rest explain it. It goes in
     * box (the banner at the top by default, or the one beside the invite button) and clears the other,
     * so only the latest result is on screen.
     */
    function showStatus(message, ok, box) {
        var target = box || statusBox;
        var lines = Array.isArray(message) ? message : [message];
        target.replaceChildren(element(lines.length > 1 ? 'strong' : 'span', null, lines[0]));
        lines.slice(1).forEach(function (line) {
            target.appendChild(element('div', null, line));
        });
        target.classList.toggle('portal-status--error', !ok);
        target.hidden = false;
        [statusBox, inviteStatusBox].forEach(function (other) {
            if (other && other !== target) {
                other.hidden = true;
            }
        });
    }

    async function csrfToken() {
        if (window.csrfTokenReady) {
            try {
                await window.csrfTokenReady;
            } catch (ignored) {
                // The input below is checked either way; a missing token surfaces as a refused POST.
            }
        }
        var input = document.querySelector('input[name="CSRF-TOKEN"]');
        return input ? input.value : '';
    }

    async function call(method, path, params) {
        var query = new URLSearchParams(params || {});
        query.set('demographicNo', demographicNo);
        var options = {
            method: method,
            credentials: 'same-origin',
            headers: {'X-Requested-With': 'XMLHttpRequest'}
        };
        var url = context + path;
        if (method === 'GET') {
            url += '?' + query.toString();
        } else {
            options.headers['Content-Type'] = 'application/x-www-form-urlencoded';
            options.headers['CSRF-TOKEN'] = await csrfToken();
            options.body = query.toString();
        }
        var response = await fetch(url, options);
        var payload = null;
        try {
            payload = await response.json();
        } catch (ignored) {
            // A non-JSON answer (for example a CSRF rejection page) is reported generically below.
        }
        return {status: response.status, payload: payload};
    }

    /** Disables every control while a request runs, so a double click cannot start a second delivery. */
    function setBusy(value) {
        busy = value;
        root.setAttribute('aria-busy', value ? 'true' : 'false');
        root.querySelectorAll('button').forEach(function (control) {
            control.disabled = value;
        });
    }

    /** Performs an action and reloads the panel; its result is shown in box, or the top banner. */
    async function act(path, params, confirmation, box) {
        if (busy || (confirmation && !window.confirm(confirmation))) {
            return;
        }
        setBusy(true);
        try {
            await perform(path, params, box);
            await load();
        } finally {
            setBusy(false);
        }
    }

    async function perform(path, params, box) {
        var result;
        try {
            result = await call('POST', path, params);
        } catch (failure) {
            showStatus(text('error.generic'), false, box);
            return;
        }
        var body = result.payload;
        if (body && body.ok) {
            // An attempt that stopped short still answers 200: the request was handled, the invitation was
            // not necessarily delivered. Only a sent one is good news, and its state label alone does not
            // tell staff what to do, so the attempt's own explanation is shown with it.
            var delivery = body.delivery;
            showStatus(delivery ? describe(delivery) : text('done'), logic.isGoodNews(delivery), box);
        } else if (logic.offersWithdrawal(body, params)) {
            // An earlier attempt stopped before its code was activated and may be blocking this one.
            // Nothing from it reached the patient, so withdrawing it is safe once staff agree.
            if (window.confirm(text('invites.confirmWithdrawStale'))) {
                await perform(path, Object.assign({}, params, {withdrawStale: 'true'}), box);
                return;
            }
            showStatus(refusal(body), false, box);
        } else {
            showStatus(refusal(body), false, box);
        }
    }

    function renderAccount(payload) {
        var box = document.getElementById('portal-account');
        box.replaceChildren();
        if (payload.accountError) {
            box.appendChild(element('p', 'portal-error', text('error.generic')));
            return;
        }
        var account = payload.account;
        if (account === undefined) {
            return;
        }
        // A patient with an account has nothing to be invited to; the server refuses it too.
        document.getElementById('portal-invite-form').hidden = !can.invite || account !== null;
        if (account === null) {
            box.appendChild(element('p', 'portal-muted', text('account.none')));
            return;
        }
        var disabled = account.status === 'disabled';
        var details = element('dl', 'portal-details');
        function fact(key, value) {
            details.appendChild(element('dt', null, text('account.field.' + key)));
            details.appendChild(element('dd', null, value));
        }
        fact('status', text(disabled ? 'account.disabled' : 'account.active'));
        fact('locked', text(account.locked ? 'yes' : 'no'));
        fact('resetRequired', text(account.forcePasswordReset ? 'yes' : 'no'));
        if (disabled) {
            fact('disabledAt', when(account.disabledAt));
            fact('disabledReason', account.disabledReason || '');
        }
        box.appendChild(details);

        var reason = null;
        if (can.setAccess && !disabled) {
            var field = element('div', 'portal-field');
            var label = element('label', null, text('account.disableReason'));
            label.htmlFor = 'portal-disable-reason';
            reason = element('input');
            reason.type = 'text';
            reason.id = 'portal-disable-reason';
            reason.maxLength = 64;
            reason.autocomplete = 'off';
            field.appendChild(label);
            field.appendChild(reason);
            box.appendChild(field);
        }
        var actions = element('div', 'portal-actions');
        if (can.unlock && account.locked) {
            actions.appendChild(button(text('account.unlock'), 'primary', function () {
                act('/demographic/portalAccount', {method: 'unlock'}, text('account.confirmUnlock'));
            }));
        }
        if (reason) {
            actions.appendChild(button(text('account.disable'), 'danger', function () {
                var why = reason.value.trim();
                if (!why || why.length > 64) {
                    showStatus(text('account.reasonRequired'), false);
                    reason.focus();
                    return;
                }
                act('/demographic/portalAccount', {method: 'access', enabled: 'false', reason: why},
                    text('account.confirmDisable'));
            }));
        }
        if (can.setAccess && disabled) {
            actions.appendChild(button(text('account.enable'), 'primary', function () {
                act('/demographic/portalAccount', {method: 'access', enabled: 'true'}, text('account.confirmEnable'));
            }));
        }
        if (actions.childElementCount) {
            box.appendChild(actions);
        }
    }

    function renderInvites(payload) {
        var box = document.getElementById('portal-invites');
        box.replaceChildren();
        if (payload.invitesError) {
            box.appendChild(element('p', 'portal-error', text('error.generic')));
            return;
        }
        lastInvites = payload.invites || [];
        if (lastInvites.length === 0) {
            box.appendChild(element('p', 'portal-muted', text('invites.none')));
            return;
        }
        var table = element('table', 'portal-table');
        var head = table.createTHead().insertRow();
        ['invites.status', 'invites.issued', 'invites.by', 'invites.expires', ''].forEach(function (key) {
            head.appendChild(element('th', null, key ? text(key) : ''));
        });
        var body = table.createTBody();
        lastInvites.forEach(function (invite) {
            var row = body.insertRow();
            // Each cell carries its column's name, shown beside it on a narrow screen where the header is hidden.
            function cell(key, value) {
                var node = row.insertCell();
                node.dataset.label = text(key);
                node.textContent = value;
            }
            cell('invites.status', text('invites.status.' + invite.status));
            cell('invites.issued', when(invite.lastIssuedAt));
            cell('invites.by', invite.lastIssuedBy || '');
            // A prepared invitation's expiry is the portal's deadline for committing delivery, not the
            // patient's, so it is not shown.
            cell('invites.expires', invite.status === 'pending' ? when(invite.expiresAt) : '');
            var actions = row.insertCell();
            if (invite.status === 'pending' && can.invite) {
                actions.appendChild(button(text('invites.resend'), 'secondary', function () {
                    act('/demographic/portalInvite', inviteParams({method: 'resend', inviteId: invite.inviteId}));
                }, true));
            }
            if ((invite.status === 'pending' || invite.status === 'prepared') && can.revoke) {
                actions.appendChild(button(text('invites.revoke'), 'danger', function () {
                    act('/demographic/portalInvite', {method: 'revoke', inviteId: invite.inviteId},
                        text('invites.confirmRevoke'));
                }, true));
            }
        });
        box.appendChild(table);
    }

    function renderDeliveries(payload) {
        var box = document.getElementById('portal-deliveries');
        box.replaceChildren();
        if (payload.deliveriesError) {
            box.appendChild(element('p', 'portal-error', text('error.generic')));
            return;
        }
        var deliveries = payload.deliveries || [];
        if (deliveries.length === 0) {
            box.appendChild(element('p', 'portal-muted', text('deliveries.none')));
            return;
        }
        var list = element('ul', 'portal-deliveries');
        deliveries.forEach(function (delivery) {
            var item = element('li');
            var description = describe(delivery);
            item.appendChild(element('div', 'portal-deliveries__state', description[0]));
            item.appendChild(element('div', 'portal-muted', text('deliveries.when') + ' ' + when(delivery.updatedAt)));
            description.slice(1).forEach(function (line) {
                item.appendChild(element('div', 'portal-deliveries__detail', line));
            });
            if (can.recover) {
                if (delivery.decisions && delivery.decisions.length) {
                    var actions = element('div', 'portal-actions');
                    delivery.decisions.forEach(function (decision) {
                        actions.appendChild(button(text('deliveries.decision.' + decision), 'secondary', function () {
                            // None of these can be undone: each withdraws or revokes a code, or signs a chart
                            // note, so each is confirmed first.
                            act('/demographic/portalInvite',
                                {method: 'recover', deliveryId: delivery.deliveryId, decision: decision},
                                message('deliveries.confirm.' + decision));
                        }, true));
                    });
                    item.appendChild(actions);
                } else if (!delivery.finished) {
                    item.appendChild(element('div', 'portal-muted', text('deliveries.waiting')));
                }
            }
            list.appendChild(item);
        });
        box.appendChild(list);
    }

    function inviteParams(params) {
        // The override is offered only when the chart records consent as unknown.
        var override = document.getElementById('portal-consent-override');
        if (override && override.checked) {
            params.consentOverride = 'true';
            params.consentOverrideReason = document.getElementById('portal-consent-reason').value;
        }
        params.channel = document.getElementById('portal-channel').value;
        return params;
    }

    async function load() {
        // Only the newest read may render: an older one finishing late would show a stale panel.
        var sequence = ++loadSequence;
        var result;
        try {
            result = await call('GET', '/demographic/portalPanel');
        } catch (failure) {
            if (sequence === loadSequence) {
                showStatus(text('error.generic'), false);
            }
            return;
        }
        if (sequence !== loadSequence) {
            return;
        }
        var payload = result.payload;
        if (!payload || result.status !== 200) {
            showStatus(refusal(payload), false);
            ['portal-account', 'portal-invites', 'portal-deliveries'].forEach(function (id) {
                document.getElementById(id).replaceChildren();
            });
            return;
        }
        renderAccount(payload);
        renderInvites(payload);
        renderDeliveries(payload);
    }

    document.getElementById('portal-refresh').addEventListener('click', function () {
        if (!busy) {
            statusBox.hidden = true;
            inviteStatusBox.hidden = true;
            load();
        }
    });

    var form = document.getElementById('portal-invite-form');
    if (can.invite) {
        form.hidden = false;
        var override = document.getElementById('portal-consent-override');
        if (override) {
            override.addEventListener('change', function (event) {
                document.getElementById('portal-consent-reason').hidden = !event.target.checked;
            });
        }
        form.addEventListener('submit', function (event) {
            event.preventDefault();
            var pending = lastInvites.some(function (invite) {
                return invite.status === 'pending';
            });
            if (pending && !window.confirm(text('invites.confirmReplace'))) {
                return;
            }
            act('/demographic/portalInvite', inviteParams({method: 'create', confirmReplace: pending ? 'true' : 'false'}),
                null, inviteStatusBox);
        });
    }

    load();
}());
