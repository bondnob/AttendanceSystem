# 考勤管理系统 (Attendance System)

一个基于 Spring Boot 的企业级考勤请假管理系统，支持多级审批流程、电子签名、PDF 生成等功能。

## 技术栈

| 技术 | 说明 |
|------|------|
| Spring Boot 4.0.5 | 应用框架 |
| MyBatis | ORM 持久层框架 |
| MySQL 8.0 | 关系型数据库 |
| JWT (jjwt 0.12.6) | 身份认证 |
| Spring Security Crypto | 密码加密 |
| SpringDoc OpenAPI 2.8.9 | API 文档 (Swagger) |
| Apache POI 5.4.1 | Excel 处理 |
| OpenPDF 2.0.3 | PDF 生成 |
| Lombok | 简化代码 |
| Java 17 | JDK 版本 |

## 项目结构

```
src/main/java/com/attendance/
├── AttendanceSysApplication.java          # 启动类
├── auth/                                  # 认证模块
│   ├── controller/AuthController.java     # 登录 & 工作台接口
│   ├── dto/                               # 登录/工作台 DTO
│   ├── mapper/                            # 消息 Mapper
│   ├── model/                             # 消息实体
│   ├── security/                          # JWT 认证 & 拦截器
│   └── service/AuthService.java           # 认证业务逻辑
├── admin/                                 # 系统管理模块
│   ├── controller/AdminController.java    # 管理接口
│   ├── dto/                               # 管理 DTO
│   ├── mapper/                            # 组织/权限 Mapper
│   ├── model/                             # 组织/权限实体
│   └── service/AdminService.java          # 管理业务逻辑
├── leave/                                 # 请假模块
│   ├── controller/LeaveController.java    # 请假接口
│   ├── dto/                               # 请假 DTO
│   ├── enums/                             # 状态/角色枚举
│   ├── mapper/                            # 请假 Mapper
│   ├── model/                             # 请假实体
│   └── service/                           # 请假业务逻辑
│       ├── LeaveService.java              # 请假核心服务
│       ├── LeaveApprovalService.java      # 审批流程服务
│       ├── LeaveDocumentService.java      # PDF 文档生成
│       ├── LeaveQueryService.java         # 请假查询服务
│       ├── LeaveSignatureService.java     # 签名服务
│       ├── LeaveSignRequirementService.java # 签章要求服务
│       └── LeaveValidationService.java    # 请假校验服务
├── common/                                # 公共模块
│   ├── ApiResponse.java                   # 统一响应体
│   ├── PageResponse.java                  # 分页响应体
│   └── PasswordUtils.java                 # 密码工具类
├── config/                                # 配置
│   └── FileResourceConfig.java            # 文件资源映射
└── exception/                             # 异常处理
    ├── BizException.java                  # 业务异常
    └── GlobalExceptionHandler.java        # 全局异常处理
```

## 核心功能

### 1. 用户认证 (JWT)

- 账号密码登录，JWT Token 签发
- Token 自动校验拦截器，支持 `Bearer token` 和直接传 token 两种方式
- Token 有效期 12 小时（可配置）

### 2. 组织管理

- 多级组织架构管理（新增、编辑、启停用）
- 组织下用户管理（新增、编辑、重置密码、启停用）
- 为审批人上传/管理电子签名

### 3. 请假管理

- **提交请假单**：考勤管理员为本单位职工提交请假记录单
- **修改/删除**：支持修改和删除已驳回的请假单
- **撤销请假**：发起人可撤销进行中的请假单
- **请假类型**：事假、病假、年休假、探亲假、婚假、产假、丧假等
- **分页查询**：按状态、假别筛选，支持近三个月快捷查询

### 4. 多级审批流程

系统根据人员级别、假别类型和请假天数自动匹配审批流程：

| 角色 | 说明 |
|------|------|
| ATTENDANCE_ADMIN | 考勤管理员（发起请假） |
| ORG_PRINCIPAL | 科室/车间负责人 |
| HR_SECTION_CHIEF | 劳人科科长 |
| UNIT_DEPUTY_LEADER | 单位副职领导 |
| UNIT_PRINCIPAL_LEADER | 单位主要领导 |
| DEPUTY_STATIONMASTER | 副站长 |
| STATIONMASTER | 站长 |
| PARTY_SECRETARY | 党委书记 |

审批流程特性：
- 根据审批权限配置自动匹配审批链
- 支持选择后续领导审批人
- 支持重选尚未审批的领导
- 批量一键审批
- 审批签名使用超级管理员预先配置的电子签名

### 5. 请假单状态流转

```
PENDING (待提交) → APPROVING (审批中) → APPROVED (已通过)
                                      → REJECTED (已驳回)
                                      → CANCELLED (已取消)
```

