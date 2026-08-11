import re

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "r") as f:
    content = f.read()

sim_card = """
        item {
            val availableSims = remember { AutomaticSmsManager.getAvailableSims(context) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "এসএমএস পাঠানোর সিম (SMS Sending SIM)" else "SMS Sending SIM",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    var expandedSim by remember { mutableStateOf(false) }
                    val currentSimLabel = if (selectedSim == -1) {
                        if (isBn) "সিস্টেম ডিফল্ট সিম" else "System Default SIM"
                    } else {
                        val simInfo = availableSims.find { it.subscriptionId == selectedSim }
                        if (simInfo != null) "SIM ${simInfo.slotIndex + 1} - ${simInfo.carrierName} - ${simInfo.number}" else (if (isBn) "নির্বাচিত সিম পাওয়া যায়নি" else "Selected SIM unavailable")
                    }

                    Box {
                        OutlinedButton(onClick = { expandedSim = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(text = currentSimLabel)
                        }
                        DropdownMenu(expanded = expandedSim, onDismissRequest = { expandedSim = false }) {
                            DropdownMenuItem(
                                text = { Text(if (isBn) "সিস্টেম ডিফল্ট সিম" else "System Default SIM") },
                                onClick = {
                                    selectedSim = -1
                                    AutomaticSmsManager.setSelectedSim(context, -1)
                                    expandedSim = false
                                }
                            )
                            availableSims.forEach { sim ->
                                DropdownMenuItem(
                                    text = { Text("SIM ${sim.slotIndex + 1} - ${sim.carrierName} - ${sim.number}") },
                                    onClick = {
                                        selectedSim = sim.subscriptionId
                                        AutomaticSmsManager.setSelectedSim(context, sim.subscriptionId)
                                        expandedSim = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
"""

content = content.replace("// Bill Generated", sim_card + "\n// Bill Generated")

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "w") as f:
    f.write(content)
