import os
import glob
import xml.etree.ElementTree as ET
from datetime import datetime

RES_DIR = r"C:/Users/bou/StudioProjects/AMARadio/app/src/main/res"

def parse_strings(file_path):
    strings = {}
    if not os.path.exists(file_path):
        return strings
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        for child in root:
            if child.tag == 'string':
                name = child.attrib.get('name')
                translatable = child.attrib.get('translatable', 'true')
                if translatable.lower() != 'false' and name:
                    text = child.text or ''
                    strings[name] = text.replace("\r\n", "\\n").replace("\n", "\\n")
            elif child.tag == 'string-array':
                name = child.attrib.get('name')
                translatable = child.attrib.get('translatable', 'true')
                if translatable.lower() != 'false' and name:
                    items = [item.text or '' for item in child.findall('item')]
                    strings[name] = f"[Array] {', '.join(items)}"
    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
    return strings

def export_direct_comparison():
    master_path = os.path.join(RES_DIR, "values", "strings.xml")
    master = parse_strings(master_path)

    if not master:
        print("Master strings.xml file not found.")
        return

    lang_dirs = sorted(glob.glob(os.path.join(RES_DIR, "values-*")))
    all_translations = {}

    for d in lang_dirs:
        folder_name = os.path.basename(d)
        file_path = os.path.join(d, "strings.xml")
        parsed = parse_strings(file_path)
        if parsed:
            all_translations[folder_name] = parsed

    now_dt = datetime.now()
    timestamp_file = now_dt.strftime("%Y-%m-%d_%H-%M-%S")
    timestamp_display = now_dt.strftime("%Y-%m-%d %H:%M:%S")

    script_dir = os.path.dirname(os.path.abspath(__file__))
    output_file = os.path.join(script_dir, f"strings_for_gemini_review_{timestamp_file}.txt")

    lines = [
        "#" * 80,
        "# SYSTEM INSTRUCTION FOR GEMINI AI / LOCALIZATION AUDIT",
        "#" * 80,
        f"# Project:         AMARadio (Android Open-Source Radio Application)",
        f"# Export Date:     {timestamp_display}",
        f"# Master Keys:     {len(master)} entries",
        f"# Target Locales:  {len(all_translations)} resource folders",
        "#",
        "# OBJECTIVE:",
        "# Audit each localized entry against its canonical English reference ([ORIGINAL_EN]).",
        "# Focus strictly on:",
        "# 1. Broken format specifiers or missing spaces (e.g., '%1$sText' instead of '%1$s Text').",
        "# 2. Proper nouns mistranslated (e.g., 'Cast' translated to cement, mold, plaster, or actor).",
        "# 3. Unnatural phrasing, wrong grammar, or inappropriate UI button style (e.g., wrong verb form).",
        "# 4. Missing values marked as 'MISSING!'.",
        "#",
        "# OUTPUT REQUIREMENT (CRITICAL):",
        "# Do NOT provide explanations, summaries, or polite conversational text.",
        "# Output EXACTLY ONE unified, ready-to-copy instruction prompt that can be pasted directly",
        "# into the Android Studio AI Assistant to apply all corrections at once.",
        "#",
        "# Format the response strictly as:",
        "# --- BEGIN ANDROID STUDIO PROMPT ---",
        "# Update the following strings.xml files in 'app/src/main/res/' with the exact XML replacements below:",
        "#",
        "# File: app/src/main/res/<values-folder>/strings.xml",
        "# - Replace key '<key_name>' with: <string name=\"<key_name>\">Corrected text</string>",
        "#",
        "# File: app/src/main/res/<another-values-folder>/strings.xml",
        "# - Replace key '<key_name>' with: <string name=\"<key_name>\">Corrected text</string>",
        "# --- END ANDROID STUDIO PROMPT ---",
        "#" * 80,
        "",
        "# LOCALIZATION DATA START",
        ""
    ]

    for key in sorted(master.keys()):
        lines.append(f"### KEY: {key}")
        lines.append(f"  [ORIGINAL_EN]  {master[key]}")

        for folder_name, trans_map in all_translations.items():
            if key in trans_map:
                lines.append(f"  [{folder_name:<12}] {trans_map[key]}")
            else:
                lines.append(f"  [{folder_name:<12}] MISSING!")

        lines.append("")

    try:
        with open(output_file, "w", encoding="utf-8") as f:
            f.write("\n".join(lines))
        print(f"\nReview file successfully created:")
        print(f"{output_file}\n", flush=True)
    except Exception as e:
        print(f"Error writing review export file: {e}", flush=True)

if __name__ == "__main__":
    export_direct_comparison()