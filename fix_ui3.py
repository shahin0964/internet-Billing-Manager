import re

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "r") as f:
    content = f.read()

state_vars_offset = """
    var dueOffset by remember { mutableStateOf(AutomaticSmsManager.getDueReminderOffset(context)) }
    var selectedSim by remember { mutableStateOf(AutomaticSmsManager.getSelectedSim(context)) }
"""

content = content.replace("    var selectedSim by remember { mutableStateOf(AutomaticSmsManager.getSelectedSim(context)) }", state_vars_offset)

offset_card = """
        // Due Offset Selection
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = if (isBn) "বকেয়া বিলের মেসেজ পাঠানোর সময়" else "Due Reminder Timing",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isBn) "বকেয়া বিলের মেসেজ কবে পাঠানো হবে তা নির্বাচন করুন:" else "Select when the due reminder should be sent:",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    
                    var expandedOffset by remember { mutableStateOf(false) }
                    val offsetLabel = when (dueOffset) {
                        -3 -> if (isBn) "বিলের শেষ তারিখের ৩ দিন আগে" else "3 days before due date"
                        -1 -> if (isBn) "বিলের শেষ তারিখের ১ দিন আগে" else "1 day before due date"
                        0 -> if (isBn) "বিলের শেষ তারিখে" else "On due date"
                        1 -> if (isBn) "বিলের শেষ তারিখের ১ দিন পর" else "1 day after due date"
                        3 -> if (isBn) "বিলের শেষ তারিখের ৩ দিন পর" else "3 days after due date"
                        else -> if (isBn) "বিলের শেষ তারিখে" else "On due date"
                    }
                    
                    Box {
                        OutlinedButton(onClick = { expandedOffset = true }, modifier = Modifier.fillMaxWidth()) {
                            Text(text = offsetLabel)
                        }
                        DropdownMenu(expanded = expandedOffset, onDismissRequest = { expandedOffset = false }) {
                            val options = listOf(-3, -1, 0, 1, 3)
                            options.forEach { opt ->
                                val lbl = when (opt) {
                                    -3 -> if (isBn) "বিলের শেষ তারিখের ৩ দিন আগে" else "3 days before due date"
                                    -1 -> if (isBn) "বিলের শেষ তারিখের ১ দিন আগে" else "1 day before due date"
                                    0 -> if (isBn) "বিলের শেষ তারিখে" else "On due date"
                                    1 -> if (isBn) "বিলের শেষ তারিখের ১ দিন পর" else "1 day after due date"
                                    3 -> if (isBn) "বিলের শেষ তারিখের ৩ দিন পর" else "3 days after due date"
                                    else -> ""
                                }
                                DropdownMenuItem(
                                    text = { Text(text = lbl) },
                                    onClick = {
                                        dueOffset = opt
                                        AutomaticSmsManager.setDueReminderOffset(context, opt)
                                        expandedOffset = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
"""

content = content.replace("        // Due Bill", offset_card.strip() + "\n\n        // Due Bill")

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "w") as f:
    f.write(content)

