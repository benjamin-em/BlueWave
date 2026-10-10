import datetime
import decimal
import gzip
import io
import json
import os
import time
from pathlib import Path
import pymysql
import pymysql.cursors

DB_HOST = os.environ.get("DB_HOST", "")
DB_USER = os.environ.get("DB_USER", "")
DB_PASSWORD = os.environ.get("DB_PASSWORD", "")
DB_NAME = os.environ.get("DB_NAME", "")
INTERVAL_SECONDS = int(os.environ.get("INTERVAL_SECONDS", "1800"))

EXPORT_DIR = Path(os.environ.get("EXPORT_DIR", "/export"))
OUTPUT_GZ = EXPORT_DIR / "stations.json.gz"
TEMP_GZ = EXPORT_DIR / "stations.json.gz.tmp"

SQL_QUERY = """
SELECT 
    ChangeUuid,
    StationUuid,
    ServerUuid,
    Name,
    Url,
    UrlCache,
    Homepage,
    Favicon,
    Tags,
    Country,
    CountryCode,
    CountrySubdivisionCode,
    Subcountry,
    Language,
    LanguageCodes,
    Votes,
    Creation,
    Codec,
    Bitrate,
    Hls,
    LastCheckOK,
    LastCheckTime,
    LastCheckOkTime,
    LastLocalCheckTime,
    ClickTimestamp,
    clickcount,
    ClickTrend,
    SslError,
    GeoLat,
    GeoLong,
    ExtendedInfo
FROM Station
WHERE LastCheckOK = 1
"""


def parse_datetime(dt):
    if not dt:
        return "", None
    if isinstance(dt, (datetime.datetime, datetime.date)):
        return dt.strftime("%Y-%m-%d %H:%M:%S"), dt.strftime("%Y-%m-%dT%H:%M:%SZ")
    return str(dt), None


def map_station(row):
    creation_std, creation_iso = parse_datetime(row.get("Creation"))
    click_std, click_iso = parse_datetime(row.get("ClickTimestamp"))
    check_std, check_iso = parse_datetime(row.get("LastCheckTime"))
    checkok_std, checkok_iso = parse_datetime(row.get("LastCheckOkTime"))
    local_std, local_iso = parse_datetime(row.get("LastLocalCheckTime"))

    lat = row.get("GeoLat")
    if isinstance(lat, (decimal.Decimal, float)):
        lat = float(lat)

    lon = row.get("GeoLong")
    if isinstance(lon, (decimal.Decimal, float)):
        lon = float(lon)

    return {
        "changeuuid": row.get("ChangeUuid") or "",
        "stationuuid": row.get("StationUuid") or "",
        "serveruuid": row.get("ServerUuid"),
        "name": row.get("Name") or "",
        "url": row.get("Url") or "",
        "url_resolved": row.get("UrlCache") or "",
        "homepage": row.get("Homepage") or "",
        "favicon": row.get("Favicon") or "",
        "tags": row.get("Tags") or "",
        "country": row.get("Country") or "",
        "countrycode": row.get("CountryCode") or "",
        "iso_3166_2": row.get("CountrySubdivisionCode") or "",
        "state": row.get("Subcountry") or "",
        "language": row.get("Language") or "",
        "languagecodes": row.get("LanguageCodes") or "",
        "votes": int(row.get("Votes") or 0),
        "lastchangetime": creation_std,
        "lastchangetime_iso8601": creation_iso,
        "codec": row.get("Codec") or "",
        "bitrate": int(row.get("Bitrate") or 0),
        "hls": int(row.get("Hls") or 0),
        "lastcheckok": int(row.get("LastCheckOK") or 0),
        "lastchecktime": check_std,
        "lastchecktime_iso8601": check_iso,
        "lastcheckoktime": checkok_std,
        "lastcheckoktime_iso8601": checkok_iso,
        "lastlocalchecktime": local_std,
        "lastlocalchecktime_iso8601": local_iso,
        "clicktimestamp": click_std,
        "clicktimestamp_iso8601": click_iso,
        "clickcount": int(row.get("clickcount") or 0),
        "clicktrend": int(row.get("ClickTrend") or 0),
        "ssl_error": int(row.get("SslError") or 0),
        "geo_lat": lat,
        "geo_long": lon,
        "geo_distance": None,
        "has_extended_info": bool(row.get("ExtendedInfo", False)),
    }


def execute_export():
    EXPORT_DIR.mkdir(parents=True, exist_ok=True)
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    print(f"[{timestamp}] Starte Export aus {DB_NAME} auf {DB_HOST}...", flush=True)

    try:
        connection = pymysql.connect(
            host=DB_HOST,
            user=DB_USER,
            password=DB_PASSWORD,
            database=DB_NAME,
            cursorclass=pymysql.cursors.SSDictCursor,
            charset="utf8mb4",
        )

        record_count = 0
        with connection.cursor() as cursor:
            cursor.execute(SQL_QUERY)

            fd = os.open(str(TEMP_GZ), os.O_WRONLY | os.O_CREAT | os.O_TRUNC)
            with io.FileIO(fd, mode="wb") as raw_file:
                with gzip.GzipFile(filename="stations.json", mode="wb", fileobj=raw_file, mtime=0.0) as gz_out:
                    with io.TextIOWrapper(gz_out, encoding="utf-8") as writer:
                        writer.write("[")
                        is_first = True

                        while True:
                            row = cursor.fetchone()
                            if row is None:
                                break

                            station_data = map_station(row)
                            serialized = json.dumps(station_data, ensure_ascii=False)

                            if not is_first:
                                writer.write(",")
                            else:
                                is_first = False

                            writer.write(serialized)
                            record_count += 1

                        writer.write("]")

        connection.close()

        os.replace(str(TEMP_GZ), str(OUTPUT_GZ))
        file_size_mb = OUTPUT_GZ.stat().st_size / (1024 * 1024)
        print(f"Export erfolgreich: {record_count} Stationen | Dateigroesse: {file_size_mb:.2f} MB", flush=True)

    except Exception as err:
        if TEMP_GZ.exists():
            try:
                TEMP_GZ.unlink()
            except OSError:
                pass
        print(f"Fehler beim Exportieren: {err}", flush=True)


if __name__ == "__main__":
    execute_export()
    while True:
        time.sleep(INTERVAL_SECONDS)
        execute_export()
