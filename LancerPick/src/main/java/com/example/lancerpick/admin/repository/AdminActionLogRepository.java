package com.example.lancerpick.admin.repository;

import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.entity.AdminActionLog;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AdminActionLogRepository extends JpaRepository<AdminActionLog, Long> {
  Optional<AdminActionLog> findByAdminIdAndRequestKey(long adminId, String requestKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select a from AdminActionLog a where a.action=:action and a.targetId=:targetId"
          + " and a.status=com.example.lancerpick.admin.entity.AdminActionStatus.REQUESTED")
  Optional<AdminActionLog> findPendingForUpdate(
      @Param("action") AdminAction action, @Param("targetId") long targetId);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select a from AdminActionLog a where a.id=:id")
  Optional<AdminActionLog> findByIdForUpdate(@Param("id") long id);
}
