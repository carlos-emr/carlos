1. Refactor `Pattern.compile` calls in `DSValue.java` to `private static final Pattern` fields.
2. Update the regex patterns to use negated character classes and remove redundant capturing groups, conforming to Bolt's memory about ReDoS and SonarCloud warnings.
3. Test compilation and run targeted tests using `mvn test -Dtest=DSGuidelineDroolsUnitTest` to ensure changes don't cause regressions.
4. Complete pre-commit steps to ensure proper testing, verification, review, and reflection are done.
5. Submit the changes.
