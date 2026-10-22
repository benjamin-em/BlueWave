import requests

url = "http://strm112.1.fm/deeptech_mobile_mp3"
response = requests.get(url, allow_redirects=True, stream=True, timeout=10)

print(f"Finaler Endpunkt: {response.url}\n")
print("Redirect-Kette:")
for resp in response.history:
    print(f"[{resp.status_code}] {resp.url}")
print(f"[{response.status_code}] {response.url} (Ziel)")