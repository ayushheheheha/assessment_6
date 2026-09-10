package com.emergency.dispatch;

import com.emergency.dispatch.AmbulanceDispatchSystem.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Detailed test suite covering all positive and negative test cases with live console output.
 */
class AmbulanceDispatchSystemTest {

    private DispatchService service;
    private Hospital hosp;
    private Driver d1, d2;

    @BeforeEach
    void setUp(TestInfo info) {
        System.out.println("\n>>> [TEST] " + info.getDisplayName());
        service = new DispatchService();
        hosp = new Hospital("H1", "City Trauma Center", new Location(28.6139, 77.2090, "Central"), 50, "911");
        d1 = new Driver("D1", "Rajesh", "9876543210", "DL-01");
        d2 = new Driver("D2", "Amit", "9876543211", "DL-02");
    }

    // ==========================================
    // POSITIVE TEST CASES
    // ==========================================

    @Test
    @DisplayName("TC-POS-01: Critical Emergency Immediate Allocation to ICU Ambulance")
    void testCriticalAllocationToIcu() {
        Ambulance icu = new Ambulance("A-ICU", AmbulanceType.ICU, d1, new Location(28.62, 77.21, "Base"));
        service.registerAmbulance(icu);

        EmergencyRequest req = new EmergencyRequest("P1", "CARDIAC_ARREST", EmergencyPriority.CRITICAL, new Location(28.618, 77.212, "Site"), hosp);
        service.submitRequest(req);

        System.out.printf("  [OUTPUT] Status: %s | Ambulance: %s | Distance: %.2f km | ETA: %.1f min\n",
                req.getStatus(), req.getAssignedAmbulanceId(), req.getDistanceKm(), req.getEtaMinutes());

        assertThat(req.getStatus()).isEqualTo("DISPATCHED");
        assertThat(req.getAssignedAmbulanceId()).isEqualTo("A-ICU");
        assertThat(req.getEtaMinutes()).isGreaterThan(0.0);
        System.out.println("  [VALIDATION] [PASS] Allocated immediately to ICU with valid ETA.");
    }

    @Test
    @DisplayName("TC-POS-02: Multi-Emergency Capability Tier Matching (Critical -> ICU, Normal -> Basic)")
    void testCapabilityMatching() {
        service.registerAmbulance(new Ambulance("A-BLS", AmbulanceType.BASIC, d1, new Location(28.60, 77.20, "Base 1")));
        service.registerAmbulance(new Ambulance("A-ICU", AmbulanceType.ICU, d2, new Location(28.60, 77.20, "Base 2")));

        EmergencyRequest crit = new EmergencyRequest("P1", "CARDIAC_ARREST", EmergencyPriority.CRITICAL, new Location(28.61, 77.21, "Loc 1"), hosp);
        EmergencyRequest norm = new EmergencyRequest("P2", "MINOR_INJURY", EmergencyPriority.NORMAL, new Location(28.61, 77.21, "Loc 2"), hosp);

        service.submitRequest(crit);
        service.submitRequest(norm);

        System.out.printf("  [OUTPUT] Critical matched: %s (%s) | Normal matched: %s (%s)\n",
                crit.getAssignedAmbulanceId(), crit.getAssignedType(), norm.getAssignedAmbulanceId(), norm.getAssignedType());

        assertThat(crit.getAssignedAmbulanceId()).isEqualTo("A-ICU");
        assertThat(norm.getAssignedAmbulanceId()).isEqualTo("A-BLS");
        System.out.println("  [VALIDATION] [PASS] Each emergency matched its capability tier.");
    }

