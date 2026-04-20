package com.ondeedu.settings.service;

import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.settings.dto.BranchDto;
import com.ondeedu.settings.dto.SaveBranchRequest;
import com.ondeedu.settings.entity.TenantBranch;
import com.ondeedu.settings.mapper.TenantBranchMapper;
import com.ondeedu.settings.repository.TenantBranchRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBranchService {

    private final TenantBranchRepository repository;
    private final TenantBranchMapper mapper;

    @Transactional(readOnly = true)
    @Cacheable(value = "settings", key = "T(com.ondeedu.common.cache.TenantCacheKeys).fixed('tenant-branches')")
    public List<BranchDto> getAll() {
        return repository.findAllByOrderByIsDefaultDescNameAsc()
                .stream()
                .map(mapper::toDto)
                .toList();
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public BranchDto create(SaveBranchRequest request) {
        if (repository.existsByNameIgnoreCase(request.getName())) {
            throw new BusinessException("DUPLICATE_BRANCH", "Branch with this name already exists");
        }

        TenantBranch entity = mapper.toEntity(request);
        if (entity.getActive() == null) {
            entity.setActive(true);
        }

        boolean noExistingDefault = repository.findFirstByIsDefaultTrue().isEmpty();
        if (Boolean.TRUE.equals(entity.getIsDefault()) || noExistingDefault) {
            clearDefaultBranch();
            entity.setIsDefault(true);
        }

        entity = repository.save(entity);
        log.info("Created tenant branch: {}", entity.getName());
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public BranchDto update(UUID id, SaveBranchRequest request) {
        TenantBranch entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantBranch", "id", id));

        if (!entity.getName().equalsIgnoreCase(request.getName())
                && repository.existsByNameIgnoreCase(request.getName())) {
            throw new BusinessException("DUPLICATE_BRANCH", "Branch with this name already exists");
        }

        boolean requestSetDefault = Boolean.TRUE.equals(request.getIsDefault());

        mapper.updateEntity(entity, request);
        if (requestSetDefault) {
            clearDefaultBranch();
            entity.setIsDefault(true);
        }

        if (Boolean.FALSE.equals(entity.getActive()) && repository.countByActiveTrue() <= 1) {
            throw new BusinessException("LAST_ACTIVE_BRANCH", "At least one active branch must remain");
        }

        entity = repository.save(entity);

        if (Boolean.TRUE.equals(entity.getIsDefault())) {
            ensureSingleDefault(entity.getId());
        } else if (repository.findFirstByIsDefaultTrue().isEmpty()) {
            entity.setIsDefault(true);
            entity = repository.save(entity);
        }

        log.info("Updated tenant branch: {}", id);
        return mapper.toDto(entity);
    }

    @Transactional
    @CacheEvict(value = "settings", allEntries = true)
    public void delete(UUID id) {
        TenantBranch entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("TenantBranch", "id", id));

        if (repository.count() <= 1) {
            throw new BusinessException("LAST_BRANCH", "At least one branch must exist");
        }

        boolean wasDefault = Boolean.TRUE.equals(entity.getIsDefault());
        boolean wasActive = Boolean.TRUE.equals(entity.getActive());

        repository.delete(entity);

        if (wasActive && repository.countByActiveTrue() == 0) {
            TenantBranch fallback = repository.findAllByOrderByIsDefaultDescNameAsc().stream().findFirst()
                    .orElseThrow(() -> new BusinessException("BRANCH_NOT_FOUND", "No branches available"));
            fallback.setActive(true);
            repository.save(fallback);
        }

        if (wasDefault) {
            repository.findAllByOrderByIsDefaultDescNameAsc().stream().findFirst().ifPresent(branch -> {
                branch.setIsDefault(true);
                repository.save(branch);
            });
        }

        log.info("Deleted tenant branch: {}", id);
    }

    private void clearDefaultBranch() {
        repository.findFirstByIsDefaultTrue().ifPresent(existingDefault -> {
            existingDefault.setIsDefault(false);
            repository.save(existingDefault);
        });
    }

    private void ensureSingleDefault(UUID currentDefaultId) {
        repository.findAllByOrderByIsDefaultDescNameAsc().stream()
                .filter(branch -> !branch.getId().equals(currentDefaultId) && Boolean.TRUE.equals(branch.getIsDefault()))
                .forEach(branch -> {
                    branch.setIsDefault(false);
                    repository.save(branch);
                });
    }
}
