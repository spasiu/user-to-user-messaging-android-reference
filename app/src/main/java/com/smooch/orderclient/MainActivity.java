package com.smooch.orderclient;

import android.os.Bundle;
import android.os.StrictMode;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;
import android.view.Menu;
import android.view.MenuItem;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;

import io.smooch.core.CardSummary;
import io.smooch.core.Conversation;
import io.smooch.core.ConversationEvent;
import io.smooch.core.InitializationStatus;
import io.smooch.core.LoginResult;
import io.smooch.core.LogoutResult;
import io.smooch.core.Message;
import io.smooch.core.MessageAction;
import io.smooch.core.MessageUploadStatus;
import io.smooch.core.PaymentStatus;
import io.smooch.core.Settings;
import io.smooch.core.Smooch;
import io.smooch.core.SmoochCallback;
import io.smooch.core.SmoochConnectionStatus;
import io.smooch.core.User;
import io.smooch.ui.ConversationActivity;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class MainActivity extends AppCompatActivity implements Conversation.MessageModifierDelegate, Conversation.Delegate {
    private class Listener extends WebSocketListener {
        @Override
        public void onMessage(WebSocket webSocket, String data) {
            // When receives websocket message from server subscribes to new conversartions
            try {
                JSONObject json = new JSONObject(data);
                subscribeToConversations(json.getJSONArray("conversations"));
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    // global variables
    HashMap<String, ConversationRecord> interlocutors = new HashMap<String, ConversationRecord>();
    String destinationAppId;
    String destinationAppUserId;
    String destinationConvoId;

    // constants
    String appId = "";
    String backendUrl = "";

    @Override
    public Message beforeSend(Message message) {
        String id = Smooch.getConversation().getId();
        HashMap metadata = new HashMap();
        metadata.put("destinationAppId", destinationAppId);
        metadata.put("destinationAppUserId", destinationAppUserId);
        metadata.put("destinationConversationId", destinationConvoId);
        message.setMetadata(metadata);
        return message;
    }

    @Override
    public void onSmoochShown() {
        // when opening conversation view insure routing info is set to conversation
        String id = Smooch.getConversation().getId();
        // using ConversationRecord to store interlocutor until conversation.metadata is exposed on Smooch client
        ConversationRecord interlocutor = interlocutors.get(id);
        if (interlocutor == null) {
            return;
        }

        destinationAppId = interlocutor.getAppId();
        destinationAppUserId = interlocutor.getAppUserId();
        destinationConvoId = interlocutor.getConvoId();
    }

    public void createNewOrder() throws IOException, JSONException {
        StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().permitAll().build());
        JSONObject body = new JSONObject();
        JSONObject customer = new JSONObject();
        customer.put("appId", appId);
        customer.put("userId", User.getCurrentUser().getAppUserId());
        body.put("customer", customer);

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json; charset=utf-8"), body.toString());
        Request request = new Request.Builder()
                .url(backendUrl + "/orders")
                .post(requestBody)
                .build();

        OkHttpClient client = new OkHttpClient();
        Call call = client.newCall(request);
        Response response = call.execute();
        String responseBody = response.body().string();
        JSONObject data = new JSONObject(responseBody);
        final JSONObject convo = data.getJSONObject("conversation");
        final String id = convo.getString("_id");
        interlocutors.put(id, new ConversationRecord(convo));
        Smooch.loadConversation(id, new SmoochCallback() {
            @Override
            public void run(Response response) {
                ConversationActivity.show(MainActivity.this);
            }
        });
    }

    public void subscribeToConversations(JSONArray conversations) throws JSONException {
        // call load conversation for each conversation in order to subscribe client
        for (int i = 0; i < conversations.length(); i++) {
            JSONObject convo = conversations.getJSONObject(i);
            // adding ConversationRecord until conversation.metadata is exposed on Smooch client
            final String id = convo.getString("_id");
            interlocutors.put(id, new ConversationRecord(convo));
            Smooch.loadConversation(id, null);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        Smooch.init(getApplication(), new Settings(appId), new SmoochCallback() {
            @Override
            public void run(Response response) {
                // start conversation call creates a default conversation if it doesn't exist
                Smooch.startConversation(new SmoochCallback() {
                    @Override
                    public void run(Response response) {
                        // set delegates
                        Smooch.getConversation().setMessageModifierDelegate(MainActivity.this);
                        Smooch.getConversation().setSmoochUIDelegate(MainActivity.this);

                        // subscribe to webhooks
                        String userId = User.getCurrentUser().getAppUserId();
                        String url = backendUrl + "?appId=" + appId + "&userId=" + userId;
                        Request request = new Request.Builder().url(url).build();
                        Listener listener = new Listener();
                        OkHttpClient client = new OkHttpClient();
                        client.newWebSocket(request, listener);
                    }
                });
            }
        });

        FloatingActionButton fab = findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                try {
                    // when button clicked create new order
                    createNewOrder();
                } catch (Exception e) {
                    e.printStackTrace();
                    Snackbar.make(view, e.getMessage(), Snackbar.LENGTH_LONG)
                            .setAction("Action", null).show();
                }
            }
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    // default implementations of Smooch delegates

    @Override
    public Message beforeDisplay(Message message) {
        return message;
    }

    @Override
    public Message beforeNotification(Message message) {
        return message;
    }

    @Override
    public void onMessagesReceived(Conversation conversation, List<Message> list) {

    }

    @Override
    public void onUnreadCountChanged(Conversation conversation, int i) {

    }

    @Override
    public void onMessagesReset(Conversation conversation, List<Message> list) {

    }

    @Override
    public void onMessageSent(Message message, MessageUploadStatus messageUploadStatus) {

    }

    @Override
    public void onConversationEventReceived(ConversationEvent conversationEvent) {

    }

    @Override
    public void onInitializationStatusChanged(InitializationStatus initializationStatus) {

    }

    @Override
    public void onLoginComplete(LoginResult loginResult) {

    }

    @Override
    public void onLogoutComplete(LogoutResult logoutResult) {

    }

    @Override
    public void onPaymentProcessed(MessageAction messageAction, PaymentStatus paymentStatus) {

    }

    @Override
    public boolean shouldTriggerAction(MessageAction messageAction) {
        return false;
    }

    @Override
    public void onCardSummaryLoaded(CardSummary cardSummary) {

    }

    @Override
    public void onSmoochConnectionStatusChanged(SmoochConnectionStatus smoochConnectionStatus) {

    }

    @Override
    public void onSmoochHidden() {

    }
}
