package com.nexa.bank.nexabank.controller;

import com.nexa.bank.nexabank.model.Account;
import com.nexa.bank.nexabank.repository.AccountRepository;
import com.nexa.bank.nexabank.service.AccountService;
import com.nexa.bank.nexabank.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.ui.Model;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/change-pin")
public class ChangePinController {

    // Store OTP USING ACCOUNT NUMBER (NOT EMAIL)
    private final Map<String, String> otpStore = new HashMap<>();

    @Autowired
    private AccountService accountService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private EmailService emailService;



    /* 1️⃣ SHOW VERIFY CURRENT PIN PAGE */
    @GetMapping
    public String verifyCurrent(@RequestParam String accountNumber, Model model) {
        model.addAttribute("accountNumber", accountNumber);
        return "change-pin";
    }



    /* 2️⃣ CHECK CURRENT PIN */
    @PostMapping("/verify-current")
    public String verifyCurrentPin(@RequestParam String accountNumber,
                                   @RequestParam String currentPin,
                                   RedirectAttributes ra,
                                   Model model) {

        Account acc = accountRepository.findByAccountNumber(accountNumber).orElse(null);

        if (acc == null) {
            model.addAttribute("error", "Account not found.");
            model.addAttribute("accountNumber", accountNumber);
            return "change-pin";
        }

        // Hash entered PIN
        String hashedEnteredPin = accountService.hashPin(currentPin);

        if (!acc.getPin().equals(hashedEnteredPin)) {
            model.addAttribute("error", "Incorrect current PIN.");
            model.addAttribute("accountNumber", accountNumber);
            return "change-pin";
        }

        // Correct PIN → Move to new pin page
        ra.addAttribute("accountNumber", accountNumber);
        ra.addAttribute("currentPin", currentPin);
        return "redirect:/change-pin/new";
    }



    /* 3️⃣ SHOW NEW PIN PAGE */
    @GetMapping("/new")
    public String newPin(@RequestParam String accountNumber,
                         @RequestParam String currentPin,
                         Model model) {

        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("currentPin", currentPin);
        return "change-pin-new";
    }



    /* 4️⃣ SEND OTP */
    @PostMapping("/send-otp")
    public String sendOtp(@RequestParam String accountNumber,
                          @RequestParam String newPin,
                          Model model) {

        Account acc = accountRepository.findByAccountNumber(accountNumber).orElse(null);

        if (acc == null) {
            model.addAttribute("error", "Account not found.");
            return "change-pin-new";
        }

        String email = acc.getEmail();
        String otp = String.valueOf((int)(Math.random() * 900000) + 100000);

        otpStore.put(email, otp);

        // Send email
        emailService.sendEmail(email, "Nexa Bank PIN Change OTP",
                "Your OTP is: " + otp);

        model.addAttribute("email", email);
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("newPin", newPin);
        model.addAttribute("resendMsg", "OTP has been resent to your email.");

        return "change-pin-verify-otp";
    }



    /* 5️⃣ VERIFY OTP */
    @PostMapping("/verify-otp")
    public String verifyOtp(@RequestParam String email,
                            @RequestParam String otp,
                            @RequestParam String newPin,
                            @RequestParam String accountNumber,
                            Model model) {

        // ⭐ FIX: If email is empty/null, try retrieving it using account number
        if (email == null || email.trim().isEmpty()) {
            Account tempAcc = accountRepository.findByAccountNumber(accountNumber).orElse(null);
            if (tempAcc != null) {
                email = tempAcc.getEmail();  // restore email
            } else {
                model.addAttribute("error", "Unable to verify email! Try again.");
                return "change-pin-verify-otp";
            }
        }

        String correctOtp = otpStore.get(email);

        // ⭐ FIX: avoid null pointer and properly validate OTP
        if (correctOtp == null || !otp.equals(correctOtp)) {
            model.addAttribute("email", email);
            model.addAttribute("newPin", newPin);
            model.addAttribute("accountNumber", accountNumber);
            model.addAttribute("error", "Incorrect OTP.");
            return "change-pin-verify-otp";
        }

        Account acc = accountRepository.findByAccountNumber(accountNumber).orElse(null);

        if (acc == null) {
            model.addAttribute("error", "Account not found!");
            return "error/generic-error";
        }

        // ⭐ Save new PIN
        acc.setPin(newPin);
        accountRepository.save(acc);

        // Remove OTP
        otpStore.remove(email);

        // ⭐ FIXED: SEND PIN CHANGE EMAIL
        emailService.sendEmail(
                email,
                "Your Nexa Bank PIN has been changed",
                "Hello " + acc.getHolderName() + ",\n\n"
                        + "Your PIN for account " + acc.getAccountNumber() + " has been successfully changed.\n"
                        + "If this was not you, contact Nexa Bank support immediately.\n\n"
                        + "Regards,\nNexa Bank Security Team"
        );

        // ⭐ Pass data to the success page
        model.addAttribute("holderName", acc.getHolderName());
        model.addAttribute("accountNumber", acc.getAccountNumber());

        return "change-pin-success";
    }

}
