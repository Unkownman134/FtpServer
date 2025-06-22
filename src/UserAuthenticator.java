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

    /**
     * 验证提供的用户名和密码是否匹配
     * @param username 需要验证的用户名
     * @param password 需要验证的密码
     * @return 如果用户名和密码都有效，则返回true，否则返回false。
     */
    public boolean authenticate(String username, String password) {
        if (username == null || password == null) {
            return false;
        }
        // 从Properties对象中获取对应用户名的密码并进行比较。
        String storedPassword = users.getProperty(username);
        return storedPassword != null && storedPassword.equals(password);
    }
}