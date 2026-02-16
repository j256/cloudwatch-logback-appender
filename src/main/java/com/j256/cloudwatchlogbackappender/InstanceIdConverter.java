package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter which knows about the instance-id.
 * 
 * @author graywatson
 */
public class InstanceIdConverter extends ClassicConverter {

	private static String DEFAULT_INSTANCE_ID = "unknown";

	private static String instanceId = DEFAULT_INSTANCE_ID;

	@Override
	public String convert(ILoggingEvent event) {
		if (instanceId == null) {
			return DEFAULT_INSTANCE_ID;
		} else {
			return instanceId;
		}
	}

	public static String getInstanceId() {
		return instanceId;
	}

	/**
	 * Set the instance id which could be ECS task-id or EC2 instance-id or ...
	 */
	public static void setInstanceId(String instanceId) {
		InstanceIdConverter.instanceId = instanceId;
	}
}
