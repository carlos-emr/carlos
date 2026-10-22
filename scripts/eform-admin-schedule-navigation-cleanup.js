/**
 * Whether a delete response confirms that the temporary eForm should be absent.
 *
 * A 405 is tolerated as best-effort cleanup, but does not prove the deletion ran.
 *
 * @param {number} status HTTP response status from the cleanup request.
 * @returns {boolean} Whether the eForm row should be verified as removed.
 */
function shouldVerifyDeletedEformRow(status) {
  return status < 400;
}

module.exports = { shouldVerifyDeletedEformRow };
