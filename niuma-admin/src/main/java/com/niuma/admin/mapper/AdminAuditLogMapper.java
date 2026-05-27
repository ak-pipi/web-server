package com.niuma.admin.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.niuma.admin.entity.AdminAuditLog;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 后台审计日志 Mapper
 */
@Repository
public interface AdminAuditLogMapper extends BaseMapper<AdminAuditLog> {
}
