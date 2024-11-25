package com.projeto.redirectUrlShortner;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class Main implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final S3Client s3CLient = S3Client.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> input, Context context) {

        String pathParameters = (String) input.get("rawPath");

        // "EX.: https://example.com/3568fg65"
        // "rawPat" extrai o UUID = /3568fg65
        // A string faz um replace para pegar somente o UUID.
        // Ex.: Sem replace = /3568fg65
        // Ex.: com replace =  3568fg65
        String shortUrlCode = pathParameters.replace("/", "");

        // Validaçao para conferir se o shortUrlCode existe
        if (shortUrlCode.isEmpty()){
            throw new IllegalArgumentException("Invalid input: 'shortUrlCode' is required");
        }

        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket("url-shortener-bucket-project")
                .key(shortUrlCode + ".json")
                .build();

        InputStream s3ObjectStream;

        try {
            s3ObjectStream = s3CLient.getObject(getObjectRequest);
        } catch (Exception exception) {
            throw new RuntimeException("Errror fetching URL data from S3: "+ exception.getMessage(), exception);
        }

        UrlData urlData;
        try {
            urlData = objectMapper.readValue(s3ObjectStream, UrlData.class);
        } catch (Exception e) {
            throw new RuntimeException("Error deserializing URL data: " + e.getMessage(), e);
        }

        long currentTimeinSeconds = System.currentTimeMillis() / 1000;

        Map<String, Object> response = new HashMap<>();

        // cenario que a URL expirou
        if (urlData.getExpirationTime() < currentTimeinSeconds) {

            response.put("statusCode", 410);
            response.put("body", "This URL has expired");

            return response;
        }

        response.put("statusCode", 302);
        Map<String, String> headers = new HashMap<>();
        headers.put("Location", urlData.getOriginalUrl());
        response.put("headers", headers);

        return response;
    }
}