package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;
/**
 * Interface defining operations for electronic document conversion.
 * <p>
 * Provides a contract for implementing custom document converters used across
 * the CARLOS EMR platform to process various incoming data formats.
 * </p>
 */


public interface EDocConverterInterface {
    // Defines the contract for document conversion to abstract format-specific parsing logic.
  void convert(String html, OutputStream os) throws Exception;
}