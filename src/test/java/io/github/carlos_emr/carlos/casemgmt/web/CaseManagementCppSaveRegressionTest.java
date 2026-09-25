/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.casemgmt.web;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Case management CPP save regressions")
@Tag("unit")
@Tag("casemgmt")
class CaseManagementCppSaveRegressionTest {

    private static final Path CASE_MGMT_VIEW_JS_JSP =
            resolveProjectPath(Path.of("src/main/webapp/js/newCaseManagementView.js.jsp"));
    private static final Path NOTE_ISSUE_LIST_JSP =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/noteIssueList.jsp"));
    private static final Path CASE_MGMT_ENTRY_JSP =
            resolveProjectPath(Path.of("src/main/webapp/WEB-INF/jsp/casemgmt/CaseManagementEntry.jsp"));

    /**
     * Every clinician-authored free-text argument the note route accepts: the CPP editor body,
     * the encounter note under the three names it travels as (the serialized form, the draft
     * autosave, and the save-on-switch's {@code noteTxt}), and the editor's six prose
     * extension fields. Not every {@code <input type="text">} qualifies: the editor's date
     * boxes and age-at-onset are text inputs too but hold no prose, and they stay inspected
     * along with the {@code <select>} pickers — see the assertions below.
     */
    private static final String[] CLINICIAN_FREE_TEXT_ARGUMENTS = {
            "value", "caseNote_note", "note", "noteTxt", "problemdescription", "problemstatus",
            "treatment", "exposuredetail", "relationship", "procedure"};
    /** Editor controls that carry no prose and must never appear in exclusion 1010. */
    private static final String[] INSPECTED_EDITOR_CONTROLS = {
            "lifestage", "hidecpp", "startdate", "resolutiondate", "proceduredate", "ageatonset"};
    /**
     * The CRS tag groups that read prose as an attack. {@code attack-rfi} is on this list
     * for the note route even though 1045 and 1050 keep it: 931100 is anchored on the whole
     * argument and fires on a CPP box or note that IS a pasted IP-address link (an internal
     * PACS URL), measured 403 on the packaged install — see the rule's comment.
     */
    private static final String[] CONTENT_ATTACK_TAGS = {
            "attack-sqli", "attack-xss", "attack-rce",
            "attack-injection-php", "attack-protocol", "attack-lfi", "attack-rfi"};

    /** The allocation the write loop must perform per key, matched as source text. */
    private static final String ALLOCATION = "new CaseManagementNoteExt()";

    @Test
    @DisplayName("CPP saves should refresh Unresolved Issues without relying on a missing form element (#3422)")
    void shouldRefreshUnresolvedIssues_afterCppSave() throws IOException {
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        int callbackStart = js.indexOf("function onIssueUpdate()");
        assertThat(callbackStart).as("CPP issue update callback must exist").isGreaterThan(0);
        // Bound the slice by the next function declaration rather than by a specific
        // indentation of the closing brace, which a reformat would silently break.
        int nextFunction = js.indexOf("function ", callbackStart + 1);
        int callbackEnd = nextFunction < 0 ? js.length() : nextFunction;
        assertThat(callbackEnd).isGreaterThan(callbackStart);
        String callbackBody = js.substring(callbackStart, callbackEnd);

        assertThat(callbackBody)
                .as("the encounter demographic is already available as module state; the form element is absent")
                .doesNotContain("$(\"demographicNo\")")
                .contains("&& demographicNo")
                .contains("encodeURIComponent(demographicNo)")
                .as("both query parameters carry encoded values; reloadURL was already "
                        + "encoded before #3422 and must stay that way")
                .contains("encodeURIComponent(ctx + \"/encounter/displayIssues\")")
                .contains("cmd=unresolvedIssues")
                .contains("loadDiv('unresolvedIssueslist', reloadUrl, 0)");
    }

