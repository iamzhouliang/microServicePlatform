package com.microservice.framework.log.diff;

import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.microservice.framework.log.diff.configuration.DiffLogProperties;
import com.microservice.framework.log.diff.core.DiffFieldStrategy;
import com.microservice.framework.log.diff.core.LocalPropertyChange;
import com.microservice.framework.log.diff.core.annotation.DiffField;
import com.microservice.framework.log.diff.domain.FieldChange;
import com.microservice.framework.log.diff.domain.enums.ChangeAction;
import com.microservice.framework.log.diff.service.IFunctionService;
import com.microservice.framework.log.diff.utils.DiffUtils;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.javers.core.Javers;
import org.javers.core.diff.Change;
import org.javers.core.diff.DiffBuilder;
import org.javers.core.diff.changetype.PropertyChange;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @author Levin
 */
@Slf4j
@Setter
@Getter
@RequiredArgsConstructor
public class DefaultDiffItemsToLogContentService implements IDiffItemsToLogContentService, BeanFactoryAware, SmartInitializingSingleton {

    private final Javers javers;
    private final DiffLogProperties diffLogProperties;
    private IFunctionService functionService;
    private BeanFactory beanFactory;

    @Override
    public String toLogContent(final Object source, final Object target) {
        if (source == null && target == null) {
            return "";
        }
        var diff = DiffUtils.compare(javers, source, target);
        if (!diff.hasChanges()) {
            return "";
        }
        var changes = diff.getChangesByType(PropertyChange.class)
                .stream()
                .map(x -> LocalPropertyChange.wrap(javers, x))
                .toList();
        if (diffLogProperties.isPrettyValuePrinter()) {
            diff = new DiffBuilder(javers.getCoreConfiguration().getPrettyValuePrinter())
                    .addChanges(changes)
                    .build();
            log.info("diff format - {}", diff);
        }
        StringBuilder builder = new StringBuilder();
        for (Change change : changes) {
            processChangeNode(builder, change);
        }
        return builder.toString().replaceAll(diffLogProperties.getFieldSeparator().concat("$"), "");
    }

    private void processChangeNode(StringBuilder builder, Change change) {
        if (!(change instanceof LocalPropertyChange valueChange)) {
            return;
        }
        Field field = ReflectUtil.getField(valueChange.getClassName(), valueChange.getOriginalName());
        // 全局忽略字段优先级最高，适合 createTime、lastModifyTime 等所有实体都不应展示的字段。
        if (diffLogProperties.getIgnoreGlobalFields().contains(valueChange.getOriginalName())) {
            return;
        }
        DiffField annotation = field.getAnnotation(DiffField.class);
        // 开启注解检查时，只记录显式标注 @DiffField 的字段，避免敏感字段或技术字段误入日志。
        if (diffLogProperties.isCheckAnnotation() && annotation == null) {
            return;
        }
        String filedLogName = Optional.ofNullable(annotation).map(DiffField::name).orElse(valueChange.getPropertyName());
        String functionName = Optional.ofNullable(annotation).map(DiffField::function).orElse(null);
        DiffFieldStrategy strategy = Optional.ofNullable(annotation).map(DiffField::strategy).orElse(DiffFieldStrategy.ALWAYS);
        String logContent = getFieldLogContent(valueChange, filedLogName, functionName, strategy);
        if (StrUtil.isBlank(logContent)) {
            return;
        }
        builder.append(logContent).append(diffLogProperties.getFieldSeparator());
    }


    public String getFieldLogContent(Change change, String filedLogName, String functionName, DiffFieldStrategy strategy) {
        if (!(change instanceof LocalPropertyChange node)) {
            return "";
        }
        // 新增、删除、修改分别走不同模板，最终格式由 DiffLogProperties 统一控制。
        if (node.getAction() == ChangeAction.ADDED) {
            return diffLogProperties.formatAdd(filedLogName, getFunctionValue(node.getRight(), functionName));
        }
        if (node.getAction() == ChangeAction.REMOVED) {
            return diffLogProperties.formatDeleted(filedLogName, getFunctionValue(node.getLeft(), functionName));
        }
        if (node.getAction() == ChangeAction.UPDATED) {
            if (strategy == DiffFieldStrategy.NOT_NULL && Objects.isNull(node.getRight())) {
                // NOT_NULL 用于“空值不覆盖”的业务字段，新值为空时不记录本次变化。
                return "";
            }
            return diffLogProperties.formatUpdate(filedLogName, getFunctionValue(node.getLeft(), functionName), getFunctionValue(node.getRight(), functionName));
        }
        return "";
    }


    private Object getFunctionValue(Object value, String functionName) {
        if (StrUtil.isEmpty(functionName)) {
            return value;
        }
        // 字段函数用于把枚举、用户 ID、字典值等技术值转换成可读文案。
        return functionService.apply(functionName, value);
    }


    @Override
    public void setBeanFactory(@NonNull BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        this.functionService = beanFactory.getBean(IFunctionService.class);
    }

    @Override
    public List<FieldChange> toFieldChanges(final Object source, final Object target) {
        List<FieldChange> result = new ArrayList<>();
        if (source == null && target == null) {
            return result;
        }
        var diff = DiffUtils.compare(javers, source, target);
        if (!diff.hasChanges()) {
            return result;
        }
        var changes = diff.getChangesByType(PropertyChange.class)
                .stream()
                .map(x -> LocalPropertyChange.wrap(javers, x))
                .toList();
        for (Change change : changes) {
            FieldChange fieldChange = toFieldChange(change);
            if (fieldChange != null) {
                result.add(fieldChange);
            }
        }
        return result;
    }

    private FieldChange toFieldChange(Change change) {
        if (!(change instanceof LocalPropertyChange valueChange)) {
            return null;
        }
        Field field = ReflectUtil.getField(valueChange.getClassName(), valueChange.getOriginalName());
        // 结构化字段变化与文本日志保持同一套过滤和字段函数规则。
        if (diffLogProperties.getIgnoreGlobalFields().contains(valueChange.getOriginalName())) {
            return null;
        }
        DiffField annotation = field.getAnnotation(DiffField.class);
        if (diffLogProperties.isCheckAnnotation() && annotation == null) {
            return null;
        }
        String fieldLabel = Optional.ofNullable(annotation).map(DiffField::name).orElse(valueChange.getPropertyName());
        String functionName = Optional.ofNullable(annotation).map(DiffField::function).orElse(null);
        DiffFieldStrategy strategy = Optional.ofNullable(annotation).map(DiffField::strategy).orElse(DiffFieldStrategy.ALWAYS);
        if (valueChange.getAction() == ChangeAction.UPDATED && strategy == DiffFieldStrategy.NOT_NULL && Objects.isNull(valueChange.getRight())) {
            return null;
        }
        return FieldChange.builder()
                .name(valueChange.getOriginalName())
                .label(fieldLabel)
                .oldVal(getFunctionValue(valueChange.getLeft(), functionName))
                .newVal(getFunctionValue(valueChange.getRight(), functionName))
                .build();
    }
}
