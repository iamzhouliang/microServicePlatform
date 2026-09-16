package com.microservice.platform.wms.matedata.mapper;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.wms.matedata.domain.entity.Carrier;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 承运商 Mapper 接口
 * </p>
 *
 * @author ddCat
 * @since 2024-07-14
 */
@Repository
public interface CarrierMapper extends SuperMapper<Carrier> {

}
