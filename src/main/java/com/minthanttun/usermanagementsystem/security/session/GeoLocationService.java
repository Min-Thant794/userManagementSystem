package com.minthanttun.usermanagementsystem.security.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Service
@Slf4j
public class GeoLocationService {

    private final RestClient restClient = RestClient.builder()
            .baseUrl("http://ip-api.com")
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public String describeLocation(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || isLocalOrPrivate(ipAddress)) {
            return "Local network";
        }

        try {
            String response = restClient.get()
                    .uri("/json/{ip}?fields=status,country,city", ipAddress)
                    .retrieve()
                    .body(String.class);

            JsonNode node = objectMapper.readTree(response);

            if ("success".equals(node.path("status").asText())) {
                String city = node.path("city").asText("");
                String country = node.path("country").asText("");
                return city.isBlank() ? country : city + ", " + country;
            }
        } catch (Exception e) {
            log.warn("Geolocation lookup failed for IP {}: {}", ipAddress);
        }

        return ipAddress;
    }

    private boolean isLocalOrPrivate(String ip) {
        return ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equals("::1")
                || ip.startsWith("192.168.") || ip.startsWith("10.");
    }
}
