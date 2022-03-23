package com.j256.cloudwatchlogbackappender;

import ch.qos.logback.classic.PatternLayout;

/**
 * Extension of the pattern layout which handles some replacements specific to EC2. It replaces "%instance",
 * "%instanceName", and "%in" with the instance name. It also replaces "%instanceId" and "%iid" with the instance-id.
 *
 * @author graywatson
 */
public class Ec2PatternLayout extends PatternLayout {

	{
		DEFAULT_CONVERTER_MAP.put("instance", Ec2InstanceNameConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("instanceName", Ec2InstanceNameConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("in", Ec2InstanceNameConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("instanceId", Ec2InstanceIdConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("iid", Ec2InstanceIdConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("uuid", UuidConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("hostName", HostNameConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("host", HostNameConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("hostAddress", HostAddressConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("address", HostAddressConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("addr", HostAddressConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("systemProperty", SystemPropertyConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("property", SystemPropertyConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("prop", SystemPropertyConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("systemEnviron", SystemEnvironConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("environ", SystemEnvironConverter.class.getName());
		DEFAULT_CONVERTER_MAP.put("env", SystemEnvironConverter.class.getName());
	}
}
