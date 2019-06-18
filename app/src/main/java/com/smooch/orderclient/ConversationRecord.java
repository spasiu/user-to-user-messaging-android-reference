package com.smooch.orderclient;

import org.json.JSONException;
import org.json.JSONObject;

public class ConversationRecord {
    private String appId;
    private String appUserId;
    private String convoId;
    public ConversationRecord(JSONObject conversation) throws JSONException {
        if (!conversation.has("metadata")) return;
        JSONObject metadata = conversation.getJSONObject("metadata");
        appId = metadata.getString("interlocutorAppId");
        appUserId = metadata.getString("interlocutorAppUserId");
        convoId = metadata.getString("interlocutorConvoId");
    }

    public String getAppId() {
        return appId;
    }

    public String getAppUserId() {
        return appUserId;
    }

    public String getConvoId() {
        return convoId;
    }
}
