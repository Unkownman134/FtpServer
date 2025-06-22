import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
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
            default:
                sendReply(502, "命令未实现。");
                break;
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