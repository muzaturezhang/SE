# 数据目录

- `fixtures/`：后续可提交的人工合成测试数据；按模块和用途说明。
- `local/`：本机临时数据，默认忽略版本控制。
- `uploads/`：本机上传文件预留位置，默认忽略版本控制。

`fixtures/room-inventory.json` 是 B 的只读查询样例：房型、整数分价格、物理房间和相对启动日期的不可用区间。Maven 将它打包到 classpath:fixtures/room-inventory.json，内存适配器启动时加载；这不是数据库 schema 或真实 booking 数据。演示场景见 `docs/room-query-demo.md`。

没有真实用户数据。默认 H2 为内存库，不会自动写入此目录。业务 schema 与数据库 seed 由 D 和模块 owner 确认后添加。

不要保存真实个人信息、数据库备份或支付密钥。
