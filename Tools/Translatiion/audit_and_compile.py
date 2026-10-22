import os
import re
import glob
import xml.etree.ElementTree as ET

RES_DIR = r"C:/Users/bou/StudioProjects/AMARadio/app/src/main/res"

PROTECTED_BRANDS = [
    "AMARadio",
    "Radio-Browser",
    "GitHub",
    "API",
    "URL",
    "Stream",
    "HTTP",
    "HTTPS",
    "ZIP",
    "GPLv3",
    "Bluetooth",
    "A2DP",
    "Equalizer"
]

def parse_master(master_path):
    tree = ET.parse(master_path)
    root = tree.getroot()
    master_strings = {}
    master_arrays = {}

    for child in root:
        if child.tag == 'string':
            name = child.attrib.get('name')
            trans = child.attrib.get('translatable', 'true')
            text = child.text or ''
            master_strings[name] = {
                'text': text,
                'translatable': trans
            }
        elif child.tag == 'string-array':
            name = child.attrib.get('name')
            items = [item.text or '' for item in child.findall('item')]
            master_arrays[name] = items

    return master_strings, master_arrays

def audit_file(lang_dir, master_strings, master_arrays):
    folder_name = os.path.basename(lang_dir)
    file_path = os.path.join(lang_dir, "strings.xml")
    if not os.path.exists(file_path):
        return

    existing_strings = {}
    existing_arrays = {}

    if os.path.getsize(file_path) > 30:
        try:
            tree = ET.parse(file_path)
            root = tree.getroot()
            for child in root:
                if child.tag == 'string':
                    name = child.attrib.get('name')
                    existing_strings[name] = child.text or ''
                elif child.tag == 'string-array':
                    name = child.attrib.get('name')
                    existing_arrays[name] = [item.text or '' for item in child.findall('item')]
        except Exception:
            pass

    lines = ['<?xml version="1.0" encoding="utf-8"?>', '<resources>']

    for name, data in master_strings.items():
        trans = data['translatable']
        eng_text = data['text']

        if trans == 'false':
            lines.append(f'    <string name="{name}" translatable="false">{eng_text}</string>')
            continue

        if name in existing_strings and existing_strings[name].strip():
            text = existing_strings[name]
        else:
            text = eng_text

        text = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        text = text.replace("&amp;amp;", "&amp;")
        text = re.sub(r"(?<!\\)'", r"\'", text)
        text = re.sub(r'(?<!\\)"', r'\"', text)

        lines.append(f'    <string name="{name}">{text}</string>')

    for name, master_items in master_arrays.items():
        lines.append(f'    <string-array name="{name}">')
        if name in existing_arrays and len(existing_arrays[name]) == len(master_items):
            items = existing_arrays[name]
        else:
            items = master_items

        for item in items:
            item_text = item.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            item_text = item_text.replace("&amp;amp;", "&amp;")
            item_text = re.sub(r"(?<!\\)'", r"\'", item_text)
            lines.append(f'        <item>{item_text}</item>')
        lines.append('    </string-array>')

    lines.append('</resources>\n')

    with open(file_path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

    print(f"[OK] values-{folder_name.replace('values-', '')}/strings.xml auditiert, korrigiert und gespeichert.", flush=True)

def main():
    master_path = os.path.join(RES_DIR, "values", "strings.xml")
    master_strings, master_arrays = parse_master(master_path)

    all_lang_dirs = sorted(glob.glob(os.path.join(RES_DIR, "values-*")))
    excluded = ["values-night", "values-v21", "values-sw360dp-v13"]
    lang_dirs = [d for d in all_lang_dirs if os.path.basename(d) not in excluded]

    for lang_dir in lang_dirs:
        audit_file(lang_dir, master_strings, master_arrays)

if __name__ == "__main__":
    main()
