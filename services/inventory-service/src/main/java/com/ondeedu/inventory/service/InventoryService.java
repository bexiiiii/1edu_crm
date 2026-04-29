package com.ondeedu.inventory.service;

import com.ondeedu.common.cache.TenantCacheKeys;
import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.common.tenant.TenantContext;
import com.ondeedu.inventory.dto.*;
import com.ondeedu.inventory.entity.InventoryCategory;
import com.ondeedu.inventory.entity.InventoryItem;
import com.ondeedu.inventory.entity.InventoryRevision;
import com.ondeedu.inventory.entity.InventoryRevisionItem;
import com.ondeedu.inventory.entity.InventoryTransaction;
import com.ondeedu.inventory.entity.InventoryTransaction.TransactionType;
import com.ondeedu.inventory.entity.InventoryUnit;
import com.ondeedu.inventory.mapper.InventoryCategoryMapper;
import com.ondeedu.inventory.mapper.InventoryItemMapper;
import com.ondeedu.inventory.mapper.InventoryTransactionMapper;
import com.ondeedu.inventory.mapper.InventoryUnitMapper;
import com.ondeedu.inventory.repository.InventoryCategoryRepository;
import com.ondeedu.inventory.repository.InventoryItemRepository;
import com.ondeedu.inventory.repository.InventoryRevisionRepository;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

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
    private final InventoryRevisionRepository revisionRepository;

    // ==================== Inventory Items ====================

    private static final UUID NIL_UUID = new UUID(0, 0);

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
        if (request.getCategoryId() != null && !NIL_UUID.equals(request.getCategoryId())) {
            category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("InventoryCategory", "id", request.getCategoryId()));
        }

        // Validate unit exists (reject nil UUID — frontend sends 00000000-...-0000 when nothing selected)
        if (request.getUnitId() == null || NIL_UUID.equals(request.getUnitId())) {
            throw new BusinessException("INVENTORY_UNIT_REQUIRED", "Unit of measurement is required");
        }
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
            case RECEIVED -> quantityAfter = quantityBefore.add(quantityChange);
            case RETURNED, ISSUED, WRITE_OFF -> {
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

    @Transactional(readOnly = true)
    public PageResponse<InventoryTransactionDto> getTransactionHistory(
            LocalDate fromDate, LocalDate toDate, String transactionType, String search, int page, int size) {
        String branchId = TenantContext.getBranchId();
        if (StringUtils.hasText(branchId)) {
            try { UUID.fromString(branchId.trim()); } catch (IllegalArgumentException e) { branchId = null; }
        } else {
            branchId = null;
        }

        LocalDateTime fromDt = fromDate != null ? fromDate.atStartOfDay() : null;
        LocalDateTime toDt = toDate != null ? toDate.atTime(23, 59, 59) : null;
        String typeParam = StringUtils.hasText(transactionType) ? transactionType.toUpperCase() : null;
        String searchParam = StringUtils.hasText(search) ? search.trim() : null;

        PageRequest pageable = PageRequest.of(page, size);
        Page<InventoryTransaction> pageResult = transactionRepository.findByFilters(
                fromDt, toDt, typeParam, searchParam, branchId, pageable);

        Set<UUID> itemIds = pageResult.getContent().stream()
                .map(InventoryTransaction::getItemId)
                .collect(Collectors.toSet());
        Map<UUID, String> itemNames = itemRepository.findAllById(itemIds).stream()
                .collect(Collectors.toMap(InventoryItem::getId, InventoryItem::getName));

        List<InventoryTransactionDto> dtos = pageResult.getContent().stream()
                .map(t -> {
                    InventoryTransactionDto dto = transactionMapper.toDto(t);
                    dto.setItemName(itemNames.get(t.getItemId()));
                    return dto;
                }).toList();

        return PageResponse.<InventoryTransactionDto>builder()
                .content(dtos)
                .page(pageResult.getNumber())
                .size(pageResult.getSize())
                .totalElements(pageResult.getTotalElements())
                .totalPages(pageResult.getTotalPages())
                .first(pageResult.isFirst())
                .last(pageResult.isLast())
                .hasNext(pageResult.hasNext())
                .hasPrevious(pageResult.hasPrevious())
                .build();
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
        BigDecimal totalValue = itemRepository.sumTotalValueByBranch(branchId);

        return InventoryStatsDto.builder()
            .totalItems(itemRepository.findAllByBranch(branchId, PageRequest.of(0, 1)).getTotalElements())
            .lowStockCount(lowStock)
            .outOfStockCount(outOfStock)
            .totalTransactions(totalTransactions)
            .totalCategories(totalCategories)
            .totalInventoryValue(totalValue != null ? totalValue : BigDecimal.ZERO)
            .build();
    }

    // ==================== Export & Report ====================

    @Transactional(readOnly = true)
    public List<InventoryItemDto> listAllItemsForExport() {
        UUID branchId = resolveCurrentBranchId();
        return itemRepository.findAllByBranchForExport(branchId).stream()
            .map(itemMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<InventoryTransactionDto> listAllTransactionsForExport(LocalDate from, LocalDate to) {
        UUID branchId = resolveCurrentBranchId();
        LocalDateTime fromDt = (from != null ? from : LocalDate.now().withDayOfYear(1)).atStartOfDay();
        LocalDateTime toDt = (to != null ? to : LocalDate.now()).atTime(23, 59, 59);
        return transactionRepository.findAllByDateRangeForExport(fromDt, toDt, branchId).stream()
            .map(transactionMapper::toDto)
            .toList();
    }

    @Transactional(readOnly = true)
    public InventoryReportDto getInventoryReport() {
        UUID branchId = resolveCurrentBranchId();
        List<InventoryItemDto> items = itemRepository.findAllByBranchForExport(branchId).stream()
            .map(itemMapper::toDto)
            .toList();

        long inStock = items.stream().filter(i -> "IN_STOCK".equals(i.getStatus())).count();
        long lowStock = items.stream().filter(i -> "LOW_STOCK".equals(i.getStatus())).count();
        long outOfStock = items.stream().filter(i -> "OUT_OF_STOCK".equals(i.getStatus())).count();
        BigDecimal totalValue = items.stream()
            .map(i -> i.getTotalValue() != null ? i.getTotalValue() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        return InventoryReportDto.builder()
            .reportDate(LocalDate.now())
            .totalItems(items.size())
            .inStockCount(inStock)
            .lowStockCount(lowStock)
            .outOfStockCount(outOfStock)
            .totalInventoryValue(totalValue)
            .items(items)
            .build();
    }

    // ==================== Revision ====================

    @Transactional
    @CacheEvict(value = "inventory", allEntries = true)
    public InventoryRevisionResultDto conductRevision(InventoryRevisionRequest request) {
        UUID branchId = resolveCurrentBranchId();

        // Строим заголовок ревизии
        InventoryRevision revision = InventoryRevision.builder()
                .branchId(branchId)
                .revisionDate(request.getRevisionDate())
                .periodFrom(request.getPeriodFrom())
                .periodTo(request.getPeriodTo())
                .notes(request.getNotes())
                .status("COMPLETED")
                .build();

        List<InventoryRevisionResultDto.RevisionLineDto> lines = new ArrayList<>();
        int surplus = 0, shortage = 0, ok = 0;

        for (InventoryRevisionRequest.RevisionItem ri : request.getItems()) {
            InventoryItem item = findItemByIdInScope(ri.getItemId(), branchId);
            BigDecimal system = item.getQuantity();
            BigDecimal actual = ri.getActualQuantity();
            BigDecimal diff = actual.subtract(system);

            String discrepancy;
            UUID txId = null;

            if (diff.compareTo(BigDecimal.ZERO) > 0) {
                discrepancy = "SURPLUS";
                surplus++;
            } else if (diff.compareTo(BigDecimal.ZERO) < 0) {
                discrepancy = "SHORTAGE";
                shortage++;
            } else {
                discrepancy = "OK";
                ok++;
            }

            // Корректировка остатков при расхождении
            if (!discrepancy.equals("OK")) {
                String lineNote = ri.getNotes() != null ? ri.getNotes()
                        : (request.getNotes() != null ? "Ревизия: " + request.getNotes() : "Ревизия");
                InventoryTransaction tx = createTransaction(
                        item.getId(), branchId, TransactionType.ADJUSTMENT,
                        actual, system, actual, "REVISION", null, lineNote, null);
                item.setQuantity(actual);
                item.updateStatus();
                itemRepository.save(item);
                txId = tx.getId();
            }

            // Строка ревизии для сохранения в БД
            InventoryRevisionItem revItem = InventoryRevisionItem.builder()
                    .revision(revision)
                    .itemId(item.getId())
                    .itemName(item.getName())
                    .systemQuantity(system)
                    .actualQuantity(actual)
                    .difference(diff)
                    .discrepancyType(discrepancy)
                    .transactionId(txId)
                    .notes(ri.getNotes())
                    .build();
            revision.getItems().add(revItem);

            lines.add(InventoryRevisionResultDto.RevisionLineDto.builder()
                    .itemId(item.getId())
                    .itemName(item.getName())
                    .systemQuantity(system)
                    .actualQuantity(actual)
                    .difference(diff)
                    .discrepancyType(discrepancy)
                    .transactionId(txId)
                    .notes(ri.getNotes())
                    .build());
        }

        revision.setTotalItems(lines.size());
        revision.setSurplusItems(surplus);
        revision.setShortageItems(shortage);
        revision.setOkItems(ok);
        revision = revisionRepository.save(revision);

        log.info("Revision {} completed: {} items, {} surplus, {} shortage, {} ok, branch: {}",
                revision.getId(), lines.size(), surplus, shortage, ok, branchId);

        return InventoryRevisionResultDto.builder()
                .revisionId(revision.getId())
                .revisionDate(request.getRevisionDate())
                .periodFrom(request.getPeriodFrom())
                .periodTo(request.getPeriodTo())
                .notes(request.getNotes())
                .totalItems(lines.size())
                .surplusItems(surplus)
                .shortageItems(shortage)
                .okItems(ok)
                .lines(lines)
                .build();
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryRevisionDto> getRevisions(int page, int size) {
        UUID branchId = resolveCurrentBranchId();
        Page<InventoryRevision> pageResult = revisionRepository.findAllByBranch(
                branchId, PageRequest.of(page, size));
        return PageResponse.from(pageResult, r -> InventoryRevisionDto.builder()
                .id(r.getId())
                .branchId(r.getBranchId())
                .revisionDate(r.getRevisionDate())
                .periodFrom(r.getPeriodFrom())
                .periodTo(r.getPeriodTo())
                .status(r.getStatus())
                .notes(r.getNotes())
                .performedBy(r.getPerformedBy())
                .totalItems(r.getTotalItems())
                .surplusItems(r.getSurplusItems())
                .shortageItems(r.getShortageItems())
                .okItems(r.getOkItems())
                .createdAt(r.getCreatedAt())
                .build());
    }

    @Transactional(readOnly = true)
    public InventoryRevisionResultDto getRevisionDetails(UUID revisionId) {
        UUID branchId = resolveCurrentBranchId();
        InventoryRevision revision = revisionRepository.findById(revisionId)
                .filter(r -> branchId == null || branchId.equals(r.getBranchId()))
                .orElseThrow(() -> new ResourceNotFoundException("InventoryRevision", "id", revisionId));

        List<InventoryRevisionResultDto.RevisionLineDto> lines = revision.getItems().stream()
                .map(ri -> InventoryRevisionResultDto.RevisionLineDto.builder()
                        .itemId(ri.getItemId())
                        .itemName(ri.getItemName())
                        .systemQuantity(ri.getSystemQuantity())
                        .actualQuantity(ri.getActualQuantity())
                        .difference(ri.getDifference())
                        .discrepancyType(ri.getDiscrepancyType())
                        .transactionId(ri.getTransactionId())
                        .notes(ri.getNotes())
                        .build())
                .toList();

        return InventoryRevisionResultDto.builder()
                .revisionId(revision.getId())
                .revisionDate(revision.getRevisionDate())
                .periodFrom(revision.getPeriodFrom())
                .periodTo(revision.getPeriodTo())
                .notes(revision.getNotes())
                .totalItems(revision.getTotalItems())
                .surplusItems(revision.getSurplusItems())
                .shortageItems(revision.getShortageItems())
                .okItems(revision.getOkItems())
                .lines(lines)
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
