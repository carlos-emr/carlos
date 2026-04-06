/**
 * Reusable jQuery UI Autocomplete wrappers for provider and demographic search,
 * consolidating inline setup code previously duplicated across JSPs.
 *
 * Requires: jQuery 3.x, jQuery UI 1.14.x (autocomplete widget),
 *           demographicProviderAutocomplete.js (resultFormatter2, resultFormatter3)
 * Endpoints: /provider/SearchProvider.do, /demographic/SearchDemographic.do
 *
 * @since 2026-04-06
 */

'use strict';

/**
 * Initialize a provider autocomplete on the given input element.
 *
 * @param {string} inputSelector - jQuery selector for the text input (e.g. "#autocompleteprov")
 * @param {string} contextPath   - Application context path (e.g. request.getContextPath())
 * @param {function} onSelect    - Callback receiving (providerNo, firstName, lastName, item) on selection.
 *                                 item._raw contains the full server response object.
 * @param {object} [options]     - Optional overrides: {minLength, maxResults}
 */
function initProviderAutocomplete(inputSelector, contextPath, onSelect, options) {
    if (typeof resultFormatter3 !== 'function') {
        console.error("initProviderAutocomplete requires resultFormatter3 from demographicProviderAutocomplete.js");
        return;
    }
    var opts = options || {};
    var $el = jQuery(inputSelector);
    if (!$el.length) {
        console.error("initProviderAutocomplete: element not found for selector: " + inputSelector);
        return;
    }

    $el.autocomplete({
        source: function (request, response) {
            jQuery.ajax({
                url: contextPath + "/provider/SearchProvider.do",
                type: "POST",
                data: {query: request.term},
                dataType: "json",
                success: function (data) {
                    var results = (data && data.results) || [];
                    var maxResults = opts.maxResults || 25;
                    response(results.slice(0, maxResults).map(function (item) {
                        return {
                            label: resultFormatter3(
                                [item.providerNo, item.firstName, item.lastName],
                                request.term, ""),
                            value: (item.lastName || '') + ", " + (item.firstName || ''),
                            providerNo: item.providerNo || '',
                            firstName: item.firstName || '',
                            lastName: item.lastName || '',
                            _raw: item
                        };
                    }));
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    console.error("Provider search failed:", textStatus, errorThrown);
                    response([]);
                }
            });
        },
        minLength: opts.minLength || 3,
        select: function (event, ui) {
            if (onSelect) {
                onSelect(ui.item.providerNo, ui.item.firstName, ui.item.lastName, ui.item);
            }
            return false;
        }
    });

    var instance = $el.autocomplete("instance");
    if (instance) {
        instance._renderItem = function (ul, item) {
            return jQuery("<li>").append("<div>" + item.label + "</div>").appendTo(ul);
        };
    }
}

/**
 * Initialize a demographic (patient) autocomplete on the given input element.
 *
 * @param {string} inputSelector - jQuery selector for the text input
 * @param {string} contextPath   - Application context path
 * @param {function} onSelect    - Callback receiving (demographicNo, formattedName, formattedDob, status, item) on selection.
 *                                 item._raw contains the full server response object.
 * @param {object} [options]     - Optional overrides: {minLength, maxResults}
 */
function initDemographicAutocomplete(inputSelector, contextPath, onSelect, options) {
    if (typeof resultFormatter2 !== 'function') {
        console.error("initDemographicAutocomplete requires resultFormatter2 from demographicProviderAutocomplete.js");
        return;
    }
    var opts = options || {};
    var $el = jQuery(inputSelector);
    if (!$el.length) {
        console.error("initDemographicAutocomplete: element not found for selector: " + inputSelector);
        return;
    }

    $el.autocomplete({
        source: function (request, response) {
            jQuery.ajax({
                url: contextPath + "/demographic/SearchDemographic.do",
                type: "POST",
                data: {query: request.term},
                dataType: "json",
                success: function (data) {
                    var results = (data && data.results) || [];
                    var maxResults = opts.maxResults || 25;
                    response(results.slice(0, maxResults).map(function (item) {
                        return {
                            label: resultFormatter2(
                                [item.formattedName, item.fomattedDob, item.demographicNo, item.status],
                                request.term, ""),
                            value: item.formattedName || '',
                            demographicNo: item.demographicNo || '',
                            formattedName: item.formattedName || '',
                            // normalize legacy misspelled key "fomattedDob" from backend response
                            formattedDob: item.fomattedDob || '',
                            status: item.status || '',
                            providerNo: item.providerNo || '',
                            providerName: item.providerName || '',
                            _raw: item
                        };
                    }));
                },
                error: function (jqXHR, textStatus, errorThrown) {
                    console.error("Demographic search failed:", textStatus, errorThrown);
                    response([]);
                }
            });
        },
        minLength: opts.minLength || 3,
        select: function (event, ui) {
            if (onSelect) {
                onSelect(ui.item.demographicNo, ui.item.formattedName, ui.item.formattedDob, ui.item.status, ui.item);
            }
            return false;
        }
    });

    var instance = $el.autocomplete("instance");
    if (instance) {
        instance._renderItem = function (ul, item) {
            return jQuery("<li>").append("<div>" + item.label + "</div>").appendTo(ul);
        };
    }
}
