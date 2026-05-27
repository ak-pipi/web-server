package com.niuma.admin.controller;

import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.controller.BaseController;
import com.niuma.admin.dto.SettleMessageDTO;
import com.niuma.admin.dto.SettleQueryDTO;
import com.niuma.admin.service.ISettleService;
import com.niuma.common.page.PageResult;
import com.niuma.admin.entity.GameRound;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 后台结算管理 Controller
 * <p>
 * 提供结算记录查询、详情查看、手动补单、统计等管理功能。
 * 正常结算流程由 MQ 消费器 GameSettleConsumer 自动驱动。
 */
@RestController
@RequestMapping("/admin/settle")
@Tag(name = "后台结算管理")
@Slf4j
public class AdminSettleController extends BaseController {

    @Autowired
    private ISettleService settleService;

    /**
     * 分页查询结算记录
     */
    @Operation(summary = "查询结算记录列表")
    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('admin:settle:list')")
    public PageResult<GameRound> list(SettleQueryDTO dto) {
        return settleService.querySettlements(dto);
    }

    /**
     * 查询结算详情（含玩家明细、回放信息、房间快照）
     */
    @Operation(summary = "查询结算详情")
    @GetMapping("/detail/{roundId}")
    @PreAuthorize("@ss.hasPermi('admin:settle:query')")
    public AjaxResult detail(@PathVariable Long roundId) {
        return settleService.getSettlementDetail(roundId);
    }

    /**
     * 查询某房间的所有结算记录
     */
    @Operation(summary = "查询房间结算历史")
    @GetMapping("/room/{roomId}")
    @PreAuthorize("@ss.hasPermi('admin:settle:query')")
    public AjaxResult roomSettlements(@PathVariable String roomId) {
        return settleService.getRoomSettlements(roomId);
    }

    /**
     * 结算统计（按时间维度聚合）
     */
    @Operation(summary = "结算统计数据")
    @GetMapping("/stats")
    @PreAuthorize("@ss.hasPermi('admin:settle:list')")
    public AjaxResult stats(
            @RequestParam(required = false) Long gameId,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {
        return settleService.getSettlementStats(gameId, startDate, endDate);
    }

    /**
     * 手动触发补单（异常恢复/重新处理）
     * <p>
     * 用于以下场景：
     * - MQ 消息丢失后的数据修复
     * - 死信队列(DLQ)中的消息手动重放
     * - 测试环境模拟结算
     */
    @Operation(summary = "手动补单(仅管理员)")
    @PostMapping("/reprocess")
    @PreAuthorize("@ss.hasPermi('admin:settle:reprocess')")
    public AjaxResult reprocess(@RequestBody SettleMessageDTO dto) {
        String operator = getUsername();
        log.info("[结算-手动] 管理员触发补单: operator={}, messageId={}",
                operator, dto.getMessageId());
        return settleService.reprocessSettlement(dto, operator);
    }
}
