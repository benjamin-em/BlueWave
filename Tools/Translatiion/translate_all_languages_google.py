import os
import re
import glob
import time
import random
import shutil
import urllib.request
import urllib.parse
import urllib.error
import json
import xml.etree.ElementTree as ET
from datetime import datetime

RES_DIR = r"C:/Users/bou/StudioProjects/AMARadio/app/src/main/res"

def get_request_delay():
    return random.uniform(0.8, 1.2)

FOLDER_TO_GOOGLE_LANG = {
    'values-af': 'af', 'values-am': 'am', 'values-ar': 'ar', 'values-az': 'az',
    'values-be': 'be', 'values-bg': 'bg', 'values-bn': 'bn', 'values-bs': 'bs',
    'values-ca': 'ca', 'values-cs': 'cs', 'values-da': 'da', 'values-de': 'de',
    'values-el': 'el', 'values-es': 'es', 'values-et': 'et', 'values-eu': 'eu',
    'values-fa': 'fa', 'values-fi': 'fi', 'values-fil': 'tl', 'values-fr': 'fr',
    'values-ga': 'ga', 'values-gl': 'gl', 'values-gu': 'gu', 'values-he': 'iw',
    'values-hi': 'hi', 'values-hr': 'hr', 'values-hu': 'hu', 'values-hy': 'hy',
    'values-in': 'id', 'values-is': 'is', 'values-it': 'it', 'values-ja': 'ja',
    'values-ka': 'ka', 'values-kk': 'kk', 'values-km': 'km', 'values-kn': 'kn',
    'values-ko': 'ko', 'values-lt': 'lt', 'values-lv': 'lv', 'values-mk': 'mk',
    'values-ml': 'ml', 'values-mr': 'mr', 'values-ms': 'ms', 'values-mt': 'mt',
    'values-my': 'my', 'values-nb': 'no', 'values-ne': 'ne', 'values-nl': 'nl',
    'values-nn': 'no', 'values-pl': 'pl', 'values-pt': 'pt', 'values-pt-rBR': 'pt',
    'values-ro': 'ro', 'values-ru': 'ru', 'values-si': 'si', 'values-sk': 'sk',
    'values-sl': 'sl', 'values-sq': 'sq', 'values-sr': 'sr', 'values-sv': 'sv',
    'values-sw': 'sw', 'values-ta': 'ta', 'values-te': 'te', 'values-th': 'th',
    'values-tr': 'tr', 'values-uk': 'uk', 'values-ur': 'ur', 'values-uz': 'uz',
    'values-vi': 'vi', 'values-zh-rCN': 'zh-CN', 'values-zh-rHK': 'zh-TW',
    'values-zh-rTW': 'zh-TW', 'values-zu': 'zu',
    'values-as': 'as', 'values-ha': 'ha', 'values-ig': 'ig', 'values-ji': 'yi',
    'values-ky': 'ky', 'values-lo': 'lo', 'values-mn': 'mn', 'values-or': 'or',
    'values-pa': 'pa', 'values-ps': 'ps', 'values-rm': 'rm', 'values-xh': 'xh',
    'values-yi': 'yi', 'values-yo': 'yo'
}

MIRROR_LANG_FOLDERS = {
    'values-he': 'values-iw',
    'values-in': 'values-id'
}

PROTECTED_VALUES = {
    "AMARadio",
    "AMARadio Player",
    "AMARadio Compact",
    "Radio-Browser",
    "Radio-browser",
    "radio-browser.info",
    "Radio-Browser API",
    "GitHub",
    "Play Store",
    "M3U",
    "ZIP",
    "Android Auto",
    "GoogleCast",
    "URL",
    "Cast",
    "A2DP",
    "Bluetooth",
    "Wi-Fi",
    "RadioDroid",
    "Kotlin",
    "Java",
    "GPLv3",
    "ignore_battery_optimization",
    "kbps",
    "N/A"
}

RATE_LIMIT_HIT = False

