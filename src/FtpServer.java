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
    }
}