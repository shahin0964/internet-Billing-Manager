with open('app/src/main/java/com/example/util/AutomaticSmsManager.kt', 'r') as f:
    content = f.read()

# find the evaluateDailyWarnings block
import re

match = re.search(r'    /\*\*\n     \* Hook to evaluate daily warnings for unpaid bills\n     \*/\n    suspend fun evaluateDailyWarnings.*?    }\n', content, re.DOTALL)
if match:
    eval_func = match.group(0)
    # remove it from current pos
    content = content.replace(eval_func, '')
    
    # put it before onBillsGenerated
    content = content.replace('    /**\n     * Hooks for Bill Generation Event\n     */', eval_func + '\n    /**\n     * Hooks for Bill Generation Event\n     */')
    
    with open('app/src/main/java/com/example/util/AutomaticSmsManager.kt', 'w') as f:
        f.write(content)
