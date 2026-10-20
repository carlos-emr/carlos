/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const jsp = fs.readFileSync(path.join(__dirname, '../src/main/webapp/WEB-INF/jsp/form/formVTForm.jsp'), 'utf8');
const xml = fs.readFileSync(path.join(__dirname, '../src/main/webapp/form/VTForm.xml'), 'utf8');
const globalScript = fs.readFileSync(path.join(__dirname, '../src/main/webapp/js/global.js'), 'utf8');
const script = jsp.match(/<script type="text\/javascript">([\s\S]*?)<\/script>/)[1]
  .replace(/<fmt:message\b[^>]*\/>/g, 'Confirm save')
  .replace(/<%=[\s\S]*?%>/g, 'null')
  .replace(/\$\{[^}]*}/g, 'null');
const eyeTypes = ['iDia', 'iHyp', 'iOth', 'iRef'];
const footTypes = ['FTEx', 'FTNe', 'FTIs', 'FTUl', 'FTIn', 'FTOt', 'FTRe'];

// Tokenize the checked-in JSP fixture, never produce filtered HTML. Complete JSP/comment/
// script blocks are consumed before opening tags; quoted '>' stays inside its attribute.
// This is a source-test reader, not a sanitizer or a general JSP/HTML renderer.
function* fixtureTags(source) {
  const tokens = /<%[\s\S]*?%>|<!--[\s\S]*?-->|<script(?=[\t\n\f\r />])(?:"[^"]*"|'[^']*'|[^'">])*>[\s\S]*?<\/script(?=[\t\n\f\r />])(?:"[^"]*"|'[^']*'|[^'">])*>|<([a-z][\w:-]*)\b((?:"[^"]*"|'[^']*'|[^'">])*)>|(<%|<!--)/gi;
  for (const token of source.matchAll(tokens)) {
    assert(!token[3], 'Unterminated JSP/comment block in fixture');
    if (!token[1]) continue;
    assert.notEqual(token[1].toLowerCase(), 'script', 'Unterminated script block in fixture');
    const attributes = token[2].replace(/<%[\s\S]*?%>/g, '')
      .replace(/<fmt:message\b[^>]*\/>/g, 'Label')
      .replace(/\$\{[^}]*}/g, '');
    const attrs = {};
    for (const attribute of attributes.matchAll(/([^\s="'<>/]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'=<>]+)))?/g)) {
      attrs[attribute[1]] = attribute[2] ?? attribute[3] ?? attribute[4] ?? '';
    }
    yield {tag: token[1].toLowerCase(), attrs};
  }
}

// Build the form collection from the actual JSP controls, including duplicate radio names and
// numeric indices. Missing/malformed controls remain missing; the harness never invents fields.
function page(saved = {}, confirmed = true) {
  const nodes = [];
  const byId = new Map();
  for (const {tag, attrs} of fixtureTags(jsp)) {
    const node = {tag, attrs, name: attrs.name || '', type: attrs.type || 'text',
      value: attrs.value ?? (attrs.type === 'checkbox' ? 'on' : ''), checked: Object.hasOwn(attrs, 'checked'),
      disabled: Object.hasOwn(attrs, 'disabled'),
      style: {display: attrs.style?.match(/display\s*:\s*([^;]+)/)?.[1].trim() || ''}};
    nodes.push(node);
    if (attrs.id) byId.set(attrs.id, node);
  }
  const elements = nodes.filter(node => ['input', 'textarea', 'select', 'button'].includes(node.tag));
  const form = {elements};
  for (const name of new Set(elements.map(field => field.name).filter(Boolean))) {
    const fields = elements.filter(field => field.name === name);
    const named = fields.length === 1 ? fields[0] : fields;
    if (fields.length > 1) Object.defineProperty(named, 'value', {
      get() { return fields.find(field => field.checked)?.value || ''; },
    });
    elements[name] = named;
    form[name] = named;
  }
  const effects = {closed: 0, printed: 0, confirmations: 0};
  const context = {document: {forms: [form], getElementById: id => byId.get(id) || null,
    querySelectorAll(selector) {
      assert.equal(selector, '#vt-form-values [data-field]');
      return Object.entries(saved).map(([field, value]) => ({dataset: {field, value}}));
    }},
    confirm() { effects.confirmations++; return confirmed; },
    close() { effects.closed++; }, print() { effects.printed++; }, focus() {}, resizeTo() {},
  };
  context.window = context;
  vm.createContext(context);
  vm.runInContext(globalScript, context);
  vm.runInContext(script, context);
  const run = handler => vm.runInContext(`(function () { ${handler} }).call(window)`, context);
  const fields = name => elements.filter(field => field.name === name);
  function click(field) {
    assert(!field.disabled, `Cannot click disabled ${field.name}`);
    if (field.type === 'checkbox') field.checked = !field.checked;
    if (field.type === 'radio') for (const candidate of fields(field.name)) candidate.checked = candidate === field;
    return field.attrs.onclick ? run(field.attrs.onclick) : undefined;
  }
  function submitted() {
    const result = new URLSearchParams();
    for (const field of elements) {
      if (!field.name || field.disabled || ['button', 'submit'].includes(field.type)) continue;
      if (['checkbox', 'radio'].includes(field.type) && !field.checked) continue;
      result.append(field.name, field.value);
    }
    return result;
  }
  context.initialize();
  return {context, nodes, form, fields, effects, click, run, submitted};
}

test('fresh VT initialization resolves every visible eye/foot control without inventing an iEx result', () => {
  const p = page();
  for (const type of [...eyeTypes, ...footTypes]) {
    const radios = p.fields(`value(${type}Value)`);
    assert.deepEqual(radios.map(field => field.value), ['yes', 'no']);
    assert(radios.every(field => field.disabled));
  }
  assert.match(xml, /<type>iEx<\/type>/);
  assert.match(xml, /<type>WHR<\/type>/);
  for (const type of ['iEx', 'WHR']) {
    for (const suffix of ['Value', 'Date', 'Comments']) {
      const fields = p.fields(`value(${type}${suffix})`);
      assert.equal(fields.length, 1);
      assert.equal(fields[0].type, 'hidden');
      assert.equal(fields[0].disabled, false);
      assert.equal(p.submitted().get(`value(${type}${suffix})`), '');
    }
  }
});

test('DM and PVD native handlers enable only the applicable eye and foot groups', () => {
  const p = page();
  const disabled = types => types.every(type => p.fields(`value(${type}Value)`).every(field => field.disabled));
  p.click(p.form['value(PVDValue)']);
  assert.equal(disabled(footTypes), false);
  assert.equal(disabled(eyeTypes), true);
  p.click(p.form['value(DMValue)']);
  assert.equal(disabled(eyeTypes), false);
  p.click(p.form['value(PVDValue)']);
  assert.equal(disabled(footTypes), false);
  p.click(p.form['value(DMValue)']);
  assert.equal(disabled(footTypes), true);
  assert.equal(disabled(eyeTypes), true);
});

for (const [diabetes, pvd] of [[false, false], [false, true], [true, false], [true, true]]) {
  test(`saved DM=${diabetes} and PVD=${pvd} restore the applicable controls before another save`, () => {
    const p = page({DMValue: diabetes ? 'yes' : 'no', PVDValue: pvd ? 'yes' : 'no',
      iDiaValue: 'yes', FTNeValue: 'no'});
    for (const type of eyeTypes) {
      assert(p.fields(`value(${type}Value)`).every(field => field.disabled === !diabetes));
    }
    for (const type of footTypes) {
      assert(p.fields(`value(${type}Value)`).every(field => field.disabled === !(diabetes || pvd)));
    }
    assert.equal(p.context.document.getElementById('examinationEye').style.display, diabetes ? 'block' : 'none');
    assert.equal(p.context.document.getElementById('examinationFoot').style.display, diabetes || pvd ? 'block' : 'none');
    p.context.onSave();
    assert.equal(p.submitted().get('value(iDiaValue)'), diabetes ? 'yes' : null);
    assert.equal(p.submitted().get('value(FTNeValue)'), diabetes || pvd ? 'no' : null);
  });
}

test('DpSc and FTEx No controls share their Yes group and clear without touching adjacent fields', () => {
  const p = page({DMValue: 'yes'});
  for (const [type, index] of [['DpSc', 'DpScId'], ['FTEx', 'FTExId']]) {
    const radios = p.fields(`value(${type}Value)`);
    assert.equal(radios.length, 2);
    p.click(radios[0]);
    assert.equal(p.form[`value(${type}Value)`].value, 'yes');
    p.click(radios[1]);
    assert.equal(p.form[`value(${type}Value)`].value, 'no');
    assert.deepEqual(p.submitted().getAll(`value(${type}Value)`), ['no']);
    p.run(`clearAll(${index}, ${index}-1);`);
    assert.equal(p.form[`value(${type}Value)`].value, '');
  }
  assert.equal(p.form['value(DMValue)'].checked, true);
  assert.equal(p.fields('=').length, 0);
});

test('foot exam Yes/No handler retains its dependent radio behavior', () => {
  const p = page({DMValue: 'yes'});
  p.click(p.fields('value(FTExValue)')[1]);
  for (const type of ['FTNe', 'FTIs', 'FTUl', 'FTIn', 'FTOt']) {
    assert.equal(p.form[`value(${type}Value)`].value, 'no');
    assert.equal(p.fields(`value(${type}Value)`)[0].disabled, true);
  }
  p.click(p.fields('value(FTExValue)')[0]);
  for (const type of footTypes.slice(1)) assert.equal(p.fields(`value(${type}Value)`)[0].disabled, false);
});

test('each clear link clears its own radio group and preserves every other result', () => {
  const p = page({DMValue: 'yes'});
  let precedingRadio;
  let cleared = 0;
  for (const node of p.nodes) {
    if (node.type === 'radio') precedingRadio = node;
    if (!node.attrs.href?.startsWith('javascript:clearAll(')) continue;
    assert(precedingRadio, 'Clear must follow an actual radio group');
    for (const field of p.form.elements) {
      if (field.type === 'radio') field.checked = field.value === 'yes';
    }
    p.run(node.attrs.href);
    for (const field of p.form.elements) {
      if (field.type !== 'radio') continue;
      assert.equal(field.checked, field.name !== precedingRadio.name && field.value === 'yes',
        `${precedingRadio.name} clear must preserve ${field.name}`);
    }
    cleared++;
  }
  assert.equal(cleared, 19);
});

test('waist and hip edits retain raw values without deriving a new ratio', () => {
  const p = page({WCValue: '090.5', HCValue: '101.25'});
  assert.equal(p.form['value(WHRValue)'].type, 'hidden');
  for (const [name, value] of [['WC', '092.50'], ['HC', '105.25']]) {
    const field = p.form[`value(${name}Value)`];
    field.value = value;
    if (field.attrs.onchange) p.run(field.attrs.onchange);
  }
  p.context.onSave();
  assert.equal(p.submitted().get('value(WCValue)'), '092.50');
  assert.equal(p.submitted().get('value(HCValue)'), '105.25');
  assert.equal(p.submitted().get('value(WHRValue)'), '');
});

for (const mode of ['onSave', 'onSaveExit']) {
  test(`${mode} preserves hidden iEx/WHR history and copies visible support fields as literal values`, () => {
    const literal = '<b>literal & "quoted"</b>\nsecond line';
    const saved = {DMValue: 'yes', iExValue: 'yes', iExDate: '2020-01-02', iExComments: literal,
      WHRValue: '0.8750', WHRDate: '2019-06-07', WHRComments: literal,
      DpScValue: 'no', FTExValue: 'no', iDiaValue: 'yes'};
    const p = page(saved);
    p.form.iDate.value = '2026-03-04';
    p.form.iComments.value = 'New visible eye comment';
    p.form.SmkDate.value = '2026-03-05';
    p.form.SmkComments.value = literal;
    p.form.FTDate.value = '2026-03-06';
    p.form.FTComments.value = literal;
    p.form['value(WCValue)'].value = '090.50';
    p.form['value(HCValue)'].value = '100.25';
    const result = p.context[mode]();
    assert.equal(p.form.submit.value, mode === 'onSave' ? 'save' : 'exit');
    if (mode === 'onSaveExit') assert.equal(result, true);
    const posted = p.submitted();
    for (const type of ['iEx', 'WHR']) {
      for (const suffix of ['Value', 'Date', 'Comments']) {
        assert.equal(posted.get(`value(${type}${suffix})`), saved[`${type}${suffix}`]);
      }
    }
    for (const type of eyeTypes) {
      assert.equal(posted.get(`value(${type}Date)`), '2026-03-04');
      assert.equal(posted.get(`value(${type}Comments)`), 'New visible eye comment');
    }
    for (const type of ['SmkS', 'SmkH', 'SmkC', ...footTypes]) {
      assert.equal(posted.get(`value(${type}Comments)`), literal);
    }
    const reopened = {};
    for (const [name, value] of posted) {
      const key = name.match(/^value\(([^)]+)\)$/);
      if (key) reopened[key[1]] = value;
    }
    const again = page(reopened);
    for (const key of ['iExValue', 'iExDate', 'iExComments', 'WHRValue', 'WHRDate', 'WHRComments',
      'DpScValue', 'FTExValue', 'iDiaValue']) {
      assert.equal(again.submitted().get(`value(${key})`), saved[key]);
    }
  });
}

