package com.j256.cloudwatchlogbackappender;

import static java.lang.String.format;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.services.ec2.AmazonEC2Client;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.ec2.model.TagDescription;
import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.CreateLogGroupRequest;
import com.amazonaws.services.logs.model.CreateLogStreamRequest;
import com.amazonaws.services.logs.model.DataAlreadyAcceptedException;
import com.amazonaws.services.logs.model.DescribeLogGroupsRequest;
import com.amazonaws.services.logs.model.DescribeLogGroupsResult;
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest;
import com.amazonaws.services.logs.model.DescribeLogStreamsResult;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.InvalidSequenceTokenException;
import com.amazonaws.services.logs.model.LogGroup;
import com.amazonaws.services.logs.model.LogStream;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;
import com.amazonaws.util.EC2MetadataUtils;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;

/**
 * CloudWatch log appender for logback.
 * 
 * @author graywatson
 */
public class CloudWatchAppender extends UnsynchronizedAppenderBase<ILoggingEvent> {

	private static final int DEFAULT_MAX_BATCH_SIZE = 512;
	private static final int DEFAULT_MAX_BATCH_TIME_MILLIS = 5000;
	private static final int DEFAULT_INTERNAL_QUEUE_SIZE = 8192;
	private static final boolean DEFAULT_CREATE_LOG_DESTS = true;
	private static final int DEFAULT_MAX_QUEUE_WAIT_TIME_MILLIS = 100;
	private static final int INITIAL_LOG_STARTUP_SLEEP_MILLIS = 5000;
	private static final String EC2_DATA_UNKNOWN = "unknown";

	private String accessKey;
	private String secretKey;
	private String region;
	private String logGroup;
	private String logStream;
	private int maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
	private long maxBatchTimeMillis = DEFAULT_MAX_BATCH_TIME_MILLIS;
	private int internalQueueSize = DEFAULT_INTERNAL_QUEUE_SIZE;
	private boolean createLogDests = DEFAULT_CREATE_LOG_DESTS;

	private AWSLogsClient awsLogsClient;

