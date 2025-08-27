import sqlite3

DB_NAME = "test.db"

def init_db():
    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()
    cur.execute("""
    CREATE TABLE IF NOT EXISTS folders (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            system_name TEXT UNIQUE NOT NULL,
            user_name TEXT NOT NULL,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """)
    conn.commit()
    conn.close()

def add_device(folder_name, device_mac, latitude, longitude, signal_strength, network_type):
    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()

    cur.execute(f"""
        INSERT INTO {folder_name} 
        (device_mac, latitude, longitude, signal_strength, network_type)
        VALUES (?, ?, ?, ?, ?)
    """, (device_mac, latitude, longitude, signal_strength, network_type))

    conn.commit()
    conn.close()
    print(f"Устройство {device_mac} добавлено в {folder_name}")

def create_folder(user_name):
    conn = sqlite3.connect(DB_NAME)
    cur = conn.cursor()

    cur.execute("SELECT COUNT(*) FROM folders")
    folder_count = cur.fetchone()[0] + 1
    system_name = f"folder_{folder_count}"


    cur.execute("INSERT INTO folders (system_name, user_name) VALUES (?, ?)", (system_name, user_name))


    cur.execute(f"""
    CREATE TABLE IF NOT EXISTS {system_name} (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        device_mac TEXT NOT NULL,
        latitude REAL,
        longitude REAL,
        signal_strength INTEGER,
        network_type TEXT CHECK(network_type IN ('Wi-Fi','Bluetooth','GSM')),
        ignore_status BOOLEAN DEFAULT 0,
        alert_status BOOLEAN DEFAULT 0,
        detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
        """)
    conn.commit()
    conn.close()
    print(f"Папка '{user_name}' создана → таблица {system_name}")


if __name__ == "__main__":
    init_db()
    create_folder("Работа Wi-Fi")
    create_folder("Домашний Bluetooth")

    add_device  ("folder_1", "AA:BB:CC:DD:EE:FF", 37.7749, -122.4194, -50, "Wi-Fi")
    add_device("folder_2", "11:22:33:44:55:66", 34.0522, -118.2437, -60, "Bluetooth")