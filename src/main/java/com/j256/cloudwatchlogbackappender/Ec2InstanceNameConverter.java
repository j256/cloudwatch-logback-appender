package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter which knows about the instance-name
 * 
 * @author graywatson
 */
public class Ec2InstanceNameConverter extends ClassicConverter {

	private static String instanceName = "unknown";

	@Override
	public String convert(ILoggingEvent event) {
		return instanceName;
	}

	static void setInstanceName(String instanceName) {
		Ec2InstanceNameConverter.instanceName = instanceName;
	}
}
