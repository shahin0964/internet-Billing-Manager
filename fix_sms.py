import re

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "r") as f:
    content = f.read()

# Fix queueManualSms
old_manual = r"""    suspend fun queueManualSms\(
        context: Context,
        customerId: Long,
        customerName: String,
        mobileNumber: String,
        message: String
    \) \{
        val ispDb = com\.example\.data\.database\.IspDatabase\.getDatabase\(context\)
        val customer = ispDb\.customerDao\(\)\.getCustomerByIdSync\(customerId\)
        val bills = ispDb\.billDao\(\)\.getBillsForCustomerSync\(customerId\)
        val settings = ispDb\.settingsDao\(\)\.getSettingsSync\(\)
        
        val ispName = settings\?\.ispName \?\: "ISP Net"
        val totalDue = bills\?\.sumOf \{ it\.dueAmount \} \?\: 0\.0
        val packageName = customer\?\.packageName \?\: ""
        val monthlyFee = customer\?\.monthlyFee\?\.toString\(\) \?\: "0"
        
        val processedMessage = processTemplate\(
            template = message,
            customerName = customerName,
            monthlyFee = monthlyFee,
            dueAmount = totalDue\.toString\(\),
            packageSpeed = packageName,
            ispName = ispName,
            customerId = customerId\.toString\(\)
        \)"""

new_manual = """    suspend fun queueManualSms(
        context: Context,
        customerId: Long,
        customerName: String,
        mobileNumber: String,
        message: String
    ) {
        val ispDb = com.example.data.database.IspDatabase.getDatabase(context)
        val customer = ispDb.customerDao().getCustomerById(customerId).kotlinx.coroutines.flow.firstOrNull()
        val bills = ispDb.billDao().getBillsForCustomer(customerId).kotlinx.coroutines.flow.firstOrNull() ?: emptyList()
        val settings = ispDb.settingsDao().getSettings().kotlinx.coroutines.flow.firstOrNull()
        
        val ispName = settings?.ispName ?: "ISP Net"
        val totalDue = bills.sumOf { it.dueAmount }
        val packageName = customer?.packageName ?: ""
        val monthlyFee = customer?.monthlyFee?.toString() ?: "0"
        
        val currentBill = bills.firstOrNull()
        val billMonth = currentBill?.billingMonth ?: java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale("bn", "BD")).format(java.util.Date())
        val dueDate = currentBill?.dueDate ?: ""
        
        val processedMessage = processTemplate(
            template = message,
            customerName = customerName,
            monthlyFee = monthlyFee,
            dueAmount = totalDue.toString(),
            dueDate = dueDate,
            packageSpeed = packageName,
            ispName = ispName,
            billMonth = billMonth,
            customerId = customerId.toString()
        )"""

content = re.sub(old_manual, new_manual, content)


# Ensure processTemplate supports the aliases
old_process = r"""    fun processTemplate\(
        template: String,
        customerName: String = "",
        monthlyFee: String = "",
        dueAmount: String = "",
        dueDate: String = "",
        paymentAmount: String = "",
        paymentDate: String = "",
        packageSpeed: String = "",
        ispName: String = "",
        billMonth: String = "",
        customerId: String = ""
    \): String \{
        return template
            // New user requested placeholders
            \.replace\("\{customer_name\}", customerName\)
            \.replace\("\{bill_month\}", billMonth\)
            \.replace\("\{bill_amount\}", monthlyFee\)
            \.replace\("\{due_amount\}", dueAmount\)
            \.replace\("\{due_date\}", dueDate\)
            \.replace\("\{payment_date\}", paymentDate\)
            \.replace\("\{customer_id\}", customerId\)"""

new_process = """    fun processTemplate(
        template: String,
        customerName: String = "",
        monthlyFee: String = "",
        dueAmount: String = "",
        dueDate: String = "",
        paymentAmount: String = "",
        paymentDate: String = "",
        packageSpeed: String = "",
        ispName: String = "",
        billMonth: String = "",
        customerId: String = ""
    ): String {
        return template
            // New user requested placeholders
            .replace("{customer_name}", customerName)
            .replace("{billing_month}", billMonth)
            .replace("{bill_month}", billMonth)
            .replace("{bill_amount}", monthlyFee)
            .replace("{monthly_bill}", monthlyFee)
            .replace("{due_amount}", dueAmount)
            .replace("{due_date}", dueDate)
            .replace("{payment_date}", paymentDate)
            .replace("{customer_id}", customerId)
            .replace("{company_name}", ispName)"""

content = re.sub(old_process, new_process, content)

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "w") as f:
    f.write(content)
