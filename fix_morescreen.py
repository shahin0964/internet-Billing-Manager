import re

with open("app/src/main/java/com/example/ui/screens/MoreScreen.kt", "r") as f:
    content = f.read()

def replace_card(title_pattern, icon_name, subtitle_pattern=None, extra_args=""):
    # This regex is meant to find the inner Row contents inside the Surface of a card
    # Usually it looks like:
    # SectionHeader(...)
    # Row(verticalAlignment = Alignment.CenterVertically) {
    #     Icon(...)
    #     Spacer(...)
    #     Icon(imageVector = Icons.Default.TouchApp, ...)
    # }
    
    # We will search for SectionHeader(.*?)Row(.*?)Icon(.*?)Icon(.*?)TouchApp(.*?)
    pass

# Better approach: find all occurrences of SectionHeader followed by Row with Icons and replace it.
pattern = re.compile(
    r'(SectionHeader\s*\([\s\S]*?\))\s*'
    r'Row\(\s*verticalAlignment\s*=\s*Alignment\.CenterVertically\s*\)\s*\{\s*'
    r'(Icon\s*\(\s*imageVector\s*=\s*[^,]+,\s*contentDescription\s*=\s*null,\s*modifier\s*=\s*Modifier\.size\([^)]+\),\s*tint\s*=\s*MaterialTheme\.colorScheme\.primary\s*\))'
    r'\s*'
    r'(Spacer\s*\(\s*modifier\s*=\s*Modifier\.width\([^)]+\)\s*\))?\s*'
    r'(Icon\s*\(\s*imageVector\s*=\s*(?:androidx\.compose\.material\.icons\.)?Icons\.Default\.TouchApp,\s*contentDescription\s*=\s*"[^"]*",\s*modifier\s*=\s*Modifier\.size\([^)]+\),\s*tint\s*=\s*MaterialTheme\.colorScheme\.onSurfaceVariant\s*\))?'
    r'\s*\}',
    re.MULTILINE
)

def replacer(match):
    section_header = match.group(1)
    icon_code = match.group(2)
    spacer = match.group(3) or "Spacer(modifier = Modifier.width(16.dp))"
    
    return f"""Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {{
                        {icon_code}
                        {spacer}
                        {section_header}
                    }}
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )"""

content_new = pattern.sub(replacer, content)

# Special case for Language which doesn't have TouchApp icon
lang_pattern = re.compile(
    r'(SectionHeader\s*\(\s*title\s*=\s*androidx\.compose\.ui\.res\.stringResource\(com\.example\.R\.string\.language\),\s*subtitle\s*=\s*if\s*\(currentLang\s*==\s*"bn"\)\s*"বাংলা"\s*else\s*"English"\s*\))\s*'
    r'Row\(\s*verticalAlignment\s*=\s*Alignment\.CenterVertically\s*\)\s*\{\s*'
    r'(Icon\s*\(\s*imageVector\s*=\s*androidx\.compose\.material\.icons\.Icons\.Default\.Language,\s*contentDescription\s*=\s*null,\s*modifier\s*=\s*Modifier\.size\([^)]+\),\s*tint\s*=\s*MaterialTheme\.colorScheme\.primary\s*\))\s*'
    r'\}',
    re.MULTILINE
)

def lang_replacer(match):
    section_header = match.group(1)
    icon_code = match.group(2)
    return f"""Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {{
                        {icon_code}
                        Spacer(modifier = Modifier.width(16.dp))
                        {section_header}
                    }}
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Tap to open",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )"""

content_new = lang_pattern.sub(lang_replacer, content_new)

with open("app/src/main/java/com/example/ui/screens/MoreScreen.kt", "w") as f:
    f.write(content_new)

