package com.membaza.api.users.service.email;

import com.membaza.api.users.service.text.TextService;
import lombok.Data;
import org.springframework.core.env.Environment;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.support.BasicAuthorizationInterceptor;
import org.springframework.http.converter.FormHttpMessageConverter;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;

/**
 * @author Emil Forslund
 * @since  1.0.0
 */
@Service
public final class EmailServiceImpl implements EmailService {

    private static final String EMAIL_FILE = "/mail/*.html";

    private final Environment env;
    private final TextService text;
    private final RestTemplate mailgun;

    EmailServiceImpl(Environment env,
                     TextService text) {

        this.env     = requireNonNull(env);
        this.text    = requireNonNull(text);
        this.mailgun = new RestTemplate();

        this.mailgun.setMessageConverters(asList(
            new FormHttpMessageConverter(),
            new StringHttpMessageConverter(),
            new MappingJackson2HttpMessageConverter()
        ));

        this.mailgun.getInterceptors().add(new BasicAuthorizationInterceptor(
            "api", env.getProperty("mailgun.apiKey")
        ));
    }

    @Override
    public void send(String to,
                     String toEmail,
                     String template,
                     String lang,
                     Map<String, String> args) {

        final String language = getLanguage(lang);
        final String subject  = text.get(template.replace('_', '.') + ".subject", language, args);
        final String body     = body(template, language, args);
        send(subject, to, toEmail, body);
    }

    private String body(String template, String language, Map<String, String> args) {
        final String templateFile = EMAIL_FILE.replace("*", template);

        try {
            return Files.lines(Paths.get(
                new PathMatchingResourcePatternResolver()
                    .getResource(templateFile)
                    .getURI()
            )).map(line -> text.format(line, language, args))
                .collect(joining("\n"));
        } catch (final IOException ex) {
            throw new IllegalArgumentException(
                "Could not find email template '" + templateFile + "'."
            );
        }
    }

    private void send(String subject, String to, String toEmail, String body) {
        final String url = "https://api.mailgun.net/v3/" +
            env.getProperty("mailgun.domain") + "/messages";

        final MultiValueMap<String, String> args = new LinkedMultiValueMap<>();
        args.put("subject", singletonList(subject));
        args.put("from",  singletonList(env.getProperty("service.email.sitename") +
                  " <" +  env.getProperty("service.email.sender") + ">"));
        args.put("to", singletonList(to + " <" + toEmail + ">"));
        args.put("html", singletonList(body));

        final ResponseEntity<MailGunResponse> response =
            mailgun.postForEntity(url, args, MailGunResponse.class);

        if (!response.getStatusCode().is2xxSuccessful()) {
            throw new RuntimeException(
                "Error delivering mail. Message: " +
                response.getBody().getMessage()
            );
        }
    }

    private String getLanguage(String lang) {
        if (lang == null) {
            return "en";
        } else {
            if (text.has(lang)) {
                return lang;
            } else {
                throw new IllegalArgumentException(
                    "'" + lang + "' is not a valid language."
                );
            }
        }
    }

    @Data
    private final static class MailGunResponse {
        private String message;
        private String id;
    }
}
