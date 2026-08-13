package com.example.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import com.example.data.model.BillEntity
import com.example.data.model.BusinessSettingsEntity
import com.example.data.model.CustomerEntity
import com.example.data.model.PaymentEntity

object ReceiptPrintUtils {

    private var activeWebView: WebView? = null

    private fun findActivity(context: Context): Activity? {
        var current = context
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    fun generateReceiptHtml(
        payment: PaymentEntity,
        bill: BillEntity?,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ): String {
        val ispName = settings.ispName.ifBlank { if (isBn) "আইএসপি ডিজিটাল নেটওয়ার্ক" else "ISP Digital Network" }
        val hotline = settings.hotline.ifBlank { if (isBn) "০১৭০০-০০০০০০" else "01700-000000" }
        val address = settings.address.ifBlank { if (isBn) "হেড অফিস, ঢাকা, বাংলাদেশ" else "Head Office, Dhaka, Bangladesh" }
        val currency = settings.currencySymbol.ifBlank { "৳" }

        val custName = customer?.name ?: payment.customerName
        val custCode = customer?.customerCode ?: "CUST-${payment.customerId}"
        val custPhone = customer?.phone ?: "N/A"
        val pppoeUser = customer?.pppoeUsername ?: "N/A"
        val packageName = customer?.packageName ?: "Standard Package"
        val custAddress = customer?.address ?: "N/A"

        val invNo = bill?.billNumber ?: "INV-${payment.billId}"
        val receiptNo = payment.paymentReceiptNo
        val billMonth = bill?.billingMonth ?: payment.paymentDate.take(7)
        val billAmt = String.format("%.2f", bill?.amount ?: payment.amount)
        val paidAmt = String.format("%.2f", payment.amount)
        val dueAmt = String.format("%.2f", bill?.dueAmount ?: 0.0)

        return """
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="utf-8">
                <style>
                    body {
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
                        padding: 24px;
                        color: #1f2937;
                        max-width: 650px;
                        margin: 0 auto;
                        background: #ffffff;
                    }
                    .header {
                        text-align: center;
                        border-bottom: 2px solid #2563eb;
                        padding-bottom: 12px;
                        margin-bottom: 16px;
                    }
                    .company-name {
                        font-size: 26px;
                        font-weight: 800;
                        color: #1e3a8a;
                        margin: 0;
                        text-transform: uppercase;
                        letter-spacing: 0.5px;
                    }
                    .company-info {
                        font-size: 13px;
                        color: #4b5563;
                        margin-top: 4px;
                    }
                    .badge-container {
                        text-align: center;
                        margin: 14px 0;
                    }
                    .title-badge {
                        display: inline-block;
                        background: #2563eb;
                        color: #ffffff;
                        padding: 6px 18px;
                        font-size: 13px;
                        font-weight: 700;
                        border-radius: 20px;
                        letter-spacing: 0.5px;
                    }
                    .section-title {
                        font-size: 13px;
                        font-weight: 700;
                        color: #1e40af;
                        border-bottom: 1px solid #e5e7eb;
                        padding-bottom: 4px;
                        margin: 16px 0 8px 0;
                        text-transform: uppercase;
                    }
                    .grid-table {
                        width: 100%;
                        border-collapse: collapse;
                        margin-bottom: 12px;
                    }
                    .grid-table td {
                        padding: 5px 2px;
                        font-size: 13px;
                        vertical-align: top;
                    }
                    .label {
                        font-weight: 600;
                        color: #4b5563;
                        width: 42%;
                    }
                    .val {
                        color: #111827;
                        font-weight: 500;
                    }
                    .payment-box {
                        border: 1px solid #cbd5e1;
                        border-radius: 8px;
                        padding: 14px;
                        background: #f8fafc;
                        margin-top: 14px;
                        margin-bottom: 16px;
                    }
                    .amount-row {
                        display: flex;
                        justify-content: space-between;
                        font-size: 14px;
                        padding: 5px 0;
                    }
                    .amount-paid {
                        font-size: 17px;
                        font-weight: 800;
                        color: #16a34a;
                    }
                    .status-paid {
                        display: inline-block;
                        background: #dcfce7;
                        color: #15803d;
                        padding: 2px 10px;
                        border-radius: 4px;
                        font-size: 12px;
                        font-weight: 700;
                    }
                    .footer {
                        text-align: center;
                        font-size: 12px;
                        color: #6b7280;
                        border-top: 1px dashed #cbd5e1;
                        padding-top: 12px;
                        margin-top: 24px;
                    }
                    @media print {
                        body { padding: 0; }
                    }
                </style>
            </head>
            <body>
                <div class="header">
                    <h1 class="company-name">$ispName</h1>
                    <div class="company-info">$address</div>
                    <div class="company-info">${if (isBn) "হটলাইন/মোবাইল:" else "Hotline:"} <strong>$hotline</strong></div>
                </div>

                <div class="badge-container">
                    <div class="title-badge">${if (isBn) "পেমেন্ট রশিদ (OFFICIAL PAYMENT RECEIPT)" else "OFFICIAL PAYMENT RECEIPT"}</div>
                </div>

                <div class="section-title">${if (isBn) "গ্রাহকের তথ্য (CUSTOMER DETAILS)" else "CUSTOMER DETAILS"}</div>
                <table class="grid-table">
                    <tr>
                        <td class="label">${if (isBn) "গ্রাহকের নাম (Customer Name):" else "Customer Name:"}</td>
                        <td class="val"><strong>$custName</strong> ($custCode)</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "মোবাইল (Phone):" else "Phone Number:"}</td>
                        <td class="val">$custPhone</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "ইউজারনেম (Username):" else "PPPoE Username:"}</td>
                        <td class="val">$pppoeUser</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "প্যাকেজ (Package):" else "Package Name:"}</td>
                        <td class="val">$packageName</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "ঠিকানা (Address):" else "Address:"}</td>
                        <td class="val">$custAddress</td>
                    </tr>
                </table>

                <div class="section-title">${if (isBn) "পেমেন্ট ও বিল তথ্য (PAYMENT & BILL DETAILS)" else "PAYMENT & BILL DETAILS"}</div>
                <table class="grid-table">
                    <tr>
                        <td class="label">${if (isBn) "রশিদ নং (Receipt No):" else "Receipt No:"}</td>
                        <td class="val"><strong style="color:#1e3a8a;">$receiptNo</strong></td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "ইনভয়েস নং (Invoice No):" else "Invoice No:"}</td>
                        <td class="val">$invNo</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "পেমেন্টের তারিখ (Date):" else "Payment Date:"}</td>
                        <td class="val">${payment.paymentDate}</td>
                    </tr>
                    <tr>
                        <td class="label">${if (isBn) "বিলিং মাস (Bill Month):" else "Billing Month:"}</td>
                        <td class="val">$billMonth</td>
                    </tr>
                </table>

                <div class="payment-box">
                    <div class="amount-row">
                        <span>${if (isBn) "মোট বিল পরিমাণ (Total Bill):" else "Total Bill Amount:"}</span>
                        <span>$currency $billAmt</span>
                    </div>
                    <div class="amount-row">
                        <span>${if (isBn) "পরিশোধিত পরিমাণ (Paid Amount):" else "Paid Amount:"}</span>
                        <span class="amount-paid">$currency $paidAmt</span>
                    </div>
                    <div class="amount-row">
                        <span>${if (isBn) "অবশিষ্ট বকেয়া (Remaining Due):" else "Remaining Due:"}</span>
                        <span>$currency $dueAmt</span>
                    </div>
                    <div class="amount-row" style="margin-top: 8px; border-top: 1px dashed #cbd5e1; padding-top: 8px;">
                        <span>${if (isBn) "পেমেন্ট মাধ্যম (Method):" else "Payment Method:"}</span>
                        <span><strong>${payment.paymentMethod}</strong></span>
                    </div>
                    <div class="amount-row">
                        <span>${if (isBn) "পেমেন্ট স্ট্যাটাস (Status):" else "Payment Status:"}</span>
                        <span class="status-paid">${if (isBn) "PAID (পরিশোধিত)" else "PAID"}</span>
                    </div>
                </div>

                <div class="footer">
                    <p><strong>${if (isBn) "আমাদের ইন্টারনেট সেবা ব্যবহার করার জন্য আপনাকে ধন্যবাদ!" else "Thank you for using our internet service!"}</strong></p>
                    <p style="font-size:10px; font-style:italic;">This is a computer-generated digital receipt issued by $ispName.</p>
                </div>
            </body>
            </html>
        """.trimIndent()
    }

    fun printReceipt(context: Context, htmlContent: String, jobName: String = "Payment_Receipt") {
        Handler(Looper.getMainLooper()).post {
            try {
                val activity = findActivity(context) ?: context
                val webView = WebView(activity)
                activeWebView = webView

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        try {
                            val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                            if (printManager != null) {
                                val printAdapter = webView.createPrintDocumentAdapter(jobName)
                                printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                            } else {
                                Toast.makeText(activity, "প্রিন্ট সার্ভিস এই ডিভাইসে সমর্থিত নয়", Toast.LENGTH_SHORT).show()
                            }
                        } catch (t: Throwable) {
                            t.printStackTrace()
                            Toast.makeText(activity, "প্রিন্ট বা পিডিএফ খুলতে ব্যর্থ: ${t.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
                webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
            } catch (t: Throwable) {
                t.printStackTrace()
                Toast.makeText(context, "প্রিন্ট অপশন খুলতে সমস্যা হয়েছে: ${t.localizedMessage ?: "Unknown error"}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun savePdfReceipt(context: Context, htmlContent: String, fileName: String = "Payment_Receipt.pdf") {
        printReceipt(context, htmlContent, fileName.removeSuffix(".pdf"))
    }

    fun shareReceiptText(
        context: Context,
        payment: PaymentEntity,
        customer: CustomerEntity?,
        settings: BusinessSettingsEntity,
        isBn: Boolean = true
    ) {
        try {
            val ispName = settings.ispName.ifBlank { if (isBn) "আইএসপি ডিজিটাল নেটওয়ার্ক" else "ISP Digital Network" }
            val hotline = settings.hotline.ifBlank { if (isBn) "০১৭০০-০০০০০০" else "01700-000000" }
            val custName = customer?.name ?: payment.customerName
            val custCode = customer?.customerCode ?: "CUST-${payment.customerId}"
            val currency = settings.currencySymbol.ifBlank { "৳" }

            val text = """
                🧾 *পেমেন্ট রশিদ (Payment Receipt)*
                --------------------------------
                🏢 *$ispName*
                📞 Hotline: $hotline

                👤 গ্রাহক: $custName ($custCode)
                🆔 রশিদ নং: ${payment.paymentReceiptNo}
                📅 তারিখ: ${payment.paymentDate}
                💳 মাধ্যম: ${payment.paymentMethod}
                💰 পরিশোধিত পরিমাণ: $currency${payment.amount}
                --------------------------------
                ধন্যবাদ আমাদের সাথে থাকার জন্য!
            """.trimIndent()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Payment Receipt - ${payment.paymentReceiptNo}")
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(Intent.createChooser(shareIntent, if (isBn) "রশিদ শেয়ার করুন" else "Share Receipt"))
        } catch (t: Throwable) {
            t.printStackTrace()
            Toast.makeText(context, "শেয়ার করতে ব্যর্থ হয়েছে: ${t.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}
