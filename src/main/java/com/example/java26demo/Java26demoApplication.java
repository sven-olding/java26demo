package com.example.java26demo;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.UnsupportedProtocolVersionException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SuppressWarnings("preview")
@SpringBootApplication
public class Java26demoApplication implements CommandLineRunner {

    // JEP 526 - Lazy Constants
    // https://openjdk.org/jeps/526
    private final LazyConstant<Logger> logger =
            LazyConstant.of(() -> LoggerFactory.getLogger(Java26demoApplication.class));
    private static final LazyConstant<SomeRepository> SOME_REPOSITORY = LazyConstant.of(SomeRepository::new);

    static SomeRepository someRepository() {
        return SOME_REPOSITORY.get();
    }

    static void main(String[] args) {
        SpringApplication.run(Java26demoApplication.class, args);
    }

    // Trust-all SSLContext for demo purposes only!
    private static SSLContext trustAllSslContext() throws Exception {
        TrustManager[] trustAll = new TrustManager[]{
            new X509TrustManager() {
                public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                public void checkClientTrusted(X509Certificate[] c, String a) { /* demo purposes */ }
                public void checkServerTrusted(X509Certificate[] c, String a) { /* demo purposes */ }
            }
        };
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, trustAll, new SecureRandom());
        return ctx;
    }

    private HttpClient buildHttpClient() throws Exception {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_3)
                    .sslContext(trustAllSslContext())
                    .build();
            logger.get().info("HTTP/3 is supported — racing HTTP/3 against HTTP/2");
            return client;
        } catch (UncheckedIOException e) {
            if (!(e.getCause() instanceof UnsupportedProtocolVersionException)) throw e;
            logger.get().warn("HTTP/3 not available (run with --enable-preview), falling back to HTTP/2");
            return HttpClient.newBuilder().sslContext(trustAllSslContext()).build();
        }
    }

    @Override
    public void run(String @NonNull ... args) throws Exception {
        logger.get().info("Application started with Java 26 features!");

        // JEP 517 - HTTP/3
        // https://openjdk.org/jeps/517
        try (var httpClient = buildHttpClient()) {
			HttpResponse<String> response = httpClient.send(
					HttpRequest.newBuilder()
							.uri(URI.create("https://www.google.com"))
							.build(),
					HttpResponse.BodyHandlers.ofString());
			logger.get().info("response status code: {}", response.statusCode());
			if (response.statusCode() == 200) {
				String body = response.body().substring(0, Math.min(1000, response.body().length())) + "...";
				logger.get()
						.info("response body: {}", body);
			}
		}
    }
}
