package com.streamer.fileStreamer;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;

@SpringBootTest
@AutoConfigureWebTestClient
class FileStreamerApplicationTests {

	@Test
	void contextLoads() {

	}

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void testFileDeliveryApiFailure() {
		byte[] fileContent = new byte[20];
				String fileName = "example.txt"; // Replace with your actual file name
		MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
		parts.add("filename", fileName);
		parts.add("file", BodyInserters.fromValue(fileContent));
		// Perform the file delivery API request
		webTestClient.post()
				.uri("http://localhost:8080/api/fileDelivery?filename={filename}", fileName)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(BodyInserters.fromMultipartData(parts))
				.exchange()
				.expectStatus().is5xxServerError()
				.expectBody(String.class)
				.consumeWith(response -> {
					// Validate the response if needed
					String responseBody = response.getResponseBody();
					// Add assertions or validations based on your expected response
				});
	}
}
