# Logback log appender for AWS CloudWatch

## Background

This package provides a logback appender that writes its log events to Cloudwatch.  Before you say it,
there seem to be many projects like this out there but I could find none of them that were
self-contained and that were published to the central Maven repo.

Code available from the [git repository](https://github.com/j256/cloudwatch-logback-appender).

Maven packages are published via the [maven central repo](http://repo1.maven.org/maven2/com/j256/cloudwatchlogbackappender/cloudwatchlogbackappender/).	

Enjoy,
Gray Watson

## Maven Configuration

``` xml
<dependencies>
	<dependency>
		<groupId>com.j256.cloudwatchlogbackappender</groupId>
		<artifactId>cloudwatchlogbackappender</artifactId>
		<!-- NOTE: change the version to the most recent release version from the repo -->
		<version>1.0</version>
	</dependency>
</dependencies>
```

## logback.xml Configuration

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

You may want to use our ```Ec2PatternLayout``` class which adds support for the ec2 instance-name tag from the tokens
%instance, %instanceName, and %in.  It also supports %instanceId and %iid for the instance-id as well.

``` xml
<appender name="CLOUDWATCH" class="com.j256.cloudwatchlogbackappender.CloudWatchAppender">
	...
	<layout class="com.j256.cloudwatchlogbackappender.Ec2PatternLayout">
		<pattern>[%instance] [%thread] %level %logger{20} - %msg%n%xThrowable</pattern>
	</layout>
```

*NOTE:* If the instance-name is not available then the instance-id will be used for the name instead.

Here is the complete list of the appender properties.

| Property | Type | Description |
| -------- | ---- | ----------- |
| `accessKey` | *string* | **Default: none, code will use ```DefaultAWSCredentialsProviderChain```** <br /> AWS API access key, see IAM policy details below |
| `secretKey` | *string* | **Default: none, code will use ```DefaultAWSCredentialsProviderChain```** <br /> AWS API secret key, see IAM policy details below |
| `region` | *string* | AWS region needed by CloudWatch API |
| `logGroup` | *string* | Log group name |
| `logStream` | *string* | Log stream name |
| `maxBatchSize` | *integer* | **Default: 128**<br/>Maximum number of log events put into CloudWatch in single request. |
| `maxBatchTimeMillis` | *integer* | **Default: 5000**<br/>Maximum time in milliseconds to collect log events to submit batch. |
| `maxQueueWaitTimeMillis` | *integer* | **Default: 100**<br/>Maximum time in milliseconds to wait if internal queue is full before using the emergency appender (see below). |
| `internalQueueSize` | *integer* | **Default: 8192**<br/>Size of the internal log event queue. |
| `createLogDests` | *boolean* | **Default: true**<br/>Create the CloudWatch log and stream if they don't exist. |

### Emergency Appender

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

### AWS Permissions

You can specify the AWS CloudWatch permissions in a number of ways.  If you use the ```accessKey``` and ```secretKey```
settings in the config file then the appender will use those credentials directly.  If they are not specified then the
appender will use the ```DefaultAWSCredentialsProviderChain``` which looks the access and secret keys in:

* Environment Variables – ```AWS_ACCESS_KEY_ID``` and ```AWS_SECRET_ACCESS_KEY``` or ```AWS_ACCESS_KEY``` and ```AWS_SECRET_KEY```
* Java System Properties – ```aws.accessKeyId``` and ```aws.secretKey```
* Credential file at the default location (```~/.aws/credentials```) shared by all AWS SDKs and the AWS CLI
* Instance profile credentials delivered through the Amazon EC2 metadata service

When making any AWS API calls, we typically create a IAM user with specific permissions so if any API keys are stolen,
the hacker only have limited access to our AWS services.  To get the appender to be able to publish to CloudWatch,
the following IAM policy is required to create the log group and put log events to CloudWatch.

The ```logs:CreateLogGroup``` and ```logs:CreateLogStream``` actions are only required if the appender is creating the
log-group and stream itself (see ```createLogDests``` option above).  The ```ec2:DescribeTags``` action is only required
if you want the appender to query for the ec2 instance name it is on – see ```Ec2PatternLayout``` above.

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
you should be able to limit it to the resource ```arn:aws:logs:*:*:*"```.

## ChangeLog Release Notes

See the [ChangeLog.txt file](src/main/javadoc/doc-files/changelog.txt).
