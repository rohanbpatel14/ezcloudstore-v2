package com.ezcloudstore.adapters.out.dynamodb;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.ShareLink;
import com.ezcloudstore.domain.model.ShareToken;
import com.ezcloudstore.domain.port.ShareLinkRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.ezcloudstore.adapters.out.dynamodb.DynamoDbTableSchema.GSI1;

/**
 * Share links carry a DynamoDB TTL attribute (expiresAtEpoch) so expired
 * tokens are reaped by the table itself; resolve-time expiry stays enforced
 * in the domain (TTL deletion is eventual, not exact).
 */
public class DynamoDbShareLinkRepository implements ShareLinkRepository {

    private final DynamoDbClient dynamo;
    private final String table;

    public DynamoDbShareLinkRepository(DynamoDbClient dynamo, String table) {
        this.dynamo = dynamo;
        this.table = table;
    }

    private static String sharePk(ShareToken token) {
        return "SHARE#" + token.value();
    }

    private static AttributeValue s(String value) {
        return AttributeValue.fromS(value);
    }

    @Override
    public void save(ShareLink link) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(sharePk(link.token())));
        item.put("SK", s("META"));
        item.put("GSI1PK", s("FILE#" + link.fileId().value()));
        item.put("GSI1SK", s(sharePk(link.token())));
        item.put("token", s(link.token().value()));
        item.put("fileId", s(link.fileId().value()));
        item.put("owner", s(link.owner().value()));
        item.put("createdAt", s(link.createdAt().toString()));
        item.put("expiresAt", s(link.expiresAt().toString()));
        item.put("expiresAtEpoch", AttributeValue.fromN(Long.toString(link.expiresAt().getEpochSecond())));

        dynamo.putItem(p -> p.tableName(table).item(item));
    }

    @Override
    public Optional<ShareLink> find(ShareToken token) {
        var result = dynamo.getItem(g -> g
                .tableName(table)
                .key(Map.of("PK", s(sharePk(token)), "SK", s("META"))));
        if (!result.hasItem() || result.item().isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(toLink(result.item()));
    }

    @Override
    public void delete(ShareToken token) {
        dynamo.deleteItem(d -> d
                .tableName(table)
                .key(Map.of("PK", s(sharePk(token)), "SK", s("META"))));
    }

    @Override
    public void deleteAllForFile(FileId fileId) {
        var result = dynamo.query(q -> q
                .tableName(table)
                .indexName(GSI1)
                .keyConditionExpression("GSI1PK = :pk AND begins_with(GSI1SK, :prefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", s("FILE#" + fileId.value()),
                        ":prefix", s("SHARE#"))));
        result.items().forEach(item -> dynamo.deleteItem(d -> d
                .tableName(table)
                .key(Map.of("PK", item.get("PK"), "SK", item.get("SK")))));
    }

    private static ShareLink toLink(Map<String, AttributeValue> item) {
        return ShareLink.restore(
                ShareToken.of(item.get("token").s()),
                FileId.of(item.get("fileId").s()),
                OwnerId.of(item.get("owner").s()),
                Instant.parse(item.get("createdAt").s()),
                Instant.parse(item.get("expiresAt").s()));
    }
}
