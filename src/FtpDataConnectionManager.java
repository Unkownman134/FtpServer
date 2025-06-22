import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FtpDataConnectionManager {
    // 数据传输模式
    private String dataTransferMode;
    // 数据连接的目标IP地址
    private String dataHost;
    // 数据连接的目标端口
    private int dataPort;

    // 连接超时时间
    private static final int DATA_CONNECTION_TIMEOUT_MS = 10000;
    // 缓冲区大小
    private static final int TRANSFER_BUFFER_SIZE = 4096;

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

    /**
     * 将格式化的文件列表写入到数据连接中。
     * @param dataSocket 已建立的数据连接Socket
     * @param currentDirectory 需要列出内容的目录路径
     * @throws IOException 如果写入数据时发生IO错误
     */
    public void writeFileList(Socket dataSocket, Path currentDirectory) throws IOException {
        try (PrintWriter dataWriter = new PrintWriter(dataSocket.getOutputStream(), true)) {
            // 遍历当前目录下的所有文件和子目录
            Files.list(currentDirectory).forEach(path -> {
                try {
                    // 根据文件类型设置权限字符串：目录为drwxr-xr-x，文件为-rw-r--r--
                    String permissions = Files.isDirectory(path) ? "drwxr-xr-x" : "-rw-r--r--";
                    String owner = "ftp";
                    String group = "ftp";
                    // 获取文件大小，如果是目录则大小为0
                    long size = Files.isDirectory(path) ? 0 : Files.size(path);
                    // 格式化文件的最后修改时间
                    SimpleDateFormat sdf = new SimpleDateFormat("MMM dd HH:mm", java.util.Locale.ENGLISH);
                    String date = sdf.format(new Date(Files.getLastModifiedTime(path).toMillis()));
                    // 获取文件或目录的名称
                    String name = path.getFileName().toString();

                    // 格式化输出字符串，遵循FTP LIST命令的标准格式
                    dataWriter.println(String.format("%s 1 %s %s %10d %s %s", permissions, owner, group, size, date, name));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }

    /**
     * 将指定文件的内容通过数据连接发送给客户端
     * @param dataSocket 已建立的数据连接Socket
     * @param filePath 要传输的文件的路径
     * @throws IOException 如果读写文件或网络传输时发生IO错误
     */
    public void writeFileContent(Socket dataSocket, Path filePath) throws IOException {
        try (InputStream fileIn = Files.newInputStream(filePath);
             OutputStream dataOut = dataSocket.getOutputStream()) {

            byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dataOut.write(buffer, 0, bytesRead);
            }
        }
    }

    /**
     * 从数据连接中读取数据并写入到本地文件
     * @param dataSocket 已建立的数据连接Socket
     * @param filePath 要写入的本地文件的路径
     * @throws IOException 如果读写文件或网络传输时发生IO错误
     */
    public void writeFileToPath(Socket dataSocket, Path filePath) throws IOException {
        try (InputStream dataIn = dataSocket.getInputStream();
             OutputStream fileOut = Files.newOutputStream(filePath)) {

            byte[] buffer = new byte[TRANSFER_BUFFER_SIZE];
            int bytesRead;
            while ((bytesRead = dataIn.read(buffer)) != -1) {
                fileOut.write(buffer, 0, bytesRead);
            }
        }
    }
}
