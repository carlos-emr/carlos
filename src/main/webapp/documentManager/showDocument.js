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
    urlNew = url + "demographic_no=" + d + "&name=" + n + "&chart_no=&bFirstDisp=false&messageID=null&docType=DOC&docId=" + docId;
    return popup2(height, width, 0, 0, urlNew, windowName);
}

function setupDemoAutoCompletion(docId, contextPath) {
    var inputEl = document.getElementById('autocompletedemo' + docId);
    var dropdownEl = document.getElementById('autocomplete_choices' + docId);
    if (!inputEl || !dropdownEl) return;

    var activeOnlyEl = document.getElementById('activeOnly' + docId);
    inputEl.setAttribute('autocomplete', 'off');
    var abortCtrl = null;

    inputEl.addEventListener('input', function () {
        var term = inputEl.value.trim();
        // Clear any previous demographic selection to prevent saving against wrong chart
        var demoIdEl = document.getElementById('demofind' + docId);
        var demoNameEl = document.getElementById('demofindName' + docId);
        if (demoIdEl) demoIdEl.value = '';
        if (demoNameEl) demoNameEl.value = '';
        ['save', 'saveNext', 'msgBtn_', 'mainTickler_', 'mainEchart_', 'mainMaster_', 'mainApptHistory_']
            .forEach(function (prefix) {
                var el = document.getElementById(prefix + docId);
                if (el) el.disabled = true;
            });
        if (term.length < 2) {
            dropdownEl.innerHTML = '';
            dropdownEl.style.display = 'none';
            return;
        }
        var activeOnly = activeOnlyEl ? activeOnlyEl.checked : true;
        var url = contextPath + '/demographic/SearchDemographic.do?jqueryJSON=true&activeOnly=' + activeOnly + '&term=' + encodeURIComponent(term);
        if (abortCtrl) { abortCtrl.abort(); }
        abortCtrl = new AbortController();
        fetch(url, { signal: abortCtrl.signal })
            .then(function (r) { return r.json(); })
            .then(function (items) {
                dropdownEl.innerHTML = '';
                if (!items || items.length === 0) {
                    dropdownEl.style.display = 'none';
                    return;
                }
                items.forEach(function (item) {
                    var div = document.createElement('div');
                    div.className = 'ac-item';
                    div.textContent = item.label;
                    div.addEventListener('mousedown', function (e) {
                        e.preventDefault();
                        inputEl.value = item.label;
                        document.getElementById('demofind' + docId).value = item.value;
                        document.getElementById('demofindName' + docId).value = item.formattedName;
                        selectedDemos.push(item.label);

                        if (item.providerNo !== undefined && item.providerNo !== null && item.providerNo !== '' && item.providerNo !== 'null') {
                            addDocToList(item.providerNo, item.provider + ' (MRP)', docId);
                        }

                        // enable Save button whenever a selection is made
                        var saveEl = document.getElementById('save' + docId);
                        var saveNextEl = document.getElementById('saveNext' + docId);
                        var msgBtn = document.getElementById('msgBtn_' + docId);
                        var ticklerBtn = document.getElementById('mainTickler_' + docId);
                        var echartBtn = document.getElementById('mainEchart_' + docId);
                        var masterBtn = document.getElementById('mainMaster_' + docId);
                        var apptHistBtn = document.getElementById('mainApptHistory_' + docId);
                        if (saveEl) saveEl.disabled = false;
                        if (saveNextEl) saveNextEl.disabled = false;
                        if (msgBtn) msgBtn.disabled = false;
                        if (ticklerBtn) ticklerBtn.disabled = false;
                        if (echartBtn) echartBtn.disabled = false;
                        if (masterBtn) masterBtn.disabled = false;
                        if (apptHistBtn) apptHistBtn.disabled = false;

                        dropdownEl.innerHTML = '';
                        dropdownEl.style.display = 'none';
                    });
                    dropdownEl.appendChild(div);
                });
                dropdownEl.style.display = 'block';
            })
            .catch(function () {});
    });

    inputEl.addEventListener('blur', function () {
        setTimeout(function () {
            dropdownEl.innerHTML = '';
            dropdownEl.style.display = 'none';
        }, 200);
    });
}
