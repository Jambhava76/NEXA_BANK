package com.nexa.bank.nexabank.service;

import com.nexa.bank.nexabank.model.Account;
import com.nexa.bank.nexabank.model.Loan;
import com.nexa.bank.nexabank.model.LoanStatus;
import com.nexa.bank.nexabank.model.Transaction;
import com.nexa.bank.nexabank.repository.AccountRepository;
import com.nexa.bank.nexabank.repository.LoanRepository;
import com.nexa.bank.nexabank.repository.TransactionRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class LoanService {

    private final LoanRepository loanRepo;
    private final AccountRepository accountRepo;
    private final TransactionService txService;
    private final EmailService emailService;

    @Autowired
    private TransactionRepository transactionRepo;

    public LoanService(
            LoanRepository loanRepo,
            AccountRepository accountRepo,
            TransactionService txService,
            EmailService emailService
    ) {
        this.loanRepo = loanRepo;
        this.accountRepo = accountRepo;
        this.txService = txService;
        this.emailService = emailService;
    }

    // -------------------------------------------
    // LOAN LIST FOR A SPECIFIC ACCOUNT
    // -------------------------------------------
    public List<Loan> getLoansByAccount(String accountNumber) {
        return loanRepo.findByAccountNumberOrderByCreatedAtDesc(accountNumber);
    }

    // -------------------------------------------
    // INTEREST % CALCULATOR (based on credit score)
    // -------------------------------------------
    private double calculateInterestRate(Integer creditScore, String loanType) {
        if (creditScore == null) return 15.0;

        if (creditScore >= 800) return 8.0;
        else if (creditScore >= 700) return 10.0;
        else if (creditScore >= 600) return 12.5;
        else return 15.0;
    }

    private BigDecimal calculateEmi(BigDecimal principal, double annualRate, int months) {
        if (principal == null || months <= 0) {
            return BigDecimal.ZERO;
        }

        double r = annualRate / (12 * 100.0); // monthly %

        if (r == 0) {
            return principal.divide(BigDecimal.valueOf(months), 2, RoundingMode.HALF_UP);
        }

        double pow = Math.pow(1 + r, months);
        double emiDouble = (principal.doubleValue() * r * pow) / (pow - 1);

        return BigDecimal.valueOf(emiDouble).setScale(2, RoundingMode.HALF_UP);
    }

    // -------------------------------------------
    // CREATE LOAN (customer applies)
    // -------------------------------------------
    @Transactional
    public Loan createLoan(Loan loan) {

        Account acc = accountRepo.findByAccountNumber(loan.getAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        Integer creditScore = acc.getCreditScore();

        double rate = calculateInterestRate(creditScore, loan.getLoanType());
        loan.setInterestRate(rate);

        BigDecimal emi = calculateEmi(loan.getAmount(), rate, loan.getTenure());
        loan.setEmiAmount(emi);

        loan.setStatus(LoanStatus.PENDING);
        loan.setCreatedAt(LocalDate.now());
        loan.setAccount(acc);
        loan.setClosed(Boolean.FALSE);

        return loanRepo.save(loan);
    }

    // -------------------------------------------
    // BASIC QUERIES
    // -------------------------------------------
    public List<Loan> findByAccount(String accNo) {
        return loanRepo.findByAccountNumber(accNo);
    }

    public List<Loan> findByStatus(LoanStatus status) {
        return loanRepo.findByStatus(status);
    }

    public Loan findById(Long id) {
        return loanRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Loan not found"));
    }

    public List<Loan> findAllLoans() {
        return loanRepo.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    public List<Loan> findApprovedNotClosedLoans() {
        return loanRepo.findByStatusAndClosedFalse(LoanStatus.DISBURSED);
    }

    // ===================================================================
//                       APPROVE LOAN
// ===================================================================
    @Transactional
    public Loan approveLoan(Long loanId, String reason) {

        System.out.println("\n==============================");
        System.out.println("APPROVE LOAN START");
        System.out.println("Loan ID received: " + loanId);
        System.out.println("==============================");

        Loan loan = findById(loanId);

        System.out.println("Loan found. Status = " + loan.getStatus());
        System.out.println("Account Number = " + loan.getAccountNumber());

        if (loan.getStatus() != LoanStatus.PENDING) {
            System.out.println("ERROR: Loan already processed!");
            throw new IllegalStateException("Loan already processed.");
        }

        // 1️⃣ Approve & save
        loan.setStatus(LoanStatus.APPROVED);
        loan.setApprovedAt(LocalDate.now());
        loanRepo.save(loan);
        System.out.println("Loan status set to APPROVED.");

        // 2️⃣ Disburse (credit money)
        System.out.println("Calling disburseLoanInternal...");
        disburseLoanInternal(loan);
        System.out.println("Loan disbursed successfully.");

        // 3️⃣ Try to send email
        try {
            Account acc = accountRepo.findByAccountNumber(loan.getAccountNumber())
                    .orElse(null);

            if (acc != null) {
                System.out.println("Sending approval email to: " + acc.getEmail());
                emailService.sendLoanApprovedEmail(acc.getEmail(), loan);
            } else {
                System.err.println("ERROR: Account not found for loan " + loanId);
            }

        } catch (Exception ex) {
            System.err.println("Failed to send loan approval email: " + ex.getMessage());
        }

        System.out.println("=== APPROVE LOAN END ===");
        System.out.println("==============================\n");

        return loan;
    }



    // ===================================================================
    //                         REJECT LOAN
    // ===================================================================
    @Transactional
    public void rejectLoan(Long loanId, String reason) {

        Loan loan = findById(loanId);

        if (loan.getStatus() != LoanStatus.PENDING) {
            throw new IllegalStateException("Loan already processed.");
        }

        loan.setStatus(LoanStatus.REJECTED);
        loan.setAdminRemark(reason);

        loanRepo.save(loan);

        Account acc = accountRepo.findByAccountNumber(loan.getAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));

        try {
            emailService.sendLoanRejectedEmail(acc.getEmail(), loan);
        } catch (Exception ex) {
            System.err.println("Email error: " + ex.getMessage());
        }
    }

    // ===================================================================
    //                    DISBURSE LOAN (CREDIT MONEY)
    // ===================================================================
    @Transactional
    protected Loan disburseLoanInternal(Loan loan) {

        if (loan.getStatus() != LoanStatus.APPROVED) {
            throw new IllegalStateException("Loan must be APPROVED before disbursement.");
        }

        Account acc = accountRepo.findByAccountNumber(loan.getAccountNumber())
                .orElseThrow(() -> new RuntimeException("Account not found"));


        Transaction tx = new Transaction();
        tx.setTransactionId(UUID.randomUUID().toString().substring(0, 10).toUpperCase());
        tx.setType("LOAN_CREDITED");
        tx.setSenderAccountNumber("NEXA-BANK");
        tx.setReceiverAccountNumber(acc.getAccountNumber());
        tx.setAccountNumber(acc.getAccountNumber()); // FIX 1
        tx.setAmount(loan.getAmount());
        tx.setReason("Loan credited to account");
        tx.setCreatedAt(LocalDateTime.now());
        tx.setTransactionTime(LocalDateTime.now());  // 💥 FIX 2 (required)
        tx.setBalanceAfter(acc.getBalance().add(loan.getAmount()));

        // update balance
        acc.setBalance(acc.getBalance().add(loan.getAmount()));
        accountRepo.save(acc);

        // save transaction
        transactionRepo.save(tx);

        // update loan
        loan.setStatus(LoanStatus.DISBURSED);
        loan.setDisbursedAt(LocalDate.now());
        loan.setRemainingBalance(loan.getAmount());
        loan.setNextEmiDate(LocalDate.now().plusMonths(1));
        loan.setClosed(Boolean.FALSE);

        return loanRepo.save(loan);
    }

    // ===================================================================
    //                          EMI DEDUCTION
    // ===================================================================
    @Transactional
    public void collectEmi(Loan loan) {

        if (loan.getStatus() != LoanStatus.DISBURSED) return;

        BigDecimal emi = loan.getEmiAmount();
        String accNo = loan.getAccountNumber();

        try {
            txService.withdraw(accNo, emi, "Monthly EMI for Loan #" + loan.getId());

            BigDecimal newBalance = loan.getRemainingBalance().subtract(emi);

            if (newBalance.compareTo(BigDecimal.ZERO) <= 0) {
                loan.setRemainingBalance(BigDecimal.ZERO);
                loan.setStatus(LoanStatus.COMPLETED);
                loan.setNextEmiDate(null);
                loan.setClosed(Boolean.TRUE);
            } else {
                loan.setRemainingBalance(newBalance);
                loan.setNextEmiDate(loan.getNextEmiDate().plusMonths(1));
            }

            loanRepo.save(loan);

        } catch (Exception ex) {
            throw new RuntimeException("EMI deduction failed: " + ex.getMessage());
        }
    }
}
