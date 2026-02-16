package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter which knows about the instance-name
 * 
 * @author graywatson
 */
public class InstanceNameConverter extends ClassicConverter {

	private static String DEFAULT_INSTANCE_NAME = "unknown";

	private static String instanceName = DEFAULT_INSTANCE_NAME;

	@Override
	public String convert(ILoggingEvent event) {
		if (instanceName == null) {
			return DEFAULT_INSTANCE_NAME;
		} else {
			return instanceName;
		}
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
