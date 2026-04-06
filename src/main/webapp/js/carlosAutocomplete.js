/**
 * jQuery UI Autocomplete wrappers for provider and demographic search.
 * Replaces legacy YUI 2.8 AutoComplete usage across the application.
 *
 * Requires: jQuery 3.x, jQuery UI 1.14.x (autocomplete widget)
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
 * @param {function} onSelect    - Callback receiving (providerNo, firstName, lastName, item) on selection
 * @param {object} [options]     - Optional overrides: {minLength, maxResults}
 */
function initProviderAutocomplete(inputSelector, contextPath, onSelect, options) {
    var opts = options || {};
    jQuery(inputSelector).autocomplete({
        source: function (request, response) {
            jQuery.ajax({
                url: contextPath + "/provider/SearchProvider.do",
                type: "POST",
                data: {query: request.term},
                dataType: "json",
                success: function (data) {
                    var results = data.results || [];
                    var maxResults = opts.maxResults || 25;
                    response(results.slice(0, maxResults).map(function (item) {
                        return {
                            label: resultFormatter3(
                                [item.providerNo, item.firstName, item.lastName],
                                request.term, ""),
                            value: item.lastName + ", " + item.firstName,
                            providerNo: item.providerNo,
                            firstName: item.firstName,
                            lastName: item.lastName,
                            _raw: item
                        };
                    }));
                }
            });
        },
        minLength: opts.minLength || 3,
        html: true,
        select: function (event, ui) {
            if (onSelect) {
                onSelect(ui.item.providerNo, ui.item.firstName, ui.item.lastName, ui.item);
            }
        }
    }).autocomplete("instance")._renderItem = function (ul, item) {
        return jQuery("<li>").append("<div>" + item.label + "</div>").appendTo(ul);
    };
}

/**
 * Initialize a demographic (patient) autocomplete on the given input element.
 *
 * @param {string} inputSelector - jQuery selector for the text input
 * @param {string} contextPath   - Application context path
 * @param {function} onSelect    - Callback receiving (demographicNo, formattedName, formattedDob, status, item) on selection
 * @param {object} [options]     - Optional overrides: {minLength, maxResults}
 */
function initDemographicAutocomplete(inputSelector, contextPath, onSelect, options) {
    var opts = options || {};
    jQuery(inputSelector).autocomplete({
        source: function (request, response) {
            jQuery.ajax({
                url: contextPath + "/demographic/SearchDemographic.do",
                type: "POST",
                data: {query: request.term},
                dataType: "json",
                success: function (data) {
                    var results = data.results || [];
                    var maxResults = opts.maxResults || 25;
                    response(results.slice(0, maxResults).map(function (item) {
                        return {
                            label: resultFormatter2(
                                [item.formattedName, item.fomattedDob, item.demographicNo, item.status],
                                request.term, ""),
                            value: item.formattedName,
                            demographicNo: item.demographicNo,
                            formattedName: item.formattedName,
                            formattedDob: item.fomattedDob,
                            status: item.status,
                            providerNo: item.providerNo,
                            providerName: item.providerName,
                            _raw: item
                        };
                    }));
                }
            });
        },
        minLength: opts.minLength || 3,
        html: true,
        select: function (event, ui) {
            if (onSelect) {
                onSelect(ui.item.demographicNo, ui.item.formattedName, ui.item.formattedDob, ui.item.status, ui.item);
            }
        }
    }).autocomplete("instance")._renderItem = function (ul, item) {
        return jQuery("<li>").append("<div>" + item.label + "</div>").appendTo(ul);
    };
}