test('new saves leave the unedited iEx value empty and Save and Exit cancellation is retained', () => {
  const p = page({}, false);
  assert.equal(p.context.onSaveExit(), false);
  assert.equal(p.effects.confirmations, 1);
  assert.equal(p.submitted().get('value(iExValue)'), '');
});

test('every existing inline click/change/clear handler executes against the real control names', () => {
  const p = page({DMValue: 'yes', PVDValue: 'yes'});
  for (const node of p.nodes) {
    for (const event of ['onclick', 'onchange']) {
      if (node.attrs[event]) assert.doesNotThrow(() => p.run(node.attrs[event]), `${node.name} ${event}`);
    }
    // Every anchor in this JSP is an inline event script. Execute each one, including its
    // JavaScript label, rather than silently ignoring an unexpected anchor in the fixture.
    if (node.tag === 'a' && Object.hasOwn(node.attrs, 'href')) {
      assert.doesNotThrow(() => p.run(node.attrs.href), node.attrs.href);
    }
  }
  assert(p.effects.printed > 0);
  assert(p.effects.closed > 0);
});

test('Show/Hide Detail toggles the existing counselling and medication sections in both directions', () => {
  const p = page();
  const sections = ['subjective', 'detailHistory', 'detailFT', 'detailEye', 'detailLab',
    'counselling', 'medications', 'medicationAllergies'];
  const section = id => p.context.document.getElementById(id);
  for (const id of sections) assert.equal(section(id).style.display, 'none', id);
  p.context.showHideDetail();
  for (const id of sections) assert.equal(section(id).style.display, 'block', id);
  p.context.showHideDetail();
  for (const id of sections) assert.equal(section(id).style.display, 'none', id);
});

