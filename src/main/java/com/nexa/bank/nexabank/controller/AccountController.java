package com.nexa.bank.nexabank.controller;
import com.nexa.bank.nexabank.model.Account;
import com.nexa.bank.nexabank.model.Complaint;
import com.nexa.bank.nexabank.model.Transaction;
import com.nexa.bank.nexabank.repository.AccountRepository;
import com.nexa.bank.nexabank.repository.ComplaintRepository;
import com.nexa.bank.nexabank.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.ui.Model;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Logger;
import jakarta.servlet.http.HttpServletRequest;

// inside class: add a logger near top (optional but useful)
@SessionAttributes("pendingMinor")

@Controller
public class AccountController {
    @Autowired
    private ComplaintRepository complaintRepository;
    // inside AccountController class (existing)
    private static final Logger logger = Logger.getLogger(AccountController.class.getName());
    @Autowired private AccountService accountService;
    @Autowired private AccountRepository accountRepository;
    @Autowired private OtpService otpService;
    @Autowired private JavaMailSender mailSender;
    @Autowired private TransactionService transactionService;
    @Autowired
    private BalanceHistoryService balanceHistoryService;
    @Autowired
    private EmailService emailService;
    @Autowired
    private PdfService pdfService;
    private final Map<String, Account> pendingAccountMap = new HashMap<>();
    private final Map<String, Account> loginPending = new HashMap<>();
    private final Map<String, Account> pendingMinorMap = new HashMap<>();
    private final Map<String, String> pendingMinorGuardianMap = new HashMap<>();

    // ------------------ SELECT ACCOUNT TYPE ------------------
    @GetMapping("/select-account-type")
    public String selectAccountType() {
        return "select-account-type";
    }

    // ------------------ MAJOR ACCOUNT ------------------
    @GetMapping("/create-account")
    public String createMajor(Model model) {
        model.addAttribute("account", new Account());
        return "create-account";
    }
    @Autowired
    private HttpSession session;

    @PostMapping("/major/send-otp")
    public String sendMajorOtp(@ModelAttribute("account") Account acc,
                               RedirectAttributes ra,
                               Model model) {

        if (acc.getDob() == null || !accountService.isAdult(acc.getDob())) {
            model.addAttribute("account", acc);
            model.addAttribute("error", "Age must be 18+ for Major Account.");
            return "create-account";
        }

        session.setAttribute("pendingMajorAccount", acc);
        otpService.generateAndSendOtp(acc.getEmail(), mailSender);

        return "redirect:/verify-otp?email=" + acc.getEmail();
    }


    @GetMapping("/verify-otp")
    public String verifyOtpPage(@RequestParam(name="email") String email, Model model) {
        model.addAttribute("email", email);
        return "verify-otp";
    }

    @PostMapping("/major/verify-otp")
    public String verifyMajorOtp(@RequestParam String email,
                                 @RequestParam String otp,
                                 Model model) {

        if (!otpService.verifyOtp(email, otp)) {
            model.addAttribute("email", email);
            model.addAttribute("error", "Invalid OTP. Try Again.");
            return "verify-otp";
        }

        // ✅ Get saved account from session
        Account acc = (Account) session.getAttribute("pendingMajorAccount");

        if (acc == null) {
            model.addAttribute("account", new Account());
            model.addAttribute("error", "Session expired! Please fill the form again.");
            return "create-account";
        }

        // Save to DB
        Account saved = accountService.createAccount(acc);

        // Remove from session
        session.removeAttribute("pendingMajorAccount");

        // Send welcome email
        otpService.sendWelcomeEmail(
                saved.getEmail(),
                saved.getHolderName(),
                saved.getAccountNumber(),
                saved.getBranchName(),
                saved.getIfscCode()
        );

        model.addAttribute("holder", saved.getHolderName());
        model.addAttribute("accNumber", saved.getAccountNumber());
        model.addAttribute("branch", saved.getBranchName());
        model.addAttribute("ifsc", saved.getIfscCode());

        return "account-success";
    }




    // ------------------ MINOR ACCOUNT ------------------
    @GetMapping("/create-minor-account")
    public String createMinor(Model model) {
        model.addAttribute("account", new Account());
        return "create-minor-account";
    }
    @PostMapping("/minor/step1")
    public String captureMinor(@ModelAttribute("account") Account minor,
                               RedirectAttributes ra) {

        String normalizedMinorEmail = minor.getEmail().trim().toLowerCase();
        minor.setEmail(normalizedMinorEmail);
        minor.setMinor(true);

        // ✅ Save in session (important)
        session.setAttribute("pendingMinor", minor);

        ra.addAttribute("minorEmail", normalizedMinorEmail);
        return "redirect:/enter-guardian-account";
    }