def translate_google(text, target_lang):
    global RATE_LIMIT_HIT
    if RATE_LIMIT_HIT or not text or text.startswith("@string/") or text in PROTECTED_VALUES:
        return None if RATE_LIMIT_HIT else text

    placeholders = []
    def mask_match(match):
        placeholders.append(match.group(0))
        return f" XPLACEHOLDER{len(placeholders)-1}X "

    pattern = r'%(\d+\$)?[0-9\.\-]*[a-zA-Z]'
    masked_text = re.sub(pattern, mask_match, text)
    masked_text = masked_text.replace("\\n", " XNEWLINEHANDLERX ")

    url = 'https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=' + target_lang + '&dt=t&q=' + urllib.parse.quote(masked_text)
    req = urllib.request.Request(
        url,
        headers={
            'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
            'Accept': '*/*'
        }
    )

    try:
        time.sleep(get_request_delay())
        with urllib.request.urlopen(req, timeout=8) as res:
            data = json.loads(res.read().decode('utf-8'))

        translated = ""
        for chunk in data[0]:
            if chunk[0]:
                translated += chunk[0]

        for idx, original_ph in enumerate(placeholders):
            ph_pattern = re.compile(rf'\s*XPLACEHOLDER{idx}X\s*', re.IGNORECASE)
            translated = ph_pattern.sub(original_ph, translated)

        translated = re.sub(r'\s*XNEWLINEHANDLERX\s*', '\\\\n', translated)
        return translated.strip()

    except urllib.error.HTTPError as e:
        if e.code == 429:
            RATE_LIMIT_HIT = True
            print(f"\n[HTTP 429] Rate-Limit erreicht. Google Translate blockiert Anfragen von dieser IP.")
            return None
        return None
    except Exception:
        return None

def parse_string_keys(file_path):
    keys = {}
    if not os.path.exists(file_path):
        return keys
    try:
        tree = ET.parse(file_path)
        root = tree.getroot()
        for child in root:
            if child.tag == 'string':
                name = child.attrib.get('name')
                translatable = child.attrib.get('translatable', 'true')
                if translatable != 'false' and name:
                    keys[name] = child.text or ''
    except Exception as e:
        print(f"Error parsing {file_path}: {e}")
    return keys

def format_xml_value(text):
    if text.startswith("@string/"):
        return text
    val = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    val = val.replace("&amp;amp;", "&amp;")
    val = re.sub(r"(?<!\\)'", r"\'", val)
    val = re.sub(r'(?<!\\)"', r'\"', val)
    if val.startswith(('@', '?')):
        val = '\\' + val
    return val

def verify_folder_structure():
    required_folders = set(FOLDER_TO_GOOGLE_LANG.keys()) | set(MIRROR_LANG_FOLDERS.keys())
    missing_items = []

    for folder_name in sorted(required_folders):
        folder_path = os.path.join(RES_DIR, folder_name)
        file_path = os.path.join(folder_path, "strings.xml")

        if not os.path.exists(folder_path):
            missing_items.append((folder_name, folder_path, file_path, "Ordner und strings.xml fehlen"))
        elif not os.path.exists(file_path):
            missing_items.append((folder_name, folder_path, file_path, "strings.xml fehlt"))

    if missing_items:
        print("\n" + "=" * 60)
        print("WARNUNG: FEHLENDE ORDNER / DATEIEN GEFUNDEN")
        print("=" * 60)
        for folder_name, _, _, reason in missing_items:
            print(f"  - {folder_name}: {reason}")
        print("=" * 60 + "\n")

        confirm = input("Fehlende Ordner/Dateien automatisch anlegen? (j/n): ").strip().lower()
        if confirm not in ['j', 'y', 'ja', 'yes']:
            print("Vorgang durch Benutzer abgebrochen.")
            return False

        print("\nLege fehlende Struktur an...")
        for folder_name, folder_path, file_path, _ in missing_items:
            os.makedirs(folder_path, exist_ok=True)
            if not os.path.exists(file_path):
                with open(file_path, "w", encoding="utf-8") as f:
                    f.write("<resources>\n</resources>\n")
                print(f"  Angelegt: {folder_name}/strings.xml")
        print("Struktur erfolgreich vervollständigt!\n")

    total_folders = len(required_folders)
    print(f"Alle {total_folders} Sprachordner und strings.xml vorhanden und bereit.\n", flush=True)
    return True

