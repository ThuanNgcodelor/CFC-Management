package com.booking.system.health.repository;

import com.booking.system.health.entity.HealthAuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HealthAuditRepository extends JpaRepository<HealthAuditEvent, String> { }
