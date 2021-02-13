package com.j256.cloudwatchlogbackappender;

import static org.junit.Assert.*;

import org.junit.Test;

public class Ec2MetadataUtilsClientTest {

	@Test
	public void testStuff() {
		String id = "hello";
		Ec2MetadataUtilsClient.setInstanceId(id);
		assertEquals(id, Ec2MetadataUtilsClient.lookupInstanceId());
		Ec2MetadataUtilsClient.setReturnNull(true);
		assertNull(Ec2MetadataUtilsClient.lookupInstanceId());
	}
}
