package com.sabayride.rental.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "payway")
public class PayWayProperties {

    private String merchantId = "ec479045";
    private String apiKey = "eaf45f07b874c56d28261bf3574ae9f77d3465d7";
    private String purchaseUrl = "https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/purchase";
    private String checkTransactionUrl = "https://checkout-sandbox.payway.com.kh/api/payment-gateway/v1/payments/check-transaction-2";
    private String rsaPublicKey = "-----BEGIN PUBLIC KEY-----\n" +
            "MIGfMA0GCSqGSIb3DQEBAQUAA4GNADCBiQKBgQCaJ6WUxByIIgW3HhSWjp94/qeJ\n" +
            "fm5zdVwifE0GjX11Szzf8H8rdh0PyWYuEGcuDRV4ZS7Lo7UR4CclDRf5W1xxMGXQ\n" +
            "YayZSy/pCsKT3Y30lGOKr7E0K7fAhMBtj9+wU3oMER4k2AEwWf3xg+lO3ZqVruma\n" +
            "walaS9nBYW8BeDqksQIDAQAB\n" +
            "-----END PUBLIC KEY-----";
    private String rsaPrivateKey = "-----BEGIN RSA PRIVATE KEY-----\n" +
            "MIICXAIBAAKBgQC7i3EncgvlC3he3mBoUSUWjOBqu014KLyr4/p5kaAO2e9w6jST\n" +
            "/HMCqyVOXgZb2R4SqFjBfMHwDaLxv3AAyLIc+t9TGNO+MBAIXBjdIr/G/BeV+ECw\n" +
            "m2IVfu/2zmiScwvNXyWPtMc1FmZhcio7Z6sutm944wfjW+GDzOiLnxBfJwIDAQAB\n" +
            "AoGANW27zkeE0PtMDwbeQ0m+vaZjtrRmlRFR8sxPirusdB6tQqdOVEyKvVthlOpf\n" +
            "eGIp2ZnhMzomDAvufF2T/H0wlGhFKdndG8Lao/NReMyszLXBkrSGkK3ZW9+Inngk\n" +
            "25VdEr1R+DkoPhWMF7GVsLo0Ac/aK1Okg8lOchy3A6DO+3UCQQDC3T8aT7zbNTG8\n" +
            "VygY61VtjxpxDZdRk6vjdxg2zDuR9kSQ3NQxY88SF1x9rma2i/vAKgkdLytv7AIB\n" +
            "XUmxUvorAkEA9mJRNy3JJ/U+E+FS/9b2Egy3ayDmnmYQsJ2G7mLYFFJdKeT5XSsD\n" +
            "fTUGpVz/Dg/VzSTZTkVgPwUZGxber/7c9QJAF2EiLA77ErXcJMO0POEnW8C6pGvE\n" +
            "BvaXve/RCOoSD54jddn434AhHQOOhknBaw96ggDJHSZGqOQBDZbau5rABwJBAI0j\n" +
            "SO7Y9ZBENilhPBB+bTttuWxOzo0SXqEtu+u3B5ysid3D4uzyBO+mfoYfvaOqgokf\n" +
            "gLQLndS78OPJuAsNbjkCQDZtUB3acOcjZNNZnW6G+qkVBK3o1C/BL7Rphc6dp8zz\n" +
            "NOY+Xi0ok1tJ3P8Xja1ApIiW7/YeA5Y0atmXAQNN0TE=\n" +
            "-----END RSA PRIVATE KEY-----";

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getPurchaseUrl() {
        return purchaseUrl;
    }

    public void setPurchaseUrl(String purchaseUrl) {
        this.purchaseUrl = purchaseUrl;
    }

    public String getCheckTransactionUrl() {
        return checkTransactionUrl;
    }

    public void setCheckTransactionUrl(String checkTransactionUrl) {
        this.checkTransactionUrl = checkTransactionUrl;
    }

    public String getRsaPublicKey() {
        return rsaPublicKey;
    }

    public void setRsaPublicKey(String rsaPublicKey) {
        this.rsaPublicKey = rsaPublicKey;
    }

    public String getRsaPrivateKey() {
        return rsaPrivateKey;
    }

    public void setRsaPrivateKey(String rsaPrivateKey) {
        this.rsaPrivateKey = rsaPrivateKey;
    }
}
