from zeroconf import ServiceInfo, Zeroconf
import time
import socket

desc = {
    'fn': 'Test-Cast-Receiver', 
    'md': 'Chromecast', 
    've': '05', 
    'rs': ''
}

info = ServiceInfo(
    "_googlecast._tcp.local.",
    "TestCastDevice._googlecast._tcp.local.",
    addresses=[socket.inet_aton("192.168.1.140")],
    port=8009,
    properties=desc,
    server="testcast.local.",
)

zeroconf = Zeroconf()
zeroconf.register_service(info)
print("Fake Chromecast mDNS service running... Press Ctrl+C to exit.")
try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    zeroconf.unregister_service(info)
    zeroconf.close()