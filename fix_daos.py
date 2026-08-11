with open("app/src/main/java/com/example/data/dao/Daos.kt", "r") as f:
    content = f.read()

import re
content = re.sub(r'    @Query\("SELECT \* FROM customers WHERE id = :id"\)\n    suspend fun getCustomerByIdSync\(id: Long\): CustomerEntity\?\n', '', content)
content = re.sub(r'    @Query\("SELECT \* FROM bills WHERE customerId = :customerId ORDER BY id DESC"\)\n    suspend fun getBillsForCustomerSync\(customerId: Long\): List<BillEntity>\n', '', content)
content = re.sub(r'    @Query\("SELECT \* FROM business_settings LIMIT 1"\)\n    suspend fun getSettingsSync\(\): BusinessSettingsEntity\?\n', '', content)

with open("app/src/main/java/com/example/data/dao/Daos.kt", "w") as f:
    f.write(content)
