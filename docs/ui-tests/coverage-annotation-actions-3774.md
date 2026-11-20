# Annotation action coverage for issue #3774

This release-targeted slice executes the changed `AnnotateDocument2Action` and `DocumentTextBoxes2Action` code that had zero covered changed lines in the release audit. The focused tests pass 22/22. A clean JaCoCo run covers 48/51 changed executable lines in the viewer gate and 68/76 in the word-box endpoint against the alpha12 `main` baseline (`d182f254cd`).

The viewer gate tests cover write privilege, invalid and absent IDs, missing documents, PDF type, linked-patient authorization, page count and size limits, unreadable source, and a successful provider-scoped document. The word-box tests cover GET/HEAD/POST behavior, read privilege, invalid page, missing document, linked-patient authorization, image-only and text-layer PDFs, and malformed PDF fallback. The text-layer test uses a real PDF and confirms the JSON contains only box geometry, not the fixture text.

Code review found two behavior defects. The viewer gate now checks linked-patient access before exposing file type or missing-filename state; the word-box endpoint applies the same ordering. The word-box endpoint also returns headers without a body or PDF extraction for HEAD requests. These changes have regression tests.

Reproduce with:

```bash
rm -f target/jacoco.exec
mvn -B -Dtest=AnnotateDocument2ActionUnitTest,DocumentTextBoxes2ActionUnitTest \
  test org.jacoco:jacoco-maven-plugin:0.8.14:report
```

The release audit script in PR #3781 can intersect `target/site/jacoco/jacoco.xml` with `d182f254cd` through this branch's head. Installed-DEB Playwright validation of the annotation viewer remains a separate browser coverage step; this Java slice does not claim that result.
