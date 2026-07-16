package com.campusguinness;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class MinioContainerTest {

    private static final String MINIO_IMAGE = "minio/minio:RELEASE.2025-02-28T09-55-16Z";

    @Container
    static MinIOContainer minio = new MinIOContainer(MINIO_IMAGE);

    @Test
    void containerStartsAndIsAccessible() {
        assertThat(minio.isRunning()).isTrue();
    }

    @Test
    void canCreateAndVerifyBucket() throws Exception {
        MinioClient client = MinioClient.builder()
                .endpoint(minio.getS3URL())
                .credentials(minio.getUserName(), minio.getPassword())
                .build();

        String bucket = "test-bucket-" + System.currentTimeMillis();
        client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());

        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        assertThat(exists).isTrue();
    }
}