test('each shared comment box is read back from a row its save writes, encoded', () => {
  // The eye and foot comment boxes are copied into every row of their section on save. A box
  // rendered from a column no save writes shows nothing (or stale legacy text) on reopen, and
  // the next save copies that over the comment the clinician entered (the iEx regression).
  const fieldOf = new Map([...script.matchAll(/case 'value\((\w+)\)':\s*(\w+) = i;/g)].map(match => [match[2], match[1]]));
  for (const box of ['iComments', 'FTComments']) {
    const written = new Set([...script.matchAll(new RegExp(
      `elements\\[(\\w+)\\]\\.value = document\\.forms\\[0\\]\\.${box}\\.value`, 'g'))]
      .map(match => fieldOf.get(match[1])));
    assert(written.size > 0 && !written.has(undefined), `${box} save targets resolve to named fields`);
    const content = jsp.match(new RegExp(`<textarea[^>]*\\bname="${box}"[^>]*>([\\s\\S]*?)</textarea>`))[1];
    const rendered = [...content.matchAll(/\b(\w+Comments)\b/g)].map(match => match[1]);
    assert(rendered.length > 0, `${box} renders a stored comment`);
    assert(written.has(rendered[0]), `${box} renders ${rendered[0]}, which its save never writes`);
    if (box === 'iComments') {
      // Stored free text reaches the page encoded, with no template whitespace added to it.
      assert.match(content, /^<carlos:encode value="\$\{not empty iDiaComments \? iDiaComments : iExComments\}"\/>$/);
      assert(!written.has('iExComments'), 'iEx stays retained history, never rewritten from the box');
    }
  }
});