    @Test
    @DisplayName("Repeated issue fragment updates should not redeclare block-scoped variables (#3422)")
    void shouldScopeFragmentVariables_whenIssueUpdatesRepeat() throws IOException {
        String jsp = Files.readString(NOTE_ISSUE_LIST_JSP, StandardCharsets.UTF_8);

        String noteTextAssignment = "var noteTxt = \"${carlos:forJavaScriptBlock(noteTxt)}\";";
        int noteTextStart = jsp.indexOf(noteTextAssignment);
        assertThat(noteTextStart).as("the AJAX note-text assignment must exist").isGreaterThanOrEqualTo(0);
        int fragmentScriptStart = jsp.lastIndexOf("<script type=\"text/javascript\">", noteTextStart);
        assertThat(fragmentScriptStart).as("the AJAX note text must be inside a script block").isGreaterThanOrEqualTo(0);
        int fragmentScriptEnd = jsp.indexOf("</script>", fragmentScriptStart);
        assertThat(fragmentScriptEnd)
                .as("the AJAX note text must be inside the same script block being tested")
                .isGreaterThan(noteTextStart);
        String fragmentScript = jsp.substring(fragmentScriptStart, fragmentScriptEnd + "</script>".length());

        assertThat(fragmentScript)
                .as("the script containing the AJAX note text may run repeatedly and must isolate all declarations")
                .containsPattern("<script type=\"text/javascript\">\\s*\\(function \\(\\) \\{")
                .containsPattern("\\}\\(\\)\\);\\s*</script>")
                .as("stored clinical text in the fragment must use JavaScript-block encoding")
                .doesNotContain("fn:escapeXml(noteTxt)")
                .contains(noteTextAssignment)
                .contains("${carlos:forJavaScriptBlock(caseManagementEntryForm.caseNote.encounter_type)}");
    }

    @Test
    @DisplayName("Note styling should read the note index from page scope, not the empty scriptlet local (#3422)")
    void shouldReadNoteIndexFromPageScope_forNoteStyling() throws IOException {
        String jsp = Files.readString(NOTE_ISSUE_LIST_JSP, StandardCharsets.UTF_8);

        // The scriptlet declares `String noteIndex = ""` and never reassigns it, while
        // <c:set var="noteIndex"> carries the real index in page scope. Reading the
        // scriptlet local yielded the element id "bgColour", which never resolves, so
        // note colour styling silently no-opped.
        assertThat(jsp)
                .doesNotContain("<%=noteIndex%>")
                .contains("\"bgColour\" + \"${carlos:forJavaScriptBlock(noteIndex)}\"")
                .contains("\"summary\" + \"${carlos:forJavaScriptBlock(noteIndex)}\"");
    }

    @Test
    @DisplayName("Clinician free text on the note route should be exempt from WAF content scoring (#3611)")
    void shouldExemptClinicianFreeText_fromPackagedWafScoring() throws IOException {
        // Verified on a packaged Ubuntu 26.04 install: ordinary clinical prose in the
        // encounter note — a pasted PACS link whose own query string contains "&cmd" —
        // scored CRS 932110 and answered POST /carlos/CaseManagementEntry with 403. That
        // one note text broke four workflows, because the same body travels under four
        // parameter names on this route: ARGS:caseNote_note on the serialized
        // caseManagementEntryForm, ARGS:note on the 5s draft autosave, ARGS:noteTxt on the
        // save that runs when unsaved text is left for another note, and ARGS:value in
        // the CPP editor. The CPP item itself still saved (its own POST carries ARGS:value,
        // already exempt for SQLi/XSS), so the clinician saw only a spurious
        // "403 ... your session has expired" alert on a Social History entry that was in
        // fact on disk. Miss any one name and the workflow only half works.
        String rule = readExclusionRule("1010");

        assertThat(rule)
                .contains("^/carlos/CaseManagementEntry(?:[;?]|$)")
                .contains("@streq POST");
        for (String argument : CLINICIAN_FREE_TEXT_ARGUMENTS) {
            for (String tag : CONTENT_ATTACK_TAGS) {
                assertThat(rule)
                        .as("clinician free text %s is exempt from %s", argument, tag)
                        .contains("ctl:ruleRemoveTargetByTag=" + tag + ";ARGS:" + argument + ",");
            }
        }
        // The editor's two <select> pickers, its three date boxes and age-at-onset carry no
        // prose, so they have nothing to false-positive on and stay fully inspected;
        // exempting one would only hand a forged POST a rule-free parameter to smuggle a
        // payload in.
        for (String control : INSPECTED_EDITOR_CONTROLS) {
            assertThat(rule)
                    .as("non-prose control %s is not exempted", control)
                    .doesNotContain(";ARGS:" + control + ",")
                    .doesNotContain(";ARGS:" + control + "\"");
        }
        assertThat(rule)
                // reloadUrl is an app-generated URL, not prose, and keeps its narrower
                // treatment: the keyword tags plus only rule 932110, never the whole
                // attack-rce family.
                .contains("ctl:ruleRemoveTargetById=932110;ARGS:reloadUrl")
                .doesNotContain("ctl:ruleRemoveTargetByTag=attack-rce;ARGS:reloadUrl")
                // reloadUrl is a relative app path with no scheme, so no RFI rule can fire
                // on a legitimate value; leave the family on it.
                .doesNotContain("ctl:ruleRemoveTargetByTag=attack-rfi;ARGS:reloadUrl")
                // Per-argument only: nothing in this rule may drop a signature request-wide,
                // wherever in the action list such a directive might sit.
                .doesNotContain("ctl:ruleRemoveById=")
                .doesNotContain("ctl:ruleRemoveByTag=");
        // The same for every tag family, by name: a ruleRemoveTargetByTag with the tag but
        // no ";ARGS:<name>" target would switch that whole family off for the request while
        // every per-argument assertion above still passed.
        for (String tag : CONTENT_ATTACK_TAGS) {
            assertThat(rule)
                    .as("tag %s is not removed request-wide", tag)
                    .doesNotContain("ctl:ruleRemoveTargetByTag=" + tag + ",")
                    .doesNotContain("ctl:ruleRemoveTargetByTag=" + tag + "\"")
                    .doesNotContain("ctl:ruleRemoveTargetByTag=" + tag + "\\");
        }
    }

