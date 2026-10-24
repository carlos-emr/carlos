/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/** Suppress repeated user activation; the session identity also rejects direct POST replays. */
(function () {
    let pending = false;
    function mark(submitting) {
        pending = submitting;
        const form = document.getElementById('composeMessage');
        if (form) form.querySelectorAll('button[type="submit"], #sendArchive').forEach(button => {
            button.disabled = submitting;
        });
    }
    function begin() {
        if (pending) return false;
        mark(true);
        return true;
    }
    window.carlosMessengerSubmission = { begin };
    window.addEventListener('submit', function (event) {
        if (event.target.id !== 'composeMessage' || event.defaultPrevented) return;
        if (!begin()) {
            event.preventDefault();
            return;
        }
        // Later listeners can cancel the first submit; wait until dispatch has finished.
        setTimeout(function () {
            if (event.defaultPrevented) mark(false);
        }, 0);
    });
    window.addEventListener('pageshow', function (event) {
        if (event.persisted) mark(false);
    });
    if (window.navigation && typeof window.navigation.addEventListener === 'function') {
        window.navigation.addEventListener('navigateerror', function () { mark(false); });
    } else {
        window.addEventListener('beforeunload', function (event) {
            setTimeout(function () {
                if (event.defaultPrevented || event.returnValue) { // NOSONAR -- legacy unload handlers set returnValue
                    mark(false);
                }
            }, 0);
        });
    }
})();
