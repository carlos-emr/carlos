# Assess Migration from iText 5.x to Apache PDFBox

**Parent Issue**: #2139 (Category 2, Item 20)
**Previous Discussion**: #2161
**Priority**: 🟡 Medium
**Migration Effort**: High (2-3 months estimated)
**Impact**: PDF generation across application

## Background

OpenO currently uses `com.itextpdf:itextpdf:5.5.13.5` (AGPL license). While the legal implications of using AGPL libraries are nuanced, migrating to Apache PDFBox (Apache License 2.0) would eliminate license uncertainty that could affect adoption in risk-averse healthcare environments.

Apache PDFBox is a pure Java library maintained by the Apache Software Foundation for working with PDF documents. Unlike OpenPDF (which is a fork of iText 4.2.0), PDFBox has a completely different API architecture but offers comprehensive PDF creation and manipulation capabilities suitable for healthcare applications.

## Assessment Goals

This issue tracks the **assessment phase** of migrating from iText 5.x to Apache PDFBox, not the full migration itself.

### Phase 1: Usage Audit
- [ ] Identify all iText usage in codebase (search for `com.itextpdf` imports)
  - ✅ **Initial scan complete**: 47 Java files identified
- [ ] Document PDF generation features currently used
- [ ] Categorize usage by module (billing, forms, reports, etc.)
- [ ] Create inventory of iText APIs in use

**Current Usage Overview**:
- **Lab Reports**: Lab PDF creation, OLIS lab reports, lab upload handlers
- **Forms**: Custom PDF forms, eForm PDF generation, pharmacy forms (BPMH)
- **Consultations**: Consultation forms, request printing, image PDF creation, fax attachments
- **Document Management**: Document conversion, incoming documents, annotations
- **Clinical**: E-chart printing, case management notes, measurement printing
- **Administrative**: Prevention printing, envelopes, patient lists, printer management
- **Hospital Reports**: HRM PDF creation
- **Fax**: Cover page generation, PDF import

### Phase 2: Migration Complexity Analysis
- [ ] Review Apache PDFBox API documentation and architecture
- [ ] Compare iText 5.x patterns to PDFBox equivalents
- [ ] Identify API breaking changes and architectural differences
- [ ] Estimate effort per module/component
- [ ] Identify high-risk areas requiring extensive testing
- [ ] Assess font handling differences (iText vs PDFBox)
- [ ] Evaluate PDF/A compliance capabilities
- [ ] Review HTML-to-PDF conversion approaches (currently using xmlworker)

**Key Technical Considerations**:
- **API Architecture**: PDFBox uses a different paradigm than iText (page-based vs flow-based)
- **Font Handling**: PDFBox requires explicit font embedding with different APIs
- **Image Handling**: Different approach to image embedding and scaling
- **Table Generation**: No direct equivalent to iText's PdfPTable - requires custom implementation
- **HTML Conversion**: xmlworker dependency needs alternative solution
- **Encryption/Security**: Different API for PDF encryption and permissions
- **Form Fields**: Interactive PDF forms use different APIs
- **Memory Management**: PDFBox streaming vs iText's memory model

### Phase 3: Testing Strategy
- [ ] Define test coverage requirements for PDF generation
- [ ] Plan visual comparison testing approach (pixel-by-pixel or structural)
- [ ] Identify performance benchmarking needs
- [ ] Document acceptance criteria for migration
- [ ] Create test data sets covering all PDF types
- [ ] Plan regression testing for existing PDF workflows
- [ ] Define clinical validation requirements for healthcare forms

**Critical Testing Areas**:
- **Regulatory Compliance**: Ensure migrated PDFs meet healthcare documentation standards
- **Clinical Forms**: Rourke charts, BCAR forms, prescription forms must render identically
- **Lab Reports**: HL7 lab reports, OLIS integration PDFs
- **Billing Documents**: Province-specific billing forms (BC, ON)
- **Patient Safety**: Verify no data loss or corruption during PDF generation
- **Accessibility**: Ensure PDF/UA compliance where applicable

### Phase 4: Migration Plan
- [ ] Create detailed migration roadmap
- [ ] Break down work into implementable chunks
- [ ] Estimate timeline and resources
- [ ] Create follow-up implementation issues
- [ ] Define rollback strategy
- [ ] Plan phased migration approach (by module)

**Suggested Migration Order** (low-risk to high-risk):
1. Simple report generation (patient lists, envelopes)
2. Document conversion utilities
3. Prevention and measurement printing
4. Lab reports (extensive testing required)
5. Consultation and referral forms
6. Clinical forms (Rourke, BCAR - highest risk)
7. E-chart and case management printing
8. Interactive forms and annotations

## Apache PDFBox Details

**Recommended Replacement**:
```xml
<!-- Remove iText dependencies -->
<!--
<dependency>
    <groupId>com.itextpdf</groupId>
    <artifactId>itextpdf</artifactId>
    <version>5.5.13.5</version>
</dependency>
<dependency>
    <groupId>com.itextpdf.tool</groupId>
    <artifactId>xmlworker</artifactId>
    <version>5.5.13.5</version>
</dependency>
-->

<!-- Add Apache PDFBox -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.3</version>
</dependency>
<!-- Optional: Layout utilities for easier table/form generation -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox-layout</artifactId>
    <version>3.0.3</version>
</dependency>
<!-- Optional: For advanced graphics -->
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox-graphics2d</artifactId>
    <version>3.0.3</version>
</dependency>
```

