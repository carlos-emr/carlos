package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Interface defining the contract for electronic document converters.
 * Implementations of this interface are responsible for transforming various
 * document formats into standardized system representations (e.g., PDF).
 */
public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}