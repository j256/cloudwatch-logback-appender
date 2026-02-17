package com.j256.cloudwatchlogbackappender;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MiscUtilsTest {

	@Test
	public void testIsBlank() {
		assertTrue(MiscUtils.isBlank(null));
		assertTrue(MiscUtils.isBlank(""));
		assertTrue(MiscUtils.isBlank(" "));
		assertFalse(MiscUtils.isBlank("s"));
		// coverage
		new MiscUtils();
	}
}
