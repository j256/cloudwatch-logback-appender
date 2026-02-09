package com.j256.cloudwatchlogbackappender;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import org.easymock.IAnswer;
import org.junit.Test;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;
import software.amazon.awssdk.services.cloudwatchlogs.CloudWatchLogsClient;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsRequest;
import software.amazon.awssdk.services.cloudwatchlogs.model.PutLogEventsResponse;

public class SystemPropertyConverterTest extends BaseConverterTest {

	@Test(timeout = 5000)
	public void testStuff() throws InterruptedException {

		String propName = "os.version";
		String propValue = System.getProperty(propName);
		assertNotNull(propValue);

		CloudWatchLogsClient awsLogClient = createMock(CloudWatchLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		String prefix = "logstream-";
		appender.setLogStream(prefix + "%systemProperty{" + propName + "}");
		final String expectedLogStream = prefix + propValue;
		PatternLayout layout = new PatternLayout();
		layout.setPattern("%msg");
		layout.setContext(LOGGER_CONTEXT);
		layout.start();
		appender.setLayout(layout);

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		event.setLoggerName("name");
		event.setLevel(Level.DEBUG);
		event.setMessage("message");

		String sequence = "ewopjfewfj";
		final PutLogEventsResponse result =  PutLogEventsResponse.builder().nextSequenceToken(sequence).build();
		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class))).andAnswer(new IAnswer<PutLogEventsResponse>() {
			@Override
			public PutLogEventsResponse answer() {
				PutLogEventsRequest request = (PutLogEventsRequest) getCurrentArguments()[0];
				assertEquals(LOG_GROUP, request.logGroupName());
				assertEquals(expectedLogStream, request.logStreamName());
				return result;
			}
		});
		awsLogClient.close();

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

	@Test(timeout = 5000)
	public void testNoEnvNameSpecified() throws InterruptedException {

		CloudWatchLogsClient awsLogClient = createMock(CloudWatchLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		String prefix = "logstream-";
		appender.setLogStream(prefix + "%systemProperty");
		final String expectedLogStream = prefix + null;
		PatternLayout layout = new PatternLayout();
		layout.setPattern("%msg");
		layout.setContext(LOGGER_CONTEXT);
		layout.start();
		appender.setLayout(layout);

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		event.setLoggerName("name");
		event.setLevel(Level.DEBUG);
		event.setMessage("message");

		String sequence = "ewopjfewfj";
		final PutLogEventsResponse result =  PutLogEventsResponse.builder().nextSequenceToken(sequence).build();
		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class))).andAnswer(new IAnswer<PutLogEventsResponse>() {
			@Override
			public PutLogEventsResponse answer() {
				PutLogEventsRequest request = (PutLogEventsRequest) getCurrentArguments()[0];
				assertEquals(LOG_GROUP, request.logGroupName());
				assertEquals(expectedLogStream, request.logStreamName());
				return result;
			}
		});
		awsLogClient.close();

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

	@Test(timeout = 5000)
	public void testUnknownEnvNameSpecified() throws InterruptedException {

		CloudWatchLogsClient awsLogClient = createMock(CloudWatchLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		String prefix = "logstream-";
		appender.setLogStream(prefix + "%systemProperty{NOTKNOWNPROPERTY}");
		final String expectedLogStream = prefix + null;
		PatternLayout layout = new PatternLayout();
		layout.setPattern("%msg");
		layout.setContext(LOGGER_CONTEXT);
		layout.start();
		appender.setLayout(layout);

		LoggingEvent event = new LoggingEvent();
		event.setTimeStamp(System.currentTimeMillis());
		event.setLoggerName("name");
		event.setLevel(Level.DEBUG);
		event.setMessage("message");

		String sequence = "ewopjfewfj";
		final PutLogEventsResponse result =  PutLogEventsResponse.builder().nextSequenceToken(sequence).build();
		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class))).andAnswer(new IAnswer<PutLogEventsResponse>() {
			@Override
			public PutLogEventsResponse answer() {
				PutLogEventsRequest request = (PutLogEventsRequest) getCurrentArguments()[0];
				assertEquals(LOG_GROUP, request.logGroupName());
				assertEquals(expectedLogStream, request.logStreamName());
				return result;
			}
		});
		awsLogClient.close();

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
