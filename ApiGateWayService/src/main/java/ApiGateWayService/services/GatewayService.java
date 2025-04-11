package ApiGateWayService.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.ServiceInstance;
import org.springframework.cloud.netflix.eureka.EurekaDiscoveryClient;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class GatewayService {

    @Autowired
    private RedisService redisService;

    @Autowired
    private RestTemplate restTemplate;

    private static final String AUTHORIZATION_HEADER = HttpHeaders.AUTHORIZATION;
    private static final String BEARER_PREFIX = "Bearer ";
    @Autowired
    private EurekaDiscoveryClient discoveryClient;


    public ResponseEntity<?> handleRequests(
            HttpMethod method, String endPoint, HttpHeaders headers, byte[] body, Map<String, String> queryParams) throws Exception {

        HttpHeaders requestHeaders = new HttpHeaders();
        requestHeaders.addAll(headers);
        if (isAuthLoginCall(endPoint)){
            return loginUser(endPoint, headers, body);
        } else {
            String authToken = getAuthTokenFromHeaders(headers);
            if (authToken != null) {
                // TODO : Dont call the auth service, decode the token here.
                Boolean isTokenValid = validateAuthToken(authToken, requestHeaders);
                if (!isTokenValid) {
                    throw new Exception("Invalid token");
                } else {
                    System.out.println("token valid!");
                    // TODO : Need to decode the token and get user details and add it to headers.
                    // Setting user data in request headers.
                    // requestHeaders.set("X-USER-ID", String.valueOf(userData.getId()));
                    // requestHeaders.set("X-USER-USERNAME", userData.getUsername());
                    // requestHeaders.set("X-USER-SCOPES", Arrays.toString(userData.getUserScopes()));
                }
            } else {
                return new ResponseEntity<>("Not authorized", HttpStatus.UNAUTHORIZED);
            }
            try {
                return connectWithBackend(method, endPoint, requestHeaders, body, queryParams);
            } catch (Exception e) {
                return new ResponseEntity<>("Not authorized", HttpStatus.UNAUTHORIZED);
            }
        }
    }

    public boolean isAuthLoginCall(String endPoint){
        return endPoint.equals("/spring/auth/login");
    }

    public ResponseEntity<Object> loginUser(String endpoint, HttpHeaders headers, byte[] body) throws Exception {
        ResponseEntity<Object> response = connectWithBackend(
                HttpMethod.POST, endpoint, headers, body, null
        );
        Object responseBody = response.getBody();
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, String> responseData = objectMapper.convertValue(
                responseBody, new TypeReference<Map<String, String>>() {} // Note the {} for anonymous subclass
        );
        redisService.save(responseData.get("token"), 1, 24, TimeUnit.HOURS);
        return new ResponseEntity<>(responseData, HttpStatus.OK);
    }

    public Boolean validateAuthToken(String authToken, HttpHeaders headers) throws Exception {
        Integer cacheFound = redisService.get(authToken);
        if (cacheFound == null) {
            // Token is not there in the cache. Need to call auth service for validation of token.
            ResponseEntity<Object> response = connectWithBackend(
                    HttpMethod.POST, "/spring/auth/validateToken", headers, null, null
            );
            if (response == null || response.getStatusCode() != HttpStatus.OK) {
                return false;
            } else {
                redisService.save(authToken, 1, 24, TimeUnit.HOURS);
                return true;
            }
        }
        return true;
    }

    public ResponseEntity<Object> connectWithBackend(
            HttpMethod method, String endPoint, HttpHeaders inputHeaders, byte[] body, Map<String, String> queryParams
    ) throws Exception {

        // Determine Target Service based on endpoint prefix
        String serviceId;
        if (endPoint.startsWith("/spring/auth")) {
            serviceId = "Auth-Service";
        } else if (endPoint.startsWith("/spring/users")) {
            serviceId = "User-Service";
        } else if (endPoint.startsWith("/spring/products")) {
            serviceId = "Product-Service";
        } else {
            throw new Exception("Endpoint route not found: " + endPoint);
        }

        // Discover Service Instance
        ServiceInstance serviceInstance = discoveryClient.getInstances(serviceId).stream().findFirst().orElse(null);
        if (serviceInstance == null) {
            throw new Exception(serviceId + "not found in discoveryClient!");
        }
        // Getting base url of the service instance.
        String targetServiceBaseUrl = serviceInstance.getUri().toString();

        // Build Target URL with Query Parameters
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromHttpUrl(targetServiceBaseUrl).path(endPoint);
        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(uriBuilder::queryParam); // Adding query parameters
        }
        // Prepare Mutable Headers (Copy incoming headers)
        // Create a new mutable header map to avoid modifying potentially immutable input headers (Read only maybe)
        HttpHeaders mutableHeaders = new HttpHeaders();
        if (inputHeaders != null) {
            mutableHeaders.addAll(inputHeaders); // Copy all headers
        }
        HttpEntity<?> requestEntity;
        // Include body for methods that typically carry payloads
        if (method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH) {
            requestEntity = new HttpEntity<>(body, mutableHeaders);
        } else {
            requestEntity = new HttpEntity<>(mutableHeaders);
        }
        try {
            // Building the URI object which handles encoding properly
            URI uri = uriBuilder.build().toUri();
            return restTemplate.exchange(
                    uri,
                    method,
                    requestEntity,
                    Object.class // Keep response generic
            );
        } catch (HttpStatusCodeException e) {
            System.err.println(e.getStatusCode());
            System.err.println(e.getResponseBodyAsString());
            // Return the error response entity from the exception
            return ResponseEntity
                    .status(e.getStatusCode())
                    .headers(e.getResponseHeaders())
                    .body(e.getResponseBodyAsString()); // Return error body as String (or parse if needed)
        } catch (Exception e) {
            System.err.println(e.getMessage());
            // Re-throw or return a generic server error response
            throw new Exception("Error executing request to " + serviceId + ": " + e.getMessage(), e);
        }
    }

    public String getAuthTokenFromHeaders(HttpHeaders headers){
        String authToken = null;
        String authHeader = headers.getFirst(AUTHORIZATION_HEADER); // Get the Authorization header value
        if (StringUtils.hasText(authHeader) && authHeader.length() > BEARER_PREFIX.length()) {
            // Case-insensitive check for "Bearer " prefix
            if (authHeader.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
                authToken = authHeader.substring(BEARER_PREFIX.length()).trim();
            }
        }
        return authToken;
    }
}