test('no stored clinical text is printed raw: every output is a CARLOS encoder (issue #4681)', () => {
  // Measurement values, dates, descriptions, instructions, comments, drugs, allergies and ongoing
  // concerns are stored free text. check-encoder-null-safety.sh does not flag raw EL or <%= %>
  // output, so this is the guard. Server-controlled context-path/scheme output and the
  // security-tag/guard scriptlets are the only allowed raw output.
  const body = jsp.replace(/<%--[\s\S]*?--%>/g, '');
  const printed = [...body.matchAll(/<%=([\s\S]*?)%>/g)].map(match => match[1].trim());
  assert.deepEqual(printed.filter(expression => /getAttribute/.test(expression)), [],
    'request attributes must never be printed through <%= %>');
  // The only scriptlet outputs left are server-controlled URLs and the encoded error list.
  for (const expression of printed) {
    assert(/^(request\.getContextPath\(\)|roleName2\$|true|request\.getScheme\(\) \+ .*|error)$/.test(expression), expression);
  }
  assert(body.includes('<carlos:encode value="<%= error %>"/>'), 'action errors are encoded');
  // Remove every encoded output in place, then nothing but server-controlled EL may remain. Checking each
  // occurrence where it stands (not whether the same expression is encoded somewhere else) means a raw
  // ${requestScope.BPLastData} cannot hide behind an encoded one.
  const remaining = body
    .replace(/<carlos:encode\s+value="[^"]*"\s*\/>/g, '')
    .replace(/\$\{carlos:for\w+\([^}]*\)\}/g, '');
  const rawEl = [...remaining.matchAll(/\$\{([^}]*)}/g)].map(match => match[1].trim());
  assert.deepEqual(rawEl.filter(expression => !(/^(?:not\s+)?empty\s+\w+$/.test(expression)
    || expression === 'pageContext.request.contextPath' || expression === 'requestScope.formValues'
    || expression === 'drugs' || expression === 'allergies')), [], 'raw EL output of stored data');
  for (const attribute of ['SmkSDate', 'SmkSComments', 'FTNeDate', 'iDiaDate']) {
    assert(body.includes(`\${carlos:forHtmlAttribute(requestScope.${attribute})}`), `${attribute} is attribute-encoded`);
  }
  const textarea = body.match(/<textarea[^>]*\bname="FTComments"[^>]*>([\s\S]*?)<\/textarea>/)[1];
  assert.equal(textarea, '<carlos:encode value="${requestScope.FTExComments}"/>',
    'foot comment is encoded on one line with no template whitespace');
  for (const loop of ['drg', 'alg']) {
    assert(body.includes(`<carlos:encode value="\${${loop}}"/><br>`), `${loop} list entries are encoded`);
  }
  for (const stored of ['WHRBLastData', 'WCLDDate', 'HCLastData', 'WTLDDate', 'ongoingConcerns', 'MedADesc', 'BPMeasuringInstrc']) {
    assert(body.includes(`<carlos:encode value="\${requestScope.${stored}}"/>`), `${stored} is body-encoded`);
  }
  assert(body.includes('<carlos:encode value="${requestScope[\'24UADesc\']}"/>'), 'digit-leading attribute uses bracket EL');
  assert(body.includes('var varpage = "${carlos:forJavaScript(requestScope.decisionSupportURL)}";'));
});

