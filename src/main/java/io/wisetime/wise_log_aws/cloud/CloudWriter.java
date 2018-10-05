/*
 * Copyright (c) 2018 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.wise_log_aws.cloud;

import com.amazonaws.auth.profile.internal.AllProfiles;
import com.amazonaws.auth.profile.internal.BasicProfile;
import com.amazonaws.auth.profile.internal.BasicProfileConfigLoader;
import com.amazonaws.services.logs.AWSLogsAsync;
import com.amazonaws.services.logs.AWSLogsAsyncClientBuilder;
import com.amazonaws.services.logs.model.CreateLogStreamRequest;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Responsible for writing log json to AWS CloudWatch.
 */
class CloudWriter {

  private final ConcurrentLinkedQueue<InputLogEvent> messageQueue = new ConcurrentLinkedQueue<>();
  private final AWSLogsAsync awsLog;
  private final String logGroupName;
  private final String logStreamName;
  private final Map<String, String> configPropertyMap = new HashMap<>();
  /**
   * Today the basic structure of the PutLogEvents API is you do a call to PutLogEvents and it returns to you a result
   * that includes the sequence number. That same sequence number must be used in the subsequent put for the same (log
   * group, log stream) pair.
   */
  private String cloudWatchNextSequenceToken;

  private CloudWriter(ConfigPojo configPojo) {

    // use default config
    AWSLogsAsyncClientBuilder builder = AWSLogsAsyncClientBuilder.standard();

    File awsDir = new File(new File(System.getProperty("user.home")), ".aws");
    File configFile = new File(awsDir, "config");
    if (configFile.exists()) {
      AllProfiles allProfiles = BasicProfileConfigLoader.INSTANCE.loadProfiles(configFile);
      BasicProfile aDefault = allProfiles.getProfile("default");
      if (aDefault != null) {
        aDefault.getProperties().entrySet()
            .stream()
            .filter(entry -> entry.getKey() != null && !entry.getKey().toLowerCase().contains("secret"))
            .filter(entry -> !"output".equalsIgnoreCase(entry.getKey()))
            .filter(entry -> !"region".equalsIgnoreCase(entry.getKey()))
            .forEach(entry -> configPropertyMap.put(entry.getKey().toLowerCase(), entry.getValue()));
      }
    } else {
      System.out.println("Config file " + configFile.getAbsolutePath() + " doesn't exist.");
    }

    // proxy support possible via PredefinedClientConfigurations.defaultConfig()
    awsLog = builder.build();

    logGroupName = configPojo
        .getDefaultLogGroup()
        .orElseGet(() -> configPropertyMap.get("log_group_name"));
    if (logGroupName == null) {
      throw new RuntimeException("Log group name is not defined, please set <logDefaultGroup> on WiseAppender config or " +
          "set log_group_name=... in ${HOME}/.aws/config file");
    }

    logStreamName = String.format(
        "module-%s/%s",
        configPojo.getModuleName().orElse("unknown"),
        UUID.randomUUID().toString()
    );

    try {
      awsLog.createLogStream(
          new CreateLogStreamRequest()
              .withLogGroupName(logGroupName)
              .withLogStreamName(logStreamName)
      );
      System.out.println("Streaming logs to group: " + logGroupName + ", stream: " + logStreamName);
    } catch (com.amazonaws.services.logs.model.ResourceNotFoundException ex) {
      System.err.println("Unable to create log stream with " +
          "a name " + logStreamName + " for a group name " + logGroupName + ".");
      throw ex;
    }
  }

  /**
   * Send log entries
   */
  void processLogEntries() {
    boolean sentLimit;
    do {
      sentLimit = processToLimit();
    } while (sentLimit);
  }

  void stop() {
    try {
      awsLog.shutdown();
    } catch (Exception e) {
      System.out.println("Shutdown issue with cloud writer " + e.getMessage());
    }
  }

  private boolean processToLimit() {
    // process up to X messages per POST
    AtomicBoolean limitReached = new AtomicBoolean(false);

    List<InputLogEvent> eventList = createListFromQueue(limitReached);

    if (!eventList.isEmpty()) {
      // The log events in the batch must be in chronological ordered by their time stamp.
      List<InputLogEvent> eventListSorted =
          eventList.stream()
              .sorted(Comparator.comparingLong(InputLogEvent::getTimestamp))
              .collect(Collectors.toList());

      // send sorted group to cloud watch
      PutLogEventsResult result = awsLog.putLogEvents(
          new PutLogEventsRequest()
              .withLogGroupName(logGroupName)
              .withLogStreamName(logStreamName)
              .withLogEvents(eventListSorted)
              .withSequenceToken(cloudWatchNextSequenceToken)
      );
      cloudWatchNextSequenceToken = result.getNextSequenceToken();
    }
    return limitReached.get();
  }

  /**
   * <pre>
   *   a. The maximum batch size is 1,048,576 bytes, and this size is calculated as the sum of all event messages in UTF-8,
   *      plus 26 bytes for each log event.
   *   b.
   * <pre>
   * @param limitReached Set to true if limit reached
   * @return List to send to AWS
   */
  private List<InputLogEvent> createListFromQueue(AtomicBoolean limitReached) {


    final List<InputLogEvent> eventList = new ArrayList<>();
    // The maximum number of log events in a batch is 10,000.
    final int maxLogEvents = 8000;
    final AtomicInteger byteCount = new AtomicInteger();

    InputLogEvent logEvent;
    while ((logEvent = messageQueue.poll()) != null) {
      if (logEvent.getMessage() != null) {
        eventList.add(logEvent);
        if (eventList.size() >= maxLogEvents) {
          // log row limit reached
          limitReached.set(true);
          return eventList;
        }

        int logBundleSize = byteCount.addAndGet(logEvent.getMessage().getBytes(StandardCharsets.UTF_8).length + 26);
        int MAX_AWS_PUT_SIZE = 1_048_576 - 48_000;
        if (logBundleSize > MAX_AWS_PUT_SIZE) {
          // message size in bytes limit reached
          limitReached.set(true);
          return eventList;
        }
      }
    }

    return eventList;
  }

  Map<String, String> getConfigPropertyMap() {
    return configPropertyMap;
  }

  void addMessageToQueue(InputLogEvent msg) {
    messageQueue.offer(msg);
  }

  static Optional<CloudWriter> createWriter(ConfigPojo configPojo) {
    try {
      return Optional.of(new CloudWriter(configPojo));
    } catch (Throwable t) {
      System.out.println("Error creating AWS cloud log writer, cause: " + t.getMessage());
      return Optional.empty();
    }
  }

}
