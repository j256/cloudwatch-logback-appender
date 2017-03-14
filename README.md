# Logback log appender for AWS CloudWatch

## Background

This package provides a logback appender that writes its log events to Cloudwatch.  Before you say it,
there seem to be many projects like this out there but I could find none of them that were
self-contained and that we published to the central Maven repo.  So here we go reinventing the wheel
again...  Sigh.

The git repository is:
	https://github.com/j256/cloudwatch-logback-appender

Maven packages are published via the central repo: <br />
	http://repo1.maven.org/maven2/com/j256/cloudwatchlogbackappender/cloudwatchlogbackappender/

Enjoy,
Gray Watson

## Maven Configuration

``` xml
<dependencies>
	<dependency>
		<groupId>com.j256.cloudwatchlogbackappender</groupId>
		<artifactId>cloudwatchlogbackappender</artifactId>
		<!-- NOTE: change the version to the most recent release version from the repo -->
		<version>0.1</version>
	</dependency>
</dependencies>
```

## logback.xml Configuration

Minimal logback appender configuration:

``` xml
<appender name="CLOUDWATCH" class="com.j256.cloudwatchlogbackappender.CloudWatchAppender">
	<!-- required settings -->
	<accessKey>XXXXXX</accessKey>
	<secretKey>YYYYYY</secretKey>
	<region>us-east-1</region>
	<logGroup>your-log-group-name-here</logGroup>
	<logStream>your-log-stream-name-here</logStream>
</appender>
```

Complete list of the appender properties.

| Property | Type | Description |
| -------- | ---- | ----------- |
| `accessKey` | *string* | IAM user access key |
| `secretKey` | *string* | IAM user secret key |
| `region` | *string* | AWS region |
| `logGroup` | *string* | Log group name |
| `logStream` | *string* | Log stream name |
| `messagePattern` | *string* | **Default: see below**<br/> Pattern of the message written to cloudwatch. |
| `maxBatchSize` | *integer* | **Default: 128**<br/>Maximum number of log events put into cloudwatch in single request. |
| `maxBatchTimeMillis` | *integer* | **Default: 5000**<br/>Maximum time in milliseconds to collect log events to submit batch. |
| `maxQueueWaitTimeMillis` | *integer* | **Default: 100**<br/>Maximum time in milliseconds to wait if internal queue is full before dropping log event on the floor. |
| `initialStartupSleepMillis` | *integer* | **Default: 2000**<br/>Time in milliseconds to wait for the log stuff to configure before we can make AWS requests which may generate log events. |
| `internalQueueSize` | *integer* | **Default: 8192**<br/>Size of the internal log event queue. |
| `createLogDests` | *boolean* | **Default: true**<br/>Create the CloudWatch log and stream if they don't exist.  Set to **false** to require fewer IAM policy actions. |
| `logExceptions` | *boolean* | **Default: true**<br/>Write exceptions to CloudWatch as log events. |

The ```messagePattern``` defines the format of the event when posted to CloudWatch.  The default is:

``` text
[{instance}] [{thread}] {level} {logger} - {msg}
```

It allows the following replacement token names surrounded by curly braces.  

| Token | Description |
| -------- | ----------- |
| `instance` | Name of the EC2 instance or "unknown" if not in EC2 or not known |
| `thread` | Name of the thread that generated the log event |
| `level` | Name of the log level of the event |
| `logger` | Name of the logger – often the class name |
| `msg` | Message from the event (with any arguments expanded) |

### Required IAM policy

When making any AWS API calls, we typically create a IAM user with specific permissions so if any API keys are stolen,
the hacker only have limited access to our AWS services.  To get the appender to be able to publish to CloudWatch,
the following IAM policy is required to create the log group and put log events to CloudWatch.  The
```logs:CreateLogGroup``` and ```logs:CreateLogStream``` actions are only required if the appender is creating the
log-group and stream itself.  The ```ec2:Describe*``` action is only required if you want the appender to query for the
ec2 instance name it is on.

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
                "ec2:Describe*"
            ],
            "Resource": [
                "arn:aws:logs:*:*:*",
                "arn:aws:ec2:*"
            ]
        }
    ]
}```
