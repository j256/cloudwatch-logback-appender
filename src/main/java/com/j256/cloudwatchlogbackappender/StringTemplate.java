package com.j256.cloudwatchlogbackappender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Little class to simplify the substitution of a string template into a string with a replacement-map of parameters and
 * values.
 * 
 * <p>
 * For example, if your prefix is "{" and your suffix is "}" and your template is:
 * </p>
 * 
 * <pre>
 * &quot;Hello there {full-name}: How are you doing? Give us ${money}!&quot;
 * </pre>
 * 
 * <p>
 * Then you call {@link #render(Map)} with the replacement-map:
 * </p>
 * 
 * <pre>
 * "full-name" -> "Bob Peters", "money" -> "12.25"
 * </pre>
 * 
 * <p>
 * It would return:
 * </p>
 * 
 * <pre>
 * &quot;Hello there Bob Peters: How are you doing? Give us $12.25!&quot;
 * </pre>
 * 
 * <p>
 * We also handle the form of {key?full:empty}. The "key" is looked up in the replacement-map and if it is not-emtpy
 * (not-null and length > 0) then it will display the full string otherwise the empty string. So if you have the
 * template:
 * </p>
 * 
 * <pre>
 * &quot;Hello there {name?Customer :Unknown Customer}{name}: How are you doing?&quot;
 * </pre>
 * 
 * <p>
 * And you call {@link #render(Map)} with the replacement-map:
 * </p>
 * 
 * <pre>
 * "name" -> "Bob Peters"
 * </pre>
 * 
 * <p>
 * It would return:
 * </p>
 * 
 * <pre>
 * &quot;Hello there Customer Bob Peters: How are you doing?
 * </pre>
 * 
 * <p>
 * While if you called {@link #render(Map)} with the replacement-map:
 * </p>
 * 
 * <pre>
 * "name" -> ""
 * </pre>
 * 
 * <p>
 * It would return:
 * </p>
 * 
 * <pre>
 * &quot;Hello there Unknown Customer: How are you doing?
 * </pre>
 * 
 * @author graywatson
 */
public class StringTemplate {

	// these characters are the special characters inside the {key?full:emtpy}
	private static final char QUESTION_CHAR = '?';
	private static final char SEPARATOR_CHAR = ':';

	/**
	 * Characters used on either side of replacement-strings inside of a email template: "Hello there %%full-name%%: How
	 * are you doing? Give us $%%money%%!!"
	 */
	private final String prefix;
	private final String suffix;
	private final TemplatePart[] templateParts;
	private final int templateLength;

	/**
	 * @param template
	 *            String in the form of "..." + PS + replaceMap-key + PS + "..." + PS + replaceMap-key + PS.
	 * @param prefix
	 *            String which locates the start of the replace-from strings. If it is is "{" then it will look for
	 *            "{foo...".
	 * @param suffix
	 *            String which locates the end of the replace-from strings. If it is is "}" then it will look for
	 *            "...foo}".
	 */
	public StringTemplate(String template, String prefix, String suffix) {
		if (MiscUtils.isBlank(prefix)) {
			throw new IllegalArgumentException("prefix cannot be null or blank");
		}
		if (MiscUtils.isBlank(suffix)) {
			throw new IllegalArgumentException("suffix cannot be null or blank");
		}
		if (MiscUtils.isBlank(template)) {
			throw new IllegalArgumentException("template string cannot be null or blank");
		}

		this.prefix = prefix;
		this.suffix = suffix;
		this.templateParts = extractParts(template);
		this.templateLength = template.length();
	}

	/**
	 * Convert and return a new string with the substitutions.
	 */
	public String render(Map<String, Object> replacementMap) {
		StringBuilder sb = new StringBuilder(templateLength);
		for (TemplatePart templatePart : templateParts) {
			templatePart.render(sb, replacementMap);
		}
		return sb.toString();
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder(getClass().getSimpleName());
		sb.append("StringTemplate [");
		for (int i = 0; i < templateParts.length; i++) {
			if (i % 2 == 1) {
				sb.append(prefix).append(templateParts[i]).append(suffix);
			} else {
				sb.append(templateParts[i]);
			}
		}
		sb.append(']');
		return sb.toString();
	}

	/**
	 * This splits our template body into an array of strings.
	 */
	private TemplatePart[] extractParts(String template) {
		List<TemplatePart> templates = new ArrayList<TemplatePart>();
		int lastIndex = 0;
		while (true) {
			int startIndex = template.indexOf(prefix, lastIndex);
			if (startIndex < 0) {
				if (lastIndex < template.length()) {
					templates.add(new ConstantTemplatePart(template.substring(lastIndex)));
				}
				break;
			}
			int paramStart = startIndex + prefix.length();
			// find the next start after this one
			int nextStartIndex = template.indexOf(prefix, paramStart);

			// hello{{ -> hello{
			// do we have prefixprefix?
			if (nextStartIndex == paramStart) {
				// add the before which includes the prefix
				String before = template.substring(lastIndex, paramStart);
				templates.add(new ConstantTemplatePart(before));
				lastIndex = nextStartIndex + prefix.length();
				continue;
			}

			// find the end of the param
			int endIndex = template.indexOf(suffix, paramStart);

			// if there is no suffix for this prefix, OR the next prefix is _before_ the suffix of this param
			if (endIndex < 0 || (nextStartIndex >= 0 && nextStartIndex < endIndex)) {
				throw new IllegalArgumentException("Template has start prefix \"" + prefix + "\" but no end at index "
						+ startIndex + ": " + template);
			}

			// we are good, absorb the stuff before the start index
			if (lastIndex < startIndex) {
				String before = template.substring(lastIndex, startIndex);
				templates.add(new ConstantTemplatePart(before));
			}
			lastIndex = endIndex + suffix.length();

			// now see if we have a {key?full:empty} form of entry
			int questionIndex = indexOf(template, QUESTION_CHAR, paramStart, endIndex);
			if (questionIndex >= 0) {
				String key = template.substring(paramStart, questionIndex);
				int fullIndex = questionIndex + 1;
				int sepIndex = indexOf(template, SEPARATOR_CHAR, fullIndex, endIndex);
				String fullValue = null;
				String emptyValue = null;
				if (sepIndex >= 0) {
					// we do have a separator so we have full and empty values although they may be blank
					if (fullIndex < sepIndex) {
						fullValue = template.substring(fullIndex, sepIndex);
					}
					if (sepIndex + 1 < endIndex) {
						emptyValue = template.substring(sepIndex + 1, endIndex);
					}
				} else if (fullIndex < endIndex) {
					// we don't have a separator so we have a full-value and empty is null
					fullValue = template.substring(fullIndex, endIndex);
				}
				if (fullValue != null || emptyValue != null) {
					templates.add(new ValueTestTemplatePart(key, fullValue, emptyValue));
				}
			} else {
				// absorb the parameter which could be blank
				String key = template.substring(paramStart, endIndex);
				templates.add(new ValueTemplatePart(key));
			}
		}
		return templates.toArray(new TemplatePart[templates.size()]);
	}

	private int indexOf(String str, char ch, int startPos, int endPos) {
		for (int i = startPos; i < endPos; i++) {
			if (str.charAt(i) == ch) {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Part of our template that renders a particular value.
	 */
	private static interface TemplatePart {
		/**
		 * Adds something to the string builder if necessary.
		 */
		public void render(StringBuilder sb, Map<String, Object> replacementMap);
	}

	/**
	 * Adds a constant string to the string-builder.
	 */
	private static class ConstantTemplatePart implements TemplatePart {

		private final String constant;

		public ConstantTemplatePart(String constant) {
			this.constant = constant;
		}

		@Override
		public void render(StringBuilder sb, Map<String, Object> replacementMap) {
			sb.append(constant);
		}
	}

	/**
	 * Looks up a key in the replacement-map and appends the value to the string-builder.
	 */
	private static class ValueTemplatePart implements TemplatePart {

		private final String key;

		public ValueTemplatePart(String key) {
			this.key = key;
		}

		@Override
		public void render(StringBuilder sb, Map<String, Object> replacementMap) {
			Object value = replacementMap.get(key);
			if (value != null) {
				sb.append(value);
			}
		}
	}

	/**
	 * Handles patterns like {key?full:empty}. It looks up the value of the key in the replacement-map. If it is empty
	 * (null or 0-length) then it will output the emptyValue otherwise it will output the fullValue.
	 */
	private static class ValueTestTemplatePart implements TemplatePart {

		private final String key;
		private final String fullValue;
		private final String emptyValue;

		public ValueTestTemplatePart(String key, String fullValue, String emptyValue) {
			this.key = key;
			this.fullValue = fullValue;
			this.emptyValue = emptyValue;
		}

		@Override
		public void render(StringBuilder sb, Map<String, Object> replacementMap) {
			Object value = replacementMap.get(key);
			String str;
			if (value == null) {
				str = emptyValue;
			} else {
				str = String.valueOf(value);
				if (str.length() == 0) {
					str = emptyValue;
				} else {
					str = fullValue;
				}
			}
			if (str != null) {
				sb.append(str);
			}
		}
	}
}
