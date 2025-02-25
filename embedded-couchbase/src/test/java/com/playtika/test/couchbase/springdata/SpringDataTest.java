/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Playtika
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.playtika.test.couchbase.springdata;

import com.couchbase.client.java.AsyncBucket;
import com.couchbase.client.java.Bucket;
import com.playtika.test.common.operations.NetworkTestOperations;
import com.playtika.test.couchbase.EmbeddedCouchbaseBootstrapConfigurationTest;
import org.assertj.core.data.Offset;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.List;
import java.util.concurrent.Callable;

import static com.playtika.test.couchbase.CouchbaseProperties.BEAN_NAME_EMBEDDED_COUCHBASE;
import static java.time.Duration.ofMillis;
import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class SpringDataTest extends EmbeddedCouchbaseBootstrapConfigurationTest {

    @Autowired
    TestDocumentRepository documentRepository;

    @Autowired
    ConfigurableListableBeanFactory beanFactory;

    @Autowired
    NetworkTestOperations couchbaseNetworkTestOperations;

    @Autowired
    Bucket bucket;

    @Test
    public void springDataShouldWork() throws Exception {
        String key = "test::1";
        String value = "myvalue";
        assertThat(documentRepository).isNotNull();
        assertThat(documentRepository.existsById(key)).isFalse();

        TestDocument testDocument = saveDocument(key, value);

        assertThat(documentRepository.findById(key).get()).isEqualTo(testDocument);
    }

    @Test
    public void shouldEmulateNetworkLatency() throws Exception {
        couchbaseNetworkTestOperations.withNetworkLatency(ofMillis(1500),
                () -> assertThat(durationOf(() -> documentRepository.findById("abc")))
                        .isCloseTo(1500L, Offset.offset(100L))
        );

        assertThat(durationOf(() -> documentRepository.findById("abc")))
                .isLessThan(100L);
    }

    @Test
    public void n1q1ShouldWork() throws Exception {
        bucket.bucketManager().createN1qlPrimaryIndex(true, false);

        String title = "some query title";
        saveDocument("test::2", "custom value");
        saveDocument("test::3", title);
        saveDocument("test::4", title);

        List<TestDocument> resultList = documentRepository.findByTitle(title);

        assertThat(resultList.size()).isEqualTo(2);
    }

    @Test
    public void shouldSetupDependsOnForNewClient() throws Exception {
        String[] beanNamesForType = beanFactory.getBeanNamesForType(Bucket.class);
        assertThat(beanNamesForType)
                .as("New sync client should be present")
                .hasSize(1)
                .contains("couchbaseBucket");
        asList(beanNamesForType).forEach(this::hasDependsOn);

        beanNamesForType = beanFactory.getBeanNamesForType(AsyncBucket.class);
        assertThat(beanNamesForType)
                .as("New async client should be present")
                .hasSize(1)
                .contains("asyncCouchbaseBucket");
        asList(beanNamesForType).forEach(this::hasDependsOn);
    }

    private void hasDependsOn(String beanName) {
        assertThat(beanFactory.getBeanDefinition(beanName).getDependsOn())
                .isNotNull()
                .isNotEmpty()
                .contains(BEAN_NAME_EMBEDDED_COUCHBASE);
    }

    private TestDocument saveDocument(String key, String value) {
        TestDocument testDocument = TestDocument.builder()
                .key(key)
                .title(value)
                .build();
        documentRepository.save(testDocument);
        return testDocument;
    }

    private static long durationOf(Callable<?> op) throws Exception {
        long start = System.currentTimeMillis();
        op.call();
        return System.currentTimeMillis() - start;
    }
}
