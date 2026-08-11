import re

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "r") as f:
    content = f.read()

old_func = r"""    private fun getSmsManagerForIndex\(context: Context, slotIndex: Int\): SmsManager \{
        if \(slotIndex == 0\) \{
            return if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.S\) \{
                context\.getSystemService\(SmsManager::class\.java\)
            \} else \{
                SmsManager\.getDefault\(\)
            \}
        \}

        val subscriptionManager = context\.getSystemService\(Context\.TELEPHONY_SUBSCRIPTION_SERVICE\) as\? SubscriptionManager
        if \(subscriptionManager != null\) \{
            try \{
                val activeList = subscriptionManager\.activeSubscriptionInfoList
                if \(activeList != null\) \{
                    val targetSlot = slotIndex - 1 // convert 1/2 selection to 0/1 index
                    val matchedSub = activeList\.firstOrNull \{ it\.simSlotIndex == targetSlot \} 
                        \?: activeList\.getOrNull\(targetSlot\) 
                        \?: activeList\.firstOrNull\(\)

                    if \(matchedSub != null\) \{
                        return if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.S\) \{
                            context\.getSystemService\(SmsManager::class\.java\)\.createForSubscriptionId\(matchedSub\.subscriptionId\)
                        \} else \{
                            @Suppress\("DEPRECATION"\)
                            SmsManager\.getSmsManagerForSubscriptionId\(matchedSub\.subscriptionId\)
                        \}
                    \}
                \}
            \} catch \(e: SecurityException\) \{
                Log\.w\(TAG, "Cannot access SubscriptionManager due to security limits: \$\{e\.message\}"\)
            \}
        \}

        return if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.S\) \{
            context\.getSystemService\(SmsManager::class\.java\)
        \} else \{
            SmsManager\.getDefault\(\)
        \}
    \}"""

new_func = """    private fun getSmsManagerForSubId(context: Context, subId: Int): SmsManager {
        if (subId == -1) {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
        }

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        if (subscriptionManager != null) {
            try {
                val activeList = subscriptionManager.activeSubscriptionInfoList
                if (activeList != null) {
                    val matchedSub = activeList.firstOrNull { it.subscriptionId == subId }
                    if (matchedSub == null) {
                        throw IllegalStateException("Selected SIM (SubID: $subId) is not available.")
                    }
                    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        context.getSystemService(SmsManager::class.java).createForSubscriptionId(matchedSub.subscriptionId)
                    } else {
                        @Suppress("DEPRECATION")
                        SmsManager.getSmsManagerForSubscriptionId(matchedSub.subscriptionId)
                    }
                } else {
                    throw IllegalStateException("No active SIM cards found.")
                }
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot access SubscriptionManager due to security limits: ${e.message}")
                throw e
            }
        }

        throw IllegalStateException("SubscriptionManager is not available on this device.")
    }"""

content = re.sub(old_func, new_func, content, flags=re.MULTILINE|re.DOTALL)

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "w") as f:
    f.write(content)
