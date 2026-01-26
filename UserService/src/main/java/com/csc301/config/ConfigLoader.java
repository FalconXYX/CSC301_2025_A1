package com.csc301.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileReader;
import java.io.IOException;

public class ConfigLoader {
    public static Config loadConfig(String configPath) throws IOException {
        FileReader reader = new FileReader(configPath);
        JsonObject jsonConfig = JsonParser.parseReader(reader).getAsJsonObject();
        reader.close();

        JsonObject userServiceConfig = jsonConfig.getAsJsonObject("UserService");
        int port = userServiceConfig.get("port").getAsInt();
        String ip = userServiceConfig.get("ip").getAsString();

        return new Config(ip, port);
    }

    public static class Config {
        public String ip;
        public int port;

        public Config(String ip, int port) {
            this.ip = ip;
            this.port = port;
        }
    }
}
