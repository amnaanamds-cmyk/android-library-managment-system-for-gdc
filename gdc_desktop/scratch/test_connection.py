import sys
import os
from pathlib import Path

# Add parent directory to sys.path so we can import services
sys.path.append(str(Path(__file__).parent.parent))

from services.firebase_service import FirebaseService

def main():
    print("Initializing FirebaseService...")
    fb = FirebaseService()
    print(f"Mock Mode: {fb.mock_mode}")
    if fb.mock_mode:
        print("FAIL: Firebase initialized in Mock Mode!")
        sys.exit(1)
        
    print("SUCCESS: Connected to Firestore! Testing database write/read...")
    # Test read/write under institutions/_test_connection_
    test_ref = fb.db.collection("institutions").document("_test_connection_")
    test_ref.set({
        "status": "connected",
        "timestamp": firestore.client().field_path() # We'll use server timestamp
    })
    
    # Read back
    doc = test_ref.get()
    print(f"Read back document data: {doc.to_dict()}")
    
    # Delete test doc
    test_ref.delete()
    print("Write, read, and delete test successful!")

if __name__ == "__main__":
    # Import firestore package locally for helper
    from firebase_admin import firestore
    main()
