# Logback log appender for AWS CloudWatch

## Background

This package provides a logback appender that writes its log events to Cloudwatch.  Before you say it,
there seem to be many projects like this out there but I could find none of them that were
self-contained and that we published to the central Maven repo.  So here we go reinventing the wheel
again...  Sigh.

The git repository is:
	https://github.com/j256/cloudwatch-logback-appender

Maven packages are published via the central repo:
	http://repo1.maven.org/maven2/com/j256/cloudwatch-logback-appender/cloudwatch-logback-appender/

Enjoy,
Gray Watson

## Maven Configuration

``` xml
<dependencies>
	<dependency>
		<groupId>com.j256.cloudwatchlogbackappender</groupId>
		<artifactId>cloudwatchlogbackappender</artifactId>
		<version>0.1</version>
	</dependency>
</dependencies>
```

## logback.xml Configuration

``` xml
<appender name="CLOUDWATCH" class="com.j256.cloudwatchlogbackappender.CloudWatchAppender">
    <!-- required settings -->
    <accessKey>XXXXXX</accessKey>
    <secretKey>YYYYYY</secretKey>
    <region>us-east-1</region>
    <logGroup>application-name</logGroup>
    <logStream>general</logStream>
    <!-- not required settings, default values shown below -->
    <messagePattern>[{instanceName}] [{threadName}] {level} {loggerName} - {message}</messagePattern>
    <maxBatchSize>128</maxBatchSize>
    <maxBatchTimeMillis>5000</maxBatchTimeMillis>
    <internalQueueSize>8192</internalQueueSize>
    <createLogDests>true</createLogDests>
    <logExceptions>true</logExceptions>
</appender>
```
