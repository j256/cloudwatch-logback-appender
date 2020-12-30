package com.j256.cloudwatchlogbackappender;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;

import org.easymock.IAnswer;
import org.junit.Test;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.Context;
import ch.qos.logback.core.LogbackException;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.Status;

public class CloudWatchAppenderTest {

	private final LoggerContext LOGGER_CONTEXT = new LoggerContext();

	@Test(timeout = 10000)
	public void testBasic() throws InterruptedException {
		CloudWatchAppender appender = new CloudWatchAppender();
		AWSLogsClient awsLogClient = createMock(AWSLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		appender.setMaxBatchSize(1);
		appender.setRegion("region");
		final String logGroup = "pfqoejpfqe";
		appender.setLogGroup(logGroup);
		final String logStream = "pffqjfqjpoqoejpfqe";
		appender.setLogStream(logStream);
		appender.setContext(LOGGER_CONTEXT);
		PatternLayout layout = new PatternLayout();
		layout.setContext(LOGGER_CONTEXT);
		layout.setPattern("[%thread] %level %logger{20} - %msg%n%xThrowable");
		layout.start();
		appender.setLayout(layout);

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		String loggerName = "name";
		event.setLoggerName(loggerName);
		Level level = Level.DEBUG;
		event.setLevel(level);
		String message = "fjpewjfpewjfpewjfepowf";
		event.setMessage(message);

		String threadName = Thread.currentThread().getName();
		final String fullMessage = "[" + threadName + "] " + level + " " + loggerName + " - " + message + "\n";

		final PutLogEventsResult result = new PutLogEventsResult();
		String sequence = "ewopjfewfj";
		result.setNextSequenceToken(sequence);
		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class))).andAnswer(new IAnswer<PutLogEventsResult>() {
			@Override
			public PutLogEventsResult answer() {
				PutLogEventsRequest request = (PutLogEventsRequest) getCurrentArguments()[0];
				assertEquals(logGroup, request.getLogGroupName());
				assertEquals(logStream, request.getLogStreamName());
				List<InputLogEvent> events = request.getLogEvents();
				assertEquals(1, events.size());
				assertEquals(fullMessage, events.get(0).getMessage());
				return result;
			}
		}).times(2);
		awsLogClient.shutdown();

		// =====================================

		replay(awsLogClient);
		appender.start();
		// for coverage
		appender.start();
		appender.append(event);
		Thread.sleep(10);
		appender.append(event);
		while (appender.getEventsWrittenCount() < 2) {
			Thread.sleep(10);
		}
		appender.stop();
		verify(awsLogClient);
	}

	@Test(timeout = 10000)
	public void testEmergencyAppender() throws InterruptedException {
		CloudWatchAppender appender = new CloudWatchAppender();
		appender.setInitialWaitTimeMillis(0);
		AWSLogsClient awsLogClient = createMock(AWSLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		appender.setMaxBatchSize(1);
		appender.setRegion("region");
		final String logGroup = "pfqoejpfqe";
		appender.setLogGroup(logGroup);
		final String logStream = "pffqjfqjpoqoejpfqe";
		appender.setLogStream(logStream);
		appender.setContext(LOGGER_CONTEXT);
		PatternLayout layout = new PatternLayout();
		layout.setContext(LOGGER_CONTEXT);
		layout.setPattern("[%thread] %level %logger{20} - %msg%n%xThrowable");
		layout.start();
		appender.setLayout(layout);

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		final String loggerName = "name";
		event.setLoggerName(loggerName);
		final Level level = Level.DEBUG;
		event.setLevel(level);
		String message = "fjpewjfpewjfpewjfepowf";
		event.setMessage(message);
		final String threadName = Thread.currentThread().getName();

		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class)))
				.andThrow(new RuntimeException("force emergency log"))
				.anyTimes();
		awsLogClient.shutdown();

		// =====================================

		@SuppressWarnings("unchecked")
		Appender<ILoggingEvent> emergencyAppender = (Appender<ILoggingEvent>) createMock(Appender.class);
		String emergencyAppenderName = "fjpeowjfwfewf";
		expect(emergencyAppender.getName()).andReturn(emergencyAppenderName);
		expect(emergencyAppender.isStarted()).andReturn(false);
		emergencyAppender.start();
		emergencyAppender.doAppend(isA(ILoggingEvent.class));
		expectLastCall().andAnswer(new IAnswer<Void>() {
			@Override
			public Void answer() {
				ILoggingEvent event = (ILoggingEvent) getCurrentArguments()[0];
				if (event.getLevel() == level) {
					assertEquals(loggerName, event.getLoggerName());
					assertEquals(threadName, event.getThreadName());
				} else {
					assertEquals(Level.ERROR, event.getLevel());
				}
				return null;
			}
		}).times(2);
		emergencyAppender.stop();

		// =====================================

		replay(awsLogClient, emergencyAppender);
		assertNull(appender.getAppender(emergencyAppenderName));
		assertFalse(appender.isAttached(emergencyAppender));
		appender.addAppender(emergencyAppender);
		assertTrue(appender.isAttached(emergencyAppender));
		assertSame(emergencyAppender, appender.getAppender(emergencyAppenderName));
		assertNull(appender.getAppender(null));
		appender.start();
		// for coverage
		appender.start();
		appender.append(event);
		Thread.sleep(100);
		appender.detachAndStopAllAppenders();
		appender.stop();
		verify(awsLogClient, emergencyAppender);
	}

	@Test(timeout = 10000)
	public void testLogClientFailed() throws InterruptedException {
		CloudWatchAppender appender = new CloudWatchAppender();
		appender.setInitialWaitTimeMillis(0);

		appender.setAccessKeyId("not right");
		appender.setSecretKey("not right");
		appender.setInitialWaitTimeMillis(0);

		appender.setMaxBatchSize(1);
		appender.setRegion("us-east-1");
		final String logGroup = "pfqoejpfqe";
		appender.setLogGroup(logGroup);
		final String logStream = "pffqjfqjpoqoejpfqe";
		appender.setLogStream(logStream);
		appender.setContext(LOGGER_CONTEXT);
		PatternLayout layout = new PatternLayout();
		layout.setContext(LOGGER_CONTEXT);
		layout.setPattern("[%thread] %level %logger{20} - %msg%n%xThrowable");
		layout.start();
		appender.setLayout(layout);
		appender.setContext(LOGGER_CONTEXT);
		appender.start();

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		final String loggerName = "name";
		event.setLoggerName(loggerName);
		final Level level = Level.DEBUG;
		event.setLevel(level);
		String message = "fjpewjfpewjfpewjfepowf";
		event.setMessage(message);

		appender.append(event);
		Thread.sleep(3000);
		assertEquals(0, appender.getEventsWrittenCount());
		appender.stop();
	}

	@Test(timeout = 10000)
	public void testBigMessageTruncate() throws InterruptedException {
		CloudWatchAppender appender = new CloudWatchAppender();
		AWSLogsClient awsLogClient = createMock(AWSLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		appender.setMaxBatchSize(1);
		appender.setRegion("region");
		final String logGroup = "pfqoejpfqe";
		appender.setLogGroup(logGroup);
		final String logStream = "pffqjfqjpoqoejpfqe";
		appender.setLogStream(logStream);
		appender.setContext(LOGGER_CONTEXT);
		PatternLayout layout = new PatternLayout();
		layout.setContext(LOGGER_CONTEXT);
		layout.setPattern("[%thread] %level %logger{20} - %msg%n%xThrowable");
		layout.start();
		appender.setLayout(layout);
		EmergencyAppender emergency = new EmergencyAppender();
		appender.addAppender(emergency);

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		String loggerName = "name";
		event.setLoggerName(loggerName);
		Level level = Level.DEBUG;
		event.setLevel(level);
		String message = "fjpewjfpewjfpewjfepowf";
		event.setMessage(message);

		int maxSize = 10;
		appender.setMaxEventMessageSize(maxSize);

		String threadName = Thread.currentThread().getName();
		final String fullMessage =
				"[" + threadName + "] " + level + " " + loggerName + " - " + message.substring(0, maxSize) + "\n";

		final PutLogEventsResult result = new PutLogEventsResult();
		String sequence = "ewopjfewfj";
		result.setNextSequenceToken(sequence);
		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class))).andAnswer(new IAnswer<PutLogEventsResult>() {
			@Override
			public PutLogEventsResult answer() {
				PutLogEventsRequest request = (PutLogEventsRequest) getCurrentArguments()[0];
				assertEquals(logGroup, request.getLogGroupName());
				assertEquals(logStream, request.getLogStreamName());
				List<InputLogEvent> events = request.getLogEvents();
				assertEquals(1, events.size());
				assertEquals(fullMessage, events.get(0).getMessage());
				return result;
			}
		});
		awsLogClient.shutdown();

		// =====================================

		replay(awsLogClient);
		appender.start();
		// for coverage
		appender.start();
		appender.append(event);
		while (appender.getEventsWrittenCount() < 1) {
			Thread.sleep(10);
		}
		appender.stop();
		verify(awsLogClient);
		assertNull(emergency.event);
	}

	@Test(timeout = 10000)
	public void testBigMessageDrop() throws InterruptedException {
		CloudWatchAppender appender = new CloudWatchAppender();
		AWSLogsClient awsLogClient = createMock(AWSLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		appender.setMaxBatchSize(1);
		appender.setRegion("region");
		final String logGroup = "pfqoejpfqe";
		appender.setLogGroup(logGroup);
		final String logStream = "pffqjfqjpoqoejpfqe";
		appender.setLogStream(logStream);
		appender.setContext(LOGGER_CONTEXT);
		PatternLayout layout = new PatternLayout();
		layout.setContext(LOGGER_CONTEXT);
		layout.setPattern("[%thread] %level %logger{20} - %msg%n%xThrowable");
		layout.start();
		appender.setLayout(layout);
		EmergencyAppender emergency = new EmergencyAppender();
		appender.addAppender(emergency);

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		String loggerName = "name";
		event.setLoggerName(loggerName);
		Level level = Level.DEBUG;
		event.setLevel(level);
		String message = "fjpewjfpewjfpewjfepowf";
		event.setMessage(message);

		int maxSize = 10;
		appender.setMaxEventMessageSize(maxSize);
		appender.setTruncateEventMessages(false);

		final PutLogEventsResult result = new PutLogEventsResult();
		String sequence = "ewopjfewfj";
		result.setNextSequenceToken(sequence);
		awsLogClient.shutdown();

		// =====================================

		replay(awsLogClient);
		appender.start();
		// for coverage
		appender.start();
		appender.append(event);
		while (emergency.event == null) {
			Thread.sleep(10);
		}
		appender.stop();
		verify(awsLogClient);

		assertSame(event, emergency.event);
		assertEquals(0, appender.getEventsWrittenCount());
	}

	@Test(timeout = 10000)
	public void testCoverage() {
		CloudWatchAppender appender = new CloudWatchAppender();
		appender.setInitialWaitTimeMillis(0);
		appender.detachAndStopAllAppenders();
		// stop before starting
		appender.stop();
		assertFalse(appender.isWarningMessagePrinted());
		System.err.println("Expected warning on next line");
		appender.append(null);
		appender.append(null);
		assertTrue(appender.isWarningMessagePrinted());
		try {
			appender.start();
			fail("Should have thrown");
		} catch (IllegalStateException ise) {
			// expected
		}
		appender.setRegion("region");
		try {
			appender.start();
			fail("Should have thrown");
		} catch (IllegalStateException ise) {
			// expected
		}
		appender.setLogGroup("log-group");
		try {
			appender.start();
			fail("Should have thrown");
		} catch (IllegalStateException ise) {
			// expected
		}
		appender.setLogStream("log-group");
		try {
			appender.start();
			fail("Should have thrown");
		} catch (IllegalStateException ise) {
			// expected
		}
		PatternLayout layout = new PatternLayout();
		layout.setContext(LOGGER_CONTEXT);
		layout.setPattern("x");
		layout.start();
		appender.setLayout(layout);
		appender.stop();

		appender.setMaxBatchTimeMillis(1000);
		appender.setMaxQueueWaitTimeMillis(1000);
		appender.setInternalQueueSize(1);
		appender.setCreateLogDests(true);

		try {
			appender.iteratorForAppenders();
			fail("should have thrown");
		} catch (UnsupportedOperationException uoe) {
			// expected
		}

		assertNull(appender.getAppender("foo"));
		assertNull(appender.getAppender(null));
		assertNull(appender.getAppender(EmergencyAppender.NAME));

		// yes we are calling ourselves
		EmergencyAppender nullAppender = new EmergencyAppender();
		assertFalse(appender.detachAppender(nullAppender));
		assertFalse(appender.detachAppender(EmergencyAppender.NAME));
		appender.addAppender(nullAppender);

		assertNull(appender.getAppender("foo"));
		assertNull(appender.getAppender(null));
		assertSame(nullAppender, appender.getAppender(EmergencyAppender.NAME));

		appender.addAppender(new EmergencyAppender());
		assertTrue(appender.detachAppender(nullAppender));
		appender.addAppender(new EmergencyAppender());
		assertFalse(appender.detachAppender("something"));
		assertTrue(appender.detachAppender(EmergencyAppender.NAME));
		assertNull(appender.getAppender(EmergencyAppender.NAME));
	}

	private static class EmergencyAppender implements Appender<ILoggingEvent> {

		public static final String NAME = "emergency";

		public ILoggingEvent event;

		@Override
		public void start() {
		}

		@Override
		public void stop() {
		}

		@Override
		public boolean isStarted() {
			return false;
		}

		@Override
		public void setContext(Context context) {
		}

		@Override
		public Context getContext() {
			return null;
		}

		@Override
		public void addStatus(Status status) {
		}

		@Override
		public void addInfo(String msg) {
		}

		@Override
		public void addInfo(String msg, Throwable ex) {
		}

		@Override
		public void addWarn(String msg) {
		}

		@Override
		public void addWarn(String msg, Throwable ex) {
		}

		@Override
		public void addError(String msg) {
		}

		@Override
		public void addError(String msg, Throwable ex) {
		}

		@Override
		public void addFilter(Filter<ILoggingEvent> newFilter) {
		}

		@Override
		public void clearAllFilters() {
		}

		@Override
		public List<Filter<ILoggingEvent>> getCopyOfAttachedFiltersList() {
			return null;
		}

		@Override
		public FilterReply getFilterChainDecision(ILoggingEvent event) {
			return null;
		}

		@Override
		public String getName() {
			return NAME;
		}

		@Override
		public void doAppend(ILoggingEvent event) throws LogbackException {
			this.event = event;
		}

		@Override
		public void setName(String name) {
		}
	}
}
