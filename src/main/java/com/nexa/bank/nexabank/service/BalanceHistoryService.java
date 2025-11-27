package com.nexa.bank.nexabank.service;

import com.nexa.bank.nexabank.model.Transaction;
import com.nexa.bank.nexabank.repository.TransactionRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
@Service
public class BalanceHistoryService {

    private final TransactionRepository txRepo;

    public BalanceHistoryService(TransactionRepository txRepo) {
        this.txRepo = txRepo;
    }

    public List<Map<String, Object>> getLast10DaysHistory(String accountNumber, BigDecimal currentBalance) {

        List<Transaction> txList = txRepo.getAllTransactionsForAccount(accountNumber);

        // Sort by date ASC (oldest → newest)
        txList.sort(Comparator.comparing(Transaction::getCreatedAt));

        List<Map<String, Object>> history = new ArrayList<>();

        for (int i = 0; i < 10; i++) {

            LocalDate day = LocalDate.now().minusDays(i);

            // Find latest transaction BEFORE or ON this day
            BigDecimal closingBalance = null;

            for (Transaction tx : txList) {
                LocalDate txDate = tx.getCreatedAt().toLocalDate();
                if (!txDate.isAfter(day)) { // txDate <= day
                    closingBalance = tx.getBalanceAfter();
                }
            }

            // If still null → no previous transaction → balance = 0
            if (closingBalance == null) {
                closingBalance = BigDecimal.ZERO;
            }

            Map<String, Object> row = new HashMap<>();
            row.put("date", day);
            row.put("balance", closingBalance);

            history.add(row);
        }

        return history;
    }


}
