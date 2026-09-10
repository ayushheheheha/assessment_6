package com.emergency.dispatch;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

public class AmbulanceDispatchSystem {

    // Enums
    public enum EmergencyPriority {
        NORMAL(1), MODERATE(2), HIGH(3), CRITICAL(4);
        public final int level;
        EmergencyPriority(int level) { this.level = level; }
    }

    public enum AmbulanceType {
        BASIC(1), ADVANCED_LIFE_SUPPORT(2), ICU(3);
        public final int tier;
        AmbulanceType(int tier) { this.tier = tier; }
        public boolean isAdequateFor(EmergencyPriority p) {
            return switch (p) {
                case CRITICAL -> this == ICU || this == ADVANCED_LIFE_SUPPORT;
                case HIGH -> this == ICU || this == ADVANCED_LIFE_SUPPORT;
                case MODERATE, NORMAL -> true;
            };
        }
    }

    public enum AmbulanceStatus {
        AVAILABLE, DISPATCHED, EN_ROUTE, PATIENT_PICKED_UP, HOSPITAL_ARRIVED;
        public boolean canTransitionTo(AmbulanceStatus next) {
            return switch (this) {
                case AVAILABLE -> next == DISPATCHED;
                case DISPATCHED -> next == EN_ROUTE;
                case EN_ROUTE -> next == PATIENT_PICKED_UP;
                case PATIENT_PICKED_UP -> next == HOSPITAL_ARRIVED;
                case HOSPITAL_ARRIVED -> next == AVAILABLE;
            };
        }
    }

    // Records
    public record Location(double lat, double lon, String desc) {
        public Location {
            if (lat < -90 || lat > 90) throw new IllegalArgumentException("Latitude must be between -90 and 90");
            if (lon < -180 || lon > 180) throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
        public double distanceTo(Location o) {
            double dLat = Math.toRadians(o.lat - lat), dLon = Math.toRadians(o.lon - lon);
            double a = Math.sin(dLat/2)*Math.sin(dLat/2) + Math.cos(Math.toRadians(lat))*Math.cos(Math.toRadians(o.lat))*Math.sin(dLon/2)*Math.sin(dLon/2);
            return Math.round(6371.0 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 100.0) / 100.0;
        }
    }

    public record Driver(String id, String name, String phone, String license) {}
    public record Hospital(String id, String name, Location location, int capacity, String phone) {}
    public record HistoryRecord(String reqId, String ambId, String status, String note, Instant timestamp) {}

    // Exceptions
    public static class DispatchException extends RuntimeException { public DispatchException(String m) { super(m); } }
    public static class AmbulanceAlreadyAssignedException extends DispatchException {
        public AmbulanceAlreadyAssignedException(String ambId, String reqId) {
            super("Ambulance '" + ambId + "' already committed to emergency '" + reqId + "'");
        }
    }
    public static class InvalidStateTransitionException extends DispatchException {
        public InvalidStateTransitionException(String id, AmbulanceStatus from, AmbulanceStatus to) {
            super("Invalid transition for ambulance '" + id + "': cannot go from [" + from + "] to [" + to + "]");
        }
    }
    public static class InvalidRequestException extends DispatchException { public InvalidRequestException(String m) { super(m); } }
    public static class ResourceNotFoundException extends DispatchException { public ResourceNotFoundException(String m) { super(m); } }

    // Ambulance Entity
    public static class Ambulance {
        private final String id;
        private final AmbulanceType type;
        private Driver driver;
        private Location location;
        private AmbulanceStatus status = AmbulanceStatus.AVAILABLE;
        private String activeRequestId;

        public Ambulance(String id, AmbulanceType type, Driver driver, Location location) {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("Ambulance ID cannot be empty");
            this.id = id; this.type = Objects.requireNonNull(type);
            this.driver = Objects.requireNonNull(driver); this.location = Objects.requireNonNull(location);
        }
        public String getId() { return id; }
        public AmbulanceType getType() { return type; }
        public synchronized Driver getDriver() { return driver; }
        public synchronized Location getLocation() { return location; }
        public synchronized void setLocation(Location loc) { this.location = Objects.requireNonNull(loc); }
        public synchronized AmbulanceStatus getStatus() { return status; }
        public synchronized String getActiveRequestId() { return activeRequestId; }
        public synchronized boolean isAvailable() { return status == AmbulanceStatus.AVAILABLE && activeRequestId == null; }

