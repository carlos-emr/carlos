// Add hasPrivilege check to getAllDrugs()
@Override
public List<Drug> getAllDrugs(@PathVariable("demographicNo") Long demographicNo) {
    if (!securityInfoManager.hasPrivilege(info, "_rx", "r", demographicNo)) {
        throw new AccessDeniedException("_rx", "r", demographicNo);
    }
    // existing code
}

// Add hasPrivilege check to getCurrentDrugs()
@Override
public List<Drug> getCurrentDrugs(@PathVariable("demographicNo") Long demographicNo) {
    if (!securityInfoManager.hasPrivilege(info, "_rx", "r", demographicNo)) {
        throw new AccessDeniedException("_rx", "r", demographicNo);
    }
    // existing code
}

// Add hasPrivilege check to getLongtermDrugs()
@Override
public List<Drug> getLongtermDrugs(@PathVariable("demographicNo") Long demographicNo) {
    if (!securityInfoManager.hasPrivilege(info, "_rx", "r", demographicNo)) {
        throw new AccessDeniedException("_rx", "r", demographicNo);
    }
    // existing code
}

// Add hasPrivilege check to getArchivedDrugs()
@Override
public List<Drug> getArchivedDrugs(@PathVariable("demographicNo") Long demographicNo) {
    if (!securityInfoManager.hasPrivilege(info, "_rx", "r", demographicNo)) {
        throw new AccessDeniedException("_rx", "r", demographicNo);
    }
    // existing code
}

// Add hasPrivilege check to represcribe()
@Override
public void represcribe(@PathVariable("drugId") Long drugId) {
    Long demographicNo = getDemographicNoFromDrugId(drugId);
    if (!securityInfoManager.hasPrivilege(info, "_rx", "w", demographicNo)) {
        throw new AccessDeniedException("_rx", "w", demographicNo);
    }
    // existing code
}

// Restore and uncomment hasPrivilege guard in recordPrescriptionPrint()
@Override
public void recordPrescriptionPrint(@PathVariable("demographicNo") Long demographicNo) {
    if (!securityInfoManager.hasPrivilege(info, "_rx", "r", demographicNo)) {
        throw new AccessDeniedException("_rx", "r", demographicNo);
    }
    // existing code
}

// Add hasPrivilege check to watermark()
@Override
public void watermark(@PathVariable("demographicNo") Long demographicNo, @PathVariable("rxNo") Long rxNo) {
    if (!securityInfoManager.hasPrivilege(info, "_rx", "r", demographicNo)) {
        throw new AccessDeniedException("_rx", "r", demographicNo);
    }
    // existing code
}

// Add hasPrivilege check to print()
@Override
public void print(@PathVariable("demographicNo") Long demographicNo, @PathVariable("rxNo") Long rxNo) {
    if (!securityInfoManager.hasPrivilege(info, "_rx", "r", demographicNo)) {
        throw new AccessDeniedException("_rx", "r", demographicNo);
    }
    // existing code
}

// Helper method to get demographicNo from drugId
private Long getDemographicNoFromDrugId(Long drugId) {
    // implement logic to get demographicNo from drugId
    return demographicNo;
}