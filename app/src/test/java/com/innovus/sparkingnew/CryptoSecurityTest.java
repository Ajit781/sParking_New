package com.innovus.sparkingnew;

import com.innovus.sparkingnew.models.UserData;
import com.innovus.sparkingnew.network.ApiConfig;
import com.innovus.sparkingnew.network.crypto.AesCipherUtil;
import com.innovus.sparkingnew.network.crypto.CryptoManager;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class CryptoSecurityTest {

    @Test
    public void testAesGcmEncryptionDecryptionCycle() throws Exception {
        String originalText = "{\"username\":\"0020797790\",\"password\":\"Parking@123\",\"device_id\":\"0020797790\"}";

        String cipherText = AesCipherUtil.encrypt(originalText);
        assertNotNull(cipherText);
        assertFalse(cipherText.isEmpty());
        assertNotEquals(originalText, cipherText);

        String decryptedText = AesCipherUtil.decrypt(cipherText);
        assertEquals(originalText, decryptedText);
    }

    @Test
    public void testUserProvidedCurlEncryptedPayloadDecryption() throws Exception {
        // User provided curl enc_data payload
        String userProvidedEncData = "URakN7nYdjEsE+p0yipMp+XtYCpJEsIsrEo1JWhUBUw45XSsH3tvWGUq7j/nNcf62mNZEBLqxpyQg7EY47PINx9n+lCSKL3wTCFYJs027N+FwLGbuFWS4heMhRxp2InOC3NNFrxTMA==";

        String decrypted = AesCipherUtil.decrypt(userProvidedEncData);
        assertNotNull(decrypted);

        JSONObject json = new JSONObject(decrypted);
        assertEquals("0020797790", json.getString("username"));
        assertEquals("Parking@123", json.getString("password"));
        assertEquals("0020797790", json.getString("device_id"));
    }

    @Test
    public void testServerResponseDecryptionAndUserDataParsing() throws Exception {
        ApiConfig.setProduction(true);

        String serverEncryptedData = "p7RWWoONP6+ZaCETvbNomCW+PUYTNyv3FzlDUPgpxTptv3bn2foEB+c1yaeEX6sNiJNVxv9QZ9cT54DS5yb2Kw4vtxwLGjMXoVCIaJtqAEeWkguRm7CUpSNxxPCmQK+WwggoU/CCfWviEnFOE+K8cfo7MPfjS/BMRcyKCILsolgZgmWRv3fZwqwK9aHaEB6otlxGbn2tlG1g2mfCw20G6B9rKjMLboS6A/BmSS9a0Wl1o19Pp+MdLfp0JjC5gtSJG98CJx5opuhaYr/uhpOODZucXXlWgsaa3c5auWcadrFpl/hotVFYTItyoik6hDVjx5Hghw9HT1BuKh7k8COv/HLpM/DwGSlfE4wDlZsr6xPp587IJj+/i4WyASPqwptk47sQA7zGhKgYob0qRj5mOOGhwJZG75eBm4t4xzv5anlpPWeKOKx9y+k2tl9tpcIIMKJDtNUr4mI1m6F0g8chcMpZCIT94wzyz4PuyHmcK6CoDyFhXio+gXG9Ny6p7lT3vYnpUqiGPYrSLKU2Y0M6Dw91BxQxdoW7N17ttKIIykfE1iEr5u8PnKhhw3irfj5PxBfxI5eCj9nYUZxiqRjF01bODoHxkjfSimZih/BfYXgSh7O9lElTx+CjHzQd8GcTNi7VeDUeC8Q9+WPPbeNcOCeRD2andA+yQ8H8t2AvQEn5H/dixp1vp3ZKpViAKxnv01DFJsX8SUYbo130QNqYS+x/pfrHOe4ZCqThF+R6HIUdcXRzV8W7IxcF7DSNcUYEnVZ13OzsEWeUXrVTm5F3WWP95KsOPKN8/ZdGmwNnKJErAiuSXxVRNdwoYrhvJalPJ+1X9ETlVh2Wxjyckp4TYX8lZi9xDqTV5FzagRfgjSSwPJ0OtZVlQmLEeWuFyXiBXtGEHvbDJl1OUjmLMcC8tLrZvp800NP5l+v49NN/n6EgpLoGbdP3Zg8cQgb9zPYhgmXRpMivGx9vzzOK4JFKHPsQa4kh2fQmNiI9aXCIAIsBeX5zD2ZXY7sWdDRmeeKMesK0tHbTtXscaWaaj/g=";

        JSONObject serverResponse = new JSONObject();
        serverResponse.put("version", "1.0");
        serverResponse.put("status", "GEN_000");
        serverResponse.put("message", "Success");
        serverResponse.put("data", serverEncryptedData);

        JSONObject decryptedResponse = CryptoManager.decryptResponse(serverResponse.toString());
        assertNotNull(decryptedResponse);
        assertTrue(decryptedResponse.has("data"));

        JSONObject userDataJson = decryptedResponse.getJSONObject("data");
        UserData userData = UserData.fromJson(userDataJson);

        assertNotNull(userData);
        assertEquals(159, userData.getAgentId());
        assertEquals("20797790", userData.getAgentName());
        assertEquals("SOUTHERN SIDE BENTIK STREET TO OLD COURT HOUSE STR", userData.getLocation());
        assertEquals("PIONEER CO-OP. CARPARKING SERVICING", userData.getAgencyName());
        assertEquals(5.0, userData.getTwoWheelerRate(), 0.001);
        assertEquals(10.0, userData.getFourWheelerRate(), 0.001);
    }

    @Test
    public void testStagingVsProductionPayloadAndBaseUrl() throws Exception {
        JSONObject sampleData = new JSONObject();
        sampleData.put("vehicle_no", "WBTEST99");
        sampleData.put("agent_id", 159);

        // 1. TEST STAGING MODE (Plain Real Data payload)
        ApiConfig.setProduction(false);
        assertFalse(ApiConfig.isEncryptionEnabled());

        String stagingPayload = CryptoManager.prepareEncryptedPayload(sampleData);
        JSONObject stagingOuter = new JSONObject(stagingPayload);
        assertTrue(stagingOuter.has("enc_data"));
        assertEquals(sampleData.toString(), stagingOuter.getString("enc_data"));

        // 2. TEST PRODUCTION MODE (AES-256-GCM Encrypted payload + Dynamic Base URL)
        ApiConfig.setProduction(true);
        assertTrue(ApiConfig.isEncryptionEnabled());

        String dynamicUrl = "https://vigpl.com/SParkingRestAPI/api/";
        ApiConfig.setBaseUrl(null, dynamicUrl);
        assertEquals(dynamicUrl, ApiConfig.getBaseUrl());

        String prodPayload = CryptoManager.prepareEncryptedPayload(sampleData);
        JSONObject prodOuter = new JSONObject(prodPayload);
        assertTrue(prodOuter.has("enc_data"));
        String cipherData = prodOuter.getString("enc_data");
        assertNotEquals(sampleData.toString(), cipherData);

        String decrypted = AesCipherUtil.decrypt(cipherData);
        assertEquals(sampleData.toString(), decrypted);
    }

    @Test
    public void testDynamicEndpointsReflectActiveBaseUrl() {
        ApiConfig.setProduction(true);
        String customUrl = "https://vigpl.com/SParkingRestAPI/api/";
        ApiConfig.setBaseUrl(null, customUrl);

        assertEquals(customUrl, ApiConfig.getBaseUrl());
        assertEquals("https://vigpl.com/SParkingRestAPI/api/auth/generate_token", ApiConfig.getUrlGenerateToken());
        assertEquals("https://vigpl.com/SParkingRestAPI/api/auth/login", ApiConfig.getUrlLogin());
        assertEquals("https://vigpl.com/SParkingRestAPI/api/booking/checkinRegisteredVehicle", ApiConfig.getUrlCheckinRegistered());
        assertEquals("https://vigpl.com/SParkingRestAPI/api/booking/getCheckoutAmount", ApiConfig.getUrlGetCheckoutAmount());
        assertEquals("https://vigpl.com/SParkingRestAPI/api/booking/vehicleCheckout", ApiConfig.getUrlVehicleCheckout());
    }
}
