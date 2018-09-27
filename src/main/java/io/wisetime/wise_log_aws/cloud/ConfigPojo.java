/*
 * Copyright (c) 2018 Practice Insight Pty Ltd. All Rights Reserved.
 */

package io.wisetime.wise_log_aws.cloud;

import java.util.Optional;

/**
 * Immutable config object for use in writer/filter.
 */
class ConfigPojo {

  private boolean debug = false;
  private String moduleName;
  private String defaultLogGroup;

  private int flushIntervalInSeconds = 4;

  public boolean isDebug() {
    return debug;
  }

  int getFlushIntervalInSeconds() {
    return flushIntervalInSeconds;
  }

  Optional<String> getModuleName() {
    return Optional.ofNullable(moduleName);
  }

  Optional<String> getDefaultLogGroup() {
    return Optional.ofNullable(defaultLogGroup);
  }

  @SuppressWarnings("WeakerAccess")
  static final class ConfigPojoBuilder {
    private boolean debug = false;
    private int flushIntervalInSeconds = 3;
    private String moduleName;
    private String defaultLogGroup;

    private ConfigPojoBuilder() {
    }

    public static ConfigPojoBuilder aConfigPojo() {
      return new ConfigPojoBuilder();
    }

    public ConfigPojoBuilder withDebug(boolean debug) {
      this.debug = debug;
      return this;
    }

    public ConfigPojoBuilder withFlushIntervalInSeconds(int flushIntervalInSeconds) {
      this.flushIntervalInSeconds = flushIntervalInSeconds;
      return this;
    }

    public ConfigPojoBuilder withDefaultLogGroup(String defaultLogGroup) {
      this.defaultLogGroup = defaultLogGroup;
      return this;
    }

    public ConfigPojoBuilder withModuleName(String moduleName) {
      this.moduleName = moduleName;
      return this;
    }

    public ConfigPojo build() {
      ConfigPojo configPojo = new ConfigPojo();
      configPojo.debug = this.debug;
      configPojo.moduleName = this.moduleName;
      configPojo.defaultLogGroup = this.defaultLogGroup;
      configPojo.flushIntervalInSeconds = this.flushIntervalInSeconds;
      return configPojo;
    }
  }
}
