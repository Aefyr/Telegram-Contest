package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class SendAsMenuCell extends FrameLayout {

    private BackupImageView avatarImageView;
    private AvatarDrawable avatarDrawable;
    private BackupImageView closeImageView;

    private int currentAccount;

    private TLRPC.Peer currentPeer;
    private boolean isOpen = true;

    public SendAsMenuCell(@NonNull Context context, int currentAccount) {
        super(context);

        this.currentAccount = currentAccount;

        setClickable(true);

        avatarDrawable = new AvatarDrawable();
        avatarDrawable.setTextSize(AndroidUtilities.dp(16));

        avatarImageView = new BackupImageView(context);
        avatarImageView.setRoundRadius(AndroidUtilities.dp(18));

        addView(avatarImageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER, 0, 0, 0, 0));


        closeImageView = new BackupImageView(context);
        closeImageView.setRoundRadius(AndroidUtilities.dp(18));
        closeImageView.setBackgroundColor(Theme.getColor(Theme.key_chat_messagePanelVoiceBackground));
        closeImageView.setImageResource(R.drawable.ic_layer_close);
        addView(closeImageView, LayoutHelper.createFrame(32, 32, Gravity.CENTER, 0, 0, 0, 0));


        setOpen(false);
    }

    public void setCurrentPeer(TLRPC.Peer peer) {
        currentPeer = peer;

        if (currentPeer != null) {
            if (currentPeer.user_id != 0) {
                TLRPC.User user = MessagesController.getInstance(currentAccount).getUser(currentPeer.user_id);
                if (user != null) {
                    avatarDrawable.setInfo(user);
                    avatarImageView.getImageReceiver().setCurrentAccount(currentAccount);
                    avatarImageView.setForUserOrChat(user, avatarDrawable);
                }
            } else if (currentPeer.channel_id != 0) {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(currentPeer.channel_id);
                if (chat != null) {
                    avatarDrawable.setInfo(chat);
                    avatarImageView.getImageReceiver().setCurrentAccount(currentAccount);
                    avatarImageView.setForUserOrChat(chat, avatarDrawable);
                }
            } else {
                TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(currentPeer.chat_id);
                if (chat != null) {
                    avatarDrawable.setInfo(chat);
                    avatarImageView.getImageReceiver().setCurrentAccount(currentAccount);
                    avatarImageView.setForUserOrChat(chat, avatarDrawable);
                }
            }
        } else {
            //
        }
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open) {
        if (open == isOpen) {
            return;
        }

        isOpen = open;


        if (isOpen) {
            avatarImageView.setVisibility(GONE);
            closeImageView.setVisibility(VISIBLE);
        } else {
            avatarImageView.setVisibility(VISIBLE);
            closeImageView.setVisibility(GONE);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }
}
