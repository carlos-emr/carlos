# GitHub Issue Management Guidelines

## Issue Templates

OpenO uses GitHub issue templates to standardize issue reporting and automatically set the issue Type field. Templates are located in `.github/ISSUE_TEMPLATE/`.

### Available Templates

#### Bug Report (`bug_report.yml`)
- **Type**: Bug
- **Title Prefix**: `[Bug]: `
- **Required Fields**: Description, Steps to Reproduce, Expected Result, Actual Result, Module/Area
- **Optional Fields**: Error Messages/Logs, Version/Branch
- **Auto Labels**: `type: bug`, `status: needs-triage`

#### Feature Request (`feature_request.yml`)
- **Type**: Feature
- **Title Prefix**: `[Feature]: `
- **Required Fields**: Problem, Solution, Module/Area, Priority
- **Optional Fields**: Alternatives, Additional Context
- **Auto Labels**: `type: feature`, `status: needs-triage`

#### Regression (`regression.yml`)
- **Type**: Regression
- **Title Prefix**: `[Regression]: `
- **Required Fields**: Last Working Version, Broken Version, Description, Current Behavior, Steps to Reproduce
- **Optional Fields**: Workaround
- **Auto Labels**: `type: regression`, `priority: high`, `status: needs-triage`

#### Performance Issue (`performance.yml`)
- **Type**: Performance
- **Title Prefix**: `[Performance]: `
- **Required Fields**: Description, Steps to Reproduce, Actual Performance, Module/Area, Environment
- **Optional Fields**: Expected Performance, Metrics/Logs
- **Auto Labels**: `type: performance`, `status: needs-triage`

#### Documentation (`documentation.yml`)
- **Type**: Documentation
- **Title Prefix**: `[Docs]: `
- **Required Fields**: Type, Location, Issue, Suggestion
- **Auto Labels**: `type: documentation`

#### Dependency Update (`dependency_update.yml`)
- **Type**: Dependency
- **Title Prefix**: `[Dependency]: `
- **Required Fields**: Dependency, Current Version, Target Version, Reason, Breaking Changes, Testing, Priority
- **Auto Labels**: `type: maintenance`, `dependencies`

## Label Categories

### Type Labels
Core categorization of issues (automatically set by templates):
- `type: bug` - Something isn't working as expected
- `type: feature` - New feature or capability
- `type: regression` - Something that used to work but is now broken
- `type: performance` - Performance related issue
- `type: documentation` - Documentation improvements
- `type: maintenance` - Code maintenance, refactoring, dependencies
- `type: security` - Security related issue

### Priority Labels
Urgency and importance classification:
- `priority: critical` - Must be fixed ASAP
- `priority: high` - Stalls work on the project or its dependents
- `priority: medium` - Not blocking but should be addressed soon
- `priority: low` - Nice to have but not urgent

### Status Labels
Current state of the issue:
- `status: needs-triage` - Needs to be triaged by maintainers
- `status: in-progress` - Currently being worked on
- `status: blocked` - Blocked by another issue
- `status: ready-for-review` - Ready for review

### Effort Labels
Estimated work required:
- `effort: low` - Less than 1 day of work
- `effort: medium` - 1-3 days of work
- `effort: high` - More than 3 days of work

### Special Labels
- `dependencies` - Pull requests that update a dependency file
- `blocker` - This is a blocking issue for merging code upstream
- `help wanted` - Extra attention is needed
- `good first issue` - Good for newcomers
- `bug` - Legacy label (use `type: bug` for new issues)
- `enhancement` - Legacy label (use `type: feature` for new issues)

## Issue Creation Best Practices

1. **Use Templates**: Always use the appropriate issue template when creating a new issue
2. **Clear Titles**: Keep titles concise and descriptive
3. **Steps to Reproduce**: For bugs, regressions, and performance issues, always provide clear reproduction steps
4. **Version Information**: Include branch or version information when relevant
5. **One Issue Per Problem**: Don't combine multiple unrelated problems in a single issue

## Issue Triage Process

1. New issues automatically receive `status: needs-triage`
2. Maintainers review and:
   - Verify the issue type is correct
   - Add priority label based on impact
   - Add effort label if estimation is possible
   - Remove `status: needs-triage` and add appropriate status
   - Add `help wanted` or `good first issue` if applicable

## Label Application Guidelines

- Every issue should have exactly one `type:` label
- Every triaged issue should have exactly one `priority:` label
- Status labels should reflect current state
- Effort labels help with sprint planning
- Special labels provide additional context
- Avoid over-labeling; use only labels that add value

## Template Configuration

Templates use YAML format with the following key fields:
- `name`: Template name shown in issue creation menu
- `description`: Brief description of when to use this template
- `title`: Default title prefix
- `labels`: Labels automatically applied
- `type`: Sets the GitHub issue Type field
- `body`: Form fields for issue content

## Maintenance

- Review templates quarterly for relevance
- Update label descriptions as needed
- Remove unused labels to reduce clutter
- Ensure templates align with development workflow