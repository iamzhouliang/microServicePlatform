package com.microservice.platform.wms.inbound.service.impl;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.microservice.framework.commons.BeanUtilPlus;
import com.microservice.framework.commons.MapHelper;
import com.microservice.framework.commons.exception.CheckedException;
import com.microservice.framework.commons.security.AuthenticationContext;
import com.microservice.framework.db.mybatisplus.ext.SuperServiceImpl;
import com.microservice.framework.db.mybatisplus.wrap.Wraps;
import com.microservice.framework.db.mybatisplus.wrap.query.LbqWrapper;
import com.microservice.framework.redis.plus.sequence.RedisSequenceHelper;
import com.microservice.platform.wms.basic.domain.req.ContainerOccupyReleaseReq;
import com.microservice.platform.wms.basic.service.ContainerService;
import com.microservice.platform.wms.inbound.domain.dto.ReceiptQtyDTO;
import com.microservice.platform.wms.inbound.domain.entity.InventoryReceipt;
import com.microservice.platform.wms.inbound.domain.entity.InventoryReceiptItem;
import com.microservice.platform.wms.inbound.domain.entity.ReceivingPlan;
import com.microservice.platform.wms.inbound.domain.enums.*;
import com.microservice.platform.wms.inbound.domain.req.InventoryReceiptItemSaveReq;
import com.microservice.platform.wms.inbound.domain.req.InventoryReceiptSaveReq;
import com.microservice.platform.wms.inbound.domain.req.InventoryReceiptSubmitReq;
import com.microservice.platform.wms.inbound.domain.resp.InventoryReceiptDetailResp;
import com.microservice.platform.wms.inbound.domain.resp.InventoryReceiptItemPageResp;
import com.microservice.platform.wms.inbound.mapper.InventoryReceiptItemMapper;
import com.microservice.platform.wms.inbound.mapper.InventoryReceiptMapper;
import com.microservice.platform.wms.inbound.mapper.ReceivingPlanMapper;
import com.microservice.platform.wms.inbound.service.InventoryReceiptService;
import com.microservice.platform.wms.stock.domain.req.StockSaveReq;
import com.microservice.platform.wms.stock.service.StockService;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * <p>
 * 入库单 服务类
 * </p>
 *
 * @author ddCat
 * @since 2024-06-27
 */
@Service
@AllArgsConstructor
public class InventoryReceiptServiceImpl extends SuperServiceImpl<InventoryReceiptMapper, InventoryReceipt> implements InventoryReceiptService {

    private final InventoryReceiptItemMapper inventoryReceiptItemMapper;
    private final ContainerService containerService;
    private final ReceivingPlanMapper receivingPlanMapper;
    private final StockService stockService;
    private final RedisSequenceHelper sequenceHelper;
    private final AuthenticationContext authenticationContext;
    private final AuthenticationContext context;

