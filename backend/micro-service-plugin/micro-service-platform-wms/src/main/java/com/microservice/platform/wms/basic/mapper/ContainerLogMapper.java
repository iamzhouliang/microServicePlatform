package com.microservice.platform.wms.basic.mapper;

import com.microservice.framework.db.mybatisplus.ext.SuperMapper;
import com.microservice.platform.wms.basic.domain.entity.ContainerLog;
import org.springframework.stereotype.Repository;

/**
 * <p>
 * 容器日志 Mapper 接口
 * </p>
 *
 * @author ddCat
 * @since 2024-07-02
 */
@Repository
public interface ContainerLogMapper extends SuperMapper<ContainerLog> {

}