        public synchronized void assignTo(String reqId) {
            if (!isAvailable()) throw new AmbulanceAlreadyAssignedException(id, activeRequestId);
            this.activeRequestId = reqId; this.status = AmbulanceStatus.DISPATCHED;
        }
        public synchronized void transitionTo(AmbulanceStatus next) {
            if (!status.canTransitionTo(next)) throw new InvalidStateTransitionException(id, status, next);
            this.status = next;
            if (next == AmbulanceStatus.AVAILABLE) this.activeRequestId = null;
        }
        public synchronized void release() { this.status = AmbulanceStatus.AVAILABLE; this.activeRequestId = null; }
        @Override public String toString() { return id + " (" + type + ", " + status + ")"; }
    }

    // Emergency Request Entity
    public static class EmergencyRequest implements Comparable<EmergencyRequest> {
        private final String id, patientId, emergencyType;
        private final EmergencyPriority priority;
        private final Location pickupLocation;
        private final Hospital destinationHospital;
        private final Instant requestedAt = Instant.now();
        private String assignedAmbulanceId, status = "RECEIVED";
        private AmbulanceType assignedType;
        private Double distanceKm, etaMinutes;
        private final List<HistoryRecord> history = new ArrayList<>();

        public EmergencyRequest(String id, String patientId, String emergencyType, EmergencyPriority priority, Location pickup, Hospital hosp) {
            if (patientId == null || patientId.isBlank()) throw new IllegalArgumentException("Patient ID cannot be empty");
            this.id = Objects.requireNonNull(id); this.patientId = patientId;
            this.emergencyType = Objects.requireNonNull(emergencyType); this.priority = Objects.requireNonNull(priority);
            this.pickupLocation = Objects.requireNonNull(pickup); this.destinationHospital = Objects.requireNonNull(hosp);
            log("RECEIVED", "Emergency call received with priority " + priority);
        }
        public EmergencyRequest(String patientId, String emergencyType, EmergencyPriority priority, Location pickup, Hospital hosp) {
            this(UUID.randomUUID().toString(), patientId, emergencyType, priority, pickup, hosp);
        }

        public String getId() { return id; }
        public String getPatientId() { return patientId; }
        public String getEmergencyType() { return emergencyType; }
        public EmergencyPriority getPriority() { return priority; }
        public Location getPickupLocation() { return pickupLocation; }
        public Hospital getDestinationHospital() { return destinationHospital; }
        public synchronized String getAssignedAmbulanceId() { return assignedAmbulanceId; }
        public synchronized AmbulanceType getAssignedType() { return assignedType; }
        public synchronized Double getDistanceKm() { return distanceKm; }
        public synchronized Double getEtaMinutes() { return etaMinutes; }
        public synchronized String getStatus() { return status; }
        public synchronized List<HistoryRecord> getHistory() { return Collections.unmodifiableList(new ArrayList<>(history)); }

        public synchronized void markDispatched(String ambId, AmbulanceType type, double dist, double eta) {
            this.assignedAmbulanceId = ambId; this.assignedType = type; this.distanceKm = dist; this.etaMinutes = eta;
            this.status = "DISPATCHED";
            log("DISPATCHED", String.format("Assigned %s [%s], Dist: %.2fkm, ETA: %.1fmin", type, ambId, dist, eta));
        }
        public synchronized void updateStatus(String st, String note) { this.status = st; log(st, note); }
        private synchronized void log(String st, String note) { history.add(new HistoryRecord(id, assignedAmbulanceId, st, note, Instant.now())); }

