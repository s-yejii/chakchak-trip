package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tripbiseo.common.exception.ApiException;

import tools.jackson.databind.JsonNode;

/**
 * ④ 리뷰 RAG: 글 → 임베딩(숫자 768개). Gemini batchEmbedContents API를 RestClient로 직접 불러요.
 * 뜻이 비슷한 글은 숫자 배열도 비슷하게 나와서, 거리(<=>)로 비슷한 리뷰를 찾을 수 있어요.
 * 저장할 리뷰는 RETRIEVAL_DOCUMENT, 찾는 질문은 RETRIEVAL_QUERY로 알려 주면 질문과 리뷰가 더 잘 맞아요.
 */
@Component
public class ReviewEmbedder {

    public static final int MAX_BATCH = 100;        // Gemini가 한 번에 받는 글 최대 개수
    public static final int DIMENSIONS = 768;       // review_embedding.embedding vector(768)과 같아야 함
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:batchEmbedContents";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    // RestClient: Spring에 들어 있는 HTTP 호출 도구 (다른 서버 API 부르기)
    private final RestClient restClient;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;
    @Value("${GEMINI_EMBED_MODEL:gemini-embedding-001}")
    private String model;

    public ReviewEmbedder() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
    }

    /** 임베딩 모델 이름 (review_embedding.model에 저장) */
    public String getModel() {
        return model;
    }

    /** 저장해 둘 리뷰 여러 개(최대 100) → 임베딩 여러 개 (같은 순서) */
    public List<float[]> embedDocuments(List<String> texts) {
        return callGemini(texts, "RETRIEVAL_DOCUMENT");
    }

    /** 찾는 질문 하나 → 임베딩 하나 */
    public float[] embedQuery(String text) {
        return callGemini(List.of(text), "RETRIEVAL_QUERY").get(0);
    }

    private List<float[]> callGemini(List<String> texts, String taskType) {
        if (apiKey.isBlank()) {
            throw new ApiException(503, "Gemini 키가 없어요. 환경변수 GEMINI_API_KEY를 확인해 주세요.");
        }
        try {
            JsonNode root = restClient.post()
                    .uri(String.format(URL, model))
                    .header("x-goog-api-key", apiKey)
                    .body(makeRequestBody(texts, taskType))
                    .retrieve()
                    .body(JsonNode.class);
            return readEmbeddings(root, texts.size());
        } catch (HttpClientErrorException.TooManyRequests e) {
            throw new ApiException(429, "Gemini 임베딩 사용량이 다 찼어요. 1분쯤 뒤에 다시 불러 주세요.");
        } catch (RestClientException e) {
            throw new ApiException(502, "Gemini 임베딩을 받지 못했어요: " + e.getMessage());
        }
    }

    // 보내는 JSON: {"requests": [{model, content: {parts: [{text}]}, taskType, outputDimensionality}, …]}
    private Map<String, Object> makeRequestBody(List<String> texts, String taskType) {
        List<Map<String, Object>> requests = new ArrayList<>();
        for (String text : texts) {
            Map<String, Object> one = new HashMap<>();
            one.put("model", "models/" + model);
            one.put("content", Map.of("parts", List.of(Map.of("text", text))));
            one.put("taskType", taskType);
            one.put("outputDimensionality", DIMENSIONS);
            requests.add(one);
        }
        return Map.of("requests", requests);
    }

    // 받는 JSON: {"embeddings": [{"values": [0.01, -0.2, …]}, …]} (보낸 순서 그대로)
    private List<float[]> readEmbeddings(JsonNode root, int expectedCount) {
        JsonNode embeddings = root == null ? null : root.get("embeddings");
        if (embeddings == null || embeddings.size() != expectedCount) {
            throw new ApiException(502, "Gemini 임베딩 개수가 보낸 글 수와 달라요.");
        }
        List<float[]> result = new ArrayList<>();
        for (JsonNode embedding : embeddings) {
            JsonNode values = embedding.get("values");
            float[] vector = new float[values.size()];
            for (int i = 0; i < vector.length; i++) {
                vector[i] = values.get(i).floatValue();
            }
            result.add(vector);
        }
        return result;
    }
}
