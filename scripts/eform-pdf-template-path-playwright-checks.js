#!/usr/bin/env node
/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
/*
 * Issue #5316: /eform/createpdf opened `EFORM_IMAGES_DIR + "/" + __template + ".pdf"` with no
 * validation, so any eForm reader could name `../` or an absolute path and get back any PDF the
 * server can read. The package-wide SonarCloud exclusion for **\/eform/** hid it.
 *
 * Against the installed application this check:
 *   1. plants a marker template in the eForm image directory and a marker "other" PDF in the
 *      document store (outside the image directory), both owned by this run;
 *   2. as the test user, POSTs /eform/createpdf naming the marker template and requires a PDF
 *      back (the fix must not break the feature);
 *   3. POSTs it naming the other PDF by a relative `../` path, by its absolute path, and with no
 *      template, and requires each to be refused without a PDF body and without echoing the
 *      value. On a front door with the WAF, a WAF refusal of the traversal payload also passes,
 *      but the application's own answer is asserted wherever the request reaches it.
 * Cleanup removes both planted files. No database rows are written.
 *
 * Direct POSTs are deliberate: the servlet has no in-app page (eForms post to it from their own
 * markup), and the probes are the attack, not a user path.
 *
 * Env: EFORM_IMAGE_DIR, DOCUMENT_DIR (the packaged stores; the check must run on the server),
 * EFORM_PDF_DEMOGRAPHIC_NO or CONSULT_DEMO_NO (a patient the test user may read),
 * TEST_PASSWORD, TEST_PIN.
 */
const fs = require('fs');
const path = require('path');
const crypto = require('crypto');
const h = require('./lib/playwright-harness');
const { csrfToken } = require('./lib/front-door-checks');

const NAME = 'eform-pdf-template-path';

/** A one-page PDF with correct cross-reference offsets; OpenPDF's PdfReader accepts it. */
function minimalPdf(label) {
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Contents 4 0 R /Resources << >> >>',
  ];
  const stream = `% ${label}\n`;
  objects.push(`<< /Length ${Buffer.byteLength(stream)} >>\nstream\n${stream}endstream`);
  let body = '%PDF-1.4\n';
  const offsets = [];
  objects.forEach((object, index) => {
    offsets.push(Buffer.byteLength(body));
    body += `${index + 1} 0 obj\n${object}\nendobj\n`;
  });
  const xref = Buffer.byteLength(body);
  body += `xref\n0 ${objects.length + 1}\n0000000000 65535 f \n`;
  for (const offset of offsets) body += `${String(offset).padStart(10, '0')} 00000 n \n`;
  body += `trailer\n<< /Size ${objects.length + 1} /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF\n`;
  return Buffer.from(body, 'latin1');
}

function requireDirectory(name) {
  const dir = process.env[name];
  if (!dir) throw new h.SkipCheck(`${name} is not set; this check plants files in the packaged store`);
  if (!fs.existsSync(dir) || !fs.statSync(dir).isDirectory()) {
    throw new h.SkipCheck(`${name}=${dir} is not a directory on this host; run the check on the server`);
  }
  return fs.realpathSync(dir);
}

async function postTemplate(context, config, token, demographicNo, template) {
  const form = { demographic_no: demographicNo, __title: 'FAKE-5316 template probe', 'CSRF-TOKEN': token };
  if (template !== undefined) form.__template = template;
  const response = await context.request.post(h.appUrl(config.baseUrl, '/eform/createpdf'), {
    form, headers: { 'CSRF-TOKEN': token }, maxRedirects: 0, failOnStatusCode: false,
  });
  const body = await response.body();
  const result = {
    status: response.status(),
    contentType: response.headers()['content-type'] || '',
    isPdf: body.subarray(0, 5).toString('latin1') === '%PDF-',
    text: body.toString('utf8'),
  };
  await response.dispose();
  return result;
}

