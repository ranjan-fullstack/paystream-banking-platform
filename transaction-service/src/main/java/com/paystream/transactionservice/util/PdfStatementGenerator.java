package com.paystream.transactionservice.util;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.paystream.transactionservice.entity.Transaction;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class PdfStatementGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm");

    public byte[] generate(String accountNumber, List<Transaction> transactions) {
        try {
            Document document = new Document(PageSize.A4);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, out);
            document.open();

            Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            document.add(new Paragraph("PayStream Banking Platform - Account Statement", titleFont));
            document.add(new Paragraph("Account Number: " + accountNumber));
            document.add(new Paragraph(" "));

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);
            addHeaderCell(table, "Date");
            addHeaderCell(table, "Transaction ID");
            addHeaderCell(table, "Mode");
            addHeaderCell(table, "Debit A/C");
            addHeaderCell(table, "Credit A/C");
            addHeaderCell(table, "Amount (INR)");

            for (Transaction txn : transactions) {
                table.addCell(txn.getInitiatedAt() != null ? txn.getInitiatedAt().format(DATE_FMT) : "-");
                table.addCell(txn.getTransactionId());
                table.addCell(txn.getPaymentMode().name());
                table.addCell(txn.getDebitAccountNumber());
                table.addCell(txn.getCreditAccountNumber());
                table.addCell(txn.getAmount().toPlainString());
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (DocumentException e) {
            throw new IllegalStateException("Failed to generate account statement PDF", e);
        }
    }

    private void addHeaderCell(PdfPTable table, String text) {
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10);
        table.addCell(new Phrase(text, headerFont));
    }
}
