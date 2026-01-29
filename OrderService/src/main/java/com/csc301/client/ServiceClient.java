package com.csc301.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ServiceClient {
    private final String baseUrl;
    private final HttpClient httpClient;

    public static class ServiceResponse {
        public final int statusCode;
        public final String body;

        public ServiceResponse(int statusCode, String body) {
            this.statusCode = statusCode;
            this.body = body;
        }
    }

    public ServiceClient(String ip, int port) {
        this.baseUrl = "http://" + ip + ":" + port;
        this.httpClient = HttpClient.newHttpClient();
    }

    public int getStatusCode(String path, String method, String body) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(new URI(baseUrl + path));

        if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json");
        } else {
            requestBuilder.GET();
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.statusCode();
    }

    public JsonObject makeRequest(String path, String method, String body) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(new URI(baseUrl + path));

        if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json");
        } else {
            requestBuilder.GET();
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        
        if (response.statusCode() == 200) {
            return JsonParser.parseString(response.body()).getAsJsonObject();
        }
        return null;
    }

    public ServiceResponse request(String path, String method, String body) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(new URI(baseUrl + path));

        if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body))
                    .header("Content-Type", "application/json");
        } else {
            requestBuilder.GET();
        }

        HttpRequest request = requestBuilder.build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return new ServiceResponse(response.statusCode(), response.body());
    }
}
