package com.nexa.bank.nexabank.controller;

import com.nexa.bank.nexabank.model.Loan;
import com.nexa.bank.nexabank.model.LoanStatus;
import com.nexa.bank.nexabank.model.Account;
import com.nexa.bank.nexabank.repository.LoanRepository;
import com.nexa.bank.nexabank.repository.AccountRepository;

import com.nexa.bank.nexabank.service.LoanService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AdminLoanRequestController {

    private final LoanService loanService;
    private final LoanRepository loanRepository;
    private final AccountRepository accountRepository;

    public AdminLoanRequestController(LoanRepository loanRepository,
                                      AccountRepository accountRepository,
                                      LoanService loanService) {
        this.loanRepository = loanRepository;
        this.accountRepository = accountRepository;
        this.loanService = loanService;
    }
    @GetMapping("/admin/loan-requests")
    public String showLoanRequests(Model model) {
        model.addAttribute("loanList", loanRepository.findAllByOrderByCreatedAtDesc());
        return "admin-loan-requests";
    }

    @PostMapping("/admin/loans/approve")
    public String approveLoan(@RequestParam Long loanId,
                              @RequestParam String reason,
                              RedirectAttributes ra) {

        try {
            loanService.approveLoan(loanId, reason);
            ra.addFlashAttribute("success", "Loan Approved & Disbursed!");
        } catch (Exception e) {
            e.printStackTrace();  // ← add this line
            ra.addFlashAttribute("error", "Approve failed: " + e.getMessage());
        }


        return "redirect:/admin/loan-requests";
    }


    // ❌ REJECT LOAN
    @PostMapping("/admin/loans/reject")
    public String rejectLoan(
            @RequestParam Long loanId,
            @RequestParam String reason,
            RedirectAttributes ra) {

        Loan loan = loanRepository.findById(loanId).orElse(null);
        if (loan == null) {
            ra.addFlashAttribute("error", "Loan not found!");
            return "redirect:/admin/loan-requests";
        }

        // ⭐ NEW: Call LoanService reject logic (email sending)
        try {
            loanService.rejectLoan(loanId, reason);
            ra.addFlashAttribute("success", "Loan Rejected.");
        } catch (Exception e) {
            ra.addFlashAttribute("error", "Error while rejecting loan: " + e.getMessage());
        }

        return "redirect:/admin/loan-requests";
    }

}
