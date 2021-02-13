package com.j256.cloudwatchlogbackappender;

import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.amazonaws.SDKGlobalConfiguration;

@Ignore("for integration testing")
public class CloudWatchAppenderRealTest {

	static {
		System.setProperty(SDKGlobalConfiguration.EC2_METADATA_SERVICE_OVERRIDE_SYSTEM_PROPERTY,
				"http://bad-address.j256.com");
	}

	@Test
	public void testStuff() throws InterruptedException {
		Logger logger = LoggerFactory.getLogger(getClass());
		logger.info("testing stuff");
		logger.error("Here's a throw", new RuntimeException(new Exception("test exception here")));
		Thread.sleep(1000);
		logger.info("more stuff");
		Thread.sleep(10000000);
	}
}
