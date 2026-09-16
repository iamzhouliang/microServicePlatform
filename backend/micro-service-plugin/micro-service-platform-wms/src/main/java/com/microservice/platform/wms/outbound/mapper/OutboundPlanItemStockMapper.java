package com.microservice.platform.wms.outbound.mapper;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.wms.outbound.domain.entity.OutboundPlanItemStock;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 出库行项库存余额明细行 Mapper 接口
 * </p>
 *
 * @author ddCat
 * @since 2024-08-07
 */
@Repository
public interface OutboundPlanItemStockMapper extends SuperMapper<OutboundPlanItemStock> {

}
