import re

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "r") as f:
    content = f.read()

state_vars_sim = """
    var ruleGeneralNotice by remember { mutableStateOf(AutomaticSmsManager.isRuleGeneralNoticeEnabled(context)) }
    var selectedSim by remember { mutableStateOf(AutomaticSmsManager.getSelectedSim(context)) }
"""

content = content.replace("    var ruleGeneralNotice by remember { mutableStateOf(AutomaticSmsManager.isRuleGeneralNoticeEnabled(context)) }", state_vars_sim)

sim_card = """
        // SIM Selection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "এসএমএস প্রেরণের মাধ্যম (SIM Card)" else "SMS Delivery Method (SIM Card)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isBn) "কোন সিম দিয়ে মেসেজ পাঠানো হবে তা নির্বাচন করুন:" else "Select which SIM to use for sending SMS:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val simOptions = listOf(
                            0 to (if (isBn) "ডিফল্ট" else "Default"),
                            1 to "SIM 1",
                            2 to "SIM 2"
                        )
                        simOptions.forEach { (index, label) ->
                            FilterChip(
                                selected = selectedSim == index,
                                onClick = {
                                    selectedSim = index
                                    AutomaticSmsManager.setSelectedSim(context, index)
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
"""

content = content.replace("        // Bill Generated", sim_card.strip() + "\n\n        // Bill Generated")

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "w") as f:
    f.write(content)

