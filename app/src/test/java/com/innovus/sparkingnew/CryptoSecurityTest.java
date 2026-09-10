package com.innovus.sparkingnew;

import com.innovus.sparkingnew.network.ApiConfig;
import com.innovus.sparkingnew.network.crypto.AesCipherUtil;
import com.innovus.sparkingnew.network.crypto.CryptoManager;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CryptoSecurityTest {

    @Test
    public void testAesEncryptionDecryptionCycle() throws Exception {
        String originalText = "{\"username\":\"0020797790\",\"password\":\"Secret@123\",\"device_id\":\"PAX-9988\"}";

        String cipherText = AesCipherUtil.encrypt(originalText);
        assertNotNull(cipherText);
        assertFalse(cipherText.isEmpty());
        assertNotEquals(originalText, cipherText);

        String decryptedText = AesCipherUtil.decrypt(cipherText);
        assertEquals(originalText, decryptedText);
    }

    @Test
    public void testPayloadFormatting() throws Exception {
        JSONObject inner = new JSONObject();
        inner.put("vehicle_no", "WBTEST99");
        inner.put("agent_id", 159);

        String payload = CryptoManager.prepareEncryptedPayload(inner);
        JSONObject outer = new JSONObject(payload);

        assertTrue(outer.has("enc_data"));
        assertNotNull(outer.getString("enc_data"));
    }

    @Test
    public void testProductionAesResponseDecryption() throws Exception {
        // Verify AesCipherUtil decrypts server response seamlessly
        String plainData = "{\"balance\":500,\"status\":\"active\"}";
        String encryptedData = AesCipherUtil.encrypt(plainData);

        String decryptedResult = AesCipherUtil.decrypt(encryptedData);
        assertEquals(plainData, decryptedResult);
    }

    @Test
    public void testMasterSwitchConfiguration() {
        assertNotNull(ApiConfig.getBaseUrl());
        assertTrue(ApiConfig.getBaseUrl().startsWith("http"));
        assertNotNull(ApiConfig.getUrlGenerateToken());
        assertNotNull(ApiConfig.getUrlLogin());
        assertNotNull(ApiConfig.getUrlCheckinRegistered());
        assertNotNull(ApiConfig.getUrlGetCheckoutAmount());
        assertNotNull(ApiConfig.getUrlVehicleCheckout());
    }
}
