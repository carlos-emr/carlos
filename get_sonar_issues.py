import urllib.request
import json
import sys

url = "https://sonarcloud.io/api/hotspots/search?projectKey=carlos-emr_carlos&pullRequest=1962"
try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        for hotspot in data.get("hotspots", []):
            print(f"Hotspot in {hotspot.get('component')}: Line {hotspot.get('line')}")
            print(f"Message: {hotspot.get('message')}")
            print(f"Rule: {hotspot.get('ruleKey')}")
except Exception as e:
    print(f"Error fetching hotspots: {e}")