    @Override
    @Transactional
    public void saveOrUpdateInventoryReceipt(Long id, InventoryReceiptSaveReq req) {
        if (ObjectUtils.isEmpty(req.getItems())) {
            throw CheckedException.badRequest("明细行不能为空");
        }
        InventoryReceipt oldInventoryReceipt = new InventoryReceipt();
        if (id != null) {
            oldInventoryReceipt = this.getById(id);
            if (InventoryReceiptStatus.DRAFT != oldInventoryReceipt.getStatus()) {
                // 草稿才可以编辑
                throw CheckedException.badRequest("草稿才可编辑");
            }
            // 编辑入库单时只允许改明细和备注等草稿内容，计划关联、单号和状态沿用原单，避免跨计划篡改。
            LbqWrapper<InventoryReceiptItem> lbqWrapper = Wraps.<InventoryReceiptItem>lbQ().eq(InventoryReceiptItem::getReceiptId, id);
            inventoryReceiptItemMapper.delete(lbqWrapper);
        }
        InventoryReceipt inventoryReceipt = InventoryReceipt.builder()
                .planId(req.getPlanId())
                .supplierId(req.getSupplierId())
                .warehouseId(req.getWarehouseId())
                .remark(req.getRemark())
                .planNum(req.getPlanNum())
                .build();
        if (id != null) {
            inventoryReceipt.setId(oldInventoryReceipt.getId());
            inventoryReceipt.setStatus(oldInventoryReceipt.getStatus());
            inventoryReceipt.setReceiptNum(oldInventoryReceipt.getReceiptNum());
            inventoryReceipt.setPlanId(oldInventoryReceipt.getPlanId());
            inventoryReceipt.setPlanNum(oldInventoryReceipt.getPlanNum());
        } else {
            inventoryReceipt.setStatus(InventoryReceiptStatus.DRAFT);
            inventoryReceipt.setReceiptNum(sequenceHelper.generate(WmsSequence.INVENTORY_RECEIPT, authenticationContext.tenantId()));
        }
        this.saveOrUpdate(inventoryReceipt);
        id = inventoryReceipt.getId();
        // 删除明细行项旧数据
        inventoryReceiptItemMapper.delete(Wraps.<InventoryReceiptItem>lbQ().eq(InventoryReceiptItem::getReceiptId, id));
        List<InventoryReceiptItem> items = new ArrayList<>();
        for (InventoryReceiptItemSaveReq item : req.getItems()) {
            // TODO 计量单位转换待完成
            // 当前先假设收货单位等于库存单位；接入单位换算后，qty/unit 应保存为库存基准单位。
            BigDecimal qty = item.getReceivingQty();
            String unit = item.getReceivingUnit();
            InventoryReceiptItem inventoryReceiptItem = InventoryReceiptItem.builder()
                    .receiptId(id)
                    .planItemId(item.getPlanItemId())
                    .materialId(item.getMaterialId())
                    .locationId(item.getLocationId())
                    .batchNum(item.getBatchNum())
                    .receivingQty(item.getReceivingQty())
                    .receivingUnit(item.getReceivingUnit())
                    .qty(qty)
                    .unit(unit)
                    .remark(item.getRemark())
                    .expiryDate(item.getExpiryDate())
                    .productionDate(item.getProductionDate())
                    .build();
            items.add(inventoryReceiptItem);
        }
        inventoryReceiptItemMapper.insertBatch(items);
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public void removeInventoryReceipt(Long id) {
        InventoryReceipt inventoryReceipt = this.getById(id);
        if (InventoryReceiptStatus.DRAFT != inventoryReceipt.getStatus()) {
            throw CheckedException.badRequest("草稿的入库单才可删除");
        }
        LbqWrapper<InventoryReceiptItem> lbqWrapper = Wraps.<InventoryReceiptItem>lbQ().eq(InventoryReceiptItem::getReceiptId, id);
        this.inventoryReceiptItemMapper.delete(lbqWrapper);
        this.removeById(id);
        // 返还数量
        // TODO 当前删除后再查询同一批明细会为空，后续返还计划数量时应先缓存删除前的明细快照。
        List<InventoryReceiptItem> inventoryReceiptItems = this.inventoryReceiptItemMapper.selectList(lbqWrapper);
        Map<Long, InventoryReceiptItem> inventoryReceiptItemMap = inventoryReceiptItems.stream()
                .collect(Collectors.toMap(InventoryReceiptItem::getPlanItemId, inventoryReceiptItem -> inventoryReceiptItem));
        // 判断收货计划是否全部完成
        List<ReceiptQtyDTO> receiptQtys = inventoryReceiptItemMapper.selectReceiptQtyByPlanId(inventoryReceipt.getPlanId());
        boolean complete = true;
        for (ReceiptQtyDTO receiptQty : receiptQtys) {
            InventoryReceiptItem inventoryReceiptItem = inventoryReceiptItemMap.get(receiptQty.getPlanItemId());
            if (inventoryReceiptItem == null) {
                complete = false;
                continue;
            }
            // 该明细行 收货进行中的数量 不等于 本次入库单的数量
            if (inventoryReceiptItem.getQty().compareTo(receiptQty.getProgressQty().add(receiptQty.getCompleteQty())) != 0) {
                complete = false;
            }
        }
        if (complete) {
            ReceivingPlan receivingPlan = receivingPlanMapper.selectById(inventoryReceipt.getPlanId());
            receivingPlan.setStatus(ReceivingStatus.WAIT);
            receivingPlanMapper.updateById(receivingPlan);
        }
    }

    @Override
    @DSTransactional(rollbackFor = Exception.class)
    public void submit(Long id, InventoryReceiptSubmitReq req) {
        InventoryReceipt receipt = Optional.ofNullable(this.getById(id)).orElseThrow(() -> CheckedException.notFound("入库单不存在"));
        if (InventoryReceiptStatus.DRAFT != receipt.getStatus()) {
            throw CheckedException.badRequest("草稿才可提交");
        }
        long notConfigLocation = req.getItems().stream()
                .map(InventoryReceiptSubmitReq.InventoryReceiptItemSubmitReq::getLocationId)
                .filter(Objects::isNull).count();
        if (notConfigLocation > 0) {
            throw CheckedException.badRequest("请先配置储位");
        }

        List<InventoryReceiptItem> itemList = req.getItems().stream().map(x -> InventoryReceiptItem.builder()
                .id(x.getId()).locationId(x.getLocationId()).build()).collect(toList());
        this.baseMapper.updateById(InventoryReceipt.builder().id(id).status(InventoryReceiptStatus.COMPLETED).build());
        this.inventoryReceiptItemMapper.updateBatch(itemList);
        List<InventoryReceiptItem> receiptItemList = inventoryReceiptItemMapper.selectList(InventoryReceiptItem::getReceiptId, id);
        Map<Long, InventoryReceiptItem> itemMap = MapHelper.toHashMap(receiptItemList, InventoryReceiptItem::getPlanItemId, x -> x);
        // 判断收货计划是否全部完成
        // 完成判断以计划维度聚合数量为准：所有计划明细的进行中数量都被当前入库单确认后，计划才完成。
        List<ReceiptQtyDTO> receiptQtyList = inventoryReceiptItemMapper.selectReceiptQtyByPlanId(receipt.getPlanId());
        boolean complete = receiptQtyList.stream()
                .allMatch(receiptQty -> {
                    InventoryReceiptItem inventoryReceiptItem = itemMap.get(receiptQty.getPlanItemId());
                    return inventoryReceiptItem != null &&
                            // 该明细行 收货进行中的数量 等于 本次入库单的数量 则认为完成
                            inventoryReceiptItem.getQty().compareTo(receiptQty.getProgressQty()) == 0;
                });
        if (complete) {
            ReceivingPlan receivingPlan = receivingPlanMapper.selectById(receipt.getPlanId());
            // 释放容器
            // 容器占用与收货计划生命周期绑定，计划完成后释放给后续作业使用。
            ContainerOccupyReleaseReq releaseReq = ContainerOccupyReleaseReq.builder()
                    .containerId(receivingPlan.getContainerId()).docId(receivingPlan.getId())
                    .status(ContainerStatus.RELEASE).occupationTaskType(ContainerTaskType.RECEIVING_PLAN).build();
            containerService.occupyOrRelease(List.of(releaseReq));
            receivingPlanMapper.updateById(ReceivingPlan.builder().id(receivingPlan.getId()).status(ReceivingStatus.COMPLETED).build());
        }
        // 上架库存操作
        List<StockSaveReq> stocks = receiptItemList.stream().map(item -> StockSaveReq.builder()
                .warehouseId(receipt.getWarehouseId()).docId(receipt.getId()).docItemId(item.getId())
                .locationId(item.getLocationId())
                .materialId(item.getMaterialId())
                .batchNum(item.getBatchNum())
                .productionDate(item.getProductionDate())
                .expiryDate(item.getExpiryDate())
                .qty(item.getQty()).unit(item.getUnit())
                .level(StockLevelStatus.GOODS)
                .changeType(StockChangeType.IN)
                .remarks(item.getRemark())
                .build()).collect(toList());
        stockService.saveStocks(stocks);
    }

    @Override
    public InventoryReceiptDetailResp detail(Long id) {
        InventoryReceipt receipt = this.baseMapper.selectById(id);
        if (receipt == null) {
            throw CheckedException.notFound("入库单不存在");
        }
        InventoryReceiptDetailResp result = BeanUtilPlus.toBean(receipt, InventoryReceiptDetailResp.class);
        List<InventoryReceiptItem> receiptItemList = this.inventoryReceiptItemMapper.selectList(InventoryReceiptItem::getReceiptId, id);
        List<InventoryReceiptItemPageResp> items = BeanUtilPlus.toBeans(receiptItemList, InventoryReceiptItemPageResp.class);
        result.setItems(items);
        return result;
    }


}
