/*

  Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
  This software is published under the GPL GNU General Public License.
  This program is free software; you can redistribute it and/or
  modify it under the terms of the GNU General Public License
  as published by the Free Software Foundation; either version 2
  of the License, or (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software
  Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

  This software was written for the
  Department of Family Medicine
  McMaster University
  Hamilton
  Ontario, Canada

*/

// Define regex patterns to match control characters
const CONTROL_CHAR_PATTERN_1 = /[\x00-\x1F\x7F]/g; // Matches all control characters except tab, new line, and carriage return
const CONTROL_CHAR_PATTERN_2 = /[\x00-\x08\x0B\x0C\x0E-\x1F\x7F]/g; // Excludes tab, new line, and carriage return

function sanitizeAllElements() {
    sanitizeAllTextInputs();
    sanitizeAllTextAreas();
}

// Function to sanitize all text input fields using CONTROL_CHAR_PATTERN_1
function sanitizeAllTextInputs() {
    let inputs = document.querySelectorAll('input[type="text"]');

    // Sanitize all input elements by removing control characters
    sanitizeElementsByPattern(inputs, CONTROL_CHAR_PATTERN_1);
}

// Function to sanitize all textarea fields using CONTROL_CHAR_PATTERN_2
function sanitizeAllTextAreas() {
    let textAreas = document.querySelectorAll('textarea');

    // Sanitize all textarea elements by removing control characters
    sanitizeElementsByPattern(textAreas, CONTROL_CHAR_PATTERN_2);
}

// General function to sanitize elements based on a provided regex pattern
function sanitizeElementsByPattern(elements, pattern) {
    for (let i = 0; i < elements.length; i++) {
        sanitizeElementByPattern(elements[i], pattern);
    }
}

// Function to sanitize element based on a provided regex pattern
function sanitizeElementByPattern(element, pattern) {
        // Replace control characters in the element's value with an empty string
        element.value = element.value.replace(pattern, "");
}
