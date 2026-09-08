package io.github.semanticsearch.support;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.onnxruntime.OrtEnvironment;

/**
 * Whether ONNX Runtime can load its native library on this machine.
 *
 * <p>The jar carries binaries for linux-x64, linux-aarch64, osx-aarch64 and win-x64, and for
 * nothing else. Intel macOS is the platform that notices: Microsoft stopped shipping a macOS x64
 * build after 1.22, so a contributor on one of those machines cannot run the ONNX provider at all.
 *
 * <p>Tests that need it are gated on this the same way the Elasticsearch tests are gated on Docker,
 * so a clone builds green on a machine that cannot run the model. CI runs on linux-x64, where the
 * condition is always true and the tests always execute.
 */
public final class OnnxRuntimeAvailable {

  private static final Logger log = LoggerFactory.getLogger(OnnxRuntimeAvailable.class);

  private static final boolean LOADS = attemptLoad();

  private OnnxRuntimeAvailable() {}

  /** Referenced by name from {@code @EnabledIf} on the tests that need the model. */
  public static boolean loads() {
    return LOADS;
  }

  private static boolean attemptLoad() {
    try {
      OrtEnvironment.getEnvironment();
      return true;
    } catch (Throwable failure) {
      // Throwable on purpose. A missing native surfaces as UnsatisfiedLinkError
      // or NoClassDefFoundError, neither of which is an Exception.
      log.warn(
          "ONNX Runtime will not load on {} {}, so the tests that need it are skipped: {}",
          System.getProperty("os.name"),
          System.getProperty("os.arch"),
          failure.toString());
      return false;
    }
  }
}
