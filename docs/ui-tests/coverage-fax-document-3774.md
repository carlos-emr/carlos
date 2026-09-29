# Fax and document coverage for issue #3774

This release-targeted slice exercises three changed production classes that had no covered changed lines in the clean release audit: `FaxRecipientSearch2Action`, `FaxDocument2Action`, and `FaxManagerImpl`.

The focused Java run passed 80 tests with no failures or skips. After deleting the previous JaCoCo execution file, the run covered 73/75 changed executable lines in the recipient search action, 68/71 in the document-to-fax action, and 17/33 in the fax manager. The comparison is against the issue's alpha12 `main` baseline (`d182f254cd`); it is a focused execution measurement, not whole-suite coverage.

The recipient tests verify method and privilege gates, short search terms, combined specialist/pharmacy output, missing surname and fax handling, the 20-item cap, and a failed directory lookup. The document tests verify method and privilege gates, unknown IDs, absent accounts, PDF type, stored-path containment, missing files, the prepared-fax handoff, and linked-patient authorization. Manager tests exercise the document link check and provider-scoped documents using a real temporary file.

Code review found that `FaxDocument2Action` checked file type, path, and missing-filename state before linked-patient access. That could reveal document details to someone without chart access. The action now authorizes the linked patient immediately after loading metadata, before those document details are tested. Regressions use linked non-PDF and missing-filename documents and confirm the caller receives `SecurityException` without a document-state message.

Reproduce the focused Java run with:

```bash
rm -f target/jacoco.exec
mvn -B -Dtest=FaxRecipientSearch2ActionUnitTest,FaxDocument2ActionUnitTest,FaxManagerImplUnitTest \
  test org.jacoco:jacoco-maven-plugin:0.8.14:report
```

The release audit script in PR #3781 can intersect the resulting `target/site/jacoco/jacoco.xml` with `d182f254cd` through this branch's head. A live installed-DEB fax handoff and recipient-picker browser run remains to be validated; this Java slice does not claim that result.