	private BlockingQueue<ILoggingEvent> queue = new LinkedBlockingQueue<ILoggingEvent>();
	private Thread cloudWatchWriterThread;
	private final ThreadLocal<Boolean> guard = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return false;
		}
	};

	public CloudWatchAppender() {
		// for spring
	}

	/**
	 * After all of the setters, call initial to setup the appender.
	 */
	@Override
	public void start() {
		/*
		 * NOTE: as we startup here, we can't make any log calls so we can't make any RPC calls or anything without
		 * going recursive.
		 */
		if (MiscUtils.isBlank(accessKey)) {
			throw new IllegalStateException("Access-key not set or invalid for appender");
		}
		if (MiscUtils.isBlank(secretKey)) {
			throw new IllegalStateException("Secret-key not set or invalid for appender");
		}
		if (MiscUtils.isBlank(region)) {
			throw new IllegalStateException("Region not set or invalid for appender: " + region);
		}
		if (MiscUtils.isBlank(logGroup)) {
			throw new IllegalStateException("Log group name not set or invalid for appender: " + logGroup);
		}
		if (MiscUtils.isBlank(logStream)) {
			throw new IllegalStateException("Log stream name not set or invalid for appender: " + logStream);
		}

		queue = new ArrayBlockingQueue<ILoggingEvent>(internalQueueSize);

		// create our writer thread in the background
		cloudWatchWriterThread = new Thread(new CloudWatchWriter(), getClass().getSimpleName());
		cloudWatchWriterThread.setDaemon(true);
		cloudWatchWriterThread.start();

		super.start();
	}

	@Override
	public void stop() {
		super.stop();

		cloudWatchWriterThread.interrupt();
		try {
			cloudWatchWriterThread.join(100);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (awsLogsClient != null) {
			awsLogsClient.shutdown();
			awsLogsClient = null;
		}
	}

	@Override
	protected void append(ILoggingEvent loggingEvent) {
		// skip it if we just went recursive
		if (!guard.get()) {
			try {
				// TODO: if this fails, we should log it locally or something
				queue.offer(loggingEvent, DEFAULT_MAX_QUEUE_WAIT_TIME_MILLIS, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	public void setAccessKey(String accessKey) {
		this.accessKey = accessKey;
	}

	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	public void setRegion(String region) {
		this.region = region;
	}

	public void setLogGroup(String logGroup) {
		this.logGroup = logGroup;
	}

	public void setLogStream(String logStream) {
		this.logStream = logStream;
	}

	public void setMaxBatchSize(int maxBatchSize) {
		this.maxBatchSize = maxBatchSize;
	}

	public void setMaxBatchTime(long maxBatchTime) {
		this.maxBatchTimeMillis = maxBatchTime;
	}

	public void setInternalQueueSize(int internalQueueSize) {
		this.internalQueueSize = internalQueueSize;
	}

	public void setCreateLogDests(boolean createLogDests) {
		this.createLogDests = createLogDests;
	}

	private void verifyLogGroupExists() {
		DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(logGroup);
		DescribeLogGroupsResult result = awsLogsClient.describeLogGroups(request);
		for (LogGroup group : result.getLogGroups()) {
			if (logGroup.equals(group.getLogGroupName())) {
				return;
			}
		}
		CreateLogGroupRequest createRequest = new CreateLogGroupRequest(logGroup);
		awsLogsClient.createLogGroup(createRequest);
		addInfo(format("Created log group '%s'", logGroup));
	}

	private void verifLogStreamExists() {
		DescribeLogStreamsRequest request =
				new DescribeLogStreamsRequest().withLogGroupName(logGroup).withLogStreamNamePrefix(logStream);
		DescribeLogStreamsResult result = awsLogsClient.describeLogStreams(request);
		for (LogStream stream : result.getLogStreams()) {
			if (logStream.equals(stream.getLogStreamName())) {
				return;
			}
		}
		CreateLogStreamRequest createRequest = new CreateLogStreamRequest(logGroup, logStream);
		awsLogsClient.createLogStream(createRequest);
		addInfo(format("Created log stream '%s' for group '%s'", logStream, logGroup));
	}

	/**
	 * Background thread that writes the log events to cloudwatch.
	 */
	private class CloudWatchWriter implements Runnable {

		private String token;
		private String instanceName;

		@Override
		public void run() {

			try {
				Thread.sleep(INITIAL_LOG_STARTUP_SLEEP_MILLIS);
			} catch (InterruptedException e) {
				// ignore
			}

			AWSCredentials awsCredentials = new BasicAWSCredentials(accessKey, secretKey);
			awsLogsClient = new AWSLogsClient(awsCredentials);
			awsLogsClient.setRegion(RegionUtils.getRegion(region));
			if (createLogDests) {
				verifyLogGroupExists();
				verifLogStreamExists();
			}
			instanceName = requestInstanceName(awsCredentials);
			addInfo(format("{} started", getClass().getSimpleName()));

			List<ILoggingEvent> events = new ArrayList<ILoggingEvent>(maxBatchSize);
			Thread thread = Thread.currentThread();
			while (!thread.isInterrupted()) {
				long batchTimeout = System.currentTimeMillis() + maxBatchTimeMillis;
				while (!thread.isInterrupted()) {
					try {
						long waitFor = batchTimeout - System.currentTimeMillis();
						if (waitFor < 0) {
							break;
						}
						ILoggingEvent event = queue.poll(waitFor, TimeUnit.MILLISECONDS);
						if (event == null) {
							// wait timed out
							break;
						}
						events.add(event);
						if (events.size() >= maxBatchSize) {
							// batch size exceeded
							break;
						}
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						writeEvents(events);
						return;
					}
				}
				if (!events.isEmpty()) {
					writeEvents(events);
					events.clear();
				}
			}

			// now clear the queue and write all the rest
			events.clear();
			while (true) {
				ILoggingEvent event = queue.poll();
				if (event == null) {
					// nothing else waiting
					break;
				}
				events.add(event);
				if (events.size() >= maxBatchSize) {
					writeEvents(events);
					events.clear();
				}
			}
		}

		private void writeEvents(List<ILoggingEvent> events) {
			// we need this in case our RPC calls create log output which we don't want to then log again
			guard.set(true);
			Exception exception = null;
			try {
				List<InputLogEvent> logEvents = new ArrayList<InputLogEvent>(events.size());
				for (ILoggingEvent event : events) {
					InputLogEvent logEvent =
							new InputLogEvent().withTimestamp(event.getTimeStamp()).withMessage(eventToString(event));
					logEvents.add(logEvent);
				}
				PutLogEventsRequest request = new PutLogEventsRequest(logGroup, logStream, logEvents);
				if (token != null) {
					request = request.withSequenceToken(token);
				}
				PutLogEventsResult result = awsLogsClient.putLogEvents(request);
				token = result.getNextSequenceToken();
			} catch (DataAlreadyAcceptedException daac) {
				exception = daac;
				token = daac.getExpectedSequenceToken();
			} catch (InvalidSequenceTokenException iste) {
				exception = iste;
				token = iste.getExpectedSequenceToken();
			} catch (AmazonServiceException ase) {
				exception = ase;
			} finally {
				guard.set(false);
				if (exception != null) {
					addError(format("Exception thrown when creating logging %d events", events.size()), exception);
				}
			}
		}

		private String requestInstanceName(AWSCredentials awsCredentials) {
			String instanceId = EC2MetadataUtils.getInstanceId();
			if (instanceId == null) {
				return EC2_DATA_UNKNOWN;
			}
			try {
				AmazonEC2Client ec2Client = new AmazonEC2Client(awsCredentials);
				DescribeTagsRequest request = new DescribeTagsRequest();
				request.setFilters(Arrays.asList(new Filter("resource-type").withValues("instance"),
						new Filter("resource-id").withValues(instanceId)));
				DescribeTagsResult result = ec2Client.describeTags(request);
				List<TagDescription> tags = result.getTags();
				for (TagDescription tag : tags) {
					if ("Name".equals(tag.getKey())) {
						return tag.getValue();
					}
				}
				addInfo("Could not find EC2 instance name in tags: " + tags);
			} catch (AmazonServiceException ase) {
				addInfo("Looking up EC2 instance-name threw: " + ase);
			}
			return EC2_DATA_UNKNOWN;
		}

		private String eventToString(ILoggingEvent loggingEvent) {
			StringBuilder sb = new StringBuilder();
			// <pattern>[instance] [%thread] %-5level %logger{36} - %msg</pattern>
			sb.append('[')
					.append(instanceName)
					.append("] [")
					.append(loggingEvent.getThreadName())
					.append("] ")
					.append(loggingEvent.getLevel())
					.append(' ')
					.append(loggingEvent.getLoggerName())
					.append(" - ")
					.append(loggingEvent.getFormattedMessage());
			// handle any throw information
			if (loggingEvent.getThrowableProxy() != null) {
				sb.append('\n');
				boolean first = true;
				sb.setLength(0);
				for (IThrowableProxy throwable = loggingEvent.getThrowableProxy(); throwable != null; throwable =
						throwable.getCause()) {
					if (first) {
						first = false;
					} else {
						sb.append("Caused by: ");
					}
					sb.append(throwable.getClassName()).append(": ").append(throwable.getMessage()).append("\n");
					for (StackTraceElementProxy proxy : throwable.getStackTraceElementProxyArray()) {
						sb.append("     ").append(proxy.getSTEAsString()).append("\n");
					}
				}
			}
			return sb.toString();
		}
	}
}
