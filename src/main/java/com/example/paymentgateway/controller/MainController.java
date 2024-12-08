package com.example.paymentgateway.controller;

import com.example.paymentgateway.DTO.PayPalCreateResponseDTO;
import com.example.paymentgateway.DTO.PayPalExecuteRequestDTO;
import com.example.paymentgateway.DTO.PaymentRequestDTO;
import com.example.paymentgateway.entity.PaymentHistory;
import com.example.paymentgateway.entity.Token;
import com.example.paymentgateway.service.KeyLoaderService;
import com.example.paymentgateway.service.PaymentGatewayService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.security.PublicKey;
import java.util.*;
import java.util.stream.Collectors;

@RestController
public class MainController {

    @Value("${paypal.client.id}")
    private String clientId;

    @Value("${paypal.client.secret}")
    private String clientSecret;

    private WebClient.Builder builder;

    private KeyLoaderService keyLoaderService;

    private PaymentGatewayService paymentGatewayService;

    @Value("${money.manager.api.url}")
    private String moneyManagerApiUrl;

    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "201"),
                    @ApiResponse(responseCode = "400",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(example = "{\"message\" : \"Amount must be greater than 0\"}"))),
                    @ApiResponse(responseCode = "501")
            }
    )
    @GetMapping("/createPayment")
    public ResponseEntity<?> createPayment(@RequestBody PaymentRequestDTO paymentRequest) {
        if (paymentRequest.getAmount() <= 0) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Amount must be greater than 0");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
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
                        "currency": "CZK"
                      },
                      "description": "Pokémon Gamba Payment: %s"
                    }
                  ],
                  "redirect_urls": {
                    "return_url": "%s",
                    "cancel_url": "%s"
                  }
                }
                """.formatted(paymentRequest.getAmount(), paymentRequest.getDescription().replace('"', '\''),
                    paymentRequest.getSuccessUrl().replace("\"", "%22"),
                    paymentRequest.getCancelUrl().replace("\"", "%22"));

        WebClient.ResponseSpec responseSpec = webClient.post()
                .uri("/payments/payment")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.getAccess_token())
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .bodyValue(requestBody)
                .retrieve();

        String response = responseSpec.bodyToMono(String.class).block();

        if (response != null) {
            PayPalCreateResponseDTO formatedResponse = formattedJson(response);
            Map<String, String> responses = new HashMap<>();
            responses.put("approval_url", formatedResponse.getApprovalUrl() );
            return ResponseEntity.status(HttpStatus.CREATED).body(responses);
        }
        return ResponseEntity.internalServerError().build();
    }
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200"),
                    @ApiResponse(responseCode = "500")
            }
    )
    @GetMapping("/executePayment")
    public ResponseEntity<?> executePayment(@RequestHeader(HttpHeaders.AUTHORIZATION) String userToken, @RequestBody PayPalExecuteRequestDTO executeRequest) {
        String userId = getUserIdFromToken(userToken);
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

        if(response != null) {
            PaymentHistory paymentHistory = formattedHistory(response, userId);
            paymentGatewayService.insertPaymentHistory(paymentHistory);
            addMoney(userToken, (int)paymentHistory.getAmount()).bodyToMono(String.class).block();
            return ResponseEntity.status(HttpStatus.OK).build();
        }
        return ResponseEntity.internalServerError().build();
    }
    @ApiResponses(
            value = {
                    @ApiResponse(responseCode = "200",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(implementation = PaymentHistory.class))),
                    @ApiResponse(responseCode = "404",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(example = "{\"message\" : \"User not found\"}"))),
            }
    )
    @GetMapping("/paymentHistory")
    public ResponseEntity<?> PaymentHistory(@RequestHeader(HttpHeaders.AUTHORIZATION) String userToken) {
        String userId = getUserIdFromToken(userToken);
        Map<String, String> response = new HashMap<>();
        List<PaymentHistory> paymentHistory = paymentGatewayService.findAllByUserId(userId);
        if(paymentHistory == null){
            response.put("message", "User not found");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }

        return ResponseEntity.ok(paymentHistory);
    }

    private String getUserIdFromToken(String authHeader) {
        String token = authHeader.replace("Bearer", "").trim();

        PublicKey publicKey;
        try {
            String path = MainController.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
            File publicKeyFile = new File(path, "decoding_key");
            if (!publicKeyFile.exists()) {
                return "aaa";
            }
            BufferedReader reader = new BufferedReader(new FileReader(publicKeyFile));
            String publicKeyContent = reader.lines().collect(Collectors.joining("\n"));
            reader.close();
            publicKey = keyLoaderService.getPublicKey(publicKeyContent);
        } catch (Exception e) {
            return "bbb";
        }

        Claims claims = Jwts.parser().verifyWith(publicKey).build().parseSignedClaims(token).getPayload();

        String userId = claims.get("user_id", String.class);
        if (userId == null) {
            return "ccc";
        }

        return userId;
    }

    public WebClient.ResponseSpec addMoney(String authHeader, int balance) {
        WebClient webClient = builder.baseUrl(moneyManagerApiUrl).build();
        return webClient.get()
                .uri("/modifyBalance/" + balance)
                .header(HttpHeaders.AUTHORIZATION, authHeader)
                .retrieve();
    }

    @Autowired
    public void setKeyLoaderService(KeyLoaderService keyLoaderService) {
        this.keyLoaderService = keyLoaderService;
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

    private PayPalCreateResponseDTO formattedJson(String json) {
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

    private PaymentHistory formattedHistory(String json, String userId) {
        JSONObject jsonObject = new JSONObject(json);
        String id = jsonObject.getString("id");
        String state = jsonObject.getString("state");
        JSONArray transactions = jsonObject.getJSONArray("transactions");
        JSONObject transaction = transactions.getJSONObject(0);
        JSONObject amount = transaction.getJSONObject("amount");
        String total = amount.getString("total");
        String currency = amount.getString("currency");

        PaymentHistory paymentHistory = new PaymentHistory();
        paymentHistory.setPaymentId(id);
        paymentHistory.setUserId(userId);
        paymentHistory.setAmount(Float.parseFloat(total));
        paymentHistory.setCurrency(currency);
        paymentHistory.setStatus(state);

        return paymentHistory;
    }

    @Autowired
    public void setBuilder(WebClient.Builder builder) {
        this.builder = builder;
    }

    @Autowired
    public void setPaymentGatewayService(PaymentGatewayService paymentGatewayService) {
        this.paymentGatewayService = paymentGatewayService;
    }
}