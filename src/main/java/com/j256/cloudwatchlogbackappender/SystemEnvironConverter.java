package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Get the value of a system property, the name of which is the {option}.
 * 
 * @author graywatson
 */
public class SystemEnvironConverter extends ClassicConverter {

	private String variableName;

	@Override
	public void start() {
		super.start();
		variableName = getFirstOption();
	}

	@Override
	public String convert(ILoggingEvent event) {
		if (variableName == null) {
			return "null";
		}
		String value = System.getenv(variableName);
		if (value == null) {
			return "null";
		} else {
			return value;
		}
	}
}
