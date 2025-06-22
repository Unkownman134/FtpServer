# 简易 FTP 服务器

## 项目简介

这是一个基于 Java 实现的简化版 FTP (文件传输协议) 服务器。该项目旨在演示 FTP 协议的核心机制，包括控制连接、数据连接、并发客户端处理以及常见 FTP 命令的实现。它可用于学习和理解网络编程基础、多线程并发、文件系统操作以及标准协议的实现。

## 主要功能

本 FTP 服务器已实现以下核心功能和兼容性增强：

* **用户认证**:
    * 支持 `USER` 和 `PASS` 命令。
    * 用户凭据从外部 `users.properties` 配置文件加载，便于管理和扩展。
    * 支持多用户登录。
* **文件和目录操作**:
    * **文件列表**: 支持 `LIST` 命令，用于显示服务器当前目录的文件和目录列表。兼容 Windows FTP 客户端发送的 `NLST` 和 `LIST` 命令。
    * **文件上传**: 支持 `STOR` 命令，允许客户端将文件上传至服务器。
    * **文件下载**: 支持 `RETR` 命令，允许客户端从服务器下载文件。
    * **目录管理**: 支持 `CWD` (Change Working Directory)、`PWD` (Print Working Directory)、`MKD` (Make Directory)、`RMD` (Remove Directory) 命令。
    * **文件删除**: 支持 `DELE` (Delete File) 命令。
    * **文件/目录重命名**: 支持 `RNFR` (Rename From) 和 `RNTO` (Rename To) 命令组合。
    * **文件信息**: 支持 `SIZE` (文件大小) 和 `MDTM` (修改时间) 命令。
* **协议兼容性**:
    * 自动处理 Windows 命令行 FTP 客户端发送的 `OPTS UTF8 ON` 命令。
    * 兼容 `EPRT` (Extended Port) 命令，支持 IPv6 主动模式连接。
    * 兼容 `XMKD` (Extended Make Directory) 和 `XRMD` (Extended Remove Directory) 命令。
* **并发服务**:
    * 服务器使用线程池 (ExecutorService) 并发处理多个客户端连接，确保高响应性。
* **错误响应**:
    * 对各种异常和不符合协议的行为提供相应的 FTP 错误码响应。

## 项目结构

项目代码被合理地组织到以下几个关键类中，遵循职责分离原则：

* `FtpServer.java`:
    * **职责**: FTP 服务器的主入口点。负责创建 `ServerSocket` 监听控制端口，并为每个新连接的客户端分配一个 `FtpClientHandler` 线程进行处理。
* `FtpClientHandler.java`:
    * **职责**: 处理单个客户端的 FTP 会话。它读取客户端发送的命令，解析并分发给相应的处理方法。它维护客户端的会话状态（如认证状态、当前工作目录）。
* `FtpDataConnectionManager.java`:
    * **职责**: 封装了所有与 FTP 数据连接相关的逻辑。包括 `PORT` 和 `PASV` 模式下的数据 Socket 创建，以及文件内容的读写（上传和下载）和目录列表的传输。
* `UserAuthenticator.java`:
    * **职责**: 专门负责用户认证逻辑。它从外部 `users.properties` 文件中加载有效的用户名和密码，并提供方法进行认证。
* `users.properties`:
    * **职责**: 外部配置文件，以 `username=password` 的格式存储服务器允许登录的用户凭据。

## 如何运行

1.  **准备环境**:
    * 确保已安装 Java Development Kit (JDK) 8 或更高版本。
    * 推荐使用 IntelliJ IDEA 或其他 Java IDE。

2.  **克隆/下载项目**:
    * 将项目文件（包括 `src` 目录和 `users.properties` 文件）放置到你的本地工作目录。

3.  **配置用户**:
    * 打开项目根目录下的 `users.properties` 文件。
    * 可以根据需要添加或修改用户凭据（例如：`newuser=newpass`）。默认已有 `user=pass` 和 `admin=adminpass`。

4.  **在 IntelliJ IDEA 中运行**:
    * 打开 IntelliJ IDEA。
    * 选择 `File` -> `Open`，然后选择项目的根目录（包含 `src` 和 `users.properties` 的文件夹）。
    * 等待 IDEA 加载项目并构建完成。
    * 导航到 `src` 目录下的 `FtpServer.java` 文件。
    * 右键点击 `FtpServer.java`，选择 `Run 'FtpServer.main()'`。
    * 服务器将在 IDEA 的 "Run" 窗口中启动，并显示 "FTP Server started on port 21" 信息。

## 如何使用

服务器启动后，可以通过 Windows 命令提示符 (CMD) 中的 `ftp` 客户端进行连接和交互。

1.  **打开命令提示符 (CMD)**:
    * 按下 `Win + R`，输入 `cmd`，然后按回车。

2.  **连接到 FTP 服务器**:
    ```bash
    ftp localhost
    ```
    * 服务器将返回 `220 Service ready for new user.`

3.  **登录**:
    * 输入用户名 (例如 `user`)，然后按回车。
        ```bash
        user user
        ```
    * 服务器将返回 `331 Password required for user.`
    * 输入密码 (例如 `pass`)，然后按回车。
        ```bash
        pass pass
        ```
    * 服务器将返回 `230 User user logged in.`，表示登录成功。

4.  **常用命令示例**:
    * **显示文件列表**:
        ```bash
        dir
        ```
    * **上传文件**: (确保 `my_local_file.txt` 存在于你当前 CMD 所在目录)
        ```bash
        put my_local_file.txt
        ```
    * **下载文件**: (下载服务器上的 `some_remote_file.txt`)
        ```bash
        get some_remote_file.txt
        ```
    * **创建目录**:
        ```bash
        mkdir new_directory
        ```
    * **删除文件**: (删除 `some_file.txt`)
        ```bash
        delete some_file.txt
        ```
    * **重命名文件**: (将 `old_name.txt` 重命名为 `new_name.txt`)
        ```bash
        rename old_name.txt new_name.txt
        ```
    * **获取文件大小**: (使用 `quote` 命令发送原始 FTP 命令)
        ```bash
        quote SIZE some_file.txt
        ```
    * **获取文件修改时间**: (使用 `quote` 命令发送原始 FTP 命令)
        ```bash
        quote MDTM some_file.txt
        ```
    * **切换目录**:
        ```bash
        cd new_directory
        ```
    * **显示当前工作目录**:
        ```bash
        pwd
        ```
    * **退出 FTP 会话**:
        ```bash
        quit
        ```
