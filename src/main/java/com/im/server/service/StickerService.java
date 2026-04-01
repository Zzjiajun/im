package com.im.server.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.im.server.common.BusinessException;
import com.im.server.mapper.StickerItemMapper;
import com.im.server.mapper.StickerPackMapper;
import com.im.server.model.dto.CreateStickerItemRequest;
import com.im.server.model.dto.CreateStickerPackRequest;
import com.im.server.model.entity.StickerItem;
import com.im.server.model.entity.StickerPack;
import com.im.server.model.vo.StickerPackDetailVO;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class StickerService {

    private final StickerPackMapper stickerPackMapper;
    private final StickerItemMapper stickerItemMapper;

    public List<StickerPackDetailVO> listEnabledPacks() {
        List<StickerPack> packs = stickerPackMapper.selectList(
            new LambdaQueryWrapper<StickerPack>()
                .eq(StickerPack::getStatus, 1)
                .orderByAsc(StickerPack::getSortOrder)
                .orderByAsc(StickerPack::getId)
        );
        List<StickerPackDetailVO> result = new ArrayList<>();
        for (StickerPack pack : packs) {
            result.add(buildDetail(pack));
        }
        return result;
    }

    @Transactional
    public StickerPackDetailVO createPack(CreateStickerPackRequest request) {
        long exists = stickerPackMapper.selectCount(
            new LambdaQueryWrapper<StickerPack>().eq(StickerPack::getCode, request.getCode())
        );
        if (exists > 0) {
            throw new BusinessException("表情包编码已存在");
        }
        StickerPack pack = new StickerPack();
        pack.setCode(StringUtils.trimToEmpty(request.getCode()));
        pack.setName(StringUtils.trimToEmpty(request.getName()));
        pack.setCoverUrl(request.getCoverUrl());
        pack.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        pack.setStatus(1);
        pack.setCreatedAt(LocalDateTime.now());
        stickerPackMapper.insert(pack);
        return buildDetail(pack);
    }

    @Transactional
    public void addItem(CreateStickerItemRequest request) {
        StickerPack pack = stickerPackMapper.selectById(request.getPackId());
        if (pack == null) {
            throw new BusinessException("表情包不存在");
        }
        StickerItem item = new StickerItem();
        item.setPackId(request.getPackId());
        item.setCode(StringUtils.trimToEmpty(request.getCode()));
        item.setImageUrl(StringUtils.trimToEmpty(request.getImageUrl()));
        item.setSortOrder(request.getSortOrder() == null ? 0 : request.getSortOrder());
        item.setCreatedAt(LocalDateTime.now());
        stickerItemMapper.insert(item);
    }

    private StickerPackDetailVO buildDetail(StickerPack pack) {
        List<StickerItem> items = stickerItemMapper.selectList(
            new LambdaQueryWrapper<StickerItem>()
                .eq(StickerItem::getPackId, pack.getId())
                .orderByAsc(StickerItem::getSortOrder)
                .orderByAsc(StickerItem::getId)
        );
        List<StickerPackDetailVO.StickerItemVO> itemVos = new ArrayList<>();
        for (StickerItem it : items) {
            itemVos.add(StickerPackDetailVO.StickerItemVO.builder()
                .itemId(it.getId())
                .code(it.getCode())
                .imageUrl(it.getImageUrl())
                .sortOrder(it.getSortOrder())
                .build());
        }
        return StickerPackDetailVO.builder()
            .packId(pack.getId())
            .code(pack.getCode())
            .name(pack.getName())
            .coverUrl(pack.getCoverUrl())
            .sortOrder(pack.getSortOrder())
            .items(itemVos)
            .build();
    }
}
