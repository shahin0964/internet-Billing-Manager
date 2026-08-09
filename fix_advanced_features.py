with open("app/src/main/java/com/example/ui/screens/AdvancedFeaturesScreen.kt", "r") as f:
    content = f.read()

# Make sure we add the imports we need.
import_str = "import androidx.compose.runtime.getValue\nimport androidx.compose.runtime.setValue\nimport androidx.compose.runtime.mutableStateOf\nimport androidx.compose.runtime.remember\n"

content = content.replace("import androidx.compose.runtime.Composable", import_str + "import androidx.compose.runtime.Composable")

# Add the Network Tools item
new_item = """
            // Network Tools Feature Entry
            item {
                var showNetworkTools by remember { mutableStateOf(false) }
                if (showNetworkTools) {
                    androidx.compose.ui.window.Dialog(
                        onDismissRequest = { showNetworkTools = false },
                        properties = androidx.compose.ui.window.DialogProperties(
                            usePlatformDefaultWidth = false
                        )
                    ) {
                        NetworkToolsScreen(
                            onBackClick = { showNetworkTools = false }
                        )
                    }
                }

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showNetworkTools = true },
                    shape = RoundedCornerShape(18.dp),
                    shadowElevation = 4.dp,
                    tonalElevation = 2.dp,
                    color = MaterialTheme.colorScheme.surface,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier
                                        .padding(10.dp)
                                        .fillMaxSize()
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = stringResource(com.example.R.string.network_tools),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(com.example.R.string.network_tools_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
"""

# Append to the cut off file
content = content + new_item

with open("app/src/main/java/com/example/ui/screens/AdvancedFeaturesScreen.kt", "w") as f:
    f.write(content)
