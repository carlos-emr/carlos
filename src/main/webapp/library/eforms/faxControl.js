/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* Legacy eForms set otherFaxInput from ready/onload handlers, often before the toolbar arrives. */
(function () {
    'use strict';
    if (window.carlosEformFax) return;

    function ensureInput() {
        var input = document.getElementById('otherFaxInput');
        var form = document.forms[0];
        if (!form) return input;
        if (!input) {
            input = document.createElement('input');
            input.type = 'hidden';
            input.id = 'otherFaxInput';
            form.appendChild(input);
        }
        if (!document.getElementById('otherFaxSelect')) {
            var select = document.createElement('select');
            select.id = 'otherFaxSelect';
            select.hidden = true;
            form.appendChild(select);
        }
        return input;
    }

    var selectedSource = null;
    // The value the clinician last typed into otherFaxInput, so a later programmatic copy of a
    // different number (a designer button, AddOtherFaxProvider) is never mistaken for typing.
    var typedValue = null;
    document.addEventListener('change', function (event) {
        if (['faxnumList', 'otherFaxSelect'].includes(event.target.id)) selectedSource = event.target.id;
    });
    // A number the clinician types into the form's own other-fax field is a new explicit choice.
    // Only trusted typing counts: AddOtherFax/AddOtherFaxProvider copy values in programmatically
    // and must not make a copied list value authoritative. The field becomes the source (rather
    // than clearing the source) so an older list choice cannot win over what was just typed.
    document.addEventListener('input', function (event) {
        if (event.isTrusted && event.target.id === 'otherFaxInput') {
            selectedSource = 'otherFaxInput';
            typedValue = event.target.value.trim();
        }
    });

    function selectedOption(id) {
        var select = document.getElementById(id);
        var option = select && select.options && select.options[select.selectedIndex];
        return option && option.value.trim() ? option : null;
    }

    function recipient() {
        var input = ensureInput();
        var option;
        // The list the clinician changed last is authoritative. When they cleared it, falling
        // through to the other list (or to the otherFaxInput value AddOtherFax/AddOtherFaxProvider
        // copied from it) would fax a recipient they had just deselected, so fail closed with no
        // number rather than misroute the document.
        if (selectedSource === 'otherFaxInput') {
            // manual: typed by the clinician, so the toolbar keeps it when the name is edited.
            var fax = input ? input.value.trim() : '';
            return {name: '', fax: fax, manual: fax !== '' && fax === typedValue};
        }
        if (selectedSource) {
            option = selectedOption(selectedSource);
            return option
                ? {name: option.getAttribute('name') || option.textContent.trim(), fax: option.value.trim()}
                : {name: '', fax: ''};
        }
        for (var id of ['otherFaxSelect', 'faxnumList']) {
            option = selectedOption(id);
            if (option) return {name: option.getAttribute('name') || option.textContent.trim(), fax: option.value.trim()};
        }
        if (input && input.value.trim()) return {name: '', fax: input.value.trim()};
        var fax = document.querySelector('[name="recipientFaxNumber"]:not([data-carlos-workflow-flag])');
        var name = document.querySelector('[name="recipient"]:not([data-carlos-workflow-flag])');
        return {name: name ? name.value : '', fax: fax ? fax.value.trim() : ''};
    }

    window.carlosEformFax = {ensureInput: ensureInput, recipient: recipient};
    // Capture runs before ordinary DOMContentLoaded listeners (including jQuery ready), even when
    // a form registered those listeners before loading this library. Never duplicate an authored id.
    document.addEventListener('DOMContentLoaded', ensureInput, {capture: true, once: true});
    ensureInput();

    window.AddOtherFax = function () {
        var input = ensureInput();
        var select = document.getElementById('otherFaxSelect');
        // Promoting the number the clinician typed keeps it theirs; the select change below would
        // otherwise make the list its source and a later name edit would clear it.
        var keepTyped = selectedSource === 'otherFaxInput' && input && typedValue !== null
            && input.value.trim() === typedValue;
        if (input && select && input.value.trim()) {
            var option = Array.from(select.options).find(item => item.value === input.value.trim());
            if (!option) {
                option = new Option(input.value.trim(), input.value.trim());
                select.add(option);
            }
            select.value = option.value;
            select.dispatchEvent(new Event('change', {bubbles: true}));
            if (keepTyped) selectedSource = 'otherFaxInput';
        }
        if (input) input.dispatchEvent(new Event('change', {bubbles: true}));
        return false;
    };
    window.AddOtherFaxProvider = function () {
        var input = ensureInput();
        var select = document.getElementById('otherFaxSelect');
        if (input && select && select.value.trim()) {
            input.value = select.value;
            select.dispatchEvent(new Event('change', {bubbles: true}));
        }
        return false;
    };
    window.submitFaxButtonAjax = function () {
        if (typeof window.remoteFax === 'function') window.remoteFax();
        return false;
    };
}());
