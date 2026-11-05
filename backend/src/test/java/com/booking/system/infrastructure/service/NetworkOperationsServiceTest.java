package com.booking.system.infrastructure.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class NetworkOperationsServiceTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void ipamKeepsOnlyBoundDhcpLeasesWithValidIpAndMac() throws Exception {
        var rows = new ArrayList<NetworkOperationsService.Observed>();
        var leases = mapper.readTree("""
                [
                  {"address":"192.168.1.20","mac-address":"AA:BB:CC:DD:EE:01","host-name":"pc-01","status":"bound"},
                  {"address":"192.168.1.21","mac-address":"AA:BB:CC:DD:EE:02","status":"waiting"},
                  {"address":"999.1.1.1","mac-address":"AA:BB:CC:DD:EE:03","status":"bound"},
                  {"address":"192.168.1.22","mac-address":"not-a-mac","status":"bound"}
                ]
                """);

        NetworkOperationsService.collect(leases, "DHCP", rows);

        assertThat(rows).containsExactly(new NetworkOperationsService.Observed("DHCP", "192.168.1.20",
                "AA:BB:CC:DD:EE:01", "pc-01", null, "bound"));
    }

    @Test
    void arpObservationsDoNotRequireDhcpLeaseStatus() throws Exception {
        var rows = new ArrayList<NetworkOperationsService.Observed>();
        NetworkOperationsService.collect(mapper.readTree("[{\"address\":\"192.168.1.9\",\"mac-address\":\"AA:BB:CC:DD:EE:09\",\"interface\":\"bridge1\"}]"), "ARP", rows);
        assertThat(rows).containsExactly(new NetworkOperationsService.Observed("ARP", "192.168.1.9",
                "AA:BB:CC:DD:EE:09", null, "bridge1", null));
    }
}