### 6. 电子签名

- 超级管理员为审批人预先配置电子签名图片
- 请假人/班组长可上传鼠标手写签名
- 审批时自动使用预配置的电子签名
- 支持签名日期图片上传

### 7. PDF 文档生成

- 自动生成请假记录单 PDF
- 支持批量下载（按时间段合并多个请假单 PDF）
- PDF 中包含完整的审批签名记录

### 8. 工作台 Dashboard

- 当月请假类别及人数统计
- 待审批/已审批数量统计
- 最近信息提示推送

### 9. 消息通知

- 超级管理员可向指定账号发送信息提示
- 工作台展示最近消息列表（分页）

## 数据库设计

系统包含以下核心数据表：

| 表名 | 说明 |
|------|------|
| `user_account` | 用户账号表 |
| `org_unit` | 组织单元表 |
| `leave_type` | 请假类型表 |
| `leave_request` | 请假申请表 |
| `leave_approval` | 审批记录表 |
| `approval_rule` | 审批规则表 |
| `approval_rule_step` | 审批规则步骤表 |
| `approval_permission` | 审批权限配置表 |
| `leave_sign_requirement` | 请假签章要求表 |
| `user_message` | 用户消息表 |

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Maven 3.6+

### 1. 克隆项目

```bash
git clone https://github.com/your-username/attendance-sys.git
cd attendance-sys
```

### 2. 初始化数据库

```bash
mysql -u root -p < src/main/resources/db/attendance_sys.sql
```

### 3. 修改配置

编辑 `src/main/resources/application.properties`：

```properties
# 数据库配置
spring.datasource.url=jdbc:mysql://localhost:3306/attendance_sys?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
spring.datasource.username=root
spring.datasource.password=your_password

# JWT 密钥（生产环境请修改）
attendance.jwt.secret=your-key
attendance.jwt.expire-hours=12

# 文件上传大小限制
spring.servlet.multipart.max-file-size=5MB

# 文件存储路径
attendance.file-storage-path=uploads
```

### 4. 运行项目

```bash
mvn spring-boot:run
```

项目启动后访问：
- API 基础路径：`http://localhost:8080/api`
- Swagger 文档：`http://localhost:8080/swagger-ui.html`

## API 接口概览

### 认证接口 `/api/auth`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/login` | 用户登录 |
| GET | `/dashboard` | 工作台数据 |
| GET | `/dashboard/leave-type-request-counts` | 请假类别统计 |
| GET | `/dashboard/approval-stats` | 审批统计 |
| GET | `/dashboard/messages` | 消息列表 |

### 请假接口 `/api/leaves`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/` | 提交请假单 |
| PUT | `/{leaveId}` | 修改驳回请假单 |
| DELETE | `/{leaveId}` | 删除驳回请假单 |
| GET | `/` | 请假单列表 |
| GET | `/{leaveId}` | 请假单详情 |
| POST | `/{leaveId}/approve` | 审批请假单 |
| POST | `/batch-approve` | 批量审批 |
| POST | `/{leaveId}/cancel` | 撤销请假单 |
| POST | `/{leaveId}/handwritten-signature` | 上传手写签名 |
| POST | `/{leaveId}/select-approvers` | 选择后续领导 |
| POST | `/{leaveId}/reselect-approvers` | 重选后续领导 |
| GET | `/{leaveId}/pdf` | 获取请假单 PDF |
| POST | `/pdf/batch` | 批量下载 PDF |
| GET | `/types` | 请假类型列表 |
| GET | `/statuses` | 请假状态列表 |
| GET | `/pending-summary` | 待审批数量 |

### 管理接口 `/api/admin`

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/org-units` | 新增组织 |
| GET | `/org-units` | 组织列表 |
| PUT | `/org-units/{id}` | 编辑组织 |
| PATCH | `/org-units/{id}/enabled` | 启停用组织 |
| POST | `/users` | 新增用户 |
| GET | `/users` | 用户列表 |
| PUT | `/users/{id}` | 编辑用户 |
| PATCH | `/users/{id}/enabled` | 启停用用户 |
| PATCH | `/users/{id}/signature` | 上传审批签名 |
| POST | `/users/{id}/reset-password` | 重置密码 |
| POST | `/approval-permissions` | 保存审批权限 |
| GET | `/approval-permissions` | 审批权限列表 |
| POST | `/messages` | 发送消息 |
| GET | `/leaves` | 所有请假记录 |

## 统一响应格式

```json
{
    "success": true,
    "message": "操作成功",
    "data": { ... }
}
```

分页响应：

```json
{
    "success": true,
    "message": "OK",
    "data": {
        "list": [ ... ],
        "total": 100,
        "pageNum": 1,
        "pageSize": 10
    }
}
```

## License

MIT
