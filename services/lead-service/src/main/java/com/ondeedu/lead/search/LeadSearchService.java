package com.ondeedu.lead.search;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.lead.dto.LeadDto;
import com.ondeedu.lead.entity.Lead;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
public class LeadSearchService {

    private final ElasticsearchOperations elasticsearchOperations;

    public void indexLead(Lead lead, String tenantId) {
        if (!StringUtils.hasText(tenantId)) {
            log.debug("Skipping Elasticsearch index for lead {} because tenantId is missing", lead.getId());
            return;
        }

        elasticsearchOperations.save(LeadSearchDocument.from(lead, tenantId));
    }

    public void deleteLead(java.util.UUID leadId) {
        elasticsearchOperations.delete(leadId.toString(), LeadSearchDocument.class);
    }

    public PageResponse<LeadDto> searchLeads(String tenantId, String query, Pageable pageable) {
        Criteria criteria = new Criteria("tenantId").is(tenantId);

        if (StringUtils.hasText(query)) {
            Criteria searchCriteria = new Criteria("fullName").matches(query)
                    .or(new Criteria("firstName").matches(query))
                    .or(new Criteria("lastName").matches(query))
                    .or(new Criteria("email").matches(query))
                    .or(new Criteria("phone").matches(query))
                    .or(new Criteria("source").matches(query))
                    .or(new Criteria("courseInterest").matches(query))
                    .or(new Criteria("notes").matches(query));
            criteria = criteria.and(searchCriteria);
        }

        CriteriaQuery criteriaQuery = new CriteriaQuery(criteria, pageable);
        SearchHits<LeadSearchDocument> hits =
                elasticsearchOperations.search(criteriaQuery, LeadSearchDocument.class);

        PageImpl<LeadDto> page = new PageImpl<>(
                hits.stream().map(hit -> hit.getContent().toDto()).toList(),
                pageable,
                hits.getTotalHits()
        );
        return PageResponse.from(page);
    }
}
