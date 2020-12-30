package com.j256.cloudwatchlogbackappender;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CloudWatchAppenderRealTest {

	@Ignore
	@Test
	public void test() throws InterruptedException {
		Logger logger = LoggerFactory.getLogger(getClass());
		logger.info("testing stuff");
		logger.error("Here's a throw", new RuntimeException(new Exception("test exception here")));
		Thread.sleep(1000);
		logger.info("more stuff");
		Thread.sleep(10000);
	}
}
