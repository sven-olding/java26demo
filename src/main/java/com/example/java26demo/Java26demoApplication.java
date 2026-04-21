package com.example.java26demo;

import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.UnsupportedProtocolVersionException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Random;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import jdk.incubator.vector.FloatVector;
import jdk.incubator.vector.VectorOperators;
import jdk.incubator.vector.VectorSpecies;

import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SuppressWarnings({"preview", "incubating"})
@SpringBootApplication
public class Java26demoApplication implements CommandLineRunner {


    // JEP 529 - Vector API
    // https://openjdk.org/jeps/529
    private static final VectorSpecies<Float> SPECIES = FloatVector.SPECIES_PREFERRED;

    /** Scalar (loop-based) dot product */
    private static float scalarDot(float[] a, float[] b) {
        float sum = 0f;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    /**
     * Vectorized dot product using the Vector API — analogous to np.dot() in Python.
     * Uses 4 independent accumulators to hide FMA latency (the same trick the JIT
     * applies automatically when auto-vectorizing a scalar loop).
     */
    private static float vectorDot(float[] a, float[] b) {
        int i = 0;
        int stride = SPECIES.length() * 4;
        int upperBound = a.length - (a.length % stride);

        // 4 independent accumulators break the loop-carried dependency on sum,
        // allowing the CPU to issue multiple FMAs per cycle.
        var sum0 = FloatVector.zero(SPECIES);
        var sum1 = FloatVector.zero(SPECIES);
        var sum2 = FloatVector.zero(SPECIES);
        var sum3 = FloatVector.zero(SPECIES);
        for (; i < upperBound; i += stride) {
            int s = SPECIES.length();
            sum0 = FloatVector.fromArray(SPECIES, a, i      ).fma(FloatVector.fromArray(SPECIES, b, i      ), sum0);
            sum1 = FloatVector.fromArray(SPECIES, a, i +   s).fma(FloatVector.fromArray(SPECIES, b, i +   s), sum1);
            sum2 = FloatVector.fromArray(SPECIES, a, i + 2*s).fma(FloatVector.fromArray(SPECIES, b, i + 2*s), sum2);
            sum3 = FloatVector.fromArray(SPECIES, a, i + 3*s).fma(FloatVector.fromArray(SPECIES, b, i + 3*s), sum3);
        }
        var sum = sum0.add(sum1).add(sum2).add(sum3);

        // scalar tail for elements that don't fill a full stride
        int tail = SPECIES.loopBound(a.length - i) + i;
        for (; i < tail; i += SPECIES.length()) {
            sum = FloatVector.fromArray(SPECIES, a, i).fma(FloatVector.fromArray(SPECIES, b, i), sum);
        }
        float result = sum.reduceLanes(VectorOperators.ADD);
        for (; i < a.length; i++) {
            result += a[i] * b[i];
        }
        return result;
    }

    private void demoVectorApi() throws InterruptedException {
        // a=[1,2,3,4], b=[-1,4,3,2] → dot = -1+8+9+8 = 24
        float[] a = {1, 2, 3, 4};
        float[] b = {-1, 4, 3, 2};
        logger.get().info("[Vector API] scalarDot([1,2,3,4], [-1,4,3,2]) = {}", scalarDot(a, b));
        logger.get().info("[Vector API] vectorDot([1,2,3,4], [-1,4,3,2]) = {}", vectorDot(a, b));

        // Large-array benchmark (10M floats)
        int n = 10_000_000;
        var rng = new Random(1);
        float[] bigA = new float[n];
        float[] bigB = new float[n];
        for (int i = 0; i < n; i++) {
            bigA[i] = rng.nextFloat();
            bigB[i] = rng.nextFloat();
        }

        // Let the JIT finish compiling Spring Boot's classes before we benchmark.
        // NOTE: for best timings, run the packaged JAR rather than spring-boot:run:
        //   mvn package && java --enable-preview --add-modules jdk.incubator.vector -jar target/java26demo-*.jar
        Thread.sleep(2000);

        // Warm-up on the actual benchmark arrays so the JIT profile matches
        for (int w = 0; w < 5; w++) {
            vectorDot(bigA, bigB);
            scalarDot(bigA, bigB);
        }

        // Take the minimum over several runs — removes one-off GC / scheduling noise
        long minVectorMs = Long.MAX_VALUE, minScalarMs = Long.MAX_VALUE;
        float vectorResult = 0, scalarResult = 0;
        for (int run = 0; run < 5; run++) {
            long tic = System.nanoTime();
            vectorResult = vectorDot(bigA, bigB);
            minVectorMs = Math.min(minVectorMs, (System.nanoTime() - tic) / 1_000_000);

            tic = System.nanoTime();
            scalarResult = scalarDot(bigA, bigB);
            minScalarMs = Math.min(minScalarMs, (System.nanoTime() - tic) / 1_000_000);
        }

        String speedupMsg = minVectorMs < minScalarMs
                ? String.format("vector ~%.1fx faster", (float) minScalarMs / Math.max(1, minVectorMs))
                : String.format("scalar ~%.1fx faster (JIT auto-vectorized the loop too)", (float) minVectorMs / Math.max(1, minScalarMs));

        logger.get().info("[Vector API] SIMD species: {} ({} float lanes)", SPECIES, SPECIES.length());
        logger.get().info("[Vector API] vectorDot(bigA, bigB) = {}  →  {} ms (best of 5)", String.format("%.4f", vectorResult), minVectorMs);
        logger.get().info("[Vector API] scalarDot(bigA, bigB) = {}  →  {} ms (best of 5)", String.format("%.4f", scalarResult), minScalarMs);
        logger.get().info("[Vector API] {}", speedupMsg);
    }

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

        // JEP 529 - Vector API
        // https://openjdk.org/jeps/529
        demoVectorApi();
    }
}
