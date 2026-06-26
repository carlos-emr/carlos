package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Defines the interface for converting electronic documents across various formats.
 */
public interface EDocConverterInterface {
    // Implementations must handle specific conversion strategies

  void convert(String html, OutputStream os) throws Exception;
}