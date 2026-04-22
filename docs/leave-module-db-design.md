# 请假模块数据库设计

## 1. 当前流程

### 1.1 普通职工

由考勤管理员发起申请，然后按下面顺序流转：

事假 5 天以下：

1. 科室车间负责人审批（电子签名）
2. 流程结束

普通假、病假 7 日内等其他常规场景：

1. 科室车间负责人审批
2. 劳动人事科科长审批
3. 返回科室车间负责人上传电子签名
4. 主管站长审批
5. 主管站长上传电子签名

病假、事假如果超过一个月，再增加：

6. 站长审批

这里当前后端按 `leave_days > 30` 视为“超过一个月”。

### 1.2 管理人员

由考勤管理员发起申请，然后按下面顺序流转：

1. 科室车间负责人审批
2. 劳动人事科科长审批
3. 返回科室车间负责人上传电子签名
4. 主管站长审批
5. 主管站长上传电子签名
6. 站长审批
7. 站长上传电子签名
8. 党委书记审批
9. 党委书记上传电子签名

## 2. 数据库结构

### 2.1 `approval_rule`

定义不同人员走哪套规则。

关键字段：

- `applicant_type`
- `position_level_code`
- `leave_scope`
- `exceeds_month_only`

### 2.2 `approval_rule_step`

定义每个流程的每一步。

关键字段：

- `action_type`
  - `APPROVE` 审批节点
  - `SIGN` 签名节点
- `step_code`
- `step_code_name`
- `approver_role_code`
- `approver_role_name`

### 2.3 `leave_request`

保存请假主信息，并新增：

- `current_action_type`
- `exceeds_one_month`

### 2.4 `leave_approval`

每个节点一条记录，并新增：

- `action_type`
- `signature_url`

## 3. 接口约定

### 3.1 审批接口

- `POST /api/leaves/{leaveId}/approve`

请求体：

- `approved`
- `comment`

### 3.2 签名接口

- `POST /api/leaves/{leaveId}/sign`

提交格式：

- `multipart/form-data`

字段：

- `comment`
- `signatureFile`

## 4. 展示建议

前端页面只展示中文字段：

- `role_name`
- `step_code_name`
- `approver_role_name`
- `action_type` 可转成“审批”或“签名”
