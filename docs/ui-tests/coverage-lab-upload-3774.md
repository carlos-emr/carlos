# Lab upload coverage for issue #3774

This release-targeted slice executes four changed production paths that had zero covered changed lines in the release audit: the shared upload writer, inside-lab batch action, BC PathNet uploader, and ON CML uploader.

The focused Java run now passes 17 tests with no failures or skips. The line counts below were measured on the original 16-test run: after deleting the previous JaCoCo execution file, it covered 36/54 changed executable lines in `Utilities`, 6/13 in `InsideLabUpload2Action`, 13/27 in BC `LabUpload2Action`, and 10/30 in ON CML `LabUpload2Action`. The comparison is against alpha12 `main` (`d182f254cd`) and measures focused execution, not whole-suite coverage.

The tests use real temporary document directories. They verify upload content, generated names, stream closure, partial-file cleanup, HL7 message splitting, access and key gates, duplicate handling, and archive contents. PathNet and CML parsing/storage collaborators are mocked at their external boundaries, while the action's stream and file-writing paths execute.

Code review found that BC PathNet called `reset()` on a `Files.newInputStream` after `FileUploadCheck.addFile` consumed it. That stream does not support reset, so a successful duplicate check failed before parsing or archiving. The action now reads the validated upload once into a snapshot and gives the duplicate check, parser, and archive writer their own stream over it, so all three see the same bytes even if the temporary file changes. A regression consumes the complete first stream and rewrites the temporary file, then verifies the parser and archive still receive the original, hashed content and that the file stream is closed. The `reset()` also ran before the duplicate branch, so a rejected duplicate reported `exception` instead of `uploadedPreviously`; a second PathNet test covers that path and checks nothing is parsed or archived. Both tests fail against the pre-fix action.

Reproduce with:

```bash
rm -f target/jacoco.exec
mvn -B -Dtest=UtilitiesUploadUnitTest,InsideLabUpload2ActionUnitTest,io.github.carlos_emr.carlos.lab.ca.bc.PathNet.pageUtil.LabUpload2ActionUnitTest,io.github.carlos_emr.carlos.lab.ca.on.CML.Upload.LabUpload2ActionUnitTest \
  test org.jacoco:jacoco-maven-plugin:0.8.14:report
```

The release audit script in PR #3781 can intersect `target/site/jacoco/jacoco.xml` with `d182f254cd` through this branch's head. Installed-DEB lab upload Playwright validation remains a separate browser coverage step; this Java slice does not claim it.
