package com.ondeedu.inventory.service;

import com.ondeedu.common.cache.TenantCacheKeys;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.inventory.dto.*;
import com.ondeedu.inventory.entity.InventoryCategory;
import com.ondeedu.inventory.entity.InventoryItem;
import com.ondeedu.inventory.entity.InventoryTransaction;
import com.ondeedu.inventory.entity.InventoryTransaction.TransactionType;
import com.ondeedu.inventory.entity.InventoryUnit;
import com.ondeedu.inventory.mapper.InventoryCategoryMapper;
import com.ondeedu.inventory.mapper.InventoryItemMapper;
import com.ondeedu.inventory.mapper.InventoryTransactionMapper;
import com.ondeedu.inventory.mapper.InventoryUnitMapper;
import com.ondeedu.inventory.repository.InventoryCategoryRepository;
import com.ondeedu.inventory.repository.InventoryItemRepository;
import com.ondeedu.inventory.repository.InventoryTransactionRepository;
import com.ondeedu.inventory.repository.InventoryUnitRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final InventoryItemRepository itemRepository;
    private final InventoryTransactionRepository transactionRepository;
    private final InventoryCategoryRepository categoryRepository;
    private final InventoryUnitRepository unitRepository;

    private final InventoryItemMapper itemMapper;
    private final InventoryTransactionMapper transactionMapper;
    private final InventoryCategoryMapper categoryMapper;
    private final InventoryUnitMapper unitMapper;

    // ==================== Inventory Items ====================

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public InventoryItemDto createItem(CreateInventoryItemRequest request) {
        UUID branchId = resolveCurrentBranchId();

        // Validate SKU uniqueness
        if (request.getSku() != null && itemRepository.existsBySkuAndBranchId(request.getSku(), branchId)) {
            throw new BusinessException("INVENTORY_DUPLICATE_SKU", "Item with SKU '" + request.getSku() + "' already exists");
        }

        // Validate category exists
        InventoryCategory category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryCategory", "id", request.getCategoryId()));
        }

        // Validate unit exists
        InventoryUnit unit = unitRepository.findById(request.getUnitId())
            .orElseThrow(() -> new ResourceNotFoundException("InventoryUnit", "id", request.getUnitId()));

        InventoryItem item = itemMapper.toEntity(request);
        item.setBranchId(branchId);
        item.setCategory(category);
        item.setUnit(unit);
        item.updateStatus();

        item = itemRepository.save(item);

        // Create initial transaction for quantity
        if (request.getQuantity() != null && request.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            createTransaction(item.getId(), branchId, TransactionType.RECEIVED, request.getQuantity(),
                BigDecimal.ZERO, request.getQuantity(), null, null, "Initial stock", null);
        }

        log.info("Created inventory item: {} (SKU: {}) in branch: {}", item.getName(), item.getSku(), branchId);
        return itemMapper.toDto(item);
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "inventory", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id) + '::branch=' + T(com.ondeedu.common.tenant.TenantContext).getBranchId()")
    public InventoryItemDto getItem(UUID id) {
        UUID branchId = resolveCurrentBranchId();
        InventoryItem item = findItemByIdInScope(id, branchId);
        return itemMapper.toDto(item);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryItemDto> listItems(String status, String search, int page, int size) {
        UUID branchId = resolveCurrentBranchId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<InventoryItem> itemsPage;
        if (StringUtils.hasText(search)) {
            itemsPage = itemRepository.searchByBranch(search, branchId, pageable);
        } else if (StringUtils.hasText(status)) {
            itemsPage = itemRepository.findByStatusAndBranch(status, branchId, pageable);
        } else {
            itemsPage = itemRepository.findAllByBranch(branchId, pageable);
        }

        return PageResponse.from(itemsPage, itemMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryItemDto> getItemsByCategory(UUID categoryId, int page, int size) {
        UUID branchId = resolveCurrentBranchId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<InventoryItem> pageResult = itemRepository.findByCategoryId(categoryId, branchId, pageable);
        return PageResponse.from(pageResult, itemMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryItemDto> getReorderRequired(int page, int size) {
        UUID branchId = resolveCurrentBranchId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<InventoryItem> pageResult = itemRepository.findReorderRequired(branchId, pageable);
        return PageResponse.from(pageResult, itemMapper::toDto);
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public InventoryItemDto updateItem(UUID id, UpdateInventoryItemRequest request) {
        UUID branchId = resolveCurrentBranchId();
        InventoryItem item = findItemByIdInScope(id, branchId);

        // Track quantity change for transaction
        BigDecimal quantityBefore = item.getQuantity();

        itemMapper.updateEntity(item, request);

        // Update status based on quantity
        item.updateStatus();

        item = itemRepository.save(item);

        // Create transaction if quantity changed
        BigDecimal quantityAfter = item.getQuantity();
        if (quantityBefore.compareTo(quantityAfter) != 0) {
            BigDecimal diff = quantityAfter.subtract(quantityBefore);
            TransactionType type = diff.compareTo(BigDecimal.ZERO) > 0 ? TransactionType.RECEIVED : TransactionType.ADJUSTMENT;
            createTransaction(item.getId(), branchId, type, diff.abs(), quantityBefore, quantityAfter,
                null, null, "Manual quantity adjustment", null);
        }

        log.info("Updated inventory item: {} in branch: {}", id, branchId);
        return itemMapper.toDto(item);
    }

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public void deleteItem(UUID id) {
        UUID branchId = resolveCurrentBranchId();
        InventoryItem item = findItemByIdInScope(id, branchId);

        if (item.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            throw new BusinessException("INVENTORY_DELETE_NOT_EMPTY", "Cannot delete item with remaining quantity. Write off stock first.");
        }

        itemRepository.delete(item);
        log.info("Deleted inventory item: {} in branch: {}", id, branchId);
    }

    // ==================== Inventory Transactions ====================

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public InventoryTransactionDto recordTransaction(UUID itemId, CreateInventoryTransactionRequest request) {
        UUID branchId = resolveCurrentBranchId();
        InventoryItem item = findItemByIdInScope(itemId, branchId);

        BigDecimal quantityBefore = item.getQuantity();
        BigDecimal quantityChange = request.getQuantity();
        BigDecimal quantityAfter;

        // Calculate new quantity based on transaction type
        switch (request.getTransactionType()) {
            case RECEIVED, RETURNED -> quantityAfter = quantityBefore.add(quantityChange);
            case ISSUED, WRITE_OFF -> {
                if (quantityBefore.compareTo(quantityChange) < 0) {
                    throw new BusinessException("INVENTORY_INSUFFICIENT_STOCK", "Insufficient stock for item: " + item.getName());
                }
                quantityAfter = quantityBefore.subtract(quantityChange);
            }
            case ADJUSTMENT -> quantityAfter = quantityChange; // Adjustment sets absolute value
            default -> throw new BusinessException("INVENTORY_INVALID_TRANSACTION", "Unsupported transaction type");
        }

        // Update item quantity
        item.setQuantity(quantityAfter);
        item.updateStatus();
        itemRepository.save(item);

        // Create transaction record
        InventoryTransaction transaction = createTransaction(
            itemId, branchId, request.getTransactionType(), quantityChange,
            quantityBefore, quantityAfter, request.getReferenceType(),
            request.getReferenceId(), request.getNotes(), request.getReason()
        );

        log.info("Recorded inventory transaction: {} for item: {} in branch: {}", transaction.getId(), itemId, branchId);
        return transactionMapper.toDto(transaction);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDto> getItemTransactions(UUID itemId, int page, int size) {
        UUID branchId = resolveCurrentBranchId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
        Page<InventoryTransaction> pageResult = transactionRepository.findByItemId(itemId, branchId, pageable);
        return PageResponse.from(pageResult, transactionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDto> getTransactionsByType(String transactionType, int page, int size) {
        UUID branchId = resolveCurrentBranchId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
        TransactionType type = TransactionType.valueOf(transactionType.toUpperCase());
        Page<InventoryTransaction> pageResult = transactionRepository.findByTransactionType(type, branchId, pageable);
        return PageResponse.from(pageResult, transactionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDto> getTransactionsByDateRange(LocalDateTime fromDate, LocalDateTime toDate, int page, int size) {
        UUID branchId = resolveCurrentBranchId();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "transactionDate"));
        Page<InventoryTransaction> pageResult = transactionRepository.findByDateRange(fromDate, toDate, branchId, pageable);
        return PageResponse.from(pageResult, transactionMapper::toDto);
    }

    // ==================== Categories ====================

    @Transactional(readOnly = true)
    public List<InventoryCategoryDto> getCategories() {
        UUID branchId = resolveCurrentBranchId();
        List<InventoryCategory> categories = categoryRepository.findAllByBranchOrdered(branchId);
        return categories.stream().map(categoryMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "inventory-categories", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id) + '::branch=' + T(com.ondeedu.common.tenant.TenantContext).getBranchId()")
    public InventoryCategoryDto getCategory(UUID id) {
        UUID branchId = resolveCurrentBranchId();
        InventoryCategory category = findCategoryByIdInScope(id, branchId);
        return categoryMapper.toDto(category);
    }

    @Transactional
    @CacheEvict(value = {"inventory", "inventory-categories"}, allEntries = true)
    public InventoryCategoryDto createCategory(SaveInventoryCategoryRequest request) {
        UUID branchId = resolveCurrentBranchId();

        if (categoryRepository.existsByNameAndBranchId(request.getName(), branchId)) {
            throw new BusinessException("INVENTORY_DUPLICATE_CATEGORY", "Category '" + request.getName() + "' already exists");
        }

        InventoryCategory category = InventoryCategory.builder()
            .branchId(branchId)
            .name(request.getName())
            .description(request.getDescription())
            .icon(request.getIcon())
            .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
            .isActive(true)
            .isSystem(false)
            .build();

        category = categoryRepository.save(category);
        log.info("Created inventory category: {} in branch: {}", category.getName(), branchId);
        return categoryMapper.toDto(category);
    }

    @Transactional
    @CacheEvict(value = {"inventory", "inventory-categories"}, allEntries = true)
    public InventoryCategoryDto updateCategory(UUID id, SaveInventoryCategoryRequest request) {
        UUID branchId = resolveCurrentBranchId();
        InventoryCategory category = findCategoryByIdInScope(id, branchId);

        if (category.getIsSystem()) {
            throw new BusinessException("INVENTORY_SYSTEM_CATEGORY", "Cannot modify system category");
        }

        category.setName(request.getName());
        category.setDescription(request.getDescription());
        category.setIcon(request.getIcon());
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }

        category = categoryRepository.save(category);
        log.info("Updated inventory category: {} in branch: {}", id, branchId);
        return categoryMapper.toDto(category);
    }

    @Transactional
    @CacheEvict(value = {"inventory", "inventory-categories"}, allEntries = true)
    public void deleteCategory(UUID id) {
        UUID branchId = resolveCurrentBranchId();
        InventoryCategory category = findCategoryByIdInScope(id, branchId);

        if (category.getIsSystem()) {
            throw new BusinessException("INVENTORY_SYSTEM_CATEGORY", "Cannot delete system category");
        }

        long itemCount = itemRepository.findByCategoryId(id, branchId, PageRequest.of(0, 1)).getTotalElements();
        if (itemCount > 0) {
            throw new BusinessException("INVENTORY_CATEGORY_IN_USE", "Cannot delete category with assigned items");
        }

        categoryRepository.delete(category);
        log.info("Deleted inventory category: {} in branch: {}", id, branchId);
    }

    // ==================== Units ====================

    @Transactional(readOnly = true)
    public List<InventoryUnitDto> getUnits() {
        UUID branchId = resolveCurrentBranchId();
        List<InventoryUnit> units = unitRepository.findAllByBranchOrdered(branchId);
        return units.stream().map(unitMapper::toDto).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "inventory-units", key = "T(com.ondeedu.common.cache.TenantCacheKeys).id(#id) + '::branch=' + T(com.ondeedu.common.tenant.TenantContext).getBranchId()")
    public InventoryUnitDto getUnit(UUID id) {
        UUID branchId = resolveCurrentBranchId();
        InventoryUnit unit = findUnitByIdInScope(id, branchId);
        return unitMapper.toDto(unit);
    }

    @Transactional
    @CacheEvict(value = {"inventory", "inventory-units"}, allEntries = true)
    public InventoryUnitDto createUnit(SaveInventoryUnitRequest request) {
        UUID branchId = resolveCurrentBranchId();

        if (unitRepository.existsByNameAndBranchId(request.getName(), branchId)) {
            throw new BusinessException("INVENTORY_DUPLICATE_UNIT", "Unit '" + request.getName() + "' already exists");
        }

        if (unitRepository.existsByAbbreviationAndBranchId(request.getAbbreviation(), branchId)) {
            throw new BusinessException("INVENTORY_DUPLICATE_UNIT_ABBR", "Unit abbreviation '" + request.getAbbreviation() + "' already exists");
        }

        InventoryUnit unit = InventoryUnit.builder()
            .branchId(branchId)
            .name(request.getName())
            .abbreviation(request.getAbbreviation())
            .unitType(request.getUnitType())
            .description(request.getDescription())
            .isActive(true)
            .isSystem(false)
            .build();

        unit = unitRepository.save(unit);
        log.info("Created inventory unit: {} in branch: {}", unit.getName(), branchId);
        return unitMapper.toDto(unit);
    }

    @Transactional
    @CacheEvict(value = {"inventory", "inventory-units"}, allEntries = true)
    public InventoryUnitDto updateUnit(UUID id, SaveInventoryUnitRequest request) {
        UUID branchId = resolveCurrentBranchId();
        InventoryUnit unit = findUnitByIdInScope(id, branchId);

        if (unit.getIsSystem()) {
            throw new BusinessException("INVENTORY_SYSTEM_UNIT", "Cannot modify system unit");
        }

        unit.setName(request.getName());
        unit.setAbbreviation(request.getAbbreviation());
        unit.setUnitType(request.getUnitType());
        unit.setDescription(request.getDescription());

        unit = unitRepository.save(unit);
        log.info("Updated inventory unit: {} in branch: {}", id, branchId);
        return unitMapper.toDto(unit);
    }

    @Transactional
    @CacheEvict(value = {"inventory", "inventory-units"}, allEntries = true)
    public void deleteUnit(UUID id) {
        UUID branchId = resolveCurrentBranchId();
        InventoryUnit unit = findUnitByIdInScope(id, branchId);

        if (unit.getIsSystem()) {
            throw new BusinessException("INVENTORY_SYSTEM_UNIT", "Cannot delete system unit");
        }

        unitRepository.delete(unit);
        log.info("Deleted inventory unit: {} in branch: {}", id, branchId);
    }

    // ==================== Dashboard Stats ====================

    @Transactional(readOnly = true)
    public InventoryStatsDto getStats() {
        UUID branchId = resolveCurrentBranchId();
        long lowStock = itemRepository.countLowStockByBranch(branchId);
        long outOfStock = itemRepository.countOutOfStockByBranch(branchId);
        long totalTransactions = transactionRepository.countAllByBranch(branchId);
        long totalCategories = categoryRepository.countActiveByBranch(branchId);

        return InventoryStatsDto.builder()
            .totalItems(itemRepository.findAllByBranch(branchId, PageRequest.of(0, 1)).getTotalElements())
            .lowStockCount(lowStock)
            .outOfStockCount(outOfStock)
            .totalTransactions(totalTransactions)
            .totalCategories(totalCategories)
            .build();
    }

    // ==================== Helpers ====================

    private InventoryItem findItemByIdInScope(UUID id, UUID branchId) {
        return itemRepository.findById(id)
            .filter(item -> branchId == null || branchId.equals(item.getBranchId()))
            .orElseThrow(() -> new ResourceNotFoundException("InventoryItem", "id", id));
    }

    private InventoryCategory findCategoryByIdInScope(UUID id, UUID branchId) {
        return categoryRepository.findById(id)
            .filter(cat -> branchId == null || branchId.equals(cat.getBranchId()))
            .orElseThrow(() -> new ResourceNotFoundException("InventoryCategory", "id", id));
    }

    private InventoryUnit findUnitByIdInScope(UUID id, UUID branchId) {
        return unitRepository.findById(id)
            .filter(unit -> branchId == null || branchId.equals(unit.getBranchId()))
            .orElseThrow(() -> new ResourceNotFoundException("InventoryUnit", "id", id));
    }

    private InventoryTransaction createTransaction(UUID itemId, UUID branchId, TransactionType type,
                                                   BigDecimal quantity, BigDecimal quantityBefore,
                                                   BigDecimal quantityAfter, String referenceType,
                                                   UUID referenceId, String notes, String reason) {
        InventoryTransaction transaction = InventoryTransaction.builder()
            .branchId(branchId)
            .itemId(itemId)
            .transactionType(type)
            .quantity(quantity)
            .quantityBefore(quantityBefore)
            .quantityAfter(quantityAfter)
            .referenceType(referenceType)
            .referenceId(referenceId)
            .transactionDate(LocalDateTime.now())
            .notes(notes)
            .reason(reason)
            .build();

        return transactionRepository.save(transaction);
    }

    private UUID resolveCurrentBranchId() {
        String rawBranchId = TenantContext.getBranchId();
        if (!StringUtils.hasText(rawBranchId)) return null;
        try {
            return UUID.fromString(rawBranchId.trim());
        } catch (IllegalArgumentException e) {
            throw new BusinessException("BRANCH_ID_INVALID", "Invalid branch_id in tenant context");
        }
    }
}
