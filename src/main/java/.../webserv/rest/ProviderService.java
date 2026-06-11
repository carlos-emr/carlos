@POST
@Path("/settings/{providerNo}/save")
public RestResponse<String> saveProviderSettings(ProviderSettings json,
        @PathParam("providerNo") String providerNo) {
    LoggedInInfo info = getLoggedInInfo();
    if (!info.getLoggedInProvider().getProviderNo().equals(providerNo)
            && !securityInfoManager.hasPrivilege(info, "_admin", "w", null)) {
        throw new AccessDeniedException("_admin", "w", null);
    }
    MiscUtils.getLogger().warn(json.toString());           // also logs settings object
    providerManager.updateProviderSettings(getLoggedInInfo(), providerNo, json);
    return RestResponse.successResponse(null);
}