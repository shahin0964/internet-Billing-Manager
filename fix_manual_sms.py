import re

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "r") as f:
    content = f.read()

# Replace the DropdownMenu implementation with a LazyColumn implementation inside the dialog
old_dropdown_box = """                    Box(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (selectedCustomer != null && it != selectedCustomer?.name) {
                                    selectedCustomer = null
                                    customNumber = ""
                                }
                                dropdownExpanded = true
                            },
                            placeholder = { Text(if (isBn) "খুঁজুন (নাম বা মোবাইল)" else "Search (Name or phone)") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { dropdownExpanded = !dropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )

                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false },
                            modifier = Modifier
                                .fillMaxWidth(0.9f)
                                .heightIn(max = 250.dp)
                        ) {
                            if (filteredCustomers.isEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(if (isBn) "কোনো গ্রাহক পাওয়া যায়নি" else "No customers found") },
                                    onClick = {}
                                )
                            } else {
                                filteredCustomers.take(20).forEach { customer ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(text = customer.name, fontWeight = FontWeight.Bold)
                                                Text(text = "📞 ${customer.phone} | ID: ${customer.customerCode}", style = MaterialTheme.typography.bodySmall)
                                            }
                                        },
                                        onClick = {
                                            selectedCustomer = customer
                                            customNumber = customer.phone
                                            dropdownExpanded = false
                                            searchQuery = customer.name
                                        }
                                    )
                                }
                            }
                        }
                    }"""

new_dropdown_box = """                    Column(modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = selectedCustomer?.name ?: searchQuery,
                            onValueChange = {
                                searchQuery = it
                                if (selectedCustomer != null && it != selectedCustomer?.name) {
                                    selectedCustomer = null
                                    customNumber = ""
                                }
                                dropdownExpanded = true
                            },
                            placeholder = { Text(if (isBn) "খুঁজুন (নাম, মোবাইল বা আইডি)" else "Search (Name, Phone or ID)") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = {
                                IconButton(onClick = { dropdownExpanded = !dropdownExpanded }) {
                                    Icon(imageVector = Icons.Default.ArrowDropDown, contentDescription = null)
                                }
                            }
                        )
                        
                        androidx.compose.animation.AnimatedVisibility(visible = dropdownExpanded) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 250.dp)
                                    .padding(top = 4.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                if (filteredCustomers.isEmpty()) {
                                    Text(
                                        text = if (isBn) "কোনো গ্রাহক পাওয়া যায়নি" else "No customers found",
                                        modifier = Modifier.padding(16.dp),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                } else {
                                    androidx.compose.foundation.lazy.LazyColumn(
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        items(filteredCustomers.size) { index ->
                                            val customer = filteredCustomers[index]
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        selectedCustomer = customer
                                                        customNumber = customer.phone
                                                        dropdownExpanded = false
                                                        searchQuery = customer.name
                                                    }
                                                    .padding(16.dp)
                                            ) {
                                                Column {
                                                    Text(text = customer.name, fontWeight = FontWeight.Bold)
                                                    Text(text = "📞 ${customer.phone} | ID: ${customer.customerCode}", style = MaterialTheme.typography.bodySmall)
                                                }
                                            }
                                            if (index < filteredCustomers.size - 1) {
                                                HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }"""

if old_dropdown_box in content:
    content = content.replace(old_dropdown_box, new_dropdown_box)
    with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "w") as f:
        f.write(content)
    print("Successfully replaced dialog UI!")
else:
    print("Could not find old dropdown box.")
    
