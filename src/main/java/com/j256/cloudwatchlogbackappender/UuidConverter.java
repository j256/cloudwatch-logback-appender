package com.j256.cloudwatchlogbackappender;

import java.util.UUID;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter which returns a random UUID string for this JVM.
 * 
 * @author graywatson
 */
public class UuidConverter extends ClassicConverter {

	private static String UUID_STRING = UUID.randomUUID().toString();

	@Override
	public String convert(ILoggingEvent event) {
		return UUID_STRING;
	}

	/*
	 * For testing purposes.
	 */
	static void setUuidString(String uuidString) {
		UuidConverter.UUID_STRING = uuidString;
	}
}
