/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
/* global toastui */
/** Preserve literal entity text when composing a message in Toast UI's WYSIWYG mode. */
function carlosMessengerMarkdown() {
    return {
        toMarkdownRenderers: {
            text(nodeInfo) {
                const node = nodeInfo.node;
                if (node.text && node.text.includes('&')) {
                    // The bundled converter reads nodeInfo.node.text; its text converter does
                    // not consume the plugin's returned `text` option. Replace only the
                    // conversion descriptor with an immutable text-node copy, retaining marks.
                    // The live editor document and undo history must remain untouched.
                    nodeInfo.node = node.withText(node.text.replace(/&/g, '&amp;'));
                }
                // Keep Toast UI's Markdown escaping/formatting. Code spans and code blocks
                // have their own converters; link destinations are attributes, not text.
                return {};
            }
        }
    };
}
