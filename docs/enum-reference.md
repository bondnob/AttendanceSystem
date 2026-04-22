# 项目枚举值中文对照表

本文档整理项目中实际使用到的固定枚举值、状态值、编码值及其中文含义，供前后端联调和接口文档编写时参考。

## 1. 请假单状态

来源：

- `src/main/java/com/attendance/leave/enums/LeaveRequestStatus.java`

| 值 | 中文 |
| --- | --- |
| `PENDING` | 待审批 |
| `APPROVING` | 审批中 |
| `APPROVED` | 已通过 |
| `REJECTED` | 已驳回 |
| `CANCELLED` | 已取消 |

## 2. 审批状态

来源：

- `src/main/java/com/attendance/leave/enums/ApprovalStatus.java`

| 值 | 中文 |
| --- | --- |
| `PENDING` | 待处理 |
| `APPROVED` | 已同意 |
| `REJECTED` | 已拒绝 |
| `SKIPPED` | 已跳过 |

## 3. 审批步骤特殊值

来源：

- `src/main/java/com/attendance/leave/enums/ApprovalStep.java`

| 值 | 中文 |
| --- | --- |
| `99` | 流程结束 |

## 4. 系统角色编码

来源：

- `src/main/java/com/attendance/leave/enums/RoleCode.java`
- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `SYSTEM_ADMIN` | 超级管理员 |
| `ATTENDANCE_ADMIN` | 考勤管理员 |
| `ORG_PRINCIPAL` | 科室车间负责人 |
| `HR_SECTION_CHIEF` | 劳动人事科科长 |
| `DEPUTY_STATIONMASTER` | 主管站长 |
| `STATIONMASTER` | 站长 |
| `PARTY_SECRETARY` | 党委书记 |
| `UNIT_LEADER` | 单位领导 |
| `UNIT_DEPUTY_LEADER` | 单位副职领导 |
| `UNIT_PRINCIPAL_LEADER` | 单位正职领导 |
| `EMPLOYEE` | 职工角色代码 |

## 5. 人员类别

来源：

- `src/main/java/com/attendance/leave/service/LeaveService.java`
- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `EMPLOYEE` | 普通职工 |
| `CADRE` | 管理人员/干部 |

## 6. 岗位级别

来源：

- `src/main/java/com/attendance/leave/service/LeaveService.java`
- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `STAFF` | 普通职工 |
| `GENERAL_CADRE` | 一般干部 |
| `SECTION_LEVEL` | 中层正职 |
| `UNIT_PRINCIPAL` | 单位正职 |

## 7. 数据范围

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `ALL` | 全部数据 |
| `ORG` | 本单位数据 |
| `SELF` | 仅本人数据 |

## 8. 审批范围

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `ALL` | 全部审批范围 |
| `ORG` | 本单位审批范围 |
| `NONE` | 无审批范围 |

## 9. 组织类型

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `DEPARTMENT` | 科室/部门 |
| `WORKSHOP` | 车间 |

## 10. 请假范围

来源：

- `src/main/java/com/attendance/leave/service/LeaveService.java`
- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `ALL` | 所有假别 |
| `OTHER` | 其他假 |
| `SICK` | 病假 |
| `PERSONAL` | 事假 |

## 11. 审批节点类型

来源：

- `src/main/java/com/attendance/leave/service/LeaveService.java`
- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `APPROVE` | 审批节点 |
| `SELECT` | 选择后续审批人节点 |

## 12. 审批人来源

来源：

- `src/main/java/com/attendance/leave/service/LeaveService.java`
- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `APPLICANT_ORG` | 从申请人所在单位取审批人 |
| `HR_ORG` | 从人事口全局角色取审批人 |
| `SELECTED` | 由上一步手动选择审批人 |
| `UNIT` | 单位级来源 |

## 13. 领导候选分组

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `SUPERVISOR_LEADER` | 主管领导组 |
| `UNIT_LEADER` | 单位领导组 |
| `PARTY_AND_PRINCIPAL` | 党政主要领导组 |

## 14. 请假计量单位

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `DAY` | 天 |
| `MONTH` | 月 |
| `HOUR` | 小时 |

## 15. 请假计算规则

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `MANUAL` | 手工计算 |
| `WORKDAY` | 按工作日计算 |
| `SHIFT_CONVERT` | 按轮班折算 |
| `NATURAL_DAY` | 按自然日计算 |

## 16. 审批规则编码

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `EMPLOYEE_OTHER_FLOW` | 普通职工普通假流程 |
| `EMPLOYEE_SICK_7_WITHIN` | 普通职工病假7日内流程 |
| `EMPLOYEE_SICK_7_TO_30` | 普通职工病假7日以上1个月内流程 |
| `EMPLOYEE_SICK_OVER_30` | 普通职工病假1个月以上流程 |
| `EMPLOYEE_PERSONAL_5_TO_10` | 普通职工事假5天以上10天以内流程 |
| `EMPLOYEE_PERSONAL_10_TO_30` | 普通职工事假10天以上30天以内流程 |
| `EMPLOYEE_PERSONAL_OVER_30` | 普通职工事假30天以上流程 |
| `MANAGER_GENERAL_FLOW` | 管理人员一般干部流程 |
| `MANAGER_SECTION_FLOW` | 管理人员中层正职流程 |

## 17. 审批步骤编码

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `ORG_PRINCIPAL_APPROVE` | 科室车间负责人审批 |
| `HR_APPROVE` | 劳动人事科科长审批 |
| `SELECT_SUPERVISOR` | 选择主管领导 |
| `SELECT_UNIT_LEADER` | 选择单位领导 |
| `SELECT_PARTY_PRINCIPAL` | 选择党政领导 |
| `SELECTED_LEADER_APPROVE` | 已选领导审批 |
| `SELECTED_LEADER_APPROVE_1` | 已选领导审批一 |
| `SELECTED_LEADER_APPROVE_2` | 已选领导审批二 |

## 18. 假别代字

来源：

- `src/main/resources/db/attendance_sys.sql`

| 值 | 中文 |
| --- | --- |
| `伤` | 工伤假 |
| `年` | 年休假 |
| `病` | 病假 |
| `事` | 事假 |
| `婚` | 婚假 |
| `产` | 产假 |
| `陪` | 陪产假 |
| `育` | 育儿假 |
| `护` | 护理假 |
| `保` | 保育假 |
| `乳` | 哺乳时间 |
| `计` | 计划生育假 |
| `丧` | 丧假 |
| `搬` | 搬家假 |
| `探` | 探亲假 |
| `旷` | 旷工 |
