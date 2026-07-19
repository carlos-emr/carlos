function searchDxCode(request, response) {
    jQuery.ajax({
        url: ctx + "/dxCodeSearchJSON.do",
        type: 'POST',
        data: {
            method: request.method,
            keyword: request.term,
            codeSystem: request.codeSystem
        },
        dataType: "json",
        success: function (data) {
            if (data && data.length > 0) {
                data = jQuery.map(data, function (item) {
                    return {
                        label: item.code + ": " + item.description.trim(),
                        value: item.code,
                        id: item.id
                    };
                })
            } else {
                data = [{
                    label: 'No results',
                    value: '0',
                    id: '0'
                }];
            }
            return response(data);
        }
    })
}

function deployAutocomplete(inputField) {

    var codeSystem = (jQuery('#codingSystem option:selected, input#codingSystem').val()).toUpperCase();
    inputField.autocomplete({
        source: function (request, response) {
            request.method = 'search' + codeSystem
            searchDxCode(request, response);
        },
        minLength: 3,
        select: function (event, ui) {
            event.preventDefault();
            var valueid = ui.item.value;

            if (valueid == '0') {
                this.value = '';
            } else {
                this.value = valueid;
                jQuery(this).prop('title', ui.item.label);
            }

            inputField.autocomplete("destroy");
        },
        change: function (event, ui) {
            inputField.autocomplete("destroy");
        }
    }).autocomplete("search", inputField.val());

}

function bindDxJSONEvents() {

    jQuery(document).on("keydown", ".jsonDxSearchInput", function () {
        jQuery(this).prop('title', '');
    });

    jQuery("#jsonDxSearch, .jsonDxSearch").autocomplete({
        source: function (request, response) {
            request.method = 'search' + (jQuery('#codingSystem option:selected, input#codingSystem').val()).toUpperCase();
            searchDxCode(request, response);
        },
        delay: 100,
        minLength: 2,
        select: function (event, ui) {
            event.preventDefault();
            var valueid = ui.item.value;

            if (valueid == '0') {
                this.value = '';
            } else {
                this.value = valueid;
                jQuery(this).prop('title', ui.item.label);
            }
        }
    })

    /*
     * Listener for enter button inside of text field.
     */
    jQuery(document).on("keypress", ".jsonDxSearchInput", function (event) {
        if ((event && event.keyCode === 13) || event && event.which === 13) {
            event.preventDefault();
            var inputField = jQuery("#" + this.id);
            deployAutocomplete(inputField);
        }
    })


    jQuery(document).on("click", ".jsonDxSearchButton", function () {
        var inputField = jQuery("#" + this.value);
        deployAutocomplete(inputField);
    });

}

jQuery(document).ready(function () {
    bindDxJSONEvents();
})
