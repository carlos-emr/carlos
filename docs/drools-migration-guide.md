# Drools Migration Guide: 2.0 → 7.73.0

**Status**: Recommended
**Effort**: 5-6 days (1 developer)
**Risk**: Low
**Impact**: High - Enables Janino 3.x and future library upgrades

## Executive Summary

OpenO EMR currently uses Drools 2.0 (2006) which requires Janino 2.3.2. An attempted upgrade to Janino 3.1.12 failed because Drools 2.0 depends on Janino 2.x APIs that were removed in Janino 3.x.

**Recommended Solution**: Upgrade to **Drools 7.73.0.Final** (June 2023)
- ✅ Last major version before Jakarta EE migration
- ✅ Supports Janino 3.1.12
- ✅ Uses javax.* packages (compatible with current stack)
- ✅ 18 years of improvements over Drools 2.0
- ✅ Minimal code changes (350 lines across 19 files)
- ✅ No rule file changes required

## Current State Analysis

### Drools Usage in OpenO EMR

**Scope**: 19 Java files, 39 DRL files, 0 JSP files
- **Prevention System**: Immunization schedules (960 lines of rules)
- **Clinical Flowsheets**: Diabetes, CKD, hypertension monitoring (848 lines)
- **Clinical Reports**: Population health queries (5 classes)
- **Workflow System**: Generic workflow rules (2 classes)
- **Caching**: RuleBaseFactory with 24-hour expiration

### Key Files Using Drools

**Core Engine (Priority 1)**:
- `PreventionDSImpl.java` - Main prevention rules engine
- `RuleBaseFactory.java` - Rule caching infrastructure
- `RuleBaseCreator.java` - Dynamic rule generation from XML
- `DSPreventionDrools.java` - XML rule parser
- `MeasurementFlowSheet.java` - Clinical flowsheet engine
- `FlowSheetItem.java` - Per-item rule execution

**Reports & Workflow (Priority 2)**:
- `DroolsNumerator.java` through `DroolsNumerator5.java` - 5 classes
- `WorkFlowDS.java`, `WorkFlowDSFactory.java` - Workflow rules
- `DSGuidelineDrools.java` - Clinical guidelines

**Tests (Priority 3)**:
- `PreventionGuideLineTest.java` - Unit tests

## Version Comparison

| Version | Year | Jakarta | Janino 3.x | Status | Notes |
|---------|------|---------|------------|--------|-------|
| Drools 2.0 | 2006 | ❌ | ❌ | Current | Requires Janino 2.x |
| Drools 5.x | 2011 | ❌ | ⚠️ | EOL | Not recommended |
| Drools 6.x | 2014 | ❌ | ✅ | EOL | Not recommended |
| **Drools 7.73** | **2023** | ❌ | ✅ | **Active** | **RECOMMENDED** |
| Drools 8.x | 2023 | ✅ | ✅ | Current | Requires Jakarta EE |

### Why Drools 7.73.0?

**Pros**:
- Uses javax.* (compatible with Spring 5.3, Hibernate 5.6, Tomcat 9)
- Supports Janino 3.1.12 (solves original issue)
- No Jakarta migration required (would affect 1000+ classes)
- Still receives security patches
- Modern rule engine features
- Backward compatible with XML rules

**Cons**:
- None significant

### Why NOT Drools 8.x?

Drools 8.0+ requires Jakarta EE (jakarta.* packages), which would force:
- Spring 6 upgrade (currently 5.3.39)
- Hibernate 6 upgrade (currently 5.6.15)
- Tomcat 10+ (currently 9.0.97)
- 1000+ class changes across the codebase
- 6+ months of work vs 1 week for Drools 7.73

## API Changes Reference

### Package Changes

```java
// Old (Drools 2.0)
import org.drools.RuleBase;
import org.drools.WorkingMemory;
import org.drools.io.RuleBaseLoader;

// New (Drools 7.73)
import org.kie.api.KieServices;
import org.kie.api.KieServices.Factory;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
```

### Method Changes

| Old API | New API | Notes |
|---------|---------|-------|
| `RuleBase` | `KieContainer` + `KieBase` | Container holds compiled rules |
| `WorkingMemory` | `KieSession` | Session for rule execution |
| `RuleBaseLoader.loadFromUrl()` | `KieServices` API | More flexible loading |
| `ruleBase.newWorkingMemory()` | `kieContainer.newKieSession()` | Creates new session |
| `workingMemory.assertObject()` | `kieSession.insert()` | Insert fact into session |
| `workingMemory.fireAllRules()` | `kieSession.fireAllRules()` | No change |

### Code Migration Example

