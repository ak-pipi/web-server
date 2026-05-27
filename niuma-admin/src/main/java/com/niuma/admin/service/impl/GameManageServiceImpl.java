package com.niuma.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.niuma.admin.dto.GameManageDTO;
import com.niuma.admin.entity.Game;
import com.niuma.admin.mapper.GameMapper;
import com.niuma.admin.service.IGameManageService;
import com.niuma.common.core.domain.AjaxResult;
import com.niuma.common.exception.http.BadRequestException;
import com.niuma.common.exception.http.NotFoundException;
import com.niuma.common.page.PageResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 游戏管理服务实现
 */
@Service
@Slf4j
public class GameManageServiceImpl extends ServiceImpl<GameMapper, Game> implements IGameManageService {

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult create(GameManageDTO dto) {
        // 检查编码是否重复
        LambdaQueryWrapper<Game> codeCheck = Wrappers.lambdaQuery(Game.class);
        codeCheck.eq(Game::getCode, dto.getCode());
        if (this.count(codeCheck) > 0) {
            throw new BadRequestException("游戏编码已存在: " + dto.getCode());
        }

        Game game = new Game();
        game.setCode(dto.getCode());
        game.setName(dto.getName());
        game.setType(dto.getType() != null ? dto.getType() : "mahjong");
        game.setPluginVersion(dto.getPluginVersion());
        game.setSortOrder(dto.getSortOrder() != null ? dto.getSortOrder() : 0);
        game.setIconUrl(dto.getIconUrl());
        game.setDescription(dto.getDescription());
        game.setStatus(0);  // 默认下架
        game.setCreateTime(LocalDateTime.now());
        this.save(game);

        log.info("[游戏管理] 创建游戏: id={}, name={}", game.getId(), game.getName());
        AjaxResult result = AjaxResult.successEx();
        result.put("gameId", game.getId());
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult update(GameManageDTO dto) {
        if (dto.getId() == null) {
            throw new BadRequestException("更新时游戏ID不能为空");
        }
        Game game = this.getById(dto.getId());
        if (game == null) {
            throw new NotFoundException("游戏不存在");
        }

        // 检查编码冲突（排除自身）
        if (dto.getCode() != null && !dto.getCode().equals(game.getCode())) {
            LambdaQueryWrapper<Game> codeCheck = Wrappers.lambdaQuery(Game.class);
            codeCheck.eq(Game::getCode, dto.getCode()).ne(Game::getId, dto.getId());
            if (this.count(codeCheck) > 0) {
                throw new BadRequestException("游戏编码已存在: " + dto.getCode());
            }
            game.setCode(dto.getCode());
        }

        game.setName(dto.getName());
        if (dto.getType() != null) game.setType(dto.getType());
        if (dto.getPluginVersion() != null) game.setPluginVersion(dto.getPluginVersion());
        if (dto.getSortOrder() != null) game.setSortOrder(dto.getSortOrder());
        if (dto.getIconUrl() != null) game.setIconUrl(dto.getIconUrl());
        if (dto.getDescription() != null) game.setDescription(dto.getDescription());
        this.updateById(game);

        log.info("[游戏管理] 更新游戏: id={}, name={}", game.getId(), game.getName());
        return AjaxResult.successEx();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult delete(Long id) {
        Game game = this.getById(id);
        if (game == null) {
            throw new NotFoundException("游戏不存在");
        }
        if (game.getStatus() == 1) {
            throw new BadRequestException("请先下架游戏再删除");
        }
        this.removeById(id);
        log.info("[游戏管理] 删除游戏: id={}", id);
        return AjaxResult.successEx();
    }

    @Override
    public AjaxResult getDetail(Long id) {
        Game game = this.getById(id);
        if (game == null) {
            throw new NotFoundException("游戏不存在");
        }
        return AjaxResult.successEx(game);
    }

    @Override
    public PageResult<Game> pageList(String keyword, String type, Integer status, int pageNum, int pageSize) {
        LambdaQueryWrapper<Game> wrapper = Wrappers.lambdaQuery(Game.class);
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(Game::getName, keyword).or().like(Game::getCode, keyword));
        }
        if (type != null && !type.isEmpty()) {
            wrapper.eq(Game::getType, type);
        }
        if (status != null) {
            wrapper.eq(Game::getStatus, status);
        }
        wrapper.orderByAsc(Game::getSortOrder).orderByDesc(Game::getId);
        Page<Game> page = new Page<>(pageNum, pageSize);
        Page<Game> result = this.baseMapper.selectPage(page, wrapper);
        return new PageResult<>(result.getRecords(), (int) result.getCurrent(), (int) result.getTotal());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult online(Long id) {
        Game game = this.getById(id);
        if (game == null) {
            throw new NotFoundException("游戏不存在");
        }
        if (game.getStatus() == 1) {
            throw new BadRequestException("游戏已是上架状态");
        }
        game.setStatus(1);
        this.updateById(game);
        log.info("[游戏管理] 上架游戏: id={}, name={}", id, game.getName());
        return AjaxResult.successEx();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult offline(Long id) {
        Game game = this.getById(id);
        if (game == null) {
            throw new NotFoundException("游戏不存在");
        }
        if (game.getStatus() == 0) {
            throw new BadRequestException("游戏已是下架状态");
        }
        game.setStatus(0);
        this.updateById(game);
        log.info("[游戏管理] 下架游戏: id={}, name={}", id, game.getName());
        return AjaxResult.successEx();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult setMaintenance(Long id, String reason) {
        Game game = this.getById(id);
        if (game == null) {
            throw new NotFoundException("游戏不存在");
        }
        game.setStatus(2);  // 维护状态
        this.updateById(game);
        log.info("[游戏管理] 设置维护模式: id={}, name={}, 原因: {}", id, game.getName(), reason);
        
        AjaxResult result = AjaxResult.successEx();
        result.put("msg", "游戏已进入维护模式");
        return result;
    }

    @Override
    public AjaxResult listOnlineGames() {
        LambdaQueryWrapper<Game> wrapper = Wrappers.lambdaQuery(Game.class);
        wrapper.eq(Game::getStatus, 1)
               .orderByAsc(Game::getSortOrder);
        List<Game> games = this.list(wrapper);
        AjaxResult result = AjaxResult.successEx();
        result.put("list", games);
        return result;
    }
}
