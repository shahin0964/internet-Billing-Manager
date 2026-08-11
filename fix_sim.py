import re

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "r") as f:
    content = f.read()

# Replace getSelectedSim to use KEY_SELECTED_SUB_ID instead of KEY_SELECTED_SIM, default -1
content = content.replace(
    'fun getSelectedSim(context: Context): Int = getPrefs(context).getInt(KEY_SELECTED_SIM, 0)',
    'fun getSelectedSim(context: Context): Int = getPrefs(context).getInt("key_selected_sub_id", -1)'
)
content = content.replace(
    'fun setSelectedSim(context: Context, sim: Int) = getPrefs(context).edit().putInt(KEY_SELECTED_SIM, sim).apply()',
    'fun setSelectedSim(context: Context, subId: Int) = getPrefs(context).edit().putInt("key_selected_sub_id", subId).apply()'
)

with open("app/src/main/java/com/example/util/AutomaticSmsManager.kt", "w") as f:
    f.write(content)
