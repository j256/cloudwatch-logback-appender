package com.j256.cloudwatchlogbackappender;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudWatchAppenderTest {

	@Ignore("Integration test to test going to cloudwatch")
	@Test
	public void test() throws InterruptedException {
		Logger logger = LoggerFactory.getLogger(getClass());
		logger.info("testing stuff");
		logger.error("Here's a throw", new Exception("test exception here"));
		Thread.sleep(10000000);
	}
}