test('saved VT values stay in encoded data attributes and are restored through DOM properties', () => {
  assert(jsp.includes('data-field="${carlos:forHtmlAttribute(field.key)}"'));
  assert(jsp.includes('data-value="${carlos:forHtmlAttribute(field.value)}"'));
  assert(script.includes('field.value = stored.dataset.value;'));
  assert(!script.includes('innerHTML'));
});

test('affected VT controls have unique IDs and contextual labels without changing their clinical names', () => {
  const p = page();
  const identified = p.nodes.filter(node => node.attrs.id);
  const byId = new Map(identified.map(node => [node.attrs.id, node]));
  assert.equal(byId.size, identified.length, 'Every ID must resolve unambiguously');
  const labelled = [];
  function assertReferences(field, expected) {
    assert(field, 'The existing clinical control must be present');
    assert.deepEqual(field.attrs['aria-labelledby'].split(/\s+/), expected);
    for (const id of expected) assert(byId.has(id), `Missing label reference ${id}`);
    labelled.push(field);
  }
  for (const type of ['DpSc', 'StSc', 'LcCt', 'MedG', 'MedN', 'MedR', 'MedA', 'WHRB', 'FTEx']) {
    for (const answer of ['yes', 'no']) {
      const field = p.fields(`value(${type}Value)`).find(node => node.value === answer);
      const answerLabel = `vt-${type}-${answer}-label`;
      assertReferences(field, [`vt-${type}-label`, answerLabel]);
      assert.equal(byId.get(answerLabel).tag, 'label');
      assert.equal(byId.get(answerLabel).attrs.for, field.attrs.id, 'Native radio label activates its own answer');
    }
    if (type !== 'FTEx') {
      assertReferences(p.form[`value(${type}Comments)`], [`vt-${type}-label`, 'vt-comments-label']);
    }
  }
  for (const type of ['BP', 'WC', 'HC', 'HR', 'HT', 'WT']) {
    const field = p.form[`value(${type}Value)`];
    assertReferences(field, [`vt-${type}-label`, 'vt-new-data-label']);
    assert.equal(byId.get(`vt-${type}-label`).tag, 'label');
    assert.equal(byId.get(`vt-${type}-label`).attrs.for, field.attrs.id);
    assertReferences(p.form[`value(${type}Comments)`], [`vt-${type}-label`, 'vt-comments-label']);
  }
  for (const name of ['value(objective)', 'FTDate', 'FTComments', 'iComments']) {
    const field = p.form[name];
    assert.equal(p.nodes.filter(node => node.tag === 'label' && node.attrs.for === field.attrs.id).length, 1);
    labelled.push(field);
  }
  assert.equal(p.form.FTDate.attrs.id, 'FTDate', 'Keep the existing ID used by callers');
  assert.equal(labelled.length, 42);
  assert(labelled.every(field => field.type !== 'hidden'));
  assert.equal(p.form.elements.length, 186, 'Accessibility must not introduce clinical controls');
});

