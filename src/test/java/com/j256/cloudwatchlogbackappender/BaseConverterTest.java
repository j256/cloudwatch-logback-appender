package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.LoggerContext;

public abstract class BaseConverterTest {

	protected static final String LOG_GROUP_NAME = "test-appender-junit";
	protected final LoggerContext LOGGER_CONTEXT = new LoggerContext();
	protected final CloudWatchAppender appender = new CloudWatchAppender();

	{
		appender.setMaxBatchSize(1);
		appender.setMaxBatchTimeMillis(0);
		appender.setContext(LOGGER_CONTEXT);
		appender.setLogGroup(LOG_GROUP_NAME);
		appender.setInitialWaitTimeMillis(0);
	}
}
