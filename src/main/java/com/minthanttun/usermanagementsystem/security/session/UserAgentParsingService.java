package com.minthanttun.usermanagementsystem.security.session;

import org.springframework.stereotype.Service;
import ua_parser.Client;
import ua_parser.Parser;

@Service
public class UserAgentParsingService {

    private final Parser uaParser = new Parser();

    public String describeDevice(String rawUserAgent) {
        if (rawUserAgent == null || rawUserAgent.isBlank()) {
            return "Unknown device";
        }

        Client client = uaParser.parse(rawUserAgent);

        String browser = client.userAgent.family;
        String os = client.os.family;

        if (isUnknown(browser) && isUnknown(os)) {
            return "Unknown device";
        }

        if (isUnknown(os)) {
            return browser;
        }

        if (isUnknown(browser)) {
            return os;
        }

        return browser + " on " + os;
    }

    private boolean isUnknown(String value) {
        return value == null || value.equalsIgnoreCase("Other");
    }
}
