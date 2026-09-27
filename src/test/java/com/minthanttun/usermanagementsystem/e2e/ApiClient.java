package com.minthanttun.usermanagementsystem.e2e;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

// Uses real TCP HTTP, supports PATCH, and never follows redirects silently.
final class ApiClient implements AutoCloseable {
    private final URI base;
    private final ObjectMapper json;
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).version(HttpClient.Version.HTTP_1_1).build();
    ApiClient(URI base,ObjectMapper json) { this.base=base; this.json=json; }

    Reply call(String method,String path,Object body,String access,String refresh) throws Exception {
        var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(15))
                .header("Accept","application/json, application/problem+json");
        if(access!=null) builder.header("Authorization","Bearer "+access);
        // Explicit cookie handling, not a browser cookie jar. A stale token can be replayed deliberately.
        if(refresh!=null) builder.header("Cookie","refreshToken="+refresh);
        var publisher=HttpRequest.BodyPublishers.noBody();
        if(body!=null) {
            builder.header("Content-Type","application/json");
            publisher=HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body),StandardCharsets.UTF_8);
        }
        var response=client.send(builder.method(method,publisher).build(),HttpResponse.BodyHandlers.ofString());
        String raw=response.body();
        JsonNode parsed=json.nullNode();
        if(!raw.isBlank() && response.headers().firstValue("Content-Type").orElse("").contains("json"))
            parsed=json.readTree(raw);
        return new Reply(method+" "+path,response.statusCode(),response.headers(),parsed);
    }
    record Reply(String request,int status,HttpHeaders headers,JsonNode body) {
        Reply expect(int expected) {
            // Avoid dumping response tokens into test logs on failure.
            assertThat(status).as("%s status (problem title: %s)",request,body.path("title").asText())
                    .isEqualTo(expected);
            return this;
        }
        String cookieHeader() {
            return headers.allValues("Set-Cookie").stream().filter(v -> v.startsWith("refreshToken="))
                    .findFirst().orElseThrow(() -> new AssertionError("Missing refresh cookie: "+request));
        }
        String refreshCookie() {
            return cookieHeader().split(";",2)[0].substring("refreshToken=".length());
        }
    }
    @Override public void close() { client.close(); }
}