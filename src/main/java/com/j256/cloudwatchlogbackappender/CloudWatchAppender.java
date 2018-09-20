package com.j256.cloudwatchlogbackappender;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.internal.StaticCredentialsProvider;
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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Layout;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import org.slf4j.MDC;

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
	private static final String AWS_ACCESS_KEY_ID_PROPERTY = "cloudwatchappender.aws.accessKeyId";
	/** property looked for to find the aws secret-key */
	private static final String AWS_SECRET_KEY_PROPERTY = "cloudwatchappender.aws.secretKey";

	private String accessKeyId;
	private String secretKey;
	private String region;
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

	private AWSLogsClient awsLogsClient;
	private long eventsWrittenCount;

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
		if (MiscUtils.isBlank(region)) {
			throw new IllegalStateException("Region not set or invalid for appender: " + region);
		}
		if (MiscUtils.isBlank(logGroupName)) {
			throw new IllegalStateException("Log group name not set or invalid for appender: " + logGroupName);
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
			awsLogsClient.shutdown();
			awsLogsClient = null;
		}

		super.stop();
	}

	@Override
	protected void append(ILoggingEvent loggingEvent) {

		// check wiring
		if (loggingEventQueue == null) {
			if (!warningMessagePrinted) {
				System.err.println(getClass().getSimpleName() + " not wired correctly, ignoring all log messages");
				warningMessagePrinted = true;
			}
			return;
		}

		// skip it if we just went recursive
		Boolean stopped = stopMessagesThreadLocal.get();
		if (stopped == null || !stopped) {
			try {
				if (loggingEvent instanceof LoggingEvent) {
					LoggingEvent le = (LoggingEvent) loggingEvent;
					if (le.getThreadName() == null) {
						// we need to do this so that the right thread gets set in the event
						((LoggingEvent) loggingEvent).setThreadName(Thread.currentThread().getName());
					}
					le.setMDCPropertyMap(MDC.getCopyOfContextMap());
				}
				if (!loggingEventQueue.offer(loggingEvent, maxQueueWaitTimeMillis, TimeUnit.MILLISECONDS)) {
					if (emergencyAppender != null) {
						emergencyAppender.doAppend(loggingEvent);
					}
				}
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		}
	}

	// not-required, default is to use the DefaultAWSCredentialsProviderChain
	public void setAccessKeyId(String accessKeyId) {
		this.accessKeyId = accessKeyId;
	}

	// not-required, default is to use the DefaultAWSCredentialsProviderChain
	public void setSecretKey(String secretKey) {
		this.secretKey = secretKey;
	}

	// required
	public void setRegion(String region) {
		this.region = region;
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
	void setAwsLogsClient(AWSLogsClient awsLogsClient) {
		this.awsLogsClient = awsLogsClient;
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
	 * Background thread that writes the log events to cloudwatch.
	 */
	private class CloudWatchWriter implements Runnable {

		private String sequenceToken;
		private String instanceId = "unknown";
		private String instanceName = "unknown";
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
						loggingEvent = loggingEventQueue.poll(timeoutMillis, TimeUnit.MILLISECONDS);
					} catch (InterruptedException ex) {
						Thread.currentThread().interrupt();
						break;
					}
					if (loggingEvent == null) {
						// wait timed out
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

			// now clear the queue and write all the rest
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
					logError("Problems initializing cloudwatch writer", exception);
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
				List<InputLogEvent> logEvents = new ArrayList<InputLogEvent>(events.size());
				for (ILoggingEvent event : events) {
					String message = layout.doLayout(event);
					InputLogEvent logEvent =
							new InputLogEvent().withTimestamp(event.getTimeStamp()).withMessage(message);
					logEvents.add(logEvent);
				}
				// events must be in sorted order according to AWS otherwise an exception is thrown
				Collections.sort(logEvents, inputLogEventComparator);

				for (int i = 0; i < PUT_REQUEST_RETRY_COUNT; i++) {
					try {
						PutLogEventsRequest request = new PutLogEventsRequest(logGroupName, logStreamName, logEvents);
						if (sequenceToken != null) {
							request.withSequenceToken(sequenceToken);
						}
						PutLogEventsResult result = awsLogsClient.putLogEvents(request);
						sequenceToken = result.getNextSequenceToken();
						exception = null;
						eventsWrittenCount += logEvents.size();
						break;
					} catch (InvalidSequenceTokenException iste) {
						exception = iste;
						sequenceToken = iste.getExpectedSequenceToken();
					}
				}
			} catch (DataAlreadyAcceptedException daac) {
				exception = daac;
				sequenceToken = daac.getExpectedSequenceToken();
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

		private void appendToEmergencyAppender(List<ILoggingEvent> events) {
			if (emergencyAppender != null) {
				try {
					for (ILoggingEvent event : events) {
						emergencyAppender.doAppend(event);
					}
				} catch (Exception e) {
					// oh well, we tried
				}
			}
		}

		private void createLogsClient() {
			AWSCredentialsProvider credentialProvider;
			if (MiscUtils.isBlank(accessKeyId)) {
				// try to use our class properties
				accessKeyId = System.getProperty(AWS_ACCESS_KEY_ID_PROPERTY);
				secretKey = System.getProperty(AWS_SECRET_KEY_PROPERTY);
			}
			if (MiscUtils.isBlank(accessKeyId)) {
				// if we are still blank then use the default credentials provider
				credentialProvider = new DefaultAWSCredentialsProviderChain();
			} else {
				credentialProvider = new StaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretKey));
			}
			awsLogsClient = new AWSLogsClient(credentialProvider);
			awsLogsClient.setRegion(RegionUtils.getRegion(region));
			lookupInstanceName(credentialProvider);
			logStreamName = buildLogStreamName();
			verifyLogGroupExists();
			verifyLogStreamExists();
		}

		private void verifyLogGroupExists() {
			DescribeLogGroupsRequest request = new DescribeLogGroupsRequest().withLogGroupNamePrefix(logGroupName);
			DescribeLogGroupsResult result = awsLogsClient.describeLogGroups(request);
			for (LogGroup group : result.getLogGroups()) {
				if (logGroupName.equals(group.getLogGroupName())) {
					return;
				}
			}
			if (createLogDests) {
				callLogClientMethod("createLogGroup", new CreateLogGroupRequest(logGroupName));
			} else {
				logWarn("Log-group '" + logGroupName + "' doesn't exist and not created", null);
			}
		}

		private void verifyLogStreamExists() {
			DescribeLogStreamsRequest request = new DescribeLogStreamsRequest().withLogGroupName(logGroupName)
					.withLogStreamNamePrefix(logStreamName);
			DescribeLogStreamsResult result = awsLogsClient.describeLogStreams(request);
			for (LogStream stream : result.getLogStreams()) {
				if (logStreamName.equals(stream.getLogStreamName())) {
					sequenceToken = stream.getUploadSequenceToken();
					return;
				}
			}
			if (createLogDests) {
				callLogClientMethod("createLogStream", new CreateLogStreamRequest(logGroupName, logStreamName));
			} else {
				logWarn("Log-stream '" + logStreamName + "' doesn't exist and not created", null);
			}
		}

		private String buildLogStreamName() {
			String name = CloudWatchAppender.this.logStreamName;
			if (name.indexOf('%') < 0) {
				return name;
			}
			StringBuilder sb = new StringBuilder();
			// NOTE: larger strings should be earlier in the array
			String[][] patternValues = new String[][] { { "{instanceName}", instanceName }, //
					{ "instanceName", instanceName }, //
					{ "{instanceId}", instanceId }, //
					{ "instanceId", instanceId }, //
					{ "{instance}", instanceName }, //
					{ "instance", instanceName }, //
					{ "{iid}", instanceId }, //
					{ "iid", instanceId }, //
					{ "{in}", instanceName }, //
					{ "in", instanceName }, //
			};
			// go through the name looking for %pattern that we can expand them
			OUTER: for (int i = 0; i < name.length();) {
				char ch = name.charAt(i);
				i++;
				if (ch != '%') {
					sb.append(ch);
					continue;
				}
				// run through pattern-values looking to see if the pattern is at this location, then insert value
				for (String[] patternValue : patternValues) {
					String pattern = patternValue[0];
					if (isSubstringAtPosition(name, i, pattern)) {
						sb.append(patternValue[1]);
						i += pattern.length();
						continue OUTER;
					}
				}
				sb.append(ch);
			}
			return sb.toString();
		}

		private boolean isSubstringAtPosition(CharSequence cs, int pos, CharSequence substring) {
			if (cs == null || cs.length() == 0) {
				return false;
			}
			int max = pos + substring.length();
			if (cs.length() < max) {
				return false;
			} else {
				return cs.subSequence(pos, max).equals(substring);
			}
		}

		/**
		 * This is a hack to work around the problems that were introduced when the appender was compiled with AWS SDK
		 * version 1.9 or 1.10 but the user was running with version 1.11.
		 * 
		 * The problem was that the createLogStream() method added a return object somewhere between 1.10 and 1.11 which
		 * broke backwards compatibility and the applications would throw NoSuchMethodError. Using reflection causes the
		 * linkage to be weaker and seems to work.
		 */
		private void callLogClientMethod(String methodName, AmazonWebServiceRequest arg) {
			try {
				Method method = awsLogsClient.getClass().getMethod(methodName, arg.getClass());
				method.invoke(awsLogsClient, arg);
				logInfo("Created: " + arg);
			} catch (Exception e) {
				logError("Problems creating: " + arg, e);
			}
		}

		private void lookupInstanceName(AWSCredentialsProvider credentialProvider) {
			instanceId = EC2MetadataUtils.getInstanceId();
			if (instanceId == null) {
				return;
			}
			Ec2InstanceIdConverter.setInstanceId(instanceId);
			AmazonEC2Client ec2Client = null;
			try {
				ec2Client = new AmazonEC2Client(credentialProvider);
				ec2Client.setRegion(RegionUtils.getRegion(region));
				DescribeTagsRequest request = new DescribeTagsRequest();
				request.setFilters(Arrays.asList(new Filter("resource-type").withValues("instance"),
						new Filter("resource-id").withValues(instanceId)));
				DescribeTagsResult result = ec2Client.describeTags(request);
				List<TagDescription> tags = result.getTags();
				for (TagDescription tag : tags) {
					if ("Name".equals(tag.getKey())) {
						instanceName = tag.getValue();
						Ec2InstanceNameConverter.setInstanceName(instanceName);
						return;
					}
				}
				logInfo("Could not find EC2 instance name in tags: " + tags);
			} catch (AmazonServiceException ase) {
				logWarn("Looking up EC2 instance-name threw", ase);
			} finally {
				if (ec2Client != null) {
					ec2Client.shutdown();
				}
			}
			// if we can't lookup the instance name then set it as the instance-id
			Ec2InstanceNameConverter.setInstanceName(instanceId);
		}

		private void logInfo(String message) {
			appendEvent(Level.INFO, message, null);
		}

		private void logWarn(String message, Throwable th) {
			appendEvent(Level.WARN, message, th);
		}

		private void logError(String message, Throwable th) {
			appendEvent(Level.ERROR, message, th);
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
			if (o1.getTimestamp() == null) {
				if (o2.getTimestamp() == null) {
					return 0;
				} else {
					// null - long
					return -1;
				}
			} else if (o2.getTimestamp() == null) {
				// long - null
				return 1;
			} else {
				return o1.getTimestamp().compareTo(o2.getTimestamp());
			}
		}
	}
}
