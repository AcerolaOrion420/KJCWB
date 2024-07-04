package org.kjcwb.Packages.Services;

import redis.clients.jedis.Jedis;

public class RedisService {

    private static String redisHost = "127.0.0.1";
    private static int redisPort = 6379;

    public static void storeOtp(String email, String otp) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            jedis.setex(email, 120, otp);
        }
    }

    public static void storeJwt(String email, String jwt) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            jedis.setex(email, 360, jwt);
        }
    }

    public static String getOtp(String email) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            return jedis.get(email);
        }
    }

    public static int getOtpTtl(String email) {
        try (Jedis jedis = new Jedis(redisHost, redisPort)) {
            return Math.toIntExact(jedis.ttl(email));
        }
    }

    public static void close() {
        // No pooling, so nothing to close
    }
}
