package io.wisetime.wise_log_aws.cloud;

import com.amazonaws.util.IOUtils;

import org.json.JSONException;
import org.junit.jupiter.api.Test;
import org.skyscreamer.jsonassert.JSONAssert;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.LoggingEvent;

class LayoutEngineJsonTest {

  @Test
  void testDoLayout() throws JSONException {
    LayoutEngineJson layoutEngineJson = new LayoutEngineJson();
    Logger rootLogger = (Logger) LoggerFactory.getLogger("root");
    Level level = Level.INFO;
    String message = "This is an error message...";
    MDC.put("mdc-key1", "mdc-value1");
    MDC.put("mdc-key2", "mdc-value2");

    LoggingEvent event = new LoggingEvent(null, rootLogger, level, message, new Exception("Some exception"), null);
    layoutEngineJson.start();
    String output = layoutEngineJson.doLayout(event);
    JSONAssert.assertEquals(getFile("/testJsonLayout.json"), output, false);
    // strict mode false, because stacktrace is not predictable (depends on a test runner maven vs intellij)
  }

  private String getFile(String name) {
    try {
      try (InputStream stream = this.getClass().getResourceAsStream(name)) {
        return IOUtils.toString(stream);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