    @Test
    @DisplayName("TC-POS-03: Priority Queue Preemption (Critical Preempts Normal and Moderate)")
    void testPriorityQueuePreemption() {
        EmergencyRequest norm = new EmergencyRequest("R-NORM", "P1", "MINOR_INJURY", EmergencyPriority.NORMAL, new Location(28.60, 77.20, "L1"), hosp);
        EmergencyRequest mod = new EmergencyRequest("R-MOD", "P2", "FRACTURE", EmergencyPriority.MODERATE, new Location(28.60, 77.20, "L2"), hosp);
        EmergencyRequest crit = new EmergencyRequest("R-CRIT", "P3", "CARDIAC_ARREST", EmergencyPriority.CRITICAL, new Location(28.60, 77.20, "L3"), hosp);

        service.submitRequest(norm);
        service.submitRequest(mod);
        service.submitRequest(crit);

        List<EmergencyRequest> q = service.getWaitingQueue();
        System.out.printf("  [OUTPUT] Queue: 1.%s (Prio %s) -> 2.%s (Prio %s) -> 3.%s (Prio %s)\n",
                q.get(0).getId(), q.get(0).getPriority(), q.get(1).getId(), q.get(1).getPriority(), q.get(2).getId(), q.get(2).getPriority());

        assertThat(q.get(0).getId()).isEqualTo("R-CRIT");
        assertThat(q.get(1).getId()).isEqualTo("R-MOD");
        assertThat(q.get(2).getId()).isEqualTo("R-NORM");
        System.out.println("  [VALIDATION] [PASS] Critical emergency queued ahead of earlier lower priority calls.");
    }

    @Test
    @DisplayName("TC-POS-04: Proximity Allocation (Closest Capable Ambulance Selected)")
    void testProximityAllocation() {
        Ambulance far = new Ambulance("A-FAR", AmbulanceType.BASIC, d1, new Location(28.80, 77.40, "Far Post"));
        Ambulance close = new Ambulance("A-CLOSE", AmbulanceType.BASIC, d2, new Location(28.611, 77.206, "Close Post"));
        service.registerAmbulance(far);
        service.registerAmbulance(close);

        EmergencyRequest req = new EmergencyRequest("P1", "MINOR_INJURY", EmergencyPriority.NORMAL, new Location(28.610, 77.205, "Central"), hosp);
        service.submitRequest(req);

        System.out.println("  [OUTPUT] Allocated Ambulance: " + req.getAssignedAmbulanceId() + " (Distance: " + req.getDistanceKm() + "km)");
        assertThat(req.getAssignedAmbulanceId()).isEqualTo("A-CLOSE");
        System.out.println("  [VALIDATION] [PASS] Closest ambulance selected.");
    }

    @Test
    @DisplayName("TC-POS-05: Full State Machine Progression (Available -> Dispatched -> En Route -> Picked Up -> Arrived -> Available)")
    void testFullLifecycle() {
        Ambulance amb = new Ambulance("A1", AmbulanceType.BASIC, d1, new Location(28.60, 77.20, "Base"));
        service.registerAmbulance(amb);

        EmergencyRequest req = new EmergencyRequest("P1", "FRACTURE", EmergencyPriority.MODERATE, new Location(28.61, 77.21, "Home"), hosp);
        service.submitRequest(req);
        System.out.println("  [STATE 1] Dispatched: Amb=" + amb.getStatus() + ", Req=" + req.getStatus());

        service.updateAmbulanceStatus("A1", AmbulanceStatus.EN_ROUTE);
        System.out.println("  [STATE 2] En Route: Amb=" + amb.getStatus() + ", Req=" + req.getStatus());

        service.updateAmbulanceStatus("A1", AmbulanceStatus.PATIENT_PICKED_UP);
        System.out.println("  [STATE 3] Picked Up: Amb=" + amb.getStatus() + ", Req=" + req.getStatus());

        service.updateAmbulanceStatus("A1", AmbulanceStatus.HOSPITAL_ARRIVED);
        System.out.println("  [STATE 4] Arrived: Amb=" + amb.getStatus() + ", Req=" + req.getStatus());

        service.updateAmbulanceStatus("A1", AmbulanceStatus.AVAILABLE);
        System.out.println("  [STATE 5] Available: Amb=" + amb.getStatus() + ", Req=" + req.getStatus());

        assertThat(amb.isAvailable()).isTrue();
        assertThat(req.getStatus()).isEqualTo("COMPLETED");
        System.out.println("  [VALIDATION] [PASS] State machine completed all 5 transitions successfully.");
    }

