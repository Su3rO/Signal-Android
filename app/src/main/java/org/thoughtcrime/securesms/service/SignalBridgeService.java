package org.thoughtcrime.securesms.service;

import android.app.Service;
import android.content.Intent;
import android.database.Cursor;
import android.os.Binder;
import android.os.IBinder;

import androidx.annotation.Nullable;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.MessageTable;
import org.thoughtcrime.securesms.database.SignalDatabase;
import org.thoughtcrime.securesms.mms.OutgoingMessage;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.sms.MessageSender;

import java.util.ArrayList;
import java.util.List;

/**
 * Simple service that exposes basic APIs for sending and retrieving messages
 * so that other applications can interact with Signal.
 */
public class SignalBridgeService extends Service {

    private static final String TAG = Log.tag(SignalBridgeService.class);

    private final IBinder binder = new BridgeBinder();

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public class BridgeBinder extends Binder {

        public SignalBridgeService getService() {
            return SignalBridgeService.this;
        }

        /**
         * Send a plain text message to the given recipient. The identifier may be
         * a phone number in E164 format or a UUID.
         */
        public void sendMessage(String recipientIdentifier, String body) {
            Recipient recipient = Recipient.external(recipientIdentifier);
            if (recipient == null) {
                Log.w(TAG, "Unable to parse recipient: " + recipientIdentifier);
                return;
            }

            OutgoingMessage outgoing = OutgoingMessage.text(recipient, body, 0);
            MessageSender.send(SignalBridgeService.this, outgoing, -1,
                               MessageSender.SendType.SIGNAL, null, null);
        }

        /**
         * Return the most recent text bodies for the given recipient.
         */
        public List<String> getRecentMessages(String recipientIdentifier, int limit) {
            Recipient recipient = Recipient.external(recipientIdentifier);
            if (recipient == null) {
                Log.w(TAG, "Unable to parse recipient: " + recipientIdentifier);
                return new ArrayList<>();
            }

            long threadId = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);

            Cursor cursor = SignalDatabase.messages().getReadableDatabase()
                    .query(MessageTable.TABLE_NAME,
                           new String[]{MessageTable.BODY},
                           MessageTable.THREAD_ID + "=?",
                           new String[]{String.valueOf(threadId)},
                           null,
                           null,
                           MessageTable.DATE_RECEIVED + " DESC",
                           String.valueOf(limit));

            List<String> messages = new ArrayList<>(limit);
            try {
                int column = cursor.getColumnIndexOrThrow(MessageTable.BODY);
                while (cursor.moveToNext()) {
                    messages.add(cursor.getString(column));
                }
            } finally {
                cursor.close();
            }
            return messages;
        }
    }
}
