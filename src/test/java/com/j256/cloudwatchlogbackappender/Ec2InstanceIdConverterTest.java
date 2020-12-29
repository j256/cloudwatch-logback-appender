package com.j256.cloudwatchlogbackappender;

import static org.easymock.EasyMock.createMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.getCurrentArguments;
import static org.easymock.EasyMock.isA;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.*;

import org.easymock.IAnswer;
import org.junit.Test;

import com.amazonaws.services.logs.AWSLogsClient;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.logs.model.PutLogEventsResult;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.spi.LoggingEvent;

public class Ec2InstanceIdConverterTest extends BaseConverterTest {

	@Test(timeout = 5000)
	public void testInstanceId() throws InterruptedException {

		String instanceId = "fewhwehpewpf";
		Ec2InstanceIdConverter.setInstanceId(instanceId);

		AWSLogsClient awsLogClient = createMock(AWSLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		String prefix = "logstream-";
		appender.setLogStream(prefix + "%instanceId");
		final String expectedLogStream = prefix + instanceId;
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

		final PutLogEventsResult result = new PutLogEventsResult();
		result.setNextSequenceToken("ewopjfewfj");
		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class))).andAnswer(new IAnswer<PutLogEventsResult>() {
			@Override
			public PutLogEventsResult answer() {
				PutLogEventsRequest request = (PutLogEventsRequest) getCurrentArguments()[0];
				assertEquals(LOG_GROUP, request.getLogGroupName());
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

	@Test(timeout = 5000)
	public void testInstanceNameUnknown() throws InterruptedException {

		Ec2InstanceIdConverter.setInstanceId(null);

		AWSLogsClient awsLogClient = createMock(AWSLogsClient.class);
		appender.setAwsLogsClient(awsLogClient);

		String prefix = "logstream-";
		appender.setLogStream(prefix + "%instanceId");
		final String expectedLogStream = prefix + "unknown";
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

		final PutLogEventsResult result = new PutLogEventsResult();
		result.setNextSequenceToken("ewopjfewfj");
		expect(awsLogClient.putLogEvents(isA(PutLogEventsRequest.class))).andAnswer(new IAnswer<PutLogEventsResult>() {
			@Override
			public PutLogEventsResult answer() {
				PutLogEventsRequest request = (PutLogEventsRequest) getCurrentArguments()[0];
				assertEquals(LOG_GROUP, request.getLogGroupName());
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
