package com.ezcloudstore.infra;

import software.amazon.awscdk.Duration;
import software.amazon.awscdk.RemovalPolicy;
import software.amazon.awscdk.Stack;
import software.amazon.awscdk.StackProps;
import software.amazon.awscdk.services.dynamodb.Attribute;
import software.amazon.awscdk.services.dynamodb.AttributeType;
import software.amazon.awscdk.services.dynamodb.BillingMode;
import software.amazon.awscdk.services.dynamodb.GlobalSecondaryIndexProps;
import software.amazon.awscdk.services.dynamodb.ProjectionType;
import software.amazon.awscdk.services.dynamodb.Table;
import software.amazon.awscdk.services.s3.BlockPublicAccess;
import software.amazon.awscdk.services.s3.Bucket;
import software.amazon.awscdk.services.s3.BucketEncryption;
import software.amazon.awscdk.services.s3.CorsRule;
import software.amazon.awscdk.services.s3.HttpMethods;
import software.amazon.awscdk.services.s3.LifecycleRule;
import software.amazon.awscdk.services.s3.StorageClass;
import software.amazon.awscdk.services.s3.Transition;
import software.constructs.Construct;

import java.util.List;

/**
 * Data that must survive redeploys: the single DynamoDB table (ADR-0002)
 * and the versioned files bucket (ADR-0003). RemovalPolicy.RETAIN on both.
 * Lifecycle transitions (IA at 75 days, Glacier at 1 year) mirror the
 * documented v1 data-lifecycle story.
 */
public class StatefulStack extends Stack {

    private final Table table;
    private final Bucket filesBucket;

    public StatefulStack(Construct scope, String id, StackProps props) {
        super(scope, id, props);

        table = Table.Builder.create(this, "Table")
                .tableName("ezcloudstore")
                .partitionKey(Attribute.builder().name("PK").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("SK").type(AttributeType.STRING).build())
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .timeToLiveAttribute("expiresAtEpoch")
                .removalPolicy(RemovalPolicy.RETAIN)
                .build();

        table.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
                .indexName("GSI1")
                .partitionKey(Attribute.builder().name("GSI1PK").type(AttributeType.STRING).build())
                .sortKey(Attribute.builder().name("GSI1SK").type(AttributeType.STRING).build())
                .projectionType(ProjectionType.ALL)
                .build());

        filesBucket = Bucket.Builder.create(this, "FilesBucket")
                .versioned(true)
                .encryption(BucketEncryption.S3_MANAGED)
                .blockPublicAccess(BlockPublicAccess.BLOCK_ALL)
                .enforceSsl(true)
                .removalPolicy(RemovalPolicy.RETAIN)
                .cors(List.of(CorsRule.builder()
                        .allowedMethods(List.of(HttpMethods.PUT, HttpMethods.GET, HttpMethods.HEAD))
                        .allowedOrigins(List.of("*")) // tightened to the SPA origin in WebStack wiring
                        .allowedHeaders(List.of("*"))
                        .maxAge(3600)
                        .build()))
                .lifecycleRules(List.of(LifecycleRule.builder()
                        .transitions(List.of(
                                Transition.builder()
                                        .storageClass(StorageClass.INFREQUENT_ACCESS)
                                        .transitionAfter(Duration.days(75))
                                        .build(),
                                Transition.builder()
                                        .storageClass(StorageClass.GLACIER)
                                        .transitionAfter(Duration.days(365))
                                        .build()))
                        .build()))
                .build();
    }

    public Table table() {
        return table;
    }

    public Bucket filesBucket() {
        return filesBucket;
    }
}
