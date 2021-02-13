package com.j256.cloudwatchlogbackappender;

import com.amazonaws.util.EC2MetadataUtils;

/**
 * Class so we can mock out the ec2 stuff if running remotely.
 * 
 * @author graywatson
 */
public class Ec2MetadataUtilsClient {

	private static String instanceId;
	private static boolean returnNull;

	public static String lookupInstanceId() {
		if (returnNull) {
			return null;
		} else if (instanceId == null) {
			instanceId = EC2MetadataUtils.getInstanceId();
		}
		return instanceId;
	}

	/**
	 * For testing purposes.
	 */
	public static void setReturnNull(boolean returnNull) {
		Ec2MetadataUtilsClient.returnNull = returnNull;
	}
}
