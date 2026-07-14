package com.ezcloudstore.adapters.out.dynamodb;

import com.ezcloudstore.domain.model.FileId;
import com.ezcloudstore.domain.model.FileStatus;
import com.ezcloudstore.domain.model.FileVersion;
import com.ezcloudstore.domain.model.OwnerId;
import com.ezcloudstore.domain.model.StoredFile;
import com.ezcloudstore.domain.port.FileRepository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ezcloudstore.adapters.out.dynamodb.DynamoDbTableSchema.GSI1;

public class DynamoDbFileRepository implements FileRepository {

    private static final String FILE_ENTITY_PARTITION = "ENTITY#FILE";

    private final DynamoDbClient dynamo;
    private final String table;

    public DynamoDbFileRepository(DynamoDbClient dynamo, String table) {
        this.dynamo = dynamo;
        this.table = table;
    }

    // --- key builders -------------------------------------------------

    private static String userPk(OwnerId owner) {
        return "USER#" + owner.value();
    }

    private static String fileSk(FileId id) {
        return "FILE#" + id.value();
    }

    private static String filePk(FileId id) {
        return "FILE#" + id.value();
    }

    private static AttributeValue s(String value) {
        return AttributeValue.fromS(value);
    }

    // --- files ---------------------------------------------------------

    @Override
    public void save(StoredFile file) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(userPk(file.owner())));
        item.put("SK", s(fileSk(file.id())));
        item.put("GSI1PK", s(FILE_ENTITY_PARTITION));
        item.put("GSI1SK", s(fileSk(file.id())));
        item.put("id", s(file.id().value()));
        item.put("owner", s(file.owner().value()));
        item.put("name", s(file.name()));
        item.put("description", s(file.description()));
        item.put("sizeBytes", AttributeValue.fromN(Long.toString(file.sizeBytes())));
        item.put("contentType", s(file.contentType()));
        item.put("status", s(file.status().name()));
        item.put("createdAt", s(file.createdAt().toString()));
        item.put("updatedAt", s(file.updatedAt().toString()));
        file.currentS3VersionId().ifPresent(v -> item.put("currentS3VersionId", s(v)));

        dynamo.putItem(p -> p.tableName(table).item(item));
    }

    @Override
    public Optional<StoredFile> find(FileId id) {
        var result = dynamo.query(q -> q
                .tableName(table)
                .indexName(GSI1)
                .keyConditionExpression("GSI1PK = :pk AND GSI1SK = :sk")
                .expressionAttributeValues(Map.of(
                        ":pk", s(FILE_ENTITY_PARTITION),
                        ":sk", s(fileSk(id)))));
        return result.items().stream().findFirst().map(DynamoDbFileRepository::toFile);
    }

    @Override
    public List<StoredFile> listByOwner(OwnerId owner) {
        var result = dynamo.query(q -> q
                .tableName(table)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", s(userPk(owner)),
                        ":prefix", s("FILE#"))));
        return result.items().stream().map(DynamoDbFileRepository::toFile).toList();
    }

    @Override
    public List<StoredFile> listAll() {
        var result = dynamo.query(q -> q
                .tableName(table)
                .indexName(GSI1)
                .keyConditionExpression("GSI1PK = :pk")
                .expressionAttributeValues(Map.of(":pk", s(FILE_ENTITY_PARTITION))));
        return result.items().stream().map(DynamoDbFileRepository::toFile).toList();
    }

    @Override
    public void delete(FileId id) {
        find(id).ifPresent(file -> dynamo.deleteItem(d -> d
                .tableName(table)
                .key(Map.of(
                        "PK", s(userPk(file.owner())),
                        "SK", s(fileSk(id))))));
    }

    private static StoredFile toFile(Map<String, AttributeValue> item) {
        return StoredFile.restore(
                FileId.of(item.get("id").s()),
                OwnerId.of(item.get("owner").s()),
                item.get("name").s(),
                item.get("description").s(),
                Long.parseLong(item.get("sizeBytes").n()),
                item.get("contentType").s(),
                FileStatus.valueOf(item.get("status").s()),
                Instant.parse(item.get("createdAt").s()),
                Instant.parse(item.get("updatedAt").s()),
                item.containsKey("currentS3VersionId") ? item.get("currentS3VersionId").s() : null);
    }

    // --- versions -------------------------------------------------------

    @Override
    public void saveVersion(FileVersion version) {
        // zero-padded epoch millis keep lexicographic order == chronological order
        String seq = String.format("%020d", version.createdAt().toEpochMilli());
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", s(filePk(version.fileId())));
        item.put("SK", s("VERSION#" + seq + "#" + version.s3VersionId()));
        item.put("fileId", s(version.fileId().value()));
        item.put("s3VersionId", s(version.s3VersionId()));
        item.put("sizeBytes", AttributeValue.fromN(Long.toString(version.sizeBytes())));
        item.put("createdAt", s(version.createdAt().toString()));

        dynamo.putItem(p -> p.tableName(table).item(item));
    }

    @Override
    public List<FileVersion> listVersions(FileId id) {
        var result = dynamo.query(q -> q
                .tableName(table)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", s(filePk(id)),
                        ":prefix", s("VERSION#"))));
        return result.items().stream()
                .map(item -> new FileVersion(
                        FileId.of(item.get("fileId").s()),
                        item.get("s3VersionId").s(),
                        Long.parseLong(item.get("sizeBytes").n()),
                        Instant.parse(item.get("createdAt").s())))
                .toList();
    }

    @Override
    public void deleteVersions(FileId id) {
        var result = dynamo.query(q -> q
                .tableName(table)
                .keyConditionExpression("PK = :pk AND begins_with(SK, :prefix)")
                .expressionAttributeValues(Map.of(
                        ":pk", s(filePk(id)),
                        ":prefix", s("VERSION#")))
                .projectionExpression("PK, SK"));
        result.items().forEach(item -> dynamo.deleteItem(d -> d
                .tableName(table)
                .key(Map.of("PK", item.get("PK"), "SK", item.get("SK")))));
    }
}
