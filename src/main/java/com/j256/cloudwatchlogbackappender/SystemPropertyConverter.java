package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Get the value of a system environment variable, the name of which is the {option}.
 * 
 * @author graywatson
 */
public class SystemPropertyConverter extends ClassicConverter {

	private String propertyName;

	@Override
	public void start() {
		super.start();
		propertyName = getFirstOption();
	}

	@Override
	public String convert(ILoggingEvent event) {
		if (propertyName == null) {
			return "null";
		} else {
			return System.getProperty(propertyName, "null");
		}
	}
}
