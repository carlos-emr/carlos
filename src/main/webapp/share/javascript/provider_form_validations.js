/*
 * On page load, this script registers an event listener for the "submit" event on the
 * HTML form with ID "providerForm" that validates form numeric field values for the form.
 *
 * Validated Form Id
 *  - providerForm
 *
 * Validated values
 *  - numericFormField - numeric text input field
 *
 * Updated HTML elements
 *  - errorMessage - set it's CSS display property to "block"
 */

window.onload = function () {

    // The page that confirms a saved preference loads this script too but has no form.
    var form = document.getElementById("providerForm");
    if (!form) {
        return;
    }

    // register event listener
    form.onsubmit = function (e) {
        // get form element value in a safe manner
        var formFieldElement = document.getElementById("numericFormField");
        if (!formFieldElement) {
            return;
        }

        var value = formFieldElement.value;
        if (!value) {
            return;
        }

        // trim
        value = value.replace(/^\s+|\s+$/, "");

        // validate
        var hasNonDigits = /\D+/.test(value);
        var isGreaterThanZero = parseInt(value) > 0;
        var isValid = !hasNonDigits && isGreaterThanZero;
        if (isValid) {
            return;
        }

        // show error message
        var errorMessageElement = document.getElementById("errorMessage");
        if (errorMessageElement) {
            errorMessageElement.style.display = "block";
        }

        // highlight error
        formFieldElement.focus();
        formFieldElement.style.borderColor = "red";

        // cancel form submission
        return false;
    };
};
