package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.LoggerContext;

public abstract class BaseConverterTest {

	protected final String LOG_GROUP = "pfqoejpfqe";
	protected final LoggerContext LOGGER_CONTEXT = new LoggerContext();
	protected final CloudWatchAppender appender = new CloudWatchAppender();

	{
		appender.setMaxBatchSize(1);
		appender.setMaxBatchTimeMillis(0);
		appender.setRegion("region");
		appender.setContext(LOGGER_CONTEXT);
		appender.setLogGroup(LOG_GROUP);
		appender.setInitialWaitTimeMillis(0);
	}
}
