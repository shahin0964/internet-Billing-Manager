import re

with open("app/src/main/java/com/example/data/repository/IspRepository.kt", "r") as f:
    content = f.read()

content = re.sub(
    r'(val defaultDiag = NetworkDiagramEntity\(\s*)name = "Default Network Topology",',
    r'\1id = generateUniqueId(),\n            name = "Default Network Topology",',
    content
)

with open("app/src/main/java/com/example/data/repository/IspRepository.kt", "w") as f:
    f.write(content)
print("Patch 2 executed.")
