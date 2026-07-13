jQuery(document).ajaxError(function (event, request, options, error) {
    /*
    * Ignoring any alert messages generated because of '/oscar/ws/rs/ocean/getSettings' endpoint.
    * These alerts appeared consistently whenever the consultation request form was opened with Ocean integration enabled.
    * 
    * Why am I ignoring? The file jquery_oscar_defaults.js is included in ConsultationFormRequest.jsp. This script handles errors encountered during AJAX calls to endpoints.
    * When a user opens the consultation request form, Ocean's dynamically added script invokes the '/oscar/ws/rs/ocean/getSettings' endpoint.
    * This particular endpoint is not implemented in OpenOSP because its use cases have not been defined.
    * Consequently, the endpoint's absence results in errors and triggers the alert message whenever the consult request form is accessed.
    * 
    * TODO: To solve it, the recommended solution is to import OceanService.java from the OscarPro codebase (https://bitbucket.org/oscaremr/oscarpro/src/master/).
    * Currently not importing it because we don't know its use cases. Once its use cases are identified, it should be imported.
    */
    if (options.url.endsWith("/ocean/getSettings")) {
        return;
    }

    alert('Error contacting server, please try again. \n' + options.url);
});
