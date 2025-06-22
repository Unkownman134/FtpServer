import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class FtpClientHandler implements Runnable {
    // 用于控制连接的Socket
    private Socket controlSocket;
    // 从控制连接读取客户端命令的阅读器
    private BufferedReader reader;
    // 向控制连接发送服务器响应的写入器
    private PrintWriter writer;

    // 当前登录的用户名
    private String username;
    // 用户是否已认证
    private boolean isAuthenticated;
    // 当前客户端的虚拟工作目录
    private Path currentDirectory;
    // 用于暂存RNFR命令的源路径
    private Path renameFromPath;


    private UserAuthenticator userAuthenticator;
    private FtpDataConnectionManager dataConnectionManager;

    /**
     * 构造函数
     * @param clientSocket 代表该客户端的控制连接
     */
    public FtpClientHandler(Socket clientSocket) {
        this.controlSocket = clientSocket;
        this.isAuthenticated = false;
        // 初始工作目录
        this.currentDirectory = Paths.get(System.getProperty("user.dir"));
        this.renameFromPath = null;

        this.userAuthenticator = new UserAuthenticator();
        this.dataConnectionManager = new FtpDataConnectionManager();

        try {
            this.reader = new BufferedReader(new InputStreamReader(controlSocket.getInputStream()));
            this.writer = new PrintWriter(controlSocket.getOutputStream(), true);
        } catch (IOException e) {
            e.printStackTrace();
            try {
                if (controlSocket != null && !controlSocket.isClosed()) {
                    controlSocket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void run() {
        try {
            // 发送欢迎消息
            sendReply(220, "服务就绪，等待新用户连接。");

            String line;
            // 持续从客户端读取命令，直到连接断开或发生错误
            while ((line = reader.readLine()) != null) {
                // 处理接收到的命令
                processCommand(line);
                if (controlSocket.isClosed()) {
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 解析并处理从客户端接收到的单个FTP命令。
     * @param commandLine 客户端发送的原始命令字符串
     */
    private void processCommand(String commandLine) {
        // 解析客户端发送过来的FTP命令字符串，将其拆分成命令本身和对应的参数
        String[] parts = commandLine.split(" ", 2);
        String command = parts[0].toUpperCase();
        String argument = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "USER":
                handleUSER(argument);
                break;
            case "PASS":
                handlePASS(argument);
                break;
            case "QUIT":
                handleQUIT();
                break;
            case "SYST":
                sendReply(215, "UNIX 类型: L8");
                break;
            case "FEAT":
                sendReply(211, "支持的扩展：");
                sendReply(211, "结束");
                break;
            case "OPTS":
                handleOPTS(argument);
                break;
            case "PWD":
                handlePWD();
                break;
            case "CWD":
                handleCWD(argument);
                break;
            case "TYPE":
                handleTYPE(argument);
                break;
            case "PORT":
                handlePORT(argument);
                break;
            case "PASV":
                handlePASV();
                break;
            case "EPRT":
                handleEPRT(argument);
                break;
            case "LIST":
                handleLIST();
                break;
            case "RETR":
                handleRETR(argument);
                break;
            case "STOR":
                handleSTOR(argument);
                break;
            case "DELE":
                handleDELE(argument);
                break;
            case "MKD":
            case "XMKD":
                handleMKD(argument);
                break;
            case "RMD":
            case "XRMD":
                handleRMD(argument);
                break;
            case "RNFR":
                handleRNFR(argument);
                break;
            case "RNTO":
                handleRNTO(argument);
                break;
            case "SIZE":
                handleSIZE(argument);
                break;
            default:
                sendReply(502, "命令未实现。");
                break;
        }
    }

    /**
     * 处理SIZE命令，获取文件大小。
     * @param filename 要查询大小的文件名
     */
    private void handleSIZE(String filename) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }

        Path filePath = currentDirectory.resolve(filename).normalize();

        try {
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendReply(550, "文件未找到或它是一个目录。");
                return;
            }
            long size = Files.size(filePath);
            sendReply(213, String.valueOf(size));
        } catch (IOException e) {
            sendReply(550, "获取文件大小失败：" + e.getMessage());
        }
    }

    /**
     * 处理RNTO命令，指定重命名后的目标文件或目录。
     * @param pathname 重命名后的目标文件或目录的路径
     */
    private void handleRNTO(String pathname) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }

        if (this.renameFromPath == null) {
            sendReply(503, "命令序列错误。未执行 RNFR 命令。");
            return;
        }

        Path destinationPath = currentDirectory.resolve(pathname).normalize();

        try {
            // 检查目标路径的父目录是否存在且可写
            if (destinationPath.getParent() == null || !Files.exists(destinationPath.getParent()) || !Files.isDirectory(destinationPath.getParent()) || !Files.isWritable(destinationPath.getParent())) {
                sendReply(550, "权限不足或目标路径无效。");
                // 清除暂存的路径
                this.renameFromPath = null;
                return;
            }

            // 如果目标文件/目录已存在，重命名操作默认会失败。
            if (Files.exists(destinationPath)) {
                sendReply(550, "目标文件或目录已存在。");
                this.renameFromPath = null;
                return;
            }

            // 执行重命名操作
            Files.move(this.renameFromPath, destinationPath, StandardCopyOption.ATOMIC_MOVE);
            sendReply(250, "请求的文件操作成功，已完成。");
        } catch (IOException e) {
            sendReply(550, "重命名文件或目录失败：" + e.getMessage());
        } finally {
            // 无论成功失败，都清除暂存的路径
            this.renameFromPath = null;
        }
    }

    /**
     * 处理RNFR命令，指定要重命名的源文件或目录。
     * @param pathname 要重命名的源文件或目录的路径
     */
    private void handleRNFR(String pathname) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }

        Path sourcePath = currentDirectory.resolve(pathname).normalize();

        if (!Files.exists(sourcePath)) {
            sendReply(550, "文件或目录未找到。");
            // 清除暂存的路径
            this.renameFromPath = null;
            return;
        }

        // 检查读取权限，确保有权限访问源文件/目录
        if (!Files.isReadable(sourcePath)) {
            sendReply(550, "访问源文件/目录权限被拒绝。");
            this.renameFromPath = null;
            return;
        }

        // 暂存源路径
        this.renameFromPath = sourcePath;
        sendReply(350, "请求的文件操作正在等待进一步信息。");
    }

    /**
     * 处理RMD命令，删除服务器上的空目录。
     * @param directoryName 要删除的目录名
     */
    private void handleRMD(String directoryName) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }

        Path directoryPath = currentDirectory.resolve(directoryName).normalize();

        try {
            // 检查目录是否存在且是目录
            if (!Files.exists(directoryPath) || !Files.isDirectory(directoryPath)) {
                sendReply(550, "目录未找到或它是一个文件。");
                return;
            }

            // 检查目录是否为空
            if (Files.list(directoryPath).findAny().isPresent()) {
                sendReply(550, "目录不为空。");
                return;
            }

            // 检查是否有写入权限
            if (!Files.isWritable(directoryPath.getParent())) {
                sendReply(550, "删除目录权限被拒绝。");
                return;
            }

            // 删除空目录
            Files.delete(directoryPath);
            sendReply(250, "请求的文件操作成功，已完成。目录 " + directoryName + " 已删除。");
        } catch (IOException e) {
            sendReply(550, "删除目录失败：" + e.getMessage());
        }
    }

    /**
     * 处理MKD命令，在服务器上创建新目录。
     * @param directoryName 要创建的目录名。
     */
    private void handleMKD(String directoryName) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }

        Path newDirectoryPath = currentDirectory.resolve(directoryName).normalize();

        try {
            // 检查父目录是否存在且可写
            if (newDirectoryPath.getParent() == null || !Files.exists(newDirectoryPath.getParent()) || !Files.isDirectory(newDirectoryPath.getParent()) || !Files.isWritable(newDirectoryPath.getParent())) {
                sendReply(550, "权限不足或目录创建路径无效。");
                return;
            }

            // 检查目录是否已经存在
            if (Files.exists(newDirectoryPath)) {
                sendReply(550, "目录已存在。");
                return;
            }

            // 创建新目录
            Files.createDirectory(newDirectoryPath);
            sendReply(257, "\"" + newDirectoryPath.toAbsolutePath().normalize().toString().replace("\\", "/") + "\" 已创建。");
        } catch (IOException e) {
            sendReply(550, "创建目录失败：" + e.getMessage());
        }
    }


    /**
     * 处理DELE命令，删除服务器上的指定文件。
     * @param filename 要删除的文件名。
     */
    private void handleDELE(String filename) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }

        Path filePath = currentDirectory.resolve(filename).normalize();

        try {
            // 检查文件是否存在且是一个常规文件（不能删除目录）
            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                sendReply(550, "文件未找到或它是一个目录。");
                return;
            }

            // 检查是否有写入权限
            if (!Files.isWritable(filePath.getParent())) {
                sendReply(550, "删除文件权限被拒绝。");
                return;
            }

            // 执行删除操作
            Files.delete(filePath);
            sendReply(250, "请求的文件操作成功，已完成。文件 " + filename + " 已删除。");
        } catch (IOException e) {
            sendReply(550, "删除文件失败：" + e.getMessage());
        }
    }

    /**
     * 处理STOR命令，接收客户端上传的文件并写入到服务器。
     * @param filename 客户端请求上传的文件名
     */
    private void handleSTOR(String filename) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }

        Path filePath = currentDirectory.resolve(filename).normalize();

        // 检查父目录是否存在且是目录，并且可写
        if (filePath.getParent() == null || !Files.exists(filePath.getParent()) || !Files.isDirectory(filePath.getParent()) || !Files.isWritable(filePath.getParent())) {
            sendReply(550, "权限不足或上传路径无效。");
            return;
        }

        try {
            sendReply(150, "正在打开二进制模式数据连接，用于写入文件 " + filename + "。");
            Socket dataSocket = dataConnectionManager.createDataSocket();
            if (dataSocket == null) {
                sendReply(425, "无法打开数据连接。");
                return;
            }

            try {
                // 通过管理器读取数据并写入文件
                dataConnectionManager.writeFileToPath(dataSocket, filePath);
                sendReply(226, "传输完成。");
            } finally {
                dataSocket.close();
            }
        } catch (IOException e) {
            sendReply(550, "存储文件失败：" + e.getMessage());
        }
    }

    /**
     * 处理RETR命令。
     * 该命令用于从服务器下载指定文件到客户端。
     * 文件数据通过数据连接传输。
     * 需要用户已登录才能执行。
     * @param filename 客户端请求下载的文件名
     */
    private void handleRETR(String filename) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }
        Path filePath = currentDirectory.resolve(filename).normalize();

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            sendReply(550, "文件未找到或不是一个常规文件。");
            return;
        }

        try {
            long fileSize = Files.size(filePath);
            sendReply(150, "正在打开二进制模式数据连接，用于文件 " + filename + "（" + fileSize + " 字节）。");
            // 通过管理器创建数据Socket
            Socket dataSocket = dataConnectionManager.createDataSocket();
            if (dataSocket == null) {
                sendReply(425, "无法打开数据连接。");
                return;
            }

            try {
                // 通过管理器写入文件内容
                dataConnectionManager.writeFileContent(dataSocket, filePath);
                sendReply(226, "传输完成。");
            } finally {
                dataSocket.close();
            }
        } catch (IOException e) {
            sendReply(550, "检索文件失败：" + e.getMessage());
        }
    }

    /**
     * 处理LIST命令。
     * 该命令用于获取当前工作目录或指定目录的文件和子目录列表。
     * 数据列表通过数据连接传输给客户端。
     * 需要用户已登录才能执行。
     */
    private void handleLIST() {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }
        try {
            sendReply(150, "正在打开 ASCII 模式数据连接以获取文件列表。");
            // 通过管理器创建数据Socket
            Socket dataSocket = dataConnectionManager.createDataSocket();
            if (dataSocket == null) {
                sendReply(425, "无法打开数据连接。");
                return;
            }

            try {
                // 通过管理器写入文件列表
                dataConnectionManager.writeFileList(dataSocket, currentDirectory);
                sendReply(226, "传输完成。");
            } finally {
                dataSocket.close();
            }
        } catch (IOException e) {
            sendReply(425, "无法打开数据连接。" + e.getMessage());
        }
    }

    /**
     * 处理EPRT命令。
     * 这是PORT命令的扩展版本，支持IPv6地址，并使用更灵活的参数格式。
     * 客户端告知服务器其数据连接的IP地址和端口号，服务器将尝试连接到该地址和端口。
     * 需要用户已登录才能执行。
     * @param arg 客户端提供的EPRT命令参数，格式通常为|网络协议|主机地址|端口|。
     */
    private void handleEPRT(String arg) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }
        try {
            String[] parts = arg.substring(1, arg.length() - 1).split("\\|");
            if (parts.length != 3) {
                sendReply(501, "参数或语法错误。");
                return;
            }
            String networkProtocol = parts[0];
            String hostAddress = parts[1];
            int port = Integer.parseInt(parts[2]);

            // 1代表IPv4, 2代表IPv6
            if (!networkProtocol.equals("2") && !networkProtocol.equals("1")) {
                sendReply(522, "不支持的网络协议，请使用 AF_INET 或 AF_INET6。");
                return;
            }

            // EPRT 也是一种主动模式，使用管理器设置
            dataConnectionManager.setPortMode(hostAddress, port);
            sendReply(200, "EPRT 命令成功。数据连接参数已接收。");
        } catch (NumberFormatException e) {
            sendReply(501, "参数或语法错误（端口格式）。");
        } catch (StringIndexOutOfBoundsException | ArrayIndexOutOfBoundsException e) {
            sendReply(501, "参数或语法错误（EPRT 格式）。");
        } catch (Exception e) {
            sendReply(501, "参数或语法错误：" + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理PASV命令。
     * 该命令用于在被动模式下建立数据连接。
     * 服务器会开启一个临时端口，并将其IP地址和端口信息告知客户端，客户端随后连接此端口。
     * 需要用户已登录才能执行。
     */
    private void handlePASV() {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }
        try {
            // 使用管理器设置模式并获取端口
            int port = dataConnectionManager.setPasvMode();
            String ipAddress = controlSocket.getLocalAddress().getHostAddress();
            String[] ipParts = ipAddress.split("\\.");
            int p1 = port / 256;
            int p2 = port % 256;
            sendReply(227, "进入被动模式 (" + String.join(",", ipParts) + "," + p1 + "," + p2 + ")。");
        } catch (IOException e) {
            sendReply(421, "服务不可用，无法打开数据连接。");
        }
    }

    /**
     * 处理PORT命令。
     * 该命令用于在主动模式下设置数据连接的IP地址和端口号。
     * 客户端会指定一个IP地址和端口，服务器将尝试连接到该地址和端口以建立数据连接。
     * @param arg 客户端提供的PORT命令参数，格式为 h1,h2,h3,h4,p1,p2，其中h1-h4是IP地址，p1和p2用于计算端口号
     */
    private void handlePORT(String arg) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }
        try {
            String[] parts = arg.split(",");
            if (parts.length != 6) {
                sendReply(501, "参数或语法错误。");
                return;
            }
            String host = String.join(".", parts[0], parts[1], parts[2], parts[3]);
            int port = Integer.parseInt(parts[4]) * 256 + Integer.parseInt(parts[5]);
            // 使用管理器设置模式
            dataConnectionManager.setPortMode(host, port);
            sendReply(200, "PORT 命令成功。数据连接参数已接收。");
        }
        catch (NumberFormatException e) {
            sendReply(501, "参数或语法错误（端口格式）。");
        } catch (Exception e) {
            sendReply(501, "参数或语法错误。");
        }
    }

    /**
     * 处理TYPE命令。
     * 该命令用于设置数据传输类型，常见的有ASCII模式（'A'）和二进制模式（'I'）。
     * @param type 客户端请求设置的数据传输类型，通常是"A"或"I"
     */
    private void handleTYPE(String type) {
        if (type.equalsIgnoreCase("A")) {
            sendReply(200, "类型已设置为 ASCII。");
        } else if (type.equalsIgnoreCase("I")) {
            sendReply(200, "类型已设置为二进制。");
        } else {
            sendReply(504, "该参数的命令未实现。");
        }
    }

    /**
     * 处理CWD命令。
     * 该命令用于更改客户端在服务器上的当前虚拟工作目录。
     * 需要用户已登录才能执行。
     * @param path 客户端请求更改的目标目录路径
     */
    private void handleCWD(String path) {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }
        Path newPath = currentDirectory.resolve(path).normalize();
        if (Files.exists(newPath) && Files.isDirectory(newPath)) {
            currentDirectory = newPath;
            sendReply(250, "目录已成功更改为 " + currentDirectory.toAbsolutePath().normalize().toString().replace("\\", "/") + "。");
        } else {
            sendReply(550, "更改目录失败。目录未找到或不可访问。");
        }
    }

    /**
     * 处理PWD命令。
     * 该命令用于向客户端返回当前用户在服务器上的虚拟工作目录的绝对路径。
     * 需要用户已登录才能执行。
     */
    private void handlePWD() {
        if (!isAuthenticated) {
            sendReply(530, "未登录。");
            return;
        }
        sendReply(257, "\"" + currentDirectory.toAbsolutePath().normalize().toString().replace("\\", "/") + "\" 是当前目录。");
    }

    /**
     * 处理OPTS命令。
     * 该命令用于设置或查询服务器的特定选项。
     * @param argument OPTS命令的参数
     */
    private void handleOPTS(String argument) {
        if (argument.equalsIgnoreCase("UTF8 ON")) {
            sendReply(200, "UTF8 命令成功。");
        } else {
            sendReply(501, "参数或语法错误（不支持的OPTS参数）。");
        }
    }

    /**
     * 处理QUIT命令。
     */
    private void handleQUIT() {
        sendReply(221, "再见。");
        try {
            controlSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 处理PASS命令。
     * @param password 客户端提供的密码
     */
    private void handlePASS(String password) {
        if (!isAuthenticated && username != null && userAuthenticator.authenticate(username, password)) {
            isAuthenticated = true;
            sendReply(230, "用户 " + username + " 已登录。");
        } else {
            isAuthenticated = false;
            sendReply(530, "未登录。用户名或密码不正确。");
        }
    }

    /**
     * 处理USER命令。
     * @param user 客户端提供的用户名
     */
    private void handleUSER(String user) {
        this.username = user;
        boolean isValid = userAuthenticator.isUsernameValid(username);

        // 检查用户名是否存在
        if (isValid) {
            sendReply(331, "用户 " + username + " 需要密码。");
        } else {
            sendReply(530, "未登录。用户名无效。");
        }
    }

    /**
     * 向客户端发送一个包含响应码和对应的文本消息的FTP协议响应。
     * @param code FTP响应码
     * @param message 响应的文本消息
     */
    private void sendReply(int code, String message) {
        String reply = code + " " + message;
        writer.println(reply);
    }
}