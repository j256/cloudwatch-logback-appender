package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter which knows about the instance-id.
 * 
 * @author graywatson
 */
public class Ec2InstanceIdConverter extends ClassicConverter {

	private static String instanceId = "unknown";

	@Override
	public String convert(ILoggingEvent event) {
		return instanceId;
	}

	static void setInstanceId(String instanceId) {
		Ec2InstanceIdConverter.instanceId = instanceId;
	}
}
