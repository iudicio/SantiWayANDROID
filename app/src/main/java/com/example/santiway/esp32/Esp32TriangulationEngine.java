package com.example.santiway.esp32;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Esp32TriangulationEngine {
    public static final long FRESH_ANCHOR_MS = 5L * 60L * 1000L;
    public static final long OBSERVATION_LOOKBACK_MS = 60L * 1000L;
    public static final long TRIANGULATION_TIME_WINDOW_MS = 15L * 1000L;

    private static final double METERS_PER_LATITUDE = 110540.0;
    private static final double METERS_PER_LONGITUDE = 111320.0;

    private Esp32TriangulationEngine() {
    }

    public static final class Sample {
        public final String sourceAddress;
        public final double latitude;
        public final double longitude;
        public final double altitude;
        public final int rssi;
        public final long observedAt;
        public final String details;
        public final double distanceMeters;

        public Sample(String sourceAddress,
                      double latitude,
                      double longitude,
                      double altitude,
                      int rssi,
                      long observedAt,
                      String details) {
            this.sourceAddress = sourceAddress == null ? "" : sourceAddress.trim().toUpperCase(Locale.US);
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.rssi = rssi;
            this.observedAt = observedAt;
            this.details = details == null ? "" : details;
            this.distanceMeters = rssiToMeters(rssi);
        }
    }

    public static final class Position {
        public final double latitude;
        public final double longitude;
        public final double altitude;
        public final double accuracy;
        public final int anchorCount;
        public final double rssiAverage;
        public final String name;
        public final long lastSeen;

        public Position(double latitude,
                        double longitude,
                        double altitude,
                        double accuracy,
                        int anchorCount,
                        double rssiAverage,
                        String name,
                        long lastSeen) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.altitude = altitude;
            this.accuracy = accuracy;
            this.anchorCount = anchorCount;
            this.rssiAverage = rssiAverage;
            this.name = name == null ? "" : name;
            this.lastSeen = lastSeen;
        }
    }

    public static Position estimate(List<Sample> rawSamples) {
        if (rawSamples == null || rawSamples.isEmpty()) {
            return null;
        }

        List<Sample> samples = sanitize(rawSamples);

        if (samples.size() < 3) {
            return null;
        }

        List<Sample> coherent = selectCoherentWindow(samples);

        if (coherent.size() < 3) {
            return null;
        }

        Position multilaterated = estimateByMultilateration(coherent);

        if (multilaterated != null) {
            return multilaterated;
        }

        return estimateByWeightedCentroid(coherent);
    }

    public static double rssiToMeters(int rssi) {
        if (rssi == 0 || rssi <= -120) {
            return 80.0;
        }

        double txPower = -59.0;
        double pathLoss = 2.35;

        double distance = Math.pow(10.0, (txPower - rssi) / (10.0 * pathLoss));

        if (Double.isNaN(distance) || Double.isInfinite(distance)) {
            return 80.0;
        }

        return clamp(distance, 0.5, 120.0);
    }

    private static List<Sample> sanitize(List<Sample> raw) {
        List<Sample> result = new ArrayList<>();

        for (Sample sample : raw) {
            if (sample == null) continue;
            if (sample.sourceAddress.isEmpty()) continue;
            if (!validCoordinate(sample.latitude, sample.longitude)) continue;
            if (sample.observedAt <= 0) continue;

            result.add(sample);
        }

        result.sort((a, b) -> Long.compare(b.observedAt, a.observedAt));

        List<Sample> uniqueBySource = new ArrayList<>();
        Set<String> used = new HashSet<>();

        for (Sample sample : result) {
            if (used.add(sample.sourceAddress)) {
                uniqueBySource.add(sample);
            }
        }

        return uniqueBySource;
    }

    private static List<Sample> selectCoherentWindow(List<Sample> samples) {
        List<Sample> best = new ArrayList<>();

        for (Sample anchor : samples) {
            long end = anchor.observedAt;
            long start = end - TRIANGULATION_TIME_WINDOW_MS;

            List<Sample> window = new ArrayList<>();
            Set<String> used = new HashSet<>();

            for (Sample sample : samples) {
                if (sample.observedAt < start || sample.observedAt > end) continue;
                if (used.add(sample.sourceAddress)) {
                    window.add(sample);
                }
            }

            if (window.size() > best.size()) {
                best = window;
            }

            if (best.size() >= 4) {
                break;
            }
        }

        return best;
    }

    private static Position estimateByWeightedCentroid(List<Sample> samples) {
        double weightSum = 0.0;
        double lat = 0.0;
        double lon = 0.0;
        double alt = 0.0;
        double rssiSum = 0.0;
        long lastSeen = 0L;
        String name = "";

        for (Sample sample : samples) {
            double weight = weightFor(sample);

            weightSum += weight;
            lat += sample.latitude * weight;
            lon += sample.longitude * weight;
            alt += sample.altitude * weight;
            rssiSum += sample.rssi;
            lastSeen = Math.max(lastSeen, sample.observedAt);

            if (name.isEmpty() && sample.details != null && !sample.details.trim().isEmpty()) {
                name = sample.details.trim();
            }
        }

        if (weightSum <= 0.0) return null;

        double latitude = lat / weightSum;
        double longitude = lon / weightSum;
        double altitude = alt / weightSum;
        double accuracy = estimateAccuracy(samples, latitude, longitude);

        return new Position(
                latitude,
                longitude,
                altitude,
                accuracy,
                samples.size(),
                rssiSum / samples.size(),
                name,
                lastSeen
        );
    }

    private static Position estimateByMultilateration(List<Sample> samples) {
        if (samples.size() < 3) return null;

        Sample base = samples.get(0);
        double lat0 = base.latitude;
        double lon0 = base.longitude;
        double cosLat = Math.max(0.01, Math.abs(Math.cos(Math.toRadians(lat0))));

        int n = samples.size();

        double[][] p = new double[n][2];
        double[] d = new double[n];

        for (int i = 0; i < n; i++) {
            Sample s = samples.get(i);
            p[i][0] = (s.longitude - lon0) * METERS_PER_LONGITUDE * cosLat;
            p[i][1] = (s.latitude - lat0) * METERS_PER_LATITUDE;
            d[i] = s.distanceMeters;
        }

        double[][] normal = new double[2][2];
        double[] rhs = new double[2];

        double r0 = d[0];

        for (int i = 1; i < n; i++) {
            double a = 2.0 * (p[i][0] - p[0][0]);
            double b = 2.0 * (p[i][1] - p[0][1]);
            double c = r0 * r0 - d[i] * d[i]
                    + p[i][0] * p[i][0] - p[0][0] * p[0][0]
                    + p[i][1] * p[i][1] - p[0][1] * p[0][1];

            double weight = 1.0 / Math.max(1.0, d[i]);

            normal[0][0] += weight * a * a;
            normal[0][1] += weight * a * b;
            normal[1][0] += weight * b * a;
            normal[1][1] += weight * b * b;

            rhs[0] += weight * a * c;
            rhs[1] += weight * b * c;
        }

        double[] xy = solve2x2(normal, rhs);

        if (xy == null) {
            return null;
        }

        if (Math.abs(xy[0]) > 50000 || Math.abs(xy[1]) > 50000) {
            return null;
        }

        double latitude = lat0 + xy[1] / METERS_PER_LATITUDE;
        double longitude = lon0 + xy[0] / (METERS_PER_LONGITUDE * cosLat);
        double altitude = weightedAltitude(samples);
        double accuracy = estimateAccuracy(samples, latitude, longitude);

        if (!validCoordinate(latitude, longitude)) {
            return null;
        }

        double rssiSum = 0.0;
        long lastSeen = 0L;
        String name = "";

        for (Sample sample : samples) {
            rssiSum += sample.rssi;
            lastSeen = Math.max(lastSeen, sample.observedAt);

            if (name.isEmpty() && sample.details != null && !sample.details.trim().isEmpty()) {
                name = sample.details.trim();
            }
        }

        return new Position(
                latitude,
                longitude,
                altitude,
                accuracy,
                samples.size(),
                rssiSum / samples.size(),
                name,
                lastSeen
        );
    }

    private static double[] solve2x2(double[][] a, double[] b) {
        double det = a[0][0] * a[1][1] - a[0][1] * a[1][0];

        if (Math.abs(det) < 0.000001) {
            return null;
        }

        double x = (b[0] * a[1][1] - a[0][1] * b[1]) / det;
        double y = (a[0][0] * b[1] - b[0] * a[1][0]) / det;

        if (Double.isNaN(x) || Double.isNaN(y)
                || Double.isInfinite(x) || Double.isInfinite(y)) {
            return null;
        }

        return new double[]{x, y};
    }

    private static double weightedAltitude(List<Sample> samples) {
        double sum = 0.0;
        double weightSum = 0.0;

        for (Sample sample : samples) {
            double weight = weightFor(sample);
            sum += sample.altitude * weight;
            weightSum += weight;
        }

        return weightSum <= 0.0 ? 0.0 : sum / weightSum;
    }

    private static double weightFor(Sample sample) {
        double byDistance = 1.0 / Math.max(1.0, sample.distanceMeters);
        double bySignal = Math.max(0.1, (120.0 + sample.rssi) / 120.0);

        return byDistance * bySignal;
    }

    private static double estimateAccuracy(List<Sample> samples, double latitude, double longitude) {
        if (samples == null || samples.isEmpty()) return 0.0;

        double sum = 0.0;

        for (Sample sample : samples) {
            sum += distanceMeters(latitude, longitude, sample.latitude, sample.longitude);
        }

        double spread = sum / samples.size();
        double anchorBonus = Math.max(0.55, 1.0 - samples.size() * 0.08);

        return clamp(spread * anchorBonus, 3.0, 150.0);
    }

    private static double distanceMeters(double lat1, double lon1, double lat2, double lon2) {
        double meanLat = Math.toRadians((lat1 + lat2) / 2.0);
        double dx = (lon2 - lon1) * METERS_PER_LONGITUDE * Math.cos(meanLat);
        double dy = (lat2 - lat1) * METERS_PER_LATITUDE;

        return Math.sqrt(dx * dx + dy * dy);
    }

    private static boolean validCoordinate(double latitude, double longitude) {
        return !Double.isNaN(latitude)
                && !Double.isNaN(longitude)
                && !Double.isInfinite(latitude)
                && !Double.isInfinite(longitude)
                && Math.abs(latitude) <= 90.0
                && Math.abs(longitude) <= 180.0
                && !(latitude == 0.0 && longitude == 0.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
