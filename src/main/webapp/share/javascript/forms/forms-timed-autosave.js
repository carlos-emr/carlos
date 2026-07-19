class FormsTimedAutosave {

    constructor(formId, autosaveInterval, saveEndpointUrl, csrfToken, validationMethod, validationFailedMessage, messageDivName, promptSaveOnClose = false) {
        let formElement = document.getElementById(formId);
        if (!formElement) {
            console.error(`FormsTimedAutosave: formElement \'${formId}\' does not exist`)
        }
        this.formElement = formElement;
        this.idleTime = 0;
        this.changed = false;
        this.saveEndpointUrl = saveEndpointUrl;
        this.csrfToken = csrfToken;
        this.messageDivName = messageDivName;
        this.validationMethod = validationMethod;
        this.validationFailedMessage = validationFailedMessage;
        this.lastsavedDateString = "";

        // bind events
        let boundTimerIncrement = this.timerIncrement.bind(this);
        this.idleInterval = setInterval(boundTimerIncrement, autosaveInterval);
        let boundFormChangedEvent = this.formChangedEvent.bind(this);
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
        const httpRequest = new XMLHttpRequest();
        const formData = new FormData(this.formElement);
        formData.append('submit', 'autosaveAjax');

        // bind events
        let onSendRequestSuccessEvent = this.onSendRequestSuccess.bind(this);
        httpRequest.addEventListener('load', onSendRequestSuccessEvent);
        let onSendRequestErrorEvent = this.onSendRequestError.bind(this);
        httpRequest.addEventListener('error', onSendRequestErrorEvent);

        httpRequest.open('POST', this.saveEndpointUrl);
        httpRequest.setRequestHeader(this.csrfToken['name'], this.csrfToken['value']);
        httpRequest.send(formData);
    }

    formChangedEvent(event) {
        this.changed = true;
    }

    onSendRequestSuccess(event) {
        let data = JSON.parse(event.target.responseText);
        if (data['success'] === true) {
            window.history.replaceState({fromId: data['newFormId']}, '', data['newNewUrl']);
            this.lastsavedDateString = data['formAutosaveDate'];
            this.setMessageDivs(`Autosaved at ${this.lastsavedDateString}`);
        }
        this.changed = false;
    }

    onSendRequestError(event) {
        this.changed = false;
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
