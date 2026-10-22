import requests

url = "https://webradio1.tda.dz:8001/Tlemcen_64K.mp3"
headers = {
    "Origin": "https://www.gstatic.com",
    "User-Agent": "Mozilla/5.0 (Chromecast; Linux armv7l) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/85.0.4183.127 Safari/537.36"
}

try:
    response = requests.get(url, headers=headers, stream=True, timeout=5)
    print(f"Status Code: {response.status_code}")
    print("\nResponse Headers:")
    for key, value in response.headers.items():
        print(f"{key}: {value}")
    response.close()
except Exception as e:
    print(f"Fehler: {e}")