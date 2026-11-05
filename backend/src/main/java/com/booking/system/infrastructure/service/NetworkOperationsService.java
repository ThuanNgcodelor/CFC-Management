package com.booking.system.infrastructure.service;

import com.booking.system.infrastructure.api.InfrastructureDtos.*;
import com.booking.system.infrastructure.config.InfrastructureProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class NetworkOperationsService {
    private static final Logger log = LoggerFactory.getLogger(NetworkOperationsService.class);
    private final JdbcTemplate jdbc;
    private final InfrastructureProperties properties;
    private final RestClient.Builder restBuilder;
    private final NetworkObservationPersistence persistence;

    public NetworkOperationsService(JdbcTemplate jdbc, InfrastructureProperties properties, RestClient.Builder restBuilder, NetworkObservationPersistence persistence) {
        this.jdbc = jdbc; this.properties = properties; this.restBuilder = restBuilder; this.persistence = persistence;
    }

    public int syncIpam() {
        if (!properties.monitoringEnabled() || !routerConfigured()) throw new IllegalStateException("Chưa bật kết nối RouterOS HTTPS chỉ đọc.");
        String routerId = jdbc.query("SELECT id FROM network_devices WHERE active=TRUE AND device_type='ROUTER' AND management_ip=? LIMIT 1", rs -> rs.next() ? rs.getString(1) : null, routerIp());
        if (routerId == null) throw new IllegalStateException("Chưa có router trong danh mục thiết bị.");
        try {
            URI base = URI.create(properties.routerBaseUrl());
            RestClient client = restBuilder.baseUrl(base.toString()).requestFactory(requestFactory())
                    .defaultHeaders(headers -> headers.setBasicAuth(properties.routerUsername(), properties.routerPassword())).build();
            List<Observed> observations = new ArrayList<>();
            collect(client.get().uri("/rest/ip/dhcp-server/lease").retrieve().body(JsonNode.class), "DHCP", observations);
            collect(client.get().uri("/rest/ip/arp").retrieve().body(JsonNode.class), "ARP", observations);
            return persistence.persist(routerId, observations);
        } catch (Exception exception) {
            log.warn("Không đồng bộ được IPAM từ RouterOS {}: {}", routerIp(), exception.getClass().getSimpleName());
            throw new IllegalStateException("Không lấy được DHCP/ARP từ RouterOS. Kiểm tra quyền read-only và kết nối HTTPS.");
        }
    }

    @Transactional(readOnly = true)
    public List<IpObservation> observations(String query) {
        String q = like(query);
        return jdbc.query("SELECT ip_address,mac_address,host_name,interface_name,source,lease_status,first_seen_at,last_seen_at FROM network_ip_observations WHERE (?='%%' OR ip_address LIKE ? OR COALESCE(mac_address,'') LIKE ? OR COALESCE(host_name,'') LIKE ?) ORDER BY last_seen_at DESC LIMIT 500",
                (rs, n) -> new IpObservation(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getTimestamp(7).toInstant().toString(), rs.getTimestamp(8).toInstant().toString()), q, q, q, q);
    }

    @Transactional(readOnly = true)
    public List<IpConflict> conflicts() {
        return jdbc.query("SELECT o.ip_address,GROUP_CONCAT(DISTINCT o.mac_address ORDER BY o.mac_address) macs,GROUP_CONCAT(DISTINCT o.source ORDER BY o.source) sources,MAX(o.last_seen_at) seen,COALESCE(r.status,'OPEN') review_status,r.note FROM network_ip_observations o LEFT JOIN network_ip_reviews r ON r.ip_address=o.ip_address WHERE o.mac_address IS NOT NULL AND o.last_seen_at>=CURRENT_TIMESTAMP(6)-INTERVAL 15 MINUTE GROUP BY o.ip_address,r.status,r.note HAVING COUNT(DISTINCT o.mac_address)>1 ORDER BY seen DESC LIMIT 200",
                (rs, n) -> new IpConflict(rs.getString("ip_address"), csv(rs.getString("macs")), csv(rs.getString("sources")), rs.getString("review_status"), rs.getString("note"), rs.getTimestamp("seen").toInstant().toString()));
    }

    @Transactional
    public void reviewIp(String ip, IpReviewRequest request, String actor) {
        jdbc.update("INSERT INTO network_ip_reviews(ip_address,status,note,reviewed_by,reviewed_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP(6)) ON DUPLICATE KEY UPDATE status=VALUES(status),note=VALUES(note),reviewed_by=VALUES(reviewed_by),reviewed_at=CURRENT_TIMESTAMP(6)", ip, request.status(), clean(request.note()), actor);
    }

    @Transactional(readOnly = true)
    public List<TopologyLink> topology() {
        return jdbc.query("SELECT l.id,l.source_device_id,s.name,l.source_port,l.target_device_id,t.name,l.target_port,l.link_type,l.vlan_id,l.verification_status,l.notes,l.created_by FROM network_topology_links l JOIN network_devices s ON s.id=l.source_device_id JOIN network_devices t ON t.id=l.target_device_id ORDER BY s.name,t.name LIMIT 500",
                (rs, n) -> new TopologyLink(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getObject(9, Integer.class),rs.getString(10),rs.getString(11),rs.getString(12)));
    }

    @Transactional
    public TopologyLink addLink(TopologyLinkRequest request, String actor) {
        if (request.sourceDeviceId().equals(request.targetDeviceId())) throw new IllegalArgumentException("Hai đầu liên kết phải là hai thiết bị khác nhau.");
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO network_topology_links(id,source_device_id,source_port,target_device_id,target_port,link_type,vlan_id,verification_status,notes,created_by) VALUES(?,?,?,?,?,?,?,?,?,?)", id, request.sourceDeviceId(), clean(request.sourcePort()), request.targetDeviceId(), clean(request.targetPort()), Optional.ofNullable(request.linkType()).orElse("ETHERNET"), request.vlanId(), Optional.ofNullable(request.verificationStatus()).orElse("UNVERIFIED"), clean(request.notes()), actor);
        return topology().stream().filter(link -> link.id().equals(id)).findFirst().orElseThrow();
    }

    @Transactional
    public void verifyLink(String id, String status) {
        if (!Set.of("UNVERIFIED", "MANUAL_VERIFIED").contains(status)) throw new IllegalArgumentException("Trạng thái xác minh không hợp lệ.");
        if (jdbc.update("UPDATE network_topology_links SET verification_status=? WHERE id=?", status, id) == 0) throw new NoSuchElementException("Không tìm thấy liên kết topology.");
    }

    @Transactional(readOnly = true)
    public List<Asset> assets(String query) {
        String q = like(query);
        return jdbc.query("SELECT id,asset_tag,asset_type,name,mac_address,ip_address,department,location,responsible_person,status,notes FROM network_assets WHERE (?='%%' OR asset_tag LIKE ? OR name LIKE ? OR COALESCE(mac_address,'') LIKE ? OR COALESCE(ip_address,'') LIKE ?) ORDER BY name LIMIT 500",
                (rs,n)->new Asset(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7),rs.getString(8),rs.getString(9),rs.getString(10),rs.getString(11)),q,q,q,q,q);
    }

    @Transactional
    public Asset saveAsset(String id, AssetRequest request, String actor) {
        String mac = clean(request.macAddress()); if (mac != null) mac = mac.toUpperCase(Locale.ROOT);
        String department = clean(request.department()), location = clean(request.location()), responsible = clean(request.responsiblePerson());
        String status = Optional.ofNullable(request.status()).orElse("ACTIVE");
        if (id == null) {
            id = UUID.randomUUID().toString();
            jdbc.update("INSERT INTO network_assets(id,asset_tag,asset_type,name,mac_address,ip_address,department,location,responsible_person,status,notes) VALUES(?,?,?,?,?,?,?,?,?,?,?)",id,request.assetTag().trim(),request.assetType(),request.name().trim(),mac,clean(request.ipAddress()),department,location,responsible,status,clean(request.notes()));
        } else {
            jdbc.update("UPDATE network_asset_assignments SET valid_to=CURRENT_TIMESTAMP(6) WHERE asset_id=? AND valid_to IS NULL",id);
            if (jdbc.update("UPDATE network_assets SET asset_tag=?,asset_type=?,name=?,mac_address=?,ip_address=?,department=?,location=?,responsible_person=?,status=?,notes=? WHERE id=?",request.assetTag().trim(),request.assetType(),request.name().trim(),mac,clean(request.ipAddress()),department,location,responsible,status,clean(request.notes()),id)==0) throw new NoSuchElementException("Không tìm thấy tài sản.");
        }
        jdbc.update("INSERT INTO network_asset_assignments(id,asset_id,department,location,responsible_person,changed_by) VALUES(?,?,?,?,?,?)",UUID.randomUUID().toString(),id,department,location,responsible,actor);
        String assetId=id;
        return assets("").stream().filter(asset -> asset.id().equals(assetId)).findFirst().orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<AssetAssignment> assetHistory(String id) {
        return jdbc.query("SELECT id,department,location,responsible_person,valid_from,valid_to,changed_by FROM network_asset_assignments WHERE asset_id=? ORDER BY valid_from DESC LIMIT 100",
                (rs,n)->new AssetAssignment(rs.getString(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getTimestamp(5).toInstant().toString(),rs.getTimestamp(6)==null?null:rs.getTimestamp(6).toInstant().toString(),rs.getString(7)),id);
    }

    public record Observed(String source, String ip, String mac, String host, String interfaceName, String status) {}
    static void collect(JsonNode list, String source, List<Observed> out) {
        if (list == null || !list.isArray()) return;
        for (JsonNode item : list) {
            String status = clean(item.path("status").asText(null));
            if ("DHCP".equals(source) && !"bound".equalsIgnoreCase(status)) continue;
            String ip = clean(item.path("address").asText(null));
            if (ip == null) ip = clean(item.path("active-address").asText(null));
            String mac = clean(item.path("mac-address").asText(null));
            if (!validIpv4(ip)) continue;
            if (mac != null && !mac.matches("(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}")) mac = null;
            if (mac == null || out.size() >= 2000) continue;
            out.add(new Observed(source,ip,mac,clean(item.path("host-name").asText(null)),clean(item.path("interface").asText(null)),status));
        }
    }
    private boolean routerConfigured() { try { return "https".equalsIgnoreCase(URI.create(properties.routerBaseUrl()).getScheme()) && properties.routerUsername()!=null && !properties.routerUsername().isBlank() && properties.routerPassword()!=null && !properties.routerPassword().isBlank(); } catch (Exception e) { return false; } }
    private String routerIp() { try { return URI.create(properties.routerBaseUrl()).getHost(); } catch (Exception e) { return ""; } }
    private org.springframework.http.client.ClientHttpRequestFactory requestFactory() { var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()); factory.setReadTimeout(Duration.ofSeconds(8)); return factory; }
    private String like(String value) { return value==null||value.isBlank()?"%%":"%"+value.trim()+"%"; }
    private String clean(String value) { return value==null||value.isBlank()?null:value.trim(); }
    private List<String> csv(String value) { return value==null?List.of():Arrays.asList(value.split(",")); }
    private static boolean validIpv4(String value) {
        if (value == null) return false;
        String[] parts = value.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            try { int octet = Integer.parseInt(part); if (octet < 0 || octet > 255) return false; }
            catch (NumberFormatException exception) { return false; }
        }
        return true;
    }
}
