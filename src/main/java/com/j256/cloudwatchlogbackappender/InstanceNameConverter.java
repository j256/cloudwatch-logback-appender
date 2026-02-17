package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter which knows about the instance-name. This uses the {@link Ec2InstanceNameUtil} which may throw an exception
 * if the EC2 SDK is not on the classpath.
 * 
 * @author graywatson
 */
public class InstanceNameConverter extends ClassicConverter {

	private static String instanceName;

	@Override
	public String convert(ILoggingEvent event) {
		if (instanceName == null) {
			try {
				instanceName = Ec2InstanceNameUtil.lookupInstanceName();
			} catch (Throwable th) {
				// catch throwable here in case the class isn't on the classpath or something
				instanceName = CloudWatchAppender.UNKNOWN_CLOUD_NAME;
			}
		}
		return instanceName;
	}

	public static String getInstanceName() {
		return instanceName;
	}

	/**
	 * Set the instance name which could be ECS task-id or EC2 name or ...
	 */
	public static void setInstanceName(String instanceName) {
		InstanceNameConverter.instanceName = instanceName;
	}
}
