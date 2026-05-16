import requests
import json
import os
from datetime import datetime

# --- CONFIGURATION ---
API_ENDPOINT = "http://4.224.186.213/evaluation-service/notifications"
AUTH_URL = "http://4.224.186.213/evaluation-service/auth"

def load_config():
    """
    Loads configuration from the vehicle_maintenance_scheduler config.properties file.
    """
    config_path = r"c:\Users\allur\Desktop\22MIS7041\vehicle_maintence_scheduler\src\main\resources\config.properties"
    config = {}
    try:
        if os.path.exists(config_path):
            with open(config_path, 'r') as f:
                for line in f:
                    if '=' in line and not line.startswith('#'):
                        name, value = line.split('=', 1)
                        config[name.strip()] = value.strip()
        else:
            print(f"Warning: Config file not found at {config_path}")
    except Exception as e:
        print(f"Error loading config file: {e}")
    return config

def get_auth_token(config):
    """
    Authenticates with the service to retrieve a fresh JWT using loaded config.
    """
    auth_payload = {
        "companyName": "Afford Medical Technologies Private Limited",
        "clientID": config.get("client_id"),
        "clientSecret": config.get("client_secret"),
        "ownerName": config.get("name", "alluri deepak srivachasa aneeswar"),
        "ownerEmail": config.get("email", "aneeswar11@gmail.com"),
        "rollNo": config.get("roll_no"),
        "email": config.get("email"),
        "name": config.get("name"),
        "accessCode": config.get("access_code")
    }
    
    try:
        response = requests.post(AUTH_URL, json=auth_payload, timeout=10)
        if response.status_code in [200, 201]:
            return response.json().get('access_token')
        else:
            print(f"Auth Failed: {response.status_code} - {response.text}")
            return None
    except Exception as e:
        print(f"Auth Connection Error: {e}")
        return None

def fetch_and_prioritize():
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Loading Configuration...")
    config = load_config()
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Authenticating...")
    token = get_auth_token(config)
    
    if not token:
        print("Aborting: Could not obtain authorization token.")
        return

    headers = {
        "Accept": "application/json",
        "Authorization": f"Bearer {token}"
    }
    
    print(f"[{datetime.now().strftime('%H:%M:%S')}] Connecting to Notification Service...")
    
    try:
        # 1. Fetch live incoming notification data
        response = requests.get(API_ENDPOINT, headers=headers, timeout=10)
        
        if response.status_code != 200:
            print(f"Error: Received {response.status_code} from service.")
            return

        notifications = response.json()
        
        if not notifications or not isinstance(notifications, list):
            print("Notice: No new notifications found in the stream.")
            return

        # 2. Process and Sort
        # Rule 1: Priority Weight (Placement > Result > Event)
        # Rule 2: Recency (Most recent first)
        
        def calculate_sort_key(item):
            # We negate the values because Python's sort is ascending by default, 
            # and we want highest priority/latest time at the top.
            p_weight = TYPE_PRIORITY.get(item.get('Type'), 0)
            timestamp = parse_timestamp(item.get('Timestamp', ''))
            
            # Key: (Priority Weight, Timestamp)
            return (p_weight, timestamp)

        # Apply sort: Highest weight first, then latest timestamp first
        sorted_notifications = sorted(notifications, key=calculate_sort_key, reverse=True)

        # 3. Extract Top 10
        top_10 = sorted_notifications[:10]

        print("\n" + "="*60)
        print(f" TOP {len(top_10)} PRIORITY NOTIFICATIONS (Live Stream) ")
        print("="*60)
        
        for idx, notify in enumerate(top_10, 1):
            n_type = notify.get('Type', 'Unknown')
            msg = notify.get('Message', 'N/A')
            ts = notify.get('Timestamp', 'Unknown')
            
            # Clean output formatting
            print(f"{idx}. [{n_type.upper()}] | {ts}")
            print(f"   Message: {msg}")
            print("-" * 40)

    except requests.exceptions.RequestException as e:
        print(f"Connection Error: Unable to reach the notification service. {e}")
    except ValueError as e:
        print(f"Data Error: Received malformed JSON from service. {e}")

if __name__ == "__main__":
    fetch_and_prioritize()
