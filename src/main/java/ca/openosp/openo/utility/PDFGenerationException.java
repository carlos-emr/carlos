//CHECKSTYLE:OFF
package ca.openosp.openo.utility;

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
    
    /**
     * Constructs a new PDF generation exception with no detail message.
     */
    public PDFGenerationException() {
        super();
    }

    /**
     * Constructs a new PDF generation exception with the specified detail message.
     * 
     * @param message the detail message explaining the cause of the exception
     */
    public PDFGenerationException(String message) {
        super(message);
    }

    /**
     * Constructs a new PDF generation exception with the specified cause.
     * 
     * @param cause the underlying cause of this exception
     */
    public PDFGenerationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new PDF generation exception with the specified detail message and cause.
     * 
     * @param message the detail message explaining the cause of the exception
     * @param cause the underlying cause of this exception
     */
    public PDFGenerationException(String message, Throwable cause) {
        super(message, cause);
    }
}
