/*
 * This is the code provided in
 * "CareConnect ‘POST’ Integration Technical Specification"
 * Version 2.01, February 2019
 */
(function (eHealth) {
    eHealth.postwith = {
        // DateTimeFormat = "yyyyMMdd";
        // domain is an optional parameter that will put CareConnect into
        // ‘PORTLET’ mode
        theFollowingPatientInfo: function (uri, phn, firstName, lastName,
                                           dateOfBirth, gender, org) {
            var postToCareConnect = function (params) {
                var form = document.createElement("form");
                form.setAttribute("method", "post");
                form.setAttribute("action", uri);
                form.setAttribute("target", "_blank");
                for (var key in params) {
                    if (params.hasOwnProperty(key)) {
                        var hiddenField = document.createElement("input");
                        hiddenField.setAttribute("type", "hidden");
                        hiddenField.setAttribute("name", key);
                        hiddenField.setAttribute("value", params[key]);
                        form.appendChild(hiddenField);
                    }
                }
                document.body.appendChild(form);
                form.submit();
            };
            this.init = function () {
                const integrationInfo = new Object({
                    "phn": phn,
                    "fn": firstName,
                    "ln": lastName,
                    "dob": dateOfBirth,
                    "g": gender,
                    "o": org
                });

                console.log("POSTING to CareConnect with parameters:")
                console.log("URL: " + uri);
                console.log(integrationInfo);
                postToCareConnect(integrationInfo);

            };
            this.init(uri, phn, firstName, lastName, dateOfBirth, gender, org);
        }
    };
})(window.eHealth = window.eHealth || {});

/*
 * add this to any button that intends to launch CareConnect
 */
function callCareConnect(uri, phn, fname, lname, dob, gender, org) {

    var dateOfBirth = null;

    try {
        dateOfBirth = dob.replaceAll("-", "");
    } catch (err) {
        alert("CareConnect failed: invalid date of birth.");
        return false;
    }

    if (!uri || !org) {
        alert("CareConnect not enabled or incorrectly configured. Contact support.");
        return false;
    }

    if (!phn || phn.length < 10 || phn.substring(0, 1) !== '9') {
        alert("CareConnect failed: invalid or missing PHN");
        return false;
    }

    if (!fname) {
        alert("CareConnect failed: missing patient first name");
        return false;
    }

    if (!lname) {
        alert("CareConnect failed: missing patient last name");
        return false;
    }

    var gender = gender.toUpperCase();
    if (gender !== "M" && gender !== "F" && gender !== 'U' && gender !== "UN") {
        gender = "U";
    }

    if (eHealth) {
        console.log("Got eHealth object");
        eHealth.postwith.theFollowingPatientInfo(uri, phn, fname, lname, dateOfBirth, gender, org.toLowerCase());
    }
}
