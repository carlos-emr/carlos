class FormsTimedAutosave {

    constructor(formId, autosaveInterval, saveEndpointUrl, validationMethod, validationFailedMessage, messageDivName, promptSaveOnClose = false) {
        let formElement = document.getElementById(formId);
        if (!formElement) {
            console.error(`FormsTimedAutosave: formElement \'${formId}\' does not exist`)
        }
        this.formElement = formElement;
        this.idleTime = 0;
        this.changed = false;
        this.changeVersion = 0;
        this.saving = false;
        this.saveEndpointUrl = saveEndpointUrl;
        this.messageDivName = messageDivName;
        this.validationMethod = validationMethod;
        this.validationFailedMessage = validationFailedMessage;
        this.lastsavedDateString = "";

        // bind events
        let boundTimerIncrement = this.timerIncrement.bind(this);
        this.idleInterval = setInterval(boundTimerIncrement, autosaveInterval);
        let boundFormChangedEvent = this.formChangedEvent.bind(this);
        this.formElement.addEventListener('input', boundFormChangedEvent);
        this.formElement.addEventListener('change', boundFormChangedEvent);
        this.formElement.addEventListener('keydown', boundFormChangedEvent);

        if (promptSaveOnClose) {
            let boundPromptSaveBeforeClose = this.promptSaveBeforeClose.bind(this);
            window.addEventListener('beforeunload', boundPromptSaveBeforeClose);
        }

        // Zero the idle timer on change
        this.formElement.addEventListener('dataChanged', () => {
            this.idleTime = 0;
        });
    }

    timerIncrement() {
        this.idleTime += 1;
        if (this.idleTime > 1 && this.changed) {
            this.saveForm();
        }
    }

    saveForm() {
        if (this.validationMethod()) {
            this.sendRequest();
        } else {
            let lastSavedText = this.lastsavedDateString ? ' Last autosaved at ' + this.lastsavedDateString : "";
            this.setMessageDivs(`${this.validationFailedMessage}${lastSavedText}`);
        }
    }

    promptSaveBeforeClose(event) {
        if (this.changed) {
            event.preventDefault();
            event.returnValue = true; // any non-null return value causes browser to prompt user
        }
    }

    sendRequest() {
        // A slow save must not start another snapshot on every timer tick.
        if (this.saving) return;
        this.saving = true;
        const sentVersion = this.changeVersion;
        const httpRequest = new XMLHttpRequest();
        const formData = new FormData(this.formElement);
        formData.append('submit', 'autosaveAjax');

        // bind events
        let onSendRequestSuccessEvent = event => this.onSendRequestSuccess(event, sentVersion);
        httpRequest.addEventListener('load', onSendRequestSuccessEvent);
        let onSendRequestErrorEvent = this.onSendRequestError.bind(this);
        httpRequest.addEventListener('error', onSendRequestErrorEvent);
        httpRequest.addEventListener('abort', onSendRequestErrorEvent);
        httpRequest.addEventListener('timeout', onSendRequestErrorEvent);

        httpRequest.open('POST', this.saveEndpointUrl);
        httpRequest.timeout = 60000;
        if ([...formData.values()].some(value => typeof value !== 'string')) {
            httpRequest.send(formData);
        } else {
            // Rourke has hundreds of text/check fields. Use the same encoding as
            // manual Save so these fields do not exceed the multipart part limit.
            httpRequest.setRequestHeader('Content-Type', 'application/x-www-form-urlencoded;charset=UTF-8');
            httpRequest.send(new URLSearchParams(formData).toString());
        }
    }

    formChangedEvent(event) {
        this.changeVersion += 1;
        this.changed = true;
    }

    onSendRequestSuccess(event, sentVersion) {
        try {
            // Gateways and expired sessions can answer an autosave with HTML.
            // Only an explicit successful acknowledgement may clear the warning.
            if (event.target.status < 200 || event.target.status >= 300) throw new Error('Autosave failed');
            const data = JSON.parse(event.target.responseText);
            if (!data || data.success !== true || !Number.isSafeInteger(data.newFormId) || data.newFormId <= 0
                || typeof data.newNewUrl !== 'string' || !data.newNewUrl
                || typeof data.formAutosaveDate !== 'string' || !data.formAutosaveDate) throw new Error('Autosave failed');
            window.history.replaceState({fromId: data['newFormId']}, '', data['newNewUrl']);
            this.lastsavedDateString = data['formAutosaveDate'];
            // Edits made while this snapshot was in flight still need saving.
            if (this.changeVersion === sentVersion) this.changed = false;
            this.setMessageDivs(`Autosaved at ${this.lastsavedDateString}${this.changed ? '. New changes are still unsaved.' : ''}`);
            this.saving = false;
        } catch (error) {
            this.onSendRequestError();
        }
    }

    onSendRequestError(event) {
        this.saving = false;
        this.changed = true;
        this.setMessageDivs('Autosave failed. Your changes are still unsaved. Use Save or try again.');
    }

    setMessageDivs(message) {
        let divs = document.getElementsByName(this.messageDivName);
        for (let i = 0; i < divs.length; i++) {
            divs[i].innerText = message;
        }
    }

    setChangedFalse() {
        this.changed = false;
    }
}
