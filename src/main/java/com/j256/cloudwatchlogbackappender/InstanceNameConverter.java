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
		return instanceName;
	}

	static void setInstanceName(String instanceName) {
		if (instanceName == null) {
			InstanceNameConverter.instanceName = DEFAULT_INSTANCE_NAME;
		} else {
			InstanceNameConverter.instanceName = instanceName;
		}
	}
}
