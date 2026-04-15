/**

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

 **/



let oscarAlert;

/**
 * Create and display a Bootstrap alert with the given message, type, and duration
 * @param {string} alertId - unique identifier for the alert div
 * @param {string} message - the message to display inside the alert
 * @param {string} alertType - type of the alert ('success', 'danger', 'warning')
 * @param {number} duration - time in seconds before the alert disappears
 * @param {event} onDismissEvent - action to trigger on alert dismiss event
 */
function createAndShowAlert(alertId, message, alertType, duration, onDismissEvent) {
    // Check if the alert already exists
    if (oscarAlert) {
        oscarAlert.dismissAlert();
    }
    oscarAlert = new OscarAlert(alertId, alertType, message, duration);

    if (onDismissEvent) {
        oscarAlert.setOnDismissEvent(onDismissEvent);
    }

    oscarAlert.injectInToParentBefore(document.body);

    oscarAlert.showAlert().then(undefined);
}

/**
 * Show an error alert
 */
function showErrorAlert() {
    this.createAndShowAlert('submit-error-alert', 'The form could not be saved. Please try again.', 'danger', 5, undefined);
}

/**
 * Show a success alert
 */
function showSuccessAlert(onDismissEvent) {
    this.createAndShowAlert('submit-success-alert', 'The form has been saved successfully.', 'success', 5, onDismissEvent);
}

function sleep(ms) {
    return new Promise(resolve => setTimeout(resolve, ms));
}


/**
 * The `OscarAlert` class, which is to create and manage
 * Bootstrap alert messages dynamically with customizable countdown timers and
 * dismissal functionality.Z
 * */
class OscarAlert {

    constructor(alertId, alertType, message, duration) {
        this.alertDiv = document.createElement('div');
        this.alertDiv.id = alertId;
        this.alertType = alertType;
        this.countdown = duration;
        this.duration = duration;
        this.alertDiv.className = `alert alert-${alertType} alert-dismissible fade oscar-alert`;
        this.alertDiv.role = 'alert';

        this.renderAlert(message);
        this.alertDiv.querySelector('.btn-close').addEventListener('click', () => this.dismissAlert());
    }

    counter() {
        if (this.countdown <= 0) {
            this.dismissAlert();
            return;
        }
        this.countdown--;
        const element = document.getElementById(`countdown-${this.alertDiv.id}`);
        if (element) {
            element.textContent = this.countdown.toString();
        }
    }

    startCountdown() {
        const counterFunc = () => this.counter();
        this.resetCountdownInterval();
        this.counterHandlerNumber = setInterval(counterFunc, 1000);
    }

    isVisible() {
        return document.getElementById(`${this.alertDiv.id}`) && this.alertDiv.classList.contains('show');
    }

    async showAlert() {
        if (this.isVisible()) {
            this.dismissAlert();
        }
        await sleep(50);
        this.updateAlertVisibility(true);
        this.startCountdown();
    }

    dismissAlert() {
        clearInterval(this.counterHandlerNumber);
        this.updateAlertVisibility(false);
        this.resetCountdownInterval();
        this.alertDiv.remove();
        if (this.onDismissEvent)
            this.onDismissEvent();
    }

    resetCountdownInterval() {
        this.countdown = this.duration;
    }

    injectInToParentBefore(parent) {
        parent.appendChild(this.alertDiv);
    }

    setOnDismissEvent(onDismissEvent) {
        this.alertDiv.addEventListener('close.bs.alert', onDismissEvent);
        this.onDismissEvent = onDismissEvent;
    }

    updateAlertVisibility(isShow) {
        if (isShow) {
            this.alertDiv.classList.add('show');
        } else {
            this.alertDiv.classList.remove('show');
        }
    }

    renderAlert(message) {
        this.alertDiv.replaceChildren();

        const label = document.createElement('strong');
        label.textContent = this.getLabel();

        const messageText = document.createTextNode(` ${String(message ?? '')}`);
        const lineBreak = document.createElement('br');

        const small = document.createElement('small');
        small.append(document.createTextNode(this.getDismissalMessage()));

        const countdown = document.createElement('span');
        countdown.id = `countdown-${this.alertDiv.id}`;
        countdown.textContent = this.countdown.toString();
        small.append(countdown);
        small.append(document.createTextNode(' seconds.'));

        const closeButton = document.createElement('button');
        closeButton.type = 'button';
        closeButton.className = 'btn-close';
        closeButton.setAttribute('data-bs-dismiss', 'alert');
        closeButton.setAttribute('aria-label', 'Close');

        this.alertDiv.append(label, messageText, lineBreak, document.createTextNode(' '), small, closeButton);
    }

    getDismissalMessage() {
        return this.alertType === 'success' ? 'This form will close in ' : 'This message will disappear in ';

    }

    getLabel() {
        const labels = {
            danger: 'Error!', warning: 'Warning!', success: 'Success!'
        };

        return labels[this.alertType] || 'Unknown';
    }
}
