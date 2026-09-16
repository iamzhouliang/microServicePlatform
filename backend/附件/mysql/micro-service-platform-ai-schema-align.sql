-- =====================================================================================
-- micro-service-platform-ai 模块 Schema 对齐迁移脚本
-- =====================================================================================
-- 用途：
--   使数据库 schema 与后端实体（权威）保持一致，修复三处 DDL↔实体/XML 漂移：
--     1) 向量化任务表：旧 DDL `ai_vector_task`(tinyint 设计) ↔ 实体/XML `ai_kb_vectorization_task`(String 枚举设计)
--     2) `ai_knowledge_item.type/status`：tinyint ↔ 枚举以 String code 存储（@EnumValue），并补齐实体缺失列
--     3) `ai_vector_metadata`：实体存在但 DDL 缺失该表，补建
--
-- 权威判定：
--   后端实体（KnowledgeItem / VectorizationTask / VectorMetadata）+ 枚举（均 @EnumValue 存 String code）
--   为运行时权威；`附件/mysql/micro-service-platform-ai.sql` 中上述表为陈旧设计，本脚本以实体为准对齐。
--
-- 适用环境：MySQL 8.0（utf8mb4 / utf8mb4_0900_ai_ci）。
-- 幂等性：使用 information_schema 判断（COUNT(*) 形式），可重复执行；已符合的表/列会跳过。
-- 影响范围：仅结构变更（新增列/改列类型/重命名表或列/补建表），不删除任何既有列与数据。
--
-- 【执行前必做】
--   1. 备份数据库（尤其 ai_vector_task / ai_knowledge_item）。
--   2. 停止或暂停 AI 相关写入（向量化任务、知识条目导入）以避免结构变更期间写冲突。
--
-- 【关于 type/status 历史数据】
--   枚举以 String code 存储（DOCUMENT/QA_PAIR/STRUCTURED/TEXT_SNIPPET、PENDING/PROCESSING/PROCESSED/FAILED）。
--   若旧库 type/status 实际存的是数字(0/1/2/3)，本脚本仅改列类型为 varchar，不做数值->code 映射：
--   旧 DDL 注释(1-File/2-URL/3-Text)属被废弃的数值方案，与现行枚举不一一对应，无可靠映射。
--   如确有数字历史数据，请在改列类型后按业务实际手工 UPDATE（文件末尾附“可选数据映射”模板，默认注释）。
-- =====================================================================================

SET NAMES utf8mb4;

DELIMITER $$