    @GetMapping("/enter-guardian-account")
    public String enterGuardian(@RequestParam String minorEmail, Model model) {
        model.addAttribute("minorEmail", minorEmail);
        return "enter-guardian-account";
    }

    @PostMapping("/minor/guardian-lookup")
    public String lookupGuardian(@RequestParam String minorEmail,
                                 @RequestParam String guardianAccountNumber,
                                 RedirectAttributes ra,
                                 Model model) {

        Optional<Account> guardianOpt = accountRepository.findByAccountNumber(guardianAccountNumber);

        if (guardianOpt.isEmpty()) {
            model.addAttribute("minorEmail", minorEmail);
            model.addAttribute("error", "Guardian account not found.");
            return "enter-guardian-account";
        }

        Account guardian = guardianOpt.get();


        if (guardian.isMinor()) {
            model.addAttribute("minorEmail", minorEmail);
            model.addAttribute("error", "Guardian cannot be a minor.");
            return "enter-guardian-account";
        }

        if (guardian.getDob() == null || !accountService.isAdult(guardian.getDob())) {
            model.addAttribute("minorEmail", minorEmail);
            model.addAttribute("error", "Guardian must be 18+.");
            return "enter-guardian-account";
        }
        pendingMinorGuardianMap.put(minorEmail.trim().toLowerCase(), guardianAccountNumber);

        otpService.generateAndSendOtp(guardian.getEmail(), mailSender);

        ra.addAttribute("email", guardian.getEmail());
        ra.addAttribute("minorEmail", minorEmail);
        return "redirect:/verify-guardian-otp";
    }

    @GetMapping("/verify-guardian-otp")
    public String guardianOtp(@RequestParam String email,
                              @RequestParam String minorEmail,
                              Model model) {
        model.addAttribute("email", email);
        model.addAttribute("minorEmail", minorEmail);
        return "verify-guardian-otp";
    }
    @PostMapping("/minor/verify-guardian-otp")
    public String verifyGuardian(@RequestParam String minorEmail,
                                 @RequestParam String email,
                                 @RequestParam String otp,
                                 RedirectAttributes ra,
                                 Model model) {

        minorEmail = minorEmail.trim().toLowerCase();
        email = email.trim().toLowerCase();

        if (!otpService.verifyOtp(email, otp)) {
            model.addAttribute("email", email);
            model.addAttribute("minorEmail", minorEmail);
            model.addAttribute("error", "Invalid OTP.");
            return "verify-guardian-otp";
        }

        // ✅ FIX HERE
        return "redirect:/guardian-link-success?minorEmail=" + minorEmail;
    }
    @GetMapping("/guardian-link-success")
    public String guardianLinked(@RequestParam String minorEmail, Model model, HttpSession session) {

        // ✅ Get minor from session (not model)
        Account minor = (Account) session.getAttribute("pendingMinor");

        if (minor == null) {
            model.addAttribute("error", "Session expired. Start again.");
            return "select-account-type";
        }

        model.addAttribute("minorEmail", minorEmail);
        return "guardian-link-success";
    }
    @PostMapping("/minor/send-minor-otp")
    public String sendMinorOtp(@RequestParam String minorEmail,
                               RedirectAttributes ra,
                               HttpSession session) {

        minorEmail = minorEmail.trim().toLowerCase();

        // ✅ Get minor from session (NOT from map)
        Account minor = (Account) session.getAttribute("pendingMinor");

        if (minor == null || !minor.getEmail().equals(minorEmail)) {
            ra.addFlashAttribute("error", "Session expired. Start again.");
            return "redirect:/select-account-type";
        }

        otpService.generateAndSendOtp(minorEmail, mailSender);

        ra.addAttribute("minorEmail", minorEmail);
        return "redirect:/verify-minor-otp";
    }

    @GetMapping("/verify-minor-otp")
    public String showMinorOtpPage(@RequestParam String minorEmail,
                                   Model model,
                                   HttpSession session) {

        minorEmail = minorEmail.trim().toLowerCase();

        // ❗ Get minor from session
        Account minor = (Account) session.getAttribute("pendingMinor");

        // ❗ If not found, session expired
        if (minor == null || !minor.getEmail().equals(minorEmail)) {
            model.addAttribute("error", "Session expired. Start again.");
            return "select-account-type";
        }

        model.addAttribute("minorEmail", minorEmail);
        model.addAttribute("email", minorEmail);

        return "verify-minor-otp";
    }

