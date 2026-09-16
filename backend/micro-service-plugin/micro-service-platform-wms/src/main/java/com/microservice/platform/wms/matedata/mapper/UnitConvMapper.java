package com.microservice.platform.wms.matedata.mapper;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.wms.matedata.domain.entity.UnitConv;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 基本计量单位转换表 Mapper 接口
 * </p>
 *
 * @author ddCat
 * @since 2024-06-24
 */
@Repository
public interface UnitConvMapper extends SuperMapper<UnitConv> {

}
