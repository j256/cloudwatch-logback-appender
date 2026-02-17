Logback log appender for AWS CloudWatch
=======================================

[![Maven Central](https://img.shields.io/maven-central/v/com.j256.cloudwatchlogbackappender/cloudwatchlogbackappender?style=flat-square)](https://mvnrepository.com/artifact/com.j256.cloudwatchlogbackappender/cloudwatchlogbackappender/latest)
[![javadoc](https://javadoc.io/badge2/com.j256.cloudwatchlogbackappender/cloudwatchlogbackappender/javadoc.svg)](https://javadoc.io/doc/com.j256.cloudwatchlogbackappender/cloudwatchlogbackappender)
[![CircleCI](https://circleci.com/gh/j256/cloudwatch-logback-appender.svg?style=svg)](https://circleci.com/gh/j256/cloudwatch-logback-appender) [![CodeCov](https://img.shields.io/codecov/c/github/j256/cloudwatch-logback-appender.svg)](https://codecov.io/github/j256/cloudwatch-logback-appender/)

# Background

This package provides a logback appender that writes its log events to Cloudwatch.  Before you say it,
there seem to be many projects like this out there but I could find none of them that were
self-contained and that were published to the central Maven repo.

* Code available from the [git repository](https://github.com/j256/cloudwatch-logback-appender).
* Maven packages are published via [Maven Central](https://mvnrepository.com/artifact/com.j256.cloudwatchlogbackappender/cloudwatchlogbackappender/latest)
* Documentation: [javadoc files](https://javadoc.io/doc/com.j256.cloudwatchlogbackappender/cloudwatchlogbackappender)

Enjoy. Gray Watson

# logback.xml Configuration

Minimal logback appender configuration:

``` xml
<appender name="CLOUDWATCH" class="com.j256.cloudwatchlogbackappender.CloudWatchAppender">
	<region>us-east-1</region>
	<logGroup>your-log-group-name-here</logGroup>
	<logStream>your-log-stream-name-here</logStream>
	<layout>
		<!-- possible layout pattern -->
		<pattern>[%thread] %level %logger{20} - %msg%n%xThrowable</pattern>
	</layout>
</appender>
```

CloudWatch unfortunately does not allow multiple hosts to write to the same log-stream because of the sequence-token
needed to get correct ordering.  If multiple servers are writing logs, you should configure the log-stream name with an
instance-id suffix or something.  The `logStream` name setting uses the `Ec2PatternLayout` to generate the name, which
can also be used to format your log lines.  This allows you to use the standard `%token` such as `%date` in the name of
the log-stream – see the [logback documentation](http://logback.qos.ch/manual/layouts.html#conversionWord).  The
`Ec2PatternLayout` class also adds support for additional tokens:

| Property | Description |
| -------- | ----------- |
| `in` | Same as instanceName. |
| `instanceId` | ID of the EC2 instance if available. |
| `iid` | Same as instanceId. |
| `instanceName` | Name of the instance from the EC2 tags if the name is not available. |
| `instance` | Same as instanceName. |
| `taskId` | ID of the ECS task. |
| `uuid` | Random UUID as a string |
| `hostName` | Name of the host from `InetAddress.getLocalHost()`. |
| `host` | Same as hostName. |
| `hostAddress` | IP address of the host from `InetAddress.getHostAddress()`. |
| `address` | Same as hostAddress. |
| `addr` | Same as hostAddress. |
| `systemProperty` | Value of a system-property whose name is set as an {option}.  Ex: %systemProperty{os.version}`. |
| `property` | Same as systemProperty. |
| `prop` | Same as systemProperty. |
| `systemEnviron` | Value of a environmental variable whose name is set as an {option}.  Ex: %systemEnviron{SHELL}`. |
| `environ` | Same as systemEnviron. |
| `env` | Same as systemEnviron. |

For example:

``` xml
	<logGroup>your-log-group-name-here</logGroup>
	<logStream>general-%instance-%date{yyyyMMdd,UTC}-%uuid</logStream>
```

This will generate a log-stream name with the prefix "general-" and then with the instance-name,
date in UTC timezone, and a random UUID.

**NOTE:** The instance-name and instance-id tokens will only work when running on an EC2 instance that
supports the EC2MetadataUtils methods for looking up the information.  You can call
`Ec2InstanceNameConverter.setInstanceName(...)` or `Ec2InstanceIdConverter.setInstanceId(...)` early in your
program if you want to set them yourself. 

**NOTE:** `logGroup` must match the regex pattern `[\.\-_/#A-Za-z0-9]+`.  `logStream` cannot contain the ':' character
which will be replaced by '_'.

The appender also adds the support for the previous list of % tokens to be expanded on each log line:

``` xml
<appender name="CLOUDWATCH" class="com.j256.cloudwatchlogbackappender.CloudWatchAppender">
	...
	<layout class="com.j256.cloudwatchlogbackappender.Ec2PatternLayout">
		<pattern>\[%instance\] \[%thread\] %level %logger{20} - %msg%n%xThrowable</pattern>
	</layout>
```

Here is the complete list of the appender properties.

| Property | Type | Default | Description |
| -------- | ---- | ------- | ----------- |
| `region` | *string* | none | AWS region for the CloudWatch API.  It not specified the SDK will try to figure it out. |
| `logGroup` | *string* | none | Log group name pattern|
| `logStream` | *string* | none | Log stream name pattern |
| `maxBatchSize` | *int* | 128 | Maximum number of log events put into CloudWatch in single request. |
| `maxBatchTimeMillis` | *long* | 5000 | Maximum time in milliseconds to collect log events to submit batch. |
| `maxQueueWaitTimeMillis` | *long* | 100 | Maximum time in milliseconds to wait if internal queue is full before using the emergency appender (see below). |
| `initialWaitTimeMillis` | *long* | 0 | Initial wait time before logging messages.  Helps if server needs to configure itself initially. |
| `internalQueueSize` | *int* | 8192 | Size of the internal log event queue. |
| `createLogDests` | *boolean* | true | Create the CloudWatch log and stream if they don't exist. |
| `maxEventMessageSize` | *int* | 256k | Maximum size of event message before it is truncated or sent to emergency appender. |
| `truncateEventMessages` | *boolean* | true | If an event it too large, should the message be truncated.  If false then it will be sent to emergency appender. |
| `copyEvents` | *boolean* | true | Copies the event for logging by the background thread. |
| `printRejectedEvents` | *boolean* | false | Print any rejected events to stderr if the emergency appender doesn't work. |
| `disableAwsMetadata` | *boolean* | false | Don't try to lookup any EC2 or ECS metadata. Set values to unknown. |
| `retentionDays` | *int* | none | Optional retention period in days to add the log-group. |
| `waitForAllEvents` | *boolean* | true | Wait for all of the events to be published before stopping the appender. |

## Emergency Appender

Since this appender is queuing up log events and then writing them remotely, there are a number of situations which
might result in log events not getting remoted correctly.  To protect against this, you can add in an "emergency"
appender to write events to the console or a file by adding the following to your CLOUDWATCH appender stanza:

``` xml
<appender name="CLOUDWATCH" class="com.j256.cloudwatchlogbackappender.CloudWatchAppender">
	...
	<appender-ref ref="EMERGENCY_FILE" />
```

This appender will be used if:

* there was some problem configuring the CloudWatch or other AWS APIs
* the internal queue fills up and messages can't be written remotely fast enough
* there was some problem with the actual put events CloudWatch call – maybe a transient network failure

If no emergency appender is configured and a problem does happen then the log messages will be not be persisted.

# AWS Permissions

You can specify the AWS CloudWatch permissions in a number of ways.  If you use the `accessKeyId` and `secretKey`
settings in the `logback.xml` file then the appender will use those credentials directly.  You can also set the
`cloudwatchappender.aws.accessKeyId` and `cloudwatchappender.aws.secretKey` Java System properties which will be
used.  If neither of those are specified then the appender will use the `DefaultAWSCredentialsProviderChain` which
looks for the access and secret keys in:

* Environment Variables: `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY` or `AWS_ACCESS_KEY` and `AWS_SECRET_KEY`
* Java System Properties: `aws.accessKeyId` and `aws.secretKey`
* Credential file at the default location (`~/.aws/credentials`) shared by all AWS SDKs and the AWS CLI
* Instance profile credentials delivered through the Amazon EC2 metadata service

## IAM Permissions

When making any AWS API calls, we typically create a IAM user with specific permissions so if any API keys are stolen,
the hacker only have limited access to our AWS services.  To get the appender to be able to publish to CloudWatch,
the following IAM policy is required to create the log group and put log events to CloudWatch.

The `logs:CreateLogGroup` and `logs:CreateLogStream` actions are only required if the appender is creating the
log-group and stream itself (see `createLogDests` option above).  The `ec2:DescribeTags` action is only required
if you want the appender to query for the ec2 instance name it is on – see `Ec2PatternLayout` above.

```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "logs:CreateLogGroup",
                "logs:CreateLogStream",
                "logs:DescribeLogGroups",
                "logs:DescribeLogStreams",
                "logs:PutLogEvents",
                "ec2:DescribeTags"
            ],
            "Resource": [
                "*"
            ]
        }
    ]
}
```

I couldn't figure out how to restrict to all ec2 instances.  If you are only doing log requests then
you should be able to limit it to the resource `arn:aws:logs:*:*:*`.

# AWS Metadata Limitations

I try to limit the dependencies in most of my libraries.  For this reason the `%instanceName` tag is not by default
available unless the `ec2` dependency is available on the classpath.  See below.

In addition, the `%taskId` tag available under ECS uses a brittle regular-expression pattern to extract the task-id from
the JSON web response.  If this does not seem to work, you might consider using a more reliable API call under ECS and
setting the value using the `TaskIdConverter.setTaskId(String)` method early in your application before the logging
system initializes.

# Maven Configuration

Maven packages are published via [Maven Central](https://mvnrepository.com/artifact/com.j256.simplejmx/simplejmx/latest)

``` xml
<dependencies>
	<dependency>
		<groupId>com.j256.cloudwatchlogbackappender</groupId>
		<artifactId>cloudwatchlogbackappender</artifactId>
		<!-- NOTE: change the version to the most recent release version from the repo -->
		<version>3.0</version>
	</dependency>
</dependencies>
```

## Dependencies

By default the appender has dependencies on logback (duh) but also the CloudWatch logs AWS SDK
packages.  You can add a exclusion for these packages if you want to depend on different versions.

``` xml
<dependency>
	<groupId>software.amazon.awssdk</groupId>
	<artifactId>cloudwatchlogs</artifactId>
	<version>2.41.9</version>
</dependency>
```

If you are running inside of EC2, you might want to add the EC2 SDK which will allow the `%instanceName` tag to work.

``` xml
<dependency>
	<groupId>software.amazon.awssdk</groupId>
	<artifactId>ec2</artifactId>
	<version>2.41.9</version>
</dependency>
```

# Simple Logging Package

Another logging package that you might be interested in is my [SimpleLogging](https://github.com/j256/simplelogging).
It provides functionality similar to SLF4J but with backend support for a large number of logger implementations.

# ChangeLog Release Notes

See the [ChangeLog file](https://github.com/j256/cloudwatch-logback-appender/blob/main/src/main/javadoc/doc-files/changelog.txt).
