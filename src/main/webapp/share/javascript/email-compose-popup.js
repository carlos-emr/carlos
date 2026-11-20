/* Copyright (c) 2026 CARLOS Contributors. Licensed under the GNU GPL. */
let emailComposePopupSequence = 0;

/**
 * Opens an email-copy composer through a CSRF-protected POST in a new window.
 * The popup is reserved before awaiting token initialization to preserve the user gesture.
 * @param {string} url Same-origin compose URL with method and logId query parameters.
 * @param {number} width Popup width in pixels.
 * @param {number} height Popup height in pixels.
 * @param {string} failureMessage Localized popup/token preparation error shown to the user.
 * @returns {Promise<void>} Resolves after submission or a visible preparation error.
 */
async function openEmailCompose(url, width, height, failureMessage) {
    let popup;
    let form;
    try {
        const destination = new URL(url, window.location.href);
        if (destination.origin !== window.location.origin) {
            throw new Error('Email compose destination must be same-origin');
        }
        const name = 'emailCompose' + Date.now() + (++emailComposePopupSequence);
        popup = window.open('', name, 'width=' + width + ',height=' + height);
        if (!popup) {
            throw new Error('Email compose popup was blocked');
        }
        if (window.csrfTokenReady) {
            await window.csrfTokenReady;
        }
        const csrfToken = document.querySelector('input[name="CSRF-TOKEN"]');
        if (!csrfToken || !csrfToken.value) {
            throw new Error('Email compose CSRF token is unavailable');
        }
        destination.searchParams.set('CSRF-TOKEN', csrfToken.value);
        form = document.createElement('form');
        form.method = 'POST';
        form.action = destination.origin + destination.pathname;
        form.target = name;
        for (const [key, value] of destination.searchParams) {
            const input = document.createElement('input');
            input.type = 'hidden';
            input.name = key;
            input.value = value;
            form.appendChild(input);
        }
        document.body.appendChild(form);
        HTMLFormElement.prototype.submit.call(form);
    } catch (error) {
        if (popup) popup.close();
        window.alert(failureMessage);
    } finally {
        if (form) form.remove();
    }
}
