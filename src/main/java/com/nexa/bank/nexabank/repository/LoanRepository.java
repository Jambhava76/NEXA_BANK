package com.nexa.bank.nexabank.repository;

import com.nexa.bank.nexabank.model.Loan;
import com.nexa.bank.nexabank.model.LoanStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LoanRepository extends JpaRepository<Loan, Long> {

    List<Loan> findByAccountNumber(String accountNumber);

    List<Loan> findByStatus(LoanStatus status);

    List<Loan> findByStatusAndClosedFalse(LoanStatus status);
    List<Loan> findByAccountNumberOrderByCreatedAtDesc(String accountNumber);
    Loan findTopByAccountNumberAndStatusOrderByIdDesc(
            String accountNumber, LoanStatus status);

    // NEW – for admin listing, newest first
    List<Loan> findAllByOrderByCreatedAtDesc();
}