        @Override
        public int compareTo(EmergencyRequest o) {
            int p = Integer.compare(o.priority.level, this.priority.level);
            return p != 0 ? p : this.requestedAt.compareTo(o.requestedAt);
        }
        @Override public String toString() { return String.format("Req[%s, Pat=%s, Prio=%s, Stat=%s, Amb=%s]", id, patientId, priority, status, assignedAmbulanceId); }
    }

    // Core Dispatch Service
    public static class DispatchService {
        private final Map<String, Ambulance> ambulances = new ConcurrentHashMap<>();
        private final Map<String, EmergencyRequest> requests = new ConcurrentHashMap<>();
        private final PriorityBlockingQueue<EmergencyRequest> queue = new PriorityBlockingQueue<>();

        public void registerAmbulance(Ambulance amb) { ambulances.put(amb.getId(), amb); }
        public Optional<Ambulance> getAmbulance(String id) { return Optional.ofNullable(ambulances.get(id)); }
        public List<Ambulance> getAvailableAmbulances() { return ambulances.values().stream().filter(Ambulance::isAvailable).toList(); }
        public List<EmergencyRequest> getWaitingQueue() { List<EmergencyRequest> list = new ArrayList<>(queue); Collections.sort(list); return list; }
        public Optional<EmergencyRequest> getRequest(String id) { return Optional.ofNullable(requests.get(id)); }
        public List<HistoryRecord> getHistory(String reqId) {
            EmergencyRequest req = requests.get(reqId);
            if (req == null) throw new ResourceNotFoundException("Emergency request not found: " + reqId);
            return req.getHistory();
        }

        public synchronized EmergencyRequest submitRequest(EmergencyRequest req) {
            if (req == null) throw new InvalidRequestException("Emergency request cannot be null");
            requests.put(req.getId(), req);
            Optional<Ambulance> best = getAvailableAmbulances().stream()
                    .filter(a -> a.getType().isAdequateFor(req.getPriority()))
                    .min(Comparator.comparingInt((Ambulance a) -> tierPenalty(a.getType(), req.getPriority()))
                            .thenComparingDouble(a -> a.getLocation().distanceTo(req.getPickupLocation())));

            if (best.isPresent()) {
                dispatch(best.get(), req);
            } else {
                queue.offer(req);
                req.updateStatus("QUEUED", "Placed in waiting queue");
            }
            return req;
        }

        public synchronized void updateAmbulanceStatus(String ambId, AmbulanceStatus newStatus) {
            Ambulance amb = ambulances.get(ambId);
            if (amb == null) throw new ResourceNotFoundException("Ambulance not found: " + ambId);

            EmergencyRequest req = amb.getActiveRequestId() != null ? requests.get(amb.getActiveRequestId()) : null;
            amb.transitionTo(newStatus);

            if (req != null) {
                switch (newStatus) {
                    case EN_ROUTE -> req.updateStatus("EN_ROUTE", "Ambulance en route");
                    case PATIENT_PICKED_UP -> { amb.setLocation(req.getPickupLocation()); req.updateStatus("PATIENT_PICKED_UP", "Patient picked up"); }
                    case HOSPITAL_ARRIVED -> { amb.setLocation(req.getDestinationHospital().location()); req.updateStatus("HOSPITAL_ARRIVED", "Arrived at hospital"); }
                    case AVAILABLE -> req.updateStatus("COMPLETED", "Emergency completed");
                    case DISPATCHED -> {}
                }
            }
            if (newStatus == AmbulanceStatus.AVAILABLE) checkQueue(amb);
        }

        public synchronized void cancelRequest(String reqId, String reason) {
            EmergencyRequest req = requests.get(reqId);
            if (req == null) throw new ResourceNotFoundException("Request not found: " + reqId);
            queue.remove(req);
            if (req.getAssignedAmbulanceId() != null) {
                Ambulance amb = ambulances.get(req.getAssignedAmbulanceId());
                if (amb != null) { amb.release(); checkQueue(amb); }
            }
            req.updateStatus("CANCELLED", reason);
        }

