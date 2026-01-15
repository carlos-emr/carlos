# OpenO EMR Documentation

## Overview

This directory contains technical documentation for the OpenO EMR (Electronic Medical Records) system. OpenO is a comprehensive healthcare management platform used across Canada with multi-jurisdictional compliance.

## Documentation Structure

### 📁 Core Documentation

| Category | Documents | Description |
|----------|-----------|-------------|
| **Testing** | [📂 test/](test/) | Comprehensive modern test framework documentation (JUnit 5) |
| **Architecture** | [Struts Actions Summary](struts-actions-summary.md)<br>[Struts Actions Detailed](struts-actions-detailed.md)<br>[Integrator System Architecture](integrator-system-architecture.md) | System architecture and action mappings |
| **Security** | [Password System](Password_System.md) | Authentication and security architecture |
| **APIs** | [API Collections Index](api-collections-index.md) | REST API documentation and collections |
| **Development** | [DS Guideline](dsGuideline.md)<br>[Copyright Header (Magenta)](copyright-header-magenta.md) | Development standards and guidelines |

### 🧪 Testing Documentation

The modern test framework documentation has its own dedicated directory with comprehensive guides:

**[📂 test/README.md](test/README.md)** - Start here for all testing documentation

Key testing resources:
- Modern Test Framework Guide (JUnit 5)
- Test Writing Best Practices
- Unit Testing Strategy
- BDD Naming Conventions
- Multi-file Test Architecture

### 🏥 Healthcare Modules

| Document | Description |
|----------|-------------|
| [MyDrugref](MyDrugref.md) | Drug reference system documentation |
| [Legacy HAPI Dynamic Loading](legacy-hapi-dynamic-loading.md) | HL7 HAPI integration patterns |
| [Form Resources README](README-form-resources.md) | Medical forms and resources |

### 🔧 Technical References

| Document | Description |
|----------|-------------|
| [Runtime Directories](runtime-directories.md) | Required filesystem directories and permissions |
| [Resources Directory](resources-directory.md) | Application resources structure |
| [GitHub Issue Management](github-issue-management.md) | Issue tracking and workflow |
| [Testing Exclusions](Testing_Exclusion_of_MCEDT_and_HinValidator_tests.md) | Excluded test documentation |

## Quick Links

### For Developers

1. **Writing Tests**: Start with [test/README.md](test/README.md)
2. **Struts Migration**: Review [Struts Actions Summary](struts-actions-summary.md)
3. **Security**: Understand [Password System](Password_System.md)
4. **APIs**: Check [API Collections Index](api-collections-index.md)

### For New Team Members

1. Read the main project context: `/workspace/CLAUDE.md`
2. Review development guidelines: [DS Guideline](dsGuideline.md)
3. Understand the test framework: [test/](test/)
4. Study the architecture: [Struts Actions](struts-actions-summary.md)

## Project Context

**OpenO EMR** is a Canadian healthcare EMR system with:
- Multi-jurisdictional compliance (BC, ON, generic)
- Java 21, Spring 5.3.39, Maven 3, Tomcat 9, MariaDB/MySQL
- HIPAA/PIPEDA compliance for PHI protection
- Comprehensive medical modules (billing, prescriptions, labs, etc.)

## Documentation Standards

When adding new documentation:

1. **Use Markdown**: All documentation should be in Markdown format
2. **Include Headers**: Start with clear copyright headers where applicable
3. **Be Specific**: Use concrete examples from the actual codebase
4. **Stay Current**: Update documentation when code changes
5. **Cross-Reference**: Link to related documentation

## Contributing to Documentation

### Adding New Documents

1. Place in appropriate category or create new subdirectory
2. Update this README with the new document
3. Include clear purpose and audience at document start
4. Use consistent formatting with existing docs

### Updating Existing Documents

1. Verify changes against current code
2. Update last modified dates where present
3. Ensure examples still compile and run
4. Update cross-references if needed

## Support

For questions about documentation:
1. Check the main project file: `/workspace/CLAUDE.md`
2. Review related documentation in this directory
3. Consult the test framework guides in [test/](test/)

---

*Last Updated: January 2025*