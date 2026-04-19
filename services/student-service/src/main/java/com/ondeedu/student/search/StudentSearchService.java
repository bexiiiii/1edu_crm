package com.ondeedu.student.search;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.student.dto.StudentDto;
import com.ondeedu.student.entity.Student;
import com.ondeedu.student.support.StudentMetadata;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnBean(ElasticsearchOperations.class)
public class StudentSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    @Value("${minio.public-url:}")
    private String minioPublicUrl;

    @Value("${minio.url:http://minio:9000}")
    private String minioUrl;

    @Value("${minio.bucket:ondeedu-files}")
    private String minioBucket;

    public void indexStudent(Student student, String tenantId, StudentMetadata metadata) {
        if (!StringUtils.hasText(tenantId)) {
            log.debug("Skipping Elasticsearch index for student {} because tenantId is missing", student.getId());
            return;
        }

        elasticsearchOperations.save(StudentSearchDocument.from(student, tenantId, metadata));
    }

    public void deleteStudent(java.util.UUID studentId) {
        elasticsearchOperations.delete(studentId.toString(), StudentSearchDocument.class);
    }

    public PageResponse<StudentDto> searchStudents(String tenantId, String query, Pageable pageable) {
        Criteria criteria = new Criteria("tenantId").is(tenantId);

        if (StringUtils.hasText(query)) {
            Criteria searchCriteria = new Criteria("fullName").matches(query)
                    .or(new Criteria("firstName").matches(query))
                    .or(new Criteria("lastName").matches(query))
                    .or(new Criteria("email").matches(query))
                    .or(new Criteria("phone").matches(query))
                    .or(new Criteria("studentPhone").matches(query))
                    .or(new Criteria("parentName").matches(query))
                    .or(new Criteria("parentPhone").matches(query))
                    .or(new Criteria("customer").matches(query))
                    .or(new Criteria("school").matches(query))
                    .or(new Criteria("grade").matches(query))
                    .or(new Criteria("notes").matches(query))
                    .or(new Criteria("comment").matches(query));
            criteria = criteria.and(searchCriteria);
        }

        CriteriaQuery criteriaQuery = new CriteriaQuery(criteria, pageable);
        SearchHits<StudentSearchDocument> hits =
                elasticsearchOperations.search(criteriaQuery, StudentSearchDocument.class);

        PageImpl<StudentDto> page = new PageImpl<>(
                hits.stream().map(hit -> {
                    StudentDto dto = hit.getContent().toDto();
                    dto.setStudentPhoto(normalizeMediaUrl(dto.getStudentPhoto()));
                    return dto;
                }).toList(),
                pageable,
                hits.getTotalHits()
        );
        return PageResponse.from(page);
    }

    private String normalizeMediaUrl(String rawUrl) {
        String value = trimToNull(rawUrl);
        if (!StringUtils.hasText(value)) {
            return null;
        }

        String publicBase = normalizeBaseUrl(minioPublicUrl);
        String internalBase = normalizeBaseUrl(minioUrl);
        String effectiveBase = StringUtils.hasText(publicBase) ? publicBase : internalBase;
        if (!StringUtils.hasText(effectiveBase)) {
            return value;
        }

        if (value.startsWith("http://") || value.startsWith("https://")) {
            if (StringUtils.hasText(publicBase)
                    && StringUtils.hasText(internalBase)
                    && value.startsWith(internalBase + "/")) {
                return publicBase + value.substring(internalBase.length());
            }
            return value;
        }

        String normalizedPath = value.startsWith("/") ? value.substring(1) : value;
        String bucketPrefix = minioBucket + "/";
        if (normalizedPath.startsWith(bucketPrefix)) {
            return effectiveBase + "/" + normalizedPath;
        }
        return effectiveBase + "/" + minioBucket + "/" + normalizedPath;
    }

    private String normalizeBaseUrl(String url) {
        String value = trimToNull(url);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
