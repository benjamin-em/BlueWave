import logging
import time
import pychromecast

# Explizites Logging-Format für Socket- und Nachrichtenverkehr aktivieren
logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s"
)
logging.getLogger("pychromecast.socket_client").setLevel(logging.DEBUG)

CHROMECAST_IP = "192.168.1.139"
STREAM_URL = "http://strm112.1.fm/deeptech_mobile_mp3"

print(f"Verbinde mit Chromecast unter {CHROMECAST_IP}...")
chromecasts, browser = pychromecast.get_chromecasts(known_hosts=[CHROMECAST_IP])

if not chromecasts:
    print(f"Kein Chromecast unter {CHROMECAST_IP} gefunden.")
    exit(1)

chromecast = chromecasts[0]
chromecast.wait()

print(f"Verbunden: {chromecast.name}")
mc = chromecast.media_controller

mc.play_media(
    url=STREAM_URL,
    content_type="audio/mpeg",
    stream_type="LIVE",
    title="AMARadio Live",
    metadata={
        "metadataType": 0,
        "title": "AMARadio Live"
    }
)

mc.block_until_active()
print(f"Player-Status: {mc.status}")

time.sleep(10)
pychromecast.discovery.stop_discovery(browser)