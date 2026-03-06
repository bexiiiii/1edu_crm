package com.ondeedu.settings.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.settings.dto.RoleConfigDto;
import com.ondeedu.settings.dto.SaveRoleConfigRequest;
import com.ondeedu.settings.entity.Permission;
import com.ondeedu.settings.entity.RoleConfig;
import com.ondeedu.settings.repository.RoleConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleConfigService {

    private final RoleConfigRepository repository;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public List<RoleConfigDto> getAll() {
        return repository.findAllByOrderByNameAsc()
                .stream().map(this::toDto).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleConfigDto getById(UUID id) {
        return toDto(repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoleConfig", "id", id)));
    }

    /** Returns all available permission codes (for the frontend picker) */
    public List<String> getAllPermissions() {
        return Arrays.stream(Permission.values())
                .map(Enum::name)
                .collect(Collectors.toList());
    }

    @Transactional
    public RoleConfigDto create(SaveRoleConfigRequest request) {
        if (repository.existsByName(request.getName())) {
            throw new BusinessException("DUPLICATE_ROLE", "Role with name '" + request.getName() + "' already exists");
        }
        RoleConfig entity = RoleConfig.builder()
                .name(request.getName())
                .description(request.getDescription())
                .permissions(toJson(request.getPermissions()))
                .build();
        entity = repository.save(entity);
        log.info("Created role config: {}", entity.getName());
        return toDto(entity);
    }

    @Transactional
    public RoleConfigDto update(UUID id, SaveRoleConfigRequest request) {
        RoleConfig entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("RoleConfig", "id", id));

        if (!entity.getName().equals(request.getName()) && repository.existsByName(request.getName())) {
            throw new BusinessException("DUPLICATE_ROLE", "Role with name '" + request.getName() + "' already exists");
        }

        entity.setName(request.getName());
        if (request.getDescription() != null) {
            entity.setDescription(request.getDescription());
        }
        if (request.getPermissions() != null) {
            entity.setPermissions(toJson(request.getPermissions()));
        }
        entity = repository.save(entity);
        log.info("Updated role config: {}", id);
        return toDto(entity);
    }

    @Transactional
    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResourceNotFoundException("RoleConfig", "id", id);
        }
        repository.deleteById(id);
        log.info("Deleted role config: {}", id);
    }

    // --- helpers ---

    private RoleConfigDto toDto(RoleConfig entity) {
        return RoleConfigDto.builder()
                .id(entity.getId())
                .name(entity.getName())
                .description(entity.getDescription())
                .permissions(fromJson(entity.getPermissions()))
                .build();
    }

    private String toJson(List<String> list) {
        if (list == null) return "[]";
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
