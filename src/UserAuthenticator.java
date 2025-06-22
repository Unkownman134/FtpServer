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

    /**
     * 检查用户名是否存在
     * @param username 需要检查的用户名
     * @return 如果用户名存在，则返回true，否则返回false
     */
    public boolean isUsernameValid(String username) {
        if (username == null || username.isEmpty()) {
            return false;
        }

        return users.containsKey(username);
    }
}