import re

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "r") as f:
    content = f.read()

content = content.replace("import kotlinx.coroutines.flow.first", "import kotlinx.coroutines.flow.first\nimport kotlinx.coroutines.flow.firstOrNull")
content = content.replace(".kotlinx.coroutines.flow.firstOrNull()", ".firstOrNull()")

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "w") as f:
    f.write(content)
