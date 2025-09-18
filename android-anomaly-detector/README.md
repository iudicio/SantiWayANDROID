## Идея
По потоку «обнаруженных устройств» (MAC, lat, lon, time, accuracy, класс сети) детектор:
1) ведёт историю по каждому MAC;
2) считает расстояния и скорости;
3) определяет тип движения по медиане скорости с гистерезисом;
4) выдаёт **один alert на сессию**, когда пройдён порог дистанции.

Правила по умолчанию (можно менять в коде):
- **скорость < ~20 км/ч → WALKING**, иначе **DRIVING** (гистерезис low/high);
- **WALKING:** alert при сумме дистанции **> 1 км**;
- **DRIVING:** alert при сумме дистанции **> 10 км**;
- отбрасываем: расстояния **< 10 м**, точки с плохой точностью, нереалистичные скорости.

## Файлы
- `utils/LocationUtils.kt` — Haversine‑дистанция, скорость, нормализация долготы, валидация координат.
- `utils/Extensions.kt` — форматтеры чисел/времени.
- `model/DetectedDevice.kt` — входное наблюдение (MAC + `LocationPoint`).
- `model/LocationPoint.kt` — lat/lon/time/accuracy.
- `model/SessionState.kt` — состояние по MAC (последняя точка, накопленная дистанция, окно скоростей, тип движения, флаг alert, тайминги).
- `model/MovementType.kt` — `WALKING | DRIVING | UNKNOWN`.
- `model/AnomalyResult.kt` — результат обработки (alert/message/метаданные).
- `data/DeviceHistoryRepository.kt` — in‑memory хранилище/обновление состояний.
- `domain/MovementAnomalyDetector.kt` — основная логика (фильтры, расчёты, гистерезис, пороги, алерты).

## Как работает
1. Валидация координат и точности.
2. Дистанция (Haversine). Движение < 10 м не суммируем.
3. Скорость = `distance / Δt` → окно скоростей → **медиана**.
4. Определение `MovementType` по гистерезису.
5. Накопление дистанции сессии и **однократный alert** при превышении порога.
6. Долгий простой `idleResetMs` → новая сессия.

## Быстрый старт
```kotlin
val repo = DeviceHistoryRepository()
val detector = MovementAnomalyDetector(repo)

fun onScan(device: DetectedDevice) {
    detector.processDetectedDevice(device)?.let { res ->
        if (res.isAnomalous) Log.i("ANOMALY", res.message)
    }
}
```

### Реплей из CSV (оффлайн‑тест)
Формат: `mac,lat,lon,tsMs,accuracyM`
```
AA:BB:CC:DD:EE:FF,55.7512,37.6184,1726500000000,8.5
```
```kotlin
fun replayCsv(ctx: Context, assetName: String) {
    ctx.assets.open(assetName).bufferedReader().useLines { lines ->
        lines.drop(1).forEach { line ->
            val (mac, lat, lon, t, acc) = line.split(',')
            onScan(DetectedDevice(mac, LocationPoint(lat.toDouble(), lon.toDouble(), t.toLong(), acc.toDouble())))
        }
    }
}
```

## Интеграция
- Источник данных любой: скан Wi‑Fi/BLE/Cell → соберите `DetectedDevice` и передайте в `onObservation()`.
- Разрешения (Android 12+): `ACCESS_FINE_LOCATION`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (+ runtime).

## Тонкая настройка
В `MovementAnomalyDetector`:
- `minDistanceFilterMeters` (по умолчанию 10 м),
- `maxAccuracyMeters`, `maxSpeedKmh`,
- `walkingSpeedThresholdLow/High` (гистерезис ≈ 20 км/ч),
- `walkDistanceKm = 1`, `driveDistanceKm = 10`,
- `idleResetMs` (сброс по простоям).


