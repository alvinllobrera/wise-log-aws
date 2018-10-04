# WiseAppender - Logback AWS CloudWatch Appender

WiseAppender is Logback appender that writes directly into AWS CloudWatch using AWS SDK and CloudWatch API.

The library includes `LayoutEngineJson` that writes logging events in a structured way, as a JSON object. Logs can be
browsed and filtered directly from the CloudWatch console.

See AWS Filtering
- https://docs.aws.amazon.com/AmazonCloudWatch/latest/logs/FilterAndPatternSyntax.html

## Filtering log events, before writing to CloudWatch
WiseAppender ships with `ThresholdFilterWithExclusion` - log events filter that gives ability to filter logs before
they are sent to AWS CloudWatch.

For example, it's possible to set `ROOT` logger level to `INFO`, but only allow log events with level `WARN` or above, to the `WiseAppender` but allow logs with a defined prefix (`excludedLogPrefixList`) to be excluded from the level filter application. See the example:

```
  <!-- deny all events with a level below WARN, except for io.wisetime.* packages -->
  <filter class="io.wisetime.wise_log_aws.cloud.ThresholdFilterWithExclusion">
    <level>WARN</level>
    <excludedLogPrefixList>io.wisetime.</excludedLogPrefixList>
  </filter>
```
This is useful when you want to be sure that all the logs from your package (`io.wisetime.`) are shipped to AWS
CloudWatch.

## Usage
To use `WiseAppender`, include this library in `pom.xml` or `build.gradle`,

maven's pom.xml:
```xml
<dependency>
  <groupId>io.wisetime</groupId>
  <artifactId>wise-log-aws</artifactId>
  <version>${wise.log.aws.version}</version>
</dependency>
```

The `wise-log-aws` module has transitive dependencies to:
```xml
     <dependency>
       <groupId>org.slf4j</groupId>
       <artifactId>slf4j-api</artifactId>
       <version>1.7.25</version>
     </dependency>
     <dependency>
       <groupId>ch.qos.logback</groupId>
       <artifactId>logback-classic</artifactId>
       <version>1.2.3</version>
     </dependency>
```

gradle's build.gradle:
```groovy
dependencies {
    ...
    compile 'io.wisetime:wise-log-aws:${wise.log.aws.version}'
}

```
Check the [latest stable release](https://search.maven.org/search?q=a:wise-log-aws) at Maven Central.

## Configuring WiseTimeAppender

`resources/logback.xml`
```
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <!-- This is optional, but allows extra time for any pending log events to be sent to AWS Cloud Watch before JVM shutdown -->
  <shutdownHook class="ch.qos.logback.core.hook.DelayingShutdownHook"/>

  <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
    <!-- uses PatternLayoutEncoder by default -->
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <appender name="AWS_CLOUD" class="io.wisetime.wise_log_aws.cloud.WiseAppender">
    <!-- deny all events with a level below WARN, except for io.wisetime.* packages -->
    <filter class="io.wisetime.wise_log_aws.cloud.ThresholdFilterWithExclusion">
      <level>WARN</level>
      <excludedLogPrefixList>io.mycompany.</excludedLogPrefixList>
    </filter>
    <moduleName>my-app-name</moduleName>
    <logDefaultGroup>aws-group-i-created-goes-here</logDefaultGroup>
  </appender>

  <root level="INFO" additivity="false">
    <appender-ref ref="STDOUT"/>
    <appender-ref ref="AWS_CLOUD"/>
  </root>

</configuration>
```

### Configuring AWS and creating Credential Files

The standard AWS credentials SDK conventions are used.  For file based config, the `~/.aws` folder can provide the `credentials` file:

```
cat ~/.aws/credentials
[default]
aws_access_key_id=AKIAISH3E3(...)
aws_secret_access_key=NjQmx8gR(...)
```
See https://docs.aws.amazon.com/sdk-for-java/v1/developer-guide/credentials.html

Additional CloudWatch region can be set in normal manner:
```
cat ~/.aws/config
[default]
region=ap-southeast-1
```

The AWS library can also use environment variables and other config mechanisms.  Please refer to AWS SDK documentation in this regard for more information.

### Allowing WiseAppender to Send Logs to CloudWatch

An environment variable `CLOUD_LOG_ENABLED` should be set to `true` to enable `WiseAppender`.

For example in bash:
```
export CLOUD_LOG_ENABLED=true
```

# Shaded JAR

WiseAppender library includes shaded jars in an isolated namespace `wise_repack.log.*` to reduce dependency conflicts with upstream libraries:
- AWS SDK
- Apache Http Library
- Joda,
- Jackson.

# Library versioning
This library follows standard Semantic Versioning scheme (MAJOR.MINOR.PATCH):
- **MAJOR** version when incompatible API changes are introduced,
- **MINOR** version when new functionalities are added in a backwards-compatible manner, and
- **PATCH** version when backwards-compatible bug fixes are introduced.
