## Description
<!-- Provide a clear and concise description of your changes -->

## Related Issue
<!-- Link to the issue this PR addresses (e.g., Fixes #123) -->

## Type of Change
<!-- Check the relevant option(s) -->

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Documentation update
- [ ] Code refactoring
- [ ] Test improvement
- [ ] Dependency update

## Testing
<!-- Describe the tests you ran and how to reproduce them -->

- [ ] I have tested these changes locally
- [ ] I have added/updated tests for my changes
- [ ] All existing tests pass

## Security Checklist
<!-- Required for OpenO EMR - verify all security requirements are met -->

- [ ] All user inputs are validated and sanitized using OWASP Encoder
- [ ] All database queries use parameterized statements
- [ ] Security privilege checks are present (`SecurityInfoManager.hasPrivilege()`)
- [ ] PHI access is logged for audit trail
- [ ] No sensitive data in logs or error messages
- [ ] File operations use `PathValidationUtils` for path validation

## Code Quality
<!-- Ensure code quality standards are met -->

- [ ] My code follows the project's coding standards
- [ ] I have added JavaDoc comments for public classes and methods
- [ ] I have updated relevant documentation
- [ ] My changes generate no new warnings
- [ ] CodeQL security scan passes

## Additional Context
<!-- Add any other context, screenshots, or information about the PR -->

---

<details>
<summary>🤖 AI Tools Available</summary>

### Available AI Assistants for PR Review

OpenO EMR integrates several AI tools to assist with code review and development. You can invoke these tools by mentioning them in PR comments:

#### **Claude Code** (@claude)
- **Purpose**: Code implementation, refactoring, and complex problem-solving
- **Usage**: `@claude <your request>`
- **Best for**:
  - Implementing features or fixes
  - Refactoring code
  - Writing tests
  - Complex code analysis
- **Example**: `@claude please add tests for this new feature`

#### **CodeRabbit AI** (@coderabbitai)
- **Purpose**: Code review assistance (configured for minimal notifications)
- **Usage**: `@coderabbitai <command>`
- **Best for**:
  - On-demand code reviews
  - Security analysis
  - Code quality suggestions
- **Note**: Auto-review is disabled to reduce spam. Explicitly mention @coderabbitai when you need a review.
- **Example**: `@coderabbitai review this PR for security issues`

#### **GitHub Copilot**
- **Purpose**: Code completion and suggestions in IDE
- **Usage**: Available in your IDE with GitHub Copilot extension
- **Best for**:
  - Real-time code completion
  - Generating boilerplate code
  - Following project patterns
- **Note**: See `.github/copilot-instructions.md` for OpenO-specific instructions

### Best Practices
- Use Claude for implementation and complex analysis
- Use CodeRabbit only when you need focused review feedback
- All AI-generated code must pass security checks and tests
- Human review is still required for all PRs

</details>
