package io.github.carlos_emr.carlos.fax.provider;

/**
 * Domain exception for provider transport failures.
 *
 * <p>This exception keeps provider-specific failures from leaking low-level transport details
 * directly into the fax orchestration flow.</p>
 */
public class FaxProviderException extends Exception {

    /**
     * Creates a provider exception with message only.
     */
    public FaxProviderException(String message) {
        super(message);
    }

    /**
     * Creates a provider exception with message and original cause.
     */
    public FaxProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
