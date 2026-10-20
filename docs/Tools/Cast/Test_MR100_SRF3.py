import sys
import time
from uuid import UUID
import pychromecast

ip = "192.168.1.139"
stream_url = "http://stream.srg-ssr.ch/m/drs3/mp3_128"

print(f"Verbinde direkt mit Chromecast unter {ip} (ohne mDNS-Suche)...")

try:
    # Direkter Verbindungsaufbau per IP und Port 8009
    host_info = (ip, 8009, UUID("00000000-0000-0000-0000-000000000000"), "Chromecast", "Chromecast Speaker")
    
    cast = pychromecast.get_chromecast_from_host(host_info)
    cast.wait()
    
    print("Verbunden mit Gerät!")

    class StatusLogger:
        def new_media_status(self, status):
            print(f"\n[CHROMECAST STATUS-UPDATE]")
            print(f"  - Player State : {status.player_state}")
            print(f"  - Content ID   : {status.content_id}")
            print(f"  - Idle Reason  : {status.idle_reason}")
            print(f"  - Roher Status : {status}")

    cast.media_controller.register_status_listener(StatusLogger())

    print(f"\nSende Stream: {stream_url}")
    cast.media_controller.play_media(
        stream_url,
        content_type="audio/mpeg",
        title="SRF 3",
        stream_type="BUFFERED",
        autoplay=True
    )

    print("Lausche auf Live-Nachrichten vom Chromecast (Abbruch mit Strg+C)...")
    while True:
        time.sleep(1)

except KeyboardInterrupt:
    print("\nSkript beendet.")
    sys.exit(0)
except Exception as e:
    print(f"Fehler: {e}")
    sys.exit(1)