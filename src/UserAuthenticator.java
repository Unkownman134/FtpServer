import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class UserAuthenticator {
    private Properties users;

    /**
     * 构造函数
     */
    public UserAuthenticator() {
        users = new Properties();
        loadUsers();
    }

    /**
     * 从外部配置文件加载用户凭据
     */
    private void loadUsers() {
        Path configFilePath = Paths.get(System.getProperty("user.dir"), "users.properties");

        try (InputStream input = Files.newInputStream(configFilePath);
             InputStreamReader reader = new InputStreamReader(input, "UTF-8")) {
            // 加载配置文件
            users.load(reader);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}