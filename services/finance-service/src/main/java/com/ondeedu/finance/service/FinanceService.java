package com.ondeedu.finance.service;

import com.ondeedu.common.dto.PageResponse;
import com.ondeedu.common.exception.BusinessException;
import com.ondeedu.common.exception.ResourceNotFoundException;
import com.ondeedu.finance.dto.CreateTransactionRequest;
import com.ondeedu.finance.dto.TransactionDto;
import com.ondeedu.finance.dto.UpdateTransactionRequest;
import com.ondeedu.finance.entity.AmountChangeReasonCode;
import com.ondeedu.finance.entity.Transaction;
import com.ondeedu.finance.entity.TransactionType;
import com.ondeedu.finance.mapper.TransactionMapper;
import com.ondeedu.finance.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FinanceService {

    private final TransactionRepository transactionRepository;
    private final TransactionMapper transactionMapper;

    @Transactional
    public TransactionDto createTransaction(CreateTransactionRequest request) {
        validateReasonConsistency(request.getAmountChangeReasonCode(), request.getAmountChangeReasonOther());
        Transaction transaction = transactionMapper.toEntity(request);
        transaction = transactionRepository.save(transaction);
        log.info("Created transaction: {} {} {}", transaction.getType(), transaction.getAmount(), transaction.getCurrency());
        return transactionMapper.toDto(transaction);
    }

    @Transactional(readOnly = true)
    public TransactionDto getTransaction(UUID id) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));
        return transactionMapper.toDto(transaction);
    }

    @Transactional
    public TransactionDto updateTransaction(UUID id, UpdateTransactionRequest request) {
        Transaction transaction = transactionRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Transaction", "id", id));

        boolean amountChanged = request.getAmount() != null && isAmountChanged(transaction.getAmount(), request.getAmount());
        if (amountChanged && request.getAmountChangeReasonCode() == null) {
            throw new BusinessException(
                "TRANSACTION_AMOUNT_REASON_REQUIRED",
                "Amount change reason code is required when transaction amount is changed"
            );
        }
        validateReasonConsistency(request.getAmountChangeReasonCode(), request.getAmountChangeReasonOther());

        transactionMapper.updateEntity(transaction, request);
        transaction = transactionRepository.save(transaction);
        log.info("Updated transaction: {}", id);
        return transactionMapper.toDto(transaction);
    }

    @Transactional
    public void deleteTransaction(UUID id) {
        if (!transactionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Transaction", "id", id);
        }
        transactionRepository.deleteById(id);
        log.info("Deleted transaction: {}", id);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> listTransactions(TransactionType type, Pageable pageable) {
        Page<Transaction> page;
        if (type != null) {
            page = transactionRepository.findByType(type, pageable);
        } else {
            page = transactionRepository.findAll(pageable);
        }
        return PageResponse.from(page, transactionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> listByDateRange(LocalDate from, LocalDate to, Pageable pageable) {
        Page<Transaction> page = transactionRepository.findByTransactionDateBetween(from, to, pageable);
        return PageResponse.from(page, transactionMapper::toDto);
    }

    @Transactional(readOnly = true)
    public PageResponse<TransactionDto> listByStudent(UUID studentId, Pageable pageable) {
        Page<Transaction> page = transactionRepository.findByStudentId(studentId, pageable);
        return PageResponse.from(page, transactionMapper::toDto);
    }

    private boolean isAmountChanged(BigDecimal currentAmount, BigDecimal requestedAmount) {
        if (currentAmount == null) {
            return requestedAmount != null;
        }
        return currentAmount.compareTo(requestedAmount) != 0;
    }

    private void validateReasonConsistency(AmountChangeReasonCode code, String otherReason) {
        if (code == AmountChangeReasonCode.OTHER && !StringUtils.hasText(otherReason)) {
            throw new BusinessException(
                "TRANSACTION_AMOUNT_REASON_OTHER_REQUIRED",
                "amountChangeReasonOther is required when amountChangeReasonCode is OTHER"
            );
        }

        if (code != null && code != AmountChangeReasonCode.OTHER && StringUtils.hasText(otherReason)) {
            throw new BusinessException(
                "TRANSACTION_AMOUNT_REASON_OTHER_FORBIDDEN",
                "amountChangeReasonOther is allowed only when amountChangeReasonCode is OTHER"
            );
        }
    }
}