test('medication comments follow natural DOM order without positive tab indices', () => {
  const p = page();
  const preceding = p.form['value(MedRComments)'];
  const comments = p.form['value(MedAComments)'];
  for (const element of p.form.elements) {
    assert(!element.attrs.tabindex || Number(element.attrs.tabindex) <= 0, `Positive tabindex on ${element.name}`);
  }
  assert.equal(comments.attrs.tabindex, preceding.attrs.tabindex);
  assert.equal(Object.hasOwn(comments.attrs, 'abindex'), false);
  assert(p.form.elements.indexOf(comments) > p.form.elements.indexOf(preceding));
});

test('entry matrices retain four real column headers with names even when detail text is collapsed', () => {
  for (const id of ['vt-medication-adherence', 'vital', 'detailVital']) {
    const tableStart = jsp.lastIndexOf('<table', jsp.indexOf(`id="${id}"`));
    const rowStart = jsp.indexOf('<tr', tableStart);
    const row = jsp.slice(rowStart, jsp.indexOf('</tr>', rowStart));
    const nodes = [...fixtureTags(row)];
    const headers = nodes.filter(node => node.tag === 'th');
    assert.equal(headers.length, 4, `${id} has its own headers, not headers borrowed from a nested table`);
    assert(headers.every(node => node.attrs.scope === 'col'));
    const hiddenHeadingIds = id === 'vt-medication-adherence'
      ? ['medHead1', 'medHead2', 'medHead3', 'medHead4']
      : id === 'vital' ? ['vitalHead1', 'vitalHead2', 'vitalHead3'] : [];
    for (const headingId of hiddenHeadingIds) {
      assert(nodes.some(node => node.attrs.id === headingId));
      assert(headers.some(node => node.attrs['aria-labelledby'] === headingId),
        `${headingId} remains an explicit accessible-name source while display:none`);
    }
    assert(![...fixtureTags(jsp.slice(tableStart, rowStart))].some(node => node.attrs.role === 'presentation'));
  }
});

