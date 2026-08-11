import re

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "r") as f:
    content = f.read()

# Replace simLabel calculation in SmsDashboardTab
old_sim_label = r"""    val selectedSim = AutomaticSmsManager\.getSelectedSim\(context\)
    val simLabel = when \(selectedSim\) \{
        1 -> "SIM 1"
        2 -> "SIM 2"
        else -> if \(isBn\) "সিস্টেম ডিফল্ট সিম" else "OS Default SIM"
    \}"""

new_sim_label = """    val selectedSim = AutomaticSmsManager.getSelectedSim(context)
    val availableSims = remember { AutomaticSmsManager.getAvailableSims(context) }
    val simLabel = if (selectedSim == -1) {
        if (isBn) "সিস্টেম ডিফল্ট সিম" else "OS Default SIM"
    } else {
        val simInfo = availableSims.find { it.subscriptionId == selectedSim }
        if (simInfo != null) {
            "SIM ${simInfo.slotIndex + 1} - ${simInfo.carrierName} - ${simInfo.number}"
        } else {
            if (isBn) "নির্বাচিত সিম পাওয়া যায়নি" else "Selected SIM unavailable"
        }
    }"""

content = re.sub(old_sim_label, new_sim_label, content, flags=re.MULTILINE)

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "w") as f:
    f.write(content)
