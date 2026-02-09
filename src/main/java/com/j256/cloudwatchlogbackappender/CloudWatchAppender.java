package com.j256.cloudwatchlogbackappender;

import java.net.Inet4Address;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.slf4j.Marker;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClientBuilder;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogGroupRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.CreateLogStreamRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DataAlreadyAcceptedException;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogGroupsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.DescribeLogStreamsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.InputLogEvent;
import software.amazon.awssdk.services.cloudwatchlogs.model.InvalidSequenceTokenException;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogGroup;
import software.amazon.awssdk.services.cloudwatchlogs.model.LogStream;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutRetentionPolicyRequest;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeTagsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeTagsResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.TagDescription;

/**
 * CloudWatch log appender for logback.
 * 
 * @author graywatson
 */
public class CloudWatchAppender extends UnsynchronizedAppenderBase<ILoggingEvent>
		implements AppenderAttachable<ILoggingEvent> {

	/** size of batch to write to cloudwatch api */
	private static final int DEFAULT_MAX_BATCH_SIZE = 128;
	/** time in millis to wait until we have a bunch of events to write */
	private static final long DEFAULT_MAX_BATCH_TIME_MILLIS = 5000;
	/** internal event queue size before we drop log requests on the floor */
	private static final int DEFAULT_INTERNAL_QUEUE_SIZE = 8192;
	/** create log destination group and stream when we startup */
	private static final boolean DEFAULT_CREATE_LOG_DESTS = true;
	/** max time to wait in millis before dropping a log event on the floor */
	private static final long DEFAULT_MAX_QUEUE_WAIT_TIME_MILLIS = 100;
	/** time to wait to initialize which helps when application is starting up */
	private static final long DEFAULT_INITIAL_WAIT_TIME_MILLIS = 0;
	/** how many times to retry a cloudwatch request */
	private static final int PUT_REQUEST_RETRY_COUNT = 2;
	/** property looked for to find the aws access-key-id */
	public static final String AWS_ACCESS_KEY_ID_PROPERTY = "cloudwatchappender.aws.accessKeyId";
	/** property looked for to find the aws secret-key */
	public static final String AWS_SECRET_KEY_PROPERTY = "cloudwatchappender.aws.secretKey";
	/** property looked for to find the aws endpoint-url */
	public static final String AWS_ENDPOINT_URL_PROPERTY = "cloudwatchappender.aws.endpointUrl";
	public static final int DEFAULT_MAX_EVENT_MESSAGE_SIZE = 256 * 1024;
	public static final boolean DEFAULT_TRUNCATE_EVENT_MESSAGES = true;
	public static final boolean DEFAULT_COPY_EVENTS = true;
	public static final boolean DEFAULT_PRINT_REJECTED_EVENTS = false;
	public static final Pattern LOG_GROUP_PATTERN = Pattern.compile("[\\.\\-_/#A-Za-z0-9]+");

	private URI endpointUrl;
	private Region region;
	private String logGroupName;
	private String logStreamName;
	private Layout<ILoggingEvent> layout;
	private Appender<ILoggingEvent> emergencyAppender;
	private int maxBatchSize = DEFAULT_MAX_BATCH_SIZE;
	private long maxBatchTimeMillis = DEFAULT_MAX_BATCH_TIME_MILLIS;
	private long maxQueueWaitTimeMillis = DEFAULT_MAX_QUEUE_WAIT_TIME_MILLIS;
	private int internalQueueSize = DEFAULT_INTERNAL_QUEUE_SIZE;
	private boolean createLogDests = DEFAULT_CREATE_LOG_DESTS;
	private long initialWaitTimeMillis = DEFAULT_INITIAL_WAIT_TIME_MILLIS;
	private int maxEventMessageSize = DEFAULT_MAX_EVENT_MESSAGE_SIZE;
	private boolean truncateEventMessages = DEFAULT_TRUNCATE_EVENT_MESSAGES;
	private boolean copyEvents = DEFAULT_COPY_EVENTS;
	private boolean printRejectedEvents = DEFAULT_PRINT_REJECTED_EVENTS;
	private boolean disableEc2Metadata;
	private int retentionDays;

	private CloudWatchLogsClient awsLogsClient;
	private CloudWatchLogsClient testAwsLogsClient;
	private Ec2Client testAmazonEc2Client;
	private volatile long eventsWrittenCount;

	private BlockingQueue<ILoggingEvent> loggingEventQueue;
	private Thread cloudWatchWriterThread;
	private final ThreadLocal<Boolean> stopMessagesThreadLocal = new ThreadLocal<Boolean>();
	private volatile boolean warningMessagePrinted;
	private final InputLogEventComparator inputLogEventComparator = new InputLogEventComparator();

	public CloudWatchAppender() {
		// for spring
	}

	/**
	 * After all of the setters, call initial to setup the appender.
	 */
	@Override
	public void start() {
		if (started) {
			return;
		}
		/*
		 * NOTE: as we startup here, we can't make any log calls so we can't make any RPC calls or anything without
		 * going recursive.
		 */
		if (region == null) {
			throw new IllegalStateException("Region not set or invalid for appender: " + region);
		}
		if (MiscUtils.isBlank(logGroupName)) {
			throw new IllegalStateException("Log group name not set or invalid for appender: " + logGroupName);
		}
		if (!LOG_GROUP_PATTERN.matcher(logGroupName).matches()) {
			throw new IllegalStateException("Log group name does not match AWS acceptance pattern '" + LOG_GROUP_PATTERN
					+ "': " + logGroupName);
		}
		if (MiscUtils.isBlank(logStreamName)) {
			throw new IllegalStateException("Log stream name not set or invalid for appender: " + logStreamName);
		}
		if (layout == null) {
			throw new IllegalStateException("Layout was not set for appender");
		}

		loggingEventQueue = new ArrayBlockingQueue<ILoggingEvent>(internalQueueSize);

		// create our writer thread in the background
		cloudWatchWriterThread = new Thread(new CloudWatchWriter(), getClass().getSimpleName());
		cloudWatchWriterThread.setDaemon(true);
		cloudWatchWriterThread.start();

		if (emergencyAppender != null && !emergencyAppender.isStarted()) {
			emergencyAppender.start();
		}
		super.start();
	}

	@Override
	public void stop() {
		if (!started) {
			return;
		}

		cloudWatchWriterThread.interrupt();
		try {
			cloudWatchWriterThread.join(1000);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		if (awsLogsClient != null) {
			awsLogsClient.close();
			awsLogsClient = null;
		}

		super.stop();
	}

	@Override
	protected void append(ILoggingEvent loggingEvent) {

		// check wiring
		if (loggingEventQueue == null) {
			if (!warningMessagePrinted) {
				System.err.println(getClass().getSimpleName() + " not started correctly, ignoring all log messages");
				warningMessagePrinted = true;
			}
			return;
		}

		// skip it if we just went recursive
		Boolean stopped = stopMessagesThreadLocal.get();
		if (stopped != null && stopped) {
			return;
		}

		String message = loggingEvent.getMessage();
		boolean copied = false;
		if (message != null && message.length() > maxEventMessageSize) {
			if (!truncateEventMessages) {
				// if the message us too big and we can't truncate it then just write it to the emergency appender
				appendToEmergencyAppender(loggingEvent);
				return;
			}
			// we copy all of the fields over but with the truncated message
			loggingEvent = copyEvent(loggingEvent, message.substring(0, maxEventMessageSize));
			copied = true;
		}
		/*
		 * Since we are writing the event out in another thread, the default is to copy the event into our internal
		 * queue for transmission.
		 */
		if (!copied) {
			if (copyEvents) {
				loggingEvent = copyEvent(loggingEvent, null);
			} else {
				loggingEvent.prepareForDeferredProcessing();
			}
		}

		try {
			if (!loggingEventQueue.offer(loggingEvent, maxQueueWaitTimeMillis, TimeUnit.MILLISECONDS)) {
				appendToEmergencyAppender(loggingEvent);
			}
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			appendToEmergencyAppender(loggingEvent);
		}
	}

	// not-required, used for external cloud instances only
	public void setEndpointUrl(String endpointUrl) throws URISyntaxException {
		this.endpointUrl = new URI(endpointUrl);
	}

	public void setEndpointUrl(URI endpointUrl) {
		this.endpointUrl = endpointUrl;
	}

	// required
	public void setRegion(String region) {
		this.region = Region.of(region);
	}

	// required
	public void setLogGroup(String logGroupName) {
		this.logGroupName = logGroupName;
	}

	// required
	public void setLogStream(String logStreamName) {
		this.logStreamName = logStreamName;
	}

	// required
	public void setLayout(Layout<ILoggingEvent> layout) {
		this.layout = layout;
	}

	// not-required, default is DEFAULT_MAX_BATCH_SIZE
	public void setMaxBatchSize(int maxBatchSize) {
		this.maxBatchSize = maxBatchSize;
	}

	// not-required, default is DEFAULT_MAX_BATCH_TIME_MILLIS
	public void setMaxBatchTimeMillis(long maxBatchTimeMillis) {
		this.maxBatchTimeMillis = maxBatchTimeMillis;
	}

	// not-required, default is DEFAULT_MAX_QUEUE_WAIT_TIME_MILLIS
	public void setMaxQueueWaitTimeMillis(long maxQueueWaitTimeMillis) {
		this.maxQueueWaitTimeMillis = maxQueueWaitTimeMillis;
	}

	// not-required, default is DEFAULT_INTERNAL_QUEUE_SIZE
	public void setInternalQueueSize(int internalQueueSize) {
		this.internalQueueSize = internalQueueSize;
	}

	// not-required, default is DEFAULT_CREATE_LOG_DESTS
	public void setCreateLogDests(boolean createLogDests) {
		this.createLogDests = createLogDests;
	}

	// not-required, default is 0
	public void setInitialWaitTimeMillis(long initialWaitTimeMillis) {
		this.initialWaitTimeMillis = initialWaitTimeMillis;
	}

	// not required, for testing purposes
	void setAwsLogsClient(CloudWatchLogsClient awsLogsClient) {
		this.awsLogsClient = awsLogsClient;
	}

	// not required, for testing purposes
	void setTestAwsLogsClient(CloudWatchLogsClient testAwsLogsClient) {
		this.testAwsLogsClient = testAwsLogsClient;
	}

	// not required, for testing purposes
	void setTestAmazonEc2Client(Ec2Client testAmazonEc2Client) {
		this.testAmazonEc2Client = testAmazonEc2Client;
	}

	public void setMaxEventMessageSize(int maxEventMessageSize) {
		this.maxEventMessageSize = maxEventMessageSize;
	}

	// not required, default is true
	public void setTruncateEventMessages(boolean truncateEventMessages) {
		this.truncateEventMessages = truncateEventMessages;
	}

	// not required, default is true
	public void setCopyEvents(boolean copyEvents) {
		this.copyEvents = copyEvents;
	}

	// not required, default is false
	public void setPrintRejectedEvents(boolean printRejectedEvents) {
		this.printRejectedEvents = printRejectedEvents;
	}

	/**
	 * Set to true to not try and download EC2 metadata.
	 */
	public void setDisableEc2Metadata(boolean disableEc2Metadata) {
		this.disableEc2Metadata = disableEc2Metadata;
	}

	public void setRetentionDays(int retentionDays) {
		this.retentionDays = retentionDays;
	}

	/**
	 * For testing purposes, set the EC2 service override property to the following hostname. Can be "localhost".
	 */
	public static void setEc2InstanceName(String testInstanceName) {
		InstanceNameConverter.setInstanceName(testInstanceName);
	}

	// for testing purposes
	long getEventsWrittenCount() {
		return eventsWrittenCount;
	}

	// for testing purposes
	boolean isWarningMessagePrinted() {
		return warningMessagePrinted;
	}

	@Override
	public void addAppender(Appender<ILoggingEvent> appender) {
		if (emergencyAppender == null) {
			emergencyAppender = appender;
		} else {
			addWarn("One and only one appender may be attached to " + getClass().getSimpleName());
			addWarn("Ignoring additional appender named [" + appender.getName() + "]");
		}
	}

	@Override
	public Iterator<Appender<ILoggingEvent>> iteratorForAppenders() {
		throw new UnsupportedOperationException("Don't know how to create iterator");
	}

	@Override
	public Appender<ILoggingEvent> getAppender(String name) {
		if (emergencyAppender != null && name != null && name.equals(emergencyAppender.getName())) {
			return emergencyAppender;
		} else {
			return null;
		}
	}

	@Override
	public boolean isAttached(Appender<ILoggingEvent> appender) {
		return (emergencyAppender == appender);
	}

	@Override
	public void detachAndStopAllAppenders() {
		if (emergencyAppender != null) {
			emergencyAppender.stop();
			emergencyAppender = null;
		}
	}

	@Override
	public boolean detachAppender(Appender<ILoggingEvent> appender) {
		if (emergencyAppender == appender) {
			emergencyAppender = null;
			return true;
		} else {
			return false;
		}
	}

	@Override
	public boolean detachAppender(String name) {
		if (emergencyAppender != null && emergencyAppender.getName().equals(name)) {
			emergencyAppender = null;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Copy the event fields possible replacing the message if not null.
	 */
	private LoggingEvent copyEvent(ILoggingEvent loggingEvent, String message) {
		LoggingEvent newEvent = new LoggingEvent();
		newEvent.setArgumentArray(loggingEvent.getArgumentArray());
		newEvent.setLevel(loggingEvent.getLevel());
		newEvent.setLoggerContextRemoteView(loggingEvent.getLoggerContextVO());
		newEvent.setLoggerName(loggingEvent.getLoggerName());
		List<Marker> markers = loggingEvent.getMarkerList();
		if (markers != null && !markers.isEmpty()) {
			for (Marker marker : markers) {
				newEvent.addMarker(marker);
			}
		}
		// newEvent.setMDCPropertyMap(loggingEvent.getMDCPropertyMap());
		if (message == null) {
			newEvent.setMessage(loggingEvent.getMessage());
		} else {
			newEvent.setMessage(message);
		}
		newEvent.setThreadName(loggingEvent.getThreadName());
		IThrowableProxy ithrowableProxy = loggingEvent.getThrowableProxy();
		if (ithrowableProxy instanceof ThrowableProxy) {
			newEvent.setThrowableProxy((ThrowableProxy) ithrowableProxy);
		}
		newEvent.setTimeStamp(loggingEvent.getTimeStamp());
		return newEvent;
	}

	private void appendToEmergencyAppender(ILoggingEvent event) {
		if (emergencyAppender != null) {
			try {
				emergencyAppender.doAppend(event);
				return;
			} catch (Exception outer) {
				// fall through and maybe print below
			}
		}
		if (printRejectedEvents) {
			try {
				System.err.println(getClass().getSimpleName() + " emergency appender didn't handle event: "
						+ layout.doLayout(event));
			} catch (Exception inner) {
				// oh well, we tried
			}
		}
	}

	private void appendToEmergencyAppender(List<ILoggingEvent> events) {
		for (ILoggingEvent event : events) {
			appendToEmergencyAppender(event);
		}
	}

	/**
	 * Background thread that writes the log events to cloudwatch.
	 */
	private class CloudWatchWriter implements Runnable {

		private String sequenceToken;
		private String logStreamName;
		private boolean initialized;

		@Override
		public void run() {

			try {
				Thread.sleep(initialWaitTimeMillis);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			}

			List<ILoggingEvent> events = new ArrayList<ILoggingEvent>(maxBatchSize);
			Thread thread = Thread.currentThread();
			while (!thread.isInterrupted()) {
				long batchTimeout = System.currentTimeMillis() + maxBatchTimeMillis;
				while (!thread.isInterrupted()) {
					long timeoutMillis = batchTimeout - System.currentTimeMillis();
					if (timeoutMillis < 0) {
						break;
					}
					ILoggingEvent loggingEvent;
					try {
						System.out.println("polling for events");
						loggingEvent = loggingEventQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						break;
					}
					if (loggingEvent == null) {
						// wait timed out
						System.out.println("polling for events timed out");
						break;
					}
					events.add(loggingEvent);
					if (events.size() >= maxBatchSize) {
						// batch size exceeded
						break;
					}
				}
				if (!events.isEmpty()) {
					writeEvents(events);
					events.clear();
				}
			}

			/*
			 * We have been interrupted so write all of the rest of the events and then quit
			 */

			events.clear();
			while (true) {
				ILoggingEvent event = loggingEventQueue.poll();
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
			if (!events.isEmpty()) {
				writeEvents(events);
				events.clear();
			}
			// thread quits here
		}

		private void writeEvents(List<ILoggingEvent> events) {

			if (!initialized) {
				initialized = true;
				Exception exception = null;
				try {
					stopMessagesThreadLocal.set(true);
					if (awsLogsClient == null) {
						createLogsClient();
					} else {
						// mostly here for testing
						logStreamName = buildLogStreamName();
					}
				} catch (Exception e) {
					exception = e;
				} finally {
					stopMessagesThreadLocal.set(false);
				}
				if (exception != null) {
					appendEvent(Level.ERROR, "Problems initializing cloudwatch writer", exception);
				}
			}

			// if we didn't get an aws logs-client then just write to the emergency appender (if any)
			if (awsLogsClient == null) {
				appendToEmergencyAppender(events);
				return;
			}

			// we need this in case our RPC calls create log output which we don't want to then log again
			stopMessagesThreadLocal.set(true);
			Exception exception = null;
			try {
				List<InputLogEvent> logEvents = new ArrayList<>(events.size());
				for (ILoggingEvent event : events) {
					String message = layout.doLayout(event);
					InputLogEvent logEvent =
							InputLogEvent.builder().timestamp(event.getTimeStamp()).message(message).build();
					logEvents.add(logEvent);
				}
				// events must be in sorted order according to AWS otherwise an exception is thrown
				Collections.sort(logEvents, inputLogEventComparator);

				for (int i = 0; i < PUT_REQUEST_RETRY_COUNT; i++) {
					try {
						PutLogEventsRequest.Builder builder = PutLogEventsRequest.builder()
								.logGroupName(logGroupName)
								.logStreamName(logStreamName)
								.logEvents(logEvents);
						if (sequenceToken != null) {
							builder.sequenceToken(sequenceToken);
						}
						PutLogEventsResponse result = awsLogsClient.putLogEvents(builder.build());
						sequenceToken = result.nextSequenceToken();
						exception = null;
						eventsWrittenCount += logEvents.size();
						break;
					} catch (InvalidSequenceTokenException iste) {
						exception = iste;
						sequenceToken = iste.expectedSequenceToken();
					}
				}
			} catch (DataAlreadyAcceptedException daac) {
				exception = daac;
				sequenceToken = daac.expectedSequenceToken();
			} catch (Exception e) {
				// catch everything else to make sure we don't quit the thread
				exception = e;
			} finally {
				if (exception != null) {
					// we do this because we don't want to go recursive
					events.add(makeEvent(Level.ERROR,
							"Exception thrown when creating logging " + events.size() + " events", exception));
					appendToEmergencyAppender(events);
				}
				stopMessagesThreadLocal.set(false);
			}
		}

		private void createLogsClient() {
			CloudWatchLogsClient client;
			if (testAwsLogsClient == null) {
				CloudWatchLogsClientBuilder builder = CloudWatchLogsClient.builder().region(region);
				if (endpointUrl != null) {
					builder.endpointOverride(endpointUrl);
				}
				client = builder.build();
			} else {
				client = testAwsLogsClient;
			}
			try {
				assignInstanceName();
			} catch (Exception e) {
				appendEvent(Level.ERROR, "Problems looking up instance-name", e);
			}
			logStreamName = buildLogStreamName();
			verifyLogGroupExists(client);
			verifyLogStreamExists(client);
			awsLogsClient = client;
		}

		private void verifyLogGroupExists(CloudWatchLogsClient client) {
			DescribeLogGroupsRequest describeRequest =
					DescribeLogGroupsRequest.builder().logGroupNamePrefix(logGroupName).build();
			DescribeLogGroupsResponse describeResponse = client.describeLogGroups(describeRequest);
			for (LogGroup group : describeResponse.logGroups()) {
				if (logGroupName.equals(group.logGroupName())) {
					return;
				}
			}
			if (createLogDests) {
				CreateLogGroupRequest createRequest =
						CreateLogGroupRequest.builder().logGroupName(logStreamName).build();
				client.createLogGroup(createRequest);
				if (retentionDays > 0) {
					PutRetentionPolicyRequest retentionRequest = PutRetentionPolicyRequest.builder()
							.logGroupName(logGroupName)
							.retentionInDays(retentionDays)
							.build();
					client.putRetentionPolicy(retentionRequest);
				}
			} else {
				appendEvent(Level.WARN, "Log-group '" + logGroupName + "' doesn't exist and not created", null);
			}
		}

		private void verifyLogStreamExists(CloudWatchLogsClient client) {
			DescribeLogStreamsRequest describeRequest =
					DescribeLogStreamsRequest.builder().logStreamNamePrefix(logStreamName).build();
			DescribeLogStreamsResponse describeResponse = client.describeLogStreams(describeRequest);
			for (LogStream stream : describeResponse.logStreams()) {
				if (logStreamName.equals(stream.logStreamName())) {
					sequenceToken = stream.uploadSequenceToken();
					return;
				}
			}
			if (createLogDests) {
				CreateLogStreamRequest createRequest = CreateLogStreamRequest.builder()
						.logGroupName(logGroupName)
						.logStreamName(logStreamName)
						.build();
				client.createLogStream(createRequest);
			} else {
				appendEvent(Level.WARN, "Log-stream '" + logStreamName + "' doesn't exist and not created", null);
			}
		}

		private String buildLogStreamName() {
			String name = CloudWatchAppender.this.logStreamName;
			if (name.indexOf('%') < 0) {
				return name;
			}
			/*
			 * Little bit of a hack here. We use one of our layout instances to format the _name_ of the log-stream.
			 * This allows us to support the same %token that are supported by the messages.
			 */
			Ec2PatternLayout nameLayout = new Ec2PatternLayout();
			nameLayout.setPattern(name);
			nameLayout.setContext(context);
			nameLayout.start();
			// somewhat random logging event although the time-stamp is important
			LoggingEvent event = new LoggingEvent();
			event.setLevel(Level.INFO);
			event.setLoggerName("logStreamName");
			event.setMessage("log stream name");
			event.setTimeStamp(System.currentTimeMillis());
			name = nameLayout.doLayout(event);
			// replace the only character that cloudwatch barfs on: Member must satisfy regex pattern: [^:*]*
			name = name.replace(':', '_');
			return name;
		}

		private void assignInstanceName() {
			String name = lookupInstanceName();
			if (name != null) {
				InstanceNameConverter.setInstanceName(name);
			}
		}

		private String lookupInstanceName() {
			if (disableEc2Metadata) {
				try {
					return Inet4Address.getLocalHost().getHostName();
				} catch (UnknownHostException e) {
					// oh well we tried
					return null;
				}
			}

			String instanceId = EC2MetadataUtils.getInstanceId();
			if (instanceId == null) {
				return null;
			}
			InstanceIdConverter.setInstanceId(instanceId);
			Ec2Client ec2Client = null;
			try {
				if (testAmazonEc2Client == null) {
					ec2Client = Ec2Client.builder().region(region).build();
				} else {
					ec2Client = testAmazonEc2Client;
				}
				DescribeTagsRequest.Builder builder = DescribeTagsRequest.builder();
				builder.filters(Filter.builder().name("resource-type").values("instance").build(),
						Filter.builder().name("resource-id").values(instanceId).build());
				DescribeTagsResponse result = ec2Client.describeTags(builder.build());
				List<TagDescription> tags = result.tags();
				for (TagDescription tag : tags) {
					if ("Name".equals(tag.key())) {
						return tag.value();
					}
				}
				appendEvent(Level.INFO, "Could not find EC2 instance name in tags: " + tags, null);
			} catch (AwsServiceException ase) {
				appendEvent(Level.WARN, "Looking up EC2 instance-name threw", ase);
			} finally {
				if (ec2Client != null) {
					ec2Client.close();
				}
			}
			return instanceId;
		}

		private void appendEvent(Level level, String message, Throwable th) {
			append(makeEvent(level, message, th));
		}

		private LoggingEvent makeEvent(Level level, String message, Throwable th) {
			LoggingEvent event = new LoggingEvent();
			event.setLoggerName(CloudWatchAppender.class.getName());
			event.setLevel(level);
			event.setMessage(message);
			event.setTimeStamp(System.currentTimeMillis());
			if (th != null) {
				event.setThrowableProxy(new ThrowableProxy(th));
			}
			return event;
		}
	}

	/**
	 * Compares a log event by it's timestamp value.
	 */
	private static class InputLogEventComparator implements Comparator<InputLogEvent> {
		@Override
		public int compare(InputLogEvent o1, InputLogEvent o2) {
			if (o1.timestamp() == null) {
				if (o2.timestamp() == null) {
					return 0;
				} else {
					// null - long
					return -1;
				}
			} else if (o2.timestamp() == null) {
				// long - null
				return 1;
			} else {
				return o1.timestamp().compareTo(o2.timestamp());
			}
		}
	}
}
