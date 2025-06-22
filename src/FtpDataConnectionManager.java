import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;

public class FtpDataConnectionManager {
    // 数据传输模式
    private String dataTransferMode;
    // 数据连接的目标IP地址
    private String dataHost;
    // 数据连接的目标端口
    private int dataPort;

    // 连接超时时间
    private static final int DATA_CONNECTION_TIMEOUT_MS = 10000;

    /**
     * 构造函数。
     */
    public FtpDataConnectionManager() {
        this.dataTransferMode = null;
        this.dataHost = null;
        this.dataPort = 0;
    }

    /**
     * 设置数据传输模式为PORT。
     * @param host 客户端提供的数据连接IP地址
     * @param port 客户端提供的数据连接端口
     */
    public void setPortMode(String host, int port) {
        this.dataTransferMode = "PORT";
        this.dataHost = host;
        this.dataPort = port;
    }

    /**
     * 设置数据传输模式为PASV，并返回服务器监听的端口。
     * @return 服务器为数据连接监听的端口号
     * @throws IOException 如果无法打开ServerSocket
     */
    public int setPasvMode() throws IOException {
        // 通过监听一个临时端口来让系统自动分配一个可用端口
        try (ServerSocket tempPasvServerSocket = new ServerSocket(0)) {
            // 获取系统分配的端口
            this.dataPort = tempPasvServerSocket.getLocalPort();
        }
        this.dataTransferMode = "PASV";
        return this.dataPort;
    }

    /**
     * 根据当前设置的数据传输模式，创建并返回一个数据连接的Socket。
     * @return 成功建立的数据连接Socket，如果失败则返回null。
     * @throws IOException 如果在建立连接过程中发生IO错误。
     */
    public Socket createDataSocket() throws IOException {
        if (dataTransferMode == null) {
            return null;
        }

        if ("PORT".equalsIgnoreCase(dataTransferMode)) {
            // 主动模式：服务器主动连接客户端指定的IP和端口
            return new Socket(dataHost, dataPort);
        } else if ("PASV".equalsIgnoreCase(dataTransferMode)) {
            // 被动模式：服务器监听之前通过PASV命令告知客户端的端口，并等待客户端连接
            ServerSocket tempPasvServerSocket = null;
            try {
                tempPasvServerSocket = new ServerSocket(dataPort);
                tempPasvServerSocket.setSoTimeout(DATA_CONNECTION_TIMEOUT_MS);

                // 阻塞等待客户端连接
                Socket clientDataSocket = tempPasvServerSocket.accept();
                return clientDataSocket;
            } catch (SocketTimeoutException e) {
                // 连接超时，返回null
                return null;
            } finally {
                if (tempPasvServerSocket != null && !tempPasvServerSocket.isClosed()) {
                    // 无论成功与否，都关闭这个临时的ServerSocket
                    tempPasvServerSocket.close();
                }
            }
        }
        // 不支持的模式
        return null;
    }
}
