package com.streamer.fileStreamer.service;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.http.MediaType;
import java.nio.ByteBuffer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;

import com.streamer.fileStreamer.FileDeliveryApiDelegate;
import com.streamer.fileStreamer.model.ModelApiResponse;

import lombok.extern.slf4j.Slf4j;

import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
@Service
@Slf4j
public class FileForwarderImpl implements FileDeliveryApiDelegate {
    private final WebClient webClient;
    private String fileUpload;
    public FileForwarderImpl(@Value("${production.api.url}") String apiUrl, @Value("${production.api.endpoints}") String endpoints) {
            if(apiUrl == null) {
                apiUrl = "http://localhost:8080";
            }
            this.fileUpload = endpoints;
            this.webClient = WebClient.builder().baseUrl(apiUrl).build();
    }

    private Mono<byte[]> getFileContent(Flux<Part> fileParts) {
            return fileParts
                .filter(part -> part.name().equals("file"))
                .flatMap(part -> part.content().map(DataBuffer::asByteBuffer))
                .reduce(ByteBuffer::put)
                .map(byteBuffer -> {
                    byte[] result = new byte[byteBuffer.remaining()];
                    byteBuffer.get(result);
                    return result;
                })
                .defaultIfEmpty(new byte[0]);
    }
    private Mono<ResponseEntity<ModelApiResponse>> send(String fileName, Flux<Part> file) {
        // Check if the file is present and non-empty
        Mono<byte[]> fileContent = getFileContent(file);

        return fileContent.flatMap(content -> {
            if (content.length == 0) {
                ModelApiResponse response  = new ModelApiResponse();
                response.setStatus("400");
                response.setMessage("No File Present");
                return Mono.just(ResponseEntity.badRequest().body(Mono.just("Empty file")))
                        .map(responseEntity -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                .body(response));
            } else {
                // File is not empty, proceed with the upload
                return webClient.post()
                        .uri(uriBuilder -> uriBuilder.path(this.fileUpload)
                                .queryParam("filename", fileName).build())
                        .contentType(MediaType.MULTIPART_FORM_DATA)
                        .bodyValue(BodyInserters.fromPublisher(Mono.just(content), byte[].class))
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .onStatus(HttpStatusCode::is5xxServerError, clientResponse ->
                                clientResponse.bodyToMono(String.class)
                                        .flatMap(errorBody -> Mono.error(new RuntimeException("Unable to connect client: " + errorBody)))
                                        .cast(Throwable.class)
                        )
                        .toEntity(ModelApiResponse.class)
                        .onErrorResume(RuntimeException.class, ex -> {
                            // Handle the custom error message here
                            String customErrorMessage = "Unable to connect with client";
                            ModelApiResponse customResponse = new ModelApiResponse();
                            customResponse.setStatus("500");
                            customResponse.setMessage(customErrorMessage);
                            log.info(ex.getMessage());
                            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(customResponse));
                        })
                        .map(responseEntity -> {
                            HttpStatus statusCode = HttpStatus.OK;
                            ModelApiResponse responseBody = responseEntity.getBody();
                            return ResponseEntity.status(statusCode).body(responseBody);
                        });
            }
        });
    }


//    private Mono<ResponseEntity<ModelApiResponse>> send(String fileName, Flux<Part> file){
//        Mono<byte[]> fileContent = getFileContent(file);
//        return webClient.post()
//        .uri(uriBuilder -> uriBuilder.path(this.fileUpload)
//        .queryParam("filename", fileName).build())
//        .contentType(MediaType.MULTIPART_FORM_DATA)
//        .bodyValue(BodyInserters.fromPublisher(fileContent, byte[].class))
//        .accept(MediaType.APPLICATION_JSON)
//        .retrieve()
//        .toEntity(ModelApiResponse.class)
//        .map(responseEntity -> {
//            HttpStatus statusCode = HttpStatus.OK;
//            ModelApiResponse responseBody = responseEntity.getBody();
//            return ResponseEntity.status(statusCode).body(responseBody);
//        });
//    }

    @Override
    public Mono<ResponseEntity<ModelApiResponse>> sendFile(String filename,
        Flux<Part> file,
        ServerWebExchange exchange) {
        log.info("File name:"+ filename);
        return this.send(filename, file);
    }
}