def process_language(lang_dir, missing_keys_for_lang, master_keys):
    global RATE_LIMIT_HIT
    added_translations = {}
    if RATE_LIMIT_HIT or not missing_keys_for_lang:
        return added_translations

    folder_name = os.path.basename(lang_dir)
    file_path = os.path.join(lang_dir, "strings.xml")
    if not os.path.exists(file_path):
        return added_translations

    target_lang = FOLDER_TO_GOOGLE_LANG.get(folder_name)
    if not target_lang:
        return added_translations

    print(f"Processing {folder_name} ({target_lang}) - {len(missing_keys_for_lang)} missing keys...", flush=True)

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    new_lines = []
    for k in missing_keys_for_lang:
        if RATE_LIMIT_HIT:
            return added_translations
        eng_text = master_keys[k]
        if eng_text.startswith("@string/"):
            translated = eng_text
        else:
            translated = translate_google(eng_text, target_lang)

        if translated:
            xml_val = format_xml_value(translated)
            new_lines.append(f'    <string name="{k}">{xml_val}</string>')
            added_translations[k] = translated
        else:
            print(f"  [FEHLGESCHLAGEN] Keine Übersetzung erhalten für Key '{k}' in {folder_name} ({target_lang})")

    if new_lines:
        last_index = content.rfind("</resources>")
        if last_index != -1:
            insert_block = "\n" + "\n".join(new_lines) + "\n"
            content = content[:last_index] + insert_block + content[last_index:]

    if new_lines and not RATE_LIMIT_HIT:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"--> Finished {folder_name}! Added: {len(new_lines)} keys", flush=True)

    return added_translations

def audit_remaining_missing_keys(master_keys):
    """
    Parst nach dem Schreibprozess alle Zielsprachen erneut und listet alle
    Keys auf, die weiterhin fehlen.
    """
    remaining_missing = {}
    all_check_folders = sorted(set(FOLDER_TO_GOOGLE_LANG.keys()) | set(MIRROR_LANG_FOLDERS.keys()))

    for folder_name in all_check_folders:
        file_path = os.path.join(RES_DIR, folder_name, "strings.xml")
        existing_keys = parse_string_keys(file_path)
        missing = [k for k in master_keys if k not in existing_keys]
        if missing:
            remaining_missing[folder_name] = missing

    return remaining_missing

