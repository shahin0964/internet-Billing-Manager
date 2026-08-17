import re
import random
import time

with open("app/src/main/java/com/example/data/repository/IspRepository.kt", "r") as f:
    content = f.read()

# Add generateUniqueId to IspRepository class
generate_method = """
    @Volatile private var idCounter = 0
    private fun generateUniqueId(): Long {
        val count = synchronized(this) { idCounter++ }
        return (System.currentTimeMillis() * 10000L) + (1000..8999).random() + (count % 1000)
    }
"""
content = re.sub(r'class IspRepository\([^)]+\)\s*\{', lambda m: m.group(0) + generate_method, content)

# Patch logActivity
content = re.sub(
    r'(val log = AuditLogEntity\(\s*)(action = action,)',
    r'\1id = generateUniqueId(),\n                \2',
    content
)

# Patch saveExpense
content = re.sub(
    r'val result = expenseDao\.insertExpense\(expense\)',
    r'val expenseToSave = if (expense.id == 0L) expense.copy(id = generateUniqueId()) else expense\n        val result = expenseDao.insertExpense(expenseToSave)',
    content
)

# Patch saveExpenseCategory
content = re.sub(
    r'val result = expenseDao\.insertCategory\(ExpenseCategoryEntity\(name = categoryName\.trim\(\)\)\)',
    r'val result = expenseDao.insertCategory(ExpenseCategoryEntity(id = generateUniqueId(), name = categoryName.trim()))',
    content
)

# Patch saveCustomer
content = re.sub(
    r'(val isNew = customer\.id == 0L\s*)val result = customerDao\.insertCustomer\(customer\)',
    r'\1val customerToSave = if (isNew) customer.copy(id = generateUniqueId()) else customer\n        val result = customerDao.insertCustomer(customerToSave)',
    content
)
content = re.sub(
    r'(targetId = if \(isNew\) result\.toString\(\) else )customer\.id\.toString\(\)',
    r'\1customerToSave.id.toString()',
    content
)

# Patch createPreviousDues
content = re.sub(
    r'(BillEntity\(\s*)billNumber = billNo,',
    r'\1id = generateUniqueId(),\n                billNumber = billNo,',
    content
)

# Patch saveCustomers (Import)
content = re.sub(
    r'customerDao\.insertCustomers\(customers\)',
    r'val customersToSave = customers.map { if (it.id == 0L) it.copy(id = generateUniqueId()) else it }\n        customerDao.insertCustomers(customersToSave)',
    content
)

# Patch savePackage
content = re.sub(
    r'(val isNew = pkg\.id == 0L\s*)val result = packageDao\.insertPackage\(pkg\)',
    r'\1val pkgToSave = if (isNew) pkg.copy(id = generateUniqueId()) else pkg\n        val result = packageDao.insertPackage(pkgToSave)',
    content
)
content = re.sub(
    r'(targetId = if \(isNew\) result\.toString\(\) else )pkg\.id\.toString\(\)',
    r'\1pkgToSave.id.toString()',
    content
)

# Patch generateMonthlyBills
content = re.sub(
    r'(newBills\.add\(\s*BillEntity\(\s*)billNumber = billNo,',
    r'\1id = generateUniqueId(),\n                    billNumber = billNo,',
    content
)

# Patch recordPayment
content = re.sub(
    r'(val payment = PaymentEntity\(\s*)paymentReceiptNo = receiptNo,',
    r'\1id = generateUniqueId(),\n            paymentReceiptNo = receiptNo,',
    content
)

# Patch createNewDiagram
content = re.sub(
    r'val diag = NetworkDiagramEntity\(name = name\.ifBlank \{ "Network Topology" \}\)',
    r'val diag = NetworkDiagramEntity(id = generateUniqueId(), name = name.ifBlank { "Network Topology" })',
    content
)

# Patch getDefaultDiagram
content = re.sub(
    r'val defaultDiag = NetworkDiagramEntity\(name = "Default Topology", isDefault = true\)',
    r'val defaultDiag = NetworkDiagramEntity(id = generateUniqueId(), name = "Default Topology", isDefault = true)',
    content
)

with open("app/src/main/java/com/example/data/repository/IspRepository.kt", "w") as f:
    f.write(content)
print("Patcher executed successfully.")
