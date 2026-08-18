-- carlosdoc is the documented local-development administrator. Remove the
-- provider-specific denial that overrides its active admin role while keeping
-- the group-creation security object admin-only for every other account.
DELETE FROM secObjPrivilege
WHERE roleUserGroup = '999998'
  AND objectName = '_admin.schedule.groupCreate'
  AND privilege = 'o'
  AND provider_no = '999998';
