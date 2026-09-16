package com.microservice.platform.wms.outbound.mapper;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.wms.outbound.domain.entity.OutboundPlanItem;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 出库单行项 Mapper 接口
 * </p>
 *
 * @author ddCat
 * @since 2024-07-20
 */
@Repository
public interface OutboundPlanItemMapper extends SuperMapper<OutboundPlanItem> {

    void removeByPlanId(Long id);
}
