import urllib.request
import json

url = "https://sonarcloud.io/api/hotspots/search?project=carlos-emr_carlos&pullRequest=1962"
try:
    req = urllib.request.Request(url)
    with urllib.request.urlopen(req) as response:
        data = json.loads(response.read().decode())
        for hotspot in data.get("hotspots", []):
            print(f"Hotspot in {hotspot['component']}: Line {hotspot['line']}")
            print(f"Message: {hotspot['message']}")
            print(f"Rule: {hotspot['ruleKey']}")
except Exception as e:
    print(f"Error fetching hotspots: {e}")
