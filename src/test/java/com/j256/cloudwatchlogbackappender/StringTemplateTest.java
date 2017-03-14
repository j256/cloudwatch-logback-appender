package com.j256.cloudwatchlogbackappender;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.junit.Test;

public class StringTemplateTest {

	@Test(expected = IllegalArgumentException.class)
	public void testStringTemplateNullTemplateString() {
		new StringTemplate((String) null, "{", "}");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringTemplateNullPrefix() {
		new StringTemplate("template", null, "}");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringTemplateNullSuffix() {
		new StringTemplate("template", "{", null);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringTemplateEmptyPrefix() {
		new StringTemplate("template", "", "}");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testStringTemplateEmptySuffix() {
		new StringTemplate("template", "{", "");
	}

	@Test
	public void testTemplateStringConstructor() {
		testOne("hello ", "x", " there", "bob");
	}

	@Test
	public void testParamAtStart() {
		testOne("", "x", "stuff", "bob");
	}

	@Test
	public void testParamAtEnd() {
		testOne("stuff ", "x", "", "bob");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNoClosePrevious() {
		String.format("hello {x {no close previous", "{", "}");
		new StringTemplate("hello {x {no close previous", "{", "}");
	}

	@Test(expected = IllegalArgumentException.class)
	public void testNoCloseAtEnd() {
		new StringTemplate("hello {prefixtoend", "{", "}");
	}

	@Test
	public void testEmptyParam() {
		StringTemplate strTemplate = new StringTemplate("hello {{ there", "{", "}");
		assertEquals("hello { there", strTemplate.render(Collections.<String, Object> emptyMap()));
	}

	@Test
	public void testCoverage() {
		StringTemplate strTemplate = new StringTemplate("hello {{ there", "{", "}");
		strTemplate.toString();
	}

	@Test
	public void testUnknownParam() {
		StringTemplate strTemplate = new StringTemplate("hello {x} there", "{", "}");
		assertEquals("hello  there", strTemplate.render(Collections.<String, Object> emptyMap()));
	}

	@Test
	public void testTwoParams() {
		String arg1 = "x";
		String arg2 = "y";
		String before = "hello ";
		String middle = " there ";
		String end = " folks";
		StringTemplate strTemplate =
				new StringTemplate(before + "{" + arg1 + "}" + middle + "{" + arg2 + "}" + end, "{", "}");
		Map<String, Object> replaceMap = new HashMap<String, Object>();
		String val1 = "fejwpojpoefw";
		replaceMap.put("x", val1);
		String val2 = "ft43y89hoiug34";
		replaceMap.put("y", val2);
		assertEquals(before + val1 + middle + val2 + end, strTemplate.render(replaceMap));
	}

	@Test
	public void testTwoEmptyParams() {
		StringTemplate strTemplate = new StringTemplate("hello {x} there {y}", "{", "}");
		assertEquals("hello  there ", strTemplate.render(Collections.<String, Object> emptyMap()));
	}

	private void testOne(String before, String param, String after, String value) {
		String prefix = "{";
		String suffix = "}";
		String template = before + prefix + param + suffix + after;
		StringTemplate strTemplate = new StringTemplate(template, prefix, suffix);
		Map<String, Object> replaceMap = new HashMap<String, Object>();
		replaceMap.put(param, value);
		assertEquals(before + value + after, strTemplate.render(replaceMap));
	}
}
