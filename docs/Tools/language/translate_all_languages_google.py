import os
import re
import glob
import time
import urllib.request
import urllib.parse
import urllib.error
import json
import xml.etree.ElementTree as ET

RES_DIR = r"C:/Users/bou/StudioProjects/AMARadio/app/src/main/res"

REQUEST_DELAY = 0.35

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
    "URL"
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
        time.sleep(REQUEST_DELAY)
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

def process_language(lang_dir, master_keys):
    global RATE_LIMIT_HIT
    if RATE_LIMIT_HIT:
        return

    folder_name = os.path.basename(lang_dir)
    file_path = os.path.join(lang_dir, "strings.xml")
    if not os.path.exists(file_path):
        return

    target_lang = FOLDER_TO_GOOGLE_LANG.get(folder_name)
    if not target_lang:
        return

    existing_keys = parse_string_keys(file_path)

    missing_keys = []
    replace_keys = []

    for k, eng_text in master_keys.items():
        if k not in existing_keys:
            missing_keys.append(k)
        elif existing_keys[k].strip() == eng_text.strip() and eng_text.strip() not in PROTECTED_VALUES:
            replace_keys.append(k)

    if not missing_keys and not replace_keys:
        return

    print(f"Processing {folder_name} ({target_lang}) - {len(missing_keys)} missing, {len(replace_keys)} untranslated English...", flush=True)

    with open(file_path, "r", encoding="utf-8") as f:
        content = f.read()

    replaced_count = 0
    for k in replace_keys:
        if RATE_LIMIT_HIT:
            return
        eng_text = master_keys[k]
        if eng_text.startswith("@string/"):
            continue

        translated = translate_google(eng_text, target_lang)
        if translated:
            xml_val = format_xml_value(translated)
            pattern = re.compile(rf'(<string\s+name="{re.escape(k)}"[^>]*>)(.*?)(</string>)', re.DOTALL)
            content = pattern.sub(rf'\g<1>{xml_val}\g<3>', content)
            replaced_count += 1

    new_lines = []
    for k in missing_keys:
        if RATE_LIMIT_HIT:
            return
        eng_text = master_keys[k]
        if eng_text.startswith("@string/"):
            translated = eng_text
        else:
            translated = translate_google(eng_text, target_lang)

        if translated:
            xml_val = format_xml_value(translated)
            new_lines.append(f'    <string name="{k}">{xml_val}</string>')

    if new_lines:
        last_index = content.rfind("</resources>")
        if last_index != -1:
            insert_block = "\n" + "\n".join(new_lines) + "\n"
            content = content[:last_index] + insert_block + content[last_index:]

    if (replaced_count > 0 or new_lines) and not RATE_LIMIT_HIT:
        with open(file_path, "w", encoding="utf-8") as f:
            f.write(content)
        print(f"--> Finished {folder_name}! Updated: {replaced_count}, Added: {len(new_lines)}", flush=True)

def main():
    default_path = os.path.join(RES_DIR, "values", "strings.xml")
    master_keys = parse_string_keys(default_path)
    print(f"Master translatable keys: {len(master_keys)}", flush=True)

    all_lang_dirs = sorted(glob.glob(os.path.join(RES_DIR, "values-*")))
    lang_dirs = [d for d in all_lang_dirs if os.path.basename(d) in FOLDER_TO_GOOGLE_LANG]

    for lang_dir in lang_dirs:
        if RATE_LIMIT_HIT:
            print("\n[Abbruch] Vorgang abgebrochen wegen Rate-Limit (HTTP 429).")
            break
        process_language(lang_dir, master_keys)

    if not RATE_LIMIT_HIT:
        print("ALL LANGUAGES COMPLETED SUCCESSFULLY!", flush=True)

if __name__ == "__main__":
    main()
