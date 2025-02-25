package com.playtika.test.minio;

import com.playtika.test.common.operations.NetworkTestOperations;
import com.playtika.test.common.utils.ThrowingRunnable;
import io.minio.MinioClient;
import io.minio.errors.InvalidEndpointException;
import io.minio.errors.InvalidPortException;
import org.assertj.core.data.Offset;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.junit4.SpringRunner;

import java.net.URL;
import java.nio.charset.StandardCharsets;

import static java.time.Duration.ofMillis;
import static org.assertj.core.api.Assertions.assertThat;

@RunWith(SpringRunner.class)
@SpringBootTest(
        classes = EmbeddedMinioBootstrapConfigurationTest.MinioTestConfiguration.class
)
public class EmbeddedMinioBootstrapConfigurationTest {

    private static final String BUCKET = "my-test-bucket";

    @Autowired
    private MinioClient minioClient;

    @Autowired
    NetworkTestOperations minioNetworkTestOperations;

    @Before
    public void setUp() throws Exception {
        if (!minioClient.bucketExists(BUCKET)) {
            minioClient.makeBucket(BUCKET);
        }
    }

    @Test
    public void shouldSuccessfullyWriteAndReadData() throws Exception {
        writeFileToMinio("example.txt", getFilePath("example.txt"));

        String content = readFileFromMinio("example.txt");

        assertThat(content).isEqualTo("Hello Minio!");
    }

    @Test
    public void shouldEmulateLatency() throws Exception {
        minioNetworkTestOperations.withNetworkLatency(ofMillis(1500),
                () -> assertThat(durationOf(() -> writeFileToMinio("example.txt", getFilePath("example.txt"))))
                        .isCloseTo(1500L, Offset.offset(300L))
        );

        assertThat(durationOf(() -> writeFileToMinio("example.txt", getFilePath("example.txt"))))
                .isLessThan(100L);
    }

    private void writeFileToMinio(String fileName, String path) throws Exception {
        minioClient.putObject(BUCKET, fileName, path, null, null, null, null);
    }

    private String readFileFromMinio(String fileName) throws Exception {
        return convertStreamToString(minioClient.getObject(BUCKET, fileName));
    }

    private String getFilePath(String name) {
        URL resource = this.getClass().getClassLoader().getResource(name);
        if (resource == null) {
            throw new IllegalArgumentException("File " + name + " not found");
        }
        return resource.getFile();
    }

    private static String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is, StandardCharsets.UTF_8.name()).useDelimiter("\\A");
        return s.hasNext() ? s.next() : "";
    }

    private static long durationOf(ThrowingRunnable op) throws Exception {
        long start = System.currentTimeMillis();
        op.run();
        return System.currentTimeMillis() - start;
    }

    @EnableAutoConfiguration
    public static class MinioTestConfiguration {

        @Bean
        public MinioClient minioClient(
                @Value("${embedded.minio.port}") int port,
                @Value("${embedded.minio.accessKey}") String accessKey,
                @Value("${embedded.minio.secretKey}") String secretKey,
                @Value("${embedded.minio.region}") String region) throws InvalidPortException, InvalidEndpointException {
            return new MinioClient("http://localhost:" + port, accessKey, secretKey, region);
        }
    }
}