    @PostMapping("/minor/verify-minor-otp")
    public String verifyMinorOtp(@RequestParam String minorEmail,
                                 @RequestParam String otp,
                                 Model model,
                                 HttpSession session) {

        String normalizedMinor = minorEmail.trim().toLowerCase();

        // 1️⃣ Validate OTP
        if (!otpService.verifyOtp(normalizedMinor, otp)) {
            model.addAttribute("email", normalizedMinor);
            model.addAttribute("minorEmail", normalizedMinor);
            model.addAttribute("error", "Invalid or expired OTP.");
            return "verify-minor-otp";
        }

        // 2️⃣ Get Minor from session (correct way)
        Account minor = (Account) session.getAttribute("pendingMinor");

        if (minor == null || !minor.getEmail().equals(normalizedMinor)) {
            model.addAttribute("error", "Session expired. Start again.");
            return "select-account-type";
        }

        // 3️⃣ Get guardian account number (from map or session)
        String guardianAccNo = pendingMinorGuardianMap.get(normalizedMinor);
        Account guardian = accountRepository.findByAccountNumber(guardianAccNo)
                .orElseThrow(() -> new RuntimeException("Guardian not found"));

        // 4️⃣ Assign guardian details to minor
        minor.setAccountType("Savings");
        minor.setGuardianAccountNumber(guardianAccNo);
        minor.setBranchName(guardian.getBranchName());
        minor.setIfscCode(guardian.getIfscCode());

        // 5️⃣ Save account
        Account saved = accountService.createAccount(minor);

        // 6️⃣ Send welcome email
        otpService.sendWelcomeEmail(
                saved.getEmail(),
                saved.getHolderName(),
                saved.getAccountNumber(),
                saved.getBranchName(),
                saved.getIfscCode()
        );

        // 7️⃣ Clear session + map entry
        session.removeAttribute("pendingMinor");
        pendingMinorGuardianMap.remove(normalizedMinor);

        // 8️⃣ Show success page
        model.addAttribute("holder", saved.getHolderName());
        model.addAttribute("accNumber", saved.getAccountNumber());
        model.addAttribute("branch", saved.getBranchName());
        model.addAttribute("ifsc", saved.getIfscCode());

        return "account-success";
    }

    @PostMapping("/minor/resend-minor-otp")
    public String resendMinorOtp(@RequestParam String minorEmail,
                                 RedirectAttributes ra,
                                 HttpSession session) {

        String normalizedMinorEmail = minorEmail.trim().toLowerCase();

        // 🔍 Get minor from session (correct)
        Account minor = (Account) session.getAttribute("pendingMinor");

        if (minor == null || !minor.getEmail().equals(normalizedMinorEmail)) {
            ra.addFlashAttribute("error", "Session expired. Start again.");
            return "redirect:/select-account-type";
        }

        // 📩 Send OTP
        otpService.generateAndSendOtp(normalizedMinorEmail, mailSender);

        ra.addFlashAttribute("message", "OTP resent successfully!");

        String encoded = URLEncoder.encode(normalizedMinorEmail, StandardCharsets.UTF_8);
        return "redirect:/verify-minor-otp?minorEmail=" + encoded;
    }


    @GetMapping("/login")
    public String showLogin(Model model) {
        String captcha = otpService.generateCaptcha();
        model.addAttribute("captcha", captcha);
        return "login";
    }

