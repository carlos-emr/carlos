/*
 * stamps.js: clinic signature stamps for the Rich Text Letter (optional).
 *
 * Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later.
 * CARLOS EMR Project, https://github.com/carlos-emr/carlos
 *
 * The Rich Text Letter loads this file on every open. CARLOS deploys this placeholder to the eForm
 * image directory once, when no stamps.js is there, and never overwrites it, so the letter finds a
 * script here instead of a 404.
 *
 * The letter's Stamp and Closing Salutation buttons sign with the provider's own signature image,
 * which each provider uploads under Preferences > Signature Stamp. This file is only needed for a
 * stamp kept outside that, such as a shared one. To use it, upload a file named stamps.js through
 * the eForm image manager, replacing this one, that lists "Last name|image file" pairs for images
 * uploaded the same way:
 *
 *   ImgArray = ["Smith|smith_signature.png", "Jones|jones_signature.png"];
 *
 * A name matches when it appears in the current user or the patient's doctor ("Last, First").
 */
