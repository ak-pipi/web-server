package com.niuma.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.niuma.admin.dto.AgencyMemberDTO;
import com.niuma.admin.dto.AgencyMemberQueryDTO;
import com.niuma.admin.dto.AgencyMatchGiftStatsDTO;
import com.niuma.admin.dto.AgencyMatchLedgerDTO;
import com.niuma.admin.dto.AgencyMatchOperationLogDTO;
import com.niuma.admin.dto.AgencyMatchPlayerDTO;
import com.niuma.admin.dto.AgencyMatchQueryDTO;
import com.niuma.admin.dto.AgencyStatsDTO;
import com.niuma.admin.dto.AgencyStatsQueryDTO;
import com.niuma.admin.dto.AgencyPlayLimitDTO;
import com.niuma.admin.dto.CollectRecordDTO;
import com.niuma.admin.dto.JuniorPlayerDTO;
import com.niuma.admin.dto.RewardDTO;
import com.niuma.admin.dto.WalletAdjustDTO;
import com.niuma.admin.entity.Agency;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;

/**
 * 代理相关服务接口
 * @author wujian
 * @email 393817707@qq.com
 * @date 2024.11.15
 */
public interface IAgencyService extends IService<Agency> {
    AjaxResult getAgency();

    PageResult<JuniorPlayerDTO> getJuniorPlayers(PageBody dto);

    PageResult<AgencyMemberDTO> getMemberPage(AgencyMemberQueryDTO dto);

    AjaxResult setMemberAgent(String playerId, Integer commissionRateBp);

    AjaxResult removeMember(String playerId, String reason);

    AjaxResult demoteMember(String playerId, String reason);

    AjaxResult updateMemberGameStatus(String playerId, Integer banned, String reason);

    AjaxResult updateMemberRemark(String playerId, String remark);

    AjaxResult getPlayLimit(String playerId);

    AjaxResult updatePlayLimit(AgencyPlayLimitDTO dto);

    PageResult<AgencyStatsDTO> getStatsPage(AgencyStatsQueryDTO dto);

    PageResult<AgencyMatchPlayerDTO> getMatchPlayerPage(AgencyMatchQueryDTO dto);

    PageResult<AgencyMatchLedgerDTO> getMatchLedgerPage(AgencyMatchQueryDTO dto);

    PageResult<AgencyMatchOperationLogDTO> getMatchOperationLogPage(AgencyMatchQueryDTO dto);

    PageResult<AgencyMatchGiftStatsDTO> getMatchGiftStatsPage(AgencyMatchQueryDTO dto);

    AjaxResult adjustMatchScore(WalletAdjustDTO dto);

    AjaxResult incomeBox();

    AjaxResult withdrawIncomeBox(Long amount);

    PageResult<RewardDTO> getRewards(PageBody dto);

    AjaxResult collect();

    PageResult<CollectRecordDTO> collectRecord(PageBody dto);
}
