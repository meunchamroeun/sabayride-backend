package com.sabayride.rental.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class PayWayClient {

    private static final Logger log = LoggerFactory.getLogger(PayWayClient.class);
    private static final DateTimeFormatter REQ_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC);

    private final PayWayProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public PayWayClient(PayWayProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public record PurchaseResult(
            String tranId,
            String qrString,
            String qrImage,
            String abapayDeeplink,
            String statusCode,
            String message
    ) {}

    public record CheckTransactionResult(
            String tranId,
            String paymentStatus,
            int paymentStatusCode,
            BigDecimal paymentAmount,
            String rawJson
    ) {
        public boolean isPaid() {
            return paymentStatusCode == 0 ||
                    "APPROVED".equalsIgnoreCase(paymentStatus) ||
                    "SUCCESS".equalsIgnoreCase(paymentStatus);
        }

        public boolean isFailed() {
            return "FAILED".equalsIgnoreCase(paymentStatus) ||
                    "DECLINED".equalsIgnoreCase(paymentStatus) ||
                    "CANCELLED".equalsIgnoreCase(paymentStatus);
        }
    }

    /**
     * Calls ABA PayWay Purchase API to initiate payment and generate KHQR & Deeplink.
     */
    public PurchaseResult createPurchase(
            String tranId,
            BigDecimal amount,
            String currency,
            String itemName,
            String customerFirstName,
            String customerLastName,
            String customerEmail,
            String customerPhone,
            String returnUrl
    ) {
        String reqTime = REQ_TIME_FORMATTER.format(Instant.now());
        String merchantId = properties.getMerchantId();
        String formattedAmount = amount.setScale(2, RoundingMode.HALF_UP).toString();
        String curr = (currency != null && !currency.isBlank()) ? currency : "USD";

        String firstname = (customerFirstName != null && !customerFirstName.isBlank()) ? customerFirstName : "Customer";
        String lastname = (customerLastName != null && !customerLastName.isBlank()) ? customerLastName : "User";
        String email = (customerEmail != null && !customerEmail.isBlank()) ? customerEmail : "customer@sabayride.com";
        String phone = (customerPhone != null && !customerPhone.isBlank()) ? customerPhone : "012345678";
        String type = "purchase";
        String paymentOption = "abapay_khqr_deeplink";

        // Construct items JSON base64
        String itemsJson = String.format("[{\"name\":\"%s\",\"quantity\":1,\"price\":%s}]",
                escapeJson(itemName != null ? itemName : "Motorbike Rental"), formattedAmount);
        String itemsBase64 = Base64.getEncoder().encodeToString(itemsJson.getBytes(StandardCharsets.UTF_8));

        String returnUrlBase64 = (returnUrl != null && !returnUrl.isBlank())
                ? Base64.getEncoder().encodeToString(returnUrl.getBytes(StandardCharsets.UTF_8))
                : "";

        // Standard PayWay v1/v3 purchase hash concatenation formula:
        // req_time + merchant_id + tran_id + amount + items + shipping + firstname + lastname + email + phone +
        // type + payment_option + return_url + cancel_url + continue_success_url + return_deeplink + currency +
        // custom_fields + return_params + payout + lifetime + additional_params + google_pay_token + skip_success_page
        String b4hash = reqTime + merchantId + tranId + formattedAmount + itemsBase64 + "" +
                firstname + lastname + email + phone + type + paymentOption +
                returnUrlBase64 + "" + "" + "" +
                curr + "" + "" + "" + "" +
                "" + "" + "";

        String hash = computeHmacSha512(b4hash, properties.getApiKey());

        Map<String, String> formData = new LinkedHashMap<>();
        formData.put("req_time", reqTime);
        formData.put("merchant_id", merchantId);
        formData.put("tran_id", tranId);
        formData.put("amount", formattedAmount);
        formData.put("items", itemsBase64);
        formData.put("firstname", firstname);
        formData.put("lastname", lastname);
        formData.put("email", email);
        formData.put("phone", phone);
        formData.put("type", type);
        formData.put("payment_option", paymentOption);
        formData.put("currency", curr);
        if (!returnUrlBase64.isEmpty()) {
            formData.put("return_url", returnUrlBase64);
        }
        formData.put("hash", hash);

        String boundary = "----SabayRideBoundary" + UUID.randomUUID().toString().replace("-", "");
        byte[] requestBody = buildMultipartFormData(formData, boundary);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getPurchaseUrl()))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .header("User-Agent", "SabayRide/1.0")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestBody))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("PayWay purchase response status: {}", response.statusCode());

            if (response.statusCode() >= 400) {
                log.error("PayWay purchase HTTP error {}: {}", response.statusCode(), response.body());
                throw new RuntimeException("PayWay purchase error: HTTP " + response.statusCode());
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode statusNode = root.path("status");
            String code = statusNode.path("code").asText();
            String message = statusNode.path("message").asText();

            if (!"00".equals(code) && !"0".equals(code)) {
                log.error("PayWay purchase returned error code {}: {}", code, message);
                throw new RuntimeException("PayWay rejected transaction: " + message + " (code " + code + ")");
            }

            String qrString = root.path("qrString").asText(null);
            String qrImage = root.path("qrImage").asText(null);
            String deeplink = root.path("abapay_deeplink").asText(null);

            return new PurchaseResult(tranId, qrString, qrImage, deeplink, code, message);
        } catch (Exception e) {
            log.error("Failed to call PayWay purchase API", e);
            throw new RuntimeException("Failed to initiate ABA PayWay transaction: " + e.getMessage(), e);
        }
    }

    /**
     * Polls PayWay check-transaction-2 API to check the real status of a transaction.
     */
    public CheckTransactionResult checkTransaction(String tranId) {
        String reqTime = REQ_TIME_FORMATTER.format(Instant.now());
        String merchantId = properties.getMerchantId();

        // Formula for check-transaction-2: req_time + merchant_id + tran_id
        String b4hash = reqTime + merchantId + tranId;
        String hash = computeHmacSha512(b4hash, properties.getApiKey());

        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("req_time", reqTime);
        payload.put("merchant_id", merchantId);
        payload.put("tran_id", tranId);
        payload.put("hash", hash);

        try {
            String jsonBody = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(properties.getCheckTransactionUrl()))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "SabayRide/1.0")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            log.info("PayWay check-transaction status: {}", response.statusCode());

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode dataNode = root.path("data");
            String paymentStatus = dataNode.path("payment_status").asText("UNKNOWN");
            int paymentStatusCode = dataNode.path("payment_status_code").asInt(-1);
            BigDecimal totalAmount = BigDecimal.valueOf(dataNode.path("total_amount").asDouble(0.00));

            return new CheckTransactionResult(tranId, paymentStatus, paymentStatusCode, totalAmount, response.body());
        } catch (Exception e) {
            log.error("Failed to query PayWay check-transaction for tranId: {}", tranId, e);
            return new CheckTransactionResult(tranId, "UNKNOWN", -1, BigDecimal.ZERO, "{}");
        }
    }

    /**
     * Computes HMAC-SHA512 and Base64 encodes it.
     */
    public String computeHmacSha512(String data, String key) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512");
            mac.init(secretKeySpec);
            byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(rawHmac);
        } catch (Exception e) {
            throw new RuntimeException("Failed to calculate HMAC-SHA512 hash: " + e.getMessage(), e);
        }
    }

    private byte[] buildMultipartFormData(Map<String, String> fields, String boundary) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            sb.append("--").append(boundary).append("\r\n");
            sb.append("Content-Disposition: form-data; name=\"").append(entry.getKey()).append("\"\r\n\r\n");
            sb.append(entry.getValue()).append("\r\n");
        }
        sb.append("--").append(boundary).append("--\r\n");
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
