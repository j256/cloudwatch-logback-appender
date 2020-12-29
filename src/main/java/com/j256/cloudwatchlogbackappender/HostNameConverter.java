package com.j256.cloudwatchlogbackappender;

import java.net.InetAddress;
import java.net.UnknownHostException;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Get the local hostname returned by InetAddress.getLocalHost().
 * 
 * @author graywatson
 */
public class HostNameConverter extends ClassicConverter {

	@Override
	public String convert(ILoggingEvent event) {
		try {
			return InetAddress.getLocalHost().getHostName();
		} catch (UnknownHostException e) {
			return "unknown";
		}
	}
}
