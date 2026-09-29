package com.sabayride.identity.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Dispatches SMS messages via Plasgate (+Gate) Cloud REST API.
 * Docs: https://cloud.plasgate.com/support/
 */
@Component
public class PlasgateSmsSender {

    private static final Logger log = LoggerFactory.getLogger(PlasgateSmsSender.class);

    private final String apiUrl;
    private final String privateKey;
    private final String secret;
    private final String sender;
    private final String deliveryMode;
    private final RestClient restClient;

    public PlasgateSmsSender(
            @Value("${sabayride.otp.plasgate.api-url:https://cloudapi.plasgate.com/rest/send}") String apiUrl,
            @Value("${sabayride.otp.plasgate.private-key:1Cifz_UnM2l9bBllbSE07gsc6PBDZGBBdrBVVkwRrEb14llW6VNdaLRt7sqEBxbejHNeBX7sGYLiPQlLYEzc1w}") String privateKey,
            @Value("${sabayride.otp.plasgate.secret:$5$rounds=535000$oeyUjS60DfR3m6Ci$2nmQdLM4Vfwnw5NzkbweZKAeSbjiioCQp3A5CgxB5O7}") String secret,
            @Value("${sabayride.otp.plasgate.sender:PlasGateUAT}") String sender,
            @Value("${sabayride.otp.delivery:PLASGATE}") String deliveryMode) {
        this.apiUrl = apiUrl;
        this.privateKey = privateKey;
        this.secret = secret;
        this.sender = sender;
        this.deliveryMode = deliveryMode;
        this.restClient = RestClient.builder().build();
    }

    /**
     * Sends SMS with verification code.
     * @param rawPhone recipient phone
     * @param content SMS text message
     * @return true if successfully delivered or logged
     */
    public boolean sendSms(String rawPhone, String content) {
        String phone = toPlasgatePhone(rawPhone);

        if ("LOG".equalsIgnoreCase(deliveryMode) || privateKey.isBlank() || secret.isBlank()) {
            log.info("═══ [OTP SMS LOG MODE] ═══ To: {} | Content: {}", phone, content);
            return true;
        }

        try {
            String url = apiUrl + "?private_key=" + privateKey.trim();
            Map<String, String> payload = Map.of(
                    "sender", sender.trim(),
                    "to", phone,
                    "content", content
            );

            ResponseEntity<String> response = restClient.post()
                    .uri(url)
                    .header("X-Secret", secret.trim())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toEntity(String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info("Plasgate SMS sent successfully to {}. Response: {}", phone, response.getBody());
                return true;
            } else {
                log.warn("Plasgate returned non-2xx status: {}. Body: {}", response.getStatusCode(), response.getBody());
            }
        } catch (Exception e) {
            log.error("Failed to send SMS via Plasgate: {}. Notice: check Sender ID authorization in Plasgate dashboard.", e.getMessage());
        }

        // Fallback: Always print OTP to backend logs so development and testing are not blocked
        log.info("═══ [OTP FALLBACK LOG] ═══ To: {} | Content: {}", phone, content);
        return true;
    }

    /**
     * Converts phone numbers to Cambodia format expected by Plasgate:
     * e.g. "+85512345678" -> "85512345678", "012345678" -> "85512345678".
     */
    public static String toPlasgatePhone(String raw) {
        if (raw == null) return "";
        String cleaned = raw.replaceAll("[^0-9]", "");
        if (cleaned.startsWith("0")) {
            return "855" + cleaned.substring(1);
        } else if (!cleaned.startsWith("855")) {
            return "855" + cleaned;
        }
        return cleaned;
    }
}
