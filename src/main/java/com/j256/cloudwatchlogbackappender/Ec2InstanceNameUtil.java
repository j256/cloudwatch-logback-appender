package com.j256.cloudwatchlogbackappender;

import java.util.List;

import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.regions.internal.util.EC2MetadataUtils;
import software.amazon.awssdk.services.ec2.Ec2Client;
import software.amazon.awssdk.services.ec2.model.DescribeTagsRequest;
import software.amazon.awssdk.services.ec2.model.DescribeTagsResponse;
import software.amazon.awssdk.services.ec2.model.Filter;
import software.amazon.awssdk.services.ec2.model.TagDescription;

/**
 * This is in its own file so we can do a test load of it and ignore any class-not-found exceptions if the EC2
 * dependency isn't being used.
 * 
 * @author graywatson
 */
public class Ec2InstanceNameUtil {

	private static Ec2Client ec2Client;

	public static String lookupInstanceName() {

		// quick test
		String instanceId = EC2MetadataUtils.getInstanceId();
		if (instanceId == null) {
			return CloudWatchAppender.UNKNOWN_CLOUD_NAME;
		}

		boolean builtClient = false;
		try {
			if (ec2Client == null) {
				ec2Client = Ec2Client.builder().build();
				builtClient = true;
			}
			DescribeTagsRequest.Builder builder = DescribeTagsRequest.builder();
			builder.filters(Filter.builder().name("resource-type").values("instance").build(),
					Filter.builder().name("resource-id").values(instanceId).build());
			DescribeTagsResponse result = ec2Client.describeTags(builder.build());
			List<TagDescription> tags = result.tags();
			for (TagDescription tag : tags) {
				if ("Name".equals(tag.key())) {
					String value = tag.value();
					if (value == null) {
						return CloudWatchAppender.UNKNOWN_CLOUD_NAME;
					} else {
						return value;
					}
				}
			}
			return CloudWatchAppender.UNKNOWN_CLOUD_NAME;
		} catch (AwsServiceException ase) {
			return CloudWatchAppender.UNKNOWN_CLOUD_NAME;
		} finally {
			if (builtClient) {
				ec2Client.close();
			}
		}
	}

	/**
	 * For testing purposes.
	 */
	public static void setEc2Client(Ec2Client ec2Client) {
		Ec2InstanceNameUtil.ec2Client = ec2Client;
	}
}
