package com.sabayride.rental.payment;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PayWayClientTest {

    private PayWayClient client;
    private PayWayProperties properties;

    @BeforeEach
    void setUp() {
        properties = new PayWayProperties();
        properties.setMerchantId("ec479045");
        properties.setApiKey("eaf45f07b874c56d28261bf3574ae9f77d3465d7");
        client = new PayWayClient(properties, new ObjectMapper());
    }

    @Test
    void testComputeHmacSha512() {
        String data = "20261001120000ec479045SR123456";
        String key = "eaf45f07b874c56d28261bf3574ae9f77d3465d7";

        String hash = client.computeHmacSha512(data, key);
        assertNotNull(hash);
        assertFalse(hash.isBlank());
        // Verify deterministic hashing
        assertEquals(hash, client.computeHmacSha512(data, key));
    }

    @Test
    void testCheckTransactionResultEvaluation() {
        PayWayClient.CheckTransactionResult paidResult =
                new PayWayClient.CheckTransactionResult("SR123", "APPROVED", 0, java.math.BigDecimal.ONE, "{}");
        assertTrue(paidResult.isPaid());
        assertFalse(paidResult.isFailed());

        PayWayClient.CheckTransactionResult pendingResult =
                new PayWayClient.CheckTransactionResult("SR123", "PENDING", 2, java.math.BigDecimal.ONE, "{}");
        assertFalse(pendingResult.isPaid());
        assertFalse(pendingResult.isFailed());

        PayWayClient.CheckTransactionResult failedResult =
                new PayWayClient.CheckTransactionResult("SR123", "DECLINED", 1, java.math.BigDecimal.ONE, "{}");
        assertFalse(failedResult.isPaid());
        assertTrue(failedResult.isFailed());
    }
}
