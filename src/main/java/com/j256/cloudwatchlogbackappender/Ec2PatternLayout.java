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
		defaultConverterMap.put("instance", Ec2InstanceNameConverter.class.getName());
		defaultConverterMap.put("instanceName", Ec2InstanceNameConverter.class.getName());
		defaultConverterMap.put("in", Ec2InstanceNameConverter.class.getName());
		defaultConverterMap.put("instanceId", Ec2InstanceIdConverter.class.getName());
		defaultConverterMap.put("iid", Ec2InstanceIdConverter.class.getName());
		defaultConverterMap.put("uuid", UuidConverter.class.getName());
		defaultConverterMap.put("hostName", HostNameConverter.class.getName());
		defaultConverterMap.put("host", HostNameConverter.class.getName());
		defaultConverterMap.put("hostAddress", HostAddressConverter.class.getName());
		defaultConverterMap.put("address", HostAddressConverter.class.getName());
		defaultConverterMap.put("addr", HostAddressConverter.class.getName());
		defaultConverterMap.put("systemProperty", SystemPropertyConverter.class.getName());
		defaultConverterMap.put("property", SystemPropertyConverter.class.getName());
		defaultConverterMap.put("prop", SystemPropertyConverter.class.getName());
		defaultConverterMap.put("systemEnviron", SystemEnvironConverter.class.getName());
		defaultConverterMap.put("environ", SystemEnvironConverter.class.getName());
		defaultConverterMap.put("env", SystemEnvironConverter.class.getName());
	}
}
