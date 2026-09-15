package io.github.carlos_emr.carlos.utility;

/**
 * Exception thrown when PDF generation operations fail.
 * 
 * <p>This exception is used to indicate failures during PDF document generation,
 * such as:</p>
 * <ul>
 *   <li>Template processing errors</li>
 *   <li>Data formatting issues</li>
 *   <li>I/O errors during PDF writing</li>
 *   <li>Library-specific PDF generation failures</li>
 * </ul>
 * 
 * <p>Example usage:</p>
 * <pre>
 * try {
 *     generatePDF(data, outputStream);
 * } catch (PDFGenerationException e) {
 *     logger.error("Failed to generate PDF", e);
 *     // Handle error appropriately
 * }
 * </pre>
 */
public class PDFGenerationException extends Exception {

    private final boolean retryable;

    /**
     * Constructs a new PDF generation exception with no detail message.
     */
    public PDFGenerationException() {
        super();
        this.retryable = false;
    }

    /**
     * Constructs a new PDF generation exception with the specified detail message.
     *
     * @param message the detail message explaining the cause of the exception
     */
    public PDFGenerationException(String message) {
        super(message);
        this.retryable = false;
    }

    /**
     * Constructs a new PDF generation exception with the specified cause.
     *
     * @param cause the underlying cause of this exception
     */
    public PDFGenerationException(Throwable cause) {
        super(cause);
        this.retryable = false;
    }

    /**
     * Constructs a new PDF generation exception with the specified detail message and cause.
     *
     * @param message the detail message explaining the cause of the exception
     * @param cause the underlying cause of this exception
     */
    public PDFGenerationException(String message, Throwable cause) {
        super(message, cause);
        this.retryable = false;
    }

    /**
     * Constructs a new PDF generation exception carrying a structural retryability signal.
     *
     * <p>Some failures are transient by construction — the browser renderer is momentarily at its
     * concurrency cap, or its wait for a render slot was interrupted before a render ever started —
     * and a caller may reasonably retry them. Others (a broken page, bad configuration, a corrupt
     * render) will not succeed on retry. Callers that need to tell these apart should use
     * {@link #isRetryable()} rather than matching on {@link #getMessage()}: the message text is not
     * a stable contract and can be reworded (typo fix, wording change, localization) without
     * warning.
     *
     * @param message the detail message explaining the cause of the exception
     * @param retryable whether this specific failure is transient and may reasonably be retried
     */
    public PDFGenerationException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    /**
     * Returns whether this failure is transient and may reasonably be retried, as recorded by the
     * thrower at construction time. Defaults to {@code false} for every constructor that does not
     * take an explicit {@code retryable} argument.
     */
    public boolean isRetryable() {
        return retryable;
    }
}