DROP PROCEDURE IF EXISTS `ai_schema_align` $$
CREATE PROCEDURE `ai_schema_align`()
BEGIN
    DECLARE v_db VARCHAR(64) DEFAULT DATABASE();

    -- =================================================================================
    -- 1. 向量化任务表 ai_vector_task -> ai_kb_vectorization_task
    -- =================================================================================
    -- 1a. 旧表存在且新表不存在：重命名（保留历史数据）
    IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_vector_task') > 0
       AND (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task') = 0 THEN
        RENAME TABLE `ai_vector_task` TO `ai_kb_vectorization_task`;
    END IF;

    -- 1b. 新表仍不存在：按权威结构创建
    IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task') = 0 THEN
        CREATE TABLE `ai_kb_vectorization_task` (
            `id`               bigint       NOT NULL COMMENT 'ID',
            `task_id`          varchar(64)  DEFAULT NULL COMMENT '任务ID（业务唯一标识）',
            `kb_id`            bigint       DEFAULT NULL COMMENT '知识库ID',
            `item_id`          bigint       DEFAULT NULL COMMENT '知识条目ID',
            `task_type`        varchar(32)  DEFAULT NULL COMMENT '任务类型: KNOWLEDGE_ITEM/BATCH/DOCUMENT/FAQ/STRUCTURED',
            `status`           varchar(20)  NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PROCESSING/COMPLETED/FAILED',
            `progress`         int          DEFAULT '0' COMMENT '处理进度(0-100)',
            `vector_ids`       json         DEFAULT NULL COMMENT '结果向量ID列表(JSON)',
            `error_message`    varchar(2000) DEFAULT NULL COMMENT '错误信息',
            `token_usage`      int          DEFAULT NULL COMMENT '消耗的Token数',
            `tenant_id`        bigint       DEFAULT NULL COMMENT '租户ID',
            `deleted`          tinyint(1)   DEFAULT '0' COMMENT '逻辑删除',
            `create_by`        bigint       DEFAULT NULL COMMENT '创建人ID',
            `create_name`      varchar(255) DEFAULT NULL COMMENT '创建人名称',
            `create_time`      datetime     DEFAULT NULL COMMENT '创建时间',
            `last_modify_by`   bigint       DEFAULT NULL COMMENT '最后修改人ID',
            `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
            `last_modify_time` datetime     DEFAULT NULL COMMENT '最后修改时间',
            PRIMARY KEY (`id`),
            UNIQUE KEY `uk_task_id` (`task_id`),
            KEY `idx_item_id` (`item_id`),
            KEY `idx_kb_id` (`kb_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='向量化任务';
    END IF;

    -- 1c. 逐列对齐（针对由旧 ai_vector_task 重命名而来的表；新建的表已符合，以下守卫会自动跳过）
    -- task_type: tinyint -> varchar
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='task_type' AND DATA_TYPE<>'varchar') > 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` MODIFY COLUMN `task_type` varchar(32) DEFAULT NULL COMMENT '任务类型: KNOWLEDGE_ITEM/BATCH/DOCUMENT/FAQ/STRUCTURED';
    END IF;
    -- status: tinyint -> varchar
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='status' AND DATA_TYPE<>'varchar') > 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PROCESSING/COMPLETED/FAILED';
    END IF;
    -- task_id 缺失则补
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='task_id') = 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` ADD COLUMN `task_id` varchar(64) DEFAULT NULL COMMENT '任务ID（业务唯一标识）' AFTER `id`;
    END IF;
    -- message -> error_message（旧表列名为 message）
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='message') > 0
       AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='error_message') = 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` CHANGE COLUMN `message` `error_message` varchar(2000) DEFAULT NULL COMMENT '错误信息';
    ELSEIF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='error_message') = 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` ADD COLUMN `error_message` varchar(2000) DEFAULT NULL COMMENT '错误信息';
    END IF;
    -- vector_ids
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='vector_ids') = 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` ADD COLUMN `vector_ids` json DEFAULT NULL COMMENT '结果向量ID列表(JSON)';
    END IF;
    -- token_usage
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='token_usage') = 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` ADD COLUMN `token_usage` int DEFAULT NULL COMMENT '消耗的Token数';
    END IF;
    -- tenant_id
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND COLUMN_NAME='tenant_id') = 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` ADD COLUMN `tenant_id` bigint DEFAULT NULL COMMENT '租户ID';
    END IF;
    -- 唯一/普通索引
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND INDEX_NAME='uk_task_id') = 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` ADD UNIQUE KEY `uk_task_id` (`task_id`);
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_kb_vectorization_task' AND INDEX_NAME='idx_item_id') = 0 THEN
        ALTER TABLE `ai_kb_vectorization_task` ADD KEY `idx_item_id` (`item_id`);
    END IF;

    -- =================================================================================
    -- 2. ai_knowledge_item：type/status tinyint->varchar，并补齐实体缺失列
    -- =================================================================================
    -- type: tinyint -> varchar(String 枚举 code)
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='type' AND DATA_TYPE<>'varchar') > 0 THEN
        ALTER TABLE `ai_knowledge_item` MODIFY COLUMN `type` varchar(32) NOT NULL COMMENT '类型: DOCUMENT/QA_PAIR/STRUCTURED/TEXT_SNIPPET';
    END IF;
    -- status: tinyint -> varchar
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='status' AND DATA_TYPE<>'varchar') > 0 THEN
        ALTER TABLE `ai_knowledge_item` MODIFY COLUMN `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '状态: PENDING/PROCESSING/PROCESSED/FAILED';
    END IF;
    -- question / answer（QA 对）
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='question') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD COLUMN `question` text NULL COMMENT '问题（仅问答对类型有效）' AFTER `title`;
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='answer') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD COLUMN `answer` text NULL COMMENT '答案（仅问答对类型有效）' AFTER `question`;
    END IF;
    -- content_type
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='content_type') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD COLUMN `content_type` varchar(50) NULL COMMENT '内容类型（pdf/text/html 等）';
    END IF;
    -- file_url -> file_path（实体字段为 filePath）
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='file_url') > 0
       AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='file_path') = 0 THEN
        ALTER TABLE `ai_knowledge_item` CHANGE COLUMN `file_url` `file_path` varchar(500) NULL COMMENT '文件路径';
    ELSEIF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='file_path') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD COLUMN `file_path` varchar(500) NULL COMMENT '文件路径';
    END IF;
    -- vectorized / graphed
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='vectorized') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD COLUMN `vectorized` tinyint(1) DEFAULT '0' COMMENT '是否已向量化';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='graphed') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD COLUMN `graphed` tinyint(1) DEFAULT '0' COMMENT '是否已图谱化';
    END IF;
    -- version（乐观锁 @Version）
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='version') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD COLUMN `version` int DEFAULT '0' COMMENT '乐观锁版本';
    END IF;
    -- tenant_id
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND COLUMN_NAME='tenant_id') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD COLUMN `tenant_id` bigint DEFAULT NULL COMMENT '租户ID';
    END IF;
    -- kb_id 索引
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_item' AND INDEX_NAME='idx_kb_id') = 0 THEN
        ALTER TABLE `ai_knowledge_item` ADD KEY `idx_kb_id` (`kb_id`);
    END IF;

    -- =================================================================================
    -- 3. ai_vector_metadata：DDL 缺失，补建（实体 VectorMetadata）
    -- =================================================================================
    IF (SELECT COUNT(*) FROM information_schema.TABLES WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_vector_metadata') = 0 THEN
        CREATE TABLE `ai_vector_metadata` (
            `id`               bigint       NOT NULL COMMENT 'ID',
            `vector_id`        varchar(64)  DEFAULT NULL COMMENT '向量ID（向量库中唯一标识）',
            `kb_id`            bigint       DEFAULT NULL COMMENT '所属知识库ID',
            `item_id`          bigint       DEFAULT NULL COMMENT '关联知识条目ID',
            `chunk_id`         bigint       DEFAULT NULL COMMENT '关联知识分片ID',
            `chunk_type`       varchar(20)  DEFAULT NULL COMMENT '分片类型: TEXT/QUESTION/ANSWER/FULL_QA',
            `collection_name`  varchar(128) DEFAULT NULL COMMENT '向量库集合名称',
            `text_content`     longtext     COMMENT '文本内容',
            `text_hash`        varchar(64)  DEFAULT NULL COMMENT '文本哈希值',
            `similarity_score` double       DEFAULT NULL COMMENT '相似度分数',
            `metadata`         json         DEFAULT NULL COMMENT '扩展元数据(JSON)',
            `tenant_id`        bigint       DEFAULT NULL COMMENT '租户ID',
            `deleted`          tinyint(1)   DEFAULT '0' COMMENT '逻辑删除',
            `create_by`        bigint       DEFAULT NULL COMMENT '创建人ID',
            `create_name`      varchar(255) DEFAULT NULL COMMENT '创建人名称',
            `create_time`      datetime     DEFAULT NULL COMMENT '创建时间',
            `last_modify_by`   bigint       DEFAULT NULL COMMENT '最后修改人ID',
            `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
            `last_modify_time` datetime     DEFAULT NULL COMMENT '最后修改时间',
            PRIMARY KEY (`id`),
            KEY `idx_vector_id` (`vector_id`),
            KEY `idx_item_id` (`item_id`),
            KEY `idx_kb_id` (`kb_id`),
            KEY `idx_chunk_id` (`chunk_id`)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='向量元数据';
    END IF;

    -- =================================================================================
    -- 4. ai_knowledge_chunk：补齐实体 KnowledgeChunk 缺失列（旧 DDL 与实体漂移）
    --    （XML 表名 ai_kb_knowledge_chunk 已在代码侧改回 ai_knowledge_chunk）
    -- =================================================================================
    -- chunk_type（selectByItemIdAndType 依赖，缺失会导致查询报错）
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND COLUMN_NAME='chunk_type') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` ADD COLUMN `chunk_type` varchar(20) NULL COMMENT '分片类型: TEXT/QUESTION/ANSWER/FULL_QA' AFTER `item_id`;
    END IF;
    -- content_hash
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND COLUMN_NAME='content_hash') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` ADD COLUMN `content_hash` varchar(255) NULL COMMENT '内容哈希值';
    END IF;
    -- chunk_idx -> chunk_index（实体字段 chunkIndex）
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND COLUMN_NAME='chunk_idx') > 0
       AND (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND COLUMN_NAME='chunk_index') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` CHANGE COLUMN `chunk_idx` `chunk_index` int DEFAULT '0' COMMENT '分片序号';
    ELSEIF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND COLUMN_NAME='chunk_index') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` ADD COLUMN `chunk_index` int DEFAULT '0' COMMENT '分片序号';
    END IF;
    -- start_position / end_position
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND COLUMN_NAME='start_position') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` ADD COLUMN `start_position` int NULL COMMENT '在原文中的起始位置';
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND COLUMN_NAME='end_position') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` ADD COLUMN `end_position` int NULL COMMENT '在原文中的结束位置';
    END IF;
    -- tenant_id
    IF (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND COLUMN_NAME='tenant_id') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` ADD COLUMN `tenant_id` bigint DEFAULT NULL COMMENT '租户ID';
    END IF;
    -- 索引
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND INDEX_NAME='idx_item_id') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` ADD KEY `idx_item_id` (`item_id`);
    END IF;
    IF (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=v_db AND TABLE_NAME='ai_knowledge_chunk' AND INDEX_NAME='idx_kb_id') = 0 THEN
        ALTER TABLE `ai_knowledge_chunk` ADD KEY `idx_kb_id` (`kb_id`);
    END IF;