    @Test
    @DisplayName("TC-POS-06: Automatic Queue Reallocation on Ambulance Becoming Available")
    void testAutoQueueAllocation() {
        Ambulance amb = new Ambulance("A1", AmbulanceType.ICU, d1, new Location(28.60, 77.20, "Base"));
        service.registerAmbulance(amb);

        EmergencyRequest r1 = new EmergencyRequest("P1", "CARDIAC_ARREST", EmergencyPriority.CRITICAL, new Location(28.61, 77.21, "L1"), hosp);
        EmergencyRequest r2 = new EmergencyRequest("P2", "RESPIRATORY_FAILURE", EmergencyPriority.CRITICAL, new Location(28.62, 77.22, "L2"), hosp);

        service.submitRequest(r1);
        service.submitRequest(r2); // Queued
        System.out.println("  [INPUT] R1 is DISPATCHED. R2 is " + r2.getStatus() + " in queue.");

        // Complete R1
        service.updateAmbulanceStatus("A1", AmbulanceStatus.EN_ROUTE);
        service.updateAmbulanceStatus("A1", AmbulanceStatus.PATIENT_PICKED_UP);
        service.updateAmbulanceStatus("A1", AmbulanceStatus.HOSPITAL_ARRIVED);
        service.updateAmbulanceStatus("A1", AmbulanceStatus.AVAILABLE);

        System.out.println("  [OUTPUT] After A1 returned to AVAILABLE -> R2 Status: " + r2.getStatus() + " (Amb: " + r2.getAssignedAmbulanceId() + ")");
        assertThat(r2.getStatus()).isEqualTo("DISPATCHED");
        assertThat(r2.getAssignedAmbulanceId()).isEqualTo("A1");
        assertThat(service.getWaitingQueue()).isEmpty();
        System.out.println("  [VALIDATION] [PASS] Waiting request automatically allocated.");
    }

    @Test
    @DisplayName("TC-POS-07: Complete Audit History Trail Recording")
    void testAuditHistory() {
        Ambulance amb = new Ambulance("A1", AmbulanceType.BASIC, d1, new Location(28.60, 77.20, "Base"));
        service.registerAmbulance(amb);

        EmergencyRequest req = new EmergencyRequest("R-AUDIT", "P1", "MINOR_INJURY", EmergencyPriority.NORMAL, new Location(28.61, 77.21, "L1"), hosp);
        service.submitRequest(req);
        service.updateAmbulanceStatus("A1", AmbulanceStatus.EN_ROUTE);
        service.updateAmbulanceStatus("A1", AmbulanceStatus.PATIENT_PICKED_UP);
        service.updateAmbulanceStatus("A1", AmbulanceStatus.HOSPITAL_ARRIVED);
        service.updateAmbulanceStatus("A1", AmbulanceStatus.AVAILABLE);

        List<HistoryRecord> hist = service.getHistory("R-AUDIT");
        System.out.println("  [OUTPUT] Recorded History (" + hist.size() + " events):");
        hist.forEach(h -> System.out.println("    [" + h.timestamp() + "] " + h.status() + " - " + h.note()));

        assertThat(hist).hasSize(6);
        assertThat(hist).extracting(HistoryRecord::status).containsExactly("RECEIVED", "DISPATCHED", "EN_ROUTE", "PATIENT_PICKED_UP", "HOSPITAL_ARRIVED", "COMPLETED");
        System.out.println("  [VALIDATION] [PASS] Complete audit trail preserved.");
    }

    @Test
    @DisplayName("TC-POS-08: Emergency Cancellation and Resource Release")
    void testCancelRequest() {
        Ambulance amb = new Ambulance("A1", AmbulanceType.BASIC, d1, new Location(28.60, 77.20, "Base"));
        service.registerAmbulance(amb);

        EmergencyRequest req = new EmergencyRequest("R-CAN", "P1", "MINOR_INJURY", EmergencyPriority.NORMAL, new Location(28.61, 77.21, "L1"), hosp);
        service.submitRequest(req);
        System.out.println("  [INPUT] Amb status before cancel: " + amb.getStatus());

        service.cancelRequest("R-CAN", "Alternative vehicle found");
        System.out.println("  [OUTPUT] Req status: " + req.getStatus() + " | Amb status: " + amb.getStatus());

        assertThat(req.getStatus()).isEqualTo("CANCELLED");
        assertThat(amb.isAvailable()).isTrue();
        System.out.println("  [VALIDATION] [PASS] Cancelled emergency successfully released ambulance.");
    }

