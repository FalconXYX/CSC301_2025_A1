package com.csc301.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.FileReader;
import java.io.IOException;

public class ConfigLoader {
    public static Config loadConfig(String configPath) throws IOException {
        return loadConfig(configPath, "UserService");
    }

    public static Config loadConfig(String configPath, String serviceName) throws IOException {
        FileReader reader = new FileReader(configPath);
        JsonObject jsonConfig = JsonParser.parseReader(reader).getAsJsonObject();
        reader.close();

        JsonObject serviceConfig = jsonConfig.getAsJsonObject(serviceName);
        int port = serviceConfig.get("port").getAsInt();
        String ip = serviceConfig.get("ip").getAsString();

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
