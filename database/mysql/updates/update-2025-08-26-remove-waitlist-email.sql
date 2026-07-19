-- Update script to remove waitlist email notification functionality
-- Date: 2025-08-26
-- Description: Complete removal of waitlist email notification system
-- 
-- CAUTION: This removal affects deprecated/non-functional code only
-- All related methods were already throwing UnsupportedOperationException
--

-- Note: The following related functionality has been removed from the codebase:
-- - org.oscarehr.PMmodule.notification.EmailTriggerServlet (Servlet class)
-- - org.oscarehr.threads.WaitListEmailThread (Background thread)
-- - org.oscarehr.managers.WaitListManager.sendProxyEformNotification() (Service method)
-- - /ProxyEformNotification servlet mapping from www.xml
-- - WaitListEmailThread references from ContextStartupListener
-- - LoginFilter sec exemptions for /ProxyEformNotification

-- Configuration properties removed:
-- - enable_wait_list_email_notifications
-- - wait_list_email_notification_period  
-- - wait_list_email_notification_program_ids

-- No database tables were affected by this removal as the functionality
-- was purely application-level email notification handling.

-- Benefits:
-- - Removed ~200 lines of dead code
-- - Eliminated background thread that only threw exceptions
-- - Removed unused servlet endpoint
-- - Cleaned up sec filter configurations
-- - Reduced application startup complexity

-- To re-enable similar functionality in the future:
-- 1. Restore all deleted Java classes
-- 2. Re-add servlet configuration in www.xml
-- 3. Restore sec exemptions in LoginFilter
-- 4. Re-add configuration properties
-- 5. Implement working email notification logic (replacing UnsupportedOperationException)