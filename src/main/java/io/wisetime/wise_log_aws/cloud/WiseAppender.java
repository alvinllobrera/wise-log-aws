/*
 * Copyright (c) 2018 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.wise_log_aws.cloud;

import com.amazonaws.services.logs.model.InputLogEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

/**
 * Appender used at wise-sites for critical level logging for alerts & monitoring services.
 */
public class WiseAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

  /**
   * Environment variable to enable WiseAppender
   */
  private static final String CLOUD_LOG_ENABLED_ENV_VAR = "CLOUD_LOG_ENABLED";

  private final ConfigPojo.ConfigPojoBuilder configBuilder = ConfigPojo.ConfigPojoBuilder.aConfigPojo();
  private final LayoutEngineJson layoutEngine = new LayoutEngineJson();
  private ScheduledExecutorService scheduledExecutor;
  private CloudWriter cloudWriter = null;
  private boolean enabled = false;

  private AtomicBoolean initialised = new AtomicBoolean(false);

  @Override
  public void start() {
    layoutEngine.start();
    super.start();

    if (!enabled) {
      enabled = determineEnabled();
    }
  }

  void flushLogs() {
    if (cloudWriter != null) {
      try {
        cloudWriter.processLogEntries();
      } catch (Exception e) {
        addWarn("Internal error", e);
      }
    }
  }


  @Override
  protected void append(ILoggingEvent eventObject) {
    if (!isStarted()) {
      // if super.start() has not been run, do not write to log
      return;
    }

    if (!initialised.get()) {
      // to avoid any logging prior to parent starting do single init for append
      runInit();
    }

    if (enabled && cloudWriter != null) {
      InputLogEvent msg = new InputLogEvent();
      msg.setTimestamp(eventObject.getTimeStamp());
      String jsonStr = layoutEngine.doLayout(eventObject);
      msg.setMessage(jsonStr);
      cloudWriter.addMessageToQueue(msg);
    }
  }

  private synchronized void runInit() {
    if (initialised.compareAndSet(false, true)) {
      // SCHEDULER
      ThreadFactory threadFactory = r -> {
        Thread thread = Executors.defaultThreadFactory().newThread(r);
        thread.setName("wise-log-" + UUID.randomUUID().toString().substring(0, 7));
        thread.setDaemon(true);
        return thread;
      };
      scheduledExecutor = Executors.newSingleThreadScheduledExecutor(threadFactory);
      final ConfigPojo configPojo = configBuilder.build();

      if (enabled) {
        getOrCreateLogWriter(configPojo)
            .ifPresent(writer -> {
                  this.cloudWriter = writer;
                  scheduledExecutor.scheduleWithFixedDelay(
                      this::flushLogs,
                      configPojo.getFlushIntervalInSeconds(),
                      configPojo.getFlushIntervalInSeconds(),
                      TimeUnit.SECONDS);

                  layoutEngine.addConfig(configPojo, cloudWriter.getConfigPropertyMap());

                }
            );
      }
    }
  }

  private Optional<CloudWriter> getOrCreateLogWriter(ConfigPojo configPojo) {
    return cloudWriter == null ? CloudWriter.createWriter(configPojo) : Optional.of(cloudWriter);
  }

  private boolean determineEnabled() {
    final Optional<String> cloudEnabledValue = System.getenv().keySet()
        .stream()
        .filter(CLOUD_LOG_ENABLED_ENV_VAR::equalsIgnoreCase)
        .map(System.getenv()::get)
        .findAny();

    // if config value set in environment, then set enabled to true
    if (!cloudEnabledValue.isPresent()) {
      System.out.println("WiseAppender is not enabled. Use " + CLOUD_LOG_ENABLED_ENV_VAR + " env variable to enable it.");
      return false;
    }

    if (!"true".equalsIgnoreCase(cloudEnabledValue.get())) {
      System.out.println(CLOUD_LOG_ENABLED_ENV_VAR + " env var found but false " + cloudEnabledValue.get());
      return false;
    }

    // filter checks complete
    return true;
  }

  @Override
  public void stop() {
    if (cloudWriter != null) {
      scheduledExecutor.shutdown();
      try {
        scheduledExecutor.awaitTermination(6 * configBuilder.build().getFlushIntervalInSeconds(), TimeUnit.SECONDS);
      } catch (InterruptedException e) {
        addWarn("Exception waiting for termination of scheduler", e);
      }

      flushLogs();
      cloudWriter.stop();
    }
    super.stop();
    enabled = false;
  }

  boolean isEnabled() {
    return enabled;
  }

  @SuppressWarnings("unused") // called via reflection
  public void setDebug(boolean debug) {
    configBuilder.withDebug(debug);
  }

  public void setEnabledByDefault(boolean enabledByDefault) {
    this.enabled = true;
  }

  @SuppressWarnings("unused") // called via reflection
  public void setFlushIntervalInSeconds(int flushIntervalInSeconds) {
    configBuilder.withFlushIntervalInSeconds(flushIntervalInSeconds);
  }

  @SuppressWarnings("unused") // called via reflection
  public void setLogDefaultGroup(String defaultLogName) {
    configBuilder.withDefaultLogGroup(defaultLogName);
  }

  @SuppressWarnings("unused") // called via reflection
  public void setModuleName(String moduleName) {
    configBuilder.withModuleName(moduleName);
  }

  @SuppressWarnings("unused") // called via reflection
  public ConfigPojo getConfigPojo() {
    return configBuilder.build();
  }

  void enableForTest() {
    setEnabledByDefault(true);
    initialised.set(false);
  }

  void setLogsWriter(CloudWriter logsWriter) {
    this.cloudWriter = logsWriter;
  }
}
