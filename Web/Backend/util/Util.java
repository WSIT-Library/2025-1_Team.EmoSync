package com.example.capstone.util;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class Util {
    public static class url {
        // url 에 한글을 넣기 위해서 encode 함
        public static String encode(String str) {
            try {
                return URLEncoder.encode(str, "UTF-8");
            } catch (UnsupportedEncodingException e) {
                return str;
            }
        }
    }
}
