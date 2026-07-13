// Instead of importing cme.js using the CME tag (as done in Oscar19/OscarPro),
// we are opting to directly import cme.js into newEncounterLayout.jsp to load the ocean toolbar without utilizing the CME tag.

// Here, the 'ocean-host' script attribute is added in the cme.js script tag as follows:
// '<script id="mainScript" src="${ pageContext.request.contextPath }/js/custom/ocean/cme.js?no-cache=<%=randomNo%>&autoRefresh=true" ocean-host=<%=Encode.forUriComponent(OscarProperties.getInstance().getProperty("ocean_host"))%>></script>'
// when it is being imported into the newEncounterLayout.jsp.
(function (script) {
    let oceanHost = script.attributes['ocean-host'];
    if (oceanHost && oceanHost.value) {
        window.oceanHost = decodeURIComponent(oceanHost.value);
    }
})(document.currentScript);

jQuery(document).ready(function () {
    let pollingAttempt = 0; // Counter to track the number of polling attempts
    let pollingInterval = setInterval(checkForPlaceholderVisibility, 1000);

    // Check for the visibility of the placeholder element
    function checkForPlaceholderVisibility() {
        pollingAttempt++; // Increment the counter on each polling attempt

        // Stop polling after 10 attempts if the placeholder is not found
        if (pollingAttempt === 10) {
            clearInterval(pollingInterval);
        }

        if (jQuery('#ocean_placeholder').length == 0) {
            return;
        }

        // rather than highjacking a <div> lets put OCEAN where the OSCAR instance has allowance
        jQuery("#ocean_placeholder").show();
        jQuery("#ocean_placeholder").append("<div id='ocean_div' style='width: 100%; display: none; font-size: 11px;'>Sorry, the Ocean toolbar is currently unavailable.</div>");

        jQuery.ajax({
            url: window.oceanHost + "/robots.txt",
            cache: true,
            dataType: "text",
            success: function () {
                jQuery.ajax({
                    url: window.oceanHost + "/oscar_resources/OscarToolbar.js",
                    cache: true,
                    dataType: "script",
                    success: function () {
                        // Wait for 500 miliseconds
                        setTimeout(function () {
                            // Apply the CSS styles after the delay
                            jQuery("#oceanLogo img").css({
                                "height": "14px",
                                "position": "relative",
                                "top": "2px"
                            });
                        }, 500);
                    }
                });
            },
            error: function (jqXHR, textStatus, error) {
                console.log("Ocean toolbar error: " + textStatus + ", " + error);
                jQuery("#ocean_div").show().css("padding", "5px").css("text-align", "center");
            }
        });

        clearInterval(pollingInterval);
    }
});