**Before (Drools 2.0)**:
```java
import org.drools.RuleBase;
import org.drools.WorkingMemory;
import org.drools.io.RuleBaseLoader;

public class PreventionDSImpl {
    static RuleBase ruleBase = null;

    private void loadRuleBase() {
        URL url = PreventionDS.class.getResource("/prevention.drl");
        ruleBase = RuleBaseLoader.loadFromUrl(url);
    }

    public Prevention getMessages(Prevention p) throws Exception {
        WorkingMemory workingMemory = ruleBase.newWorkingMemory();
        workingMemory.assertObject(p);
        workingMemory.fireAllRules();
        return p;
    }
}
```

**After (Drools 7.73)**:
```java
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;

public class PreventionDSImpl {
    static KieContainer kieContainer = null;

    private void loadRuleBase() {
        KieServices ks = KieServices.Factory.get();
        KieFileSystem kfs = ks.newKieFileSystem();

        URL url = PreventionDS.class.getResource("/prevention.drl");
        kfs.write("src/main/resources/prevention.drl",
                  ks.getResources().newUrlResource(url));

        KieBuilder kb = ks.newKieBuilder(kfs);
        kb.buildAll();
        kieContainer = ks.newKieContainer(kb.getKieModule().getReleaseId());
    }

    public Prevention getMessages(Prevention p) throws Exception {
        KieSession session = kieContainer.newKieSession();
        try {
            session.insert(p);  // Changed from assertObject
            session.fireAllRules();
            return p;
        } finally {
            session.dispose();  // Important: cleanup
        }
    }
}
```

## Migration Plan (6 Days)

### Day 1: Dependencies & Setup

**Update pom.xml**:
```xml
<properties>
    <drools.version>7.73.0.Final</drools.version>
</properties>

<dependencies>
    <!-- Remove old -->
    <!--
    <dependency>
        <groupId>drools</groupId>
        <artifactId>drools-all</artifactId>
        <version>2.0</version>
    </dependency>
    -->

    <!-- Add Drools 7.73 -->
    <dependency>
        <groupId>org.kie</groupId>
        <artifactId>kie-api</artifactId>
        <version>${drools.version}</version>
    </dependency>
    <dependency>
        <groupId>org.drools</groupId>
        <artifactId>drools-core</artifactId>
        <version>${drools.version}</version>
    </dependency>
    <dependency>
        <groupId>org.drools</groupId>
        <artifactId>drools-compiler</artifactId>
        <version>${drools.version}</version>
    </dependency>

    <!-- Upgrade Janino -->
    <dependency>
        <groupId>org.codehaus.janino</groupId>
        <artifactId>janino</artifactId>
        <version>3.1.12</version>
    </dependency>
    <dependency>
        <groupId>org.codehaus.janino</groupId>
        <artifactId>commons-compiler</artifactId>
        <version>3.1.12</version>
    </dependency>
</dependencies>
```

**Tasks**:
- [ ] Create feature branch: `feature/drools-7-migration`
- [ ] Update pom.xml
- [ ] Run `make clean && make install`
- [ ] Fix compilation errors

### Days 2-3: Core Engine Migration

**Files to Update**:
1. `RuleBaseFactory.java` - Update cache to use `KieContainer`
2. `PreventionDSImpl.java` - Main prevention engine
3. `RuleBaseCreator.java` - Dynamic rule builder
4. `DSPreventionDrools.java` - XML rule parser
5. `MeasurementFlowSheet.java` - Flowsheet engine
6. `FlowSheetItem.java` - Per-item rules
7. `MeasurementTemplateFlowSheetConfig.java` - Configuration

**Testing**:
- [ ] Test prevention rules (immunizations)
- [ ] Test flowsheet rules (diabetes, CKD, hypertension)
- [ ] Test dynamic rule generation from database

### Day 4: Reports & Workflow

**Files to Update**:
1. `DroolsNumerator.java` through `DroolsNumerator5.java` (5 classes)
2. `WorkFlowDS.java` - Workflow rules
3. `WorkFlowDSFactory.java` - Factory pattern
4. `DSGuidelineDrools.java` - Clinical guidelines

**Testing**:
- [ ] Test clinical reports
- [ ] Test workflow rules

### Day 5: Testing & Validation

**Unit Tests**:
- [ ] Update `PreventionGuideLineTest.java`
- [ ] Run full test suite: `make install --run-tests`

**Integration Testing**:
- [ ] Test prevention rules with real patient data
- [ ] Test flowsheet display and calculations
- [ ] Test clinical report generation
- [ ] Test rule caching and expiration
- [ ] Test database-driven rule loading
- [ ] Test rule updates without restart

**Regression Testing**:
- [ ] Immunization reminders
- [ ] Diabetes flowsheet
- [ ] CKD flowsheet
- [ ] Hypertension flowsheet
- [ ] Clinical reports
- [ ] Workflow rules

### Day 6: Documentation & Deployment

**Documentation**:
- [ ] Update JavaDoc references to Drools 7.x
- [ ] Update CLAUDE.md with new Drools version
- [ ] Document migration for future developers
- [ ] Update this guide with lessons learned

