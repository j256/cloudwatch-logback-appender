package com.j256.cloudwatchlogbackappender;

import java.net.InetAddress;
import java.net.UnknownHostException;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Get the local host ip-address returned by InetAddress.getLocalHost().
 * 
 * @author graywatson
 */
public class HostAddressConverter extends ClassicConverter {

	@Override
	public String convert(ILoggingEvent event) {
		try {
			return InetAddress.getLocalHost().getHostAddress();
		} catch (UnknownHostException e) {
			return "unknown";
		}
	}
}
