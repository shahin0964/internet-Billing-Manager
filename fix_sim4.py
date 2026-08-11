import re

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "r") as f:
    content = f.read()

old_block = r"""        val selectedSimIndex = getSelectedSim\(context\) // 0: Default, 1: SIM 1, 2: SIM 2
        val smsManager = try \{
            getSmsManagerForIndex\(context, selectedSimIndex\)
        \} catch \(e: Exception\) \{
            Log\.e\(TAG, "Error selecting SIM subscription, falling back to default SMS manager: \$\{e\.message\}"\)
            if \(Build\.VERSION\.SDK_INT >= Build\.VERSION_CODES\.S\) \{
                context\.getSystemService\(SmsManager::class\.java\)
            \} else \{
                SmsManager\.getDefault\(\)
            \}
        \}"""

new_block = """        val selectedSubId = getSelectedSim(context) // -1: Default, >0: specific subId
        val smsManager = try {
            getSmsManagerForSubId(context, selectedSubId)
        } catch (e: Exception) {
            Log.e(TAG, "Error selecting SIM subscription: ${e.message}")
            return@withContext Result.failure(e)
        }"""

content = re.sub(old_block, new_block, content, flags=re.MULTILINE)

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "w") as f:
    f.write(content)
