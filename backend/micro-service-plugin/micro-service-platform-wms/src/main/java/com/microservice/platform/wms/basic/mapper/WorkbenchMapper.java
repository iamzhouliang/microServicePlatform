package com.microservice.platform.wms.basic.mapper;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.wms.basic.domain.entity.Workbench;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 工作台表 Mapper 接口
 * </p>
 *
 * @author ddCat
 * @since 2024-06-17
 */
@Repository
public interface WorkbenchMapper extends SuperMapper<Workbench> {

}
