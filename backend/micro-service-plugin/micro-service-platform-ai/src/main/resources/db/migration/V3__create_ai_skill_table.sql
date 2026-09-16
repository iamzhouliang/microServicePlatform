/*
 * AI Skill Catalog - Database Schema
 *
 * 管理 LangChain4j Skills 文件系统技能包。每个技能对应一个目录，
 * 目录下包含 SKILL.md 和可选资源文件。
 */

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ai_skill
-- ----------------------------
CREATE TABLE IF NOT EXISTS `ai_skill` (
    `id` bigint NOT NULL COMMENT '主键ID',
    `name` varchar(100) NOT NULL COMMENT '技能名称',
    `code` varchar(100) NOT NULL COMMENT '技能编码',
    `description` varchar(500) DEFAULT NULL COMMENT '技能描述',
    `category` varchar(50) DEFAULT NULL COMMENT '技能分类',
    `icon` varchar(100) DEFAULT NULL COMMENT '图标',
    `tags` varchar(500) DEFAULT NULL COMMENT '标签列表，逗号分隔',
    `skill_path` varchar(255) NOT NULL COMMENT '技能目录相对路径',
    `skill_file` varchar(255) NOT NULL COMMENT 'SKILL.md相对路径',
    `resource_count` int NOT NULL DEFAULT '0' COMMENT '资源文件数量',
    `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
    `published` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否发布',
    `tenant_id` bigint DEFAULT NULL COMMENT '租户ID',
    `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除标志',
    `create_by` bigint DEFAULT NULL COMMENT '创建人ID',
    `create_name` varchar(255) DEFAULT NULL COMMENT '创建人名称',
    `create_time` datetime DEFAULT NULL COMMENT '创建时间',
    `last_modify_by` bigint DEFAULT NULL COMMENT '最后修改人ID',
    `last_modify_name` varchar(255) DEFAULT NULL COMMENT '最后修改人名称',
    `last_modify_time` datetime DEFAULT NULL COMMENT '最后修改时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_skill_code` (`code`),
    KEY `idx_skill_category` (`category`),
    KEY `idx_skill_status` (`status`),
    KEY `idx_skill_published` (`published`),
    KEY `idx_skill_tenant` (`tenant_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='AI技能配置';

SET FOREIGN_KEY_CHECKS = 1;
