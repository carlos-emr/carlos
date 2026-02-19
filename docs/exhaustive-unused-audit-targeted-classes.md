# Exhaustive Usage Audit (Targeted Classes)

This audit checks whether the 11 requested classes are referenced anywhere in the repository, including code and framework-style configuration files (XML/properties/YAML/etc.).

## Scope and method

Searches were run from repo root with `rg` across all files except large generated/vendor directories (`.git`, `node_modules`, `docs/static`, `website/node_modules`, `.docusaurus`, `target`).

Primary command used:

```bash
rg -n "EnumNameComparator|HinValidator|SpringPropertyConfigurer|FileHolder|PagerDef|OscarDbPropertiesListener|LookupTagValue|OscarHibernateProperties|@Dangerous|Dangerous|MergedDemographicInterceptor|ContactManager|io\.github\.carlos_emr\.carlos\.(utility|util|commons|db|commn)\.(EnumNameComparator|HinValidator|SpringPropertyConfigurer|FileHolder|PagerDef|OscarDbPropertiesListener|LookupTagValue|OscarHibernateProperties|Dangerous|MergedDemographicInterceptor|ContactManager)" . --glob '!.git' --glob '!node_modules' --glob '!docs/static' --glob '!website/node_modules' --glob '!.docusaurus' --glob '!target'
```

A second pass searched each class name and FQCN independently and excluded self-file matches to detect external usage.

## Results

| Class | External runtime/config reference found? | Notes |
|---|---|---|
| `EnumNameComparator` | **No** | Only appears in docs reports; no Java/XML/properties/YAML references. |
| `HinValidator` | **No** | Only appears in docs plus `HinValidatorTest` class name and a Maven test exclusion entry; no use of `io.github.carlos_emr.carlos.utility.HinValidator`. |
| `SpringPropertyConfigurer` | **No** | No usage in source or framework config files. |
| `FileHolder` | **No** | No usages outside own source file and docs reports. |
| `PagerDef` | **No** | No usages outside own source file and docs reports. |
| `OscarDbPropertiesListener` | **No** | No web.xml/Spring/etc registration found. |
| `LookupTagValue` | **No** | No external references in source or config. |
| `OscarHibernateProperties` | **No** | No external references in source or config. |
| `Dangerous` | **No** | No `@Dangerous` annotation usages found. One unrelated test method contains the word “Dangerous” in method name only. |
| `MergedDemographicInterceptor` | **No** | No AOP/Spring XML or Java registration found. |
| `ContactManager` | **No** | No references in source/config found. |

## Conclusion

All 11 requested classes are **truly unused** with respect to repository code paths and framework configuration references.

The only matches found outside each class definition are documentation/reporting mentions, plus non-usage name collisions (e.g., test file names or method text containing the same word).
