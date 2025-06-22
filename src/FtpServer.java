import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FtpServer {
    // 定义FTP控制连接的默认端口号
    private static final int CONTROL_PORT = 21;
    // 线程池，用于并发处理多个客户端连接，以避免为每个客户端都创建一个新线程的开销
    private ExecutorService clientThreadPool;
    // 初始化线程池大小
    private static final int DEFAULT_THREAD_POOL_SIZE = 10;

    /**
     * 构造函数
     */
    public FtpServer() {
        clientThreadPool = Executors.newFixedThreadPool(DEFAULT_THREAD_POOL_SIZE);
    }

    /**
     * Java应用程序入口方法
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        new FtpServer().start();
    }

    private void start() {
        try (ServerSocket serverSocket = new ServerSocket(CONTROL_PORT)) {
            while (true) {
                // 一直等待，直到有客户端请求连接
                Socket clientSocket = serverSocket.accept();

                // 为每个新连接的客户端创建一个 FtpClientHandler 实例
                 clientThreadPool.submit(new FtpClientHandler(clientSocket));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            // 关闭线程池以释放资源
            clientThreadPool.shutdown();
        }
    }
}