package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.constant.NiuMaCodeEnum;
import com.niuma.admin.dto.*;
import com.niuma.admin.entity.*;
import com.niuma.admin.enums.LedgerBizType;
import com.niuma.admin.enums.WalletType;
import com.niuma.admin.mapper.*;
import com.niuma.admin.service.ICapitalService;
import com.niuma.admin.service.IWalletService;
import com.niuma.common.constant.ResultCodeEnum;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.core.domain.model.LoginPlayer;
import com.niuma.common.page.PageBody;
import com.niuma.common.page.PageResult;
import com.niuma.common.exception.http.*;
import com.niuma.common.utils.AesUtil;
import com.niuma.common.utils.CommonUtils;
import com.niuma.common.utils.PlayerSecurityUtils;
import com.niuma.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CapitalServiceImpl extends ServiceImpl<CapitalMapper, Capital> implements ICapitalService {
    private static final int MONEY_SCALE = 1;

    @Autowired
    private ExchangeMapper exchangeMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private TransferMapper transferMapper;

    @Autowired
    private BuyDiamondMapper buyDiamondMapper;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private IWalletService walletService;

    @Override
    public AjaxResult getCapital() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        Capital entity = this.getCapital(player.getId());
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("gold", entity.getGold());
        ajax.put("deposit", entity.getDeposit());
        ajax.put("diamond", entity.getDiamond());
        return ajax;
    }

    @Override
    public Capital getCapital(String playerId) {
        Capital entity = this.baseMapper.selectById(playerId);
        if (entity == null)
            entity = this.initCapital(playerId);
        return entity;
    }

    private void initCapital1(String playerId) {
        LambdaQueryWrapper<Capital> query = Wrappers.lambdaQuery();
        query.eq(Capital::getPlayerId, playerId);
        Integer count = this.baseMapper.selectCount(query);
        if (count == null || count.equals(0))
            this.initCapital(playerId);
    }

    private Capital initCapital(String playerId) {
        Capital entity = new Capital();
        entity.setPlayerId(playerId);
        entity.setGold(money(0L));
        entity.setDeposit(money(0L));
        entity.setDiamond(0L);
        this.baseMapper.insert(entity);
        return entity;
    }

    private BigDecimal money(long value) {
        return BigDecimal.valueOf(value).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    @Override
    public AjaxResult bankPassword(BankPwdDTO dto) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        String oldPassword = AesUtil.decrypt(dto.getOldPassword());
        String newPassword = AesUtil.decrypt(dto.getNewPassword());
        if (StringUtils.isNotEmpty(newPassword))
            newPassword = StringUtils.trim(newPassword);
        if (StringUtils.isEmpty(newPassword))
            throw new BadRequestException(NiuMaCodeEnum.BANK_PASSWORD_ERROR.getCode(), "New password error");
        String bankPassword = this.baseMapper.getBankPassword(player.getId());
        if (StringUtils.isNotEmpty(bankPassword)) {
            if (StringUtils.isEmpty(oldPassword))
                throw new UnauthorizedException(NiuMaCodeEnum.BANK_PASSWORD_ERROR);
            if (!this.bCryptPasswordEncoder.matches(oldPassword, bankPassword))
                throw new UnauthorizedException(NiuMaCodeEnum.BANK_PASSWORD_ERROR);
        }
        initCapital1(player.getId());
        newPassword = this.bCryptPasswordEncoder.encode(newPassword);
        this.baseMapper.updateBankPassword(player.getId(), newPassword);
        return AjaxResult.successEx();
    }

    private void checkPassword(String inputPwd, String bankPwd) {
        if (StringUtils.isEmpty(bankPwd)) {
            if (StringUtils.isNotEmpty(inputPwd))
                throw new ForbiddenException(NiuMaCodeEnum.BANK_PASSWORD_ERROR);
        } else {
            String password = AesUtil.decrypt(inputPwd);
            if (StringUtils.isEmpty(password))
                throw new ForbiddenException(NiuMaCodeEnum.BANK_PASSWORD_ERROR);
            if (!this.bCryptPasswordEncoder.matches(password, bankPwd))
                throw new ForbiddenException(NiuMaCodeEnum.BANK_PASSWORD_ERROR);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult debitOrDeposit(AmountDTO dto, boolean isDebit) {
        Long amount = dto.getAmount();
        if (amount == 0 || amount < 0)
            throw new BadRequestException(NiuMaCodeEnum.CAPITAL_AMOUNT_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        Capital entity = this.getCapital(player.getId());
        if (isDebit) {
            // 取出需要验证密码
            this.checkPassword(dto.getPassword(), entity.getPassword());
        }
        if (isDebit) {
            walletService.decrease(player.getId(), WalletType.DEPOSIT.getCode(), amount,
                    LedgerBizType.SAFE_WITHDRAW.getCode(), null, "保险箱取出");
            walletService.increase(player.getId(), WalletType.GOLD.getCode(), amount,
                    LedgerBizType.SAFE_WITHDRAW.getCode(), null, "保险箱取出");
        } else {
            walletService.decrease(player.getId(), WalletType.GOLD.getCode(), amount,
                    LedgerBizType.SAFE_DEPOSIT.getCode(), null, "存入保险箱");
            walletService.increase(player.getId(), WalletType.DEPOSIT.getCode(), amount,
                    LedgerBizType.SAFE_DEPOSIT.getCode(), null, "存入保险箱");
        }
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("gold", walletService.getBalance(player.getId(), WalletType.GOLD.getCode()));
        ajax.put("deposit", walletService.getBalance(player.getId(), WalletType.DEPOSIT.getCode()));
        return ajax;
    }

    @Override
    public AjaxResult getAccount() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        AjaxResult ajax = AjaxResult.successEx();
        AccountDTO dto = this.baseMapper.getAccount(player.getId());
        if (dto != null) {
            if (StringUtils.isNotEmpty(dto.getAlipayAccount()))
                ajax.put("alipayAccount", dto.getAlipayAccount());
            if (StringUtils.isNotEmpty(dto.getAlipayName()))
                ajax.put("alipayName", dto.getAlipayName());
            if (StringUtils.isNotEmpty(dto.getBankAccount()))
                ajax.put("bankAccount", dto.getBankAccount());
            if (StringUtils.isNotEmpty(dto.getBankName()))
                ajax.put("bankName", dto.getBankName());
        }
        return ajax;
    }

    @Override
    public AjaxResult bindAccount(BindAccountDTO dto) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        Integer type = dto.getType();
        if ((type == null) || !(type.equals(0) || type.equals(1)))
            throw new BadRequestException(NiuMaCodeEnum.ACCOUNT_TYPE_ERROR);
        String bankPassword = this.baseMapper.getBankPassword(player.getId());
        this.checkPassword(dto.getPassword(), bankPassword);
        initCapital1(player.getId());
        AjaxResult ajax = AjaxResult.successEx();
        if (type.equals(0)) {
            this.baseMapper.setAlipayAccount(player.getId(), dto.getAccount(), dto.getName());
            ajax.put("alipayAccount", dto.getAccount());
        }
        else {
            this.baseMapper.setBankAccount(player.getId(), dto.getAccount(), dto.getName());
            ajax.put("bankAccount", dto.getAccount());
        }
        return ajax;
    }

    @Override
    @Transactional
    public AjaxResult exchange(ExchangeDTO dto) {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        Integer type = dto.getType();
        if ((type == null) || !(type.equals(0) || type.equals(1)))
            throw new BadRequestException(NiuMaCodeEnum.ACCOUNT_TYPE_ERROR);
        Capital entity = this.getCapital(player.getId());
        this.checkPassword(dto.getPassword(), entity.getPassword());
        AccountDTO account = this.baseMapper.getAccount(player.getId());
        if (account == null)
            throw new ForbiddenException(NiuMaCodeEnum.ACCOUNT_NOT_BIND);
        if (type.equals(0)) {
            if (StringUtils.isEmpty(account.getAlipayAccount()))
                throw new ForbiddenException(NiuMaCodeEnum.ACCOUNT_NOT_BIND);
        } else if (StringUtils.isEmpty(account.getBankAccount()))
            throw new ForbiddenException(NiuMaCodeEnum.ACCOUNT_NOT_BIND);
        long remainder = dto.getAmount() % 50L;
        if ((remainder != 0L) || (dto.getAmount() < 50L))
            throw new BadRequestException(NiuMaCodeEnum.EXCHANGE_AMOUNT_ERROR);
        walletService.decrease(player.getId(), WalletType.DEPOSIT.getCode(), dto.getAmount(),
                LedgerBizType.WITHDRAW.getCode(), null, "申请提现兑换");
        // 添加兑换记录
        Exchange tmp = new Exchange();
        tmp.setPlayerId(player.getId());
        tmp.setAmount(dto.getAmount());
        tmp.setAccountType(dto.getType());
        if (type.equals(0)) {
            tmp.setAccount(account.getAlipayAccount());
            tmp.setAccountName(account.getAlipayName());
        } else {
            tmp.setAccount(account.getBankAccount());
            tmp.setAccountName(account.getBankName());
        }
        tmp.setStatus(0);
        tmp.setApplyTime(LocalDateTime.now());
        this.exchangeMapper.insert(tmp);
        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("deposit", walletService.getBalance(player.getId(), WalletType.DEPOSIT.getCode()));
        return ajax;
    }

    @Override
    public PageResult<ExchangeRecordDTO> exchangeRecord(PageBody dto) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        PageResult<ExchangeRecordDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        Integer totalNum = this.exchangeMapper.countRecord(player.getId());
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<ExchangeRecordDTO> records = this.exchangeMapper.getPage(player.getId(), offset, dto.getPageSize());
        result.setRecords(records);
        return result;
    }

    @Override
    @Transactional
    public AjaxResult transfer(TransferDTO dto) {
        if (dto.getAmount() < 1)
            throw new BadRequestException(NiuMaCodeEnum.CAPITAL_AMOUNT_ERROR.getCode(), "Transfer amount must be greater than 0");
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        if (dto.getPlayerId().equals(player.getId()))
            throw new ForbiddenException(NiuMaCodeEnum.TRANSFER_ERROR.getCode(), "Can't transfer to yourself");
        Capital capital1 = this.getCapital(player.getId());
        this.checkPassword(dto.getPassword(), capital1.getPassword());
        Player dstPlayer = this.playerMapper.selectById(dto.getPlayerId());
        if (dstPlayer == null)
            throw new NotFoundException(NiuMaCodeEnum.PLAYER_NOT_EXIST);
        if (CommonUtils.predicate(dstPlayer.getBanned()) || CommonUtils.predicate(dstPlayer.getDelFlag()))
            throw new NotFoundException(NiuMaCodeEnum.PLAYER_STATUS_ERROR);
        walletService.decrease(player.getId(), WalletType.DEPOSIT.getCode(), dto.getAmount(),
                LedgerBizType.TRANSFER_OUT.getCode(), null, "转账给玩家:" + dto.getPlayerId());
        walletService.increase(dto.getPlayerId(), WalletType.DEPOSIT.getCode(), dto.getAmount(),
                LedgerBizType.TRANSFER_IN.getCode(), null, "收到玩家转账:" + player.getId());
        // 添加转账记录
        Transfer tmp = new Transfer();
        tmp.setSrcPlayerId(player.getId());
        tmp.setDstPlayerId(dto.getPlayerId());
        tmp.setAmount(dto.getAmount());
        tmp.setTime(LocalDateTime.now());
        this.transferMapper.insert(tmp);

        AjaxResult ajax = AjaxResult.successEx();
        ajax.put("deposit", walletService.getBalance(player.getId(), WalletType.DEPOSIT.getCode()));
        ajax.put("dstDeposit", walletService.getBalance(dto.getPlayerId(), WalletType.DEPOSIT.getCode()));
        Long accAmount = this.transferMapper.getAccAmount(player.getId());
        if (accAmount == null)
            accAmount = 0L;
        ajax.put("accAmount", accAmount);
        return ajax;
    }

    @Override
    public PageResult<TransferRecordDTO> transferRecord(PageBody dto) {
        if (dto.getPageNum() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_NUM_ERROR);
        if (dto.getPageSize() < 1)
            throw new BadRequestException(ResultCodeEnum.PAGE_SIZE_ERROR);
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        PageResult<TransferRecordDTO> result = new PageResult<>();
        result.setCodeEnum(ResultCodeEnum.SUCCESS);
        result.setPageNum(dto.getPageNum());
        Integer totalNum = this.transferMapper.countRecord(player.getId());
        Integer offset = (dto.getPageNum() - 1) * dto.getPageSize();
        result.setTotal(totalNum);
        if (offset >= totalNum)
            return result;
        List<TransferRecordDTO> records = this.transferMapper.getPage(player.getId(), offset, dto.getPageSize());
        if (records != null) {
            String nickname = null;
            Map<String, String> tmpMap = new HashMap<>();
            for (TransferRecordDTO record : records) {
                if (tmpMap.containsKey(record.getDstPlayerId()))
                    nickname = tmpMap.get(record.getDstPlayerId());
                else {
                    nickname = this.playerMapper.getNickname(record.getDstPlayerId());
                    tmpMap.put(record.getDstPlayerId(), nickname);
                }
                record.setDstNickname(nickname);
            }
        }
        result.setRecords(records);
        return result;
    }

    @Override
    public AjaxResult transferAcc() {
        LoginPlayer player = PlayerSecurityUtils.getLoginPlayer();
        if (player == null)
            throw new InternalServerException("Current login player is null, this is unexpected");
        AjaxResult ajax = AjaxResult.successEx();
        Long accAmount = this.transferMapper.getAccAmount(player.getId());
        if (accAmount == null)
            accAmount = 0L;
        ajax.put("accAmount", accAmount);
        return ajax;
    }

    @Override
    @Transactional
    public AjaxResult buyDiamond(Integer index) {
        throw new BadRequestException(ResultCodeEnum.BAD_REQUEST.getCode(), "钻石功能已下线");
    }
}
