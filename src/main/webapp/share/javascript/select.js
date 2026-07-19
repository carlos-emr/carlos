// Copyright (c) 2006 - 2007 Gabriel Lanzani (http://www.glanzani.com.ar)
//
// Permission is hereby granted, free of charge, to any person obtaining
// a copy of this software and associated documentation files (the
// "Software"), to deal in the Software without restriction, including
// without limitation the rights to use, copy, modify, merge, publish,
// distribute, sublicense, and/or sell copies of the Software, and to
// permit persons to whom the Software is furnished to do so, subject to
// the following conditions:
//
// The above copyright notice and this permission notice shall be
// included in all copies or substantial portions of the Software.
//
// THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
// EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
// MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
// NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE
// LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION
// OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION
// WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
//
// SEE CHANGELOG FOR A COMPLETE CHANGES OVERVIEW
// VERSION 0.3

// HTML escaping function to prevent XSS vulnerabilities
function escapeHTML(str) {
    const map = {
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;'
    };
    return String(str).replace(/[&<>"']/g, m => map[m]);
}

Autocompleter.SelectBox = Class.create();

Autocompleter.SelectBox.prototype = Object.extend(new Autocompleter.Base(), {
    initialize: function (select, options) {
	this.element = document.createElement('input');
	this.element.type = 'text';
	this.element.id = select + "_combo";

	const selectElement = document.getElementById(select);
	selectElement.parentNode.insertBefore(this.element, selectElement);

	const inputClasses = selectElement.classList;
	const comboElement = document.getElementById(select + "_combo");
	inputClasses.forEach(function(inputClass) {
		if (comboElement) {
			comboElement.classList.add(inputClass);
		}
        });

	this.update = document.createElement('div');
	this.update.id = selectElement.id + "_options";
	this.update.className = "autocomplete";

	selectElement.parentNode.insertBefore(this.update, selectElement);

    this.baseInitialize(this.element.id, this.update.id, options);
        this.select = select;
        this.selectOptions = [];

	this.element.setAttribute('readonly', 'readonly');
        this.element.readOnly = true;
	if(this.options.debug) {
		alert('input ' + this.element.id + ' and div ' + this.update.id + ' created, Autocompleter.Base() initialized');
	}
	if(!this.options.debug) {
		selectElement.style.display = 'none';
	}

	const optionList = selectElement.getElementsByTagName('option');
	const nodes = Array.from(optionList);

	for(let i=0; i < nodes.length; i++){
            // Escape HTML to prevent XSS vulnerabilities
            const optionText = nodes[i].innerHTML ? escapeHTML(nodes[i].innerHTML) : '';
            const optionValue = nodes[i].value ? escapeHTML(nodes[i].value) : '';
            
            this.selectOptions.push("<li id=\"" + optionValue + "\">" + optionText + '</li>');
            if (nodes[i].getAttribute("selected")) this.element.value = nodes[i].textContent || nodes[i].innerText || '';

            if (this.options.debug) alert('option ' + (nodes[i].textContent || nodes[i].innerText || '') + ' added to ' + this.update.id);
        }

	this.element.addEventListener("click", this.activate.bind(this));

	if (selectElement.selectedIndex >= 0) {
		// Use textContent to avoid XSS vulnerabilities
		const selectedOption = selectElement.options[selectElement.selectedIndex];
		this.element.value = selectedOption.textContent || selectedOption.innerText || '';
	}

	const self = this;
        this.options.afterUpdateElement = function (text, li) {
		const optionList = selectElement.getElementsByTagName('option');
		const nodes = Array.from(optionList);

		const opt = nodes.find( function(node){
                return (node.value == li.id);
            });
		selectElement.selectedIndex = nodes.indexOf(opt);
		if (self.options.redirect) {
			document.location.href = opt.value;
		}
		if(self.options.autoSubmit) {
			const autoSubmitElement = document.getElementById(self.options.autoSubmit);
			if (autoSubmitElement) {
				autoSubmitElement.submit();
			}
		}
        }
    },

    getUpdatedChoices: function () {
        this.updateChoices(this.setValues());
    },

    setValues: function () {
        return ("<ul>" + this.selectOptions.join('') + "</ul>");
    },

    setOptions: function (options) {
        this.options = Object.extend({
            //MORE OPTIONS TO EXTEND THIS CLASS
            redirect: false, // redirects to option value
            debug: false, //show alerts with information
            autoSubmit: '' //form Id to submit after change
        }, options || {});
    }
});
