import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

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

    private UserAuthenticator userAuthenticator;

    /**
     * 构造函数
     * @param clientSocket 代表该客户端的控制连接
     */
    public FtpClientHandler(Socket clientSocket) {
        this.controlSocket = clientSocket;
        this.isAuthenticated = false;

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
            default:
                sendReply(502, "命令未实现。");
                break;
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