test('presentational tables only arrange one historical date and value, never entry controls or data columns', () => {
  const tables = [...jsp.matchAll(/<table role="presentation"[^>]*>([\s\S]*?)<\/table>/g)];
  assert.equal(tables.length, 14);
  for (const [, body] of tables) {
    const nodes = [...fixtureTags(body)];
    assert.deepEqual(nodes.map(node => node.tag).filter(tag => tag !== 'carlos:encode'), ['tr', 'td', 'tr', 'td']);
    assert.match(body, /LDDate/);
    assert.match(body, /LastData/);
  }
});

test('the objective editor toggles as a labelled layout section without table-header semantics', () => {
  const p = page();
  const section = p.context.document.getElementById('objective');
  assert.equal(section.tag, 'div');
  assert.equal(section.style.display, 'none');
  p.context.showHideItem('objective');
  assert.equal(section.style.display, 'block');
  p.form['value(objective)'].value = 'Literal objective <finding>';
  p.context.showHideItem('objective');
  assert.equal(section.style.display, 'none');
  assert.equal(p.submitted().get('value(objective)'), 'Literal objective <finding>');
});

test('fixture scanner skips fake controls inside JSP, comments and mixed-case script blocks', () => {
  const source = `<% String example = "<input name='fake-jsp'>"; %>
    <%-- <input name="fake-jsp-comment"> --%>
    <!-- <input name="fake-comment"> -->
    <ScRiPt type="text/javascript">const example = '<input name="fake-script">';</sCrIpT>
    <SCRIPT src="fixture.js" data-example="a > b"><input name="fake-external-script"></SCRIPT>
    <input name="real" value="actual">`;
  assert.deepEqual([...fixtureTags(source)], [{tag: 'input', attrs: {name: 'real', value: 'actual'}}]);
});

test('fixture scanner rejects unterminated blocks instead of inventing enclosed controls', () => {
  for (const source of ['<!-- <input name="fake">', '<% String example = "<input name=\'fake\'>";',
    '<script>const example = \'<input name="fake">\';',
    '<SCRIPT src="fixture.js" data-example="a > b"><input name="fake">']) {
    assert.throws(() => [...fixtureTags(source)], /Unterminated .* block in fixture/);
  }
});

test('fixture scanner consumes complete script end tags with whitespace and ignored attributes', () => {
  for (const closing of ['</script\t\n bar>', '</script foo="bar">', '</SCRIPT data-example="a > b">', '</script/>', '</script >']) {
    const source = `<script>const text = '<input name="fake-script">';
      </scripture></script-not-real><input name="still-inside-script">${closing}<input name="real">`;
    assert.deepEqual([...fixtureTags(source)], [{tag: 'input', attrs: {name: 'real'}}], closing);
  }
  assert.deepEqual([...fixtureTags('<script-example><input name="real"></script-example>')], [
    {tag: 'script-example', attrs: {}}, {tag: 'input', attrs: {name: 'real'}},
  ]);
});

test('fixture scanner retains quoted delimiters and event attributes after JSP labels', () => {
  const source = `<input name="value(WTComments)" value="a > b" title='c > d'>
    <input type="submit" value="<fmt:message key="global.btnExit"/>" onclick="return onExit();">
    <input name="value(formId)" value="\${requestScope.formValues['formId']}">
    <a href="javascript:showHideItem('detailEye');" title="Eye > detail">Open</a>`;
  assert.deepEqual([...fixtureTags(source)], [
    {tag: 'input', attrs: {name: 'value(WTComments)', value: 'a > b', title: 'c > d'}},
    {tag: 'input', attrs: {type: 'submit', value: 'Label', onclick: 'return onExit();'}},
    {tag: 'input', attrs: {name: 'value(formId)', value: ''}},
    {tag: 'a', attrs: {href: "javascript:showHideItem('detailEye');", title: 'Eye > detail'}},
  ]);
});
