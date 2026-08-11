import re

with open("app/src/main/java/com/example/util/SmsQueueWorker.kt", "r") as f:
    content = f.read()

# find:
old_worker = r"""            if \(sendResult\.isSuccess\) \{
                Log\.d\(TAG, "Successfully sent SMS ID \$\{sms\.id\} to \$cleanNumber"\)
                dao\.updateSms\(
                    sms\.copy\(
                        status = "SENT",
                        lastError = null,
                        mobileNumber = cleanNumber // Update to the actual number sent to
                    \)
                \)
            \}"""

new_worker = """            if (sendResult.isSuccess) {
                Log.d(TAG, "Successfully sent SMS ID ${sms.id} to $cleanNumber")
                
                // Fetch the SIM label to store in history
                val selectedSubId = AutomaticSmsManager.getSelectedSim(context)
                val availableSims = AutomaticSmsManager.getAvailableSims(context)
                val simLabel = if (selectedSubId == -1) "OS Default SIM" else {
                    val simInfo = availableSims.find { it.subscriptionId == selectedSubId }
                    if (simInfo != null) "SIM ${simInfo.slotIndex + 1}" else "Unknown SIM"
                }
                
                dao.updateSms(
                    sms.copy(
                        status = "SENT",
                        lastError = "Sent via $simLabel", // Use lastError field to store SIM info for history
                        mobileNumber = cleanNumber
                    )
                )
            }"""

content = re.sub(old_worker, new_worker, content, flags=re.MULTILINE)

with open("app/src/main/java/com/example/util/SmsQueueWorker.kt", "w") as f:
    f.write(content)
