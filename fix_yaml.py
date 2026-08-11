import re

with open(".github/workflows/android-release.yml", "r") as f:
    content = f.read()

old_step = r"""      - name: Build Release APK"""

new_step = """      - name: Setup Firebase Config
        env:
          GOOGLE_SERVICES_JSON: ${{ secrets.GOOGLE_SERVICES_JSON }}
        run: |
          if [ -n "$GOOGLE_SERVICES_JSON" ]; then
            echo "$GOOGLE_SERVICES_JSON" > app/google-services.json
          fi

      - name: Build Release APK"""

content = content.replace(old_step, new_step)

with open(".github/workflows/android-release.yml", "w") as f:
    f.write(content)
