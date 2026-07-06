# Scour Picture

Scour Picture 是一个图片空间管理项目，包含后端服务 `picture` 和前端应用 `picture-web`。项目支持公共图库、私有空间、团队空间、图片上传、空间成员权限、空间分析、以图搜图、AI 扩图、WebSocket 协同编辑以及基于空间级别的图片分表。

## 项目结构

```text
scour-picture
├── picture          # Spring Boot 后端服务
├── picture-web      # Vue 3 前端应用
├── sql              # 数据库初始化脚本
└── README.md
```

## 技术栈

后端：

- Java 17
- Spring Boot 3.5.6
- MyBatis-Plus 3.5.12
- MySQL
- Redis
- Sa-Token
- ShardingSphere JDBC 5.4.1
- 腾讯云 COS
- 阿里云 AI

前端：

- Vue 3
- TypeScript
- Vite
- Pinia
- Vue Router
- Ant Design Vue

## 环境要求

- JDK 17
- Maven 3.8+
- Node.js 20.19+ 或 22.12+
- MySQL 8.x
- Redis 6+

## 初始化数据库

先创建并初始化数据库：

```bash
mysql -u root -p < sql/create_table.sql
```

默认数据库名是：

```text
yu_picture
```

如果需要修改数据库名，请同步修改后端配置中的数据库连接地址。

## 后端配置

后端默认配置文件位于：

```text
picture/src/main/resources/application.yml
```

本地开发时，复制一份本地配置文件：

```bash
cp picture/src/main/resources/application.yml picture/src/main/resources/application-local.yml
```

Windows PowerShell：

```powershell
Copy-Item picture/src/main/resources/application.yml picture/src/main/resources/application-local.yml
```

然后修改 `application-local.yml` 中的本地配置，例如：

- MySQL 地址、用户名、密码
- Redis 地址、密码
- 腾讯云 COS 配置
- 阿里云 AI API Key

`application-local.yml` 不提交到远程仓库，用来保存每个人自己的本地配置。

主要配置项也支持环境变量：

```text
DB_URL
DB_USERNAME
DB_PASSWORD
REDIS_HOST
REDIS_PORT
REDIS_PASSWORD
COS_HOST
COS_SECRET_ID
COS_SECRET_KEY
COS_REGION
COS_BUCKET
ALIYUN_AI_API_KEY
```

## 启动后端

```bash
cd picture
mvn spring-boot:run
```

默认后端地址：

```text
http://localhost:8082/api
```

接口文档地址通常为：

```text
http://localhost:8082/api/doc.html
```

## 启动前端

安装依赖：

```bash
cd picture-web
npm install
```

启动开发服务：

```bash
npm run dev
```

前端默认地址以 Vite 输出为准，通常是：

```text
http://localhost:5173
```

## 构建

后端测试：

```bash
cd picture
mvn test
```

前端构建：

```bash
cd picture-web
npm run build-only
```

完整前端构建命令是：

```bash
npm run build
```

如果 `vue-tsc` 报现有类型错误，可以先使用 `npm run build-only` 验证 Vite 打包。

## 图片分表规则

项目使用 ShardingSphere JDBC 管理图片表路由。

当前规则：

- 公共图库：写入 `picture`，`spaceId = null`
- 普通 / 专业私有空间：写入 `picture`，通过 `spaceId` 区分
- 普通 / 专业团队空间：写入 `picture`，通过 `spaceId` 区分
- 旗舰级空间：创建并使用 `picture_{spaceId}` 物理分表

后端启动时会扫描已有的 `picture_*` 物理分表并加入 ShardingSphere 路由规则。运行时创建旗舰级空间时，会动态创建物理分表并刷新路由。