        private void checkQueue(Ambulance amb) {
            for (EmergencyRequest r : getWaitingQueue()) {
                if (amb.getType().isAdequateFor(r.getPriority())) {
                    if (queue.remove(r)) { dispatch(amb, r); break; }
                }
            }
        }

        private void dispatch(Ambulance amb, EmergencyRequest req) {
            double dist = amb.getLocation().distanceTo(req.getPickupLocation());
            double speed = req.getPriority() == EmergencyPriority.CRITICAL ? 60.0 : (req.getPriority() == EmergencyPriority.HIGH ? 50.0 : 35.0);
            double eta = Math.round(((dist / speed) * 60.0 + 1.0) * 10.0) / 10.0;
            amb.assignTo(req.getId());
            req.markDispatched(amb.getId(), amb.getType(), dist, eta);
        }

        private int tierPenalty(AmbulanceType t, EmergencyPriority p) {
            if (p == EmergencyPriority.CRITICAL) return t == AmbulanceType.ICU ? 0 : 1;
            if (p == EmergencyPriority.HIGH) return t == AmbulanceType.ADVANCED_LIFE_SUPPORT ? 0 : 1;
            return switch (t) { case BASIC -> 0; case ADVANCED_LIFE_SUPPORT -> 1; case ICU -> 2; };
        }
    }

    // Main Simulation Runner
    public static void main(String[] args) {
        System.out.println("=== EMERGENCY AMBULANCE DISPATCH & TRACKING SYSTEM ===");
        DispatchService service = new DispatchService();
        Hospital hosp = new Hospital("H-1", "Central Trauma Care", new Location(28.6139, 77.2090, "Central"), 50, "911");

        Ambulance icu = new Ambulance("AMB-ICU", AmbulanceType.ICU, new Driver("D1", "John", "999", "L1"), new Location(28.62, 77.21, "Base 1"));
        Ambulance bls = new Ambulance("AMB-BLS", AmbulanceType.BASIC, new Driver("D2", "Bob", "888", "L2"), new Location(28.63, 77.22, "Base 2"));
        service.registerAmbulance(icu);
        service.registerAmbulance(bls);

        EmergencyRequest r1 = new EmergencyRequest("R1", "P1", "CARDIAC_ARREST", EmergencyPriority.CRITICAL, new Location(28.618, 77.212, "Site 1"), hosp);
        EmergencyRequest r2 = new EmergencyRequest("R2", "P2", "FRACTURE", EmergencyPriority.MODERATE, new Location(28.630, 77.220, "Site 2"), hosp);
        EmergencyRequest r3 = new EmergencyRequest("R3", "P3", "RESPIRATORY_FAILURE", EmergencyPriority.CRITICAL, new Location(28.615, 77.210, "Site 3"), hosp);

        service.submitRequest(r1);
        service.submitRequest(r2);
        service.submitRequest(r3); // Queued because all ambulances busy

        System.out.println("R1: " + r1.getStatus() + " -> " + r1.getAssignedAmbulanceId() + " (ETA: " + r1.getEtaMinutes() + "m)");
        System.out.println("R2: " + r2.getStatus() + " -> " + r2.getAssignedAmbulanceId() + " (ETA: " + r2.getEtaMinutes() + "m)");
        System.out.println("R3: " + r3.getStatus() + " (Queue Size: " + service.getWaitingQueue().size() + ")");

        // Progress R1 lifecycle
        service.updateAmbulanceStatus("AMB-ICU", AmbulanceStatus.EN_ROUTE);
        service.updateAmbulanceStatus("AMB-ICU", AmbulanceStatus.PATIENT_PICKED_UP);
        service.updateAmbulanceStatus("AMB-ICU", AmbulanceStatus.HOSPITAL_ARRIVED);
        service.updateAmbulanceStatus("AMB-ICU", AmbulanceStatus.AVAILABLE);

        System.out.println("After AMB-ICU Available, R3 status: " + r3.getStatus() + " -> " + r3.getAssignedAmbulanceId());
        System.out.println("System demonstration finished successfully.");
    }
}
