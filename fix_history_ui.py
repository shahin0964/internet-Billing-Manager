import re

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "r") as f:
    content = f.read()

# I want to add a block for SENT showing lastError
old_ui = r"""            // If Failed, display error reason logs
            if \(sms\.status == "FAILED" && !sms\.lastError\.isNullOrBlank\(\)\) \{
                Spacer\(modifier = Modifier\.height\(8\.dp\)\)
                Box\("""

new_ui = """            // If SENT, display SIM info if available
            if (sms.status == "SENT" && !sms.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📱 ${sms.lastError}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            // If Failed, display error reason logs
            if (sms.status == "FAILED" && !sms.lastError.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Box("""

content = re.sub(old_ui, new_ui, content, flags=re.MULTILINE)

with open("app/src/main/java/com/example/ui/screens/AutomaticSmsScreen.kt", "w") as f:
    f.write(content)