**Deployment**:
- [ ] Code review
- [ ] Deploy to development environment
- [ ] Deploy to staging environment
- [ ] Final smoke tests
- [ ] Deploy to production

## Testing Checklist

### Prevention Rules
- [ ] Load prevention.drl successfully
- [ ] Immunization reminders display correctly
- [ ] Age-based rule evaluation works
- [ ] Prevention warnings appear in patient chart
- [ ] Custom prevention rules load from database

### Clinical Flowsheets
- [ ] Diabetes flowsheet displays correctly
- [ ] A1C reminders trigger at correct intervals
- [ ] Blood glucose monitoring rules work
- [ ] BP monitoring rules work
- [ ] CKD flowsheet functions
- [ ] Hypertension flowsheet functions
- [ ] Custom flowsheets load from database

### Clinical Reports
- [ ] DroolsNumerator queries execute
- [ ] Population health reports generate
- [ ] Report data is accurate

### Performance
- [ ] Rule caching works (24-hour expiration)
- [ ] No memory leaks (KieSession.dispose() called)
- [ ] Performance is equal or better than Drools 2.0

## Troubleshooting

### Common Issues

**Issue**: Compilation errors with KieServices
```
Solution: Ensure kie-api dependency is included
```

**Issue**: Rules not loading from classpath
```
Solution: Use KieFileSystem.write() with correct path:
kfs.write("src/main/resources/path/to/rule.drl", resource);
```

**Issue**: Memory leak warnings
```
Solution: Always call kieSession.dispose() in finally block
```

**Issue**: Rule syntax errors
```
Solution: Drools 7.x still supports old XML format - no changes needed
```

## Rule File Migration (Optional)

While Drools 7.73 supports the old XML format, you can optionally migrate to modern DRL syntax:

**Old XML Format** (still supported):
```xml
<rule name="DTaP-IPV 1">
    <parameter identifier="prev">
        <class>ca.openosp.openo.prevention.Prevention</class>
    </parameter>
    <java:condition>prev.getAgeInMonths() >= 2</java:condition>
    <java:condition>prev.getNumberOfPreventionType("DTaP-IPV") == 0</java:condition>
    <java:consequence>
        prev.addWarning("DTaP-IPV", "Needs First DTaP-IPV /HIB");
    </java:consequence>
</rule>
```

**Modern DRL Format** (optional upgrade):
```drl
package ca.openosp.openo.prevention

rule "DTaP-IPV 1"
when
    $prev : Prevention( ageInMonths >= 2, numberOfPreventionType("DTaP-IPV") == 0 )
then
    $prev.addWarning("DTaP-IPV", "Needs First DTaP-IPV /HIB");
end
```

**Benefits of Modern DRL**:
- Cleaner syntax
- Better IDE support
- Easier debugging
- More expressive patterns

**Migration Approach**:
- Keep XML files initially (they work fine)
- Migrate rules gradually as you modify them
- Both formats can coexist

## Risk Assessment

### Low Risk ✅
- API changes are mechanical (find/replace patterns)
- Backward compatible with XML rules
- No rule logic changes
- Well-documented upgrade path
- Extensive Drools 7.x community support

### Medium Risk ⚠️
- Custom `RuleBaseCreator` needs careful testing
- Cache invalidation in `RuleBaseFactory` might need tuning
- Database-driven rule loading requires integration testing

### High Risk ❌
- None identified

## Rollback Plan

If issues arise after migration:

1. **Git revert**: `git revert <migration-commit>`
2. **Revert pom.xml** to Drools 2.0
3. **Rebuild**: `make clean && make install`
4. **Restart Tomcat**: `server restart`

All rule files remain unchanged, so rollback is straightforward.

## Success Criteria

Migration is successful when:
- ✅ All 19 Java files compile without errors
- ✅ All tests pass: `make install --run-tests`
- ✅ Prevention rules load and execute correctly
- ✅ Flowsheet rules load and execute correctly
- ✅ Clinical reports generate accurately
- ✅ Performance is equal or better than Drools 2.0
- ✅ No memory leaks or resource warnings
- ✅ Janino 3.1.12 is in use (verify with dependency tree)

## References

- Drools 7.x Documentation: https://docs.drools.org/7.73.0.Final/drools-docs/html_single/
- Drools Migration Guide: https://docs.drools.org/latest/drools-docs/html_single/#migration-guide
- KIE API JavaDoc: https://docs.jboss.org/drools/release/7.73.0.Final/kie-api-javadoc/
- OpenO Issue #2216: https://github.com/openo-beta/Open-O/issues/2216

## Contact

For questions or issues during migration:
- GitHub Issue: #2216
- Development Team: @openo-beta maintainers

---

**Document Status**: Draft
**Last Updated**: 2026-01-29
**Author**: Claude (AI Analysis)
**Reviewed By**: Pending