    @Test
    @DisplayName("the legacy entry view should encode the note it puts back into the textarea")
    void shouldEncodeNoteBody_inLegacyEntryTextarea() throws IOException {
        // Exclusion 1010 stops the WAF scoring ARGS:caseNote_note for XSS, so the
        // application's output encoding is the whole stored-XSS defence for that text. The
        // Struts "view" result of the note route renders CaseManagementEntry.jsp, which used
        // to write ${caseNote.note} raw inside its <textarea>: a note containing
        // "</textarea><script>" would have run on the next open of that legacy view.
        String jsp = Files.readString(CASE_MGMT_ENTRY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("${carlos:forHtmlContent(caseNote.note)}")
                .doesNotContain("${caseNote.note}");
    }

    @Test
    @DisplayName("the legacy entry view's autosave should post the note with encodeURIComponent")
    void shouldEncodeLegacyAutosaveNote_asOneParameter() throws IOException {
        // escape() leaves "+" alone and form decoding turns it into a space, so "H&P +1"
        // arrived as "H&P  1" in the draft; encodeURIComponent keeps the note intact.
        String jsp = Files.readString(CASE_MGMT_ENTRY_JSP, StandardCharsets.UTF_8);

        assertThat(jsp)
                .contains("\"&note=\" + encodeURIComponent(obj.value)")
                .doesNotContain("escape(obj.value)");
    }

    @Test
    @DisplayName("the save-on-switch view should HTML-escape the note before splicing it into markup")
    void shouldEscapeNoteText_beforeInsertingSavedNote() throws IOException {
        // noteIssueList.jsp hands completeChangeToView() the typed note as a JavaScript
        // string, and the function used to splice it straight into a <span> via
        // insertAdjacentHTML/update. With attack-xss off ARGS:noteTxt, that was a DOM XSS.
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        assertThat(js)
                .contains("function escapeNoteText(text)")
                .contains("note = escapeNoteText(note).replace(/\\n/g, \"<br>\");")
                .doesNotContain("note = note.replace(/\\n/g, \"<br>\");");
    }

    @Test
    @DisplayName("reopening a saved note for editing should decode the rendered text, not copy its HTML")
    void shouldDecodeRenderedNote_whenReopenedForEditing() throws IOException {
        // editNote() used to take the view's innerHTML into the textarea, so a note holding
        // "A&B" (or, once the view escapes, "<") came back as literal "&amp;B" and was resaved
        // corrupted. It now reads the text of the rendered note with <br> as newlines.
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        assertThat(js)
                .contains("function renderedNoteText(el)")
                .contains("payload = renderedNoteText($(txtId));")
                .doesNotContain("payload = $(txtId).innerHTML;");
    }

    @Test
    @DisplayName("the note escaper's behaviour should be pinned by the node test that runs it")
    void shouldPinEscaperBehaviour_inNodeTest() throws IOException {
        // The assertions here pin WHERE escapeNoteText() is called; what it does (the escape
        // map, and that "</textarea>" cannot close the editor while the text still decodes
        // back unchanged) is exercised by running the function itself under node, which a
        // Java test cannot do. Keep the two in step: npm run test:scripts.
        String nodeTest = read(Path.of("scripts", "note-text-escaping.test.js"));

        assertThat(nodeTest)
                .contains("function escapeNoteText(text)")
                .contains("</textarea><img src=x onerror=alert(1)>")
                .contains("decodeEntities(escapeNoteText(note))");
    }

    @Test
    @DisplayName("reopening a saved note for editing should HTML-escape the decoded text inside the textarea markup")
    void shouldEscapeDecodedNote_whenBuildingEditorMarkup() throws IOException {
        // renderedNoteText() hands back the raw note, and editNote() splices it into
        // "<textarea ...>" + payload + "</textarea>" through insertAdjacentHTML. Unescaped, a
        // note holding "</textarea><img onerror=...>" would close the element and run when
        // the next clinician opened it. Escaped, the textarea's RCDATA decodes it back to the
        // clinician's text.
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        int editNote = js.indexOf("function editNote(e)");
        int escaped = js.indexOf("payload = escapeNoteText(payload);", editNote);
        int spliced = js.indexOf("id='\" + caseNote + \"'>\" + payload + \"<\\/textarea>\"", editNote);
        assertThat(editNote).as("editNote() is defined").isGreaterThanOrEqualTo(0);
        assertThat(escaped).as("editNote() escapes the payload").isGreaterThan(editNote);
        assertThat(spliced).as("editNote() splices the payload into the textarea markup").isGreaterThan(escaped);

        // newNote() builds its textarea the same way from the pre-populated reason (an
        // appointment reason, decoded into a JavaScript string), so it gets the same escape.
        assertThat(js)
                .contains("id='caseNote_note\" + safeNoteIdSuffix + \"'>\" + escapeNoteText(reason) + \"<\\/textarea>\"")
                .doesNotContain("+ \"'>\" + reason + \"<\\/textarea>\"");
    }

    @Test
    @DisplayName("save-on-switch should hand the saved note text and its container to the view")
    void shouldRenderSavedNote_afterSaveOnSwitch() throws IOException {
        // The fragment reads ${noteTxt}, a scoped attribute, so ajaxsave() must set it or the
        // view of the note just saved renders empty. It then promoted the container with
        // $("nc" + origId), but the page renders the initial note as nc<offset><idx> ("nc00"
        // for n0), so that lookup was null and threw before the fragment finished.
        String action = read(Path.of("src/main/java/io/github/carlos_emr/carlos/casemgmt/web/CaseManagementEntry2Action.java"));
        String jsp = Files.readString(NOTE_ISSUE_LIST_JSP, StandardCharsets.UTF_8);

        assertThat(action).contains("request.setAttribute(\"noteTxt\", noteTxt);");
        assertThat(jsp)
                .contains("var savedNoteDiv = $(\"n\" + newId);")
                .contains("noteContainer.id = \"nc\" + maxNcId;")
                .doesNotContain("$(\"nc\" + origId)");
    }

    @Test
    @DisplayName("save-on-switch should post the whole note as one noteTxt parameter")
    void shouldEncodeNoteTxt_asOneParameter() throws IOException {
        // ajaxSaveNote() is the save that runs when a note with unsaved text is left for
        // another note or a new one. It built its body with encodeURI, which leaves & = +
        // intact, so "H&P" or a pasted link with "&cmd=" reached ajaxsave() cut off at the
        // first "&" (the remainder became stray parameters) and a "+" arrived as a space.
        // Exempting ARGS:noteTxt in exclusion 1010 only helps once the text travels intact.
        String js = Files.readString(CASE_MGMT_VIEW_JS_JSP, StandardCharsets.UTF_8);

        assertThat(js)
                .contains("\"&noteTxt=\" + encodeURIComponent(noteTxt)")
                .doesNotContain("encodeURI(noteTxt)");
    }

    @Test
    @DisplayName("change detection should treat disagreeing duplicate rows as a change (#3739)")
    void shouldTreatDivergentDuplicatesAsChange_inIssueNoteSave() throws IOException {
        // THE RECONCILIATION IS UNREACHABLE IF THE SAVE RETURNS FIRST. getExtByNote() orders id
        // desc, so stopping at the first matching row reads the NEWEST, while
        // NotesService.getNote() assigns from every row and ends on the OLDEST. On a note that
        // already holds two rows for one key, submitting the value the newest row has looked like
        // "nothing changed": issueNoteSave returned early and the write loop that refreshes every
        // row never ran, so the chart kept showing the older value. The inner loop must therefore
        // walk EVERY row for the key rather than break at the first.
        String action = read(Path.of("src", "main", "java", "io", "github", "carlos_emr", "carlos",
                "casemgmt", "web", "CaseManagementEntry2Action.java"));

        int blockStart = action.indexOf("List<CaseManagementNoteExt> cmeList = caseManagementNoteExtDao.getExtByNote(");
        assertThat(blockStart)
                .as("issueNoteSave still reads the note's existing extensions for change detection")
                .isGreaterThan(-1);
        String block = action.substring(blockStart, action.indexOf("// if note has not changed don't save", blockStart));

        assertThat(block)
                .as("every row for the key is compared, so a disagreeing duplicate is itself a change")
                .contains("extKeyMatched = true;")
                .doesNotContain("extKeyMatched = true;\n                    break;");
        assertThat(block)
                .as("the date comparison is resolved once per key; re-normalising an already "
                        + "normalised value on a second row would compare the wrong thing")
                .contains("String comparableDate =")
                .contains("String comparableValue =");
        // partialDateFormat() returns the EMPTY STRING for a full YYYY-MM-DD date, so the
        // "was a value added?" test has to read what was submitted rather than its comparison
        // form -- otherwise a newly typed complete date reads as nothing added and the early
        // return discards it.
        assertThat(block)
                .as("a newly added full date is not mistaken for an empty field")
                .contains("filled(submitted) && !extKeyMatched")
                .doesNotContain("filled(comparableValue)");
    }

    @Test
    @DisplayName("the note route should upsert one note extension per key (#3739)")
    void shouldUpsertOneNoteExtensionPerKey_inIssueNoteSave() throws IOException {
        // Two regressions have to stay shut here, and only one of them is about allocation.
        // (1) saveNoteExt() is a JPA persist(), so reusing one entity across the keys collapses
        //     them into the row the first call created and only the last key survives.
        // (2) saveNote() merges an existing note instead of revising it under a fresh id, so a
        //     blind persist() per save adds a second row for a key that already has one; with
        //     getExtByNote() ordered id desc and consumers assigning from every row, the oldest
        //     wins and an edited value reads back as the one it replaced.
        // CaseManagementCppExtPersistenceIntegrationTest pins the persistence behaviour behind
        // both; this guard pins the shape of the write so neither can quietly come back.
        String action = read(Path.of("src", "main", "java", "io", "github", "carlos_emr", "carlos",
                "casemgmt", "web", "CaseManagementEntry2Action.java"));

        int blockStart = action.indexOf("/* save extra fields */");
        assertThat(blockStart).as("the extension save block is present").isGreaterThan(0);
        int blockEnd = action.indexOf("caseManagementMgr.getEditors(note);", blockStart);
        assertThat(blockEnd).as("the extension save block is bounded").isGreaterThan(blockStart);
        String block = action.substring(blockStart, blockEnd);

        assertThat(block)
                .as("the block indexes the rows the note already has before writing")
                .contains("caseManagementNoteExtDao.getExtByNote(note.getId())")
                .as("an existing key is updated in place rather than inserted again")
                .contains("caseManagementMgr.updateNoteExt(")
                // A note from before the per-key fix can hold several rows for one key, and no
                // reader ignores the extras: change detection scans every row and calls any
                // divergence a change, while NotesService assigns from every row in id-desc
                // order so the oldest is what the chart shows. Refreshing one row therefore
                // leaves the note permanently dirty and still displaying the stale value.
                .as("a key's rows are collected as a set, not reduced to a single row")
                .contains("extByKey.computeIfAbsent(")
                .doesNotContain("extByKey.putIfAbsent(")
                .as("every row a key already has is refreshed")
                .contains("for (CaseManagementNoteExt cme : rows)");

        int forStatement = block.indexOf("for (int i = 0; i < extNames.length; i++)");
        assertThat(forStatement).as("the block loops over the extension keys").isGreaterThan(0);

        // Bound the loop body by brace-matching rather than by position relative to the `for`
        // header alone: "after the header" would also be satisfied by an allocation sitting
        // past the loop's closing brace, which is not what this guard claims to check. The
        // block holds no string or character literal containing a brace, so a plain scan is
        // enough and does not need to model Java lexing.
        int bodyStart = block.indexOf('{', forStatement);
        assertThat(bodyStart).as("the loop body opens").isGreaterThan(forStatement);
        int depth = 0;
        int bodyEnd = -1;
        for (int i = bodyStart; i < block.length(); i++) {
            char c = block.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}' && --depth == 0) {
                bodyEnd = i;
                break;
            }
        }
        assertThat(bodyEnd).as("the loop body closes inside the extension save block").isGreaterThan(bodyStart);

