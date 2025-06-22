import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

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
            default:
                sendReply(502, "命令未实现。");
                break;
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