    @PostMapping("/login-request-otp")
    public String sendLoginOtp(@RequestParam String accountNumber,
                               @RequestParam String pin,
                               @RequestParam String captchaInput,
                               @RequestParam String captcha,
                               Model model,
                               RedirectAttributes ra) {

        if (!captcha.equals(captchaInput)) {
            model.addAttribute("error", "Incorrect CAPTCHA");
            model.addAttribute("captcha", otpService.generateCaptcha());
            return "login";
        }

        Optional<Account> accOpt = accountRepository.findByAccountNumber(accountNumber);

        if (accOpt.isEmpty()) {
            model.addAttribute("error", "Invalid Account Number or PIN");
            model.addAttribute("captcha", otpService.generateCaptcha());
            return "login";
        }

        Account acc = accOpt.get();

        if (!accountService.hashPin(pin).equals(acc.getPin())) {
            model.addAttribute("error", "Invalid Account Number or PIN");
            model.addAttribute("captcha", otpService.generateCaptcha());
            return "login";
        }

        otpService.generateAndSendOtp(acc.getEmail(), mailSender);
        loginPending.put(acc.getEmail(), acc);

        ra.addAttribute("email", acc.getEmail());
        return "redirect:/verify-login-otp";
    }
    @PostMapping("/verify-login-otp")
    public String verifyLoginOtp(@RequestParam("email") String email,
                                 @RequestParam("otp") String otp,
                                 HttpServletRequest request,
                                 Model model) {

        boolean isValid = otpService.verifyOtp(email, otp);

        if (isValid) {

            Account account = loginPending.get(email);

            if (account == null) {
                model.addAttribute("error", "Session expired. Please login again.");
                return "login";
            }

            // ⭐ SAVE LAST LOGIN TIME & IP
            account.setLastLoginTime(LocalDateTime.now());
            account.setLastLoginIp(request.getRemoteAddr());
            accountRepository.save(account);

            return "redirect:/dashboard?accountNumber=" + account.getAccountNumber();
        }
        else {
            model.addAttribute("email", email);
            model.addAttribute("error", "Invalid OTP. Please try again.");
            return "verify-login-otp";
        }
    }

    @GetMapping("/verify-login-otp")
    public String showVerifyLoginOtp(@RequestParam("email") String email, Model model) {
        model.addAttribute("email", email);
        return "verify-login-otp"; // must match the html file name in templates/
    }
    // Resend OTP for Major account
    @PostMapping("/major/resend-otp")
    public String resendMajorOtp(@RequestParam String email, RedirectAttributes ra) {

        // Keep session alive
        Account acc = (Account) session.getAttribute("pendingMajorAccount");

        if (acc == null || !acc.getEmail().equals(email)) {
            ra.addFlashAttribute("error", "Session expired. Please fill the form again.");
            return "redirect:/create-account";
        }

        otpService.generateAndSendOtp(email, mailSender);

        ra.addFlashAttribute("success", "OTP has been resent to your email!");

        return "redirect:/verify-otp?email=" + email;
    }


    // Replace your existing dashboard(...) and viewAccountDetails(...) methods with these

    @GetMapping("/dashboard")
    public String showDashboard(@RequestParam(name = "accountNumber", required = false) String accountNumber, Model model) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            model.addAttribute("error", "Missing accountNumber parameter.");
            return "error/account-error";
        }

        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElse(null);

        if (account == null) {
            model.addAttribute("error", "Account not found for account number: " + accountNumber);
            return "error/account-error";
        }


        model.addAttribute("holderName", account.getHolderName());
        model.addAttribute("balance", account.getBalance());
        model.addAttribute("branch", account.getBranchName());
        model.addAttribute("ifsc", account.getIfscCode());
        model.addAttribute("accNumber", account.getAccountNumber());
        model.addAttribute("lastLoginTime", account.getLastLoginTime());
        model.addAttribute("lastLoginIp", account.getLastLoginIp());

        // Robust handling for createdAt: supports LocalDate or LocalDateTime or null
        String accountAge = "N/A";
        try {
            Object created = account.getCreatedAt();
            if (created != null) {
                LocalDate createdDate = null;
                if (created instanceof LocalDate) {
                    createdDate = (LocalDate) created;
                } else if (created instanceof LocalDateTime) {
                    createdDate = ((LocalDateTime) created).toLocalDate();
                } else {
                    // If your entity uses java.util.Date or String, you can add handling here
                }

                if (createdDate != null) {
                    LocalDate currentDate = LocalDate.now();
                    Period period = Period.between(createdDate, currentDate);
                    accountAge = String.format("%d years %d months %d days",
                            period.getYears(), period.getMonths(), period.getDays());
                }
            }
        } catch (Exception e) {
            // Log optionally — but don't throw. We keep a friendly fallback.
            accountAge = "N/A";
        }
// FORMAT LAST LOGIN TIME
        if (account.getLastLoginTime() != null) {
            DateTimeFormatter formatter =
                    DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a");

            String formattedTime = account.getLastLoginTime().format(formatter);
            model.addAttribute("formattedLastLogin", formattedTime);
        } else {
            model.addAttribute("formattedLastLogin", "First Login");
        }

