package com.nexa.bank.nexabank.service;

import com.nexa.bank.nexabank.model.Loan;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    /**
     * Send email with optional PDF attachment
     * @param to - recipient email
     * @param subject - mail subject
     * @param html - email body (HTML allowed)
     * @param pdfBytes - PDF file bytes (nullable)
     * @param fileName - dynamic file name for the attachment
     */
    public void sendWithAttachment(String to,
                                   String subject,
                                   String html,
                                   byte[] pdfBytes,
                                   String fileName) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true);

            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(html, true);

            // Attach only if PDF exists
            if (pdfBytes != null && fileName != null) {
                helper.addAttachment(fileName, new ByteArrayResource(pdfBytes));
            }

            mailSender.send(message);

        } catch (Exception e) {
            throw new RuntimeException("Email sending failed: " + e.getMessage());
        }
    }
    // ------------------------------------------------------
// 1) EMAIL TO SENDER WITH PDF RECEIPT
// ------------------------------------------------------
    public void sendTransferReceiptToSender(String toEmail,
                                            String senderName,
                                            byte[] pdfBytes,
                                            String transactionId,
                                            String toAccount,
                                            String amount) {

        String subject = "NexaBank - Transfer Receipt (" + transactionId + ")";

        String html = "<h2>Transfer Successful</h2>" +
                "<p>Hello <b>" + senderName + "</b>,</p>" +
                "<p>Your transfer has been completed successfully.</p>" +
                "<p><b>Transaction ID:</b> " + transactionId + "</p>" +
                "<p><b>Amount:</b> ₹" + amount + "</p>" +
                "<p><b>Recipient Account:</b> " + toAccount + "</p>" +
                "<p>The PDF receipt is attached.</p>" +
                "<br><p>Regards,<br><b>NexaBank</b></p>";

        sendWithAttachment(
                toEmail,
                subject,
                html,
                pdfBytes,                         // attach PDF
                "NexaBank_Transfer_" + transactionId + ".pdf"
        );
    }

    // ------------------------------------------------------
// 2) EMAIL TO RECEIVER (NO PDF)
// ------------------------------------------------------
    public void sendTransferNotificationToReceiver(String toEmail,
                                                   String receiverName,
                                                   String senderName,
                                                   String amount) {

        String subject = "You Received Money - NexaBank";

        String html = "<h2>Amount Received</h2>" +
                "<p>Hello <b>" + receiverName + "</b>,</p>" +
                "<p>You have received <b>₹" + amount + "</b> from <b>" + senderName + "</b>.</p>" +
                "<p>Please check your updated account balance.</p>" +
                "<br><p>Regards,<br><b>NexaBank</b></p>";

        // NO PDF → pdfBytes = null
        sendWithAttachment(
                toEmail,
                subject,
                html,
                null,      // no attachment
                null
        );
    }
    // ------------------------------------------------------
// 3) EMAIL NOTIFICATION FOR FREEZE / UNFREEZE ACTION
// ------------------------------------------------------
    public void sendAccountFreezeEmail(String email, String name, String accountNumber, boolean isFreeze) {

        String subject = isFreeze
                ? "Your NexaBank Account Has Been Frozen"
                : "Your NexaBank Account Has Been Unfrozen";

        String statusText = isFreeze ? "frozen" : "unfrozen";

        String html = "<h2>Account Status Update</h2>" +
                "<p>Hello <b>" + name + "</b>,</p>" +
                "<p>Your NexaBank account <b>" + accountNumber + "</b> has been <b>" + statusText + "</b> by the administrator.</p>" +
                "<p>If you have any concerns, please contact support.</p>" +
                "<br><p>Regards,<br><b>NexaBank</b></p>";

        sendWithAttachment(
                email,
                subject,
                html,
                null,
                null
        );
    }

    // ------------------------------------------------------
// 4) EMAIL NOTIFICATION FOR ADMIN REPLY TO COMPLAINT
// ------------------------------------------------------
    public void sendComplaintReplyEmail(String toEmail,
                                        Long complaintId,
                                        String type,
                                        String replyMessage) {

        String subject = "NexaBank - Complaint Response (ID: " + complaintId + ")";

        String html = "<h2>Complaint Update</h2>" +
                "<p>Hello,</p>" +
                "<p>Your complaint has been updated by our support team.</p>" +

                "<p><b>Complaint ID:</b> " + complaintId + "</p>" +
                "<p><b>Type:</b> " + type + "</p>" +
                "<p><b>Reply:</b> " + replyMessage + "</p>" +

                "<br><p>Thank you for your patience.</p>" +
                "<p>Regards,<br><b>NexaBank Support Team</b></p>";

        sendWithAttachment(
                toEmail,
                subject,
                html,
                null,   // no PDF attached for complaint reply
                null
        );
    }
    public void sendLoanApprovedEmail(String to, Loan loan) {
        String subject = "Your Nexa Bank loan has been APPROVED";
        String text = "Dear " + loan.getAccount().getHolderName() + ",\n\n" +
                "Your " + loan.getLoanType() + " loan for amount ₹" + loan.getAmount() +
                " has been APPROVED.\n" +
                "EMI: ₹" + loan.getEmiAmount() + " for " + loan.getTenure() + " months.\n" +
                (loan.getAdminRemark() != null ? "Note from bank: " + loan.getAdminRemark() + "\n\n" : "\n") +
                "Amount has been credited to your account " + loan.getAccountNumber() + ".\n\n" +
                "Regards,\nNexa Bank";

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        mailSender.send(msg);
    }

    public void sendLoanRejectedEmail(String to, Loan loan) {
        String subject = "Your Nexa Bank loan has been REJECTED";
        String text = "Dear " + loan.getAccount().getHolderName() + ",\n\n" +
                "We are sorry to inform you that your " + loan.getLoanType() +
                " loan request for ₹" + loan.getAmount() + " has been REJECTED.\n" +
                (loan.getAdminRemark() != null ? "Reason: " + loan.getAdminRemark() + "\n\n" : "\n") +
                "For more details, please contact the branch.\n\n" +
                "Regards,\nNexa Bank";

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(to);
        msg.setSubject(subject);
        msg.setText(text);
        mailSender.send(msg);
    }
    public void sendEmail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

}
