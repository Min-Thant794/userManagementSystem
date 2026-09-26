package com.minthanttun.usermanagementsystem.security.session;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.test.util.ReflectionTestUtils;
import ua_parser.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("remaining-unit")
public class UserAgentParsingServiceTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void missingUserAgentReturnsUnknownWithoutParsing(String input) {
        Parser parser = mock(Parser.class);
        var service = new UserAgentParsingService();
        ReflectionTestUtils.setField(service, "uaParser", parser);
        assertThat(service.describeDevice(input)).isEqualTo("Unknown device");
        verifyNoInteractions(parser);
    }

    @ParameterizedTest
    @CsvSource(value = {"Chrome,Windows,Chrome on Windows", "Chrome,Other,Chrome", "Other,Linux,Linux",
            "Other,Other,Unknown device", "NULL,NULL,Unknown device", "Chrome,NULL,Chrome", "NULL,Linux,Linux",
            "other,OTHER,Unknown device"}, nullValues = "NULL")
    void formatsParsedBrowserAndOsBranches(String browser, String os, String expected) {
        Parser parser = mock(Parser.class);
        when(parser.parse("fixture-agent")).thenReturn(new Client(
                new UserAgent(browser, null, null, null), new OS(os, null, null, null, null), Device.OTHER));
        var service = new UserAgentParsingService();
        ReflectionTestUtils.setField(service, "uaParser", parser);
        assertThat(service.describeDevice("fixture-agent")).isEqualTo(expected);
        verify(parser).parse("fixture-agent");
    }

    @Test
    void actualParserRecognizesRepresentativeBrowser() {
        String agent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
        assertThat(new UserAgentParsingService().describeDevice(agent)).isEqualTo("Chrome on Windows");
    }

    @Test
    void actualParserHandlesUnrecognizedText() {
        assertThat(new UserAgentParsingService().describeDevice("unrecognizable-fixture-agent")).isEqualTo("Unknown device");
    }
}
