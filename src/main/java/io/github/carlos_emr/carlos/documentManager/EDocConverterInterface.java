package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;
/**
 * Provides data conversion utilities for EDocConverterInterface objects, handling translation between database, API, and internal representations.
 *
 * <p>This class implements domain-specific functionality to support the CARLOS EMR platform,
 * ensuring backwards compatibility with legacy integrations and adherence to healthcare standards.</p>
 */

public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}