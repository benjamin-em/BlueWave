from zeroconf import ServiceInfo, Zeroconf
import time
import socket

# Exakte IP deines PCs übergeben
local_ip = "192.168.1.140"

desc = {
    'fn': 'Test-Cast-Receiver', 
    'md': 'Chromecast', 
    've': '05', 
    'rs': ''
}

info = ServiceInfo(
    "_googlecast._tcp.local.",
    "TestCastDevice._googlecast._tcp.local.",
    addresses=[socket.inet_aton(local_ip)],
    port=8009,
    properties=desc,
    server="testcast.local.",
)

# Zeroconf an die spezifische Netzwerkkarte binden
zeroconf = Zeroconf(interfaces=[local_ip])
zeroconf.register_service(info)

print(f"Fake Chromecast mDNS running on {local_ip}... Press Ctrl+C to exit.")
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    zeroconf.unregister_service(info)
    zeroconf.close()