def main():
    if not verify_folder_structure():
        return

    default_path = os.path.join(RES_DIR, "values", "strings.xml")
    master_keys = parse_string_keys(default_path)
    print(f"Master translatable keys count: {len(master_keys)}", flush=True)

    all_lang_dirs = sorted(glob.glob(os.path.join(RES_DIR, "values-*")))
    lang_dirs = [d for d in all_lang_dirs if os.path.basename(d) in FOLDER_TO_GOOGLE_LANG]
    total_primary_lang_count = len(lang_dirs)

    # Analysis / Dry-Run Phase
    analysis = {}
    key_to_missing_folders = {}
    total_api_requests = 0

    for lang_dir in lang_dirs:
        folder_name = os.path.basename(lang_dir)
        file_path = os.path.join(lang_dir, "strings.xml")
        if not os.path.exists(file_path):
            continue

        existing_keys = parse_string_keys(file_path)
        missing_keys = [k for k in master_keys if k not in existing_keys]

        if missing_keys:
            analysis[lang_dir] = missing_keys
            for k in missing_keys:
                if k not in key_to_missing_folders:
                    key_to_missing_folders[k] = []
                key_to_missing_folders[k].append(folder_name)

            api_calls_for_lang = sum(1 for k in missing_keys if not master_keys[k].startswith("@string/"))
            total_api_requests += api_calls_for_lang

    print("\n" + "=" * 60)
    print("VORSCHAU: FEHLENDE STRING-KEYS")
    print("=" * 60)

    translation_log = {}

    if not key_to_missing_folders:
        print("Alle Zielsprachen sind vollständig übersetzt! Keine neuen Keys vorhanden.")
    else:
        print(f"\nEindeutige neue String-Keys ({len(key_to_missing_folders)}):")
        for k in sorted(key_to_missing_folders.keys()):
            eng_text = master_keys[k]
            folders = key_to_missing_folders[k]
            count = len(folders)
            if count == total_primary_lang_count:
                missing_str = f"Fehlt in allen {total_primary_lang_count} Zielsprachen"
            else:
                folder_list_str = ", ".join(sorted(folders))
                missing_str = f"Fehlt in ({count}): {folder_list_str}"

            print(f"  - Key: '{k}'")
            print(f"    Text: \"{eng_text}\"")
            print(f"    - {missing_str}")

        print("\nZusammenfassung der ausstehenden Übersetzungen:")
        print(f"  - Betroffene Zielsprachen: {len(analysis)}")
        print(f"  - Geplante API-Requests:   {total_api_requests}")
        print("=" * 60 + "\n")

        confirm = input("Übersetzung starten? (j/n): ").strip().lower()
        if confirm not in ['j', 'y', 'ja', 'yes']:
            print("Vorgang durch Benutzer abgebrochen.")
            return

        print("\nStarte Übersetzung...\n", flush=True)

        for lang_dir, missing_keys in analysis.items():
            if RATE_LIMIT_HIT:
                print("\n[Abbruch] Vorgang abgebrochen wegen Rate-Limit (HTTP 429).")
                break
            folder_name = os.path.basename(lang_dir)
            target_lang = FOLDER_TO_GOOGLE_LANG.get(folder_name, '')
            added = process_language(lang_dir, missing_keys, master_keys)
            if added:
                translation_log[folder_name] = {
                    'lang': target_lang,
                    'translations': added,
                    'is_mirror': False
                }

    if not RATE_LIMIT_HIT:
        print("\nSynchronisiere gespiegelte Sprachordner...", flush=True)
        for src_folder_name, target_folder_name in MIRROR_LANG_FOLDERS.items():
            src_dir = os.path.join(RES_DIR, src_folder_name)
            src_file = os.path.join(src_dir, "strings.xml")
            if os.path.exists(src_file):
                target_dir = os.path.join(RES_DIR, target_folder_name)
                os.makedirs(target_dir, exist_ok=True)
                target_file = os.path.join(target_dir, "strings.xml")
                shutil.copy2(src_file, target_file)
                print(f"  Gespiegelt: {src_folder_name}/strings.xml -> {target_folder_name}/strings.xml")
                if src_folder_name in translation_log:
                    translation_log[target_folder_name] = {
                        'lang': FOLDER_TO_GOOGLE_LANG.get(src_folder_name, ''),
                        'translations': translation_log[src_folder_name]['translations'],
                        'is_mirror': True,
                        'mirror_src': src_folder_name
                    }

    # Finales Audit: Alle XMLs nochmals auf Vollständigkeit prüfen
    print("\n" + "=" * 60)
    print("ABSCHLIESSENDE ÜBERPRÜFUNG ALLER SPRACHDATEIEN...")
    print("=" * 60)
    remaining_missing = audit_remaining_missing_keys(master_keys)

    if not remaining_missing:
        print("HERVORRAGEND: Alle Sprachordner sind zu 100% vollständig!")
    else:
        print(f"WARNUNG: In {len(remaining_missing)} Sprachordnern fehlen noch Strings:")
        for folder, keys in sorted(remaining_missing.items()):
            print(f"  - {folder}: {len(keys)} fehlende Keys -> {keys}")
    print("=" * 60 + "\n", flush=True)

    # Logging / Report File Generation
    total_new_strings = sum(len(v['translations']) for v in translation_log.values() if not v.get('is_mirror'))
    if total_new_strings > 0 or remaining_missing:
        now_dt = datetime.now()
        timestamp_file_str = now_dt.strftime("%Y-%m-%d_%H-%M-%S")
        timestamp_formatted = now_dt.strftime("%Y-%m-%d %H:%M:%S")

        processed_lang_count = len([k for k, v in translation_log.items() if not v.get('is_mirror')])

        report_lines = []
        report_lines.append("=" * 60)
        report_lines.append(f"AMARadio Translation Report - {timestamp_formatted}")
        report_lines.append("=" * 60)
        report_lines.append(f"Bearbeitete Sprachen: {processed_lang_count}")
        report_lines.append(f"Gesamtzahl neu eingefügter Strings: {total_new_strings}")
        report_lines.append(f"Ordner mit verbleibenden Fehlern: {len(remaining_missing)}")
        report_lines.append("=" * 60)
        report_lines.append("")

        if remaining_missing:
            report_lines.append("=" * 60)
            report_lines.append("WARNUNG: WEITERHIN FEHLENDE STRINGS NACH ABSCHLUSS")
            report_lines.append("=" * 60)
            for folder, keys in sorted(remaining_missing.items()):
                report_lines.append(f"[{folder}] ({len(keys)} fehlend):")
                for k in keys:
                    report_lines.append(f"  - Key: '{k}' | EN: \"{master_keys.get(k, '')}\"")
                report_lines.append("")

        if key_to_missing_folders:
            report_lines.append("=" * 60)
            report_lines.append("AUFSCHLÜSSELUNG: URSPRÜNGLICH FEHLENDE STRINGS")
            report_lines.append("=" * 60)

            for k in sorted(key_to_missing_folders.keys()):
                eng_text = master_keys[k]
                folders = key_to_missing_folders[k]
                count = len(folders)
                if count == total_primary_lang_count:
                    missing_str = f"Fehlte in allen {total_primary_lang_count} Zielsprachen"
                else:
                    folder_list_str = ", ".join(sorted(folders))
                    missing_str = f"Fehlte in ({count}): {folder_list_str}"

                report_lines.append(f"Key: '{k}' (\"{eng_text}\")")
                report_lines.append(f"  - {missing_str}")
                report_lines.append("")

        if translation_log:
            report_lines.append("=" * 60)
            report_lines.append("DETAILLIERTE AUFLISTUNG DER ÜBERSETZTEN WERTE")
            report_lines.append("=" * 60)
            report_lines.append("")

            for folder_name, data in sorted(translation_log.items()):
                trans_dict = data['translations']
                if not trans_dict:
                    continue
                lang_code = data['lang']
                if data.get('is_mirror'):
                    report_lines.append(f"[{folder_name}] (gespiegelt von {data['mirror_src']}) - {len(trans_dict)} neue Strings:")
                else:
                    report_lines.append(f"[{folder_name}] ({lang_code}) - {len(trans_dict)} neue Strings:")

                for k, val in sorted(trans_dict.items()):
                    report_lines.append(f"  {k}: {val}")
                report_lines.append("")

        report_content = "\n".join(report_lines)
        script_dir = os.path.dirname(os.path.abspath(__file__))
        report_file_path = os.path.join(script_dir, f"translations_{timestamp_file_str}.txt")

        try:
            with open(report_file_path, "w", encoding="utf-8") as f:
                f.write(report_content)
            print(f"Protokolldatei erfolgreich erstellt:")
            print(f"{report_file_path}\n", flush=True)
        except Exception as e:
            print(f"Fehler beim Erstellen der Protokolldatei: {e}", flush=True)

if __name__ == "__main__":
    main()