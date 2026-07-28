package com.niuma.admin.rabbit;

import com.niuma.admin.constant.NiuMaRedisKeys;
import com.niuma.admin.data.MqMessage;
import com.niuma.admin.data.MqWalletSync;
import com.niuma.admin.entity.Capital;
import com.niuma.admin.mapper.CapitalMapper;
import com.niuma.admin.utils.JsonUtils;
import com.niuma.common.core.redis.RedisPrimitive;
import com.niuma.common.utils.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * 钱包变动后向玩家当前所在游戏服发送同步消息。
 */
@Component
@Slf4j
public class WalletSyncPublisher {

    @Autowired
    private RedisPrimitive redisPrimitive;

    @Autowired
    private RabbitSender rabbitSender;

    @Autowired
    private JsonUtils jsonUtils;

    @Autowired
    private CapitalMapper capitalMapper;

    @Value("${rabbitmq.game.exchange}")
    private String gameExchange;

    public void publishAfterCommit(String playerId, String walletType, Long signedChangeAmount,
                                   Long balanceAfter, String bizType, String bizId, Long walletLedgerId) {
        Runnable sender = () -> {
            try {
                publishNow(playerId, walletType, signedChangeAmount,
                        balanceAfter, bizType, bizId, walletLedgerId);
            } catch (Exception ex) {
                log.error("[钱包同步] 玩家 {} 同步消息发送失败: {}", playerId, ex.getMessage(), ex);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    sender.run();
                }
            });
        } else {
            sender.run();
        }
    }

    private void publishNow(String playerId, String walletType, Long signedChangeAmount,
                            Long balanceAfter, String bizType, String bizId, Long walletLedgerId) {
        if (StringUtils.isEmpty(playerId))
            return;
        String venueId = redisPrimitive.get(NiuMaRedisKeys.PLAYER_CURRENT_VENUE + playerId);
        if (StringUtils.isEmpty(venueId))
            return;
        String routingKey = redisPrimitive.get(NiuMaRedisKeys.VENUE_SERVER_MAP + venueId);
        if (StringUtils.isEmpty(routingKey)) {
            log.warn("[钱包同步] 玩家 {} 当前场地 {} 缺少游戏服路由", playerId, venueId);
            return;
        }

        Capital capital = capitalMapper.selectById(playerId);
        MqWalletSync sync = new MqWalletSync();
        sync.setPlayerId(playerId);
        sync.setWalletType(walletType);
        sync.setChangeAmount(signedChangeAmount);
        sync.setBalanceAfter(balanceAfter);
        sync.setBizType(bizType);
        sync.setBizId(bizId);
        sync.setWalletLedgerId(walletLedgerId);
        if (capital != null) {
            sync.setGold(capital.getGold() != null ? capital.getGold() : 0L);
            sync.setDeposit(capital.getDeposit() != null ? capital.getDeposit() : 0L);
            sync.setDiamond(capital.getDiamond() != null ? capital.getDiamond() : 0L);
        }

        String json = jsonUtils.convertToStr(sync);
        MqMessage msg = new MqMessage();
        msg.setMsgType("MsgPlayerWalletSync");
        msg.setMsgPack(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
        rabbitSender.sendObject(gameExchange, routingKey, msg);
        log.info("[钱包同步] 玩家 {} 余额变动已发送到游戏服 {}", playerId, routingKey);
    }
}
