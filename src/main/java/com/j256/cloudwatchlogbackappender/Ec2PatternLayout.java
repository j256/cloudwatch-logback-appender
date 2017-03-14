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
	}
}
