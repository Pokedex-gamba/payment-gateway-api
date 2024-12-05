package com.example.paymentgateway.controller;


import com.example.paymentgateway.DTO.PayPalCreateResponseDTO;
import com.example.paymentgateway.DTO.PayPalExecuteRequestDTO;
import com.example.paymentgateway.DTO.PaymentRequestDTO;
import com.example.paymentgateway.entity.Token;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Base64;

@RestController
public class MainController {

    @Value("${paypal.client.id}")
    private String clientId;

    @Value("${paypal.client.secret}")
    private String clientSecret;

    private WebClient.Builder builder;


    /*@ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = Token.class)))
                    @ApiResponse(responseCode = "404",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(example = "{\"message\" : \"No user wallet found\"}")))
            }
    )*/
    @GetMapping(path = "/info")
    public String getBalance() {
        WebClient webClient = builder.baseUrl("https://api-m.sandbox.paypal.com/").build();
        Token token = getAccessToken();
        WebClient.ResponseSpec retrieve = webClient.get()
                .uri("v1/identity/openidconnect/userinfo?schema=openid")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccess_token())
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .retrieve();
        Mono<String> stringMono = retrieve.bodyToMono(String.class);
        return stringMono.block();
    }

    @GetMapping("/createPayment")
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequestDTO paymentRequest) {
        Token token = getAccessToken();
        WebClient webClient = builder.baseUrl("https://api-m.sandbox.paypal.com/v1").build();

        String requestBody = """
        {
          "intent": "sale",
          "payer": {
            "payment_method": "paypal"
          },
          "transactions": [
            {
              "amount": {
                "total": "%s",
                "currency": "%s"
              },
              "description": "%s"
            }
          ],
          "redirect_urls": {
            "return_url": "https://your-frontend-app.com/success",
            "cancel_url": "https://your-frontend-app.com/cancel"
          }
        }
        """.formatted(paymentRequest.getAmount(), paymentRequest.getCurrency(), paymentRequest.getDescription());

        WebClient.ResponseSpec responseSpec = webClient.post()
                .uri("/payments/payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccess_token())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(requestBody)
                .retrieve();

        String response = responseSpec.bodyToMono(String.class).block();

        if(response != null) {
            PayPalCreateResponseDTO formatedResponse = formatedJson(response);
            return ResponseEntity.ok(formatedResponse.getApprovalUrl());
        }
        //return response;
        return ResponseEntity.notFound().build();
    }

    @GetMapping("/execute")
    public String executePayment(@RequestBody PayPalExecuteRequestDTO executeRequest) {
        Token token = getAccessToken();

        WebClient webClient = builder
                .baseUrl("https://api-m.sandbox.paypal.com/v1")
                .build();

        String requestBody = """
        {
          "payer_id": "%s"
        }
        """.formatted(executeRequest.getPayerId());

        WebClient.ResponseSpec responseSpec = webClient.post()
                .uri("/payments/payment/" + executeRequest.getPaymentId() + "/execute")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccess_token())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(requestBody)
                .retrieve();

        String response = responseSpec.bodyToMono(String.class).block();

        return response;
    }

    private Token getAccessToken() {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://api-m.sandbox.paypal.com/v1")
                .build();

        String basicAuth = clientId + ":" + clientSecret;
        String encodedAuth = new String(Base64.getEncoder().encode(basicAuth.getBytes()));

        WebClient.ResponseSpec responseSpec = webClient.post()
                .uri("/oauth2/token")
                .header(HttpHeaders.AUTHORIZATION, "Basic " + encodedAuth)
                .header(HttpHeaders.CONTENT_TYPE, "application/x-www-form-urlencoded")
                .bodyValue("grant_type=client_credentials")
                .retrieve();

        Mono<Token> responseMono = responseSpec.bodyToMono(Token.class);
        return responseMono.block();
    }

    private PayPalCreateResponseDTO formatedJson(String json) {
        JSONObject jsonResponse = new JSONObject(json);

        String paymentId = jsonResponse.getString("id");
        String state = jsonResponse.getString("state");
        String paymentMethod = jsonResponse.getJSONObject("payer").getString("payment_method");

        JSONObject transaction = jsonResponse.getJSONArray("transactions").getJSONObject(0);
        String amount = transaction.getJSONObject("amount").getString("total");
        String currency = transaction.getJSONObject("amount").getString("currency");
        String description = transaction.getString("description");

        JSONArray links = jsonResponse.getJSONArray("links");
        String approvalUrl = null;
        for (int i = 0; i < links.length(); i++) {
            JSONObject link = links.getJSONObject(i);
            if ("approval_url".equals(link.getString("rel"))) {
                approvalUrl = link.getString("href");
                break;
            }
        }
        PayPalCreateResponseDTO responseObject = new PayPalCreateResponseDTO();
        responseObject.setPaymentId(paymentId);
        responseObject.setState(state);
        responseObject.setPaymentMethod(paymentMethod);
        responseObject.setAmount(amount);
        responseObject.setCurrency(currency);
        responseObject.setDescription(description);
        responseObject.setApprovalUrl(approvalUrl);

        return responseObject;
    }

    @Autowired
    public void setBuilder(WebClient.Builder builder) {
        this.builder = builder;
    }
}