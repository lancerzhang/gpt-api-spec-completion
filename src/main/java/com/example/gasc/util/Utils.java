package com.example.gasc.util;

import java.util.ArrayList;
import java.util.List;

public class Utils {

    public static String[] splitReturnContent(String content) {
        String[] splitArray = content.split("\\*\\*\\*");

        for (int i = 0; i < splitArray.length; i++) {
            splitArray[i] = splitArray[i].trim();
        }

        return splitArray;
    }

    public static List<String> getContentItems(String content) {
        String[] splitArray = content.split("\\\\n");
        List<String> itemList = new ArrayList<>();

        for (String item : splitArray) {
            String trimmedItem = item.trim();
            if (!trimmedItem.isEmpty()) {
                itemList.add(trimmedItem);
            }
        }

        return itemList;
    }


    public static boolean isAllNA(String[] codeblocks){
        for (String item : codeblocks) {
            if (!item.equals("N/A")) {
                return false;
            }
        }
        return true;
    }

}
