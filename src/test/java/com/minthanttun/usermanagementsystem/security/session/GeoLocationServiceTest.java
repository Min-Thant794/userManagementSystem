package com.minthanttun.usermanagementsystem.security.session;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.http.*;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.io.IOException;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

@Tag("remaining-unit")
public class GeoLocationServiceTest {
    private static final String IP = "203.0.113.7";
    private static final String URL = "http://ip-api.com/json/" + IP + "?fields=status,country,city";
    private GeoLocationService geoLocationService;
    private MockRestServiceServer mockRestServiceServer;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ip-api.com");
        mockRestServiceServer = MockRestServiceServer.bindTo(builder).build();
        geoLocationService = new GeoLocationService();
        ReflectionTestUtils.setField(geoLocationService, "restClient", builder.build());
    }

    @AfterEach
    void verifyRequests() {
        mockRestServiceServer.verify();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "127.0.0.1", "::1", "0:0:0:0:0:0:0:1", "192.168.1.2", "10.1.2.3"})
    void recognizedLocalAddressesDoNotMakeHttpRequests(String ip) {
        assertThat(geoLocationService.describeLocation(ip)).isEqualTo("Local network");
    }

    @Test
    void successfulLookupReturnsCityAndCountry() {
        mockRestServiceServer.expect(requestTo(URL)).andExpect(method(HttpMethod.GET)).andRespond(withSuccess(
                "{\"status\":\"success\",\"country\":\"Singapore\",\"city\":\"Singapore\"}", MediaType.APPLICATION_JSON
        ));
        assertThat(geoLocationService.describeLocation(IP)).isEqualTo("Singapore, Singapore");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"success\",\"country\":\"Singapore\"}",
            "{\"status\":\"success\",\"country\":\"Singapore\",\"city\":\"  \"}"})
    void missingOrBlankCityReturnsCountryOnly(String json) {
        mockRestServiceServer.expect(requestTo(URL)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
        assertThat(geoLocationService.describeLocation(IP)).isEqualTo("Singapore");
    }

    @ParameterizedTest
    @ValueSource(strings = {"{\"status\":\"fail\"}", "{}", "not-json", ""})
    void unsuccessfulOrMalformedResponseFallsBackToOriginalIp(String body) {
        mockRestServiceServer.expect(requestTo(URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
        assertThat(geoLocationService.describeLocation(IP)).isEqualTo(IP);
    }

    @Test
    void serverErrorFallsBackToOriginalIp() {
        mockRestServiceServer.expect(requestTo(URL)).andRespond(withServerError());
        assertThat(geoLocationService.describeLocation(IP)).isEqualTo(IP);
    }

    @Test
    void transportExceptionFallsBackToOriginalIp() {
        mockRestServiceServer.expect(requestTo(URL)).andRespond(request -> {
            throw new IOException("simulated network failure");
        });

        assertThat(geoLocationService.describeLocation(IP)).isEqualTo(IP);
    }
}
