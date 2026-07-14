package com.ezcloudstore.adapters.out.dynamodb;

import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.BillingMode;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;

/**
 * Single-table layout (ADR-0002). One definition shared by tests and tooling;
 * the CDK stack mirrors it for the real table.
 *
 * <pre>
 * File metadata: PK=USER#owner   SK=FILE#id       GSI1PK=ENTITY#FILE GSI1SK=FILE#id
 * File version:  PK=FILE#id      SK=VERSION#seq
 * Share link:    PK=SHARE#token  SK=META          GSI1PK=FILE#id     GSI1SK=SHARE#token
 * </pre>
 */
public final class DynamoDbTableSchema {

    public static final String GSI1 = "GSI1";

    public static void createTable(DynamoDbClient dynamo, String tableName) {
        dynamo.createTable(t -> t
                .tableName(tableName)
                .billingMode(BillingMode.PAY_PER_REQUEST)
                .attributeDefinitions(
                        attr("PK"), attr("SK"), attr("GSI1PK"), attr("GSI1SK"))
                .keySchema(
                        KeySchemaElement.builder().attributeName("PK").keyType(KeyType.HASH).build(),
                        KeySchemaElement.builder().attributeName("SK").keyType(KeyType.RANGE).build())
                .globalSecondaryIndexes(GlobalSecondaryIndex.builder()
                        .indexName(GSI1)
                        .keySchema(
                                KeySchemaElement.builder().attributeName("GSI1PK").keyType(KeyType.HASH).build(),
                                KeySchemaElement.builder().attributeName("GSI1SK").keyType(KeyType.RANGE).build())
                        .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                        .build()));
        dynamo.waiter().waitUntilTableExists(w -> w.tableName(tableName));
    }

    private static AttributeDefinition attr(String name) {
        return AttributeDefinition.builder()
                .attributeName(name)
                .attributeType(ScalarAttributeType.S)
                .build();
    }

    private DynamoDbTableSchema() {
    }
}
