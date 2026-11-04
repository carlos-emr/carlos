/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/** Match code-point-based server limits while retaining the browser's composition lifecycle. */
(function () {
    function limitInput(event) {
        const field = event.target;
        const maximum = Number(field.dataset && field.dataset.codePointMaxlength);
        if (!maximum || event.isComposing) return;
        let count = 0;
        let end = 0;
        // Stop at the first excess point, without allocating an array for a large paste.
        for (const point of field.value) {
            if (count === maximum) {
                const start = field.selectionStart;
                const finish = field.selectionEnd;
                field.value = field.value.slice(0, end);
                field.setSelectionRange(Math.min(start, end), Math.min(finish, end));
                return;
            }
            count++;
            end += point.length;
        }
    }
    document.addEventListener('input', limitInput);
    document.addEventListener('compositionend', limitInput);
    document.addEventListener('DOMContentLoaded', function () {
        document.querySelectorAll('input[data-code-point-maxlength], textarea[data-code-point-maxlength]')
            .forEach(field => field.removeAttribute('maxlength'));
    });
})();
