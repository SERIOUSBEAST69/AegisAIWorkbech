package com.trustai.service;

import com.trustai.dto.ai.AiCallRequest;
import com.trustai.dto.ai.AiCallResponse;
import com.trustai.dto.ai.AiMessage;
import com.trustai.entity.AiCallLog;
import com.trustai.entity.AiModel;
import com.trustai.utils.AesEncryptor;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiModelService aiModelService;
    private final AiModelAccessGuardService aiModelAccessGuardService;
    private final RateLimiterService rateLimiterService;
    private final AesEncryptor aesEncryptor;
    private final AiCallAuditService aiCallAuditService;
    private final WebClient.Builder webClientBuilder;

    public AiCallResponse chat(AiCallRequest request, Long userId, String ip) {
        AiModel model = aiModelService.lambdaQuery()
                .eq(AiModel::getModelCode, request.getModelCode())
                .one();
        aiModelAccessGuardService.validate(model, request.getAssetId(), request.getAccessReason(), mergeRequestText(request));
        String apiKey = aesEncryptor.decrypt(model.getApiKey());
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("模型未配置密钥");
        }

        rateLimiterService.checkQuota(model.getModelCode(), model.getCallLimit() == null ? 0 : model.getCallLimit(), LocalDate.now());
        Instant begin = Instant.now();
        String content;
        Integer tokens = null;
        try {
            if (isWenxin(model.getProvider())) {
                content = callWenxin(model, request, apiKey);
            } else {
                content = callOpenAiLike(model, request, apiKey);
            }
            rateLimiterService.increment(model.getModelCode(), LocalDate.now());
            AiCallLog logEntry = buildLog(model, userId, ip, request, content, "success", null, Duration.between(begin, Instant.now()).toMillis(), tokens);
            aiCallAuditService.recordAsync(logEntry);
            return new AiCallResponse(content, tokens, Duration.between(begin, Instant.now()).toMillis(), model.getProvider(), model.getModelName());
        } catch (Exception e) {
            AiCallLog logEntry = buildLog(model, userId, ip, request, null, "fail", e.getMessage(), Duration.between(begin, Instant.now()).toMillis(), tokens);
            aiCallAuditService.recordAsync(logEntry);
            throw new IllegalStateException("模型调用失败:" + e.getMessage(), e);
        }
    }

    private String callOpenAiLike(AiModel model, AiCallRequest request, String apiKey) {
        WebClient client = webClientBuilder.baseUrl(model.getApiUrl()).build();
        List<SimpleMsg> messages = convertMessages(request.getMessages(), request.getPrompt());
        OpenAiPayload payload = new OpenAiPayload(model.getModelCode(), messages);
        OpenAiResponse resp = client.post()
                .header("Authorization", "Bearer " + apiKey)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(OpenAiResponse.class)
                .blockOptional(Duration.ofSeconds(30))
                .orElseThrow(() -> new IllegalStateException("OpenAI 兼容接口无响应"));
        if (resp.getChoices() == null || resp.getChoices().isEmpty() || resp.getChoices().get(0).getMessage() == null) {
            throw new IllegalStateException("OpenAI 兼容接口返回为空");
        }
        return resp.getChoices().get(0).getMessage().getContent();
    }

    private String callWenxin(AiModel model, AiCallRequest request, String apiKey) {
        String[] parts = apiKey.split("\\|");
        if (parts.length != 2) {
            throw new IllegalStateException("文心模型需要以 clientId|clientSecret 形式配置密钥");
        }
        String token = fetchBaiduToken(parts[0], parts[1]);
        WebClient client = webClientBuilder.baseUrl(model.getApiUrl()).build();
        List<SimpleMsg> messages = convertMessages(request.getMessages(), request.getPrompt());
        WenxinPayload payload = new WenxinPayload(model.getModelCode(), messages);
        return client.post()
                .uri(uriBuilder -> uriBuilder.queryParam("access_token", token).build())
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(WenxinResponse.class)
                .blockOptional(Duration.ofSeconds(30))
                .map(WenxinResponse::firstResult)
                .orElseThrow(() -> new IllegalStateException("文心返回为空"));
    }

    private String fetchBaiduToken(String clientId, String clientSecret) {
        WebClient client = webClientBuilder.baseUrl("https://aip.baidubce.com").build();
        return client.get()
                .uri(uriBuilder -> uriBuilder.path("/oauth/2.0/token")
                        .queryParam("grant_type", "client_credentials")
                        .queryParam("client_id", clientId)
                        .queryParam("client_secret", clientSecret)
                        .build())
                .retrieve()
                .bodyToMono(TokenResp.class)
                .blockOptional(Duration.ofSeconds(15))
                .map(TokenResp::getAccess_token)
                .orElseThrow(() -> new IllegalStateException("获取文心 access_token 失败"));
    }

    private List<SimpleMsg> convertMessages(List<AiMessage> messages, String systemPrompt) {
        List<SimpleMsg> list = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            list.add(new SimpleMsg("system", systemPrompt));
        }
        if (messages != null) {
            for (AiMessage m : messages) {
                String role = m.getRole();
                if (!"user".equals(role) && !"assistant".equals(role) && !"system".equals(role)) {
                    role = "user";
                }
                list.add(new SimpleMsg(role, m.getContent()));
            }
        }
        return list;
    }

    private boolean isWenxin(String provider) {
        if (provider == null) return false;
        String p = provider.toLowerCase();
        return Objects.equals(p, "wenxin") || Objects.equals(p, "qianfan") || Objects.equals(p, "yiyan");
    }

    private AiCallLog buildLog(AiModel model, Long userId, String ip, AiCallRequest req, String output, String status, String error, long durationMs, Integer tokens) {
        AiCallLog logEntry = new AiCallLog();
        logEntry.setUserId(userId);
        logEntry.setModelId(model.getId());
        logEntry.setModelCode(model.getModelCode());
        logEntry.setDataAssetId(req.getAssetId());
        logEntry.setProvider(model.getProvider());
        String inputPreview = req.getMessages() == null || req.getMessages().isEmpty() ? "" : req.getMessages().get(req.getMessages().size() - 1).getContent();
        if (inputPreview != null && inputPreview.length() > 100) inputPreview = inputPreview.substring(0, 100);
        logEntry.setInputPreview(inputPreview);
        String outputPreview = output;
        if (outputPreview != null && outputPreview.length() > 100) outputPreview = outputPreview.substring(0, 100);
        logEntry.setOutputPreview(outputPreview);
        logEntry.setStatus(status);
        logEntry.setErrorMsg(error);
        logEntry.setDurationMs(durationMs);
        logEntry.setTokenUsage(tokens);
        logEntry.setIp(ip);
        logEntry.setCreateTime(java.time.LocalDateTime.now());
        return logEntry;
    }

    private String mergeRequestText(AiCallRequest request) {
        StringBuilder builder = new StringBuilder();
        if (request.getPrompt() != null) {
            builder.append(request.getPrompt()).append(' ');
        }
        if (request.getMessages() != null) {
            request.getMessages().stream()
                    .map(AiMessage::getContent)
                    .filter(Objects::nonNull)
                    .forEach(text -> builder.append(text).append(' '));
        }
        return builder.toString().trim();
    }

    private record TokenResp(String access_token) {
        public String getAccess_token() { return access_token; }
    }

    private static class SimpleMsg {
        private final String role;
        private final String content;
        SimpleMsg(String role, String content) {
            this.role = role;
            this.content = content;
        }
        public String getRole() { return role; }
        public String getContent() { return content; }
    }

    private static class OpenAiPayload {
        private final String model;
        private final List<SimpleMsg> messages;
        OpenAiPayload(String model, List<SimpleMsg> messages) {
            this.model = model;
            this.messages = messages;
        }
        public String getModel() { return model; }
        public List<SimpleMsg> getMessages() { return messages; }
    }

    private static class OpenAiResponse {
        private List<Choice> choices;
        public List<Choice> getChoices() { return choices; }
        public void setChoices(List<Choice> choices) { this.choices = choices; }
        private static class Choice {
            private SimpleMsg message;
            public SimpleMsg getMessage() { return message; }
            public void setMessage(SimpleMsg message) { this.message = message; }
        }
    }

    private static class WenxinPayload {
        private final String model;
        private final List<SimpleMsg> messages;
        WenxinPayload(String model, List<SimpleMsg> messages) {
            this.model = model;
            this.messages = messages;
        }
        public String getModel() { return model; }
        public List<SimpleMsg> getMessages() { return messages; }
    }

    private static class WenxinResponse {
        private List<Result> result;
        public List<Result> getResult() { return result; }
        public void setResult(List<Result> result) { this.result = result; }
        public String firstResult() {
            if (result == null || result.isEmpty()) return null;
            return result.get(0).content;
        }
        private static class Result { public String content; }
    }
}