// FORMAT LOGIN IP
        String ip = account.getLastLoginIp();
        if (ip == null) ip = "Unknown";

        if (ip.equals("0:0:0:0:0:0:0:1"))
            model.addAttribute("formattedIp", "Localhost (127.0.0.1)");
        else
            model.addAttribute("formattedIp", ip);

        model.addAttribute("accountAge", accountAge);
        return "dashboard";
    }

    @GetMapping("/account-details")
    public String viewAccountDetails(@RequestParam(name = "accountNumber", required = false) String accountNumber, Model model) {
        if (accountNumber == null || accountNumber.trim().isEmpty()) {
            model.addAttribute("error", "Missing account number in request.");
            return "error/account-error";
        }

        Account acc = accountRepository.findByAccountNumber(accountNumber)
                .orElse(null);

        if (acc == null) {
            model.addAttribute("error", "Account not found...");
            return "error/account-error";
        }

        model.addAttribute("account", acc);
        return "account-details";
    }
    @GetMapping("/deposit")
    public String showDeposit(@RequestParam String accountNumber, Model model) {
        model.addAttribute("accountNumber", accountNumber);
        return "deposit"; // corresponds to deposit.html
    }

    @PostMapping("/deposit")
    public String handleDeposit(@RequestParam String accountNumber,
                                @RequestParam BigDecimal amount,
                                RedirectAttributes ra) {

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {

            ra.addFlashAttribute("error", "Amount must be greater than ₹0.");
            return "redirect:/deposit?accountNumber=" + accountNumber;
        }

        try {
            // Step 1: Perform deposit & get Transaction object (with real TX-ID)
            Transaction tx = transactionService.deposit(accountNumber, amount);

            Account acc = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            LocalDateTime now = LocalDateTime.now();
            String date = now.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            String time = now.format(DateTimeFormatter.ofPattern("hh:mm:ss a"));

            // UI data for receipt
            ra.addFlashAttribute("success", "Money Deposited Successfully!");
            ra.addFlashAttribute("updatedBalance", tx.getBalanceAfter());
            ra.addFlashAttribute("transactionId", tx.getTransactionId());
            ra.addFlashAttribute("date", date);
            ra.addFlashAttribute("time", time);
            ra.addFlashAttribute("holderName", acc.getHolderName());
            ra.addFlashAttribute("accountNumber", accountNumber);
            ra.addFlashAttribute("amount", amount);

            // Generate PDF
            byte[] pdf = pdfService.generateDepositReceipt(
                    tx.getTransactionId(),
                    acc.getHolderName(),
                    accountNumber,
                    amount,
                    tx.getBalanceAfter(),
                    date,
                    time
            );

            // Email
            String emailMsg =
                    "<h2 style='color:#0D1B3D;'>Deposit Successful</h2>" +
                            "<p>Your deposit has been completed successfully.</p>" +
                            "<p><b>Amount:</b> ₹" + amount + "</p>" +
                            "<p><b>Updated Balance:</b> ₹" + tx.getBalanceAfter() + "</p>" +
                            "<p>Thank you for banking with <b>Nexa Bank</b>.</p>";

            emailService.sendWithAttachment(
                    acc.getEmail(),
                    "Nexa Bank - Deposit Successful",
                    emailMsg,
                    pdf,
                    "Deposit_Receipt.pdf"
            );

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/deposit?accountNumber=" + accountNumber;
    }


    @GetMapping("/balance-history")
    public String balanceHistory(@RequestParam String accountNumber, Model model) {

        Account acc = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<Map<String, Object>> history =
                balanceHistoryService.getLast10DaysHistory(accountNumber, acc.getBalance());

        model.addAttribute("history", history);
        model.addAttribute("holderName", acc.getHolderName());
        model.addAttribute("accNumber", accountNumber);

        return "balance-history";
    }

    @GetMapping("/withdraw")
    public String showWithdraw(@RequestParam String accountNumber, Model model) {
        model.addAttribute("accountNumber", accountNumber);
        return "withdraw";
    }
    @PostMapping("/withdraw")
    public String withdraw(
            @RequestParam String accountNumber,
            @RequestParam BigDecimal amount,
            @RequestParam(required = false) String reason,
            @RequestParam(required = false) String otherReason,
            RedirectAttributes ra) {

        if (otherReason != null && !otherReason.trim().isEmpty()) {
            reason = otherReason;
        }

        try {

            // ---------------------- DAILY LIMIT VALIDATION (ADDED) ----------------------
            Account acc = accountRepository.findByAccountNumber(accountNumber)
                    .orElseThrow(() -> new RuntimeException("Account not found"));

            // Reset daily usage if it's a new day
            if (acc.getLastLimitResetDate() == null ||
                    !acc.getLastLimitResetDate().equals(LocalDate.now())) {

                acc.setDailyUsed(BigDecimal.ZERO);
                acc.setLastLimitResetDate(LocalDate.now());
                accountRepository.save(acc);
            }

            // Check if limit exists and active
            if (acc.getDailyLimit() != null && acc.getDailyLimit().compareTo(BigDecimal.ZERO) > 0) {

                BigDecimal remainingLimit = acc.getDailyLimit().subtract(acc.getDailyUsed());

                // ❌ Exceeds remaining limit
                if (amount.compareTo(remainingLimit) > 0) {
                    ra.addFlashAttribute("error",
                            "Daily limit exceeded! Remaining limit: ₹" + remainingLimit);
                    return "redirect:/withdraw?accountNumber=" + accountNumber;
                }
            }
            // ---------------------- END DAILY LIMIT CHECK ----------------------


            // Step 1: Make withdrawal (returns transaction)
            Transaction tx = transactionService.withdraw(accountNumber, amount, reason);

            // After success: Add used amount
            acc.setDailyUsed(acc.getDailyUsed().add(amount));
            accountRepository.save(acc);

            LocalDateTime now = LocalDateTime.now();
            String date = now.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"));
            String time = now.format(DateTimeFormatter.ofPattern("hh:mm:ss a"));

            // UI Flash attributes
            ra.addFlashAttribute("success", "Withdrawal successful!");
            ra.addFlashAttribute("updatedBalance", tx.getBalanceAfter());
            ra.addFlashAttribute("transactionId", tx.getTransactionId());
            ra.addFlashAttribute("accountNumber", accountNumber);
            ra.addFlashAttribute("amount", amount);
            ra.addFlashAttribute("reason", reason);
            ra.addFlashAttribute("date", date);
            ra.addFlashAttribute("time", time);
            ra.addFlashAttribute("holderName", acc.getHolderName());

            // Generate PDF
            byte[] pdf = pdfService.generateWithdrawReceipt(
                    tx.getTransactionId(),
                    acc.getHolderName(),
                    accountNumber,
                    amount,
                    tx.getBalanceAfter(),
                    reason,
                    date,
                    time
            );

            String emailMsg =
                    "<h2>Withdrawal Successful</h2>" +
                            "<p><b>Transaction ID:</b> " + tx.getTransactionId() + "</p>" +
                            "<p><b>Amount:</b> ₹" + amount + "</p>" +
                            "<p><b>Reason:</b> " + reason + "</p>" +
                            "<p><b>Updated Balance:</b> ₹" + tx.getBalanceAfter() + "</p>";

            emailService.sendWithAttachment(
                    acc.getEmail(),
                    "Nexa Bank - Withdrawal Successful",
                    emailMsg,
                    pdf,
                    "Withdraw_Receipt.pdf"
            );

        } catch (Exception e) {
            ra.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/withdraw?accountNumber=" + accountNumber;
    }

    @GetMapping("/statements")
    public String showStatements(@RequestParam String accountNumber, Model model) {

        Account acc = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        List<Transaction> txList = transactionService.getAllTransactions(accountNumber);

        model.addAttribute("holderName", acc.getHolderName());
        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("transactions", txList);

        return "statements"; // statements.html
    }
    @GetMapping("/virtual-card")
    public String virtualCard(@RequestParam String accountNumber, Model model) {

        Account acc = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        // Masked card number
        String cardNum = acc.getAccountNumber();
        String masked = cardNum.substring(0, 4) + " " +
                cardNum.substring(4, 6) + "** **** " +
                cardNum.substring(10);

        // Generate Unique UPI ID (example)
        String upiId = acc.getHolderName()
                .toLowerCase()
                .replace(" ", "") + "." +
                acc.getAccountNumber().substring(5) +
                "@nexabank";

        model.addAttribute("holderName", acc.getHolderName());
        model.addAttribute("maskedCard", masked);
        model.addAttribute("fullCard", acc.getAccountNumber());
        model.addAttribute("expiry", "12/29");
        model.addAttribute("cvv", "123");  // or generate dynamically
        model.addAttribute("upiId", upiId);

        return "virtual-card";
    }

    @GetMapping("/card-security")
    public String cardSecurity(@RequestParam String accountNumber, Model model) {
        Account acc = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        model.addAttribute("account", acc);
        return "card-security";
    }

    @PostMapping("/toggle-card-freeze")
    public String toggleCardFreeze(
            @RequestParam String accountNumber,
            RedirectAttributes ra) {

        Account acc = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> new RuntimeException("Account not found"));

        acc.setCardFrozen(!acc.isCardFrozen());
        accountRepository.save(acc);

        if (acc.isCardFrozen()) {
            ra.addFlashAttribute("success", "Your card has been frozen for security.");
        } else {
            ra.addFlashAttribute("success", "Your card is now active.");
        }

        return "redirect:/card-security?accountNumber=" + accountNumber;
    }

    @GetMapping("/raise-complaint")
    public String openComplaintForm(@RequestParam String accountNumber, Model model) {
        model.addAttribute("accountNumber", accountNumber);
        return "complaint-form";  // File name in templates
    }
    @PostMapping("/submit-complaint")
    public String submitComplaint(
            @RequestParam String accountNumber,
            @RequestParam String type,
            @RequestParam String description,
            RedirectAttributes ra) {

        Complaint com = new Complaint();
        com.setAccountNumber(accountNumber);
        com.setType(type);
        com.setDescription(description);

        // Set email automatically
        Account acc = accountRepository.findByAccountNumber(accountNumber).orElse(null);
        if (acc != null) {
            com.setEmail(acc.getEmail());
        }

        complaintRepository.save(com);

        // Flash success message
        ra.addFlashAttribute("success", true);

        // IMPORTANT: Stay on the same page
        return "redirect:/raise-complaint?accountNumber=" + accountNumber;
    }

    @GetMapping("/complaint-history")
    public String complaintHistory(@RequestParam String accountNumber, Model model) {

        List<Complaint> complaints = complaintRepository.findAll()
                .stream()
                .filter(c -> c.getAccountNumber().equals(accountNumber))
                .toList();

        model.addAttribute("complaints", complaints);
        model.addAttribute("accountNumber", accountNumber);

        return "complaint-history";
    }
    @GetMapping("/logout")
    public String logoutCustomer(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }

    // ------------------ DAILY LIMIT PAGE ------------------
    @GetMapping("/daily-limit")
    public String showDailyLimit(@RequestParam String accountNumber, Model model) {

        Account acc = accountRepository.findByAccountNumber(accountNumber)
                .orElse(null);

        if (acc == null) {
            model.addAttribute("error", "Account not found");
            return "error/account-error";
        }

        // Reset today's usage if a new day
        accountService.resetLimitIfNewDay(acc);

        model.addAttribute("accountNumber", accountNumber);
        model.addAttribute("currentLimit", acc.getDailyLimit());
        model.addAttribute("success", model.getAttribute("success"));
        model.addAttribute("error", model.getAttribute("error"));

        return "daily-limit";   // must match your HTML file name
    }
    @PostMapping("/update-daily-limit")
    public String updateDailyLimit(@RequestParam String accountNumber,
                                   @RequestParam BigDecimal limit,
                                   RedirectAttributes ra) {

        Account acc = accountRepository.findByAccountNumber(accountNumber)
                .orElse(null);

        if (acc == null) {
            ra.addFlashAttribute("error", "Account not found");
            return "redirect:/daily-limit?accountNumber=" + accountNumber;
        }

        if (limit.compareTo(BigDecimal.valueOf(100)) < 0) {
            ra.addFlashAttribute("error", "Minimum limit is ₹100");
            return "redirect:/daily-limit?accountNumber=" + accountNumber;
        }

        acc.setDailyLimit(limit);
        accountRepository.save(acc);

        ra.addFlashAttribute("success", "Daily limit updated!");
        return "redirect:/daily-limit?accountNumber=" + accountNumber;
    }
    @PostMapping("/resend-login-otp")
    public String resendLoginOtp(@RequestParam String email, RedirectAttributes ra) {

        Account acc = loginPending.get(email);

        if (acc == null) {
            ra.addFlashAttribute("error", "Session expired. Please login again.");
            return "redirect:/login";
        }

        // Generate and send new OTP
        otpService.generateAndSendOtp(email, mailSender);

        ra.addAttribute("email", email);
        ra.addFlashAttribute("message", "A new OTP has been sent to your email.");

        return "redirect:/verify-login-otp";
    }
    @PostMapping("/minor/guardian-resend-otp")
    public String resendGuardianOtp(@RequestParam String email,
                                    @RequestParam String minorEmail,
                                    Model model) {

        otpService.sendGuardianOtp(email);

        model.addAttribute("email", email);
        model.addAttribute("minorEmail", minorEmail);
        model.addAttribute("message", "OTP resent successfully!");

        return "verify-guardian-otp";
    }
    @GetMapping("/forgot-pin")
    public String forgotPinPage() {
        return "forgot-pin";
    }
    @PostMapping("/forgot-pin-send-otp")
    public String sendForgotPinOtp(@RequestParam String accountNumber,
                                   RedirectAttributes ra,
                                   Model model) {

        Optional<Account> accOpt = accountRepository.findByAccountNumber(accountNumber);

        if (accOpt.isEmpty()) {
            model.addAttribute("error", "Account not found!");
            return "forgot-pin";
        }

        Account acc = accOpt.get();

        // Send OTP
        otpService.generateAndSendOtp(acc.getEmail(), mailSender);

        // Store temporarily (session map)
        session.setAttribute("resetPinAccount", acc);

        ra.addAttribute("email", acc.getEmail());
        return "redirect:/forgot-pin-verify-otp";
    }
    @GetMapping("/forgot-pin-verify-otp")
    public String showForgotPinOtp(@RequestParam String email, Model model) {
        model.addAttribute("email", email);
        return "forgot-pin-verify-otp";
    }
    @PostMapping("/forgot-pin-verify")
    public String verifyForgotPinOtp(@RequestParam String email,
                                     @RequestParam String otp,
                                     Model model,
                                     HttpSession session) {

        if (!otpService.verifyOtp(email, otp)) {
            model.addAttribute("error", "Invalid OTP");
            model.addAttribute("email", email);
            return "forgot-pin-verify-otp";
        }

        // Fetch account stored earlier
        Account acc = (Account) session.getAttribute("resetPinAccount");

        if (acc == null || !acc.getEmail().equals(email)) {
            model.addAttribute("error", "Session expired. Try again.");
            return "forgot-pin";
        }

        // Pass account number to reset page
        session.setAttribute("resetPinAccNo", acc.getAccountNumber());

        return "redirect:/reset-pin";
    }

    @GetMapping("/reset-pin")
    public String resetPinPage(Model model, HttpSession session) {

        String accNo = (String) session.getAttribute("resetPinAccNo");

        if (accNo == null) {
            model.addAttribute("error", "Session expired. Start again.");
            return "forgot-pin";
        }

        model.addAttribute("accountNumber", accNo);
        return "reset-pin";
    }
    @PostMapping("/reset-pin")
    public String resetPin(@RequestParam String accountNumber,
                           @RequestParam String newPin,
                           Model model,
                           HttpSession session) {

        Optional<Account> accOpt = accountRepository.findByAccountNumber(accountNumber);

        if (accOpt.isEmpty()) {
            model.addAttribute("error", "Account not found.");
            return "reset-pin";
        }

        Account acc = accOpt.get();

        // Validate 4-digit format
        if (!newPin.matches("\\d{4}")) {
            model.addAttribute("error", "PIN must be exactly 4 digits.");
            model.addAttribute("accountNumber", accountNumber);
            return "reset-pin";
        }

        // Prevent old PIN = new PIN
        String oldHashed = acc.getPin();
        String newHashed = accountService.hashPin(newPin);

        if (oldHashed.equals(newHashed)) {
            model.addAttribute("error", "New PIN cannot be the same as your previous PIN.");
            model.addAttribute("accountNumber", accountNumber);
            return "reset-pin";
        }

        // Save new PIN
        acc.setPin(newHashed);
        accountRepository.save(acc);

        // Clear session data
        session.removeAttribute("resetPinAccount");
        session.removeAttribute("resetPinAccNo");

        // Email confirmation
        emailService.sendEmail(
                acc.getEmail(),
                "Your Nexa Bank PIN has been changed",
                "Hello " + acc.getHolderName() +
                        ",\n\nYour PIN has been successfully changed.\nIf this was not you, contact support immediately.\n\n- Nexa Bank"
        );

        // SUCCESS message displayed on reset-pin page
        model.addAttribute("success", "Your PIN was updated successfully!");
        model.addAttribute("accountNumber", accountNumber);

        return "reset-pin"; // STAYS on page → success UI + login button visible
    }

    @PostMapping("/forgot-pin-resend-otp")
    public String resendForgotPinOtp(@RequestParam String email, RedirectAttributes ra) {

        // Get account from session
        Account acc = (Account) session.getAttribute("resetPinAccount");

        if (acc == null || !acc.getEmail().equals(email)) {
            ra.addFlashAttribute("error", "Session expired. Please try again.");
            return "redirect:/forgot-pin";
        }

        // Send new OTP
        otpService.generateAndSendOtp(email, mailSender);

        ra.addFlashAttribute("resendMessage", "A new OTP has been sent to your email!");

        return "redirect:/forgot-pin-verify-otp?email=" + email;
    }


}