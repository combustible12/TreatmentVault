# TreatmentVault / 治疗资料库

纯本地 Android 治疗资料管理 App。目标不是购物清单，而是把整个治疗过程集中在一个地方：

- 总时间轴
- 检查报告
- 治疗方案与每次治疗记录
- 药品与用品
- 症状记录
- 医生问诊
- 费用记录
- ChatGPT JSON/ZIP 更新包一键导入
- Memento CSV 迁移
- 本地备份/恢复

## 当前 MVP

当前第一版已建立 Room 数据模型和上述页面；用品、检查、症状、问诊、费用支持简单手工新增；治疗方案/治疗记录优先通过更新包导入；JSON/ZIP 更新包支持预览后事务导入与 upsert；Memento 用品 CSV 可直接迁移；备份支持结构化数据 ZIP。

## 更新包

`examples/update_package.json` 是标准示例。支持类型：
` supply / exam / treatment_plan / treatment_record / symptom / question / expense / timeline `。

ZIP 更新包须在根目录包含 `manifest.json`。第一版会读取 manifest；附件自动关联将在下一阶段加强。

## 构建 APK

GitHub Actions 已配置。push 或手动 workflow_dispatch 后执行：

```bash
gradle assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`，并上传为 Actions Artifact。

本地 Android Studio 使用 JDK 17、Gradle 8.10.2、compileSdk 35。

## 数据原则

- Room/SQLite 本地存储
- 无登录、无服务器依赖
- 更新包导入在事务内执行
- 用品按名称 upsert；其他模块使用稳定组合键匹配
- 无法识别的类型不会覆盖旧数据，而是记录失败信息

## 下一阶段

- 更完整的手工编辑表单
- 附件（图片/PDF）选择、查看、更新包 ZIP 自动解包关联
- 全局搜索
- 疑似重复“待确认”收件箱
- 导入撤销
- 症状趋势图
- 完整附件备份