END $$
DELIMITER ;

CALL `ai_schema_align`();
DROP PROCEDURE IF EXISTS `ai_schema_align`;

-- =====================================================================================
-- 可选：type/status 数值历史数据 -> 枚举 code 映射（默认注释；仅当旧库确有数字数据时按需启用）
-- 注意：旧 DDL 的数值注释(1-File/2-URL/3-Text)与现行枚举不一一对应，以下仅为示例，请按业务核对后再执行。
-- UPDATE `ai_knowledge_item` SET `type`   = 'DOCUMENT'     WHERE `type`   = '1';
-- UPDATE `ai_knowledge_item` SET `type`   = 'TEXT_SNIPPET' WHERE `type`   = '3';
-- UPDATE `ai_knowledge_item` SET `status` = 'PENDING'      WHERE `status` = '0';
-- UPDATE `ai_knowledge_item` SET `status` = 'PROCESSING'   WHERE `status` = '1';
-- UPDATE `ai_knowledge_item` SET `status` = 'PROCESSED'    WHERE `status` = '2';
-- UPDATE `ai_knowledge_item` SET `status` = 'FAILED'       WHERE `status` = '3';

-- =====================================================================================
-- 回滚说明（如需）
--   本脚本为“新增列/改类型/重命名/补表”，不删除数据。若需回滚：
--   1) 向量化任务表：RENAME TABLE `ai_kb_vectorization_task` TO `ai_vector_task`;（若此前由旧表重命名而来）
--      并按需还原 task_type/status 为 tinyint、error_message 改回 message。
--   2) ai_knowledge_item：新增列可 DROP COLUMN 还原（question/answer/content_type/file_path/vectorized/graphed/version/tenant_id）；
--      type/status 若需还原为 tinyint 需自行评估数据可转换性。
--   3) ai_vector_metadata：DROP TABLE `ai_vector_metadata`;（将丢失向量元数据，请谨慎）
--   建议：回滚前务必先备份。
-- =====================================================================================
