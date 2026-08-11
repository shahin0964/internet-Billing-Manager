import re

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "r") as f:
    content = f.read()

sim_class = """
    data class SimInfo(
        val subscriptionId: Int,
        val slotIndex: Int,
        val carrierName: String,
        val number: String
    )

    fun getAvailableSims(context: Context): List<SimInfo> {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()
        val list = mutableListOf<SimInfo>()
        try {
            val activeList = subscriptionManager.activeSubscriptionInfoList
            if (activeList != null) {
                for (info in activeList) {
                    list.add(
                        SimInfo(
                            subscriptionId = info.subscriptionId,
                            slotIndex = info.simSlotIndex,
                            carrierName = info.carrierName?.toString() ?: "Unknown",
                            number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                subscriptionManager.getPhoneNumber(info.subscriptionId) ?: info.number ?: ""
                            } else {
                                info.number ?: ""
                            }
                        )
                    )
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot access SubscriptionManager due to security limits: ${e.message}")
        }
        return list
    }
"""

# inject at line 103 (after setDefaultSendingTime)
lines = content.split('\n')
for i, line in enumerate(lines):
    if "fun setDefaultSendingTime" in line:
        lines.insert(i + 1, sim_class)
        break

content = '\n'.join(lines)

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "w") as f:
    f.write(content)
