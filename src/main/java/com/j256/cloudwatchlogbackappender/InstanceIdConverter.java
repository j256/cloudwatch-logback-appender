package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;

/**
 * Converter which knows about the instance-id.
 * 
 * @author graywatson
 */
public class InstanceIdConverter extends ClassicConverter {

	private static String instanceId;

	@Override
	public String convert(ILoggingEvent event) {
		if (instanceId == null) {
			instanceId = EC2MetadataUtils.getInstanceId();
			if (instanceId == null) {
				instanceId = CloudWatchAppender.UNKNOWN_CLOUD_NAME;
			}
		}
		return instanceId;
	}

	public static void setInstanceId(String instanceId) {
		InstanceIdConverter.instanceId = instanceId;
	}
}
