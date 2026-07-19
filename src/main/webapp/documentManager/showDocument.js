function popupPatient(height, width, url, windowName, docId) {
    d = document.getElementById('demofind' + docId).value; //demog  //attachedDemoNo
    urlNew = url + d;

    return popup2(height, width, 0, 0, urlNew, windowName);
}

function popupPatientTicklerPlus(height, width, url, windowName, docId) {
    d = document.getElementById('demofind' + docId).value; //demog  //attachedDemoNo
    n = document.getElementById('demofindName' + docId).value;
    urlNew = url + "method=edit&tickler.demographic_webName=" + n + "&tickler.demographicNo=" + d + "&docType=DOC&docId=" + docId;

    return popup2(height, width, 0, 0, urlNew, windowName);
}

function popupPatientTickler(height, width, url, windowName, docId) {
    d = document.getElementById('demofind' + docId).value; //demog  //attachedDemoNo
    n = document.getElementById('demofindName' + docId).value;
    urlNew = url + "demographic_no=" + d + "&name=" + n + "&chart_no=&bFirstDisp=false&messageID=null&remoteFacilityId=&docType=DOC&docId=" + docId;
    return popup2(height, width, 0, 0, urlNew, windowName);
}

function setupDemoAutoCompletion(docId, contextPath) {
	let autoCompleteId = "#autocompletedemo" + docId;
	if (jQuery(autoCompleteId)) {

		var url = contextPath + "/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=" + jQuery("#activeOnly" + docId).is(":checked");

		jQuery(autoCompleteId).autocomplete({
			source: url, minLength: 2,

			focus: function (event, ui) {
				jQuery(autoCompleteId).val(ui.item.label);
				return false;
			}, select: function (event, ui) {
				jQuery(autoCompleteId).val(ui.item.label);
				jQuery("#demofind" + docId).val(ui.item.value);
				jQuery("#demofindName" + docId).val(ui.item.formattedName);
				selectedDemos.push(ui.item.label);
				console.log(ui.item.providerNo);

				if (ui.item.providerNo !== undefined && ui.item.providerNo != null && ui.item.providerNo !== "" && ui.item.providerNo !== "null") {
					addDocToList(ui.item.providerNo, ui.item.provider + " (MRP)", docId);
				}

				// enable Save button whenever a selection is made
				jQuery('#save' + docId).removeAttr('disabled');
				jQuery('#saveNext' + docId).removeAttr('disabled');

				jQuery('#msgBtn_' + docId).removeAttr('disabled');
				jQuery('#mainTickler_' + docId).removeAttr('disabled');
				jQuery('#mainEchart_' + docId).removeAttr('disabled');
				jQuery('#mainMaster_' + docId).removeAttr('disabled');
				jQuery('#mainApptHistory_' + docId).removeAttr('disabled');

				return false;
			}
		});
	}
}
