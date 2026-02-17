package com.j256.cloudwatchlogbackappender;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Converter which sort of knows about the task-id in ECS. Will return "http-error", "interrupted", or "unknown" if it
 * wasn't able to use the metadata URI. This uses a brittle regex to parse the resulting JSON. Feel free to use the
 * appropriate JSON parsing request and call {@link #setTaskId(String)} early in your application.
 * 
 * @author graywatson
 */
public class TaskIdConverter extends ClassicConverter {

	private static final String NOT_IN_ECS_TASK_ID = "not-in-ecs";
	private static final String ECS_CONTAINER_METADATA_URI = "ECS_CONTAINER_METADATA_URI_V4";
	/**
	 * pattern to pluck out the task id which is TaskARM in a json with part after the last / being the task-id.
	 * arn:aws:ecs:us-east-1:4########97:task/appname/8bbe67c9363a457894068e2259f5690c
	 */
	private static final Pattern TASK_ID_PATTERN = Pattern.compile("(?s).*\"TaskARN\"\\s*:\\s*\"[^\"]+/([^\"/]+)\".*");

	private static String taskId;

	@Override
	public String convert(ILoggingEvent event) {
		if (taskId == null) {
			taskId = lookupTaskId();
		}
		return taskId;
	}

	public static void setTaskId(String instanceId) {
		TaskIdConverter.taskId = instanceId;
	}

	/**
	 * Lookup our task-id from the container metadata.
	 */
	private static String lookupTaskId() {
		String metadataUri = System.getenv(ECS_CONTAINER_METADATA_URI);
		if (metadataUri == null) {
			return NOT_IN_ECS_TASK_ID;
		}

		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(metadataUri + "/task")).GET().build();
		HttpResponse<String> response;
		try {
			response = client.send(request, HttpResponse.BodyHandlers.ofString());
		} catch (IOException e) {
			return "http-error";
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return "interrupted";
		}

		Matcher matcher = TASK_ID_PATTERN.matcher(response.body());
		if (matcher.matches()) {
			return matcher.group(1);
		} else {
			return CloudWatchAppender.UNKNOWN_CLOUD_NAME;
		}
	}
}
