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

public class CloudWatchAppenderTest {

	@Test(timeout = 5000)
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
		PatternLayout layout = new PatternLayout();
		layout.setContext(new LoggerContext());
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

	@Test(timeout = 5000)
	public void testEmergencyAppender() throws InterruptedException {
		CloudWatchAppender appender = new CloudWatchAppender();
		AWSLogsClient awsLogClient = createMock(AWSLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		appender.setMaxBatchSize(1);
		appender.setRegion("region");
		final String logGroup = "pfqoejpfqe";
		appender.setLogGroup(logGroup);
		final String logStream = "pffqjfqjpoqoejpfqe";
		appender.setLogStream(logStream);
		PatternLayout layout = new PatternLayout();
		layout.setContext(new LoggerContext());
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
				.andThrow(new RuntimeException("force emergency log")).anyTimes();
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

	@Test(timeout = 5000)
	public void testCoverage() {
		CloudWatchAppender appender = new CloudWatchAppender();
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
	}

	@Test(timeout = 5000)
	public void testInstanceName() throws InterruptedException {
		CloudWatchAppender appender = new CloudWatchAppender();
		AWSLogsClient awsLogClient = createMock(AWSLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		appender.setMaxBatchSize(1);
		appender.setRegion("region");
		final String logGroup = "pfqoejpfqe";
		appender.setLogGroup(logGroup);
		String prefix = "logstream-";
		appender.setLogStream(prefix + "%instanceName");
		final String expectedLogStream = prefix + "unknown";
		PatternLayout layout = new PatternLayout();
		layout.setPattern("%msg");
		layout.start();
		appender.setLayout(layout);

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		event.setLoggerName("name");
		event.setLevel(Level.DEBUG);
		event.setMessage("message");

		final PutLogEventsResult result = new PutLogEventsResult();
		result.setNextSequenceToken("ewopjfewfj");
		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class))).andAnswer(new IAnswer<PutLogEventsResult>() {
			@Override
			public PutLogEventsResult answer() {
				PutLogEventsRequest request = (PutLogEventsRequest) getCurrentArguments()[0];
				assertEquals(logGroup, request.getLogGroupName());
				assertEquals(expectedLogStream, request.getLogStreamName());
				return result;
			}
		});
		awsLogClient.shutdown();

		// =====================================

		replay(awsLogClient);
		appender.start();
		appender.append(event);
		while (appender.getEventsWrittenCount() < 1) {
			Thread.sleep(10);
		}
		appender.stop();
		verify(awsLogClient);
	}
}