async function main() {
  const config = h.readConfig({ require: ['TEST_PASSWORD', 'TEST_PIN'] });
  const demographicNo = process.env.EFORM_PDF_DEMOGRAPHIC_NO || process.env.CONSULT_DEMO_NO;
  if (!demographicNo) throw new h.SkipCheck('EFORM_PDF_DEMOGRAPHIC_NO or CONSULT_DEMO_NO is not set');
  h.assert(/^\d+$/.test(demographicNo), 'The demographic number must be numeric');
  const imageDir = requireDirectory('EFORM_IMAGE_DIR');
  const documentDir = requireDirectory('DOCUMENT_DIR');
  h.assert(path.relative(imageDir, documentDir).startsWith('..'),
    'DOCUMENT_DIR must lie outside EFORM_IMAGE_DIR for the traversal probe to mean anything');

  const marker = `carlos5316${crypto.randomBytes(6).toString('hex')}`;
  const templateName = `${marker}-template`;
  // Both directories are operator-supplied, realpath-resolved and checked to be directories; the
  // file names are a random hex marker plus constant suffixes, so neither join takes request data.
  const templateFile = path.join(imageDir, `${templateName}.pdf`); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- operator directory + random hex marker
  const outsideBase = path.join(documentDir, `${marker}-other-patient`); // nosemgrep: javascript.lang.security.audit.path-traversal.path-join-resolve-traversal.path-join-resolve-traversal -- operator directory + random hex marker
  const outsideFile = `${outsideBase}.pdf`;
  const planted = [templateFile, outsideFile];
  const cleanup = () => {
    for (const file of planted) fs.rmSync(file, { force: true });
    for (const file of planted) h.assert(!fs.existsSync(file), `cleanup left ${path.basename(file)} behind`);
  };

  const recorder = h.createRecorder();
  const browser = await h.launchBrowser(config);
  try {
    // Readable by the application user; written fresh so no earlier run's file is reused.
    fs.writeFileSync(templateFile, minimalPdf('FAKE-5316 template'), { mode: 0o644, flag: 'wx' });
    fs.writeFileSync(outsideFile, minimalPdf('FAKE-5316 other patient'), { mode: 0o644, flag: 'wx' });

    const context = await h.newContext(browser, config);
    const page = await h.login(context, config, recorder);
    const token = await csrfToken(page, config.baseUrl);

    const valid = await postTemplate(context, config, token, demographicNo, templateName);
    console.log(`  valid template: HTTP ${valid.status} ${valid.contentType}`);
    h.assert(valid.status === 200 && valid.contentType.startsWith('application/pdf') && valid.isPdf,
      `A template inside the eForm image directory must still render (HTTP ${valid.status})`);

    const relative = path.relative(imageDir, outsideBase);
    h.assert(relative.startsWith('..'), 'The relative probe must leave the image directory');
    const probes = [
      { label: 'relative ../ path', template: relative, waf: true },
      { label: 'absolute path', template: outsideBase, waf: true },
      { label: 'blank template', template: '', waf: false },
      { label: 'missing template', template: undefined, waf: false },
    ];
    const results = [];
    for (const probe of probes) {
      const result = await postTemplate(context, config, token, demographicNo, probe.template);
      const byWaf = probe.waf && h.isWafPage(result.status, result.text);
      console.log(`  ${probe.label}: HTTP ${result.status}${byWaf ? ' (front-door WAF)' : ''}`);
      h.assert(!result.isPdf, `${probe.label}: the server returned a PDF; the template escaped the image directory`);
      if (!byWaf) {
        // The servlet answers sendError(400); the container renders its own error page, so the
        // status (not the page text) is the application's answer. No other filter on this route
        // answers 400: CSRF and the WAF refuse with 403.
        h.assert(result.status === 400, `${probe.label}: expected the application's 400, got HTTP ${result.status}`);
      }
      if (probe.template) {
        h.assert(!result.text.includes(probe.template), `${probe.label}: the refusal echoed the submitted path`);
      }
      results.push({ probe: probe.label, status: result.status, byWaf });
    }
    h.assert(fs.existsSync(outsideFile), 'The outside PDF vanished; the probes must not touch it');
    return { valid: valid.status, probes: results };
  } finally {
    await browser.close();
    cleanup();
  }
}

if (require.main === module) h.runCheck({ name: NAME, run: main });
module.exports = { main, minimalPdf };
