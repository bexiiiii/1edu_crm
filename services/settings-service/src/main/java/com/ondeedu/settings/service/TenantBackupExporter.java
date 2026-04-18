package com.ondeedu.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.tenant.TenantContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PGobject;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.sql.Array;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBackupExporter {

    private static final DateTimeFormatter FILE_TS_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public TenantBackupArtifact exportCurrentTenantSnapshot() {
        String tenantId = TenantContext.getTenantId();
        String schemaName = TenantContext.getSchemaName();
        Instant now = Instant.now();

        try {
            List<String> tables = jdbcTemplate.queryForList("""
                    SELECT table_name
                    FROM information_schema.tables
                    WHERE table_schema = ?
                      AND table_type = 'BASE TABLE'
                    ORDER BY table_name
                    """, String.class, schemaName);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tenantId", tenantId);
            payload.put("schemaName", schemaName);
            payload.put("generatedAt", now);
            payload.put("tableCount", tables.size());

            Map<String, Object> tablesPayload = new LinkedHashMap<>();
            for (String tableName : tables) {
                tablesPayload.put(tableName, exportTable(schemaName, tableName));
            }
            payload.put("tables", tablesPayload);

            byte[] jsonBytes = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(payload);
            byte[] gzipped = gzip(jsonBytes);
            String tenantSuffix = tenantId != null ? tenantId : UUID.randomUUID().toString();
            String fileName = "tenant-" + tenantSuffix + "-backup-" + FILE_TS_FORMAT.format(now) + ".json.gz";

            log.info("Prepared tenant backup snapshot [tenant={}, schema={}, tables={}, size={} bytes]",
                    tenantId, schemaName, tables.size(), gzipped.length);

            return new TenantBackupArtifact(fileName, gzipped, "application/gzip", now);
        } catch (Exception e) {
            log.error("Failed to export tenant backup [tenant={}, schema={}]", tenantId, schemaName, e);
            throw new BusinessException("TENANT_BACKUP_EXPORT_FAILED",
                    "Failed to export tenant backup: " + e.getMessage());
        }
    }

    private Map<String, Object> exportTable(String schemaName, String tableName) {
        String sql = "SELECT * FROM " + quoteIdentifier(schemaName) + "." + quoteIdentifier(tableName);
        List<Map<String, Object>> rows = jdbcTemplate.query(sql, (rs, rowNum) -> readRow(rs.getMetaData(), rs));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("rowCount", rows.size());
        result.put("rows", rows);
        return result;
    }

    private Map<String, Object> readRow(ResultSetMetaData metaData, java.sql.ResultSet rs) throws SQLException {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 1; i <= metaData.getColumnCount(); i++) {
            row.put(metaData.getColumnLabel(i), sanitizeValue(rs.getObject(i)));
        }
        return row;
    }

    private Object sanitizeValue(Object value) throws SQLException {
        if (value == null) {
            return null;
        }
        if (value instanceof Array sqlArray) {
            Object arrayValue = sqlArray.getArray();
            if (arrayValue instanceof Object[] objectArray) {
                List<Object> items = new ArrayList<>(objectArray.length);
                for (Object item : objectArray) {
                    items.add(sanitizeValue(item));
                }
                return items;
            }
            return arrayValue;
        }
        if (value instanceof PGobject pgObject) {
            return pgObject.getValue();
        }
        if (value instanceof byte[] bytes) {
            return Base64.getEncoder().encodeToString(bytes);
        }
        return value;
    }

    private byte[] gzip(byte[] bytes) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(output)) {
            gzip.write(bytes);
        }
        return output.toByteArray();
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
