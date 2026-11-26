package com.tripbiseo.live.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.tripbiseo.common.exception.ApiException;

import tools.jackson.databind.JsonNode;

/**
 * ④ 리뷰 RAG: 글을 임베딩으로 바꿔요. 임베딩 = 글의 뜻을 숫자 768개 배열로 나타낸 것.
 * 뜻이 비슷한 글은 숫자 배열도 비슷해서, 거리(<=>)로 비슷한 리뷰를 찾을 수 있어요.
 * Gemini batchEmbedContents API를 RestClient로 직접 불러요.
 * 저장할 리뷰는 RETRIEVAL_DOCUMENT, 찾는 질문은 RETRIEVAL_QUERY로 알려 주면 질문과 리뷰가 더 잘 맞아요.
 */
@Component
public class ReviewEmbedder {

    public static final int MAX_BATCH = 100;        // Gemini가 한 번에 받는 글 최대 개수
    public static final int DIMENSIONS = 768;       // review_embedding.embedding vector(768)과 같아야 함
    private static final String URL = "https://generativelanguage.googleapis.com/v1beta/models/%s:batchEmbedContents";
    private static final int CONNECT_TIMEOUT_MS = 5000;
    private static final int READ_TIMEOUT_MS = 30000;

    private static final int MAX_QUERY_CACHE = 500;  // 임베딩을 저장해 둘 질문 수 (넘으면 비우고 다시)

    // RestClient: Spring에 들어 있는 HTTP 호출 도구 (다른 서버 API 부르기)
    private final RestClient restClient;
    // 질문 글 → 임베딩. ConcurrentHashMap: 여러 요청이 동시에 읽고 써도 안전한 Map
    private final Map<String, float[]> queryCache = new ConcurrentHashMap<>();

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;
    @Value("${GEMINI_EMBED_MODEL:gemini-embedding-001}")
    private String model;

    /** 연결 5초, 응답 30초 제한을 건 RestClient 만들기 */
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

    /** 찾는 질문 하나 → 임베딩 하나. 같은 질문은 저장해 둔 값을 다시 씀 (Gemini 한도·시간 아끼기) */
    public float[] embedQuery(String text) {
        float[] saved = queryCache.get(text);
        if (saved != null) {
            return saved;
        }
        float[] vector = callGemini(List.of(text), "RETRIEVAL_QUERY").get(0);
        if (queryCache.size() >= MAX_QUERY_CACHE) {
            queryCache.clear(); // 너무 많이 쌓이면 비우고 다시 (가장 단순한 방법)
        }
        queryCache.put(text, vector);
        return vector;
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
