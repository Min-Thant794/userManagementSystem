package com.minthanttun.usermanagementsystem.e2e;

import com.fasterxml.jackson.databind.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;
import static org.assertj.core.api.Assertions.*;

// Reads messages actually delivered by the production JavaMailSender over SMTP.
final class MailpitInbox implements AutoCloseable {
    private final URI base;
    private final ObjectMapper json;
    private final Set<String> consumedIds=new HashSet<>();
    private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    MailpitInbox(URI base,ObjectMapper json) {
        assertThat(base.toString()).isEqualTo("http://127.0.0.1:18025");
        this.base=base; this.json=json;
    }
    private HttpResponse<String> request(String method,String path,String body) throws Exception {
        var builder=HttpRequest.newBuilder(base.resolve(path)).timeout(Duration.ofSeconds(5));
        if(body!=null) builder.header("Content-Type","application/json");
        return client.send(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():
                HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString());
    }
    void awaitReady() throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        do {
            try {
                if(request("GET","/api/v1/info",null).statusCode()==200) return;
            } catch(java.io.IOException notReady) {
                // The container may be running before its HTTP listener is ready.
            }
            Thread.sleep(100);
        } while(System.nanoTime()<deadline);
        throw new AssertionError("Mailpit is not ready at "+base+"; start compose.e2e.yml");
    }
    void clear() throws Exception {
        awaitReady();
        var response=request("DELETE","/api/v1/messages","{}");
        assertThat(response.statusCode()).as("Mailpit mailbox cleanup").isEqualTo(200);
        consumedIds.clear();
    }
    String awaitToken(String recipient,String subject,String expectedPath) throws Exception {
        long deadline=System.nanoTime()+Duration.ofSeconds(10).toNanos();
        do {
            var listing=request("GET","/api/v1/messages?start=0&limit=100",null);
            assertThat(listing.statusCode()).as("Mailpit message listing").isEqualTo(200);
            for(JsonNode summary:json.readTree(listing.body()).path("messages")) {
                String id=summary.path("ID").asText();
                boolean correctTo=StreamSupport.stream(summary.path("To").spliterator(),false)
                        .anyMatch(to -> recipient.equals(to.path("Address").asText()));
                if(consumedIds.contains(id)||!correctTo||!subject.equals(summary.path("Subject").asText())) continue;
                var message=request("GET","/api/v1/message/"+id,null);
                assertThat(message.statusCode()).isEqualTo(200);
                String text=json.readTree(message.body()).path("Text").asText();
                var matcher=Pattern.compile("https?://[^\\s]+").matcher(text);
                assertThat(matcher.find()).as("Email contains a token link").isTrue();
                URI link=URI.create(matcher.group());
                assertThat(link.getScheme()).isEqualTo("https");
                assertThat(link.getHost()).isEqualTo("frontend.example");
                assertThat(link.getPath()).isEqualTo(expectedPath);
                String raw=Arrays.stream(link.getRawQuery().split("&"))
                        .filter(part -> part.startsWith("token=")).map(part -> part.substring(6))
                        .map(part -> URLDecoder.decode(part,StandardCharsets.UTF_8)).findFirst().orElseThrow();
                assertThat(raw).isNotBlank(); consumedIds.add(id); return raw;
            }
            Thread.sleep(100); // Bounded condition polling, not a fixed wait for token expiry.
        } while(System.nanoTime()<deadline);
        throw new AssertionError("No new '"+subject+"' email delivered to "+recipient+" within 10 seconds");
    }
    @Override public void close() { client.close(); }
}