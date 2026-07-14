package com.ezcloudstore.config;

import com.ezcloudstore.adapters.out.dynamodb.DynamoDbFileRepository;
import com.ezcloudstore.adapters.out.dynamodb.DynamoDbShareLinkRepository;
import com.ezcloudstore.adapters.out.id.SecureRandomIdGenerator;
import com.ezcloudstore.adapters.out.s3.S3FileStorage;
import com.ezcloudstore.domain.port.FileRepository;
import com.ezcloudstore.domain.port.FileStorage;
import com.ezcloudstore.domain.port.IdGenerator;
import com.ezcloudstore.domain.port.ShareLinkRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.time.Duration;

/**
 * Port implementations wired to real AWS. Credentials come from the runtime
 * environment (Lambda execution role in prod, aws-vault/SSO locally) —
 * never from configuration files (v1's sin, fixed).
 */
@Configuration
public class AwsConfig {

    @Bean
    public DynamoDbClient dynamoDbClient(@Value("${ezcloudstore.aws.region}") String region) {
        return DynamoDbClient.builder().region(Region.of(region)).build();
    }

    @Bean
    public S3Client s3Client(@Value("${ezcloudstore.aws.region}") String region) {
        return S3Client.builder().region(Region.of(region)).build();
    }

    @Bean
    public S3Presigner s3Presigner(@Value("${ezcloudstore.aws.region}") String region) {
        return S3Presigner.builder().region(Region.of(region)).build();
    }

    @Bean
    public FileRepository fileRepository(DynamoDbClient dynamo,
                                         @Value("${ezcloudstore.aws.table}") String table) {
        return new DynamoDbFileRepository(dynamo, table);
    }

    @Bean
    public ShareLinkRepository shareLinkRepository(DynamoDbClient dynamo,
                                                   @Value("${ezcloudstore.aws.table}") String table) {
        return new DynamoDbShareLinkRepository(dynamo, table);
    }

    @Bean
    public FileStorage fileStorage(S3Client s3, S3Presigner presigner,
                                   @Value("${ezcloudstore.aws.files-bucket}") String bucket,
                                   @Value("${ezcloudstore.presign-ttl-minutes:15}") long presignTtlMinutes) {
        return new S3FileStorage(s3, presigner, bucket, Duration.ofMinutes(presignTtlMinutes));
    }

    @Bean
    public IdGenerator idGenerator() {
        return new SecureRandomIdGenerator();
    }
}
