with open("app/src/main/java/com/example/ui/screens/MoreScreen.kt", "r") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if line.startswith("                        Row("):
        if lines[i+1].startswith("                        verticalAlignment"):
            # indent by 4 more
            lines[i+1] = "                            " + lines[i+1].lstrip()
        if lines[i+2].startswith("                        modifier"):
            lines[i+2] = "                            " + lines[i+2].lstrip()
        if lines[i+3].startswith("                    ) {"):
            lines[i+3] = "                        " + lines[i+3].lstrip()
        
        # also the closing brace for that row is not correctly indented
        # It's currently at the same level as the parent Row maybe? Let's check sed output

with open("app/src/main/java/com/example/ui/screens/MoreScreen.kt", "w") as f:
    f.writelines(lines)