    // ==========================================
    // NEGATIVE TEST CASES
    // ==========================================

    @Test
    @DisplayName("TC-NEG-01: Prevent Double Assignment to Busy Ambulance")
    void testDoubleAssignment() {
        Ambulance amb = new Ambulance("A1", AmbulanceType.BASIC, d1, new Location(28.60, 77.20, "Base"));
        amb.assignTo("REQ-1");
        System.out.println("  [ACTION] Attempting second assignment to busy ambulance A1...");

        assertThatThrownBy(() -> amb.assignTo("REQ-2"))
                .isInstanceOf(AmbulanceAlreadyAssignedException.class)
                .satisfies(e -> System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: " + e.getMessage()));
        System.out.println("  [VALIDATION] [PASS] Double-booking blocked.");
    }

    @Test
    @DisplayName("TC-NEG-02: Reject Illegal State Transitions (e.g. AVAILABLE -> PATIENT_PICKED_UP)")
    void testIllegalTransitions() {
        Ambulance amb = new Ambulance("A1", AmbulanceType.BASIC, d1, new Location(28.60, 77.20, "Base"));
        service.registerAmbulance(amb);

        System.out.println("  [ACTION] Testing illegal jump AVAILABLE -> PATIENT_PICKED_UP...");
        assertThatThrownBy(() -> service.updateAmbulanceStatus("A1", AmbulanceStatus.PATIENT_PICKED_UP))
                .isInstanceOf(InvalidStateTransitionException.class)
                .satisfies(e -> System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: " + e.getMessage()));

        System.out.println("  [VALIDATION] [PASS] Illegal state transition blocked.");
    }

    @Test
    @DisplayName("TC-NEG-03: Reject Invalid Request Parameters (Null / Empty Patient)")
    void testInvalidRequestValidation() {
        System.out.println("  [ACTION] Testing null request & blank patient ID...");
        assertThatThrownBy(() -> service.submitRequest(null)).isInstanceOf(InvalidRequestException.class);
        assertThatThrownBy(() -> new EmergencyRequest("R1", " ", "CARDIAC_ARREST", EmergencyPriority.CRITICAL, new Location(28.6, 77.2, "L"), hosp))
                .isInstanceOf(IllegalArgumentException.class);
        System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION on null/blank input");
        System.out.println("  [VALIDATION] [PASS] Request parameter validation passed.");
    }

    @Test
    @DisplayName("TC-NEG-04: Reject Resource Not Found (Unknown Ambulance or History)")
    void testResourceNotFound() {
        System.out.println("  [ACTION] Updating status of non-existent ambulance...");
        assertThatThrownBy(() -> service.updateAmbulanceStatus("GHOST-AMB", AmbulanceStatus.EN_ROUTE))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION: " + e.getMessage()));

        assertThatThrownBy(() -> service.getHistory("GHOST-REQ")).isInstanceOf(ResourceNotFoundException.class);
        System.out.println("  [VALIDATION] [PASS] ResourceNotFoundException thrown correctly.");
    }

    @Test
    @DisplayName("TC-NEG-05: Reject Invalid Geographic Coordinates (Latitude / Longitude Range)")
    void testInvalidCoordinates() {
        System.out.println("  [ACTION] Testing invalid latitude 95.0 and longitude 195.0...");
        assertThatThrownBy(() -> new Location(95.0, 77.2, "Bad Lat")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Location(28.6, 195.0, "Bad Lon")).isInstanceOf(IllegalArgumentException.class);
        System.out.println("  [OUTPUT] CAUGHT EXPECTED EXCEPTION on out-of-range coordinates");
        System.out.println("  [VALIDATION] [PASS] Coordinates boundaries validated.");
    }
}
