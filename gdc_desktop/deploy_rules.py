import os
import json
import requests
from google.oauth2 import service_account
from google.auth.transport.requests import Request

# Load service account
creds = service_account.Credentials.from_service_account_file(
    "serviceAccountKey.json",
    scopes=["https://www.googleapis.com/auth/cloud-platform"]
)

# Get token
creds.refresh(Request())
token = creds.token

# Load rules
with open("../firestore.rules", "r") as f:
    rules_content = f.read()

project_id = "nexlib-e7970"
headers = {
    "Authorization": f"Bearer {token}",
    "Content-Type": "application/json"
}

# 1. Create a ruleset
create_url = f"https://firebaserules.googleapis.com/v1/projects/{project_id}/rulesets"
payload = {
    "source": {
        "files": [
            {
                "name": "firestore.rules",
                "content": rules_content
            }
        ]
    }
}

print("Creating ruleset...")
resp = requests.post(create_url, headers=headers, json=payload)
if resp.status_code != 200:
    print("Error creating ruleset:", resp.text)
    exit(1)

ruleset_name = resp.json()["name"]
print("Created ruleset:", ruleset_name)

# 2. Update the release for Firestore
release_url = f"https://firebaserules.googleapis.com/v1/projects/{project_id}/releases/cloud.firestore"
release_payload = {
    "release": {
        "name": f"projects/{project_id}/releases/cloud.firestore",
        "rulesetName": ruleset_name
    }
}

print("Updating release...")
resp = requests.patch(release_url, headers=headers, json=release_payload)
if resp.status_code != 200:
    print("Error updating release:", resp.text)
    exit(1)

print("Deploy complete! Rules updated successfully.")
