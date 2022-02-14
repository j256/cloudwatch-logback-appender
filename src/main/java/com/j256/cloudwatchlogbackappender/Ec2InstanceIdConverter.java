package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter which knows about the instance-id.
 * 
 * @author graywatson
 */
public class Ec2InstanceIdConverter extends ClassicConverter {

	private static final String DEFAULT_INSTANCE_ID = "unknown";

	private static String instanceId = DEFAULT_INSTANCE_ID;

	@Override
	public String convert(ILoggingEvent event) {
		return instanceId;
	}

	static void setInstanceId(String instanceId) {
		if (instanceId == null) {
			Ec2InstanceIdConverter.instanceId = DEFAULT_INSTANCE_ID;
		} else {
			Ec2InstanceIdConverter.instanceId = instanceId;
		}
	}
}