**Key Facts**:
- **License**: Apache License 2.0 (permissive, healthcare-friendly, no copyleft)
- **Maintainer**: Apache Software Foundation (long-term stability)
- **Current Version**: 3.0.3 (January 2025)
- **Java Compatibility**: Java 8+ (3.x requires Java 8 minimum)
- **Status**: Actively maintained, regular releases
- **API Stability**: Mature API, major version (3.x) released 2023
- **Community**: Large Apache community, extensive documentation

**API Comparison**:

| Feature | iText 5.x | Apache PDFBox 3.x |
|---------|-----------|-------------------|
| Document Creation | `Document`, `PdfWriter` | `PDDocument`, `PDPage` |
| Content Stream | `PdfContentByte` | `PDPageContentStream` |
| Tables | `PdfPTable` | Custom or pdfbox-layout |
| Fonts | `BaseFont`, `Font` | `PDFont`, font embedding |
| Images | `Image.getInstance()` | `PDImageXObject.createFromFile()` |
| Paragraphs | `Paragraph`, `Phrase` | Manual positioning |
| HTML Conversion | `XMLWorkerHelper` | External library needed |
| Encryption | `PdfWriter.setEncryption()` | `AccessPermission`, `StandardProtectionPolicy` |

**Advantages of PDFBox**:
- ✅ Apache License 2.0 (no licensing concerns)
- ✅ Strong PDF reading/manipulation capabilities
- ✅ Well-documented, stable API
- ✅ Apache Foundation backing
- ✅ Good performance for large documents
- ✅ Extensive PDF/A support

**Challenges**:
- ⚠️ No direct high-level layout API (like iText's Document/PdfPTable)
- ⚠️ More verbose for complex layouts
- ⚠️ No built-in HTML-to-PDF conversion (need external library)
- ⚠️ Font handling requires more explicit code
- ⚠️ Steeper learning curve for developers familiar with iText

## Success Criteria

Assessment is complete when we have:
1. ✅ Complete inventory of iText usage (47 files identified)
2. ⏳ Detailed migration complexity estimate per module
3. ⏳ Comprehensive testing strategy with clinical validation
4. ⏳ Actionable migration plan with phased rollout timeline
5. ⏳ Risk assessment and mitigation strategies
6. ⏳ Estimated effort in developer-days per component

## Alternative Considerations

While Apache PDFBox is the recommended target, the assessment should also consider:

1. **OpenPDF** (original plan): LGPL license, closer API compatibility with iText 5.x, easier migration
2. **Apache FOP**: XML-based approach, good for templated documents
3. **Flying Saucer + PDFBox**: HTML/CSS to PDF rendering
4. **Hybrid Approach**: Use PDFBox for new features, migrate existing gradually

**Recommendation**: Proceed with PDFBox assessment as it provides:
- Best long-term licensing clarity (Apache 2.0)
- Strong institutional backing (Apache Foundation)
- Comprehensive PDF manipulation capabilities
- Suitable for healthcare compliance requirements

## Implementation Notes

**HTML Conversion Strategy**:
Since OpenO currently uses iText's `xmlworker` for HTML-to-PDF conversion, we'll need an alternative:

**Option 1**: Flying Saucer + PDFBox
```xml
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf-openpdf</artifactId>
    <version>9.11.0</version>
    <!-- May need PDFBox adapter -->
</dependency>
```

**Option 2**: OpenHTML to PDF
```xml
<dependency>
    <groupId>com.openhtmltopdf</groupId>
    <artifactId>openhtmltopdf-pdfbox</artifactId>
    <version>1.1.22</version>
</dependency>
```

**Option 3**: Direct PDFBox (manual HTML parsing)
- More control, more effort
- Best for simple HTML or template-based generation

**Font Management**:
PDFBox requires explicit font loading. Plan to:
- Identify all fonts used in current PDFs
- Embed fonts properly for healthcare forms
- Test font rendering across all document types
- Consider font licensing for embedded fonts

**Performance Considerations**:
- PDFBox uses streaming API for large documents
- Memory profiling needed for high-volume reports
- Benchmark against current iText performance
- Consider lazy loading for large form sets

## References

- Apache PDFBox Official: https://pdfbox.apache.org/
- PDFBox GitHub: https://github.com/apache/pdfbox
- PDFBox Maven: https://mvnrepository.com/artifact/org.apache.pdfbox/pdfbox
- Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0
- iText License History: https://itextpdf.com/en/blog/technical-notes/itext-5-license-change
- AGPL License Discussion: #2161
- PDFBox Layout Library: https://github.com/apache/pdfbox-layout
- OpenHTML to PDF: https://github.com/danfickle/openhtmltopdf

## Migration Resources

- **PDFBox Examples**: https://github.com/apache/pdfbox/tree/trunk/examples
- **API Documentation**: https://pdfbox.apache.org/docs/3.0.3/javadocs/
- **Migration Guide**: (to be created during Phase 4)
- **Healthcare PDF Standards**: Consult regulatory requirements for BC/ON

---

**Next Steps**:
1. Begin detailed usage audit (Phase 1 completion)
2. Create API mapping document (iText → PDFBox equivalents)
3. Develop proof-of-concept for high-risk components (clinical forms)
4. Estimate effort per module

**Related Issues**: #2135, #2136, #2137, #2139, #2161
