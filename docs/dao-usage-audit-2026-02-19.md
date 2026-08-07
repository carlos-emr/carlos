# DAO usage audit (requested list)

Search scope: all repository files via `rg`, excluding `.git`, `docs/**`, and `**/target/**` to avoid generated docs/build artifacts.

A DAO was marked **UNUSED** only when both the implementation class name and implemented interface name had zero non-self references.

| DAO Impl | Interface | Result | Notes |
|---|---|---|---|
| `BatchBillingDaoImpl` | `BatchBillingDAO` | **USED** | non-self refs: impl=0, iface=8; sample: `./src/main/java/io/github/carlos_emr/carlos/billing/CA/ON/web/BatchBill2Action.java:43:import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;` ; `./src/main/java/io/github/carlos_emr/carlos/billing/CA/ON/web/BatchBill2Action.java:139:        BatchBillingDAO batchBillingDAO = (BatchBillingDAO) SpringUtils.getBean(BatchBillingDAO.class);` |
| `CSSStylesDaoImpl` | `CSSStylesDAO` | **USED** | non-self refs: impl=0, iface=9; sample: `./src/main/java/io/github/carlos_emr/carlos/billing/CA/ON/web/ManageCSS2Action.java:36:import io.github.carlos_emr.carlos.commn.dao.CSSStylesDAO;` ; `./src/main/java/io/github/carlos_emr/carlos/billing/CA/ON/web/ManageCSS2Action.java:51:    private CSSStylesDAO cssStylesDao = (CSSStylesDAO) SpringUtils.getBean(CSSStylesDAO.class);` |
| `CaisiFormDaoImpl` | `CaisiFormDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `CaisiFormDataDaoImpl` | `CaisiFormDataDao` | **USED** | non-self refs: impl=0, iface=2; sample: `./src/test/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDataDaoTest.java:39:import io.github.carlos_emr.carlos.commn.dao.CaisiFormDataDao;` ; `./src/test/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormDataDaoTest.java:45:    public CaisiFormDataDao dao = SpringUtils.getBean(CaisiFormDataDao.class);` |
| `CaisiFormDataTmpSaveDaoImpl` | `CaisiFormDataTmpSaveDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `CaisiFormInstanceDaoImpl` | `CaisiFormInstanceDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `CaisiFormInstanceTmpSaveDaoImpl` | `CaisiFormInstanceTmpSaveDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `CaisiFormQuestionDaoImpl` | `CaisiFormQuestionDao` | **USED** | non-self refs: impl=0, iface=2; sample: `./src/test/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormQuestionDaoTest.java:39:import io.github.carlos_emr.carlos.commn.dao.CaisiFormQuestionDao;` ; `./src/test/java/io/github/carlos_emr/carlos/commn/dao/CaisiFormQuestionDaoTest.java:45:    public CaisiFormQuestionDao dao = SpringUtils.getBean(CaisiFormQuestionDao.class);` |
| `ConsultationRequestMergedDemographicDaoImpl` | `ConsultationRequestMergedDemographicDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `DocumentResultsMergedDemographicDaoImpl` | `DocumentResultsMergedDemographicDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `DrugMergedDemographicDaoImpl` | `DrugMergedDemographicDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `DxAssociationDaoImpl` | `DxAssociationDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `GroupNoteLinkDaoImpl` | `GroupNoteLinkDao` | **USED** | non-self refs: impl=0, iface=2; sample: `./src/test/java/io/github/carlos_emr/carlos/commn/dao/GroupNoteLinkDaoTest.java:33:import io.github.carlos_emr.carlos.commn.dao.GroupNoteLinkDao;` ; `./src/test/java/io/github/carlos_emr/carlos/commn/dao/GroupNoteLinkDaoTest.java:38:    protected GroupNoteLinkDao dao = SpringUtils.getBean(GroupNoteLinkDao.class);` |
| `IntegratorConsentComplexExitInterviewDaoImpl` | n/a | MISSING | File not found at expected path. |
| `MdsZCLDaoImpl` | `MdsZCLDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `MdsZCTDaoImpl` | `MdsZCTDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `MdsZFRDaoImpl` | `MdsZFRDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `OscarAnnotationDaoImpl` | `OscarAnnotationDao` | **USED** | non-self refs: impl=0, iface=2; sample: `./src/test/java/io/github/carlos_emr/carlos/commn/dao/OscarAnnotationDaoTest.java:33:import io.github.carlos_emr.carlos.commn.dao.OscarAnnotationDao;` ; `./src/test/java/io/github/carlos_emr/carlos/commn/dao/OscarAnnotationDaoTest.java:38:    protected OscarAnnotationDao dao = SpringUtils.getBean(OscarAnnotationDao.class);` |
| `OscarCodeDaoImpl` | `OscarCodeDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `OscarMsgTypeDaoImpl` | `OscarMsgTypeDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `PrescribeDaoImpl` | `PrescribeDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `PreventionMergedDemographicDaoImpl` | `PreventionMergedDemographicDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `ProgramAccessRolesDaoImpl` | `ProgramAccessRolesDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `ReadLabDaoImpl` | `ReadLabDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `RecycleBinBillingDaoImpl` | `RecycleBinBillingDao` | **UNUSED** | non-self refs: impl=0, iface=0 |
| `RemoteDataLogDaoImpl` | `RemoteDataLogDao` | **USED** | non-self refs: impl=0, iface=2; sample: `./src/test/java/io/github/carlos_emr/carlos/commn/dao/RemoteDataLogDaoTest.java:35:import io.github.carlos_emr.carlos.commn.dao.RemoteDataLogDao;` ; `./src/test/java/io/github/carlos_emr/carlos/commn/dao/RemoteDataLogDaoTest.java:40:    protected RemoteDataLogDao dao = SpringUtils.getBean(RemoteDataLogDao.class);` |

## Completely unused DAOs from requested list

- `CaisiFormDaoImpl`
- `CaisiFormDataTmpSaveDaoImpl`
- `CaisiFormInstanceDaoImpl`
- `CaisiFormInstanceTmpSaveDaoImpl`
- `ConsultationRequestMergedDemographicDaoImpl`
- `DocumentResultsMergedDemographicDaoImpl`
- `DrugMergedDemographicDaoImpl`
- `DxAssociationDaoImpl`
- `MdsZCLDaoImpl`
- `MdsZCTDaoImpl`
- `MdsZFRDaoImpl`
- `OscarCodeDaoImpl`
- `OscarMsgTypeDaoImpl`
- `PrescribeDaoImpl`
- `PreventionMergedDemographicDaoImpl`
- `ProgramAccessRolesDaoImpl`
- `ReadLabDaoImpl`
- `RecycleBinBillingDaoImpl`

## Not assessed due to missing file

- `IntegratorConsentComplexExitInterviewDaoImpl`
