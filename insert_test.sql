INSERT INTO folders (system_name, user_name)
VALUES ('folder_1','Тест WI-FI');


CREATE TABLE folder_1 (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    device_mac TEXT NOT NULL,
    latitude REAL,
    longitude REAL,
    signal_strength INTEGER,
    network_type TEXT CHECK(network_type IN ('Wi-Fi','Bluetooth','GSM')),
    ignore_status BOOLEAN DEFAULT 0,
    alert_status BOOLEAN DEFAULT 0,
    detected_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
    );


INSERT INTO folder_1 (device_mac, latitude, longitude, signal_strength, network_type, ignore_status, alert_status)
VALUES ('00:14:22:01:23:45', 37.7749, -122.4194, -50, 'Wi-Fi');