        // Every entity the write loop creates is created inside it: a hoisted allocation is the
        // shape that collapsed the keys into one row.
        // EVERY allocation in the block, not just the first one indexOf happens to find. The
        // block allocates twice: the detached `resolved` carrier and, in the insert branch, the
        // `cme` that is actually persisted. Checking only the first would let the persisted one
        // be hoisted above the loop -- reinstating the row-collapse bug -- while the carrier
        // stayed inside and kept the assertion green. Requiring all of them holds however many
        // the block grows to, and needs no guess about which is which.
        List<Integer> allocations = new ArrayList<>();
        for (int at = block.indexOf(ALLOCATION); at >= 0; at = block.indexOf(ALLOCATION, at + 1)) {
            allocations.add(at);
        }
        assertThat(allocations)
                .as("the block allocates note extensions at all (guards against a vacuous pass)")
                .hasSizeGreaterThanOrEqualTo(2);
        // bodyEnd is assigned inside the brace-matching loop above, so it is not effectively
        // final; copy both bounds before the lambda reads them.
        final int loopOpens = bodyStart;
        final int loopCloses = bodyEnd;
        assertThat(allocations)
                .as("every note extension is allocated inside the loop body, so each key gets its own row")
                .allSatisfy(at -> assertThat(at).isBetween(loopOpens, loopCloses));
    }

    /**
     * Returns the whole chained SecRule carrying {@code id:<ruleId>}, from its
     * {@code SecRule REQUEST_URI} line to the blank line that separates it from the next rule.
     *
     * <p>Bounding the slice on the rule's own structure rather than on the text of whichever
     * clause happens to sit last keeps the assertions about what the rule <em>says</em>.
     * Anchoring on a particular argument name meant that reordering the clauses, or adding one
     * after it, silently truncated the slice and turned real assertions into vacuous ones. A
     * closing quote is no good either: the {@code chain} action's own line ends in one while
     * the chained rule continues below it.</p>
     */
    private String readExclusionRule(String ruleId) throws IOException {
        String exclusions = read(Path.of("debian", "assets", "modsecurity",
                "REQUEST-900-EXCLUSION-RULES-BEFORE-CRS.conf"))
                .replace("\r\n", "\n");

        int idPosition = exclusions.indexOf("id:" + ruleId + ",");
        assertThat(idPosition).as("exclusion %s is present", ruleId).isGreaterThanOrEqualTo(0);

        int ruleStart = exclusions.lastIndexOf("SecRule REQUEST_URI", idPosition);
        assertThat(ruleStart).as("exclusion %s opens with a REQUEST_URI match", ruleId).isGreaterThanOrEqualTo(0);

        int ruleEnd = exclusions.indexOf("\n\n", idPosition);
        if (ruleEnd < 0) {
            ruleEnd = exclusions.length();
        }
        String rule = exclusions.substring(ruleStart, ruleEnd);

        // A rule that lost its body would satisfy every doesNotContain() below for the wrong
        // reason, so require the slice to actually span the chained rule.
        assertThat(rule).as("exclusion %s slice spans its chained rule", ruleId).contains("SecRule REQUEST_METHOD");
        return rule;
    }

    private static String read(Path relativePath) throws IOException {
        return Files.readString(resolveProjectPath(relativePath), StandardCharsets.UTF_8);
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath().normalize();

        // Walk to the filesystem root. A launcher that starts inside the package
        // directory sits well below the repository root, so any fixed depth limit
        // turns a resolvable path into a class-initialization failure.
        while (current != null) {
            Path candidate = current.resolve(relativePath).normalize();
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }

        throw new IllegalStateException("Unable to resolve project path: " + relativePath);
    }
}
