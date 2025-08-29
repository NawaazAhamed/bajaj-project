package com.example.webhookapp;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class WebhookApp implements CommandLineRunner {

    public static void main(String[] args) {
        SpringApplication.run(WebhookApp.class, args);
    }

    @Override
    public void run(String... args) {
        RestTemplate restTemplate = new RestTemplate();

        try {
            // ---------- STEP 1: Generate webhook (send your details) ----------
            String generateUrl = "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA";

            Map<String, String> request = new HashMap<>();
            request.put("name", "Nawaaz Ahamed");
            request.put("regNo", "22BEC0653");
            request.put("email", "nawaazahamed12@gmail.com");

            HttpHeaders genHeaders = new HttpHeaders();
            genHeaders.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, String>> genEntity = new HttpEntity<>(request, genHeaders);

            ResponseEntity<Map> response = restTemplate.postForEntity(generateUrl, genEntity, Map.class);
            if (response.getBody() == null) {
                throw new RuntimeException("Empty response from generateWebhook API");
            }

            Object webhookObj = response.getBody().get("webhook");
            Object tokenObj = response.getBody().get("accessToken");

            if (webhookObj == null || tokenObj == null) {
                throw new RuntimeException("Missing 'webhook' or 'accessToken' in response: " + response.getBody());
            }

            String webhookUrl = webhookObj.toString();
            String accessToken = tokenObj.toString();

            System.out.println("Webhook URL: " + webhookUrl);
            System.out.println("Access Token (JWT): " + accessToken.substring(0, Math.min(12, accessToken.length())) + "...");

            // ---------- STEP 2: Prepare final SQL query (Question 1 for odd regNo) ----------
            String finalSql =
                    "SELECT P.AMOUNT AS SALARY, " +
                    "CONCAT(E.FIRST_NAME, ' ', E.LAST_NAME) AS NAME, " +
                    "TIMESTAMPDIFF(YEAR, E.DOB, CURDATE()) AS AGE, " +
                    "D.DEPARTMENT_NAME " +
                    "FROM PAYMENTS P " +
                    "JOIN EMPLOYEE E ON P.EMP_ID = E.EMP_ID " +
                    "JOIN DEPARTMENT D ON E.DEPARTMENT = D.DEPARTMENT_ID " +
                    "WHERE DAY(P.PAYMENT_TIME) <> 1 " +
                    "ORDER BY P.AMOUNT DESC LIMIT 1;";

            Map<String, String> queryBody = new HashMap<>();
            queryBody.put("finalQuery", finalSql);

            // ---------- STEP 3: Submit SQL to the webhook with Authorization ----------
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Authorization", accessToken); // As per PDF example

            HttpEntity<Map<String, String>> entity = new HttpEntity<>(queryBody, headers);

            ResponseEntity<String> finalResponse;
            try {
                finalResponse = restTemplate.exchange(webhookUrl, HttpMethod.POST, entity, String.class);
            } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
                // Some setups require "Bearer " prefix. Retry once.
                headers.set("Authorization", "Bearer " + accessToken);
                entity = new HttpEntity<>(queryBody, headers);
                finalResponse = restTemplate.exchange(webhookUrl, HttpMethod.POST, entity, String.class);
            }

            System.out.println("✅ Submission Successful!");
            System.out.println("Response status: " + finalResponse.getStatusCode());
            System.out.println("Response body: " + finalResponse.getBody());

        } catch (Exception e) {
            System.err.println("❌ Error occurred: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
